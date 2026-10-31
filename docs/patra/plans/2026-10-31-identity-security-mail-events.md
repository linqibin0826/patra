# identity 安全邮件改发事件 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（本会话内联执行，不派子代理）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** identity 的 13 类安全邮件不再进程内发送，改为与业务同事务登记、经事件模块外送到 `identity.events` 的集成事件。

**Architecture:** 13 个 `*Mail` 领域记录加 `eventId` 当领域事件；`EmailDeliveryPort` 的实现在调用方事务里 `publishEvent`（`MANDATORY`）；boot 层把它们映射成 `patra-identity-api` 里 13 个 `*MailEvent`，路由键 `security-mail.<种类>`。IT 改读 RabbitMQ 捕获队列。

**Tech Stack:** Java 25、Spring Boot 4.1.2、Spring Modulith 2.1.2（经 `patra-tool-events`）、RabbitMQ 4.4.0（Testcontainers）、JUnit 5 + AssertJ + Mockito、ArchUnit。

**Spec:** `docs/patra/specs/2026-10-31-identity-security-mail-events-design.md`

## Global Constraints

- 交换机 `identity.events`；路由键 `security-mail.` + 模板名；绑定模式 `security-mail.*`。
- 集成事件包 `dev.patra.identity.api.account.event`；领域记录包 `dev.patra.identity.domain.account.model.vo.mail` 不变。
- `eventId` 在记录构造时生成：`of()` 内部生成；`@Builder` 记录在自声明的 builder 类里 `private UUID eventId = UUID.randomUUID();`；紧凑构造器只做非空校验，绝不「null 就生成」。
- 记录分量 ≥ 5 用 `@Builder`、不提供 `of()`；≤ 4 用 `of()`。
- 两套记录的 `toString()` 都不输出收件地址与链接。
- `send` 只能在事务里调（`@Transactional(propagation = MANDATORY)`）；事务里不碰 Redis（会话签发与撤销除外）。
- identity 的 `application.yml` 显式 `spring.modulith.events.completion-mode: delete`。
- 所有 Javadoc 用 `///` Markdown；不写全类名；测试方法 snake_case；IT 名 `*IT`。
- 中文提交信息，subject 用中文动词开头；只提交自己的路径；不 push。
- 改了 `*.gradle.kts` 要重跑 `./gradlew dumpModuleGraph` 并提交 `patra-infra/cd/module-graph.json`。

## Review Focus

1. 某个 Handler 的分支在事务外调 `send`（例如取消注销「本来就正常」的分支、改密码换令牌之后）→ 期望：所有调用都在事务里，否则 `MANDATORY` 立刻抛异常。由 Task 3、4 的每个 Handler 单测 + Task 6 的 `MANDATORY` IT 钉住。
2. 设备、IP、属地为 `null` 的安全通知经登记表往返 → 期望：补投时还原出一样的记录，不因 `null` 反序列化失败卡住整轮补投。Task 6 序列化 IT 的样本必须含一个全 `null` 的通知。
3. 路由键与绑定对不上（某个记录的 `ROUTING_KEY` 不匹配 `security-mail.*`）→ 期望：这类信不会被 broker 退回、无限补投。Task 5 映射器单测断言 13 个路由键都匹配模式。
4. 登录已提交、新设备提醒登记失败 → 期望：登录照常 200，冷却退还。Task 4 `LoginUserHandler` 单测钉住。
5. 注销到期执行时登记失败 → 期望：匿名化一起回滚、计入失败、用户仍在冷静期。Task 3 `ExecuteDueAccountDeletionsHandler` 单测钉住。

---

### Task 1: 分包规则放行 `api.{子域}.event`

**Files:**
- Create: `patra-tool/patra-tool-test/src/test/java/dev/patra/tool/test/archunit/fixtures/subdomain/templatecompliant/api/collection/event/ItemCollected.java`
- Modify: `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/archunit/SubdomainArchitectureRules.java:484`（api 白名单）及类注释中的模板
- Modify: `.claude/rules/tech/package-layout.md`（模板行 `{根}.api.{子域}.{endpoint | dto | event}`）

**Interfaces:**
- Produces: 包 `{根}.api.{子域}.event` 合规。

- [ ] **Step 1:** 加夹具类 `ItemCollected`（空 record，`///` 注释说明是 `api.{子域}.event` 的集成事件夹具）。
- [ ] **Step 2:** 跑 `./gradlew :patra-tool:patra-tool-test:test --tests "*SubdomainArchitectureRulesTest*"`，期望 `should_accept_packages_on_template` FAIL（`ItemCollected` 违规）。
- [ ] **Step 3:** `onTemplate` 的 `case "api"` 白名单加 `"event"`；文档同步。
- [ ] **Step 4:** 同一命令 PASS。

### Task 2: 领域记录加 `eventId` 与链接过期时间

**Files:**
- Modify: `patra-identity-domain/.../model/vo/mail/OutgoingMail.java` 与 13 个 `*Mail.java`
- Create/Modify: `patra-identity-domain/src/test/java/dev/patra/identity/domain/account/model/vo/mail/*MailTest.java`（已有的就改）
- Modify（跟着编译）：12 个 Handler 的构造调用、对应单测的比对；`ThymeleafMailRendererTest` 探针加 `eventId()`；渲染器单测里构造邮件的写法
- Create: `patra-identity-infra/src/testFixtures/java/dev/patra/identity/infra/account/mail/SecurityMailFixture.java`

**Interfaces:**
- Produces:
  - `OutgoingMail#UUID eventId()`
  - 记录签名（第一个分量都是 `UUID eventId`）：
    - `EmailVerificationMail(eventId, EmailAddress to, String displayName, String verifyUrl, Instant linkExpiresAt)` `@Builder`
    - `PasswordResetMail(eventId, to, displayName, String resetUrl, Instant linkExpiresAt)` `@Builder`
    - `EmailChangeConfirmMail(eventId, to, displayName, String confirmUrl, Instant linkExpiresAt)` `@Builder`
    - `EmailChangedNoticeMail(eventId, to, displayName, String newEmailMasked, String revertUrl, Instant linkExpiresAt, SecurityNotice notice)` `@Builder`
    - `EmailChangeRevertedMail(eventId, to, displayName, String resetUrl, Instant linkExpiresAt, SecurityNotice notice)` `@Builder`
    - `AccountDeletionRequestedMail(eventId, to, displayName, Instant deletionDueAt, SecurityNotice notice)` `@Builder`
    - `AccountDeletionReminderMail(eventId, to, displayName, Instant deletionDueAt, long daysLeft)` `@Builder`
    - `PasswordResetDoneMail` / `PasswordChangedMail` / `EmailChangedConfirmationMail` / `NewDeviceLoginMail` / `AccountDeletionCancelledMail`：`(eventId, to, displayName, SecurityNotice notice)`，`of(to, displayName, notice)`
    - `AccountDeletionCompletedMail(eventId, to, displayName, String username)`，`of(to, displayName, username)`
  - `SecurityMailFixture.all(): List<OutgoingMail>`（13 个，每种一个；`NewDeviceLoginMail` 的通知设备、IP、属地全为 `null`，其余齐全）、`SecurityMailFixture.NOTICE`、`SecurityMailFixture.NOTICE_WITHOUT_ORIGIN`

- [ ] **Step 1: 写失败的领域测试**（每个记录一组，或参数化）：
  - `should_generate_event_id_when_built_without_one`：`of(...)` / `builder()...build()` 的 `eventId()` 非空，连建两次不相等
  - `should_keep_given_event_id`：builder 指定 `UUID.fromString("00000000-0000-0000-0000-000000000001")` 原样保留
  - `should_reject_null_event_id`：规范构造器传 `null` 抛 `NullPointerException`
  - `should_not_leak_recipient_or_link_in_to_string`：`toString()` 含 `eventId`，不含收件地址、不含链接
  - 带链接的五个：`linkExpiresAt()` 等于传入值
- [ ] **Step 2:** `./gradlew :patra-api:patra-identity:patra-identity-domain:test`，期望编译失败 / FAIL。
- [ ] **Step 3:** 改 `OutgoingMail` 与 13 个记录（builder 类与其字段写 `///` 注释）。`EmailVerificationMail.expiresAt` 改名 `linkExpiresAt`。
- [ ] **Step 4:** 改调用方使之编译：带链接的 Handler 传 `issued.token().getExpiresAt()`；Handler 单测里 `verify(emailDelivery).send(EXPECTED_MAIL)` 改为捕获后 `usingRecursiveComparison().ignoringFields("eventId")`；探针 `OutgoingMail` 加 `eventId()`。写 `SecurityMailFixture`。
- [ ] **Step 5:** `./gradlew :patra-api:patra-identity:patra-identity-domain:test :patra-api:patra-identity:patra-identity-app:test :patra-api:patra-identity:patra-identity-infra:test` 全 PASS。

### Task 3: 端口改为事务内登记；出站适配器；用返回值的五个 Handler

**Files:**
- Modify: `domain/.../port/mail/EmailDeliveryPort.java`
- Create: `patra-identity-infra/src/main/java/dev/patra/identity/infra/account/mail/OutboxEmailDeliveryAdapter.java`
- Create: `patra-identity-infra/src/test/java/dev/patra/identity/infra/account/mail/OutboxEmailDeliveryAdapterTest.java`
- Delete: `SmtpEmailDeliveryAdapter.java`、`SmtpEmailDeliveryAdapterTest.java`、`SmtpEmailDeliveryAdapterIT.java`
- Modify: `patra-identity-boot/.../config/IdentityConfiguration.java`（删 `emailDeliveryPort`、`mailRenderer` bean 与 `MAIL_MAX_CONCURRENCY`）、`IdentityConfigurationTest.java`
- Modify: `patra-identity-infra/build.gradle.kts`（`spring-boot-starter-mail` 改 `implementation`，注释改为「PAP-105 搬走渲染后删除」）
- Modify + Test: `SendAccountDeletionRemindersHandler`、`ExecuteDueAccountDeletionsHandler`、`RequestAccountDeletionHandler`、`CancelAccountDeletionHandler`、`LoginUserHandler` 及各自单测

**Interfaces:**
- Consumes: Task 2 的记录。
- Produces: `EmailDeliveryPort#void send(OutgoingMail mail)`；`OutboxEmailDeliveryAdapter(ApplicationEventPublisher events)`，`@Component`，非 final，`send` 上 `@Transactional(propagation = Propagation.MANDATORY)`，日志 `安全邮件已登记待外送: type={}, eventId={}`。

- [ ] **Step 1: 适配器失败测试** `should_publish_the_mail_itself_as_domain_event`：mock `ApplicationEventPublisher`，`send(mail)` 后 `verify(events).publishEvent(mail)`。
- [ ] **Step 2: Handler 失败测试**（`RecordingTransactions`，协作者往同一事件列表记 `send`）：
  - 提醒：`should_mark_reminder_and_register_mail_in_one_transaction`（`send` 在 `tx-begin` 与 `tx-end` 之间）；`should_count_failure_and_keep_unmarked_when_registration_fails`（`send` 抛异常 → `failed=1`，不再调 `unmark` 的第二个事务）。删除原「执行器拒收退回标记」用例。
  - 到期执行：`should_register_completion_mail_inside_anonymizing_transaction`；`should_roll_back_and_count_failure_when_registration_fails`（`send` 抛异常 → `failed=1`、`completed=0`）。删除「拒收只记日志」用例。
  - 发起注销 / 取消注销：`send` 在事务里；来源解析（`resolver.resolve`）在 `tx-begin` 之前；取消注销「本来就正常」时不调 `send`。
  - 登录：`should_acquire_new_device_throttle_outside_login_transaction_and_register_mail_in_second_transaction`（事件顺序：`tx-begin`、`tx-end`、`throttle`、`tx-begin`、`send`、`tx-end`）；`should_still_log_in_and_reset_cooldown_when_registration_fails`（`send` 抛异常 → 返回会话、`newDeviceThrottle.reset(...)` 被调）；限流不放行时不开第二个事务。
- [ ] **Step 3:** 跑 app 与 infra 单测，期望 FAIL。
- [ ] **Step 4:** 改端口签名与注释（spec 4.1）；写适配器；删 SMTP 适配器及其测试；改 `IdentityConfiguration` 与其测试（`IdentityConfigurationTest` 去掉 `JavaMailSender` 桩与 `SmtpEmailDeliveryAdapter` 断言，改为断言 `IdentityConfiguration` 不再提供 `EmailDeliveryPort` 与 `ThymeleafMailRenderer`）；改五个 Handler（spec 4.4、4.5），类注释同步。
- [ ] **Step 5:** `./gradlew :patra-api:patra-identity:patra-identity-app:test :patra-api:patra-identity:patra-identity-infra:test :patra-api:patra-identity:patra-identity-boot:test` PASS。

### Task 4: 其余七个 Handler 把登记挪进事务

**Files:** `RegisterUserHandler`、`ResendEmailVerificationHandler`、`RequestPasswordResetHandler`、`ResetPasswordHandler`、`ChangePasswordHandler`、`RequestEmailChangeHandler`、`ConfirmEmailChangeHandler`、`RevertEmailChangeHandler` 及各自单测（`app/.../usecase/**`）。

**Interfaces:**
- Consumes: Task 3 的端口。

- [ ] **Step 1: 失败测试**（`RecordingTransactions` 或已有的 `recording(...)`）：
  - 每个：`send` 落在事务里（原来断言 `isFalse()` 的改 `isTrue()`）。
  - `RegisterUserHandler`：顺序 `issue → send → tx-end → markSent`。
  - `ChangePasswordHandler`：`send` 在 `sessions.rotate` 之前。
  - `ResetPasswordHandler` / `ChangePasswordHandler` / `ConfirmEmailChangeHandler` / `RevertEmailChangeHandler`：`resolver.resolve` 在 `tx-begin` 之前。
  - `ConfirmEmailChangeHandler`：事务里先后登记两封：`EmailChangedNoticeMail` 的 `to` 是旧地址、`newEmailMasked` 是新地址遮掩、`linkExpiresAt` 是撤销令牌的过期时间；`EmailChangedConfirmationMail` 的 `to` 是新地址。
  - `RequestEmailChangeHandler`：`EmailChangeConfirmMail.to` 是新地址。
- [ ] **Step 2:** `./gradlew :patra-api:patra-identity:patra-identity-app:test` FAIL。
- [ ] **Step 3:** 改代码（spec 4.4 表），类注释里「事务提交后发信 / 异步发信」改为「事务里登记」，日志「已排队」改「已登记」。
- [ ] **Step 4:** 同一命令 PASS。

### Task 5: 集成事件、映射器与外送配置

**Files:**
- Create: `patra-identity-api/src/main/java/dev/patra/identity/api/account/event/`：`SecurityMailEvent.java`、`OperationContext.java`、13 个 `*MailEvent.java`
- Create: `patra-identity-boot/src/main/java/dev/patra/identity/config/SecurityMailEventMapper.java`、`IdentityEventsConfiguration.java`
- Create: `patra-identity-boot/src/test/java/dev/patra/identity/config/SecurityMailEventMapperTest.java`、`IdentityEventsConfigurationTest.java`
- Modify: `patra-identity-boot/build.gradle.kts`（`patra-tool-events`、`patra-identity-api`）、`patra-identity-boot/src/main/resources/application.yml`（`spring.modulith.events.completion-mode: delete`，注释「安全邮件带一次性令牌，发完即删」）、`patra-infra/cd/module-graph.json`

**Interfaces:**
- Consumes: Task 2 的记录与 `SecurityMailFixture`。
- Produces:
  - `sealed interface SecurityMailEvent extends IntegrationEvent`：`String EXCHANGE = "identity.events"`、`String ROUTING_KEY_PATTERN = "security-mail.*"`、`String to()`、`String displayName()`。
  - `OperationContext(Instant occurredAt, String device, String ipAddress, String ipLocation)`，`of(...)`，`occurredAt` 非空。
  - 13 个记录与字段见 spec 4.6 的表；每个有 `public static final String ROUTING_KEY`；≥ 5 分量 `@Builder`，否则 `of(...)`；`eventId`、`to`、`displayName`、链接、`context` 非空。
  - `SecurityMailEventMapper(MailLinkPolicy links)`：`SecurityMailEvent toIntegrationEvent(OutgoingMail mail)`（`default` 抛 `IllegalArgumentException`）；`static String routingKeyOf(SecurityMailEvent event)`（对 sealed 接口穷举，无 `default`）。
  - `IdentityEventsConfiguration`：`TopicExchange identityEventsExchange()`、`SecurityMailEventMapper securityMailEventMapper(MailLinkPolicy)`、`EventExternalizationConfiguration eventExternalizationConfiguration(SecurityMailEventMapper)`（`selectByType(OutgoingMail.class)`、`mapping(OutgoingMail.class, mapper::toIntegrationEvent)`、`routeAll(...)` 用 `routingKeyOf` 后 `routeMapped()`）。

- [ ] **Step 1: 映射器失败测试**（参数化，样本来自 `SecurityMailFixture.all()`）：
  - `should_map_every_mail_with_same_event_id_and_recipient`：集成事件的 `eventId`、`to().equals(mail.to().value())`、`displayName` 原样。
  - 逐类字段：链接与 `linkExpiresAt` 原样；`EmailChangedNoticeMailEvent.newEmailMasked`；`context` 的四项来自 `SecurityNotice`（全 `null` 的那个仍为 `null`）；`securitySettingsUrl` 只出现在三封、等于 `links.securitySettingsUrl()`；`daysLeft`、`deletionDueAt`、`username`。
  - `should_route_every_event_under_security_mail_prefix`：13 个路由键互不相同，都等于 `"security-mail." + mail.template()`，都匹配 `security-mail.*`（不含 `.`）。
  - `should_cover_every_outgoing_mail_type`：ArchUnit `new ClassFileImporter().importPackages("dev.patra.identity.domain")` 里实现 `OutgoingMail` 的非接口类集合 == 样本类型集合。
  - `should_not_leak_recipient_or_link_in_integration_event_to_string`。
- [ ] **Step 2: 外送配置失败测试**：`supports(每个样本)` 为真、`supports(new Object())` 为假；`map(样本)` 是对应 `*MailEvent`；`determineTarget(样本)` 的 target 是 `identity.events`、key 是 `routingKeyOf(map(样本))`；交换机 bean 是持久化的 topic。
- [ ] **Step 3:** `./gradlew :patra-api:patra-identity:patra-identity-boot:test --tests "*SecurityMailEventMapperTest*" --tests "*IdentityEventsConfigurationTest*"` FAIL。
- [ ] **Step 4:** 写 api 记录、映射器、配置；boot 依赖；yml。
- [ ] **Step 5:** 同一命令 PASS；`./gradlew dumpModuleGraph` 后 `git diff patra-infra/cd/module-graph.json` 只多 identity-boot → events / identity-api 的边。

### Task 6: 登记表迁移与 IT 基座换成 RabbitMQ

**Files:**
- Create: `patra-identity-infra/src/main/resources/db/migration/V20261031025039__create_event_publication.sql`（照抄事件模块模板，头注释写明 Modulith 固定表名、DELETE 模式）
- Create（`patra-identity-boot/src/integrationTest/java/dev/patra/identity/config/`）：`IdentitySecurityMailITSpringConfig.java`、`IdentitySecurityMailITFixture.java`、`IdentitySecurityMailDrainTestExecutionListener.java`
- Delete: `IdentityMailITSpringConfig.java`、`IdentityMailDrainTestExecutionListener.java`
- Modify: `integrationTest/resources/META-INF/spring.factories`、`integrationTest/resources/application-test.yml`（`patra.events.consumer.trusted-packages: dev.patra.identity.api.account.event`）
- Modify: 所有带 `GreenMailSmtpInitializer` / `IdentityMailITSpringConfig` 的 IT 与 `IdentityFlowITSupport`：换 `RabbitMQContainerInitializer` 与 `IdentitySecurityMailITSpringConfig`
- Modify: `PatraIdentityApplicationIT.java`
- Create: `patra-identity-boot/src/integrationTest/java/dev/patra/identity/SecurityMailOutboxIT.java`

**Interfaces:**
- Consumes: Task 3 的适配器、Task 5 的配置与记录、`SecurityMailFixture`。
- Produces:
  - `IdentitySecurityMailITSpringConfig.QUEUE = "identity-it.security-mail"`；bean `Declarables`（`EventQueue.named(QUEUE).bind(SecurityMailEvent.EXCHANGE, SecurityMailEvent.ROUTING_KEY_PATTERN)`）与 `IdentitySecurityMailITFixture`。
  - `IdentitySecurityMailITFixture(RabbitTemplate, AmqpAdmin, JdbcClient)`：`void purge()`（清队列与本用例缓冲）、`void awaitRegistryDrained()`（`event_publication` 行数为 0，最多 10 秒，超时抛 `IllegalStateException` 列出剩余行的 `event_type` 与 `status`）、`<T extends SecurityMailEvent> T await(Class<T> type, String recipient)`（拉取并缓冲，最多 5 秒）、`<T extends SecurityMailEvent> List<T> all(Class<T> type, String recipient)`（先 `awaitRegistryDrained()` 再拉空队列）、`int registryRows()`。
  - `IdentityFlowITSupport`：`@Autowired IdentitySecurityMailITFixture securityMails`；`@BeforeEach` 调 `purge()`；`<T extends SecurityMailEvent> T mailEvent(Class<T> type, String recipient)`；`static String tokenIn(String url)` 保留。删 GreenMail 相关帮助方法。

- [ ] **Step 1: 失败测试**：
  - `PatraIdentityApplicationIT.should_create_event_publication_table`：`information_schema.tables` 有 `event_publication`。
  - `SecurityMailOutboxIT`（继承 `IdentityFlowITSupport`）：
    - `should_reject_send_outside_transaction`：直接调 `EmailDeliveryPort.send(样本)` 抛 `IllegalTransactionStateException`。
    - `should_round_trip_every_domain_mail_through_registry_serializer`：上下文的 Modulith `EventSerializer`，`deserialize(serialize(样本), 样本.getClass())` 等于样本（13 个）。
    - `should_round_trip_every_integration_event_through_message_converter`：上下文的 `MessageConverter`，`fromMessage(toMessage(映射后的事件, new MessageProperties()))` 等于原事件。
    - `should_delete_registry_row_after_delivery_and_use_delete_mode`：注册一个用户 → 收到 `EmailVerificationMailEvent`、`registryRows()` 归零；环境里 `spring.modulith.events.completion-mode` 为 `delete`。
- [ ] **Step 2:** `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*PatraIdentityApplicationIT*" --tests "*SecurityMailOutboxIT*"` FAIL。
- [ ] **Step 3:** 迁移、基座、各 IT 的初始化器与导入（只换这两处，流程断言留给 Task 7）。
- [ ] **Step 4:** 同一命令 PASS。

### Task 7: 流程 IT 改断言事件（验收 3、4）

**Files（`patra-identity-boot/src/integrationTest/java/dev/patra/identity/`）：** `EmailVerificationFlowIT`、`PasswordResetFlowIT`、`PasswordChangeFlowIT`、`PasswordChangeRollbackIT`、`EmailChangeFlowIT`、`AccountDeletionFlowIT`、`LoginDeviceFlowIT`，以及其他只用到收信计数的 IT。

**Interfaces:**
- Consumes: Task 6 的 `mailEvent`、`securityMails.all(...)`。

- [ ] **Step 1:** 逐个改：`tokenInMail(地址, 主题)` → `tokenIn(mailEvent(类型, 地址).链接())`；`waitForMails(n)` 删掉，改成对需要的事件 `mailEvent(...)`；「收到 N 封」→ `securityMails.all(类型, 地址)).hasSize(N)`；正文里的设备、IP、属地 → `context().device()` / `ipAddress()` / `ipLocation()`；纯模板措辞断言删除（渲染器单测仍覆盖）。
  - `EmailChangeFlowIT`（验收 3）：申请后 `mailEvent(EmailChangeConfirmMailEvent.class, 新地址)`；确认后 `mailEvent(EmailChangedNoticeMailEvent.class, 旧地址)` 的 `newEmailMasked` 等于新地址遮掩、`revertUrl` 带令牌，`mailEvent(EmailChangedConfirmationMailEvent.class, 新地址)`；`all(EmailChangedConfirmationMailEvent.class, 旧地址)` 与 `all(EmailChangedNoticeMailEvent.class, 新地址)` 为空。原 `greenMail.getReceivedMessages()).hasSize(2)` 改为对应事件计数。
  - `PasswordResetFlowIT`：已注册 5 个 `PasswordResetMailEvent`、未注册 0 个；耗时比 < 10 保留。
  - `PasswordChangeRollbackIT`：回滚后 `all(PasswordChangedMailEvent.class, 地址)` 为空。
  - `LoginDeviceFlowIT`：`NewDeviceLoginMailEvent` 的 `context` 与 `securitySettingsUrl`；冷却内不再有第二个。
  - `AccountDeletionFlowIT`：提醒、完成各 1 个；完成事件的 `to` 是注销前的邮箱、`username` 原样。
- [ ] **Step 2:** `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest` 全 PASS（个别网关/超时类抖动单独重跑判定）。

### Task 8: 重启后补投（验收 1、2）

**Files:**
- Create: `patra-identity-boot/src/integrationTest/java/dev/patra/identity/SecurityMailRestartIT.java`

**Interfaces:**
- Consumes: Task 6 的基座类、`RabbitMQContainerInitializer.getRabbitMQContainer()`、`EventPublicationResubmitter`。

- [ ] **Step 1: 测试** `should_deliver_registered_mail_after_identity_restarts`（`@Timeout(3 分钟)`）：
  1. `rabbitmqctl stop_app`；用 `SpringApplicationBuilder(PatraIdentityApplication.class, IdentitySecurityMailITSpringConfig.class)`，profile `test`，初始化器 PG / Redis / RabbitMQ，`server.port=0`、`patra.events.resubmit.min-age=1ms` 起实例 A。
  2. 经 A 的 HTTP 注册一个新邮箱，201；`event_publication` 恰 1 行、`status` 不为空且行一直在（等 15 秒内变成 `FAILED`）；读出 `serialized_event` 里的 `eventId`。
  3. 关掉 A；`rabbitmqctl start_app`；同样参数起实例 B；`B.getBean(EventPublicationResubmitter.class).resubmit()` 返回 1。
  4. B 的 `IdentitySecurityMailITFixture.await(EmailVerificationMailEvent.class, 邮箱)` 的 `eventId` 等于第 2 步读出的；用链接里的令牌经 B 验证邮箱成功（接口与断言照 `EmailVerificationFlowIT`）；`registryRows()` 为 0。
  - `@AfterEach`：broker 若停着就启动、关掉还开着的实例、删掉残留登记行。
- [ ] **Step 2:** `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*SecurityMailRestartIT*"` PASS。它是 Task 3–6 组合行为的验收测试，不另写实现；失败时按 systematic-debugging 查，不改测试迁就。

### Task 9: 文档、独立评审、全量检查、提交

**Files:**
- Modify: `docs/patra/runbooks/v0.16-notification-go-live-runbook.md`（spec 第 7 节）
- Modify: `patra-api/patra-identity/README.md`（发信一节：改为发事件，链接本 spec）
- Modify（若提到进程内发信）：`patra-tool/patra-tool-events/README.md` 不动；`.claude/skills/patra-backend/skills/patra-events/` 不动

- [ ] **Step 1:** 写 runbook 与 README。
- [ ] **Step 2:** 派独立评审子代理（`model: "opus"`，只给 diff 范围与 spec）；重要问题先写复现测试再修。
- [ ] **Step 3:** `./gradlew spotlessApply` 后 `./gradlew check integrationTest --continue` 全绿。
- [ ] **Step 4:** `git add` 自己的路径，`git diff --cached --stat` 核对后提交：`feat(identity): 改发事件：13 类安全邮件与业务同事务登记，经事件模块外送 (PAP-104)`。

### Task 10（10/31 范围调整，编排会话改派）：删掉 identity 整套发信代码

渲染器、模板、`spring-boot-starter-mail` 与 `thymeleaf`、各 `*Mail` 的模板名 / 主题 / 文案、`SecurityNotice.notMeAdvice`（改 `of()`）、`ClientOrigin.toNotice(Instant)`、`mail.from`、`management.health.mail`、测试 yml 与 Nacos 的 `spring.mail.*`；只测文案的 9 个 `*MailTest` 删除。`ApplicationConfigTest` 先改成「jar 里没有邮件服务商、发件人与邮件健康检查的键」看它失败，再删配置。Flyway 文件改用时间戳版本号 `V20261031025039__create_event_publication.sql`。验证：identity 各模块 `test` 与 `integrationTest`。
