# patra-notification

通知服务：v0.16 起接管站内提醒、提醒偏好与一键退订、邮件（安全邮件与提醒邮件）、实时推送。interaction 与 identity 只发「发生了什么」的集成事件，由本服务决定提醒谁、怎么送达（release spec `docs/patra/release-specs/v0.16-notification.md` 决策 A）。

工程设计：`docs/patra/specs/2026-10-30-notification-skeleton-design.md`（服务骨架、子域划分、存储与部署接入）；`docs/patra/specs/2026-10-31-notification-preference-unsubscribe-design.md`（提醒偏好与一键退订，PAP-142）；`docs/patra/specs/2026-10-31-notification-inbox-design.md`（信箱接管站内提醒，PAP-143）；`docs/patra/specs/2026-10-31-notification-helpful-design.md`（有帮助提醒，PAP-144）。

## 1. 三个概念

- **六层**：domain（纯 Java 业务规则）→ app（编排用例、划事务）→ infra（实现领域层定义的接口：SQL、调别的服务）/ adapter（REST、消息监听、定时任务）；api 是给别的服务调用的契约，boot 是启动与装配。外圈依赖里圈，domain 层的纯净由构建插件强制。
- **子域**：服务内部按业务切出的一块，每层里都先按子域分包。子域之间只经对方的公开包交互，跨子域的依赖要在架构测试里声明，所以哪天某个子域要拆成独立服务时，它和别人之间只剩几条声明过的边。
- **底座**：每个服务一个的特殊子域，对其他子域完全公开、谁都能用；它不依赖任何别的子域，而且只放被两个以上子域共用的东西。

## 2. 子域

| 子域 | 职责 | Issue |
|---|---|---|
| `kernel`（底座） | 提醒类别 `NotificationCategory`（回复我的 / @ 我的 / 有帮助，有帮助没有邮件）；收件信息端口 `RecipientPort`（调 identity 的批量收件信息：邮箱、是否已验证、账号状态、用户名与显示名）；注销清理：扩展点 `UserDataCleanupGateway`、底座流程 `CleanupDeletedUsersHandler`（XXL `deletedUserCleanupJob`，每天 06:30）、账号状态端口 `AccountStatePort`（调 `/summaries`）；名片端口 `UserCardPort`（调 `/summaries`，带访客算主页可见性）；对象视图端口 `SubjectViewPort`（调 interaction 的两个内部接口）；摘录渲染 `ExcerptRenderer` 与提及名字规则 `MentionNames`；在站信号 `PresenceGateway`（决策 H，存 `kernel.presence`） | PAP-142 起，随用到的 Issue 补 |
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

### inbox：信箱

- 一个队列 `notification.inbox`（死信 `.dlx` / `.dlq`），绑定 interaction 的评论发表 / 编辑 / 删除、经验删除、有帮助已点，单消费线程顺序处理。每个事件一个事务：先 `ProcessedEventPort` 按事件 ID 去重，再写业务。
- 表：`inbox.notification`（一行一个收件人一条提醒，按收件人哈希分 16 区；列表按可变的排序时间 `occurred_at` 加 ID 翻页；`created_at` 是写入信箱的时刻，提醒邮件的时间窗口从它算）、`inbox.comment`（每条评论最新版本的纯文本与墓碑：版本更新才覆盖正文，删除后变墓碑，之后这条评论的事件都丢弃）、`inbox.subject_tombstone`（已删除的投稿经验）、`inbox.helpful_actor`（有帮助点过的人，见下一条）。
- 有帮助（PAP-144）：按「被点的内容 × 北京时间一天」合成 `inbox.notification` 的一行（`category = HELPFUL`，类型 `COMMENT_HELPFUL` / `POST_HELPFUL`），那一天由事件里的点击时刻 `markedAt` 换算，不看处理时刻。点过的人与各自第一次点的时刻在附表 `inbox.helpful_actor`（级联外键随信箱行删除）：人数 = 附表行数，最近一位 = 第一次点得最晚的人，新的人让条目重新未读；同一个人取消后再点不算又有人点，结果与消息到达顺序无关。点评论的摘录走评论快照（没有快照时用事件正文补一行版本 0），点经验的存在条目行的 `post_plain_text`（事件时刻最晚者为准）。不进铃铛、不发邮件、不发「提醒生成」。
- 收件人规则 `RecipientRules`（搬自 interaction）：先被回复的人、再经验作者（只对经验下的根评论）、最后 @ 到的人，同一人取第一次出现的类型，去掉作者本人；编辑只给第一次 @ 到的人发，并去掉发表负责的那两类人，结果与到达顺序无关。
- 读时组合：列表的对象信息（链接 key、标题、期刊、是否还在、经验结果）调 interaction 现查，挂了降级为不带对象信息；名片调 identity，挂了 503。
- 给别的子域的：读端口 `InboxReadPort`（列表、三个未读数），领域事件 `UnreadCountsChangedEvent`（实时推送提交后订阅）、`NotificationsCreatedEvent`（提醒邮件同事务订阅）。

### realtime：实时推送

设计：`docs/patra/specs/2026-10-31-notification-realtime-push-design.md`（第 5 节是给门户的契约）。

- 接口 `GET /me/notifications/stream`（经网关 `/patra-notification/me/notifications/stream`，网关就要求登录、走推送专用连接池）：SSE，只推三个未读数。连上立刻推一次当前值（带 `retry: 3000`），之后每次未读数变化推一条 `unread-count`，数据与未读数接口同形；每 25 秒一行注释 `:ping`；没有 `Last-Event-ID`。
- 连接表 `UnreadCountStreamRegistry`（app）管「用户 → 连接」，不认识 SSE；`SseEmitter` 在 adapter 里实现 `UnreadCountStream`。推送读数放在每个用户的锁里，并发推送时最后一条总是最新值；写不进去的连接剔除。
- 提交后推：`UnreadCountPushListener` 订阅 inbox 的 `UnreadCountsChangedEvent`（`AFTER_COMMIT` + `@Async`，换到 Boot 的 `applicationTaskExecutor`），回滚不推；不进事件登记表。
- 寿命与超时：`SseEmitter` 的超时就是 30 分钟寿命（也是显式设的异步超时，不设是 Tomcat 默认的 30 秒），到点正常结束，浏览器 3 秒后重连、网关借机重新查会话。
- 在站：连接建立时与每轮心跳给在线的人记一次（`PresenceGateway`，决策 H）。
- 停机：`ContextClosedEvent` 时结束所有连接，优雅停机不用等它们。
- 配置：`patra.notification.realtime.heartbeat-interval`（25s）、`max-connection-age`（30m），在 jar 的 `application.yml`；只有一个实例（连接在内存里）。

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
| 定时任务 | 执行器 `patra-notification`：注销清理 `deletedUserCleanupJob`（PAP-142，每天 06:30，含偏好、信箱、在站记录）；信箱保留期 `inboxRetentionJob`（PAP-143，每天 06:45，北京时间 5 个自然月）；已处理事件清理 `processedEventPurgeJob`（事件模块提供，每天 07:00，删 30 天前的去重记录）；离站邮件、每日汇总随各 Issue 加 |
| 调用的服务 | identity 内部接口 `POST /_internal/users/recipients`（收件信息）与 `POST /_internal/users/summaries`（注销清理取账号状态、列表取名片），`lb://patra-identity`；interaction 内部接口 `POST /_internal/subjects/views`（对象视图）与 `POST /_internal/posts/outlines`（经验结果），`lb://patra-interaction`；都是连接 1 秒、读 2 秒 |
| 消费的事件 | interaction 的 `interaction.events`（队列 `notification.inbox`） |
| 上线 | `docs/patra/runbooks/v0.16-notification-go-live-runbook.md` |

## 5. 邮件渠道（mail）

全站只有通知服务发信（PAP-105）。工程设计：`docs/patra/specs/2026-10-31-notification-mail-channel-design.md`。

- **安全邮件**：identity 的 13 类事件（`identity.events` / `security-mail.*`）进队列 `notification.security-mail`，死信对 `.dlx` / `.dlq`。事件带收件地址、链接与事实，主题与文案在 `SecurityMailKind`，模板在 infra 的 `resources/mail/`。不进偏好、不能退订、不带退订头。
- **一封信怎么处理**：按投递 ID（安全邮件就是事件 ID）查发送记录 → 只为链接而发的信链接过期就不发 → 事务外经 SMTP 发出 → 短事务里 `markProcessed` 并记发送记录。发信失败分三类（spec 6.6）：服务商已接受过这个幂等键按已发记；永久拒收记 `REJECTED`、不重试；临时失败交给监听容器本地重试 3 次，之后进死信。
- **监听收原始消息**：自己按类型头还原，不是安全邮件事件就拒收进死信；不让 Spring 按参数类型转，免得把正文（地址、令牌链接）写进异常日志。
- **幂等键**：每封带 `Resend-Idempotency-Key`（投递 ID），`Message-ID` 也按投递 ID 固定。同一个投递 ID 必须渲染出同一份内容，正文里不放随发送时刻变的东西（有效期写成绝对时刻）。
- **发送记录** `mail.mail_delivery`：投递 ID、模板名、结果、时刻；不存正文、链接、收件地址与用户 ID。
- **配置**：SMTP 是 Nacos 里的 `spring.mail.*`（默认 Mailpit，mini 由 `~/.patra/secrets/mail.env` 覆盖成 Resend）；发件人 `patra.notification.mail.from`；显示时区 `patra.notification.mail.zone`（jar，`Asia/Shanghai`）。邮件健康检查关掉。
- **提醒邮件（PAP-146）复用**：`MailDeliveryPort` + `OutgoingMail`（退订头放 `headers`）；投递 ID 要由「这封信是什么」确定地算出来；发送记录各管各的（spec 第 11 节）。
