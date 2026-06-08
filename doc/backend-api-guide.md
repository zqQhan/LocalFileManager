# Backend API — 前端协作开发指南

> 给负责 **JavaFX 桌面客户端** 的伙伴。读完这份文档你就能快速上手调用后端。

## 1. 项目概览

```
file-manager/                  ← 项目根目录
├── backend/                   ← Quarkus 后端 (我们负责)
├── desktop/                   ← JavaFX 客户端 (你负责)
│   └── src/main/java/com/nick/filemanager/ui/
│       ├── App.java           ← 入口 (已搭好骨架)
│       ├── MainWindow.java    ← 主窗口 (已搭好骨架)
│       ├── client/            ← REST 客户端 (已有 FileApiClient、TagApiClient)
│       └── websocket/         ← WebSocket 客户端 (已有 WSClient)
├── common/                    ← 共享 DTO (两边共用)
│   └── src/main/java/com/nick/filemanager/common/dto/
└── doc/
    ├── api.http               ← REST Client 测试文件
    └── backend-api-guide.md   ← 你在读的这份
```

**启动后端 (你只需要做一次):**

```bash
cd backend
mvn quarkus:dev
```

后端在 `http://localhost:8080`，Swagger 文档在 `http://localhost:8080/swagger-ui`。

> 需要先启动 Docker 里的 PostgreSQL + Redis: `docker compose up -d` (在项目根目录)

---

## 2. API 总览

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/files?path=...` | 浏览目录 |
| `GET` | `/api/files/search?q=...&type=NAME` | 文件名搜索 |
| `GET` | `/api/files/search?q=...&type=CONTENT` | 内容搜索 |
| `POST` | `/api/files/index?path=...` | 索引目录 (搜索前必须调用) |
| `POST` | `/api/files/copy` | 复制 |
| `POST` | `/api/files/move` | 移动 |
| `PUT` | `/api/files/rename` | 重命名 |
| `DELETE` | `/api/files?path=...` | 删除 |
| `GET` | `/api/tags` | 列出标签 |
| `POST` | `/api/tags` | 创建标签 |
| `PUT` | `/api/tags/{id}` | 更新标签 |
| `DELETE` | `/api/tags/{id}` | 删除标签 |
| `POST` | `/api/batch` | 批量操作 |
| `GET` | `/api/batch/{id}` | 批量进度 |
| `GET` | `/api/duplicates` | 重复组列表 |
| `POST` | `/api/duplicates/scan?rootPath=...` | 扫描重复 |
| `DELETE` | `/api/duplicates/{groupId}?keepPolicy=oldest` | 清理重复 |
| `WS` | `/ws/file-monitor` | 文件实时监控 |
| `WS` | `/ws/batch-progress/{id}` | 批量进度推送 |

**重要**: 所有路径参数用正斜杠 `E:/local/test` (Windows 反斜杠也能用，但正斜杠最稳)。

---

## 3. 核心 API 详解

### 3.1 浏览目录

```
GET /api/files?path=E:/local/test
```

**响应** (200):
```json
[
  { "path": "E:\\local\\test\\test.txt", "name": "test.txt", "isDirectory": false, "sizeBytes": 1024, "modifiedAt": "2026-06-08T15:36:29.74" },
  { "path": "E:\\local\\test\\backup", "name": "backup", "isDirectory": true, "sizeBytes": 0 }
]
```

### 3.2 搜索文件

**必须先索引目录** (否则搜不到):
```
POST /api/files/index?path=E:/local/test
```

**文件名搜索**:
```
GET /api/files/search?q=test&type=NAME&page=0&size=20
```

**内容搜索**:
```
GET /api/files/search?q=hello&type=CONTENT&page=0&size=20
```

**响应** (200):
```json
{
  "query": "test",
  "files": [
    {
      "id": 1,
      "path": "E:\\local\\test\\test.txt",
      "name": "test.txt",
      "extension": "txt",
      "sizeBytes": 1024,
      "modifiedAt": "2026-06-08T15:36:29.74",
      "mimeType": "text/plain",
      "contentHash": "e3b0c44298fc1c149afbf4c8996fb924...",
      "contentSnippet": "Hello world\nThis is a test file...",
      "indexedAt": "2026-06-08T16:00:00",
      "isDirectory": false
    }
  ],
  "total": 1,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

### 3.3 文件操作

**复制**:
```
POST /api/files/copy
Content-Type: application/json
{"source": "E:/local/test/a.txt", "destination": "E:/local/backup/a.txt"}
→ 200 {"path":"E:\\local\\backup\\a.txt","name":"a.txt",...}
```

**移动**:
```
POST /api/files/move
Content-Type: application/json
{"source": "E:/local/test/a.txt", "destination": "E:/local/other/a.txt"}
→ 200 {"path":"E:\\local\\other\\a.txt","name":"a.txt",...}
```

**重命名**:
```
PUT /api/files/rename
Content-Type: application/json
{"path": "E:/local/test/old.txt", "newName": "new.txt"}
→ 200 {"path":"E:\\local\\test\\new.txt","name":"new.txt",...}
```

**删除**:
```
DELETE /api/files?path=E:/local/test/temp.txt
→ 204 (无响应体)
```

**错误响应统一格式** (400):
```json
{"error": "Source not found: E:\\local\\test\\missing.txt"}
```

> ⚠️ **安全保护**: 系统目录 (`C:\Windows`, `C:\Program Files`) 的写/删操作会被拒绝返回 400。

### 3.4 标签管理

```
POST /api/tags  {"name": "Java", "color": "#F59E0B", "description": "Java source files"}
→ 200 {"id":1,"name":"Java","color":"#F59E0B","fileCount":0}

GET /api/tags
→ 200 [{"id":1,"name":"Java","color":"#F59E0B","fileCount":0}]

PUT /api/tags/1  {"name": "Java Source", "color": "#EF4444"}
→ 200 {"id":1,"name":"Java Source","color":"#EF4444","fileCount":0}

DELETE /api/tags/1
→ 204
```

### 3.5 批量操作

```
POST /api/batch
Content-Type: application/json
{
  "type": "COPY",
  "tasks": [
    {"operation": "COPY", "source": "E:/local/a.txt", "destination": "E:/local/backup/a.txt"},
    {"operation": "DELETE", "source": "E:/local/temp.txt", "destination": ""}
  ]
}
→ 200 {"id":1,"type":"COPY","status":"COMPLETED","total":2,"processed":2,"failed":0}

GET /api/batch/1
→ 200 {"id":1,"status":"COMPLETED","total":2,"processed":2,"failed":0,...}
```

### 3.6 重复文件

```
POST /api/duplicates/scan?rootPath=E:/local/test
→ 200 [
  {
    "contentHash": "e3b0c44298fc1c149afbf4c8996fb924...",
    "fileCount": 2,
    "totalSize": 0,
    "duplicates": [
      {"path":"E:\\local\\test\\a.txt","name":"a.txt","sizeBytes":0},
      {"path":"E:\\local\\test\\b.txt","name":"b.txt","sizeBytes":0}
    ]
  }
]
# 空结果: {"message":"No duplicates found","rootPath":"..."}

GET /api/duplicates
→ 200 []  (数据库中已持久化的重复组)

DELETE /api/duplicates/1?keepPolicy=oldest
→ 200 {"deleted":2,"message":"Cleaned 2 duplicate file(s)"}
```

### 3.7 统计仪表盘

```
GET /api/stats/dashboard
→ 200 {
  "totalFiles": 34,
  "totalSizeBytes": 123456,
  "totalSizeFormatted": "120.6 KB",
  "byExtension": {"java": 32, "properties": 1, "sql": 1},
  "byMimeType": {"text/plain": 32, "unknown": 2},
  "largestFiles": [{"name":"FileIndex.java","path":"...","size":"2.6 KB"}, ...],
  "recentlyModified": [...],
  "oldestFiles": [...],
  "uniqueContentHashes": 30,
  "potentialDuplicates": 4
}
```

### 3.8 正则搜索 + 高级过滤

```
# 正则搜索文件名
GET /api/files/search?q=Resource&regex=true&type=NAME
→ {"total": 7, "files": [...]}

# 正则 + 大小过滤 + 日期过滤 + 扩展名
GET /api/files/search?q=Service&regex=true&type=NAME&sizeMin=1000&sizeMax=10000&extension=java&dateFrom=2026-01-01
→ {"total": 3, "files": [...]}

# 非法日期 → 400
GET /api/files/search?q=test&dateFrom=2026-13-45
→ 400 {"error":"Invalid dateFrom value '2026-13-45'. Use ISO format: 2026-01-01T00:00:00"}
```

### 3.9 搜索结果导出

```
# JSON 格式
GET /api/files/search/export?q=Resource&regex=true&type=NAME&format=json
→ 200 [{"name":"FileResource.java","path":"...","sizeBytes":7326}, ...]

# CSV 格式 (Content-Disposition: attachment)
GET /api/files/search/export?q=Resource&regex=true&type=NAME&format=csv
→ 200 (CSV 文件下载)
```

### 3.10 文件操作规则

```
# 创建规则
POST /api/rules
{"name":"清理临时文件","pattern":"*.tmp","actionType":"DELETE","rootPath":"E:/local/test","enabled":false}
→ 200 {"id":1,"name":"清理临时文件","pattern":"*.tmp",...}

# 列出规则
GET /api/rules → 200 [...]

# 执行规则 (匹配文件并操作)
POST /api/rules/1/execute
→ 200 {"matched":5,"affected":5}

# 执行全部启用规则
POST /api/rules/execute-all
→ 200 {"executed":2,"results":[...]}

# 更新/删除
PUT /api/rules/1 {"enabled":true} → 200
DELETE /api/rules/1 → 204
```

---

## 4. WebSocket 实时通信

### 4.1 文件监控

**连接**: `ws://localhost:8080/ws/file-monitor`

**开始监控**:
```json
{"action": "watch", "path": "E:/local/test"}
```

**收到文件变更推送**:
```json
{"type": "create", "path": "E:\\local\\test\\newfile.txt", "name": "newfile.txt"}
{"type": "modify", "path": "E:\\local\\test\\test.txt", "name": "test.txt"}
{"type": "delete", "path": "E:\\local\\test\\old.txt", "name": "old.txt"}
```

**停止监控**: `{"action": "unwatch"}`

**Java 客户端已有基础实现**: `desktop/.../websocket/WSClient.java`
```java
WSClient wsClient = new WSClient("http://localhost:8080", json -> {
    // 处理文件变更推送
    System.out.println("Event: " + json);
});
wsClient.connect();
wsClient.watchDirectory("E:/local/test");
```

### 4.2 批量任务进度

**连接**: `ws://localhost:8080/ws/batch-progress/{taskId}`

**推送格式** (每 2 秒):
```json
{"type":"progress","taskId":1,"status":"RUNNING","total":10,"processed":5,"failed":0}
{"type":"completed","taskId":1}
```

---

## 5. 共享数据模型 (common 模块)

你在 `desktop/` 里直接 import `common` 模块的类:

```java
import com.nick.filemanager.common.dto.FileInfo;
import com.nick.filemanager.common.dto.TagDTO;
import com.nick.filemanager.common.dto.BatchTaskDTO;
import com.nick.filemanager.common.dto.DuplicateGroupDTO;
import com.nick.filemanager.common.dto.SearchQuery;
import com.nick.filemanager.common.constant.AppConstants;
```

### FileInfo 核心字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 数据库 ID (索引后才有) |
| `path` | String | 绝对路径 |
| `name` | String | 文件名 |
| `extension` | String | 扩展名 (小写) |
| `sizeBytes` | long | 字节数 |
| `modifiedAt` | LocalDateTime | 修改时间 |
| `mimeType` | String | MIME 类型 |
| `contentHash` | String | SHA-256 (索引后才有) |
| `contentSnippet` | String | 内容片段 (搜索匹配用) |
| `isDirectory` | Boolean | 是否目录 |

---

## 6. REST 客户端 (已有示例)

`desktop/.../client/FileApiClient.java` 已实现，使用 Java 11+ 内置 `java.net.http.HttpClient`，零依赖:

```java
FileApiClient client = new FileApiClient("http://localhost:8080");

// 异步调用，返回 CompletableFuture
client.browseDirectory("E:/local/test")
    .thenAccept(files -> {
        // 更新 UI
        fileTable.getItems().setAll(files);
    });

client.search("keyword", "NAME", 0, 20)
    .thenAccept(results -> { ... });

client.copyFile("E:/local/a.txt", "E:/local/b.txt")
    .thenAccept(fileInfo -> { ... });
```

---

## 7. 典型交互流程

### 用户首次使用
```
1. 用户选目录 → POST /api/files/index?path=...  (索引)
2. 显示完成 → GET /api/files?path=... (浏览)
3. 用户搜索 → GET /api/files/search?q=...&type=CONTENT
```

### 文件操作
```
1. 用户右键 → 弹出菜单 (复制/移动/重命名/删除/添加标签)
2. 复制 → POST /api/files/copy
3. 刷新 → GET /api/files?path=... (重新浏览当前目录)
```

### 实时监控 (可选加分项)
```
1. 连接 WebSocket → ws://localhost:8080/ws/file-monitor
2. 发送 watch 命令 → {"action":"watch","path":"E:/local/test"}
3. 收到文件变更推送 → 自动刷新当前目录的文件列表
```

### 批量操作
```
1. 用户多选文件 → 右键"批量复制到..."
2. POST /api/batch  {"type":"COPY","tasks":[...]}
3. 连接 WS 获取进度 → ws://localhost:8080/ws/batch-progress/{taskId}
```

---

## 8. 注意事项

1. **路径用正斜杠** `E:/local/test` 而不是 `E:\local\test` (Java 自动兼容)
2. **搜索前先索引** — 否则返回 `total: 0`
3. **重启后端会清数据库** (`drop-and-create` 模式) — 需要重新索引
4. **系统目录有保护** — 操作 `C:\Windows` 等会返回 **403 Forbidden**
5. **所有 API 返回 JSON** — 错误带 `{"error":"..."}` 字段
6. **Swagger UI** — 浏览器 `localhost:8080/swagger-ui` 可交互式测试所有接口
7. **`api.http` 文件** — VS Code 装 REST Client 插件后可直接发包测试

---

## 9. 快速开始检查清单

- [ ] Docker 跑起来了 (`docker compose ps` 看到 postgres + redis)
- [ ] 后端启动了 (`mvn quarkus:dev` 看到 `Listening on: http://localhost:8080`)
- [ ] 浏览器 `localhost:8080/swagger-ui` 能看到 API 文档
- [ ] `localhost:8080/api/files/ping` 返回 `{"status":"OK"}`
- [ ] 索引一个测试目录: `POST /api/files/index?path=E:/local/test`
- [ ] 搜索能返回结果: `GET /api/files/search?q=test&type=NAME`
- [ ] 开始写 JavaFX 界面 🚀
