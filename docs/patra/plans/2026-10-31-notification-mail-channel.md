# 通知服务邮件渠道 实施计划（PAP-105）

> **For agentic workers:** 本计划在当前会话内联执行（superpowers:executing-plans），不用 subagent-driven。步骤用 `- [ ]` 勾选。

**Goal:** notification 的 mail 子域收 identity 的 13 类安全邮件事件，经 SMTP 发出，带 `Resend-Idempotency-Key`，有重试、死信与发送记录。

**Architecture:** adapter 的 `SecurityMailListener` 收 sealed `SecurityMailEvent`，转成领域的 `SecurityMail`，经命令总线交给 `SendSecurityMailHandler`：查发送记录 → 事务外经 `MailDeliveryPort` 发 → 短事务里 `markProcessed` + 记录。infra 的 `SmtpMailDeliveryAdapter` 用 Thymeleaf 渲染搬来的模板。

**Tech Stack:** Java 25、Spring Boot 4.1.2、Spring AMQP 4.1.2、patra-tool-events、Jakarta Mail（`spring-boot-starter-mail`）、Thymeleaf 引擎、JdbcClient、Flyway；测试 JUnit 5 + AssertJ + Mockito、Testcontainers（PG、RabbitMQ）、GreenMail。

**Spec:** `docs/patra/specs/2026-10-31-notification-mail-channel-design.md`

## Global Constraints

- 根包 `dev.patra.notification`；分包按 `.claude/rules/tech/package-layout.md`；mail 子域不 import 其他子域（不加依赖边）。
- 队列名 `notification.security-mail`；绑定 `SecurityMailEvent.EXCHANGE`（`identity.events`）/ `SecurityMailEvent.ROUTING_KEY_PATTERN`（`security-mail.*`）。
- 幂等键头 `Resend-Idempotency-Key`，值 = `deliveryId.toString()`（安全邮件 = 事件 ID）；另两个固定头 `Auto-Submitted: auto-generated`、`X-Auto-Response-Suppress: All`；安全邮件不带 `List-Unsubscribe`、`List-Unsubscribe-Post`。
- 时间显示 `yyyy-MM-dd HH:mm (UTC+8)`（照 identity `SecurityNoticeView.formatTime`），显示时区配置 `patra.notification.mail.zone`，jar 默认 `Asia/Shanghai`。
- 发件人 `patra.notification.mail.from`，Nacos 里 `${PATRA_MAIL_FROM:Patra <no-reply@patra.local>}`。
- 事务里不发信；Handler 不标 `@Transactional`，用 `TransactionOperations`；infra 只允许 `MANDATORY`。
- 日志、`toString()`、异常消息里不出现收件地址、链接、正文；只出现 `eventId` / `deliveryId` 与模板名。
- 模板从 `b047dec36` 用 `git show b047dec36:patra-api/patra-identity/patra-identity-infra/src/main/resources/mail/<路径>` 取，除第 7 节表里的有效期写法外逐字不改。
- 构建依赖行写法与 PAP-142（`c5eeebcd5`）一致：app `api(project(":patra-api:patra-notification:patra-notification-domain"))`、`api(project(":patra-tool:patra-tool-cqrs"))`、`api("org.springframework:spring-tx")`；adapter `api(project(":patra-api:patra-notification:patra-notification-app"))`；infra `api(project(":patra-api:patra-notification:patra-notification-domain"))`。
- 所有方法写 `///` Javadoc；中文注释；改了 `*.gradle.kts` 重跑 `./gradlew dumpModuleGraph` 并提交 `patra-infra/cd/module-graph.json`。
- commit message 中文、subject 以中文动词开头；只 `git add` 自己的路径，提交前看 `git diff --cached --stat`。
- Gradle 命令一律在 worktree 根用绝对路径：`/Users/linqibin/Projects/Products/patra/.claude/worktrees/v016-pap105-9986d4/gradlew -p /Users/linqibin/Projects/Products/patra/.claude/worktrees/v016-pap105-9986d4 …`（下文简写 `./gradlew`）。

## Review Focus

1. 新设备登录这类事件的 `context` 只有时间、设备 / IP / 属地全为 null：要渲染成「未知」，不能 NPE（Task 1、Task 3 各有一例）。
2. `now` 恰好等于 `linkExpiresAt`：按已过期处理（Task 1）。
3. 同一封信渲染两次必须逐字节相同（Resend 同键不同内容会 409）（Task 3）。
4. 类型头不在 `trusted-packages` 里、或 JSON 坏掉的消息：不重试、直接进死信，不拖垮监听（Task 7 死信 IT 加一例）。
5. 重试耗尽进死信时的日志里没有链接与收件地址（Task 7 死信 IT）。

---

### Task 0: identity-api 事件 record 的临时副本（单独提交）

PAP-104 合入后整体丢弃、以它的为准（spec 第 4 节）。这一步是别的 Issue 的契约副本，不写测试，由后续任务的消费端测试覆盖。

**Files:**
- Create: `patra-api/patra-identity/patra-identity-api/src/main/java/dev/patra/identity/api/account/event/`：`SecurityMailEvent.java`、`OperationContext.java` 与 13 个 `*MailEvent.java`

**Interfaces:**
- Produces：
  - `sealed interface SecurityMailEvent extends IntegrationEvent permits …13 个`：`String EXCHANGE = "identity.events"`、`String ROUTING_KEY_PATTERN = "security-mail.*"`、`String to()`、`String displayName()`
  - `record OperationContext(Instant occurredAt, String device, String ipAddress, String ipLocation)`，`of(...)`，`occurredAt` 非空
  - 13 个 record 的分量按 PAP-104 契约表，顺序都是 `eventId, to, displayName, …`；≤ 4 个分量用 `of()`，≥ 5 个用 `@Builder`；各有 `ROUTING_KEY = "security-mail.<模板名>"`；`toString()` 只输出 `eventId`

- [ ] **Step 1:** 按契约写 15 个文件（`OperationContext` 与 4 分量的 `PasswordResetDoneMailEvent`、`PasswordChangedMailEvent`、`AccountDeletionCancelledMailEvent`、`AccountDeletionCompletedMailEvent` 用 `of()`，其余 `@Builder`）。
- [ ] **Step 2:** `./gradlew :patra-api:patra-identity:patra-identity-api:compileJava` 通过。
- [ ] **Step 3:** 提交：`git commit -m "chore(identity): 按 PAP-104 契约临时补上安全邮件集成事件 record"`，正文写明「PAP-104 合入后以其为准、整体丢弃本提交」。

### Task 1: 领域模型

**Files:**
- Create（`patra-notification-domain/src/main/java/dev/patra/notification/domain/mail/`）：`model/enums/SecurityMailKind.java`、`model/vo/OperationContext.java`、`model/vo/SecurityNotice.java`、`model/vo/SecurityMail.java`、`model/vo/OutgoingMail.java`
- Test：`patra-notification-domain/src/test/java/dev/patra/notification/domain/mail/model/enums/SecurityMailKindTest.java`、`…/model/vo/SecurityMailTest.java`、`…/model/vo/OutgoingMailTest.java`

**Interfaces:**
- Produces：
  - `enum SecurityMailKind`：`EMAIL_VERIFICATION, PASSWORD_RESET, PASSWORD_RESET_DONE, PASSWORD_CHANGED, EMAIL_CHANGE_CONFIRM, EMAIL_CHANGED_NOTICE, EMAIL_CHANGED_CONFIRMATION, EMAIL_CHANGE_REVERTED, NEW_DEVICE_LOGIN, ACCOUNT_DELETION_REQUESTED, ACCOUNT_DELETION_CANCELLED, ACCOUNT_DELETION_REMINDER, ACCOUNT_DELETION_COMPLETED`；`String template()`（= 路由键后缀）、`String subjectFor(Map<String, Object> facts)`、`String notMeAdvice()`（无安全通知片段的 5 类为 null）、`boolean hasNotice()`、`boolean skipsWhenLinkExpired()`（前三类链接信：`EMAIL_VERIFICATION`、`PASSWORD_RESET`、`EMAIL_CHANGE_CONFIRM`）
  - `record OperationContext(Instant occurredAt, String device, String ipAddress, String ipLocation)`，`of(...)`
  - `record SecurityNotice(OperationContext context, String notMeAdvice)`，`of(...)`，两项非空
  - `@Builder record SecurityMail(UUID eventId, SecurityMailKind kind, String to, String displayName, Map<String, Object> facts, OperationContext context, Instant linkExpiresAt)`；`OutgoingMail compose()`、`boolean linkExpiredAt(Instant now)`
  - `@Builder record OutgoingMail(UUID deliveryId, String to, String template, String subject, Map<String, Object> model)`

主题与「不是我怎么办」原文取自 `b047dec36` 的 `patra-identity-domain/.../model/vo/mail/*Mail.java` 的 `SUBJECT`、`NOT_ME_ADVICE`；注销提醒主题 `"你的 Patra 账号将在 " + facts.get("daysLeft") + " 天后删除"`。

- [ ] **Step 1: 写失败的测试**
  - `SecurityMailKindTest.should_use_routing_key_suffix_as_template`：13 个值的 `template()` 依次是 `email-verification`、`password-reset`、`password-reset-done`、`password-changed`、`email-change-confirm`、`email-changed-notice`、`email-changed-confirmation`、`email-change-reverted`、`new-device-login`、`account-deletion-requested`、`account-deletion-cancelled`、`account-deletion-reminder`、`account-deletion-completed`。
  - `should_keep_identity_subjects`：`PASSWORD_RESET.subjectFor(Map.of())` 为 `重设你的 Patra 密码`；`ACCOUNT_DELETION_REMINDER.subjectFor(Map.of("daysLeft", 3L))` 为 `你的 Patra 账号将在 3 天后删除`；其余 11 类各断言一次原文。
  - `should_have_advice_exactly_for_notice_kinds`：`hasNotice()` 为真的恰好是 8 类（密码已重设、密码已修改、邮箱已更改、新邮箱已绑定、邮箱已改回、新设备登录、注销已提交、已取消注销），它们的 `notMeAdvice()` 等于 identity 原文，其余 5 类为 null。
  - `should_skip_only_link_purpose_kinds_when_expired`：`skipsWhenLinkExpired()` 为真的恰好是三类链接信。
  - `SecurityMailTest.should_compose_outgoing_mail_with_event_id_as_delivery_id`：`compose()` 的 `deliveryId` = `eventId`，`template`、`subject` 取自 kind，`model` 含 `displayName` 与全部 `facts`。
  - `should_put_notice_and_link_expiry_into_model`：通知类 `model.get("notice")` 是 `SecurityNotice`（advice = kind 的原文）；有 `linkExpiresAt` 时 `model.get("linkExpiresAt")` 是那个 `Instant`。
  - `should_compose_when_context_has_only_time`：`OperationContext.of(t, null, null, null)` 的新设备登录信 `compose()` 不抛异常。
  - `should_require_context_for_notice_kinds`：通知类缺 `context` 抛 `NullPointerException`（消息含 `context`）。
  - `should_treat_link_as_expired_at_exact_instant`：`linkExpiredAt(linkExpiresAt)` 为 true，早一纳秒为 false；非链接信即使过期也为 false。
  - `should_not_print_recipient_or_link`：`toString()` 不含收件地址与 `facts` 里的链接，含 `eventId`。
  - `OutgoingMailTest.should_copy_model_and_reject_null_values`：传入可变 Map 后修改原 Map 不影响记录；值为 null 时抛 NPE；`toString()` 只含 `deliveryId` 与 `template`。
- [ ] **Step 2:** `./gradlew :patra-api:patra-notification:patra-notification-domain:test --tests "*mail*"` FAIL（类不存在）。
- [ ] **Step 3:** 实现上述 5 个类型。`SecurityMail` 紧凑构造：四个必填非空；`facts` 为 null 时 `Map.of()`，否则 `Map.copyOf`；`kind.hasNotice()` 时 `context` 非空。
- [ ] **Step 4:** 同一命令 PASS；`./gradlew :patra-api:patra-notification:patra-notification-domain:check` 通过（领域纯净）。
- [ ] **Step 5:** 提交：`feat(notification): 新增安全邮件的领域模型与 13 类文案`。

### Task 2: 监听与转换（先证明接口参数能按类型头反序列化）

**Files:**
- Create：`patra-notification-app/src/main/java/dev/patra/notification/app/mail/usecase/security/SendSecurityMailCommand.java`、`SendSecurityMailResult.java`
- Create：`patra-notification-adapter/src/main/java/dev/patra/notification/adapter/mail/mq/SecurityMailListener.java`、`SecurityMailEventConverter.java`
- Create：`patra-notification-boot/src/main/java/dev/patra/notification/config/MailQueueConfiguration.java`
- Modify：app、adapter 的 `build.gradle.kts`；boot `src/main/resources/application.yml`（`patra.events.consumer.trusted-packages`）
- Test：`patra-notification-adapter/src/test/java/dev/patra/notification/adapter/mail/mq/SecurityMailEventConverterTest.java`；`patra-notification-boot/src/integrationTest/java/dev/patra/notification/mail/SecurityMailListenerIT.java`

**Interfaces:**
- Consumes：Task 0 的 record；Task 1 的 `SecurityMail`、`SecurityMailKind`、`OperationContext`
- Produces：
  - `record SendSecurityMailCommand(String consumer, SecurityMail mail) implements Command<SendSecurityMailResult>`，`of(...)`
  - `enum SendSecurityMailResult { SENT, DUPLICATE, LINK_EXPIRED }`
  - `SecurityMailListener`：常量 `public static final String QUEUE = "notification.security-mail"`；`@RabbitListener(queues = QUEUE) public void on(SecurityMailEvent event)` → `commandBus.handle(SendSecurityMailCommand.of(QUEUE, SecurityMailEventConverter.toSecurityMail(event)))`
  - `final class SecurityMailEventConverter`：`static SecurityMail toSecurityMail(SecurityMailEvent event)`，sealed 穷举 `switch`、无 `default`
  - `MailQueueConfiguration`：`@Bean Declarables securityMailQueue()`

`facts` 的键与模板变量同名：`verifyUrl`、`resetUrl`、`confirmUrl`、`revertUrl`、`newEmailMasked`、`securityUrl`（取事件的 `securitySettingsUrl`）、`deletionDueAt`、`daysLeft`、`username`。

- [ ] **Step 1: 写失败的 IT** `SecurityMailListenerIT.should_dispatch_interface_typed_event_by_type_header`：`@SpringBootTest` + `NotificationITPostgreSQLContainerInitializer` + `RabbitMQContainerInitializer`，`@MockitoBean CommandBus`；`rabbitTemplate.convertAndSend(SecurityMailEvent.EXCHANGE, PasswordResetMailEvent.ROUTING_KEY, event)`；`verify(commandBus, timeout(5_000)).handle(captor)`，断言 `consumer` 为 `notification.security-mail`、`mail().kind()` 为 `PASSWORD_RESET`、`mail().eventId()` 为事件 ID、`facts` 的 `resetUrl` 为事件里的链接。
- [ ] **Step 2:** `./gradlew :patra-api:patra-notification:patra-notification-boot:integrationTest --tests "*SecurityMailListenerIT*"` FAIL。
- [ ] **Step 3:** 加依赖（adapter：`api(app)`、`implementation(project(":patra-api:patra-identity:patra-identity-api"))`、`implementation("org.springframework.boot:spring-boot-starter-amqp")`；app：PAP-142 的三行），写命令、结果、监听、转换（这一步 13 个分支都写全）、队列声明、`trusted-packages`（YAML 列表）。
- [ ] **Step 4:** 同一命令 PASS。**若接口参数反序列化不了**：改成类级 `@RabbitListener` + 每类一个 `@RabbitHandler`，测试不变，在 spec 6.4 记一行偏离。
- [ ] **Step 5: 写失败的单测** `SecurityMailEventConverterTest`：13 个用例各断言 `kind`、`to`、`displayName`、`facts` 键集合、`context`（通知类非空、字段原样）、`linkExpiresAt`；`should_map_security_settings_url_to_template_variable`：`securityUrl` = 事件的 `securitySettingsUrl`。
- [ ] **Step 6:** 跑 `./gradlew :patra-api:patra-notification:patra-notification-adapter:test` 通过（转换已在 Step 3 写全，若有错按测试改）。
- [ ] **Step 7:** `./gradlew dumpModuleGraph`；提交：`feat(notification): 收 identity 安全邮件事件并转成领域模型`。

### Task 3: 模板与渲染器

**Files:**
- Create：`patra-notification-infra/src/main/resources/mail/**`（13 × html + 13 × txt + `html/fragments/{layout,button,security-notice}.html` + `text/fragments/{header,footer,security-notice}.txt`）
- Create：`patra-notification-infra/src/main/java/dev/patra/notification/infra/mail/delivery/ThymeleafMailRenderer.java`、`RenderedMail.java`、`SecurityNoticeView.java`
- Create：`patra-notification-infra/src/test/resources/mail/{html/security-notice-probe.html,text/security-notice-probe.txt}`（从 identity 测试资源拷）
- Modify：infra `build.gradle.kts`
- Test：`patra-notification-infra/src/test/java/dev/patra/notification/infra/mail/delivery/ThymeleafMailRendererTest.java`

**Interfaces:**
- Consumes：Task 1 的 `OutgoingMail`、`SecurityNotice`、`OperationContext`、`SecurityMail`
- Produces：`ThymeleafMailRenderer(ZoneId zone)`，`RenderedMail render(OutgoingMail mail)`；`record RenderedMail(String subject, String text, String html)`；`record SecurityNoticeView(String occurredAt, String device, String ip, String notMeAdvice)`，`static SecurityNoticeView from(SecurityNotice notice, ZoneId zone)`、包内 `static String formatTime(Instant, ZoneId)`

- [ ] **Step 1:** 用 `git show` 把模板与探针拷进来；只改有效期一句（spec 第 7 节）：HTML 按钮的 note 参数改为 `|${linkExpiresAt} 前有效 · 只能用一次|`（原来有「只能用一次」的保留，没有的不加），文本模板同句同改。
- [ ] **Step 2: 写失败的测试**（以 identity 的 `ThymeleafMailRendererTest` 为底改写，样本改用 `SecurityMail.builder()…build().compose()`）：
  - `should_render_verification_mail_in_text_and_html`：同 identity 原用例，把 `"24 小时"` 换成 `"2026-10-22 16:00 (UTC+8) 前有效"`（`linkExpiresAt = 2026-10-22T08:00:00Z`）。
  - `should_declare_light_and_dark_color_schemes_in_every_html`：13 类都有。
  - `should_escape_html_in_display_name_but_keep_text_raw`。
  - `should_render_security_notice_fragment_with_unknown_placeholders`：设备、IP、属地全 null 时显示「未知」三次，时间 `2026-10-21 16:30 (UTC+8)`。
  - `should_render_ip_with_location`：`203.0.113.9（中国 上海）`。
  - `should_render_every_kind_without_leftover_expressions`：13 类的 text 不含 `[(`、`${`，html 不含 `${`、无 `th:` 属性；各自的链接（有的话）在 text 与 html 各出现。
  - `should_render_identically_twice`：同一个 `OutgoingMail` 渲染两次，三个字段 `isEqualTo`。
  - `should_render_security_url_only_where_present`：新设备登录的 html 含 `href="<securityUrl>"`。
  - 其余 identity 原用例里与 `MailLinkPolicy`、`validMinutes`/`validHours`/`validDays` 有关的断言改成 `linkExpiresAt` 写法后保留。
- [ ] **Step 3:** `./gradlew :patra-api:patra-notification:patra-notification-infra:test --tests "*ThymeleafMailRendererTest*"` FAIL。
- [ ] **Step 4:** 从 identity 搬渲染器三个类：去掉 `MailLinkPolicy` 参数与 `securityUrl` 公共变量；`notice` 是 `SecurityNotice` 时换成 `SecurityNoticeView.from`；值为 `Instant` 的变量照旧格式化。infra 加 `api("org.springframework.boot:spring-boot-starter-mail")`（注释：boot 的装配签名用 `JavaMailSender`）、`implementation("org.thymeleaf:thymeleaf")`、`api(project(":patra-api:patra-notification:patra-notification-domain"))`。
- [ ] **Step 5:** 同一命令 PASS。
- [ ] **Step 6:** 提交：`feat(notification): 搬入安全邮件模板与渲染器，有效期改写成绝对时刻`。

### Task 4: SMTP 发信适配器

**Files:**
- Create：`patra-notification-domain/src/main/java/dev/patra/notification/domain/mail/port/delivery/MailDeliveryPort.java`、`…/domain/mail/exception/MailDeliveryFailedException.java`
- Create：`patra-notification-infra/src/main/java/dev/patra/notification/infra/mail/delivery/SmtpMailDeliveryAdapter.java`
- Test：`patra-notification-infra/src/test/java/dev/patra/notification/infra/mail/delivery/SmtpMailDeliveryAdapterTest.java`

**Interfaces:**
- Consumes：Task 3 的 `ThymeleafMailRenderer`
- Produces：`interface MailDeliveryPort { void deliver(OutgoingMail mail); }`；`MailDeliveryFailedException(OutgoingMail mail, String reason)`（`DomainException`，`StandardErrorTrait.DEP_UNAVAILABLE`，消息 `邮件发送失败: template=…, deliveryId=…, reason=…`，**没有 cause**）；`SmtpMailDeliveryAdapter(JavaMailSender mailSender, ThymeleafMailRenderer renderer, String from)`，常量 `static final String IDEMPOTENCY_KEY_HEADER = "Resend-Idempotency-Key"`；包内 `static String describe(Throwable failure)`：沿 cause 链每层 `类名: 消息` 用 ` <- ` 连接，邮箱地址（正则 `[^\s<>"'@]+@[^\s<>"']+`）换成 `***`，超过 500 字符截断

- [ ] **Step 1: 写失败的测试**（以 identity `SmtpEmailDeliveryAdapterTest` 为底，去掉执行器与异步相关的用例）：
  - `should_send_multipart_alternative_with_fixed_headers`：发件人、唯一收件人、主题、文本在前 HTML 在后；`Auto-Submitted`、`X-Auto-Response-Suppress` 各一个值；`Resend-Idempotency-Key` 恰为 `deliveryId.toString()`；无 `List-Unsubscribe`。
  - `should_use_same_idempotency_key_for_each_attempt`：同一个 `OutgoingMail` 发两次，两封的 `Resend-Idempotency-Key` 相同。
  - `should_wrap_send_failure_without_leaking_recipient`：`JavaMailSender.send` 抛 `new MailSendException("550 5.1.1 <chen.yu@example.com>: Recipient address rejected")` → `MailDeliveryFailedException`，`getCause()` 为 null，消息含 `deliveryId`、模板名、`550`、`MailSendException`，不含 `chen.yu@example.com`。
  - `should_truncate_long_failure_reason`：原因超过 500 字符时截断。
  - `should_wrap_invalid_recipient`：收件人 `not an address` → `MailDeliveryFailedException`，不调 `send`。
  - `should_reject_invalid_from_at_construction`：发件人两个地址或非法 → `IllegalArgumentException`。
- [ ] **Step 2:** `./gradlew :patra-api:patra-notification:patra-notification-infra:test --tests "*SmtpMailDeliveryAdapterTest*"` FAIL。
- [ ] **Step 3:** 实现端口、异常与适配器（同步发；`MessagingException | MailException | RuntimeException`（渲染）统一包装；不打日志）。
- [ ] **Step 4:** 同一命令 PASS。
- [ ] **Step 5:** 提交：`feat(notification): 新增 SMTP 发信适配器，每封带 Resend 幂等键`。

### Task 5: 发送记录

**Files:**
- Create：`…/domain/mail/model/enums/MailDeliveryOutcome.java`、`…/domain/mail/model/aggregate/MailDelivery.java`、`…/domain/mail/port/repository/MailDeliveryRepository.java`
- Create：`patra-notification-infra/src/main/resources/db/migration/mail/V{yyyyMMddHHmmss}__create_mail_delivery.sql`（spec 第 8 节原文）
- Create：`patra-notification-infra/src/main/java/dev/patra/notification/infra/mail/persistence/MailDeliveryRepositoryAdapter.java`
- Test：`…/domain/mail/model/aggregate/MailDeliveryTest.java`；`patra-notification-infra/src/integrationTest/java/dev/patra/notification/infra/mail/persistence/MailDeliveryRepositoryAdapterIT.java`

**Interfaces:**
- Produces：`enum MailDeliveryOutcome { SENT, LINK_EXPIRED }`；`record MailDelivery(UUID deliveryId, String template, MailDeliveryOutcome outcome, Instant recordedAt)`，`static MailDelivery sent(UUID, String, Instant)`、`static MailDelivery linkExpired(UUID, String, Instant)`；`interface MailDeliveryRepository { boolean exists(UUID deliveryId); void add(MailDelivery delivery); }`；`@Repository MailDeliveryRepositoryAdapter(JdbcClient)`，`add` 标 `@Transactional(propagation = MANDATORY)`

- [ ] **Step 1: 写失败的测试**
  - `MailDeliveryTest.should_build_sent_and_link_expired_records`：两个工厂的 `outcome` 对、四项非空。
  - `MailDeliveryRepositoryAdapterIT`（`NotificationITBootstrap` + PG 初始化器，事务用 `TransactionTemplate`）：`should_report_existing_delivery`（`add` 后 `exists` 为 true，别的 ID 为 false）；`should_store_outcome_and_time`（读回 `outcome`、`recorded_at`、`template`）；`should_require_caller_transaction`（无事务调 `add` 抛 `IllegalTransactionStateException`）；`should_only_have_four_columns`（`information_schema.columns` 里 `mail.mail_delivery` 恰为 `delivery_id, template, outcome, recorded_at`）。
- [ ] **Step 2:** domain test 与 `./gradlew :patra-api:patra-notification:patra-notification-infra:integrationTest --tests "*MailDeliveryRepositoryAdapterIT*"` FAIL。
- [ ] **Step 3:** 实现；迁移版本号用写脚本时的 `date +%Y%m%d%H%M%S`。
- [ ] **Step 4:** 两条命令 PASS；`NotificationMigrationIT` 仍 PASS（`mail` schema 在白名单内）。
- [ ] **Step 5:** 提交：`feat(notification): 新增邮件发送记录表，只记投递 ID、模板、结果与时间`。

### Task 6: 发送用例

**Files:**
- Create：`patra-notification-app/src/main/java/dev/patra/notification/app/mail/usecase/security/SendSecurityMailHandler.java`
- Test：`patra-notification-app/src/test/java/dev/patra/notification/app/mail/usecase/security/SendSecurityMailHandlerTest.java`

**Interfaces:**
- Consumes：Task 1、2、4、5 的类型；`ProcessedEventPort`（`patra-tool-core`）；`TransactionOperations`；`Clock`
- Produces：`SendSecurityMailHandler implements CommandHandler<SendSecurityMailCommand, SendSecurityMailResult>`

- [ ] **Step 1: 写失败的测试**（Mockito + `RecordingTransactions`；端口与仓储的 `doAnswer` 往同一个事件列表里记）：
  - `should_send_outside_transaction_then_record_and_mark_processed`：事件顺序 `exists` → `deliver` → `tx-begin` → `markProcessed` → `add` → `tx-end`；返回 `SENT`；`add` 的记录 `outcome = SENT`、`recordedAt` 为发送后取的时钟值、`deliveryId = eventId`、`markProcessed("notification.security-mail", eventId)`。
  - `should_skip_when_already_recorded`：`exists` 为 true → `DUPLICATE`，不调 `deliver`、不开事务。
  - `should_record_link_expired_without_sending`：密码重设信 `linkExpiresAt` ≤ 时钟 → `LINK_EXPIRED`，不调 `deliver`，事务里 `markProcessed` + `add(outcome = LINK_EXPIRED)`。
  - `should_send_expired_notice_kind`：邮箱已更改的信链接过期仍 `SENT`。
  - `should_not_record_when_delivery_fails`：`deliver` 抛 `MailDeliveryFailedException` → 原样抛出，不开事务、不 `markProcessed`。
  - `should_not_add_record_when_event_already_marked`：`markProcessed` 返回 false → 不 `add`，仍返回 `SENT`。
  - `should_log_without_recipient`（`OutputCaptureExtension`）：发送成功与失败的日志含 `eventId` 与模板名，不含收件地址与链接。
- [ ] **Step 2:** `./gradlew :patra-api:patra-notification:patra-notification-app:test --tests "*SendSecurityMailHandlerTest*"` FAIL。
- [ ] **Step 3:** 实现（spec 第 5 节四步）。成功 INFO `安全邮件已发送: kind=…, eventId=…`；过期 INFO `链接已过期，不发: …`；重复 INFO；失败 WARN `安全邮件发送失败，交给重试或死信: kind=…, eventId=…` 后重新抛出。
- [ ] **Step 4:** 同一命令 PASS；`./gradlew :patra-api:patra-notification:patra-notification-boot:test --tests "*NotificationArchitectureTest*"` PASS。
- [ ] **Step 5:** 提交：`feat(notification): 新增安全邮件发送用例，事务外发信、短事务记账`。

### Task 7: 装配与端到端

**Files:**
- Create：`patra-notification-boot/src/main/java/dev/patra/notification/config/MailDeliveryConfiguration.java`、`MailDeliveryProperties.java`
- Modify：boot `application.yml`（`patra.notification.mail.zone`、`management.health.mail.enabled: false`）、`patra-infra/nacos/dev/patra-notification.yaml`（`spring.mail` 整块 + `patra.notification.mail.from`）
- Test：`patra-notification-boot/src/integrationTest/java/dev/patra/notification/mail/SecurityMailITSupport.java`（共用基座）、`SecurityMailFlowIT.java`、`SecurityMailRetryIT.java`、`SecurityMailDeadLetterIT.java`；`patra-notification-boot/src/test/java/dev/patra/notification/config/MailDeliveryConfigurationTest.java`

**Interfaces:**
- Produces：`@ConfigurationProperties("patra.notification.mail") record MailDeliveryProperties(String from, ZoneId zone)`；`MailDeliveryConfiguration`：`@Bean ThymeleafMailRenderer mailRenderer(MailDeliveryProperties)`、`@Bean MailDeliveryPort mailDeliveryPort(JavaMailSender, ThymeleafMailRenderer, MailDeliveryProperties)`
- `SecurityMailITSupport`：PG + RabbitMQ 初始化器；`publish(SecurityMailEvent event, String routingKey)`；`List<MimeMessage> mailsTo(String address)`；`int deliveryRows(UUID)`、`int processedRows(UUID)`；`@BeforeEach` 清 GreenMail、两张表、队列与死信队列

- [ ] **Step 1: 写失败的测试**
  - `MailDeliveryConfigurationTest.should_fail_fast_on_invalid_from`：`ApplicationContextRunner` 配 `patra.notification.mail.from=a@x, b@y` 启动失败。
  - `SecurityMailFlowIT`（加 `GreenMailSmtpInitializer`）：
    - `should_deliver_all_thirteen_kinds`：13 个事件各发一个（收件地址各不相同），每个地址收到一封、主题对、`Resend-Idempotency-Key` = 事件 ID、无 `List-Unsubscribe`；`mail_delivery`、`processed_event` 各 13 行。
    - `should_send_change_mails_to_new_and_old_addresses`：改邮箱确认信到新地址、变更通知到旧地址。
    - `should_deliver_duplicate_event_once`：同一事件发两次 → 一封、一行记录。
    - `should_keep_idempotency_key_when_recording_fails_after_send`：`@MockitoSpyBean MailDeliveryRepository`，第一次 `add` 抛 `RuntimeException` → 收到两封，两封 `Resend-Idempotency-Key` 相同；最终一行记录。
    - `should_store_no_body_link_or_address`：记录行的每一列都不含链接与收件地址。
    - `should_skip_expired_reset_link`：过期的找回密码 → 不收信，记录 `LINK_EXPIRED`。
  - `SecurityMailRetryIT`（GreenMail；`@MockitoSpyBean JavaMailSender`，第一次 `send(MimeMessage)` 抛 `MailSendException`，之后调真实方法；测试属性 `spring.rabbitmq.listener.simple.retry.initial-interval=100ms`）：`should_retry_and_deliver_after_transient_failure`：收到一封，`send` 被调两次，记录一行。
  - `SecurityMailDeadLetterIT`（**不挂** GreenMail；`spring.mail.host=127.0.0.1`、`spring.mail.port` = 一个空闲端口；同样缩短重试间隔；`OutputCaptureExtension`）：
    - `should_dead_letter_after_retries`：消息出现在 `notification.security-mail.dlq`（`rabbitTemplate.receive(…, 10_000)` 非空、`messageId` = 事件 ID）；没有记录行；日志含 `Retries exhausted` 与事件 ID，不含链接与收件地址。
    - `should_dead_letter_untrusted_type_without_retry`：发一条类型头为 `java.util.HashMap`、内容 `{}` 的消息 → 进死信，日志无重试记录。
- [ ] **Step 2:** `./gradlew :patra-api:patra-notification:patra-notification-boot:test --tests "*MailDeliveryConfigurationTest*"` 与 `:integrationTest --tests "*SecurityMail*"` FAIL。
- [ ] **Step 3:** 写装配与配置；Nacos 的 `spring.mail` 块照 `b047dec36` 的 `patra-identity.yaml` 原样搬。
- [ ] **Step 4:** 两条命令 PASS；`PatraNotificationApplicationIT` 仍 PASS（若因监听连不上 broker 变慢或报错，给它加 `RabbitMQContainerInitializer`）；`./gradlew :patra-tool:patra-tool-launch:test`（`NacosConfigFilesTest`、`JarConfigOverlapCheckerTest`）PASS。
- [ ] **Step 5:** 提交：`feat(notification): 装配邮件渠道并补端到端、重试与死信测试`。

### Task 8: 部署与文档

**Files:**
- Modify：`patra-infra/docker/docker-compose.apps.yaml`（notification 块加 `mail.env` / `required: false`，identity 块删掉）、`patra-infra/docker/secrets/mail.env.example`（头注释改成 notification）、`patra-infra/docker/README.md`（`mail.env` 的读者改成 notification）
- Modify：`patra-api/patra-notification/README.md`（mail 子域一节：流程、队列、发送记录、给 PAP-146 的用法指向 spec 第 11 节）、`docs/patra/runbooks/v0.16-notification-go-live-runbook.md`（第 5 步、第 7 步追加 spec 第 13 节）
- 检查：`git grep -n "mail.env"` 里其他提到 identity 读 `mail.env` 的地方一并改；`patra-infra/cd` 下 compose 相关测试（`deploy.test.sh` 等）若断言 env_file 列表，按新块更新

- [ ] **Step 1:** 改 compose 与文档；`bash patra-infra/cd/deploy.test.sh`、`bash patra-infra/cd/detect-changes.test.sh` 若涉及 compose 则跑并通过。
- [ ] **Step 2:** 提交：`chore(infra): 邮件凭据改由通知服务读取，补上线步骤与说明`。

### Task 9: 评审与全量门禁

- [ ] **Step 1:** 派独立评审子代理（`model: "opus"`），只给 spec 路径与 `git diff d3644fde4..HEAD`（不含 Task 0 的副本提交也可以看），不给自己的结论；重要问题先写复现测试再修，每修一处单独提交。
- [ ] **Step 2:** `./gradlew spotlessApply` 后 `./gradlew check integrationTest --continue` 全绿（个别 IT 超时先单独重跑再判断）。
- [ ] **Step 3:** spec 末尾补「实施记录」：与设计的偏离、门禁结果。提交：`docs(notification): 补邮件渠道的实施记录`。
- [ ] **Step 4:** 给编排会话发验收汇报（提交 SHA、AC 对应测试、全量结果、评审发现与处理、留下的问题、上线步骤、给 PAP-146 的约束、Task 0 的提交要丢弃）。
