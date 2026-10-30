# 通知服务邮件渠道工程设计（PAP-105）

> 依据：release spec `docs/patra/release-specs/v0.16-notification.md` 决策 C、D、E、F、G 与 Done D2、D6；
> identity 事件契约（PAP-104，`docs/patra/specs/2026-10-31-identity-security-mail-events-design.md` 第 4.6 节，共享笔记 `PAP-104-events-contract.md`）；
> 事件模块 `patra-tool/patra-tool-events/README.md`；骨架 spec `docs/patra/specs/2026-10-30-notification-skeleton-design.md`；
> 编排会话 10/31 的两条拍板（见第 4 节）。
> 下游：PAP-146（提醒邮件，第 11 节是给它的用法）。

## 1. 要解决的问题

v0.16 之前，identity 自己发 13 类安全邮件（验证邮箱、找回密码、改邮箱、注销……）：进程内队列，失败不重试，重启就丢。PAP-104 把它改成「请发这封信」的集成事件，经 RabbitMQ 交给通知服务。本 Issue 让通知服务的 `mail` 子域成为**全站唯一的发信出口**：

- 收 identity 的 13 类事件，照着发出去；
- 每封带 `Resend-Idempotency-Key`，同一个事件重投、重试都不会让用户收到两封；
- SMTP 不通时退避重试，超过上限进死信，日志里查得到；
- 记一张发送记录表，不存正文、不存令牌链接、不存收件地址；
- 模板与文案从 identity 搬来；
- 发信端口做成通用的，PAP-146 的提醒邮件直接复用。

## 2. 先讲清几个概念

### 2.1 幂等键：让邮件服务商帮你挡重复

- **是什么**：发信时带一个「这封信的身份证号」。服务商记住它，24 小时内再收到同一个号、内容也一样，就不再真的发，直接回「已发过」。Resend 在 HTTP 接口用 `Idempotency-Key` 头，在 SMTP 里用邮件头 `Resend-Idempotency-Key`，1～256 个字符。
- **防什么**：我们这边「发出去了但没来得及记下来」的情况（2.3）。只有服务商那一侧知道信到底发没发，所以这一层只能由它挡。
- **业界**：Stripe、Resend、Postmark 都提供幂等键；做法一致——键由调用方生成，同一笔业务永远用同一个键。
- **一个容易踩的坑**：Resend 文档写明，同一个键配**不同内容**会被拒（`409 invalid_idempotent_request`）。所以同一封信每次渲染出来必须**一模一样**：正文里不能有「发送时刻」相关的东西（比如「还剩 23 分钟有效」）。见 2.5。

### 2.2 至少一次投递，为什么要两层去重

- RabbitMQ 和事件模块都是**至少一次**：同一个事件可能到两次（发布方补投、消费方处理完还没 ack 就断了）。
- 第一层：通知服务自己记「这个事件处理过了」（`processed_event` 和本 Issue 的发送记录），第二次到达时直接跳过。
- 第二层：第一层记录还没提交进程就死了，重投时会再发一次，由幂等键挡住（2.1）。
- 两层缺一不可：只有第一层，崩溃窗口里会重复；只有第二层，超过 24 小时的重复挡不住、本机 Mailpit 也没有这个功能。

### 2.3 外部副作用不能放进数据库事务

- `transaction.md` 第 2 节：事务里不许放远程调用、发 MQ、发邮件。原因：SMTP 一次几百毫秒，服务商卡住时可能十几秒，期间数据库连接一直被占；而且事务回滚撤不回已经发出去的邮件，放进去也换不来「同成同败」。
- 所以顺序是：**先查有没有发过 → 发（事务外）→ 一个很短的事务里记「处理过了」和发送记录**。
- 「发出后、记下前进程死了」：重投时查不到记录，会再发一次，同一个幂等键，Resend 挡掉（2.2 第二层）。这是有意接受的窗口。

### 2.4 死信

- 重试几次仍失败的消息，被 RabbitMQ 转进另一个队列（`<队列>.dlq`）留着，不再堵住后面的消息，也不会自己回来。
- 事件模块的约定：本地重试 3 次（间隔 1、2、4 秒），之后进死信；需要时在 RabbitMQ 管理界面把它挪回原队列重放。

### 2.5 确定性渲染

- 同一个事件不管渲染几次、什么时候渲染，主题、纯文本、HTML 都一字不差。只依赖事件里的事实和不随时间变的配置（站名、显示时区）。
- 这是和 Resend 的隐含契约（2.1 的坑），用单测守住：同一封渲染两次逐字节相等。
- 因此**有效期写成绝对时刻**：「2026-10-31 10:30 (UTC+8) 前有效」，而不是 PAP-104 契约里建议的「按发送时刻算剩余时间」。剩余时间在重试之间会跨过分钟边界（29 分钟 → 28 分钟），内容一变，Resend 就会 409。绝对时刻还有一个好处：信晚到时，用户看得出链接是不是已经过期。

## 3. 范围

**目标**

- mail 子域：领域模型、发信端口、发送记录仓储；app 用例；SMTP 适配器与 Thymeleaf 渲染器；RabbitMQ 监听；Flyway 建 `mail.mail_delivery`。
- 13 份模板（HTML + 文本）与片段从 identity（`b047dec36`）原样搬来，只改有效期写法（2.5）。
- 队列 `notification.security-mail` 绑定 `identity.events` / `security-mail.*`，死信对随事件模块的 `EventQueue`。
- 配置：Nacos `patra-notification.yaml` 加 `spring.mail.*` 与发件人；jar 配置加显示时区、关邮件健康检查、`trusted-packages`。
- compose：notification 块加 `mail.env`，identity 块去掉；`mail.env.example`、`patra-infra/docker/README.md` 改说法。
- 上线步骤追加到 `docs/patra/runbooks/v0.16-notification-go-live-runbook.md`。

**非目标**

- identity 侧的任何改动：发信代码、模板、`spring.mail.*`、mail 依赖由 PAP-104 删（第 4 节拍板 1）。
- 提醒邮件、退订头、门户地址配置（PAP-146）。
- 退信与投诉回调（release spec 第 3 节 G）、死信告警与重放工具（决策 E 的代价）。
- `processed_event` 与发送记录的过期清理（量很小，事件模块 spec 第 9 节）。

**反目标**（技术上能跑，但算失败）

- 发送记录或日志里出现令牌链接、收件地址、正文。
- 同一个事件在重试之间渲染出不同的内容（会被 Resend 409，见 2.1）。
- 在数据库事务里发信。
- 为「以后可能有用」先写 PAP-146 才用得到的代码（退订头、门户地址、汇总）。

## 4. 已定的前提

| 来源 | 内容 |
|---|---|
| 决策 D | 消费端按事件 ID 去重；邮件发出后、记录提交前崩溃由幂等键兜底 |
| 决策 E | 每个消费用途一个队列；失败本地退避重试，超过上限进死信；prefetch 小 |
| 决策 F | 安全邮件的收件地址与链接随事件带来；通知服务不存邮箱副本 |
| 决策 G | SMTP；本机与 e2e 用 Mailpit，线上用 Resend；每封带 `Resend-Idempotency-Key`；安全邮件不带退订头 |
| PAP-104 契约 | 13 个 record 在 `dev.patra.identity.api.account.event`，sealed `SecurityMailEvent`（`eventId`、`to`、`displayName`）；路由键 `security-mail.<模板名>`；一个事件一封信；主题、站名、「不是我怎么办」、有效期写法、显示时区归通知服务 |
| 编排会话拍板 1 | identity 的发信代码、模板、mail 依赖与配置、IT 里的 GreenMail 由 PAP-104 删；本 Issue 从 `b047dec36` 拷模板，不改 identity |
| 编排会话拍板 2 | `processed_event`、`event_publication`（`db/migration/kernel/`）与 boot 的 `patra-tool-events` 依赖已在 `a1eedc4d9` 接好，本 Issue 复用 |
| 编排会话拍板 3（本 Issue 改判） | 当时约定「门户地址配置由 PAP-105 先加、PAP-146 复用」，前提是安全邮件的 `securityUrl` 要用它。PAP-104 的契约把 `securitySettingsUrl` 放进了用到它的三个事件，notification 侧不再需要门户地址，本 Issue 不加；PAP-146 拼回复链接、退订链接时自己加（PAP-142 spec 第 7 节本来就这样写）。10/31 已告知编排会话 |
| 事件模块约定 | `ProcessedEventPort.markProcessed` 在业务事务里调；`EventQueue` 声明队列与死信对；AUTO 确认、重试 3 次（1/2/4 秒）、prefetch 10；按类型头分发时写 `trusted-packages` |

**PAP-104 的 record 还没进集成分支**：本分支按契约先写一份同形状的 13 个 record 与 `SecurityMailEvent`、`OperationContext`，放在**单独一个提交**（`4f2626237`，`patra-identity-api` 的 `dev.patra.identity.api.account.event`）里，字段名与类型以这个提交为准（`linkExpiresAt` 与 `deletionDueAt` 是 `Instant`，`daysLeft` 是 `long`）。PAP-104 合入后整体丢弃、以它的为准。主代码只用 accessor，测试里构造 record 的地方届时按真实签名微调。

## 5. 一封安全邮件的流程

```
identity ──(identity.events, security-mail.*)──▶ 队列 notification.security-mail
                                                    │  prefetch 10，AUTO ack
                                                    ▼
adapter  SecurityMailListener.on(SecurityMailEvent)        ← 按 __TypeId__ 反序列化成具体 record
         └─ SecurityMailEventConverter：sealed switch → 领域的 SecurityMail
         └─ commandBus.handle(SendSecurityMailCommand)
app      SendSecurityMailHandler
  1. deliveries.exists(eventId)？            是 → 返回 DUPLICATE（ack）
  2. 只为链接而发的信且链接已过期？           是 → 短事务：markProcessed + 记 LINK_EXPIRED → 返回
  3. mailDelivery.deliver(mail.compose())     事务外；失败抛 MailDeliveryFailedException
  4. 短事务：markProcessed(队列, eventId) 为 true 时记 SENT
infra    SmtpMailDeliveryAdapter：Thymeleaf 渲染 → MimeMessage（固定的头）→ JavaMailSender.send
失败     异常抛回监听容器 → 本地重试 1、2、4 秒 → 仍失败：拒绝、不回队列 → <队列>.dlq，Spring AMQP 打 WARN
```

各步崩溃时会怎样：

| 崩在哪 | 重投后 | 用户收到 |
|---|---|---|
| 第 1 步之前 | 从头来 | 1 封 |
| 第 3 步发出前 | 从头来 | 1 封 |
| 第 3 步发出后、第 4 步提交前 | 第 1 步查不到记录，再发一次，同一个幂等键 | Resend：1 封；Mailpit：2 封（本机可接受） |
| 第 4 步提交后、ack 前 | 第 1 步查到记录，跳过 | 1 封 |
| 发布方补投了一个已送达的事件 | 第 1 步查到记录，跳过 | 1 封 |

**为什么第 1 步查发送记录、不查 `processed_event`**：`ProcessedEventPort` 只有「记下」没有「查询」，它在 `patra-tool-core`，不为本 Issue 改共享接口；发送记录本来就是邮件渠道的事实，PAP-146 的定时邮件没有事件、也要按它查重（第 11 节）。第 4 步仍按 `transaction.md` 第 3 节调 `markProcessed`：它是全服务统一的「消费过」账本，上线核对也看它。两张表在同一个短事务里写，永远同时有或同时没有。

**链接过期**：只为链接而发的三类（验证邮箱、找回密码、改邮箱确认）在链接过期后不发：用户点了也只会看到「链接已失效」，不如让他重新申请。记 `LINK_EXPIRED`、打 INFO。安全通知类（邮箱已更改、邮箱已改回等）即使带的链接过期了也照发：通知本身才是重点，有效期写成绝对时刻，用户看得出链接已过期。事件晚到（broker 或通知服务停了很久）、死信重放时都走这条规则。

## 6. 模型、端口与落点

根包 `dev.patra.notification`，分包按 `.claude/rules/tech/package-layout.md`。mail 子域本 Issue 不依赖任何别的子域（不声明依赖边）。

### 6.1 domain（`domain.mail`）

| 包 | 类 | 说明 |
|---|---|---|
| `model.vo` | `OutgoingMail` | 一封要发的信：`deliveryId`（UUID，就是幂等键）、`to`、`template`（模板名）、`subject`、`model`（模板变量，不可变；值不得为 null）。`@Builder`。`toString()` 只输出 `deliveryId` 与 `template` |
| `model.vo` | `OperationContext` | 触发安全通知的那次操作：`occurredAt`（非空）、`device`、`ipAddress`、`ipLocation`（可空） |
| `model.vo` | `SecurityNotice` | 安全通知片段的内容：`OperationContext` + 这一类的「不是我怎么办」 |
| `model.vo` | `SecurityMail` | 一封安全邮件的事实：`eventId`、`kind`、`to`、`displayName`、`facts`（这一类模板专属的变量，值非空）、`context`（可空）、`linkExpiresAt`（可空）。`compose()` → `OutgoingMail`；`linkExpiredAt(Instant now)` |
| `model.enums` | `SecurityMailKind` | 13 类：模板名、主题、「不是我怎么办」（没有安全通知片段的为 null）、`skipsWhenLinkExpired`。注销提醒的主题按 `daysLeft` 拼 |
| `model.enums` | `MailDeliveryOutcome` | `SENT`、`LINK_EXPIRED` |
| `model.aggregate` | `MailDelivery` | 发送记录：`deliveryId`、`template`、`outcome`、`recordedAt`；`sent(...)`、`linkExpired(...)` |
| `port.delivery` | `MailDeliveryPort` | `void deliver(OutgoingMail mail)`：同步发出，失败抛 `MailDeliveryFailedException`。**契约**：同一个 `deliveryId` 必须对应同一份内容；实现把它写进 `Resend-Idempotency-Key` |
| `port.repository` | `MailDeliveryRepository` | `boolean exists(UUID deliveryId)`、`void add(MailDelivery delivery)` |
| `exception` | `MailDeliveryFailedException` | `DomainException` + `DEP_UNAVAILABLE`；消息含 `template`、`deliveryId` 与一段**脱敏后的原因**（见下）；**不挂原始异常为 cause** |

**为什么不挂 cause**：服务商拒收收件人时，SMTP 回复会带上地址（如 `550 5.1.1 <chen@example.com>: Recipient address rejected`），JavaMail 的 `SendFailedException` 也会列出地址。Spring AMQP 重试耗尽时的 WARN 会把整条 cause 链连同消息打出来，地址就进了日志。所以适配器把 cause 链压成一段文字：每一层的异常类名加消息，邮箱地址替换成 `***`，最长 500 个字符，放进异常消息；原始异常不往上挂。SMTP 回复码和服务商的原文（比如 Resend 拒绝同键不同内容时的说明）都还在，排查第 15 节的 409 靠它。

`facts` 的值不允许 null：`Map.copyOf` 遇到 null 会抛 NPE。可空的设备、IP、属地只放在 `OperationContext` 里，由渲染器换成「未知」（同 identity 现在的写法）。

### 6.2 app（`app.mail.usecase.security`）

- `SendSecurityMailCommand(String consumer, SecurityMail mail)`：`consumer` 是队列名，由监听方给出，用于 `markProcessed`。
- `SendSecurityMailHandler implements CommandHandler<…, SendSecurityMailResult>`：第 5 节的四步；注入 `MailDeliveryRepository`、`MailDeliveryPort`、`ProcessedEventPort`、`TransactionOperations`、`Clock`。不标 `@Transactional`（规则 16b），事务只包第 2、4 步的写库。日志只打 `eventId` 与 `kind`。
- `SendSecurityMailResult`：`SENT`、`DUPLICATE`、`LINK_EXPIRED`。

### 6.3 infra（`infra.mail`）

| 包 | 类 | 说明 |
|---|---|---|
| `delivery` | `SmtpMailDeliveryAdapter` | `MailDeliveryPort` 的实现：渲染 → `MimeMessageHelper`（`multipart/alternative`，UTF-8）→ 发件人、收件人、主题、文本 + HTML → 头 → `JavaMailSender.send`。头：`Resend-Idempotency-Key: <deliveryId>`、`Auto-Submitted: auto-generated`、`X-Auto-Response-Suppress: All`。`MessagingException` / `MailException` / 渲染异常 → `MailDeliveryFailedException`（脱敏原因，见 6.1）。不打日志（由 app 打） |
| `delivery` | `ThymeleafMailRenderer`、`RenderedMail`、`SecurityNoticeView` | 从 identity 搬来；去掉 `MailLinkPolicy`（`securityUrl` 已随事件进 `model`）；公共变量 `siteName`、`subject`；`Instant` 一律格式化成 `2026-11-04 08:00 (UTC+8)`；`notice` 是 `SecurityNotice` 时换成 `SecurityNoticeView` |
| `persistence` | `MailDeliveryRepositoryAdapter` | `JdbcClient`：`SELECT EXISTS` 与 `INSERT`；`add` 标 `MANDATORY`（只能在 app 的短事务里调） |
| `config` | `MailDeliveryProperties`、`MailDeliveryConfiguration` | `patra.notification.mail.from`（只能一个合法地址，否则启动失败）、`patra.notification.mail.zone`；装配渲染器与适配器 |
| 资源 | `mail/html/**`、`mail/text/**` | 13 份模板 + 片段，从 identity 搬来（第 7 节） |

依赖：infra `implementation("org.springframework.boot:spring-boot-starter-mail")`、`implementation("org.thymeleaf:thymeleaf")`（只要引擎，不要 starter 带的 MVC 视图解析器，同 identity 的理由）。

### 6.4 adapter（`adapter.mail.mq`）

- `SecurityMailListener`：`@RabbitListener(queues = QUEUE)`，一个方法 `on(SecurityMailEvent event)`；常量 `QUEUE = "notification.security-mail"`。参数是 sealed 接口，转换器按 `__TypeId__` 头反序列化成具体 record，只认 `trusted-packages` 里的包。
- `SecurityMailEventConverter`：对 `SecurityMailEvent` 的 13 个分支穷举 `switch`（漏一类编译不过），每类给出 `kind`、`facts`、`context`、`linkExpiresAt`。
- 依赖：`implementation(project(":patra-api:patra-identity:patra-identity-api"))`、`implementation("org.springframework.boot:spring-boot-starter-amqp")`；app 经 `api(project(app))`（与 PAP-142 写法一致）。

**单方法 + 接口参数能否反序列化是第一个要证明的事**（计划第一步的 IT）：Spring AMQP 推断出的参数类型是接口时，转换器退回按类型头。证明不了就退回类级 `@RabbitListener` + 每类一个 `@RabbitHandler`，`trusted-packages` 配置不变。

### 6.5 boot

- `config/MailQueueConfiguration`：`Declarables`：`EventQueue.named(SecurityMailListener.QUEUE).bind(SecurityMailEvent.EXCHANGE, SecurityMailEvent.ROUTING_KEY_PATTERN)`（事件模块 README 要求队列在 boot 声明）。
- `application.yml`：`patra.notification.mail.zone: Asia/Shanghai`；`management.health.mail.enabled: false`（同 identity：容器健康检查每 10 秒一次会变成一天八千多次登录服务商，SMTP 抖动也不该让 CD 回滚）；`patra.events.consumer.trusted-packages` 写成 YAML 列表，`- dev.patra.identity.api.account.event`（PAP-143 加 interaction 的包时只追加一行）。
- Nacos `patra-notification.yaml`：`spring.mail.*` 整块从 `patra-identity.yaml` 照搬（默认 Mailpit，`~/.patra/secrets/mail.env` 覆盖成 Resend），`patra.notification.mail.from: ${PATRA_MAIL_FROM:Patra <no-reply@patra.local>}`。环境变量名不变，mini 上的 `mail.env` 不用改内容。

## 7. 模板

从 `b047dec36` 的 `patra-identity-infra/src/main/resources/mail/` 原样拷到 `patra-notification-infra/src/main/resources/mail/`（`git show` 取，保证逐字一致），13 份 × 2 + 片段。只改：

| 模板 | 原来 | 改为 |
|---|---|---|
| `email-verification` | `${validHours} 小时内有效` | `${linkExpiresAt} 前有效` |
| `password-reset`、`email-change-confirm`、`email-change-reverted` | `${validMinutes} 分钟内有效` | `${linkExpiresAt} 前有效` |
| `email-changed-notice` | `${validDays} 天内有效` | `${linkExpiresAt} 前有效` |

「只能用一次」等其余措辞不动。`securityUrl` 变量名不变，值来自事件的 `securitySettingsUrl`（只有新设备登录、邮箱已更改、新邮箱已绑定三封用）。主题与「不是我怎么办」原文搬进 `SecurityMailKind`。

## 8. 发送记录表

`patra-notification-infra/src/main/resources/db/migration/mail/V{时间戳}__create_mail_delivery.sql`：

```sql
CREATE SCHEMA mail;

CREATE TABLE mail.mail_delivery
(
    delivery_id UUID                     NOT NULL PRIMARY KEY, -- 幂等键；安全邮件就是 identity 的事件 ID
    template    TEXT                     NOT NULL,             -- 哪一类信（模板名）
    outcome     TEXT                     NOT NULL CHECK (outcome IN ('SENT', 'LINK_EXPIRED')),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

- **不存正文、链接、收件地址、用户 ID**：决策 F 不存邮箱副本；表里没有任何个人数据，注销清理也就不用管它。要查「某人有没有收到找回密码的信」：在 identity 日志里按用户找到事件 ID，再按 `delivery_id` 查这张表。
- 发送失败不进表：失败的事务回滚了；失败看日志与死信队列。
- 只追加、不更新，没有 `updated_at`，不需要 `set_updated_at()`。

## 9. 队列、重试与死信

- 队列 `notification.security-mail`（持久化），死信交换机 `notification.security-mail.dlx`、死信队列 `notification.security-mail.dlq`，由 `EventQueue` 一并声明；绑定 `identity.events` / `security-mail.*`。
- 重试、prefetch、AUTO ack 全用事件模块的默认值，不给邮件单独配容器工厂。考虑过给邮件更长的退避（服务商的抖动常常超过 7 秒），没选：要多一个容器工厂与配置项；死信里的信可以重放，链接类过期就跳过（第 5 节），重放是安全的。代价写在第 15 节。
- 重试耗尽时 Spring AMQP 的 `RejectAndDontRequeueRecoverer` 打 WARN「Retries exhausted for message …」，带 `messageId`（就是事件 ID）与类型头。消息体超过 50 字节时 `Message.toString()` 不打印正文（spring-amqp 4.1.2 的 `DEFAULT_MAX_BODY_LENGTH`），链接与地址不会进日志；IT 断言这一点。WARN 里的 cause 是 `MailDeliveryFailedException`，只有脱敏后的原因（6.1）。每次发送失败，app 再打一条 WARN（事件 ID、类别、脱敏原因）。命令总线的日志拦截器只打命令类名与异常消息（也是脱敏后的）；`SecurityMail` 的 `toString()` 仍只输出事件 ID 与类别，以防别处打印它。
- 反序列化失败（类型不在 `trusted-packages` 里、JSON 坏了）：监听容器直接拒绝、不重试，进死信。

## 10. 配置与部署

- **compose**（`patra-infra/docker/docker-compose.apps.yaml`）：notification 块加 `- path: ${HOME}/.patra/secrets/mail.env` / `required: false`；identity 块删掉这两行。
  - 为什么仍是 `required: false`：没有它时发往 core 栈的 Mailpit，本机与 CI 的 compose 检查不用造假文件（`rabbitmq.env`、`nacos.env` 是必需的，测试已为它们建临时文件，不再加一个）。代价：mini 上漏放就会把真实用户的信发进 Mailpit。由 runbook 兜：部署前确认文件在，部署后 `docker exec patra-notification printenv PATRA_MAIL_HOST` 为 `smtp.resend.com`，再用测试账号走一遍找回密码。
- `mail.env.example` 头注释、`patra-infra/docker/README.md` 里「identity 读 mail.env」的说法改成 notification。
- mini 上的 `~/.patra/secrets/mail.env` 内容不变（变量名不变），只是换成 notification 容器读。

## 11. 给 PAP-146 的用法

提醒邮件和安全邮件共用：`MailDeliveryPort`、`OutgoingMail`、渲染器、发送记录、`Resend-Idempotency-Key`。PAP-146 要做的：

1. **模板**：在 `mail/html`、`mail/text` 下加自己的模板，模板名进 `OutgoingMail.template`，变量进 `model`（值非空）。渲染器不认识具体的信。
2. **幂等键（最重要）**：提醒邮件不是由某个事件触发的，没有现成的事件 ID。`deliveryId` 必须由「这封信是什么」**确定地**算出来，不能每次随机：
   - 离站逐条：`UUID.nameUUIDFromBytes(("reminder:" + userId + ":" + 类别 + ":" + 这一批里最早那条提醒的 ID).getBytes(UTF_8))`；
   - 每日汇总：`UUID.nameUUIDFromBytes(("digest:" + userId + ":" + 北京日期).getBytes(UTF_8))`。
   - 定时任务重跑、重试时算出同一个 ID：先 `MailDeliveryRepository.exists(id)` 跳过已发的，发完在短事务里 `add`。定时任务触发的信不经过 `processed_event`。
   - 同一个 ID 的内容必须一样（2.5）：正文只依赖这批提醒本身，不依赖发送时刻。
3. **退订头**：给 `OutgoingMail` 加一个 `headers`（`Map<String, String>`）分量；适配器先写这些头，再写 `Resend-Idempotency-Key`、`Auto-Submitted`、`X-Auto-Response-Suppress`，保证固定头不会被覆盖。本 Issue 不预先加（没有调用方）。
4. `MailDeliveryOutcome` 按需加值（比如「邮箱未验证不发」），同步改表的 `CHECK`。
5. 架构测试里声明 mail → inbox、mail → preference 两条边（骨架 spec 5.3）。

## 12. 测试（对照 AC）

| AC / 要求 | 测试 |
|---|---|
| 13 类安全邮件都由通知服务发出 | boot IT `SecurityMailFlowIT`：真 RabbitMQ + GreenMail + PG，向 `identity.events` 按路由键发 13 个 record（带类型头，和 identity 外送的形状一样），每类收到一封，收件人与主题对，`mail_delivery` 与 `processed_event` 各一行 |
| 同一次投递重试时幂等键不变 | 同一 IT：第一次记录时让仓储抛一次异常（信已发出），消息重试后 GreenMail 收到两封，`Resend-Idempotency-Key` 都等于事件 ID；渲染器单测：同一封渲染两次逐字节相等 |
| SMTP 临时不通按退避重试，超过上限进死信并有日志可查 | IT `SecurityMailRetryIT`：发信器第一次抛异常、第二次正常 → 收到一封，记录一行；IT `SecurityMailDeadLetterIT`（不挂 GreenMail，SMTP 指向关着的端口）→ 消息进 `notification.security-mail.dlq`，日志有「Retries exhausted」与事件 ID，日志里没有链接与收件地址 |
| 发送记录里没有邮件正文 | infra IT：`mail.mail_delivery` 的列恰好是四列；流程 IT 断言记录里任何列都不含链接、地址 |
| identity 不再依赖 SMTP | 由 PAP-104 交付（第 4 节拍板 1），本 Issue 的验收汇报指向它的提交 |
| 去重 | 流程 IT：同一个事件发两次只收到一封；handler 单测：已有记录返回 `DUPLICATE`、不调端口 |
| 链接过期 | handler 单测：三类只为链接而发的信过期后不发、记 `LINK_EXPIRED`；通知类照发 |
| 安全邮件不带退订头 | 流程 IT：收到的每封都没有 `List-Unsubscribe` 与 `List-Unsubscribe-Post` |
| 子域与分层 | `NotificationArchitectureTest` 原样通过（不新增依赖边） |

单测：`SecurityMail` / `SecurityMailKind`（13 类的主题、advice、compose 出的变量，`context` 全空时不 NPE）、`SecurityMailEventConverter`（13 个分支）、`SendSecurityMailHandler`（四条路径、事务内外顺序用 `RecordingTransactions`）、`SmtpMailDeliveryAdapter`（头、发件人、失败转换）、`ThymeleafMailRenderer`（从 identity 的 474 行单测搬来并按新模型改写：转义、深色模式声明、片段、确定性）。

IT 用 GreenMail 而不是 Mailpit：仓库惯例（`GreenMailSmtpInitializer`），不拉镜像，能直接拿 `MimeMessage` 断言头。Mailpit 留给本机和 mini 上的人工核对。AC 字面写「Mailpit 集成测试」，这里有意偏离。

## 13. 上线步骤（追加到 runbook）

1. **合并前**：确认 mini 上 `~/.patra/secrets/mail.env` 存在（`ls -l`，0600），内容不用改。
2. **合并前**：Nacos `patra-notification.yaml` 随后端 PR 由 CD 推送；本机冒烟需要时按 runbook 第 3 步的规则推。
3. **部署顺序**：notification 先于 identity（PAP-104 契约第「上线顺序」节）：notification 启动时声明 `notification.security-mail` 与绑定；顺序反了不丢信，但 identity 的事件会在登记表里积着、每分钟补投。
4. **合并后**：RabbitMQ 管理界面 Queues 里有 `notification.security-mail`、`.dlq`；Bindings 有 `identity.events` → `security-mail.*`。
5. **合并后**：`docker exec patra-notification printenv PATRA_MAIL_HOST` 为 `smtp.resend.com`；`docker exec patra-identity printenv PATRA_MAIL_HOST` 无输出。
6. **D6 验收**：测试账号走找回密码、换邮箱：收到信；换邮箱时确认信到新地址、变更通知到旧地址；Gmail「显示原始邮件」里没有 `List-Unsubscribe`；`mail.mail_delivery` 每封一行；identity 的 `event_publication` 为空。
7. **排查**：信没到 → 先查 notification 日志里这个事件 ID 的 WARN，再看 `notification.security-mail.dlq`；死信重放：管理界面里把消息挪回 `notification.security-mail`（Move messages），链接类过期的会被跳过。

## 14. 我的决定（请重点看）

1. 发信放在事务外：先查发送记录 → 发 → 短事务里 `markProcessed` + 记录（2.3、第 5 节）。
2. 有效期改写成绝对时刻，拒绝「剩余时间」写法（2.5），原因是 Resend 对同键不同内容 409。
3. 只为链接而发的三类在链接过期后不发、记 `LINK_EXPIRED`；通知类照发（第 5 节）。
4. 发送记录只有四列，不存地址、用户 ID、正文（第 8 节）；发送失败不进表。
5. 重试与死信全用事件模块默认值，不给邮件单独的退避（第 9 节）。
6. 单个监听方法、参数是 sealed 接口；第一个 IT 证明不了就退回 `@RabbitHandler`（6.4）。
7. 13 类的主题与「不是我怎么办」放领域枚举 `SecurityMailKind`；事件到领域模型的映射在 adapter（只有 adapter 依赖 identity-api）。
8. 不加门户地址配置、不加 `headers` 分量：本 Issue 没有调用方，用法写在第 11 节给 PAP-146（改判了编排会话拍板 3，理由见第 4 节）。
12. 发信失败的异常不挂原始 cause，只带脱敏后的原因（6.1），防止服务商回复里的收件地址经 Spring AMQP 的 WARN 进日志。
9. `mail.env` 在 notification 块仍是 `required: false`，靠 runbook 核对兜底（第 10 节）。
10. IT 用 GreenMail，不用 Mailpit（第 12 节）。
11. 不改 identity（编排会话拍板 1）；identity-api 的 13 个 record 是临时副本，单独提交，PAP-104 合入后丢弃（第 4 节）。

## 15. 代价与留下的问题

- **Message-ID 与 Date**：JavaMail 每次发送都生成新的 `Message-ID` 和 `Date`。Resend 文档没说这两个头算不算「内容」。算的话，「发出后、记录前崩溃」那次重投会被 409：重试 3 次都失败、进死信，但用户已经收到 1 封，结果仍正确，只是多一条死信。mini 上若在日志里看到 409，改成覆盖 `MimeMessage.updateMessageID()`、固定为 `<事件 ID@发件域名>`。
- **24 小时窗口**：Resend 只记 24 小时。超过一天的死信重放，或 broker 停了一天以上，通知类的信可能重复送达一封（链接类过期会跳过）。
- **7 秒的重试**：Resend 抖动超过约 7 秒，这段时间的安全邮件会进死信，要人去重放；本版没有死信告警（决策 E 的代价）。
- **Mailpit 不认幂等键**：本机崩溃窗口里会收到两封，属预期。
- 发送记录与 `processed_event` 不清理：按 Patra 的量级多年也就几 MB。

## 16. 与并行 Issue 的交接

- **PAP-104**：本分支的 identity-api record 副本在单独提交里，汇合时丢弃；若 PAP-104 的签名与契约表有出入，以它为准，本分支的转换器与测试跟着改。PAP-104 删掉 identity 的 `spring.mail.*` 后，compose 里 identity 的 `mail.env` 也就没用了，本 Issue 删掉那两行。
- **PAP-142**（`c5eeebcd5`，还没进本基线）：会和本分支同时改 notification 的 app / adapter / infra / boot 构建脚本、`application.yml`、README、runbook、`module-graph.json`。依赖行写法与它一致（`api(project(...-domain))`、`api(project(":patra-tool:patra-tool-cqrs"))`、`api(project(...-app))`），汇合时去重即可。
- **PAP-143**：同样要给 adapter 加 `spring-boot-starter-amqp`、给 `trusted-packages` 加 interaction 的包，汇合时合并（列表写法便于追加）。
- **PAP-146**：第 11 节。
