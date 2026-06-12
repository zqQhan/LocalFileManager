# 前端运行与交接说明

本文档给组员用于拉取、运行和验证 JavaFX 前端。

## 当前前端风格

前端已重构为 `Pac-File Arcade` 复古街机风：

- 主界面是三栏布局：左侧快速访问和常用操作，中间文件列表，右侧文件详情与统计。
- 顶部支持搜索、索引当前目录、导出 CSV、刷新。
- 目录导航支持后退、前进、上一级、打开目录。
- 状态栏有吃豆人吃豆子的加载动画。
- 主视觉图片位于 `desktop/src/main/resources/images/pac-file-arcade.png`。

## 启动依赖服务

在项目根目录启动基础设施：

```powershell
cd C:\Users\25137\Downloads\localfilemanager
docker compose up -d
```

如果本机 `8080` 已被占用，可以让后端运行在 `18080`：

```powershell
mvn -pl backend -am io.quarkus.platform:quarkus-maven-plugin:3.21.4:dev `
  -Dquarkus.http.port=18080 `
  -Dquarkus.redis.hosts=redis://localhost:6379 `
  -Dkafka.bootstrap.servers=localhost:9092
```

后端健康检查：

```text
http://localhost:18080/q/health
```

Swagger：

```text
http://localhost:18080/swagger-ui
```

## 启动前端

如果后端是默认 `8080`：

```powershell
cd desktop
mvn javafx:run
```

如果后端使用 `18080`：

```powershell
cd desktop
$env:FILEMANAGER_BACKEND_URL = "http://localhost:18080"
mvn javafx:run
```

也可以用 JVM 参数覆盖后端地址：

```powershell
mvn -Dfilemanager.backend.url=http://localhost:18080 javafx:run
```

## 已验证功能

- 浏览目录：可用。
- 后退、前进、上一级：可用。
- 搜索输入、搜索类型、正则、扩展名过滤：前端已接入后端搜索接口。
- 导出 CSV：前端已接入 `/api/files/search/export`。
- 统计面板：前端已接入 `/api/stats/dashboard`。
- 同步索引：前端已接入 `/api/files/index`，但存在后端遍历限制，见下一节。
- 重复文件扫描：前端会先要求选择目录，建议选择小型测试目录。

## 已确认的后端限制

这些不是前端问题，本次没有修改后端：

1. 标签绑定缺口

   后端目前只有标签 CRUD 接口，没有提供“把标签绑定到某个文件”的 REST API。前端会明确提示不能保存文件标签。

2. 同步索引无权限目录失败

   后端 `IndexService` 使用 `Files.walk` 遍历目录。Windows 下索引 `Documents` 时会遇到 `Documents\My Music` 之类受保护目录，并返回 `AccessDeniedException`。前端会解释为后端遍历策略问题。

3. Kafka 异步索引消费者问题

   调用 `/api/files/index/async` 后，后端 `FileIndexConsumer#processIndexTask` 可能触发响应式线程错误 `HR000068`，导致健康检查 DOWN。前端目前不主动调用这个接口，只展示说明。

4. 重复扫描大目录会卡住

   后端 `DuplicateService` 会同步遍历并读取文件。扫描 Home 这类大目录时容易超时或阻塞事件循环。演示时建议建立一个小测试目录，只放几份重复文件。

## 编译验证

前端相关验证命令：

```powershell
mvn -pl desktop -am test
```

当前已通过该命令验证。

