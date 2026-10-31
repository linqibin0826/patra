# identity 安全邮件改发事件

- 日期：2026-10-31
- 分支：`claude/v016-pap104-9cadca`（基线 `claude/v016-integration` @ `f46edcdc0`：第一波五个 Issue、api 层 `event` 包白名单、PAP-142 已汇合）
- Issue：PAP-104（v0.16 决策 B、C、E、F）
- 依赖：PAP-137 事件模块（`patra-tool-events`，设计见 `2026-10-30-event-module-design.md`）、PAP-138 RabbitMQ
- 下游：PAP-105（通知服务的邮件渠道照着这些事件发信；模板与文案从基线 `b047dec36` 拷到 notification）
- 范围调整（10/31，编排会话代用户定）：identity 整套发信代码由本 Issue 删干净（原定 PAP-105 删），见 4.9；Flyway 版本号用时间戳写法
- 契约摘要：共享笔记 `PAP-104-events-contract.md`（与本文第 5、6 节一致）

## 1. 为什么做

PAP-96 把发邮件做成了 identity 进程内的队列：`SmtpEmailDeliveryAdapter` 自持一个 `ThreadPoolTaskExecutor`（4 条虚拟线程、内存队列 1000），`send()` 入队就返回，发送失败只记 WARN、不重试，停机只等 10 秒。identity 崩溃或重启时，队列里没发出去的信就没了。注销完成通知、到期前提醒、新设备登录告警这几封，用户没有入口再要一封，丢了就是真丢。

v0.16 决策 C、F：identity 不再自己发信。`EmailDeliveryPort` 的实现改成经通用事件模块发布集成事件，交换机 `identity.events`，由通知服务的邮件渠道（PAP-105）发出。事件带收件地址和链接，因为有的信不是发到当前邮箱：换邮箱的确认信发到新地址，变更通知发到旧地址。

## 2. 先讲清几个概念

### 2.1 为什么「登记这封信」必须放进业务事务

- **现在的写法**：每个 Handler 在事务里把邮件内容备好（签令牌、拼链接），**提交之后**再 `emailDelivery.send(mail)` 丢进内存队列。端口的注释还专门要求「提交之后再调，否则用户点到链接时令牌可能还没落库」。
- **问题**：提交和入队是两步。提交成功、进程在入队前或发送前崩了，令牌在库里、信却没了。这就是事件模块 2.1 节讲的**双写**：写库和发消息是两个系统，做不到同成同败。
- **事件模块的解法**：事务里 `publishEvent(领域事件)`，Modulith 在**同一个事务**里往 `event_publication` 写一行；提交后异步外送，broker 确认收下才删这一行；发不出去就留着，补投任务每分钟再发。这一行和令牌同成同败：令牌落库 ⇔ 有一行待发。
- **所以要倒过来**：`send` 从「提交后」挪进「事务里」。令牌落库的担心也随之消失：外送在提交之后才开始，消息到通知服务时令牌一定已经在库里。

### 2.2 「请发这封信」型事件

- 决策 A 说通知服务收的是**事实型**事件（「C 回复了 B」），由它决定通知谁。安全邮件不一样：发给谁、带什么链接只有 identity 知道（换邮箱时旧地址、新地址各发一封；令牌原文只在签发那一刻存在，库里只存哈希），通知服务没法自己算。
- 所以安全邮件事件是**指令型**的：「请给这个地址发一封『重设密码』，链接是这个」。Kratos 也是认证服务自己带上地址和链接交给投递方（决策 F 的「业界」）。
- 通知服务仍然只负责**怎么发**：主题、正文、模板、语言、时区。事件里只放事实（收件地址、昵称、链接、时间、设备与 IP），不放文案。

### 2.3 至少一次投递与幂等键

- 事件模块是**至少一次**：补投可能把已经送到的事件再发一遍（确认在半路丢了），消费端处理完、ack 之前崩了 RabbitMQ 也会重投。
- 对邮件来说，「处理两次」= 用户收到两封一样的信。两层挡：通知服务的 `processed_event` 按事件 ID 去重（决策 D）；邮件已经发出、去重记录提交前进程挂了，重试会再发一次，这一层由 Resend 的幂等键挡（决策 G，同一个键 24 小时内只送达一次）。
- 两层都要一个**同一封信永远不变**的 ID。这就是 `eventId`：在 identity 构造这封信时生成，跟着登记行存进库，补投时原样还原。一个事件就是一封信，PAP-105 直接拿它当 `Resend-Idempotency-Key`。
- **代价**：Resend 只记 24 小时。broker 或通知服务停了 24 小时以上、恢复后补投，可能重复送达一封。接受：那时更该担心的是 24 小时没发出去的信。

### 2.4 一次性令牌在路上会经过哪些地方

链接里带着一次性令牌（验证邮箱、找回密码、改邮箱确认、撤销改邮箱、改回后的重设，共 5 类），拿到链接就能操作账号。改发事件后，链接会经过：

| 经过的地方 | 待多久 | 对策 |
|---|---|---|
| identity 的 `event_publication` | 外送确认前（平时毫秒级；broker 停着时到恢复为止） | 完成模式 `DELETE`：确认即删行，不留历史（决策 F）。identity 的 yml 显式写出，不只依赖模块默认值 |
| RabbitMQ 队列 | 通知服务消费前 | RabbitMQ 只在 tailnet 内开放（PAP-138） |
| 日志 | — | 领域事件与集成事件的 `toString()` 都不输出收件地址和链接；适配器的日志只打 `eventId` 与邮件种类 |
| 通知服务 | 发送时 | 发送记录不存正文（PAP-105） |

令牌本来就会出现在用户的邮箱里，多出来的是这两站的暂存，且都在内网。

### 2.5 领域事件和集成事件在这里分别是谁

事件模块要求两层（决策 B）：

- **领域事件**：登记表里存的就是它（序列化成 JSON），补投时再反序列化、重新映射。identity 里现成的 13 个 `*Mail` 记录（`EmailVerificationMail`、`PasswordResetMail`……）已经是「一封要发的信」的全部事实，**直接当领域事件**，各加一个 `UUID eventId`。不另建第三套类：渲染相关的方法与文案常量一并删掉（4.9），每封信在 identity 里只剩这一个只带事实的记录。
- **集成事件**：对外的契约，放 `patra-identity-api`，13 个 `*MailEvent` 记录，只放通知服务需要的事实。映射写在 boot 层（决策 B）。

为什么不是包一层（`SecurityMailRequested(eventId, OutgoingMail mail)`）：登记表按**具体类型**反序列化，接口类型的字段需要 `@JsonTypeInfo` 才能还原，而领域层不能依赖 Jackson（R3）。13 个扁平记录各自都能直接往返。

## 3. 目标、非目标、反目标

**目标**

1. 13 类安全邮件全部经事件模块发出：业务改动提交 ⇔ 有一行待发；broker 没收下时留给补投。
2. identity 里没有进程内发信队列，`EmailDeliveryPort` 的实现只发布事件。
3. 13 个集成事件放 `patra-identity-api`，带收件地址、模板需要的字段、链接；`eventId` 在构造时生成、补投不变。
4. 换邮箱：确认信事件带新地址，变更通知事件带旧地址。
5. identity 现有的邮件相关流程测试改为断言发出的事件，全部通过；PAP-104 的 4 条验收各有测试。

**非目标**

- 不在 notification 里发信、不搬模板（PAP-105 从基线拷贝）。identity 这边的发信代码本 Issue 删干净（4.9）。
- 不做通知服务的队列、去重、重试与死信（PAP-105）。
- 不改邮件文案与种类：13 类原样保留。
- 不把其他 identity 行为改成事件（第 3 节 K）。

**反目标**（功能都在也算失败）

- 某个 Handler 仍在事务外调 `send`：登记行和业务不同成同败，又回到双写。
- 补投换了 `eventId`，通知服务去不了重、Resend 幂等键失效。
- 令牌链接或收件地址出现在日志里。
- 登记表留着已发出的行（令牌久留）。
- 新增第 14 种邮件时忘了映射，编译和测试都发现不了。

## 4. 设计

### 4.1 端口契约

```java
/// 安全邮件的投递端口。
///
/// 必须在业务事务里调用：实现把这封信登记进同一个事务，事务提交后才外送，回滚则一起作废；
/// 投递是至少一次，同一封信补投时 eventId 不变。登记失败抛异常，业务事务随之回滚。
public interface EmailDeliveryPort {
  void send(OutgoingMail mail);
}
```

- 返回值从 `boolean`（「被执行器接下了没有」）改为 `void`：登记要么和业务一起成功，要么抛异常让事务回滚，没有「被拒收」这种中间态。依赖返回值的三处分支（注销提醒退回标记、新设备提醒退还冷却、两处「被拒收」WARN）一并删除（4.4）。
- `OutgoingMail` 接口加 `UUID eventId()`。

### 4.2 领域事件：13 个 `*Mail` 记录

每个记录：

1. 第一个分量加 `UUID eventId`，紧凑构造器里非空校验。**生成在构造时**：`of(...)` 内部 `UUID.randomUUID()`；用 `@Builder` 的记录在自己声明的 builder 类里写 `private UUID eventId = UUID.randomUUID();`（Lombok 不再生成这个字段、仍生成 `eventId(...)` 设值方法，已在 Lombok 1.18.40 上验证），调用方不用管 ID，测试可以指定。不在紧凑构造器里「为 null 就生成」：反序列化缺字段时会每次生成新的，正是要防的事。
2. 带链接的五封加**链接过期时间**：`PasswordResetMail`、`EmailChangeConfirmMail`、`EmailChangedNoticeMail`、`EmailChangeRevertedMail` 新增 `Instant linkExpiresAt`，`EmailVerificationMail` 原有的 `expiresAt` 改名 `linkExpiresAt`。值取签发出的令牌的 `expiresAt`。
   - 为什么不直接给「24 小时内有效」的数字：事件可能因 broker 停机晚到，信里写「24 小时」时链接实际只剩 22 小时；给过期时刻，通知服务能按发送时的剩余时间写，已过期的干脆不发（4.6 的建议）。
3. 记录分量数达到 5 个的改用 `@Builder`、去掉 `of()`（代码规范「≥5 用 Builder」）：`EmailVerificationMail`、`PasswordResetMail`、`EmailChangeConfirmMail`、`EmailChangeRevertedMail`、`AccountDeletionRequestedMail`、`AccountDeletionReminderMail`（`EmailChangedNoticeMail` 本来就是）。其余 6 个（`PasswordResetDoneMail`、`PasswordChangedMail`、`EmailChangedConfirmationMail`、`NewDeviceLoginMail`、`AccountDeletionCancelledMail`、`AccountDeletionCompletedMail`）保持 `of()`。
4. `template()`、`subject()`、`model()`、`TEMPLATE`、`SUBJECT`、`NOT_ME_ADVICE` 删掉：identity 不再渲染，文案归通知服务。`OutgoingMail` 只剩 `eventId()` 与 `to()`。`toString()` 只输出事件 ID 与昵称，不输出收件人与链接。
5. `SecurityNotice` 去掉 `notMeAdvice`（文案），剩四个分量，按代码规范改用 `of()`；`ClientOrigin.toNotice(Instant)` 不再收建议文案。

**Jackson 往返**：Modulith 用应用的 `JsonMapper` 把领域事件写进 `serialized_event`、补投时按具体类型读回。记录的规范构造器就是属性构造器；`EmailAddress`（单分量记录）、`SecurityNotice`（可空的设备与 IP）、`Instant` 都要能还原。用 IT 把 13 个样本经上下文里的 Modulith `EventSerializer` 往返一遍（第 6 节）。

### 4.3 适配器：`OutboxEmailDeliveryAdapter`

`infra.account.mail` 包，`@Component`：

```java
@Override
@Transactional(propagation = Propagation.MANDATORY)
public void send(OutgoingMail mail) {
  events.publishEvent(mail);
  log.info("安全邮件已登记待外送: type={}, eventId={}", mail.getClass().getSimpleName(), mail.eventId());
}
```

- `MANDATORY`：没有事务就抛 `IllegalTransactionStateException`。忘了把 `send` 挪进事务的 Handler 会立刻失败，而不是悄悄在事务外发布（事务外发布时 Modulith 的提交后监听器不会被调用，事件直接丢失）。这也是 infra 层唯一允许的事务写法（`transaction.md` 第 1 节）。
- 类不能是 `final`（事务代理要继承它）。

### 4.4 13 个 Handler 怎么改

通则：`send` 挪进事务回调，放在业务写入之后；请求来源（UA、属地）的解析是纯计算，从提交后挪到事务前（`RegisterUserHandler`、`LoginUserHandler` 早就这么做）。

| Handler | 邮件 | 改法 |
|---|---|---|
| `RegisterUserHandler` | 验证邮件 | 事务里签令牌后登记；提交后的「首封计入重发限流」（Redis）不动，顺序变为先登记、提交后再计数 |
| `ResendEmailVerificationHandler` | 验证邮件 | 事务里签令牌后登记 |
| `RequestPasswordResetHandler` | 找回密码 | 事务里签令牌后登记；未注册 / 封禁的静默分支不变 |
| `ResetPasswordHandler` | 密码已重设 | 来源解析挪到事务前；事务里撤销会话、读到昵称后登记。提交后清零登录失败（Redis）不动 |
| `ChangePasswordHandler` | 密码已修改 | 来源解析挪到事务前；登记放在**换令牌之前**：类注释要求「换令牌放在事务最后一步，之前任何一步失败都不碰旧令牌」，登记是一次写库，可能失败 |
| `RequestEmailChangeHandler` | 改邮箱确认（**新地址**） | 事务里签令牌后登记 |
| `ConfirmEmailChangeHandler` | 变更通知（**旧地址**）+ 已绑定（**新地址**） | 来源解析挪到事务前；事务里签撤销令牌后两封都登记 |
| `RevertEmailChangeHandler` | 已改回请重设 | 来源解析挪到事务前；事务里签重设令牌后登记 |
| `LoginUserHandler` | 新设备登录 | 见 4.5 |
| `RequestAccountDeletionHandler` | 注销已提交 | 来源解析挪到事务前；事务里登记。删除「被拒收」WARN |
| `CancelAccountDeletionHandler` | 已取消注销 | 来源解析挪到事务前；只有真的从冷静期回到正常才在事务里登记。删除「被拒收」WARN |
| `SendAccountDeletionRemindersHandler` | 即将删除提醒 | 记下提醒时间与登记同一个事务；删除 `unmark` 与「被拒收」分支：先记后发不再有中间态 |
| `ExecuteDueAccountDeletionsHandler` | 已注销 | 在匿名化的同一个事务里登记（收件地址是置空之前的邮箱）；删除「被拒收、无法重发」分支：登记失败时匿名化一起回滚，用户仍在冷静期，下次运行重试——比以前「注销完成、信丢了」更好 |

日志措辞从「已排队」改为「已登记」。

**找回密码的耗时**：已注册路径多一次插入（同一个事务里，约 1 毫秒），未注册路径不写库。两条路径本来就差一个事务（加锁、签令牌），`PasswordResetFlowIT` 的「耗时中位数之比 < 10」照旧成立。

### 4.5 新设备提醒：为什么另开一个短事务

- 新设备提醒先要过 24 小时限流（`NewDeviceMailThrottlePort`，Redis），放行才发。`transaction.md` 第 2 节：事务里不许碰 Redis（会话签发与撤销是唯一例外）。所以限流不能挪进登录事务。
- 做法：登录事务照旧提交 → 事务外取限流 → 放行则 `transactions.executeWithoutResult(status -> emailDelivery.send(mail))`，单独一个只写登记行的短事务。
- 登记失败（数据库抖动）：不让登录失败（会话已经建好、已经返回给用户），记 WARN 并尽力退还冷却，下次登录还能提醒——与现在「被拒收就退还冷却」同一处理。
- **代价**：两次提交之间进程崩了，这封提醒丢失。窗口是微秒级，且比现在（整段内存队列）小得多；要彻底消除就得把 Redis 限流放进登录事务，违反事务规范，不值得。

### 4.6 集成事件（`patra-identity-api`）

包 `dev.patra.identity.api.account.event`（分包规则的 api 白名单已含 `event`，见 4.10）。

```java
public sealed interface SecurityMailEvent extends IntegrationEvent
    permits EmailVerificationMailEvent, PasswordResetMailEvent, /* …共 13 个 */ {
  String EXCHANGE = "identity.events";
  String ROUTING_KEY_PATTERN = "security-mail.*";
  String to();           // 收件地址
  String displayName();  // 收件人昵称（已按用户名兜底）
}
```

- **sealed**：通知服务 `switch (event)` 能穷举 13 种，漏一种编译不过。
- 每个记录有常量 `ROUTING_KEY`；路由键 = `security-mail.` + identity 现在的模板名，PAP-105 搬模板时一一对应。
- `OperationContext(Instant occurredAt, String device, String ipAddress, String ipLocation)`：触发安全通知的那次操作的时间、设备、IP、属地；后三项可为 `null`。从领域的 `SecurityNotice` 映射，**不带** `notMeAdvice`（文案归通知服务）。
- 分量 ≥ 5 的记录用 `@Builder`，≤ 4 的用 `of()`（同代码规范）。`toString()` 只输出 `eventId`。

| 记录 | 路由键后缀 | `to` | 其他字段 | 一次性令牌 |
|---|---|---|---|---|
| `EmailVerificationMailEvent` | `email-verification` | 当前邮箱 | `verifyUrl`、`linkExpiresAt` | 有 |
| `PasswordResetMailEvent` | `password-reset` | 申请的邮箱 | `resetUrl`、`linkExpiresAt` | 有 |
| `PasswordResetDoneMailEvent` | `password-reset-done` | 当前邮箱 | `context` | |
| `PasswordChangedMailEvent` | `password-changed` | 当前邮箱 | `context` | |
| `EmailChangeConfirmMailEvent` | `email-change-confirm` | **新邮箱** | `confirmUrl`、`linkExpiresAt` | 有 |
| `EmailChangedNoticeMailEvent` | `email-changed-notice` | **旧邮箱** | `newEmailMasked`、`revertUrl`、`linkExpiresAt`、`securitySettingsUrl`、`context` | 有 |
| `EmailChangedConfirmationMailEvent` | `email-changed-confirmation` | **新邮箱** | `securitySettingsUrl`、`context` | |
| `EmailChangeRevertedMailEvent` | `email-change-reverted` | 改回的旧邮箱 | `resetUrl`、`linkExpiresAt`、`context` | 有 |
| `NewDeviceLoginMailEvent` | `new-device-login` | 当前邮箱 | `securitySettingsUrl`、`context` | |
| `AccountDeletionRequestedMailEvent` | `account-deletion-requested` | 当前邮箱 | `deletionDueAt`、`context` | |
| `AccountDeletionCancelledMailEvent` | `account-deletion-cancelled` | 当前邮箱 | `context` | |
| `AccountDeletionReminderMailEvent` | `account-deletion-reminder` | 当前邮箱 | `deletionDueAt`、`daysLeft` | |
| `AccountDeletionCompletedMailEvent` | `account-deletion-completed` | 注销前的邮箱 | `username` | |

字段从哪来：逐个对照了 13 份模板用到的变量。模板里由渲染器补的三样，`securityUrl` 是链接，按决策 F 进事件（只放用到它的三封）；`siteName` 和「N 小时/分钟/天内有效」的数字是文案，归通知服务（后者由 `linkExpiresAt` 算）。

**事件里不带、归通知服务的**：主题、站名、「不是我怎么办」、模板名、有效期数字、显示时区。identity 里的文案随 4.9 删除，PAP-105 从基线 `b047dec36` 拷（各 `*Mail` 的 `SUBJECT` / `NOT_ME_ADVICE`、`resources/mail/**`）。给 PAP-105 的建议：有效期按「发送时刻到 `linkExpiresAt` 的剩余时间」写；已过期的链接信不发、记日志。

### 4.7 外送配置（boot）

新增 `IdentityEventsConfiguration`（不往已有的 `IdentityConfiguration` 里塞：PAP-142 也在改 identity，少碰共享文件）：

```java
@Bean
TopicExchange identityEvents() {
  return ExchangeBuilder.topicExchange(SecurityMailEvent.EXCHANGE).durable(true).build();
}

@Bean
EventExternalizationConfiguration eventExternalizationConfiguration(SecurityMailEventMapper mapper) {
  return EventExternalizationConfiguration.externalizing()
      .selectByType(OutgoingMail.class)
      .mapping(OutgoingMail.class, mapper::toIntegrationEvent)
      .routeAll(event -> RoutingTarget.forTarget(SecurityMailEvent.EXCHANGE)
          .andKey(mapper.routingKeyOf((OutgoingMail) event)))
      .build();
}
```

- `SecurityMailEventMapper`（boot 的 `config` 包）：13 个分支的 `switch` 把领域记录映射成集成事件、给出路由键，`eventId` 原样复制。`OutgoingMail` 不做成 sealed（渲染器的单测有一个匿名实现的探针邮件，归 PAP-105），`switch` 的 `default` 抛异常；**防漏**靠测试：用 ArchUnit 扫出 domain 里全部 `OutgoingMail` 实现，逐个断言能选中、能映射、路由键对（第 6 节）。
- 映射器注入 `MailLinkPolicy`：`securitySettingsUrl` 没有令牌，外送时现拼，门户地址改了补投也用新的；带令牌的链接只能在签发时拼（令牌原文不落库），所以在领域记录里。
- 选择用类型：identity 只外送安全邮件。以后加别的外送事件再加选择条件。
- `spring.modulith.events.completion-mode: delete` 在 identity 的 `application.yml` 显式写出（注释写「令牌发完即删」），不只依赖模块默认值：哪天模块默认改了，identity 不跟着变。
- 服务代码里不写 `@ApplicationModuleListener`（事件模块的约束）；identity 现在没有任何 `@EventListener` / `@TransactionalEventListener`。

### 4.8 Flyway

`patra-identity-infra` 新增 `V20261031025039__create_event_publication.sql`，照抄事件模块的模板（Modulith 固定表名与列，不加 `idn_` 前缀；`PatraIdentityApplicationIT` 只断言 `idn_%` 的表，不受影响，另加一条断言登记表存在）。

### 4.9 删掉什么、留下什么

**删掉**（进程内队列就是这个适配器本身）：

- `SmtpEmailDeliveryAdapter` 与它的单测、IT；`IdentityConfiguration` 里的 `emailDeliveryPort`、`mailRenderer` 两个 bean 与 `MAIL_MAX_CONCURRENCY`。
- 测试里的 `IdentityMailITSpringConfig`（含 `MailDrain`）、`IdentityMailDrainTestExecutionListener` 及其 `spring.factories` 条目、各 IT 上的 `GreenMailSmtpInitializer`。

- 整套渲染与发信（10/31 范围调整，原定 PAP-105 删）：`ThymeleafMailRenderer` 与单测、`RenderedMail`、`SecurityNoticeView`、`resources/mail/**`（含测试探针模板）、infra 的 `spring-boot-starter-mail` 与 `thymeleaf` 依赖、各 `*Mail` 的渲染方法与文案常量（4.2）、`SecurityNotice.notMeAdvice`、`IdentityProperties.Mail.from`、`application.yml` 的 `management.health.mail`、测试 yml 与 Nacos `patra-identity.yaml` 里的 `spring.mail.*` 与 `patra.identity.mail.from`。只剩逐类测文案的 9 个 `*MailTest` 一并删掉（事件 ID 与 `toString` 的规则由 `OutgoingMailTest` 覆盖 13 种）。

**留下**：`patra.identity.mail.zone`（公开主页加入时间的换算时区）、`token-retention`、`links`、`resend` 仍在用。compose 里 identity 块的 `mail.env` 与 `patra-infra/docker/README.md` 的相关说明由 PAP-105 挪到 notification（它只改 compose，不碰 identity 代码与 Nacos）。

以后改 `*Mail` 或 `SecurityNotice` 的字段 = 改登记表里领域事件的结构，部署前要确认 identity 的 `event_publication` 是空的（事件模块 README）。本版第一次上线时表是新建的，不受影响。

### 4.10 构建与分包规则

- `patra-identity-boot`：`implementation(project(":patra-tool:patra-tool-events"))`、`implementation(project(":patra-api:patra-identity:patra-identity-api"))`（映射器直接用集成事件，按 R5 显式声明）。改完重跑 `./gradlew dumpModuleGraph`，一起提交 `patra-infra/cd/module-graph.json`。
- `patra-identity-api` 已经由 `patra.hexagonal-api` 带上 `patra-tool-core`（`IntegrationEvent` 在那里），只用 Lombok。
- **分包规则**：集成事件放 `{根}.api.{子域}.event`。白名单已由集成分支上的 `d3644fde4` 加好（interaction 的集成事件同样用它），本 Issue 不再改规则。

## 5. 包结构

```text
patra-identity-domain
  dev.patra.identity.domain.account.model.vo.mail
    OutgoingMail                         只剩 eventId()、to()
    *Mail ×13                            + eventId；带链接的 + linkExpiresAt；去掉模板名、主题、文案
  dev.patra.identity.domain.session.model.vo
    SecurityNotice                       去掉 notMeAdvice，改用 of()
  dev.patra.identity.domain.account.port.mail
    EmailDeliveryPort                    void send(OutgoingMail)，必须在事务里

patra-identity-infra
  dev.patra.identity.infra.account.mail
    OutboxEmailDeliveryAdapter           新增，MANDATORY，publishEvent
    SmtpEmailDeliveryAdapter             删除
    ThymeleafMailRenderer、RenderedMail、SecurityNoticeView  删除（4.9）
  resources/mail/**                      删除
  resources/db/migration
    V20261031025039__create_event_publication.sql     新增

patra-identity-api
  dev.patra.identity.api.account.event
    SecurityMailEvent                    sealed，EXCHANGE、ROUTING_KEY_PATTERN
    OperationContext
    *MailEvent ×13

patra-identity-boot
  dev.patra.identity.config
    IdentityEventsConfiguration          交换机、外送配置
    SecurityMailEventMapper              领域记录 → 集成事件、路由键
    IdentityConfiguration                删两个发信 bean

```

## 6. 测试

### 6.1 验收对照

| 验收（PAP-104） | 测试 |
|---|---|
| identity 在事件登记后、外送前被杀掉，重启后补投送达 | `SecurityMailRestartIT` 两例，都用 `SpringApplicationBuilder` 起两个独立的 identity 实例：①**外送前没了**——实例 A 的异步执行器换成丢掉一切任务的（`IdentityStalledAsyncITSpringConfig`），经 HTTP 注册后登记行停在 `PUBLISHED`；②**外送失败**——`rabbitmqctl stop_app` 后注册，登记行变 `FAILED`。两例都**关掉 A**（进程里的一切都没了，只剩库），起全新的实例 B 调 `EventPublicationResubmitter.resubmit()`：捕获队列收到验证邮件事件，`eventId` 与登记行里的相同，拿链接里的令牌在 B 上验证邮箱成功，登记表为空 |
| 外送完成后登记行被删除 | 同上最后一步；另在流程 IT 的排空监听里，每个用例结束都要等登记表清空（所有成功外送的行都被删了才算结束），且断言 identity 环境里 `spring.modulith.events.completion-mode=delete` |
| 换邮箱流程发出的两条事件地址正确（新地址、旧地址） | `EmailChangeFlowIT`：申请后收到发往**新地址**的 `EmailChangeConfirmMailEvent`；确认后收到发往**旧地址**的 `EmailChangedNoticeMailEvent`（`newEmailMasked` 是遮掩过的新地址、带撤销链接）与发往**新地址**的 `EmailChangedConfirmationMailEvent`，旧地址没收到「已绑定」、新地址没收到变更通知 |
| identity 现有邮件相关流程测试改为断言发出的事件后全部通过 | 全部流程 IT 从 GreenMail 换成读捕获队列：按收件地址与事件类型取事件，令牌从事件的链接字段取；正文断言（设备、IP 与属地）改为断言 `context` 字段；纯模板措辞的断言删掉（渲染器单测仍覆盖，PAP-105 一起搬） |

关于「被杀掉」：被杀时登记行可能是 `PUBLISHED`（外送还没开始）、`PROCESSING`（正在发）或 `FAILED`（发失败了）。IT 覆盖 `PUBLISHED` 与 `FAILED` 两种；`PROCESSING` 的 10 分钟保护由事件模块的测试覆盖。

### 6.2 测试基座（IT）

- 所有起 identity 应用的 IT 上下文：`GreenMailSmtpInitializer` 换成 `RabbitMQContainerInitializer`（JVM 内单例的 `rabbitmq:4.4.0-management`）。
- `IdentitySecurityMailITSpringConfig`：声明捕获队列 `identity-it.security-mail`（`EventQueue.named(...).bind("identity.events", "security-mail.*")`），每个起应用的上下文都导入它——否则外送时没有队列可投，登记行会留在共用的库里，拖住别的上下文。
- `IdentitySecurityMailITFixture`：用 `RabbitTemplate.receiveAndConvert` **拉取**（不挂监听器），按收件地址与事件类型找，最多等 5 秒；`purge()` 清空捕获队列。`application-test.yml` 配 `patra.events.consumer.trusted-packages: dev.patra.identity.api.account.event`，按 `__TypeId__` 还原具体类型——顺带验证了消费端的反序列化。
- **排空**：`IdentitySecurityMailDrainTestExecutionListener`（替代原来的邮件排空监听）在每个测试方法结束后等登记表清空（最多 10 秒）。表空 = 每条事件都已被 broker 确认收进队列，下一个用例开头 `purge()` 就能把上一个用例的事件全部清掉，不会晚到。
- 停 broker 的用例自己负责收尾（启动 broker、补投、等表空）。

### 6.3 其他测试

- **Handler 单测**（13 个）：用 `RecordingTransactions` 断言 `send` 落在事务里（先改断言变红，再挪代码）；`ChangePasswordHandler` 断言登记在换令牌之前；`LoginUserHandler` 断言限流在登录事务外、登记在第二个事务里，登记失败时登录照常返回并退还冷却；`SendAccountDeletionRemindersHandler`、`ExecuteDueAccountDeletionsHandler` 删除「被拒收」用例，加「登记失败时这个用户的改动回滚、计入失败」。邮件比对忽略 `eventId`。
- **领域单测**：每个 `*Mail` 不指定时生成 `eventId`、两次生成不同、指定时用指定的；`toString` 不含收件地址与链接。
- **适配器单测**：发布的就是传入的邮件。`MANDATORY` 在 IT 里验证（没有事务调用抛 `IllegalTransactionStateException`）。
- **映射器单测**：13 个样本逐个断言集成事件类型、字段、`eventId` 原样、路由键；ArchUnit 扫出 domain 里全部 `OutgoingMail` 实现，与样本集合相等（防漏）。
- **外送配置单测**：`EventExternalizationConfiguration` 的 `supports` / `map` / `determineTarget` 对 13 个样本给出期望结果，非邮件对象不选中。
- **序列化 IT**：13 个领域样本（含设备、IP 为 `null` 的安全通知）经上下文里的 Modulith `EventSerializer` 往返相等；13 个集成事件经上下文里的 `MessageConverter` 往返相等。
- **集成事件** `toString` 不含地址与链接：放在映射器单测里（api 模块不跑测试）。
- **启动 IT**：Flyway 建出 `event_publication`。

## 7. 上线步骤

追加到 `docs/patra/runbooks/v0.16-notification-go-live-runbook.md`：

1. **部署顺序**：PAP-104 与 PAP-105 同一个后端 PR、同一窗口上线。CD 先部署 identity、后部署 notification：中间几分钟里 identity 的安全邮件事件没有队列可投，留在 `event_publication`（不丢），notification 声明好邮件队列（绑 `identity.events` / `security-mail.*`）、补投任务登记好后一分钟内补发。这段时间注册验证、找回密码、换邮箱的信会晚到。
2. identity 启动时 Flyway 建 `event_publication`（`V20261031025039`，时间戳版本号）。
3. xxl-job-admin 给 identity 登记 `eventPublicationResubmitJob`：CRON `0 * * * * ?`，路由「第一个」，阻塞处理「丢弃后续调度」，超时 60 秒，失败重试 0 次。
4. RabbitMQ 连接与密码：PAP-138 已在 Nacos `patra-identity.yaml` 与 apps compose 的 `rabbitmq.env` 配好，本 Issue 不再动。
5. 验证：在门户触发一次找回密码，identity 库 `SELECT count(*) FROM event_publication;` 为 0；identity 日志有「安全邮件已登记待外送」带 `eventId`，通知服务日志能按同一个 `eventId` 查到发送。

## 8. 代价与留下的问题

- 每封安全邮件多一次写库、外送时多等一次 broker 确认（异步，不拖慢请求）。外送等确认时不占数据库连接：Modulith 的外送器方法是 `@ApplicationModuleListener(propagation = SUPPORTS)`，不开物理事务，进入与完成的登记是各自的短事务；前提是外送路径（这里是 `SecurityMailEventMapper`）不查库，事件模块的发送模板在等确认前检查线程上没有挂着连接（事件模块 spec 4.13，由 `ExternalizationConnectionIT` 守住）。所以 identity 不需要压异步并发；broker 卡住时只是异步线程各等确认超时（至少 5 秒），登录、注册不受影响。
- 新设备提醒在登录提交与登记之间崩溃会丢一封（4.5）。
- 24 小时以上的停机后补投可能重复送达一封（2.3）。
- `processed_event` 清理、死信告警等属于消费方（PAP-105 / 事件模块第 9 节）。
