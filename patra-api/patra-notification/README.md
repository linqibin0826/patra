# patra-notification

通知服务：v0.16 起接管站内提醒、提醒偏好与一键退订、邮件（安全邮件与提醒邮件）、实时推送。interaction 与 identity 只发「发生了什么」的集成事件，由本服务决定提醒谁、怎么送达（release spec `docs/patra/release-specs/v0.16-notification.md` 决策 A）。

工程设计：`docs/patra/specs/2026-10-30-notification-skeleton-design.md`（服务骨架、子域划分、存储与部署接入）；`docs/patra/specs/2026-10-31-notification-preference-unsubscribe-design.md`（提醒偏好与一键退订，PAP-142）。

## 1. 三个概念

- **六层**：domain（纯 Java 业务规则）→ app（编排用例、划事务）→ infra（实现领域层定义的接口：SQL、调别的服务）/ adapter（REST、消息监听、定时任务）；api 是给别的服务调用的契约，boot 是启动与装配。外圈依赖里圈，domain 层的纯净由构建插件强制。
- **子域**：服务内部按业务切出的一块，每层里都先按子域分包。子域之间只经对方的公开包交互，跨子域的依赖要在架构测试里声明，所以哪天某个子域要拆成独立服务时，它和别人之间只剩几条声明过的边。
- **底座**：每个服务一个的特殊子域，对其他子域完全公开、谁都能用；它不依赖任何别的子域，而且只放被两个以上子域共用的东西。

## 2. 子域

| 子域 | 职责 | Issue |
|---|---|---|
| `kernel`（底座） | 提醒类别 `NotificationCategory`（回复我的 / @ 我的 / 有帮助，有帮助没有邮件）；收件信息端口 `RecipientPort`（调 identity 的批量收件信息：邮箱、是否已验证、账号状态、用户名与显示名）；注销清理：扩展点 `UserDataCleanupGateway`、底座流程 `CleanupDeletedUsersHandler`（XXL `deletedUserCleanupJob`，每天 06:30）、账号状态端口 `AccountStatePort`（调 `/summaries`）；在站信号（决策 H，随 PAP-143 / 146） | PAP-142 起，随用到的 Issue 补 |
| `inbox` | 站内信箱：消费 interaction 的事件写提醒，收件人规则，去重与乱序防护，未读数、列表、已读、删除联动、5 个月保留；有帮助按「内容 × 北京时间一天」合并 | PAP-143、PAP-144 |
| `preference` | 每类提醒的邮件档位（关 / 离站逐条 / 每日汇总）、退订令牌、RFC 8058 一键退订、退订落地页数据 | PAP-142 |
| `mail` | 渠道（模板、SMTP 与幂等键、重试与死信、发送记录，发安全邮件）与提醒邮件（离站逐条、每日汇总、退订头）。收件地址经底座的 `RecipientPort` 取（preference 的退订落地页也要邮箱，所以进了底座） | PAP-105、PAP-146 |
| `realtime` | SSE 连接表、提交后推三个未读数、心跳与连接时长上限 | PAP-145 |

普通子域的公开包是 `domain.{子域}.port.read`、`domain.{子域}.model.read`、`domain.{子域}.event`；底座的一切都公开。规则由 `NotificationArchitectureTest`（`patra-tool-test` 的 `SubdomainArchitectureRules`）守住，分包模板见 `.claude/rules/tech/package-layout.md`。

**计划中的子域依赖**（无环，由引入依赖的 Issue 在架构测试里用 `allowingDependency` 声明）：

| 边 | 用途 |
|---|---|
| mail → inbox | 订阅「提醒生成」排入离站邮件；到点复查已读；每日汇总取未读 |
| mail → preference | 查邮件档位；取退订令牌拼 `List-Unsubscribe` |
| realtime → inbox | 订阅未读数变化（提交后推）；连接建立时取当前未读数 |

inbox 不依赖 preference：偏好只管邮件，站内提醒照常。

### preference：偏好与退订

- 每个用户一行 `preference.email_preference`：「回复我的」「@ 我的」各一个档位（`OFF` / `INSTANT` / `DIGEST`），加一个长期有效的退订令牌（32 字节随机数的 base64url，存原文：每封提醒邮件都要把它拼进退订链接）。没有行 = 默认值（两类都是 `INSTANT`），第一次改档位、或 mail 第一次为他取令牌时才建行。
- 接口：`/me/notification-preferences`（需登录，按类单独 `PUT`）；`/unsubscribe`（免登录：`GET` 落地页数据只读、`POST` 落地页确认）；`/unsubscribe/one-click`（免登录，RFC 8058 一键退订，不看请求体、不重定向）。链接无效一律 404 + `UNSUBSCRIBE_LINK_INVALID`。
- 退订按「设成关」实现，重复调用结果相同；写用例锁行后按列写回，同一个人的并发修改不会互相覆盖。
- 给 mail 的公开读端口 `EmailSubscriptionReadPort.subscriptionOf(userId)`：档位与令牌，没有行时建一行。邮件里的两个退订地址都在门户上（网关不对公网），格式见 spec 第 7 节。

「已处理事件」表（决策 D）不属于任何子域：表结构按事件模块（PAP-137），库里只建一张，inbox 与 mail 的消费者共用，谁先落地谁建。

## 3. 存储

- 库 `patra_notification`（现有 PG 实例），每个子域一个 schema，schema 名就是子域名，随该子域的第一张表在迁移脚本里 `CREATE SCHEMA`；不建没有表的空 schema。
- 整个服务一份 Flyway 历史（`public.flyway_schema_history`）。脚本放 `patra-notification-infra/src/main/resources/db/migration/{子域}/`，版本号是写脚本时的时间戳 `yyyyMMddHHmmss`。不设 `spring.flyway.schemas`、不设 Hibernate 默认 schema，实体写 `@Table(schema = "…")`。
- `set_updated_at()` 触发器函数每个 schema 一份，随第一张要它的表建；每次写入都显式给 `updated_at` 的表不挂（同 interaction 的 `collection.collection`），如 `preference.email_preference`。表里只存用户 ID，不存用户名（R6）。

## 4. 运行

| 项 | 值 |
|---|---|
| 端口 | 6600；XXL-Job 执行器 6601（不映射到宿主机） |
| 实例数 | 一个（release spec 第 3 节 C：SSE 连接挂在实例内存里） |
| 网关前缀 | `/patra-notification/**`（`/actuator/**` 经网关一律 403） |
| 错误码前缀 | `NOTIFICATION`（如 `NOTIFICATION-0404`） |
| 配置 | jar 里的 `application.yml` 放随代码变的默认值；库地址与 RabbitMQ 密码引用在 Nacos `patra-notification.yaml`，库账号、RabbitMQ 坐标与 xxl-job 管理端地址在 `patra-common.yaml` |
| 密钥 | `~/.patra/secrets/nacos.env`、`rabbitmq.env`（都必需） |
| 定时任务 | 执行器 `patra-notification`：注销清理 `deletedUserCleanupJob`（PAP-142）；提醒清理、离站邮件、每日汇总随各 Issue 加 |
| 调用的服务 | identity 内部接口 `POST /_internal/users/recipients`（收件信息）与 `POST /_internal/users/summaries`（注销清理取账号状态），`lb://patra-identity`，连接 1 秒、读 2 秒 |
| 上线 | `docs/patra/runbooks/v0.16-notification-go-live-runbook.md` |
