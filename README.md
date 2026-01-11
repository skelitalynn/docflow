# Docflow

Docflow 是一个基于 Spring Boot 的协作文档平台，集成文档编辑、协作成员管理、评论与任务、通知、会议与文件共享等能力。前端为内置静态单页，后端提供 REST API 与 WebSocket 实时同步。

## 功能特性
- 文档管理：新建/编辑/自动保存、版本历史与恢复、全文搜索、标签与文件夹
- 协作权限：文档成员权限（OWNER/EDITOR/VIEWER）、在线成员与协作光标
- 沟通与协作：评论与回复、@提醒、文档内聊天、文件共享
- 任务与通知：任务分配/状态跟踪、通知中心、通知偏好设置
- 会议与共享：Jitsi 会议集成、屏幕共享状态管理
- 管理后台：用户管理、用户行为统计、审计日志、满意度统计

## 技术栈
- Java 17
- Spring Boot 4.0.1（Web MVC / WebSocket / Security / Validation）
- Spring Data JPA + MySQL 8.x
- Spring Session Data Redis（依赖已引入，默认未启用）

## 快速开始

### 环境要求
- JDK 17
- Maven 3.8+（或使用 `mvnw`）
- MySQL 8.x

### 初始化数据库
默认使用 `spring.jpa.hibernate.ddl-auto=update` 自动建表，亦可手动执行：

```sql
source db/schema.mysql.sql;
```

### 启动服务
Windows:
```bash
.\mvnw.cmd spring-boot:run
```

macOS/Linux:
```bash
./mvnw spring-boot:run
```

启动后访问：`http://localhost:8080`

## 配置说明
主要配置位于 `src/main/resources/application.properties`：

| 配置项 | 说明 |
| --- | --- |
| `spring.datasource.url` | MySQL 连接串 |
| `spring.datasource.username` / `spring.datasource.password` | 数据库账号密码 |
| `spring.jpa.hibernate.ddl-auto` | 默认 `update`，生产建议改为 `validate/none` |
| `docflow.meeting.jitsiBaseUrl` | Jitsi 服务地址（默认 `https://meet.jit.si`） |
| `spring.servlet.multipart.max-file-size` | 上传限制（默认 10MB） |
| `docflow.bootstrap.*` | 管理员引导账号配置 |
| `logging.file.name` | 日志文件路径（默认 `logs/docflow.log`） |

## 管理员引导
项目支持首次启动创建管理员账号，需在配置中开启：

```properties
docflow.bootstrap.enabled=true
docflow.bootstrap.admin-email=admin@example.com
docflow.bootstrap.admin-password=admin123
docflow.bootstrap.admin-nickname=admin
```

首次启动若系统中不存在管理员账号，将自动创建。

## 认证方式
登录/注册成功后会返回 token，后续请求支持以下方式携带：
- `Authorization: Bearer <token>`
- `X-Auth-Token: <token>`

WebSocket 连接也支持通过 `?token=` 传递。

## WebSocket 协作
WebSocket 地址：`/ws`

典型用途：在线成员、协作光标、文档变更同步、通知推送、会议/文件共享事件等。

## 项目结构
- `src/main/java/com/docflow`：后端代码
- `src/main/resources/static`：前端静态页面与资源
- `db/schema.mysql.sql`：MySQL 初始化脚本
- `uploads/`：本地上传文件存储（头像/共享文件）
- `logs/`：应用日志

## 注意事项
- 认证 token 目前为内存存储（重启会失效），多实例部署需替换为持久化方案。
- 屏幕共享状态与在线协作状态为内存数据，重启后会清空。
- 上传文件保存在本地 `uploads/` 目录，生产环境建议接入对象存储。
