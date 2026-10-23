# identity 邮件发送与邮箱验证实施计划（PAP-96）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 identity 建发信链路（SMTP 适配器 + Thymeleaf 双模板 + 安全通知片段）和一次性令牌表，做完邮箱验证闭环：注册发验证邮件、`POST /me/email/verification/resend`（四维度限流）、`POST /auth/email/verify`（消费令牌、写 `email_verified_at`、撤销其他会话）、过期令牌清理任务；开发环境接 Mailpit，mini 接真实服务商。

**Architecture:** 领域层定义 `EmailToken` 聚合（库里只有 SHA-256）、`EmailDeliveryPort`（收类型化的 `OutgoingMail`）、`EmailResendThrottlePort`、`EmailVerificationIssuer` 领域服务；infra 用 Boot 的 `JavaMailSender` 异步发信、Thymeleaf 按模板名渲染文本与 HTML、一段 Lua 做滑动窗口限流、`JdbcClient` 做 `UPDATE … RETURNING` 的原子消费；会话模块加「撤销除某条外的全部会话」。令牌只走 URL 片段、只由 POST 消费。

**Tech Stack:** Java 25、Spring Boot 4.0.8（`spring-boot-starter-mail`、Jakarta Mail 2.1.5 / Angus 2.0.5）、Thymeleaf 3.1.5、Spring Data JPA + Flyway、PostgreSQL 17 与 Redis 7.0.15 的 Testcontainers、GreenMail 2.1.14、xxl-job-core 3.2.0、Lombok、MapStruct、JUnit 5 + AssertJ + Mockito + Awaitility。

**Spec:** `docs/patra/specs/2026-10-21-identity-email-verification-design.md`

## Global Constraints

- 全部工作在主检出 `/Users/linqibin/Projects/Products/patra` 的分支 `feat/v0.12-user-center-api` 上，不建 worktree、不切分支、不推送、不开 PR。命令在仓库根目录执行。
- 令牌：`EmailTokenSecret` 是 32 字节 `SecureRandom` 的 base64url 无填充，恰好 43 个 `[A-Za-z0-9_-]`；`hash()` 是 UTF-8 的 SHA-256 小写十六进制 64 位；持久层只见哈希（spec §7.1、§7.2）。
- 用途与有效期：`EMAIL_VERIFY` 24h、`PASSWORD_RESET` 1h、`EMAIL_CHANGE_CONFIRM` 1h、`EMAIL_CHANGE_REVERT` 7d（spec §7.1）。
- 限流：冷却 `60s`、每账号每小时 5、每天 10、每 IP 每小时 30；滑动窗口；`Retry-After` 取各维度最长；时间由 Java 传入 Lua，脚本不调 `TIME`，返回值永远是数字（spec §10）。
- 键名：`idn:email-resend:cooldown:user:{userId}`、`idn:email-resend:hour:user:{userId}`、`idn:email-resend:day:user:{userId}`、`idn:email-resend:hour:ip:{sha256(ip) 前 32 位十六进制}`；IP 未知用 `idn:email-resend:hour:ip:-` 并跳过该维（spec §10.1）。
- 错误：令牌问题一律 422 `errors[]` `token` / `REQUIRED` 或 `token` / `INVALID`；限流 429 `EmailResendLimitedException` + `Retry-After`；已验证重发 409 `traits` 含 `EMAIL_ALREADY_VERIFIED`；注销中 409 含 `PENDING_DELETION`；封禁 / 注销 / 无当前用户 401；每个异常只放一个标准特征（spec §12）。
- 响应：重发 `202 { email, nextResendAt }`；验证 `200 { email, emailVerifiedAt }`（spec §8.2、§8.3）。
- 链接：`{base-url}{verify-email-path}#token={令牌}`，`base-url` 只来自配置 `patra.identity.mail.links.base-url`，不读请求（spec §9.5）。
- 邮件：`multipart/alternative`，文本在前 HTML 在后；头 `Auto-Submitted: auto-generated`、`X-Auto-Response-Suppress: All`；发送异步、失败只记 WARN；收件人、令牌、链接不进日志（spec §9）。
- 配置键和默认值按 spec §13 原样：`spring.mail.*`、`patra.identity.mail.from`、`patra.identity.mail.links.base-url` 进 Nacos `patra-infra/nacos/dev/patra-identity.yaml`（`NacosConfigFilesTest` 守），其余进 jar `application.yml`，含 `management.health.mail.enabled: false`（`JarConfigOverlapCheckerTest` 守两处不重复）。
- 表：`idn_email_token` 的列、约束、三个索引按 spec §6.1，部分唯一索引的谓词含 `AND purpose <> 'EMAIL_CHANGE_REVERT'`；`revokeAllActive` 跳过 `EMAIL_CHANGE_REVERT`（spec §7.4、§7.6）；`idn_user_login_record.end_reason` 加 `REVOKED`（spec §6.2）。
- 代码规范：Google Java 格式（spotless）、`///` Javadoc 且每行不超过 100 列、不用全类名、record ≤ 4 个参数用 `of()`、≥ 5 个用 `@Builder`，集合用 `List.of` / `List.copyOf`；domain 不引框架。
- 测试规范：单测在 `src/test`，集成测试在 `src/integrationTest`，方法名 snake_case `should_…`；`./gradlew check` 不含 `integrationTest`，两个都要跑；Testcontainers 要本机 Docker（OrbStack）在线；跑 `check` 前先 `./gradlew spotlessApply`；改了任何 `build.gradle.kts` 后 `./gradlew dumpModuleGraph` 并提交 `patra-infra/cd/module-graph.json`。
- 写 Entity / Dao / JpaMapper / RepositoryAdapter 的 Task 4 开工前加载 `patra-backend:patra-jpa` 技能；写 Controller / Handler / Job 的任务开工前加载 `patra-backend:patra-hexagonal`。
- 提交：每个任务至少一次提交，格式 `type(scope): 中文 subject (PAP-96)`，subject 用中文动词起头，body 每行 ≤ 100 字符，结尾加 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`；只 `git add` 本任务的路径，提交前看 `git diff --cached --stat`（工作区有别的会话的未跟踪文件，如 `docs/patra/release-specs/v0.12-user-center.md`，不要带上）。
- Mockito 不嵌套 stub；`TransactionOperations` 的桩照 `RegisterUserHandlerTest` 现有写法（同步执行回调）。
- 新的 `@SpringBootTest` 复用已有的注解组合（同一组 initializers / profile / 导入），让 Spring 复用上下文；boot 的全部 `@SpringBootTest` 类都要加 `GreenMailSmtpInitializer`（Task 9：从那一步起 `EmailDeliveryPort` 需要 `JavaMailSender`，而 `spring.mail.host` 只在 Nacos 里）。
- 拨动 `MutableClock` 的端到端测试里，断言用 `new IdentityAssertionSigner(TestSigningKey.key(), clock)` 签（PAP-93 spec §14 第 10 条，`UsernameChangeFlowIT.assertionFor` 的写法）。

## Review Focus

1. 令牌两端带空白（复制粘贴）：先 `strip()` 再按 43 个字符校验，空白不算错——Task 1 的 `should_accept_surrounding_whitespace`。
2. 登录成甲的人去消费乙的验证令牌：验证乙、撤销乙的会话，甲的会话不动——Task 12 的 `should_only_touch_sessions_of_token_owner_when_requester_differs`。
3. 令牌签发后用户换了邮箱（PAP-98 以后会发生）：`consume` 成功但邮箱不符，仍 422，且令牌已被消费不再可用——Task 12 的 `should_reject_token_when_email_no_longer_matches`。
4. 注册时 Redis 计数失败：照样 201、照样发信——Task 10 的 `should_still_register_and_send_when_throttle_marking_fails`。
5. 请求带伪造的 `Host` / `X-Forwarded-Host`：邮件里的链接仍以配置的 `base-url` 开头——Task 14 的 `should_send_verification_mail_on_register_with_links_from_configuration_not_host_header`。

---

### Task 1: 领域基础类型：`EmailTokenPurpose`、`EmailTokenSecret`、`EmailToken`

**Files:**
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/enums/EmailTokenPurpose.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/EmailTokenSecret.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/aggregate/EmailToken.java`
- Test: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/model/enums/EmailTokenPurposeTest.java`
- Test: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/model/vo/EmailTokenSecretTest.java`
- Test: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/model/aggregate/EmailTokenTest.java`

**Interfaces:**
- Produces: `public enum EmailTokenPurpose { EMAIL_VERIFY, PASSWORD_RESET, EMAIL_CHANGE_CONFIRM, EMAIL_CHANGE_REVERT; public Duration validity() }`。
- Produces: `public record EmailTokenSecret(String value)`：`public static final int LENGTH = 43`；紧凑构造器校验 `^[A-Za-z0-9_-]{43}$`，否则 `IllegalArgumentException`；`static EmailTokenSecret generate(SecureRandom random)`（32 字节 → `Base64.getUrlEncoder().withoutPadding()`）；`static Optional<EmailTokenSecret> parse(String raw)`（`null` / 空白为空；先 `strip()` 再校验格式）；`String hash()`（SHA-256 小写十六进制）；`toString()` 恒为 `EmailTokenSecret[***]`。
- Produces: `@Getter public final class EmailToken`：字段 `Long id`、`Long userId`、`EmailTokenPurpose purpose`、`EmailAddress email`、`String tokenHash`、`Instant expiresAt`、`Instant consumedAt`、`Instant revokedAt`、`String requestedIp`（`@Getter(AccessLevel.NONE)`，公开 `Optional<String> getRequestedIp()`）、`Long version`、`Instant createdAt`、`Instant updatedAt`；`static EmailToken issue(EmailTokenPurpose purpose, long userId, EmailAddress email, EmailTokenSecret secret, Instant now, String requestedIp)`（`expiresAt = now.plus(purpose.validity())`，`tokenHash = secret.hash()`，`requestedIp` 可为 `null`）；`@Builder(builderMethodName = "restore")` 的私有全参构造器（12 个字段，`id`、`userId`、`purpose`、`email`、`tokenHash`、`expiresAt` 非空）；`boolean isActive()`（没消费也没作废）；`boolean isUsableAt(Instant now)`（`isActive() && now.isBefore(expiresAt)`）；`void markConsumed(Instant now)` / `void markRevoked(Instant now)`（已结算抛 `IllegalStateException`）；`toString()` 只输出 `id`、`userId`、`purpose`。

- [ ] **Step 1: 写失败的测试**

```java
@DisplayName("EmailTokenPurpose 单元测试")
class EmailTokenPurposeTest {
  @Test
  void should_define_validity_per_purpose() {
    assertThat(EmailTokenPurpose.EMAIL_VERIFY.validity()).isEqualTo(Duration.ofHours(24));
    assertThat(EmailTokenPurpose.PASSWORD_RESET.validity()).isEqualTo(Duration.ofHours(1));
    assertThat(EmailTokenPurpose.EMAIL_CHANGE_CONFIRM.validity()).isEqualTo(Duration.ofHours(1));
    assertThat(EmailTokenPurpose.EMAIL_CHANGE_REVERT.validity()).isEqualTo(Duration.ofDays(7));
  }
}
```

```java
@DisplayName("EmailTokenSecret 单元测试")
class EmailTokenSecretTest {
  private static final String VALID = "A".repeat(43);

  @Test
  void should_generate_43_url_safe_characters_and_differ_each_time() {
    SecureRandom random = new SecureRandom();
    EmailTokenSecret first = EmailTokenSecret.generate(random);
    assertThat(first.value()).matches("^[A-Za-z0-9_-]{43}$");
    assertThat(EmailTokenSecret.generate(random).value()).isNotEqualTo(first.value());
  }

  @Test
  void should_hash_to_lowercase_sha256_hex() throws Exception {
    String expected =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256").digest(VALID.getBytes(StandardCharsets.UTF_8)));
    assertThat(new EmailTokenSecret(VALID).hash()).isEqualTo(expected).hasSize(64);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "A", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA+A", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/A"})
  void should_reject_wrong_length_or_alphabet(String raw) {
    assertThat(EmailTokenSecret.parse(raw)).isEmpty();
  }

  @Test
  void should_accept_surrounding_whitespace() {
    assertThat(EmailTokenSecret.parse("  " + VALID + "\n")).contains(new EmailTokenSecret(VALID));
    assertThat(EmailTokenSecret.parse(null)).isEmpty();
  }

  @Test
  void should_mask_value_in_to_string() {
    assertThat(new EmailTokenSecret(VALID).toString()).isEqualTo("EmailTokenSecret[***]").doesNotContain(VALID);
  }
}
```

```java
@DisplayName("EmailToken 单元测试")
class EmailTokenTest {
  private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final EmailTokenSecret SECRET = new EmailTokenSecret("B".repeat(43));

  @Test
  void should_issue_with_expiry_from_purpose_and_store_only_hash() {
    EmailToken token = EmailToken.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, EMAIL, SECRET, NOW, "203.0.113.9");
    assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    assertThat(token.getTokenHash()).isEqualTo(SECRET.hash());
    assertThat(token.getRequestedIp()).contains("203.0.113.9");
    assertThat(token.isActive()).isTrue();
    assertThat(token.isUsableAt(NOW)).isTrue();
    assertThat(token.isUsableAt(token.getExpiresAt())).isFalse();
    assertThat(token.toString()).doesNotContain(SECRET.hash()).doesNotContain("chen.yu");
  }

  @Test
  void should_not_be_usable_once_consumed_or_revoked_and_reject_second_settlement() {
    EmailToken consumed = EmailToken.issue(EmailTokenPurpose.PASSWORD_RESET, 42L, EMAIL, SECRET, NOW, null);
    consumed.markConsumed(NOW.plusSeconds(1));
    assertThat(consumed.isUsableAt(NOW.plusSeconds(2))).isFalse();
    assertThat(consumed.getRequestedIp()).isEmpty();
    assertThatThrownBy(() -> consumed.markRevoked(NOW.plusSeconds(3))).isInstanceOf(IllegalStateException.class);
    EmailToken revoked = EmailToken.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, EMAIL, SECRET, NOW, null);
    revoked.markRevoked(NOW);
    assertThat(revoked.isActive()).isFalse();
  }

  @Test
  void should_restore_from_builder() {
    EmailToken token = EmailToken.restore().id(1L).userId(42L).purpose(EmailTokenPurpose.EMAIL_CHANGE_REVERT)
        .email(EMAIL).tokenHash(SECRET.hash()).expiresAt(NOW.plus(Duration.ofDays(7))).version(0L).build();
    assertThat(token.isUsableAt(NOW.plus(Duration.ofDays(6)))).isTrue();
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*EmailTokenPurposeTest*" --tests "*EmailTokenSecretTest*" --tests "*EmailTokenTest*"`
Expected: FAIL，编译错误（找不到 `EmailTokenPurpose` 等类型）

- [ ] **Step 3: 实现三个类型**

按 Interfaces 写。`EmailToken` 照 `User` 的写法：类级 `@Getter`、私有无参构造器给 `issue` 用、`@Builder(builderMethodName = "restore")` 放私有全参构造器上。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/enums/EmailTokenPurpose.java patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/EmailTokenSecret.java patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/aggregate/EmailToken.java patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/model/
git commit -m "feat(identity): 加一次性令牌的用途、原文与聚合 (PAP-96)"
```

---

### Task 2: 领域规则、端口与异常：`EmailResendPolicy`、`MailLinkPolicy`、`OutgoingMail`、`EmailVerificationMail`、`SecurityNotice`、`EmailTokenRepository`、`EmailDeliveryPort`、`EmailResendThrottlePort`

**Files:**
- Create: `…/domain/policy/EmailResendPolicy.java`、`…/domain/policy/MailLinkPolicy.java`
- Create: `…/domain/port/repository/EmailTokenRepository.java`
- Create: `…/domain/port/mail/OutgoingMail.java`、`EmailVerificationMail.java`、`SecurityNotice.java`、`EmailDeliveryPort.java`
- Create: `…/domain/port/throttle/EmailResendThrottlePort.java`
- Create: `…/domain/exception/EmailAlreadyVerifiedException.java`、`EmailResendLimitedException.java`
- Modify: `…/domain/exception/IdentityErrorTrait.java`（加 `EMAIL_ALREADY_VERIFIED`）
- Modify: `…/domain/model/vo/UserFieldViolations.java`（加 `TOKEN`、`INVALID`、`tokenRequired()`、`tokenInvalid()`）
- Test: `…/domain/policy/EmailResendPolicyTest.java`、`MailLinkPolicyTest.java`；`…/domain/port/mail/EmailVerificationMailTest.java`、`SecurityNoticeTest.java`；Modify `…/domain/exception/DomainExceptionTraitsTest.java`

（`…` = `patra-api/patra-identity/patra-identity-domain/src/{main,test}/java/dev/patra/identity`）

**Interfaces:**
- Produces: `public record EmailResendPolicy(Duration cooldown, int perAccountHourly, int perAccountDaily, int perIpHourly)`：`public static final Duration HOUR_WINDOW = Duration.ofHours(1)`、`DAY_WINDOW = Duration.ofHours(24)`；紧凑构造器：`cooldown` 必须为正、三个上限 ≥ 1；`static EmailResendPolicy of(...)`；`Instant nextResendAt(Instant now)`。
- Produces: `public record MailLinkPolicy(String baseUrl, String verifyEmailPath, String securitySettingsPath)`：紧凑构造器把 `baseUrl` 末尾的 `/` 去掉，要求是 `http` / `https` 的绝对地址（`URI.create` 后 `scheme` 与 `host` 非空），两个路径以 `/` 开头；`static MailLinkPolicy of(...)`；`String verifyEmailUrl(EmailTokenSecret secret)` = `baseUrl + verifyEmailPath + "#token=" + secret.value()`；`String securitySettingsUrl()`。
- Produces: `public interface OutgoingMail { EmailAddress to(); String template(); String subject(); Map<String, Object> model(); }`。
- Produces: `public record EmailVerificationMail(EmailAddress to, String displayName, String verifyUrl, Instant expiresAt) implements OutgoingMail`：`public static final String TEMPLATE = "email-verification"`、`SUBJECT = "验证你的 Patra 邮箱"`；`model()` 返回 `Map.of("displayName", displayName, "verifyUrl", verifyUrl, "validHours", 24L)`；`of(...)`；`toString()` 不含 `to` 与 `verifyUrl`。
- Produces: `@Builder public record SecurityNotice(Instant occurredAt, String device, String ipAddress, String ipLocation, String notMeAdvice)`，紧凑构造器要求 `occurredAt`、`notMeAdvice` 非空。
- Produces: `public interface EmailDeliveryPort { void send(OutgoingMail mail); }`。
- Produces: `public interface EmailTokenRepository { EmailToken save(EmailToken token); Optional<EmailToken> findByTokenHash(String tokenHash); Optional<EmailToken> consume(String tokenHash, EmailTokenPurpose purpose, Instant now); int revokeActive(long userId, EmailTokenPurpose purpose, Instant now); int revokeAllActive(long userId, Instant now); int deleteExpiredBefore(Instant cutoff, int batchSize); }`。
- Produces: `public interface EmailResendThrottlePort { Optional<Duration> acquire(long userId, String clientIp, Instant now); void markSent(long userId, Instant now); }`（`clientIp` 可为 `null`；`markSent` 只计冷却和两个按账号的窗口）。
- Produces: `EmailAlreadyVerifiedException()`（`"邮箱已经验证过了"`，`CONFLICT` + `IdentityErrorTrait.EMAIL_ALREADY_VERIFIED`）；`EmailResendLimitedException(Duration retryAfter) implements HasRetryAfter`（`"发送过于频繁，请稍后再试"`，`QUOTA_EXCEEDED`）。
- Produces: `UserFieldViolations.TOKEN = "token"`、`INVALID = "INVALID"`、`tokenRequired()`（`token` / `REQUIRED` / `"请提供验证令牌"`）、`tokenInvalid()`（`token` / `INVALID` / `"验证链接无效或已过期"`）。

- [ ] **Step 1: 写失败的测试**

```java
@DisplayName("EmailResendPolicy 单元测试")
class EmailResendPolicyTest {
  @Test
  void should_compute_next_resend_at_from_cooldown() {
    EmailResendPolicy policy = EmailResendPolicy.of(Duration.ofSeconds(60), 5, 10, 30);
    assertThat(policy.nextResendAt(Instant.parse("2026-10-21T08:00:00Z"))).isEqualTo(Instant.parse("2026-10-21T08:01:00Z"));
    assertThat(EmailResendPolicy.HOUR_WINDOW).isEqualTo(Duration.ofHours(1));
    assertThat(EmailResendPolicy.DAY_WINDOW).isEqualTo(Duration.ofHours(24));
  }

  @Test
  void should_reject_non_positive_values() {
    assertThatThrownBy(() -> EmailResendPolicy.of(Duration.ZERO, 5, 10, 30)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EmailResendPolicy.of(Duration.ofSeconds(60), 0, 10, 30)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EmailResendPolicy.of(Duration.ofSeconds(60), 5, 0, 30)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EmailResendPolicy.of(Duration.ofSeconds(60), 5, 10, 0)).isInstanceOf(IllegalArgumentException.class);
  }
}
```

```java
@DisplayName("MailLinkPolicy 单元测试")
class MailLinkPolicyTest {
  private static final EmailTokenSecret SECRET = new EmailTokenSecret("C".repeat(43));

  @Test
  void should_build_verify_url_with_fragment_and_strip_trailing_slash() {
    MailLinkPolicy policy = MailLinkPolicy.of("https://patra.example/", "/verify-email", "/settings/security");
    assertThat(policy.verifyEmailUrl(SECRET)).isEqualTo("https://patra.example/verify-email#token=" + SECRET.value());
    assertThat(policy.securitySettingsUrl()).isEqualTo("https://patra.example/settings/security");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "patra.example", "/portal", "ftp://patra.example", "http://"})
  void should_reject_relative_or_non_http_base_url(String baseUrl) {
    assertThatThrownBy(() -> MailLinkPolicy.of(baseUrl, "/verify-email", "/settings/security")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_paths_not_starting_with_slash() {
    assertThatThrownBy(() -> MailLinkPolicy.of("http://localhost:4000", "verify-email", "/settings/security")).isInstanceOf(IllegalArgumentException.class);
  }
}
```

```java
@DisplayName("EmailVerificationMail 单元测试")
class EmailVerificationMailTest {
  @Test
  void should_expose_template_subject_and_model() {
    EmailVerificationMail mail = EmailVerificationMail.of(EmailAddress.of("chen.yu@example.com"), "陈宇",
        "http://localhost:4000/verify-email#token=x", Instant.parse("2026-10-22T08:00:00Z"));
    assertThat(mail.template()).isEqualTo("email-verification");
    assertThat(mail.subject()).isEqualTo("验证你的 Patra 邮箱");
    assertThat(mail.model()).containsEntry("displayName", "陈宇").containsEntry("verifyUrl", "http://localhost:4000/verify-email#token=x").containsEntry("validHours", 24L);
    assertThat(mail.toString()).doesNotContain("chen.yu").doesNotContain("token=");
  }
}
```

```java
@DisplayName("SecurityNotice 单元测试")
class SecurityNoticeTest {
  @Test
  void should_require_occurred_at_and_advice_but_allow_unknown_device() {
    SecurityNotice notice = SecurityNotice.builder().occurredAt(Instant.parse("2026-10-21T08:30:00Z")).notMeAdvice("请立即修改密码").build();
    assertThat(notice.device()).isNull();
    assertThatThrownBy(() -> SecurityNotice.builder().notMeAdvice("x").build()).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> SecurityNotice.builder().occurredAt(Instant.EPOCH).build()).isInstanceOf(NullPointerException.class);
  }
}
```

`DomainExceptionTraitsTest.exceptions()` 加两行：`new EmailAlreadyVerifiedException()` → `"邮箱已经验证过了"` / `CONFLICT`；`new EmailResendLimitedException(Duration.ofSeconds(60))` → `"发送过于频繁，请稍后再试"` / `QUOTA_EXCEEDED`。再加一个用例 `should_carry_identity_traits_and_retry_after`：前者 `getErrorTraits()` 含 `IdentityErrorTrait.EMAIL_ALREADY_VERIFIED`；后者 `getRetryAfterSeconds()` 为 60；`UserFieldViolations.tokenInvalid()` 的 `field()` 为 `"token"`、`code()` 为 `"INVALID"`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*EmailResendPolicyTest*" --tests "*MailLinkPolicyTest*" --tests "*EmailVerificationMailTest*" --tests "*SecurityNoticeTest*" --tests "*DomainExceptionTraitsTest*"`
Expected: FAIL，编译错误

- [ ] **Step 3: 实现**

按 Interfaces 写。`MailLinkPolicy` 的校验：`URI uri = URI.create(baseUrl)`（`IllegalArgumentException` 原样抛），要求 `uri.getScheme()` 是 `http` 或 `https` 且 `uri.getHost()` 非空；去掉末尾全部 `/`。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain/src
git commit -m "feat(identity): 加邮件发送、令牌仓储、重发限流的端口与规则 (PAP-96)"
```

---

### Task 3: 领域服务 `EmailVerificationIssuer`

**Files:**
- Create: `…/domain/service/EmailVerificationIssuer.java`、`…/domain/service/PreparedVerificationMail.java`
- Test: `…/domain/service/EmailVerificationIssuerTest.java`

**Interfaces:**
- Consumes: Task 1、2 的 `EmailToken.issue`、`EmailTokenSecret.generate`、`EmailTokenRepository`、`MailLinkPolicy.verifyEmailUrl`、`EmailVerificationMail.of`。
- Produces: `public final class EmailVerificationIssuer`，构造器 `(EmailTokenRepository tokens, MailLinkPolicy links, Clock clock, SecureRandom random)`；`PreparedVerificationMail prepare(User user, String displayName, String requestedIp)`：`EmailAddress email = user.getEmail().orElseThrow(() -> new IllegalStateException("没有邮箱的用户不能发验证邮件"))` → `tokens.revokeActive(user.getId(), EMAIL_VERIFY, now)` → `secret = EmailTokenSecret.generate(random)` → `saved = tokens.save(EmailToken.issue(EMAIL_VERIFY, user.getId(), email, secret, now, requestedIp))` → 返回 `PreparedVerificationMail.of(saved, EmailVerificationMail.of(email, displayName, links.verifyEmailUrl(secret), saved.getExpiresAt()))`。
- Produces: `public record PreparedVerificationMail(EmailToken token, EmailVerificationMail mail)`，`of(...)`，`toString()` 只输出 `token`。

- [ ] **Step 1: 写失败的测试**

```java
@DisplayName("EmailVerificationIssuer 单元测试")
class EmailVerificationIssuerTest {
  private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
  private static final MailLinkPolicy LINKS = MailLinkPolicy.of("https://patra.example", "/verify-email", "/settings/security");
  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final EmailVerificationIssuer issuer = new EmailVerificationIssuer(tokens, LINKS, Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
  private final User user = User.restore().id(42L).email(EmailAddress.of("chen.yu@example.com")).username(Username.of("chen-yu")).status(UserStatus.ACTIVE).version(0L).build();

  @Test
  void should_revoke_active_tokens_then_save_a_hashed_one_and_link_the_secret() {
    when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    PreparedVerificationMail prepared = issuer.prepare(user, "陈宇", "203.0.113.9");

    InOrder order = inOrder(tokens);
    order.verify(tokens).revokeActive(42L, EmailTokenPurpose.EMAIL_VERIFY, NOW);
    ArgumentCaptor<EmailToken> saved = ArgumentCaptor.forClass(EmailToken.class);
    order.verify(tokens).save(saved.capture());
    EmailToken token = saved.getValue();
    assertThat(token.getUserId()).isEqualTo(42L);
    assertThat(token.getPurpose()).isEqualTo(EmailTokenPurpose.EMAIL_VERIFY);
    assertThat(token.getEmail().value()).isEqualTo("chen.yu@example.com");
    assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    assertThat(token.getRequestedIp()).contains("203.0.113.9");
    String url = prepared.mail().verifyUrl();
    assertThat(url).startsWith("https://patra.example/verify-email#token=");
    String secret = url.substring(url.indexOf("#token=") + 7);
    assertThat(new EmailTokenSecret(secret).hash()).isEqualTo(token.getTokenHash());
    assertThat(prepared.mail().displayName()).isEqualTo("陈宇");
    assertThat(prepared.mail().to().value()).isEqualTo("chen.yu@example.com");
    assertThat(prepared.mail().expiresAt()).isEqualTo(token.getExpiresAt());
    assertThat(prepared.toString()).doesNotContain(secret);
  }

  @Test
  void should_generate_a_different_secret_each_time() {
    when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    assertThat(issuer.prepare(user, "a", null).mail().verifyUrl()).isNotEqualTo(issuer.prepare(user, "a", null).mail().verifyUrl());
  }

  @Test
  void should_fail_for_user_without_email() {
    User deleted = User.restore().id(43L).username(Username.of("gone")).status(UserStatus.DELETED).deletedAt(NOW).version(0L).build();
    assertThatThrownBy(() -> issuer.prepare(deleted, "x", null)).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(tokens);
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*EmailVerificationIssuerTest*"`
Expected: FAIL，编译错误

- [ ] **Step 3: 实现 `EmailVerificationIssuer` 与 `PreparedVerificationMail`**

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2；再跑 `./gradlew :patra-api:patra-identity:patra-identity-domain:check`（R3 纯净性：`SecureRandom`、`MessageDigest` 都是 `java.*`）
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain/src
git commit -m "feat(identity): 加签发验证邮件令牌的领域服务 (PAP-96)"
```

---

### Task 4: `V1` 加令牌表与 `REVOKED`；令牌表的 Entity / Dao / Mapper / RepositoryAdapter

开工前加载 `patra-backend:patra-jpa`。

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-infra/src/main/resources/db/migration/V1__init_identity_schema.sql`
- Modify: `…/domain/model/enums/LoginEndReason.java`（加 `REVOKED`）
- Modify: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/persistence/entity/UserLoginRecordEntity.java`（`endReason` 注释加 `REVOKED`）
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/persistence/entity/EmailTokenEntity.java`
- Create: `…/persistence/dao/EmailTokenDao.java`
- Create: `…/persistence/converter/mapper/EmailTokenJpaMapper.java`
- Create: `…/persistence/EmailTokenRepositoryAdapter.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/integrationTest/java/dev/patra/identity/infra/adapter/persistence/EmailTokenRepositoryAdapterIT.java`
- Modify: `…/integrationTest/…/persistence/IdentitySchemaConstraintsIT.java`

**Interfaces:**
- Consumes: Task 1、2 的 `EmailToken`、`EmailTokenPurpose`、`EmailTokenRepository`。
- Produces: `EmailTokenEntity extends BaseJpaEntity`（`@Table(name = "idn_email_token")`）：`Long userId`、`String purpose`、`String email`、`String tokenHash`（`length = 64`）、`Instant expiresAt`、`Instant consumedAt`、`Instant revokedAt`、`String requestedIp`（`length = 45`）。
- Produces: `EmailTokenDao extends JpaRepository<EmailTokenEntity, Long>`：`@Modifying @Query("update EmailTokenEntity t set t.revokedAt = :now where t.userId = :userId and t.purpose = :purpose and t.consumedAt is null and t.revokedAt is null") int revokeActive(@Param("userId") Long userId, @Param("purpose") String purpose, @Param("now") Instant now)`；同形但多一个条件 `and t.purpose <> 'EMAIL_CHANGE_REVERT'` 的 `int revokeAllActive(userId, now)`；`@Modifying @Query(value = "DELETE FROM idn_email_token WHERE id IN (SELECT id FROM idn_email_token WHERE expires_at < :cutoff ORDER BY id LIMIT :batchSize)", nativeQuery = true) int deleteExpiredBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize)`；`Optional<EmailTokenEntity> findByTokenHash(String tokenHash)`；`List<EmailTokenEntity> findByUserIdOrderByIdAsc(Long userId)`（测试用）。
- Produces: `EmailTokenJpaMapper`（MapStruct，照 `UserJpaMapper`：`toEntity` 忽略审计字段，`purpose` / `email` 用 `expression`；`default EmailToken toAggregate(EmailTokenEntity)` 走 `EmailToken.restore()`）。
- Produces: `@Repository EmailTokenRepositoryAdapter implements EmailTokenRepository`，构造器 `(EmailTokenDao dao, EmailTokenJpaMapper mapper, JdbcTemplate jdbcTemplate)`，内部 `JdbcClient.create(jdbcTemplate)`：`save` 分配雪花 ID、`saveAndFlush`，撞 `uk_idn_email_token_active` 或 `uk_idn_email_token_hash` 的 `DataIntegrityViolationException` 转 `UserModifiedConcurrentlyException`；`consume` 用 `JdbcClient` 执行 `UPDATE idn_email_token SET consumed_at = :now, version = version + 1 WHERE token_hash = :hash AND purpose = :purpose AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > :now RETURNING id, user_id, purpose, email, token_hash, expires_at, consumed_at, revoked_at, requested_ip, version, created_at, updated_at`，`RowMapper` 直接建 `EmailToken.restore()`（时间列 `rs.getObject(col, OffsetDateTime.class).toInstant()`），`.optional()`；`findByTokenHash` / `revokeActive` / `revokeAllActive` / `deleteExpiredBefore` 委托 Dao。
- 常量：`ACTIVE_UNIQUE_INDEX = "uk_idn_email_token_active"`、`HASH_UNIQUE_CONSTRAINT = "uk_idn_email_token_hash"`。

- [ ] **Step 1: 改 `V1`**

在 `idn_user_former_username` 之后追加（审计列、注释、触发器照其他表）：

```sql
CREATE TABLE idn_email_token
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    purpose         VARCHAR(32)     NOT NULL,
    email           VARCHAR(254)    NOT NULL,
    token_hash      CHAR(64)        NOT NULL,
    expires_at      timestamptz(6)  NOT NULL,
    consumed_at     timestamptz(6)  NULL,
    revoked_at      timestamptz(6)  NULL,
    requested_ip    VARCHAR(45)     NULL,
    -- 审计列同其他表 --
    PRIMARY KEY (id),
    CONSTRAINT fk_idn_email_token_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
    CONSTRAINT uk_idn_email_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_idn_email_token_purpose CHECK (purpose IN ('EMAIL_VERIFY', 'PASSWORD_RESET', 'EMAIL_CHANGE_CONFIRM', 'EMAIL_CHANGE_REVERT')),
    CONSTRAINT ck_idn_email_token_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_idn_email_token_hash_format CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_idn_email_token_settled_once CHECK (consumed_at IS NULL OR revoked_at IS NULL)
);
CREATE UNIQUE INDEX uk_idn_email_token_active ON idn_email_token (user_id, purpose)
    WHERE consumed_at IS NULL AND revoked_at IS NULL AND purpose <> 'EMAIL_CHANGE_REVERT';
CREATE INDEX idx_idn_email_token_user_id ON idn_email_token (user_id);
CREATE INDEX idx_idn_email_token_expires_at ON idn_email_token (expires_at);
```

`idn_user_login_record` 的 `ck_idn_user_login_record_end_reason` 改为 `IN ('LOGOUT', 'BANNED', 'REPLACED', 'REVOKED')`，列注释同步。`LoginEndReason` 加 `REVOKED`（Javadoc：被本人的另一次操作撤销——验证邮箱、改密码、踢设备）。

- [ ] **Step 2: 写失败的集成测试**

`EmailTokenRepositoryAdapterIT` 的注解组合照 `FormerUsernameRepositoryAdapterIT`（`@DataJpaTest` + PG 初始化器 + Flyway / Jackson 自动配置 + `@Import(EmailTokenRepositoryAdapter.class)` + Mapper 包扫描 + `@ActiveProfiles("test")`），另注入 `JdbcTemplate`、`TestEntityManager`，`@BeforeEach` 用 SQL 插一个 `idn_user` 行（照 `UserProfileRepositoryAdapterIT` 的做法）。固定 `NOW = Instant.parse("2026-10-21T08:00:00Z")`，辅助方法 `EmailToken issued(EmailTokenPurpose purpose, EmailTokenSecret secret, Instant now)`。

- `should_store_only_hash_and_consume_once`：`save(issued(EMAIL_VERIFY, secret, NOW))` → `jdbcTemplate.queryForObject("select token_hash from idn_email_token where id = ?")` 等于 `secret.hash()`，且 `select count(*) ... where token_hash = ?` 用 `secret.value()` 查为 0 → `findByTokenHash(secret.hash())` 有值且 `isActive()` → `consume(secret.hash(), EMAIL_VERIFY, NOW.plusSeconds(60))` 返回的聚合 `getConsumedAt()` 为 `NOW.plusSeconds(60)`、`getUserId()` 正确 → 第二次 `consume` 为空；`findByTokenHash` 查未知哈希为空。
- `should_not_consume_expired_revoked_or_other_purpose_token`：过期（`consume` 在 `expiresAt` 当刻）为空；`revokeActive` 后为空；用 `PASSWORD_RESET` 消费 `EMAIL_VERIFY` 的哈希为空。
- `should_revoke_only_active_tokens_of_the_purpose`：先存并消费一条，再存一条新的（`revokeActive` 之后），`revokeActive(user, EMAIL_VERIFY, now)` 返回 1；已消费那条 `revoked_at` 仍为 `NULL`。
- `should_revoke_all_active_tokens_of_user`：`EMAIL_VERIFY` 与 `PASSWORD_RESET` 各一条有效 → `revokeAllActive` 返回 2，另一个用户的令牌不受影响。
- `should_let_email_change_revert_tokens_coexist_and_survive_revoke_all`：同一用户不作废直接 `save` 两条 `EMAIL_CHANGE_REVERT`（不同哈希）都成功；`revokeAllActive` 返回 0 且两条仍 `isActive()`；`revokeActive(user, EMAIL_CHANGE_REVERT, now)` 返回 2。
- `should_reject_second_active_token_for_same_user_and_purpose`：不作废直接 `save` 第二条 → `UserModifiedConcurrentlyException`。
- `should_delete_expired_tokens_in_batches`：三条过期超过 cutoff、一条过期但在 cutoff 之内、一条未过期；`deleteExpiredBefore(cutoff, 2)` 依次返回 2、1、0；剩两行。

`IdentitySchemaConstraintsIT` 加（每个用例只做一条失败插入，照现有做法）：`should_reject_unknown_token_purpose`、`should_reject_uppercase_token_email`、`should_reject_malformed_token_hash`（63 位、大写）、`should_reject_token_settled_twice`（`consumed_at` 与 `revoked_at` 同时非空）、`should_reject_duplicate_token_hash`、`should_accept_revoked_login_end_reason`。

- [ ] **Step 3: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*EmailTokenRepositoryAdapterIT*" --tests "*IdentitySchemaConstraintsIT*"`
Expected: FAIL，编译错误

- [ ] **Step 4: 实现 Entity、Dao、Mapper、Adapter**

- [ ] **Step 5: 跑测试确认通过**

Run: 同 Step 3，另跑 `./gradlew :patra-api:patra-identity:patra-identity-infra:test`（Mapper 生成、SpotBugs）
Expected: PASS。把「`JdbcClient` 的 `UPDATE … RETURNING` 在 `@DataJpaTest` 事务里返回更新后的行」写回 spec §16 第 4 条。

- [ ] **Step 6: 提交**

```bash
git add patra-api/patra-identity/patra-identity-infra/src patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/enums/LoginEndReason.java
git commit -m "feat(identity): 建一次性令牌表与仓储，登录记录结束原因加 REVOKED (PAP-96)"
```

---

### Task 5: 会话：撤销除某条外的全部会话

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-session/src/main/java/dev/patra/identity/session/RedisSessionStore.java`
- Create: `patra-api/patra-identity/patra-identity-session/src/main/resources/redis/session-delete-others.lua`
- Modify: `patra-api/patra-identity/patra-identity-session/src/integrationTest/java/dev/patra/identity/session/RedisSessionStoreIT.java`
- Modify: `patra-api/patra-identity/patra-identity-session/README.md`（操作表加一行）
- Modify: `…/domain/port/session/SessionStorePort.java`
- Modify: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/session/SessionStoreAdapter.java`
- Modify: `patra-api/patra-identity/patra-identity-infra/src/test/java/dev/patra/identity/infra/adapter/session/SessionStoreAdapterTest.java`

**Interfaces:**
- Produces: `RedisSessionStore.deleteAllExcept(AccountType accountType, long userId, long keepSessionId) → List<Long>`（脚本 `DELETE_OTHERS_SCRIPT`，`KEYS[1]` 索引键，`ARGV[1]` 会话键前缀，`ARGV[2]` 保留的会话 ID）。
- Produces: `SessionStorePort.revokeAllExcept(AccountType accountType, long userId, long keepSessionId) → List<Long>`；`SessionStoreAdapter` 委托 `deleteAllExcept`。

- [ ] **Step 1: 写失败的集成测试**

`RedisSessionStoreIT` 加：

```java
@Test
@DisplayName("deleteAllExcept：保留指定会话，删掉其余并返回它们的 ID；指定的会话不存在时等于全删")
void should_delete_all_sessions_except_the_kept_one() {
  long userId = uniqueUserId();
  SessionToken first = store.create(session(userId, 1L)).token();
  SessionToken second = store.create(session(userId, 2L)).token();
  SessionToken third = store.create(session(userId, 3L)).token();

  List<Long> deleted = store.deleteAllExcept(AccountType.USER, userId, 2L);

  assertThat(deleted).containsExactlyInAnyOrder(1L, 3L);
  assertThat(redis.hasKey(RedisSessionStore.sessionKey(AccountType.USER, second.hash()))).isTrue();
  assertThat(redis.hasKey(RedisSessionStore.sessionKey(AccountType.USER, first.hash()))).isFalse();
  assertThat(redis.hasKey(RedisSessionStore.sessionKey(AccountType.USER, third.hash()))).isFalse();
  assertThat(redis.opsForHash().keys(RedisSessionStore.indexKey(AccountType.USER, userId))).containsExactly("2");

  assertThat(store.deleteAllExcept(AccountType.USER, userId, 99L)).containsExactly(2L);
  assertThat(redis.hasKey(RedisSessionStore.indexKey(AccountType.USER, userId))).isFalse();
}
```

（`session(userId, sessionId)` 与 `uniqueUserId()` 照该类已有的辅助方法。）`SessionStoreAdapterTest` 加 `should_delegate_revoke_all_except`：`when(store.deleteAllExcept(USER, 42L, 7L)).thenReturn(List.of(5L, 6L))` → `adapter.revokeAllExcept(USER, 42L, 7L)` 返回同列表。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest --tests "*RedisSessionStoreIT*"`
Expected: FAIL，编译错误

- [ ] **Step 3: 写脚本与方法**

`session-delete-others.lua`：

```lua
-- 删掉一个用户除指定一条之外的全部会话。
-- KEYS[1] 用户的会话索引
-- ARGV[1] 会话键前缀  ARGV[2] 要保留的会话 ID
-- 返回：会话键真正删掉了的会话 ID 列表
local entries = redis.call('HGETALL', KEYS[1])
local deleted = {}
for i = 1, #entries, 2 do
  if entries[i] ~= ARGV[2] then
    if redis.call('DEL', ARGV[1] .. entries[i + 1]) == 1 then
      deleted[#deleted + 1] = entries[i]
    end
    redis.call('HDEL', KEYS[1], entries[i])
  end
end
return deleted
```

`deleteAllExcept` 照 `deleteAll` 的写法（`execute` 包一层、`toSessionIds`）。端口方法、适配器方法、README 表加一行「`deleteAllExcept(accountType, userId, keepSessionId)` / `session-delete-others.lua` / 删该用户除指定一条外的全部会话；返回真的删掉了的会话 ID」。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest --tests "*RedisSessionStoreIT*" :patra-api:patra-identity:patra-identity-session:check :patra-api:patra-identity:patra-identity-infra:test --tests "*SessionStoreAdapterTest*"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-session patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/port/session/SessionStorePort.java patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/session/SessionStoreAdapter.java patra-api/patra-identity/patra-identity-infra/src/test/java/dev/patra/identity/infra/adapter/session/SessionStoreAdapterTest.java
git commit -m "feat(identity): 会话存储加撤销除当前外全部会话 (PAP-96)"
```

---

### Task 6: 重发限流：Lua 与 `EmailResendThrottleAdapter`

**Files:**
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/resources/redis/email-resend-throttle.lua`
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/throttle/EmailResendThrottleAdapter.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/test/java/dev/patra/identity/infra/adapter/throttle/EmailResendThrottleAdapterTest.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/integrationTest/java/dev/patra/identity/infra/adapter/throttle/EmailResendThrottleAdapterIT.java`

**Interfaces:**
- Consumes: Task 2 的 `EmailResendThrottlePort`、`EmailResendPolicy`、`EmailResendLimitedException`；`TemporarilyUnavailableException`、`TransientRedisFailures`。
- Produces: `@Component public final class EmailResendThrottleAdapter implements EmailResendThrottlePort`，构造器 `(StringRedisTemplate redis, EmailResendPolicy policy)`；`static List<String> keys(long userId, String clientIp)`（四个键，`clientIp` 为 `null` 时第四个是 `idn:email-resend:hour:ip:-`）；`acquire` 以 `enforce = 1` 调脚本，返回 `null` 抛 `TemporarilyUnavailableException`，`> 0` 返回 `Optional.of(Duration.ofMillis(result))`，`0` 返回空；`markSent(userId, now)` 以 `enforce = 0`、`ipPresent = 0`、占位 IP 键调脚本、忽略返回值；Redis 暂时失败按 `TransientRedisFailures.isTransient` 转 `TemporarilyUnavailableException`。
- 脚本参数：`KEYS` 四个键；`ARGV[1]` now 毫秒、`[2]` 冷却毫秒、`[3]` 小时窗口毫秒、`[4]` 天窗口毫秒、`[5]` 小时上限、`[6]` 天上限、`[7]` IP 小时上限、`[8]` enforce、`[9]` ipPresent（1 / 0）、`[10]` 成员后缀（`UUID.randomUUID()`）。返回等待毫秒，放行为 `0`。

- [ ] **Step 1: 写失败的测试**

```java
@DisplayName("EmailResendThrottleAdapter 单元测试")
class EmailResendThrottleAdapterTest {
  @Test
  void should_hash_ip_in_key_and_use_placeholder_when_absent() {
    List<String> keys = EmailResendThrottleAdapter.keys(42L, "203.0.113.9");
    assertThat(keys).hasSize(4).startsWith("idn:email-resend:cooldown:user:42", "idn:email-resend:hour:user:42", "idn:email-resend:day:user:42");
    assertThat(keys.get(3)).matches("^idn:email-resend:hour:ip:[0-9a-f]{32}$").doesNotContain("203.0.113.9");
    assertThat(EmailResendThrottleAdapter.keys(42L, null).get(3)).isEqualTo("idn:email-resend:hour:ip:-");
  }
}
```

`EmailResendThrottleAdapterIT`（照 `LoginThrottleAdapterIT` 连共享 Redis 容器；`POLICY = EmailResendPolicy.of(Duration.ofSeconds(60), 5, 10, 30)`；每个用例用新的 `userId`、新的 IP 文本避免互相干扰；`T0 = Instant.parse("2026-10-21T08:00:00Z")`）：

- `should_allow_first_send_and_block_within_cooldown`：`acquire(u, ip, T0)` 为空；`acquire(u, ip, T0 + 10s)` 为 `Optional.of(50s)`；`acquire(u, ip, T0 + 60s)` 为空。
- `should_block_sixth_send_within_an_hour_until_oldest_leaves_window`：T0、+61s、+122s、+183s、+244s 五次放行；+305s 第六次返回 `Duration.ofSeconds(3600 - 305)`；`T0 + 3600s` 放行。
- `should_block_eleventh_send_within_a_day`：每 10 分钟一次，第 11 次（`T0 + 100min`）返回到 `T0 + 24h` 的剩余；`T0 + 24h` 放行。
- `should_block_thirty_first_send_from_same_ip_across_users`：30 个不同用户同一 IP 各一次（同一时刻 T0）放行，第 31 个用户返回 `Duration.ofHours(1)`；`clientIp` 为 `null` 的第 32 个用户放行（跳过 IP 维）。
- `should_return_longest_wait_when_several_dimensions_block`：T0、+61s、+122s、+183s、+244s 五次放行后在 +254s 再试：冷却还剩 50 秒、小时窗口还剩 3346 秒 → 返回 `Duration.ofSeconds(3346)`。
- `should_count_without_blocking_on_mark_sent`：`markSent(u, T0)` 六次不抛；随后 `acquire(u, ip, T0)` 返回有值（冷却 60 秒）。
- `should_not_count_ip_dimension_on_mark_sent`：`markSent(u, T0)` 后 `idn:email-resend:hour:ip:-` 键不存在；30 个用户各 `markSent` 后，第 31 个用户带 IP `acquire` 仍放行。
- `should_set_ttl_on_keys`：四个键的 `PTTL` 都为正。
- `should_translate_connection_failure_to_503`：指向没人监听的端口的 `StringRedisTemplate`（照 `LoginThrottleAdapterIT` 的做法）→ `TemporarilyUnavailableException`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests "*EmailResendThrottleAdapterTest*" :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*EmailResendThrottleAdapterIT*"`
Expected: FAIL，编译错误

- [ ] **Step 3: 写脚本与适配器**

`email-resend-throttle.lua`：

```lua
-- 重发验证邮件的限流：冷却 + 三个滑动窗口，一次判完一次计数。
-- KEYS[1] 冷却  KEYS[2] 账号小时窗口  KEYS[3] 账号天窗口  KEYS[4] IP 小时窗口
-- ARGV[1] now 毫秒  ARGV[2] 冷却毫秒  ARGV[3] 小时窗口毫秒  ARGV[4] 天窗口毫秒
-- ARGV[5] 小时上限  ARGV[6] 天上限  ARGV[7] IP 小时上限  ARGV[8] 1 判且计 / 0 只计
-- ARGV[9] 1 有 IP / 0 没有  ARGV[10] 成员后缀
-- 返回：0 放行并已计数；正数是还要等的毫秒
local now = tonumber(ARGV[1])
local cooldown = tonumber(ARGV[2])
local windows = { { KEYS[2], tonumber(ARGV[3]), tonumber(ARGV[5]) }, { KEYS[3], tonumber(ARGV[4]), tonumber(ARGV[6]) } }
if ARGV[9] == '1' then
  windows[#windows + 1] = { KEYS[4], tonumber(ARGV[3]), tonumber(ARGV[7]) }
end
if ARGV[8] == '1' then
  local wait = 0
  local last = tonumber(redis.call('GET', KEYS[1]) or '0')
  if last > 0 and now - last < cooldown then
    wait = math.max(wait, last + cooldown - now)
  end
  for _, w in ipairs(windows) do
    redis.call('ZREMRANGEBYSCORE', w[1], '-inf', now - w[2])
    if redis.call('ZCARD', w[1]) >= w[3] then
      local oldest = redis.call('ZRANGE', w[1], 0, 0, 'WITHSCORES')
      wait = math.max(wait, tonumber(oldest[2]) + w[2] - now)
    end
  end
  if wait > 0 then
    return wait
  end
end
redis.call('SET', KEYS[1], now, 'PX', cooldown)
local member = now .. ':' .. ARGV[10]
for _, w in ipairs(windows) do
  redis.call('ZADD', w[1], now, member)
  redis.call('PEXPIRE', w[1], w[2])
end
return 0
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-infra/src/main/resources/redis/email-resend-throttle.lua patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/throttle/EmailResendThrottleAdapter.java patra-api/patra-identity/patra-identity-infra/src/test/java/dev/patra/identity/infra/adapter/throttle/EmailResendThrottleAdapterTest.java patra-api/patra-identity/patra-identity-infra/src/integrationTest/java/dev/patra/identity/infra/adapter/throttle/EmailResendThrottleAdapterIT.java
git commit -m "feat(identity): 加重发验证邮件的四维度滑动窗口限流 (PAP-96)"
```

---

### Task 7: 邮件渲染：Thymeleaf 模板与 `ThymeleafMailRenderer`

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-infra/build.gradle.kts`（加 `implementation("org.thymeleaf:thymeleaf")`）
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/mail/ThymeleafMailRenderer.java`、`RenderedMail.java`、`SecurityNoticeView.java`
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/resources/mail/html/email-verification.html`、`mail/html/fragments/layout.html`、`mail/html/fragments/security-notice.html`、`mail/text/email-verification.txt`、`mail/text/fragments/layout.txt`、`mail/text/fragments/security-notice.txt`
- Test: `patra-api/patra-identity/patra-identity-infra/src/test/java/dev/patra/identity/infra/adapter/mail/ThymeleafMailRendererTest.java`
- Test resources: `patra-api/patra-identity/patra-identity-infra/src/test/resources/mail/html/security-notice-probe.html`、`src/test/resources/mail/text/security-notice-probe.txt`

**Interfaces:**
- Consumes: Task 2 的 `OutgoingMail`、`EmailVerificationMail`、`SecurityNotice`、`MailLinkPolicy`。
- Produces: `public record RenderedMail(String subject, String text, String html)`，`of(...)`。
- Produces: `public record SecurityNoticeView(String occurredAt, String device, String ipAddress, String ipLocation, String notMeAdvice)`（`@Builder`）；`static SecurityNoticeView from(SecurityNotice notice, ZoneId zone)`：`occurredAt` = `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")` 按 `zone` 格式化，再拼 ` (UTC` + 偏移 + `)`，偏移取 `zone.getRules().getOffset(instant)`，整点写成 `+8`、非整点写成 `+5:30`；`device` / `ipAddress` / `ipLocation` 为 `null` 时换成 `"未知"`。
- Produces: `public final class ThymeleafMailRenderer`，构造器 `(MailLinkPolicy links, ZoneId zone)`，内部建 `TemplateEngine` + 两个 `ClassLoaderTemplateResolver`（HTML：`resolvablePatterns = {"html/*"}`、prefix `mail/`、suffix `.html`、`TemplateMode.HTML`、UTF-8；TEXT：`{"text/*"}`、`.txt`、`TemplateMode.TEXT`）；`RenderedMail render(OutgoingMail mail)`：上下文 = `mail.model()` + `siteName = "Patra"` + `securityUrl = links.securitySettingsUrl()`，`model` 里 `notice` 键的 `SecurityNotice` 换成 `SecurityNoticeView`；渲染 `html/<template>` 与 `text/<template>`。

- [ ] **Step 1: 写失败的测试**

```java
@DisplayName("ThymeleafMailRenderer 单元测试")
class ThymeleafMailRendererTest {
  private static final MailLinkPolicy LINKS = MailLinkPolicy.of("https://patra.example", "/verify-email", "/settings/security");
  private final ThymeleafMailRenderer renderer = new ThymeleafMailRenderer(LINKS, ZoneId.of("Asia/Shanghai"));

  @Test
  void should_render_verification_mail_in_text_and_html() {
    RenderedMail rendered = renderer.render(EmailVerificationMail.of(EmailAddress.of("chen.yu@example.com"), "陈宇",
        "https://patra.example/verify-email#token=abc", Instant.parse("2026-10-22T08:00:00Z")));
    assertThat(rendered.subject()).isEqualTo("验证你的 Patra 邮箱");
    assertThat(rendered.text()).contains("陈宇").contains("https://patra.example/verify-email#token=abc").contains("24 小时").doesNotContain("<");
    assertThat(rendered.html()).contains("href=\"https://patra.example/verify-email#token=abc\"").contains("陈宇").contains("Patra 不会在邮件里向你索要密码").doesNotContain("th:");
  }

  @Test
  void should_escape_html_in_display_name_but_keep_text_raw() {
    RenderedMail rendered = renderer.render(EmailVerificationMail.of(EmailAddress.of("a@example.com"), "<b>x</b>", "https://patra.example/verify-email#token=t", Instant.EPOCH));
    assertThat(rendered.html()).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>");
    assertThat(rendered.text()).contains("<b>x</b>");
  }

  @Test
  void should_render_security_notice_fragment_with_unknown_placeholders() {
    OutgoingMail probe = probe(SecurityNotice.builder().occurredAt(Instant.parse("2026-10-21T08:30:00Z")).notMeAdvice("请立即修改密码").build());
    RenderedMail rendered = renderer.render(probe);
    assertThat(rendered.text()).contains("2026-10-21 16:30 (UTC+8)").contains("设备：未知").contains("IP：未知").contains("请立即修改密码").contains("https://patra.example/settings/security");
    assertThat(rendered.html()).contains("2026-10-21 16:30 (UTC+8)").contains("未知").contains("https://patra.example/settings/security");
  }

  @Test
  void should_render_security_notice_fields_when_present() {
    OutgoingMail probe = probe(SecurityNotice.builder().occurredAt(Instant.parse("2026-10-21T08:30:00Z")).device("Chrome 131 · macOS").ipAddress("203.0.113.9").ipLocation("中国 上海").notMeAdvice("x").build());
    assertThat(renderer.render(probe).text()).contains("Chrome 131 · macOS").contains("203.0.113.9").contains("中国 上海");
  }

  /// 测试资源里的探针模板 `security-notice-probe`，只放安全通知片段。
  private static OutgoingMail probe(SecurityNotice notice) { /* 匿名实现：to 任意、template "security-notice-probe"、subject "probe"、model Map.of("notice", notice) */ }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests "*ThymeleafMailRendererTest*"`
Expected: FAIL，编译错误

- [ ] **Step 3: 写模板与渲染器**

文本模板 `text/email-verification.txt`（正文是 spec §9.3 定的文案，`layout.txt` 的 `header(title)` / `footer()` 片段分别输出「Patra」抬头与页脚两行「此邮件由 Patra 系统自动发出，请勿回复。」「Patra 不会在邮件里向你索要密码。」）：

```
[# th:replace="~{text/fragments/layout :: header('验证你的 Patra 邮箱')}" /]
你好，[(${displayName})]：

感谢注册 Patra。请打开下面的链接完成邮箱验证：

[(${verifyUrl})]

链接 [(${validHours})] 小时内有效，只能使用一次。
如果不是你注册的 Patra，忽略这封邮件即可，不要点链接。
[# th:replace="~{text/fragments/layout :: footer}" /]
```

HTML 模板同样内容：单列 600px、内联样式、主按钮「验证邮箱」+ 一行完整链接文字、页脚同上，用 `th:text` / `th:href`，不用 `th:utext`。`security-notice` 片段（HTML 与 TEXT 各一）输出四行：`时间：`、`设备：`、`IP：{ipAddress}（{ipLocation}）`、`不是我？{notMeAdvice} 建议立即到安全设置修改密码并检查登录设备：{securityUrl}`。测试资源的探针模板只 `th:replace` 这个片段。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS。把「两个 `ClassLoaderTemplateResolver` 按 `resolvablePatterns` 分流」的结果写回 spec §16 第 5 条。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-infra/build.gradle.kts patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/mail patra-api/patra-identity/patra-identity-infra/src/main/resources/mail patra-api/patra-identity/patra-identity-infra/src/test/java/dev/patra/identity/infra/adapter/mail patra-api/patra-identity/patra-identity-infra/src/test/resources/mail
git commit -m "feat(identity): 加邮件模板与 Thymeleaf 双模板渲染器 (PAP-96)"
```

---

### Task 8: `GreenMailSmtpInitializer`（tool-test）与 `SmtpEmailDeliveryAdapter`

**Files:**
- Modify: `gradle/libs.versions.toml`（`greenmail = "2.1.14"`；`greenmail = { module = "com.icegreen:greenmail", version.ref = "greenmail" }`）
- Modify: `patra-tool/patra-tool-test/build.gradle.kts`（`api(libs.greenmail)`）
- Create: `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/mail/GreenMailSmtpInitializer.java`
- Test: `patra-tool/patra-tool-test/src/test/java/dev/patra/tool/test/mail/GreenMailSmtpInitializerTest.java`
- Modify: `patra-api/patra-identity/patra-identity-infra/build.gradle.kts`（加 `implementation("org.springframework.boot:spring-boot-starter-mail")`）
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/mail/SmtpEmailDeliveryAdapter.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/test/java/dev/patra/identity/infra/adapter/mail/SmtpEmailDeliveryAdapterTest.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/integrationTest/java/dev/patra/identity/infra/adapter/mail/SmtpEmailDeliveryAdapterIT.java`

**Interfaces:**
- Produces: `public class GreenMailSmtpInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>`：`static GreenMail getGreenMail()`（第一次调用 `new GreenMail(ServerSetupTest.SMTP.dynamicPort()).withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())` 并 `start()`，JVM 内单例）；`static int getSmtpPort()`；`static void purge()`（`purgeEmailFromAllMailboxes()`）；`initialize` 写 `spring.mail.host=127.0.0.1`、`spring.mail.port=<端口>`、`spring.mail.properties.mail.smtp.auth=false`、`spring.mail.properties.mail.smtp.ssl.enable=false`、`spring.mail.properties.mail.smtp.starttls.enable=false`。
- Produces: `public final class SmtpEmailDeliveryAdapter implements EmailDeliveryPort, AutoCloseable`：公开构造器 `(JavaMailSender mailSender, ThymeleafMailRenderer renderer, String from, Executor executor)`（`from` 用 `InternetAddress.parse(from, false)` 校验、只能一个地址，否则 `IllegalArgumentException`）；`static SmtpEmailDeliveryAdapter async(JavaMailSender, ThymeleafMailRenderer, String from, int maxConcurrency)`（`SimpleAsyncTaskExecutor`：`setVirtualThreads(true)`、`setConcurrencyLimit(maxConcurrency)`、`setThreadNamePrefix("mail-")`、`setTaskTerminationTimeout(10_000)`）；`send(OutgoingMail)` 把 `deliver(mail)` 交给执行器；`deliver`：`MimeMessageHelper(message, true, "UTF-8")`、`setFrom`、`setTo(mail.to().value())`、`setSubject`、`setText(text, html)`、`message.addHeader("Auto-Submitted", "auto-generated")`、`addHeader("X-Auto-Response-Suppress", "All")`、`mailSender.send`；`RuntimeException` / `MessagingException` 捕获后 `log.warn("邮件发送失败: template={}", mail.template(), e)`；`close()` 关执行器（若是 `AutoCloseable`）。

- [ ] **Step 1: 写失败的测试**

`GreenMailSmtpInitializerTest`：`should_start_once_and_expose_port`（两次 `getGreenMail()` 同一实例，端口 > 0）；`should_write_mail_properties_into_environment`（`GenericApplicationContext` 经 `initialize` 后 `environment.getProperty("spring.mail.port")` 等于端口、`spring.mail.host` 为 `127.0.0.1`）。

`SmtpEmailDeliveryAdapterTest`（同步执行器 `Runnable::run`，`JavaMailSender` 用 `mock`，`createMimeMessage()` 返回 `new MimeMessage(Session.getInstance(new Properties()))`）：

- `should_send_multipart_alternative_with_auto_headers`：捕获 `send(MimeMessage)`；`getFrom()[0]` 为 `Patra <no-reply@patra.local>`；收件人 `chen.yu@example.com`；主题 `验证你的 Patra 邮箱`；`getHeader("Auto-Submitted")[0]` 为 `auto-generated`、`X-Auto-Response-Suppress` 为 `All`；内容是 `MimeMultipart`，`getContentType()` 含 `multipart/alternative`（`MimeMessageHelper` 的多部分模式下外层是 `multipart/mixed` 包着 `multipart/alternative`——断言用递归找到 `alternative` 部分），第一部分 `text/plain` 含链接、第二部分 `text/html` 含 `href`。
- `should_log_and_swallow_failures`（`@ExtendWith(OutputCaptureExtension.class)`）：`doThrow(new MailSendException("boom")).when(mailSender).send(any(MimeMessage.class))` → `send` 不抛；输出含 `邮件发送失败` 和 `template=email-verification`，不含收件人地址。
- `should_hand_work_to_executor`：执行器只记录不执行 → `mailSender.send` 未被调用，记录了一个任务。
- `should_reject_invalid_from_address`：`"not an address"` → `IllegalArgumentException`。

`SmtpEmailDeliveryAdapterIT`：`JavaMailSenderImpl` 指向 `GreenMailSmtpInitializer.getGreenMail()` 的 `127.0.0.1:端口`；`SmtpEmailDeliveryAdapter.async(sender, renderer, "Patra <no-reply@patra.local>", 2)`；`@BeforeEach purge()`；`send` 后 `greenMail.waitForIncomingEmail(5000, 1)` 为真；`getReceivedMessages()[0]`：主题解码后是 `验证你的 Patra 邮箱`、`From` / `To` 正确、`Auto-Submitted` 头在、正文两部分各含链接。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-tool:patra-tool-test:test --tests "*GreenMailSmtpInitializerTest*" :patra-api:patra-identity:patra-identity-infra:test --tests "*SmtpEmailDeliveryAdapterTest*"`
Expected: FAIL，编译错误

- [ ] **Step 3: 实现初始化器与适配器**

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-tool:patra-tool-test:test --tests "*GreenMailSmtpInitializerTest*" :patra-tool:patra-tool-test:check :patra-api:patra-identity:patra-identity-infra:test --tests "*SmtpEmailDeliveryAdapterTest*" :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*SmtpEmailDeliveryAdapterIT*"`
Expected: PASS。写回 spec §16 第 7、8 条。

- [ ] **Step 5: 提交**

```bash
git add gradle/libs.versions.toml patra-tool/patra-tool-test patra-api/patra-identity/patra-identity-infra/build.gradle.kts patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/mail/SmtpEmailDeliveryAdapter.java patra-api/patra-identity/patra-identity-infra/src/test/java/dev/patra/identity/infra/adapter/mail/SmtpEmailDeliveryAdapterTest.java patra-api/patra-identity/patra-identity-infra/src/integrationTest/java/dev/patra/identity/infra/adapter/mail
git commit -m "feat(identity): 加 SMTP 异步发信适配器与 GreenMail 测试桩 (PAP-96)"
```

---

### Task 9: 启动层配置与装配

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/java/dev/patra/identity/config/IdentityProperties.java`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/java/dev/patra/identity/config/IdentityConfiguration.java`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application.yml`（spec §13 的 jar 部分：`management.health.mail.enabled: false`、`patra.identity.mail.*` 的静态项）
- Modify: `patra-infra/nacos/dev/patra-identity.yaml`（spec §13 的 Nacos 部分：`spring.mail.*`、`patra.identity.mail.from`、`patra.identity.mail.links.base-url`）
- Modify: `patra-api/patra-identity/patra-identity-boot/src/test/java/dev/patra/identity/config/IdentityConfigurationTest.java`、`…/ApplicationConfigTest.java`
- Modify: boot 模块 `src/integrationTest` 下全部 `@SpringBootTest` 类的 `@ContextConfiguration(initializers = …)` 加 `GreenMailSmtpInitializer.class`（同一组注解的类改法完全一致；`RedisUnavailableIT` 也加）

**Interfaces:**
- Produces: `IdentityProperties` 加 `@DefaultValue Mail mail`；`record Mail(@DefaultValue("Patra <no-reply@patra.local>") String from, @DefaultValue("Asia/Shanghai") String zone, @DefaultValue("30d") Duration tokenRetention, @DefaultValue Links links, @DefaultValue Resend resend)`；`record Links(@DefaultValue("http://linqibins-mac-mini:4000") String baseUrl, @DefaultValue("/verify-email") String verifyEmailPath, @DefaultValue("/settings/security") String securitySettingsPath)`；`record Resend(@DefaultValue("60s") Duration cooldown, @DefaultValue("5") int perAccountHourly, @DefaultValue("10") int perAccountDaily, @DefaultValue("30") int perIpHourly)`。
- Produces: `IdentityConfiguration` 的 Bean：`EmailResendPolicy emailResendPolicy(IdentityProperties)`、`MailLinkPolicy mailLinkPolicy(IdentityProperties)`、`EmailVerificationIssuer emailVerificationIssuer(EmailTokenRepository, MailLinkPolicy, Clock)`（`new SecureRandom()`）、`ThymeleafMailRenderer mailRenderer(MailLinkPolicy, IdentityProperties)`（`ZoneId.of(zone)`）、`EmailDeliveryPort emailDeliveryPort(JavaMailSender, ThymeleafMailRenderer, IdentityProperties)`（`SmtpEmailDeliveryAdapter.async(..., from, 4)`）。
- `application.yml` 与 `patra-identity.yaml` 按 spec §13 分两处写（`xxl.job.*` 留到 Task 13）。boot IT 从本任务起需要 `JavaMailSender`：由 `GreenMailSmtpInitializer` 写入 `spring.mail.host` / `port`。

- [ ] **Step 1: 写失败的测试**

`IdentityConfigurationTest` 的 `bare()` 再 `withBean(EmailTokenRepository.class, () -> mock(EmailTokenRepository.class))`、`withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))`。新用例：

- `should_bind_mail_defaults`：上下文里有 `EmailResendPolicy`（`cooldown` 60s、5 / 10 / 30）、`MailLinkPolicy`（`verifyEmailUrl` 以 `http://linqibins-mac-mini:4000/verify-email#token=` 开头）、`EmailVerificationIssuer`、`ThymeleafMailRenderer`、`EmailDeliveryPort`（是 `SmtpEmailDeliveryAdapter`）。
- `should_fail_when_resend_limit_not_positive`：`patra.identity.mail.resend.per-account-hourly=0` → `hasFailed()`，原因链里有 `IllegalArgumentException`。
- `should_fail_when_base_url_is_not_absolute_http`：`patra.identity.mail.links.base-url=patra.example` → 失败。
- `should_fail_when_from_is_not_an_address`：`patra.identity.mail.from=nobody` → 失败。

`ApplicationConfigTest` 加 `should_disable_mail_health_indicator_and_keep_environment_keys_in_nacos`：`application.yml` 里 `management.health.mail.enabled` 的值是 `false`，且没有以 `spring.mail.` 开头、也没有 `patra.identity.mail.from` / `patra.identity.mail.links.base-url` 的键。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:test --tests "*IdentityConfigurationTest*"`
Expected: FAIL

- [ ] **Step 3: 实现配置记录、Bean 与 `application.yml`**

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:test`（含 `ApplicationConfigTest`）、`./gradlew :patra-tool:patra-tool-launch:test --tests "*JarConfigOverlapCheckerTest*" --tests "*NacosConfigFilesTest*"`（jar 与 Nacos 不重复、Nacos 文件合规）、`./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*PatraIdentityApplicationIT*" --tests "*RedisUnavailableIT*"`（挂了 GreenMail 的上下文能起）
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-boot/src patra-infra/nacos/dev/patra-identity.yaml
git commit -m "feat(identity): 装配邮件发送、链接与重发限流的配置 (PAP-96)"
```

---

### Task 10: 注册成功后签发令牌并发验证邮件

开工前加载 `patra-backend:patra-hexagonal`。

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-app/src/main/java/dev/patra/identity/app/usecase/register/RegisterUserCommand.java`（加 `String clientIp`）
- Modify: `…/app/usecase/register/RegisterUserHandler.java`
- Modify: `patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/auth/AuthController.java`（`register` 加 `HttpServletRequest` 参数，`clientIp(request.getRemoteAddr())`）
- Modify: `patra-api/patra-identity/patra-identity-app/src/test/java/dev/patra/identity/app/usecase/register/RegisterUserHandlerTest.java`
- Modify: `patra-api/patra-identity/patra-identity-adapter/src/integrationTest/java/dev/patra/identity/adapter/rest/auth/AuthControllerIT.java`（注册用例断言命令的 `clientIp()` 是 `127.0.0.1`）

**Interfaces:**
- Consumes: Task 3 的 `EmailVerificationIssuer.prepare`、Task 2 的 `EmailDeliveryPort.send`、`EmailResendThrottlePort.markSent`。
- Produces: `RegisterUserHandler` 新增依赖 `EmailVerificationIssuer emailVerificationIssuer`、`EmailDeliveryPort emailDelivery`、`EmailResendThrottlePort emailResendThrottle`；`Registration` 记录加 `PreparedVerificationMail verification`；事务里最后一步 `emailVerificationIssuer.prepare(saved, account.displayName(), command.clientIp())`；事务后 `markSentQuietly(userId, now)`（`RuntimeException` 记 WARN）再 `emailDelivery.send(verification.mail())`。

- [ ] **Step 1: 写失败的测试**

`RegisterUserHandlerTest` 加三个 mock 并改构造；新用例：

- `should_issue_verification_token_inside_transaction_and_send_after_commit`：`TransactionOperations` 桩在回调执行期间把 `AtomicBoolean inTransaction` 置真、回调结束置假；`emailVerificationIssuer.prepare` 的桩记录调用时 `inTransaction.get()` 为真；`emailDelivery.send` 的桩记录调用时为假；`verify(emailDelivery).send(prepared.mail())`；`InOrder`：`sessionIssuer.issue` → `prepare` → `markSent` → `send`。
- `should_pass_client_ip_to_issuer_and_count_without_ip`：命令 `clientIp("203.0.113.9")` → `prepare(any(), eq("chen-yu"), eq("203.0.113.9"))`、`markSent(42L, NOW)`。
- `should_still_register_and_send_when_throttle_marking_fails`：`doThrow(new TemporarilyUnavailableException()).when(emailResendThrottle).markSent(...)` → 返回 201 的结果、`send` 照样被调。
- 现有用例的 `prepare` 桩返回一个固定的 `PreparedVerificationMail`。

`AuthControllerIT` 注册用例：捕获的 `RegisterUserCommand.clientIp()` 等于 `"127.0.0.1"`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*RegisterUserHandlerTest*"`
Expected: FAIL，编译错误

- [ ] **Step 3: 实现**

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*RegisterUserHandlerTest*" :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*AuthControllerIT*" :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*AccountFlowIT*" --tests "*SessionFlowIT*"`
Expected: PASS（boot IT 的注册现在会往 GreenMail 发信）

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-app/src patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/auth/AuthController.java patra-api/patra-identity/patra-identity-adapter/src/integrationTest
git commit -m "feat(identity): 注册成功后签发验证令牌并异步发验证邮件 (PAP-96)"
```

---

### Task 11: 重发验证邮件 `POST /me/email/verification/resend`

**Files:**
- Create: `patra-api/patra-identity/patra-identity-app/src/main/java/dev/patra/identity/app/usecase/email/ResendEmailVerificationCommand.java`、`EmailVerificationResent.java`、`ResendEmailVerificationHandler.java`
- Create: `patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/me/MeEmailController.java`、`…/rest/me/response/ResendEmailVerificationResponse.java`
- Test: `…/app/src/test/java/dev/patra/identity/app/usecase/email/ResendEmailVerificationHandlerTest.java`
- Test: `…/adapter/src/integrationTest/java/dev/patra/identity/adapter/rest/me/MeEmailControllerIT.java`

**Interfaces:**
- Produces: `public record ResendEmailVerificationCommand(String clientIp) implements Command<EmailVerificationResent>`，`of(clientIp)`；`public record EmailVerificationResent(String email, Instant nextResendAt)`，`of(...)`。
- Produces: `@Component ResendEmailVerificationHandler implements CommandHandler<ResendEmailVerificationCommand, EmailVerificationResent>`，依赖 `CurrentUserPort`、`UserRepository`、`UserReadPort`、`EmailResendThrottlePort`、`EmailResendPolicy`、`EmailVerificationIssuer`、`EmailDeliveryPort`、`TransactionOperations`、`Clock`；流程按 spec §8.2（状态预判 → `acquire` → 事务里 `findByIdForUpdate` + 重判 + `prepare` → 事务后 `send`）。
- Produces: `@RestController @RequestMapping("/me/email") MeEmailController`：`@PostMapping("/verification/resend") @ResponseStatus(HttpStatus.ACCEPTED) ResendEmailVerificationResponse resend(HttpServletRequest request)`；`record ResendEmailVerificationResponse(String email, Instant nextResendAt)`，`from(EmailVerificationResent)`。

- [ ] **Step 1: 写失败的测试**

`ResendEmailVerificationHandlerTest`（`NOW = 2026-10-21T08:00:00Z`，当前用户 `CurrentUser.of(42L, 7L, USER, WEB)`，`POLICY = EmailResendPolicy.of(60s, 5, 10, 30)`）：

- `should_resend_in_order_and_return_next_resend_at`：`users.findById` 与 `findByIdForUpdate` 都给未验证的 ACTIVE 用户（`thenAnswer` 每次新实例）；`acquire(42L, "203.0.113.9", NOW)` 返回空；`findAccount` 给 `displayName` `陈宇`；`prepare` 返回固定 `PreparedVerificationMail` → 结果 `email` 为用户邮箱、`nextResendAt` 为 `NOW + 60s`；`InOrder`：`acquire` → `findByIdForUpdate` → `prepare` → `send`；`prepare` 在事务内、`send` 在事务外（同 Task 10 的 `AtomicBoolean` 技巧）。
- `should_return_409_when_already_verified_without_touching_throttle`：`findById` 给已验证用户 → `EmailAlreadyVerifiedException`；`verifyNoInteractions(emailResendThrottle, emailDelivery)`。
- `should_return_409_when_pending_deletion`：`UserPendingDeletionException`。
- `should_return_401_when_banned_or_missing`：`BANNED` → `AuthenticationRequiredException`；`findById` 为空 → 同样。
- `should_return_429_with_retry_after_and_not_issue`：`acquire` 返回 `Optional.of(Duration.ofSeconds(37))` → `EmailResendLimitedException` 且 `getRetryAfter()` 为 37 秒；`verifyNoInteractions(emailVerificationIssuer, emailDelivery)`；`transactions` 未被调用。
- `should_recheck_after_lock_and_not_send_when_verified_meanwhile`：`findById` 未验证、`findByIdForUpdate` 已验证 → 409，`send` 未调。

`MeEmailControllerIT`（照 `MeControllerIT`：`@WebMvcTest` + `@Import(MeEmailController.class)` + `@MockitoBean CommandBus`）：

- `should_accept_resend_with_202`：`commandBus.handle(any(ResendEmailVerificationCommand.class))` 返回 `EmailVerificationResent.of("chen.yu@example.com", Instant.parse("2026-10-21T08:01:00Z"))` → 202、`$.email`、`$.nextResendAt`；捕获的命令 `clientIp()` 为 `127.0.0.1`。
- `should_render_limit_as_429_with_retry_after`：抛 `EmailResendLimitedException(Duration.ofMillis(36_500))` → 429、`Retry-After: 37`、`$.retryAfterSeconds` 37、`$.code` `IDN-0429`。
- `should_render_already_verified_as_409_with_trait`：`traits` 含 `CONFLICT` 与 `EMAIL_ALREADY_VERIFIED`，`detail` 为 `邮箱已经验证过了`。
- `should_render_pending_deletion_as_409`、`should_render_unauthorized_as_401`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*ResendEmailVerificationHandlerTest*"`
Expected: FAIL，编译错误

- [ ] **Step 3: 实现命令、结果、处理器、控制器、响应**

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*ResendEmailVerificationHandlerTest*" :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*MeEmailControllerIT*"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-app/src/main/java/dev/patra/identity/app/usecase/email patra-api/patra-identity/patra-identity-app/src/test/java/dev/patra/identity/app/usecase/email patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/me patra-api/patra-identity/patra-identity-adapter/src/integrationTest/java/dev/patra/identity/adapter/rest/me
git commit -m "feat(identity): 加重发验证邮件接口与限流 (PAP-96)"
```

---

### Task 12: 验证邮箱 `POST /auth/email/verify`

**Files:**
- Create: `…/app/usecase/email/VerifyEmailCommand.java`、`EmailVerified.java`、`VerifyEmailHandler.java`
- Create: `…/adapter/rest/auth/EmailVerificationController.java`、`…/rest/auth/request/VerifyEmailRequest.java`、`…/rest/auth/response/VerifyEmailResponse.java`
- Test: `…/app/src/test/java/dev/patra/identity/app/usecase/email/VerifyEmailHandlerTest.java`
- Test: `…/adapter/src/integrationTest/java/dev/patra/identity/adapter/rest/auth/EmailVerificationControllerIT.java`

**Interfaces:**
- Produces: `public record VerifyEmailCommand(String token) implements Command<EmailVerified>`，`of(token)`，`toString()` 为 `VerifyEmailCommand[token=***]`；`public record EmailVerified(String email, Instant emailVerifiedAt)`，`of(...)`。
- Produces: `@Component VerifyEmailHandler implements CommandHandler<VerifyEmailCommand, EmailVerified>`，依赖 `CurrentUserPort`、`EmailTokenRepository`、`UserRepository`、`SessionStorePort`、`UserLoginRecordRepository`、`Clock`；`@Transactional`；流程按 spec §8.3：空 → `InvalidUserFieldsException(List.of(UserFieldViolations.tokenRequired()))`；`parse` 失败 → `tokenInvalid()`；`tokens.findByTokenHash(hash)` 为空或 `purpose` 不是 `EMAIL_VERIFY` → `tokenInvalid()`；`users.findByIdForUpdate(token.userId)` 为空 → `tokenInvalid()`；`tokens.consume(hash, EMAIL_VERIFY, now)` 为空 → `tokenInvalid()`；邮箱不符 → `tokenInvalid()`；`markEmailVerified(now)` + `save`；`keep` 为当前用户且 `userId` 相同时 `revokeAllExcept(USER, userId, sessionId)`，否则 `revokeAll(USER, userId)`；被删会话的记录 `end(REVOKED, now)`。
- Produces: `@RestController @RequestMapping("/auth/email") EmailVerificationController`：`@PostMapping("/verify") VerifyEmailResponse verify(@RequestBody VerifyEmailRequest request)`；`record VerifyEmailRequest(String token)`；`record VerifyEmailResponse(String email, Instant emailVerifiedAt)`，`from(EmailVerified)`。

- [ ] **Step 1: 写失败的测试**

`VerifyEmailHandlerTest`（`SECRET = new EmailTokenSecret("D".repeat(43))`，令牌 `EmailToken.issue(EMAIL_VERIFY, 42L, EMAIL, SECRET, NOW − 1h, null)`，`findByTokenHash` 与 `consume` 的桩都按 `SECRET.hash()` 匹配）：

- `should_lock_user_before_consuming_token`：`InOrder(tokens, users)`：`findByTokenHash` → `users.findByIdForUpdate(42L)` → `tokens.consume(SECRET.hash(), EMAIL_VERIFY, NOW)`。
- `should_reject_token_of_other_purpose_without_locking_user`：`findByTokenHash` 给 `PASSWORD_RESET` 的令牌 → `token` / `INVALID`；`users`、`sessions` 无调用。
- `should_reject_when_consume_returns_empty_after_lock`：`findByTokenHash` 有值、`consume` 为空（被抢先消费）→ `token` / `INVALID`；`users.save` 未调。
- `should_verify_and_keep_current_session_but_revoke_others`：当前用户 `CurrentUser.of(42L, 7L, USER, WEB)`；`findByIdForUpdate(42L)` 给未验证用户；`revokeAllExcept(USER, 42L, 7L)` 返回 `List.of(5L, 6L)`；`records.findById(5L/6L)` 给未结束记录 → 结果 `email` 与 `emailVerifiedAt == NOW`；保存的用户 `isEmailVerified()`；两条记录 `getEndReason()` 为 `REVOKED`；`verify(sessions, never()).revokeAll(any(), anyLong())`。
- `should_revoke_all_sessions_when_anonymous`：`current()` 为空 → `revokeAll(USER, 42L)` 被调、`revokeAllExcept` 未调。
- `should_only_touch_sessions_of_token_owner_when_requester_differs`：当前用户 `CurrentUser.of(99L, 8L, …)` → `revokeAll(USER, 42L)`；对 99 的会话无任何调用。
- `should_reject_blank_token_without_querying`：`""` 与 `null` → 422 异常，`errors[0]` 为 `token` / `REQUIRED`；`verifyNoInteractions(tokens)`。
- `should_reject_malformed_token_without_querying`：`"short"` → `token` / `INVALID`；`verifyNoInteractions(tokens)`。
- `should_reject_unknown_token_without_locking_user`：`findByTokenHash` 为空 → `token` / `INVALID`；用户仓储未被调用。
- `should_reject_token_when_email_no_longer_matches`：用户邮箱 `other@example.com` → `token` / `INVALID`；`users.save` 未调；`sessions` 无调用。
- `should_be_idempotent_for_already_verified_user`：用户已验证（`emailVerifiedAt = NOW − 1 天`）→ 200 结果里 `emailVerifiedAt` 是原来的时间；仍撤销其他会话。

`EmailVerificationControllerIT`（`@WebMvcTest` + `@Import(EmailVerificationController.class)`）：`should_verify_with_200`（`$.email`、`$.emailVerifiedAt`；捕获的命令 `token()` 是请求体原文）；`should_render_invalid_token_as_422`（`errors[0].field` `token`、`code` `INVALID`、`$.code` `IDN-0422`）；`should_render_missing_token_as_422_required`；`should_reject_non_json_body_with_400`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*VerifyEmailHandlerTest*"`
Expected: FAIL，编译错误

- [ ] **Step 3: 实现**

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*VerifyEmailHandlerTest*" :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*EmailVerificationControllerIT*"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-app/src patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/auth patra-api/patra-identity/patra-identity-adapter/src/integrationTest/java/dev/patra/identity/adapter/rest/auth
git commit -m "feat(identity): 加验证邮箱接口：消费令牌、写验证时间、撤销其他会话 (PAP-96)"
```

---

### Task 13: 封禁作废令牌；过期令牌清理任务与 XXL-Job 接入

**Files:**
- Modify: `…/app/usecase/ban/BanUserHandler.java`、`…/app/src/test/…/ban/BanUserHandlerTest.java`
- Create: `…/app/usecase/email/PurgeExpiredEmailTokensCommand.java`、`PurgeExpiredEmailTokensHandler.java`；Test `PurgeExpiredEmailTokensHandlerTest.java`
- Modify: `patra-api/patra-identity/patra-identity-adapter/build.gradle.kts`（`api(libs.xxl.job)`）
- Create: `patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/patra/identity/adapter/scheduler/config/XxlJobConfig.java`（照 catalog 的，Javadoc 改成 identity）
- Create: `…/adapter/scheduler/job/EmailTokenCleanupScheduleJob.java`；Test `…/adapter/src/test/java/dev/patra/identity/adapter/scheduler/job/EmailTokenCleanupScheduleJobTest.java`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application.yml`（spec §13 的 `xxl.job.executor.*`）
- Modify: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/resources/application-test.yml` 与 `patra-api/patra-identity/patra-identity-adapter/src/integrationTest/resources/application.yml`（加 `xxl.job.enabled: false`）
**Interfaces:**
- 保留期不走 app 层的配置类（app 不依赖 boot）：`PurgeExpiredEmailTokensCommand(Duration retention)` 带着走，XXL 任务从 `@Value("${patra.identity.mail.token-retention:30d}") Duration retention` 读（Task 9 已把它绑进 `IdentityProperties.Mail`，这里只是同一个键）。
- Produces: `BanUserHandler` 新增依赖 `EmailTokenRepository tokens`，`users.save(user)` 之后 `tokens.revokeAllActive(userId, now)`。
- Produces: `public record PurgeExpiredEmailTokensCommand(Duration retention) implements Command<Integer>`，`of(retention)`；`@Component PurgeExpiredEmailTokensHandler`，依赖 `EmailTokenRepository`、`TransactionOperations`、`Clock`；`BATCH_SIZE = 1000`；循环 `transactions.execute(s -> tokens.deleteExpiredBefore(now.minus(retention), BATCH_SIZE))` 直到返回值 < 1000，返回总数。
- Produces: `@Component EmailTokenCleanupScheduleJob`，构造器 `(CommandBus commandBus, @Value("${patra.identity.mail.token-retention:30d}") Duration retention)`；`@XxlJob("emailTokenCleanupJob") public void execute()`：成功 `XxlJobHelper.handleSuccess("已删除过期令牌 " + n + " 行")`，异常 `log.error` + `handleFail`。

- [ ] **Step 1: 写失败的测试**

- `BanUserHandlerTest.should_revoke_all_email_tokens_when_banning`：`verify(tokens).revokeAllActive(42L, NOW)`，且在 `users.save` 之后。
- `PurgeExpiredEmailTokensHandlerTest.should_delete_in_batches_until_a_short_batch`：`deleteExpiredBefore(NOW.minus(30 天), 1000)` 依次返回 1000、1000、7 → 结果 2007、调用 3 次、`transactions.execute` 3 次；`should_stop_immediately_when_nothing_expired`：返回 0 → 结果 0、1 次。
- `EmailTokenCleanupScheduleJobTest`（照 `PubmedBaselineImportScheduleJobTest`，`@AfterEach` 清 `XxlJobContext`）：`should_purge_with_configured_retention`（捕获命令 `retention()` 为 30 天，`commandBus.handle` 返回 12 不抛）；`should_not_throw_when_command_fails`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*BanUserHandlerTest*" --tests "*PurgeExpiredEmailTokensHandlerTest*" :patra-api:patra-identity:patra-identity-adapter:test --tests "*EmailTokenCleanupScheduleJobTest*"`
Expected: FAIL，编译错误

- [ ] **Step 3: 实现**

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2，再跑 `./gradlew :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*PatraIdentityApplicationIT*"`（切片与启动不受 `XxlJobConfig` 影响）
Expected: PASS。写回 spec §16 第 9 条。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-app/src patra-api/patra-identity/patra-identity-adapter patra-api/patra-identity/patra-identity-boot/src/main/resources/application.yml patra-api/patra-identity/patra-identity-boot/src/integrationTest/resources/application-test.yml
git commit -m "feat(identity): 封禁时作废令牌，加过期令牌清理任务并接入 XXL-Job (PAP-96)"
```

---

### Task 14: 端到端 `EmailVerificationFlowIT`

**Files:**
- Create: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/java/dev/patra/identity/EmailVerificationFlowIT.java`

**Interfaces:**
- Consumes: 全部前序任务；注解组合与 `UsernameChangeFlowIT` 完全一致（`RANDOM_PORT`、PG + Redis + GreenMail 三个初始化器、`@ActiveProfiles("test")`、`@AutoConfigureRestTestClient`、`@Import(IdentityClockITSpringConfig.class)`），断言用 `assertionFor(userId, sessionId)`（测试时钟签）。辅助：`register(email, username)`、`login(email)`、`latestSessionIdOf(userId)`（照 `SessionFlowIT`）、`String tokenFromMail(MimeMessage)`（从文本部分正则 `#token=([A-Za-z0-9_-]{43})`）、`@BeforeEach GreenMailSmtpInitializer.purge()`、`clock.set(START)`。

- [ ] **Step 1: 写失败的测试**

- `should_send_verification_mail_on_register_with_links_from_configuration_not_host_header`：注册请求带 `Host: evil.example` 与 `X-Forwarded-Host: evil.example` → 201；`greenMail.waitForIncomingEmail(5000, 1)`；邮件 `To` 是注册邮箱，内容为 `MimeMultipart`，文本与 HTML 部分都含 `http://linqibins-mac-mini:4000/verify-email#token=`（默认 `base-url`），不含 `evil.example`；两部分的令牌相同；响应体与 `CapturedOutput` 不含令牌；`select token_hash from idn_email_token where user_id = ?` 等于令牌的 SHA-256；`select count(*) from idn_email_token where token_hash = ?` 用原文查为 0。
- `should_verify_keep_current_session_and_revoke_the_other_then_reject_reuse`：注册（会话 A）→ 登录（会话 B）→ 带 A 的断言 `POST /auth/email/verify {token}` → 200 且 `$.email` 正确；`/auth/me`（A）`emailVerified` 为真；Redis 里 B 的会话键消失、A 的还在；B 的登录记录 `end_reason` 为 `REVOKED`，A 的为空；再 POST 同一令牌 → 422 `errors[0].code` `INVALID`。
- `should_rate_limit_resend_by_cooldown_hour_and_day`：注册（T0，首封计入）后立刻 `POST /me/email/verification/resend` → 429、`Retry-After: 60`、`retryAfterSeconds` 60；`clock.set(T0 + 61s)` → 202、`$.nextResendAt` 为 T0 + 121s；旧令牌 422、GreenMail 第二封里的新令牌 200（验证成功后仍可继续测限流：已验证用户重发是 409，所以这一步改用另一个未验证的用户做窗口测试，或者把验证放到最后）；按 +122s、+183s、+244s 再重发三次都 202（这是第 5 次发送）；+305s → 429 且 `Retry-After` 为 3295；`clock.set(T0 + 3601s)`（T0 那次已出窗）→ 202，之后 +3662s、+3723s、+3784s、+3845s 各 202（第 7 ~ 10 次）；+3906s → 429（第 11 次，天窗口满，`Retry-After` 为 82494）；`clock.set(T0 + 24h + 1s)` → 202。
- `should_revoke_all_sessions_when_verified_anonymously_and_leave_requester_alone`：甲注册 + 登录两次（三个会话）；乙注册；不带断言（匿名）POST 甲的令牌 → 200；甲三个会话键都消失、记录全 `REVOKED`；乙的会话还在。
- `should_reject_resend_when_verified_or_pending_deletion`：已验证用户重发 → 409 `traits` 含 `EMAIL_ALREADY_VERIFIED`；SQL 改成 `PENDING_DELETION`（`deletion_due_at` 置值）后重发 → 409 含 `PENDING_DELETION`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*EmailVerificationFlowIT*"`
Expected: FAIL（接口已在，失败点应是某条断言——按失败修前序任务的缺陷，不在测试里绕）

- [ ] **Step 3: 修到全绿**

- [ ] **Step 4: 跑全部 identity 集成测试**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest :patra-api:patra-identity:patra-identity-session:integrationTest`
Expected: PASS。写回 spec §16 第 6、10、12 条。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-boot/src/integrationTest/java/dev/patra/identity/EmailVerificationFlowIT.java
git commit -m "test(identity): 加邮箱验证的端到端流程测试 (PAP-96)"
```

---

### Task 15: 网关公开 `/patra-identity/auth/email/verify`

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/security/GatewaySecurityConfiguration.java`（`IDENTITY_PUBLIC_PATHS` 加 `"/patra-identity/auth/email/verify"`，Javadoc 加一句）
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayAuthenticationIT.java`

- [ ] **Step 1: 写失败的测试**

- `should_forward_email_verify_anonymously_and_with_assertion_when_logged_in`：`identity.stubFor(post(urlPathEqualTo("/auth/email/verify")).willReturn(okJson("{}")))`；匿名 POST `/patra-identity/auth/email/verify` → 200，WireMock 收到且 `withoutHeader(AUTHORIZATION)`；带会话令牌再 POST → 200，收到的请求 `Authorization` 匹配 `Bearer .+` 且不等于会话令牌。
- `should_require_session_for_email_verification_resend`：匿名 POST `/patra-identity/me/email/verification/resend` → 401，WireMock 0 次；带会话 → 转发且带断言。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayAuthenticationIT*"`
Expected: FAIL（匿名 verify 得到 401）

- [ ] **Step 3: 加公开路径**

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-gateway-boot/src
git commit -m "feat(gateway): 公开邮箱验证接口的路径 (PAP-96)"
```

---

### Task 16: 基础设施与文档：Mailpit、`mail.env`、README、release spec、模块图、本机库重建与手工验收

**Files:**
- Modify: `patra-infra/docker/docker-compose.core.yaml`（spec §14.1 的 `mailpit` 服务）
- Modify: `patra-infra/docker/docker-compose.apps.yaml`（identity 的 `env_file` 加 `- path: ${HOME}/.patra/secrets/mail.env` / `required: false`，文件头注释加一行）
- Create: `patra-infra/docker/secrets/mail.env.example`（spec §14.2）
- Modify: `patra-infra/docker/README.md`（「密钥」表加 `mail.env`；「服务访问 URL」加 Mailpit SMTP 1025 / UI 8025；「首次部署」提一句 `mail.env` 可选）
- Modify: `patra-api/patra-identity/README.md`（spec §17 的六处）
- Modify: `docs/patra/release-specs/v0.12-user-center.md`（spec §19 的四处；这个文件是未跟踪的，本任务只改内容，不 `git add` 它——随后端 PR 由规划会话提交）
- Modify: `docs/patra/specs/2026-10-21-identity-email-verification-design.md`（§16 实测结果、状态行改「已实施」）
- Regenerate: `patra-infra/cd/module-graph.json`

- [ ] **Step 1: 改 compose、模板、README**

- [ ] **Step 2: 校验 compose**

Run: `docker compose -f patra-infra/docker/docker-compose.core.yaml config --quiet && docker compose -f patra-infra/docker/docker-compose.apps.yaml config --quiet`（在 `patra-infra/docker` 目录下有 `.env`，命令里用 `--project-directory patra-infra/docker`；`REDIS_PASSWORD` 等缺失只影响 core 的 env_file 存在性检查，本机已有 `~/.patra/secrets/redis.env`）
Expected: 两条都没有输出、退出码 0

- [ ] **Step 3: 重生成模块图**

Run: `./gradlew dumpModuleGraph && git diff --stat patra-infra/cd/module-graph.json`
Expected: 有改动（identity-infra、adapter、tool-test 的依赖变了）

- [ ] **Step 4: 本机库重建与手工验收**

先把 Nacos 的新键推到 mini（`NACOS_ADDR=http://100.103.73.27:8848 bash patra-infra/scripts/nacos-push.sh --check`，确认只多 `patra-identity.yaml` 的邮件键，再去掉 `--check` 推送；或者 IDE 里设 `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` 绕过）。本机 Docker PG（端口 15432）执行 identity README 第 8 节的两条 SQL 重建 `patra_identity`；按 README 启动 identity（IDE 或 `bootRun`，不设 `PATRA_MAIL_*`，设 `PATRA_PORTAL_BASE_URL=http://localhost:4000`）；`flyway_schema_history` 一行、六张表；经本地网关注册一个用户 → 在 mini 的 Mailpit（`http://linqibins-mac-mini:8025`，前提是 mini 已 `compose-all.sh up core` 起了 Mailpit；mini 还没拉到本分支时，本机临时 `docker run -d -p 1025:1025 -p 8025:8025 axllent/mailpit:v1.31.6` 并把 `PATRA_MAIL_HOST=127.0.0.1`）看到邮件，文本与 HTML 两部分都在；用 `curl` 带 `{ "token": "<片段里的值>" }` POST `/patra-identity/auth/email/verify` → 200；`/auth/me` 的 `emailVerified` 为 `true`；再 POST → 422；`POST /patra-identity/me/email/verification/resend` → 429 `Retry-After: 60`。把结果写进 spec §16。

- [ ] **Step 5: 提交**

```bash
git add patra-infra/docker/docker-compose.core.yaml patra-infra/docker/docker-compose.apps.yaml patra-infra/docker/secrets/mail.env.example patra-infra/docker/README.md patra-api/patra-identity/README.md docs/patra/specs/2026-10-21-identity-email-verification-design.md docs/patra/plans/2026-10-21-identity-email-verification.md patra-infra/cd/module-graph.json
git commit -m "docs(identity): 加 Mailpit 收集器、邮件密钥模板与邮箱验证文档，更新模块图 (PAP-96)"
```

---

### Task 17: 全量回归、Linear 与交接

- [ ] **Step 1: 格式化与全量检查**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: BUILD SUCCESSFUL（spotless 改了文件就 `git add -u` 后补一个 `style(identity): 格式化 (PAP-96)` 提交）

- [ ] **Step 2: 集成测试**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest :patra-api:patra-identity:patra-identity-session:integrationTest :patra-api:patra-gateway-boot:integrationTest :patra-tool:patra-tool-test:integrationTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 核对提交内容**

Run: `git log --oneline main..HEAD -- patra-api patra-infra patra-tool gradle docs | head -40 && git status --short`
Expected: 本 Issue 的提交都带 `(PAP-96)`；工作区只剩别的会话的未跟踪文件（release spec）

- [ ] **Step 4: Linear**

PAP-96 推到 `Review`，评论里按 spec §15.5 的表逐条贴证据（测试类名 + 命令输出的关键行、Mailpit 截图路径、mini 握手实测），列出待用户拍板与 mini 上要做的事（spec §14.4、§20）。

- [ ] **Step 5: 交接笔记**

在共享笔记目录写 `pap-96-email-verification-handover.md`：产物、提交范围、实测结果、待拍板、mini 步骤、交给 PAP-98 / 99 / 100 / 102 的约束（spec §18）。
