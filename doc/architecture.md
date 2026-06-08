# File Manager — 架构设计文档

## 1. 整体架构

采用 **前后端分离** 的两进程架构：

```
JavaFX Desktop App  ──HTTP/REST──►  Quarkus Backend  ──►  PostgreSQL
       │              ◄──WebSocket──       │               Redis
       │                                   │               Kafka
       └── 用户交互界面                     └── 业务逻辑 + 数据持久化
```

## 2. 后端分层架构

### Controller 层 (api/)
- `FileResource` — 文件 CRUD + 同步/异步索引
- `SearchResource` — 搜索 (正则/过滤) + 导出
- `TagResource` — 标签管理
- `DuplicateResource` — 去重管理
- `BatchResource` — 批量操作
- `StatsResource` — 统计仪表盘
- `FileRuleResource` — 文件操作规则

### Service 层 (service/)
- `FileService` — 文件操作 (NIO worker 线程), 内容哈希
- `SearchService` — 搜索编排, Redis 缓存, 高级过滤, 导出
- `TagService` — 标签 CRUD
- `DuplicateService` — 重复检测 (多组返回), 清理
- `IndexService` — 全量索引 (Reactive)
- `BatchService` — 批量任务 (worker 线程 offload)
- `StatsService` — 统计聚合 (Stream API)
- `FileRuleService` — 规则执行引擎 (Glob + WalkFileTree)
- `PathGuard` — 路径安全守卫 (黑名单/白名单)

### Repository 层 (repository/)
- `FileIndexRepository` — 文件索引查询 (ILIKE, 正则, tsvector)

### Entity 层 (model/entity/)
- `FileIndex` — 索引文件 (path, name, hash, snippet)
- `Tag` — 标签 (name, color, parent_id)
- `DuplicateGroup` / `DuplicateFile` — 重复文件
- `SearchHistory` — 搜索历史
- `BatchTask` — 批量任务
- `FileRule` — 文件操作规则 (Glob pattern + action)

## 3. 数据流

### 搜索流程
```
Desktop Search → GET /api/files/search?q=xxx
  → SearchService.search()
    → Redis GET search:NAME:xxx:0:20
    → [miss] PostgreSQL: SELECT ... WHERE name ILIKE '%xxx%'
    → Redis SETEX search:NAME:xxx:0:20 300 <result>
    → SearchHistory.persist()
    → Response: {"files":[...], "total": N}
```

### 批量操作流程
```
Desktop → POST /api/batch {type, tasks}
  → BatchTask.persist()
  → Kafka produce batch-tasks
  → Kafka consumer process items
  → BatchTask.progress 更新
  → WebSocket push progress
```

### 文件监控流程
```
Desktop WS Connect → ws://localhost:8080/ws/file-monitor
  → Send {"action":"watch","path":"/some/dir"}
  → WatchService 注册目录
  → 文件变更 → WebSocket push {"type":"create","path":"..."}
  → Desktop 自动刷新文件列表
```

### 正则搜索流程
```
Desktop → GET /api/files/search?q=Resource&regex=true
  → SearchService.search()
    → FileIndexRepository.regexSearch() → listAll() + Pattern.find()
    → applyFilters() — 大小/日期/扩展名内存过滤
    → Redis cache
    → Response: {"files":[...], "total": N}
```

### 规则执行流程
```
Desktop → POST /api/rules/{id}/execute
  → FileRuleService.execute()
    → PathGuard.checkSafe() — 安全校验
    → Worker Thread: Files.walkFileTree + PathMatcher.glob
    → Event Loop: Rule.persist() (reactive)
    → Response: {"matched":5,"affected":5}
```

### 导出流程
```
Desktop → GET /api/files/search/export?q=...&format=csv
  → SearchService.exportResults() — 无分页, 无缓存
    → FileIndexRepository 查询 (limit 1000)
    → applyFilters()
    → CSV: 拼接字符串 + Content-Disposition header
    → JSON: 直接返回 List<FileInfo>
```

## 4. 关键设计决策

| 决策 | 方案 | 理由 |
|------|------|------|
| 进程模型 | 分离进程 | Quarkus 标准用法, API 可独立测试 |
| 响应式 | Mutiny Uni<T> | 课程要求, 非阻塞 I/O |
| 数据访问 | Hibernate Reactive Panache | 配合 Reactive, 简化代码 |
| 搜索 | PostgreSQL ILIKE + tsvector | 无需额外搜索中间件 |
| 缓存 | Redis SETEX + TTL | 轻量, 搜索场景天然适合 |
| 异步消息 | Kafka | 课程要求, 支持批量/去重/日志 |
| 实时推送 | WebSocket (JSR 356) | 课程要求, 浏览器标准协议 |
| 客户端 | JavaFX 纯代码 (无 FXML) | 简化学习曲线 |
| HTTP 客户端 | java.net.http.HttpClient | Java 11+ 内置, 零依赖 |

## 5. 安全注意事项

- 本项目的文件操作直接使用 `java.nio.file.Files`, 无路径穿越保护
- 生产环境应添加: 路径规范化, 白名单目录限制, 操作审计日志
- 当前设计为学习项目, 默认信任所有用户输入
