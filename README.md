# File Manager — 本地文件搜索与管理系统

> Java 高级程序设计 (B01080) 期末大作业  
> 技术栈：Quarkus · REST API · Reactive · Kafka · Redis · WebSocket · PostgreSQL · JavaFX

## 项目概述

一个基于 **Quarkus 后端 + JavaFX 桌面客户端** 的本地文件搜索与管理系统，支持：

- 🔍 **文件搜索** — 文件名模糊搜索 + 全文内容检索
- 📁 **文件操作** — 浏览、复制、移动、删除、重命名
- 🏷️ **标签分类** — 给文件打标签，按标签筛选
- 🔄 **去重检测** — SHA-256 内容哈希，查找重复文件
- ⚡ **批量处理** — 通过 Kafka 异步批量操作
- 📡 **实时监控** — WebSocket 推送文件变更 + 批量进度
- 💾 **Redis 缓存** — 搜索热词缓存加速

## 技术架构

```
┌─────────────────────────────────────────────────┐
│                  JavaFX Desktop                  │
│          HTTP REST + WebSocket Client            │
└────────────────────┬────────────────────────────┘
                     │ HTTP / WS
┌────────────────────▼────────────────────────────┐
│               Quarkus Backend                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────┐ │
│  │ REST API │ │ WebSocket│ │  Reactive Mutiny │ │
│  └──────────┘ └──────────┘ └──────────────────┘ │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────┐ │
│  │  Kafka   │ │  Redis   │ │  Hibernate       │ │
│  │ Messaging│ │  Cache   │ │  Reactive Panache│ │
│  └──────────┘ └──────────┘ └──────────────────┘ │
└────────────────────┬────────────────────────────┘
                     │
         ┌───────────┼───────────┐
         ▼           ▼           ▼
      Kafka      Redis      PostgreSQL
```

## 项目结构

```
file-manager/
├── pom.xml                  # Maven 父 POM (Quarkus 3.21.4 BOM)
├── docker-compose.yml       # 基础设施容器 (PG + Redis + Kafka)
├── common/                  # 共享模块 (DTOs, 常量)
├── backend/                 # Quarkus 后端
│   └── src/main/java/com/nick/filemanager/
│       ├── api/             # REST 端点 (File, Tag, Search, Batch, Duplicate)
│       ├── model/entity/    # Panache 实体 (6 个)
│       ├── service/         # 业务逻辑层
│       ├── repository/      # 数据访问
│       ├── websocket/       # WebSocket 实时推送
│       ├── messaging/       # Kafka 生产者/消费者
│       └── search/          # 搜索 + 缓存
├── desktop/                 # JavaFX 桌面客户端
│   └── src/main/java/com/nick/filemanager/ui/
│       ├── App.java         # 入口
│       ├── MainWindow.java  # 主窗口 (菜单/搜索/文件树/表格/状态栏)
│       ├── client/          # REST API 客户端
│       └── websocket/       # WebSocket 客户端
└── doc/                     # 文档
    ├── api.http             # REST Client 测试文件
    └── init-db.sql          # 数据库初始化脚本
```

## 快速开始

### 前置条件

- JDK 21+
- Maven 3.9+
- Docker Desktop (用于 PostgreSQL, Redis, Kafka)
- VS Code (推荐，含 REST Client 插件)

### 1. 启动基础设施

```bash
cd file-manager
docker compose up -d
```

### 2. 启动后端

```bash
cd backend
mvn quarkus:dev
```

后端启动后：
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui
- OpenAPI: http://localhost:8080/openapi
- Health: http://localhost:8080/q/health

### 3. 测试 API

在 VS Code 中打开 `doc/api.http` 文件，使用 REST Client 插件逐个发送请求。

### 4. 启动桌面客户端

```bash
cd desktop
mvn javafx:run
```

## API 端点概览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/files?path=...` | 浏览目录 |
| GET | `/api/files/search?q=...` | 文件搜索 |
| POST | `/api/files/copy` | 复制文件 |
| POST | `/api/files/move` | 移动文件 |
| PUT | `/api/files/rename` | 重命名 |
| DELETE | `/api/files?path=...` | 删除文件 |
| GET/POST | `/api/tags` | 标签管理 |
| POST | `/api/batch` | 批量操作 |
| GET | `/api/duplicates` | 重复文件 |
| WS | `/ws/file-monitor` | 文件变更推送 |
| WS | `/ws/batch-progress/{id}` | 批量进度 |

## 技术栈验证清单

| # | 技术 | 实现位置 |
|---|------|----------|
| ✅ | Quarkus | `backend/pom.xml` — quarkus-rest-jackson |
| ✅ | REST API | `api/` 目录 — FileResource, TagResource 等 |
| ✅ | OpenAPI 文档 | Swagger UI at `/swagger-ui` |
| ✅ | Reactive 编程 | 全部返回 `Uni<T>` / `Multi<T>` |
| ✅ | Kafka 异步消息 | `messaging/` 目录 — Producer/Consumer |
| ✅ | Redis 缓存 | `SearchService` — 搜索缓存 |
| ✅ | WebSocket | `websocket/` — FileMonitor + BatchProgress |
| ✅ | PostgreSQL | Hibernate Reactive Panache 持久化 |
| ✅ | JavaFX GUI | `desktop/` — 完整桌面客户端 |
| ✅ | 文件搜索 | 文件名 ILIKE + 全文 tsvector |
| ✅ | 文件操作 | 复制/移动/删除/重命名 via NIO |
| ✅ | 批量处理 | Kafka 异步批量任务 |
| ✅ | 标签分类 | 多对多关联 + 层级标签 |
| ✅ | 去重 | SHA-256 内容哈希扫描 |

## 许可

MIT License — 个人学习项目
