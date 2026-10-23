# identity 账号安全实施计划（PAP-98）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 identity 加再认证（会话 `auth_at` + `POST /auth/reauth` + 守卫）、改密码、找回密码、改邮箱（申请 / 查看 / 取消 / 确认 / 撤销）四组闭环，七封邮件，四种限流，网关公开两组路径。

**Architecture:** 会话模块加 `auth_at` 字段与「换令牌保留会话 ID」「按会话 ID 读」两段 Lua；domain 加三个策略、`EmailTokenIssuer`（四种用途共用的签发服务，替代 `EmailVerificationIssuer`）、`ReauthenticationGuard`、七个 `OutgoingMail` 记录；infra 把限流 Lua 参数化（冷却 + 任意个滑动窗口）给四个适配器共用；app 加八个处理器；adapter 加两个控制器与四个端点；「待确认的改邮箱」就是一条未过期的 `EMAIL_CHANGE_CONFIRM` 令牌，不加表列。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Data JPA + Flyway（`V1` 不改）、Redis 7 Lua、Thymeleaf 3.1.5、GreenMail 2.1.14、Testcontainers（PostgreSQL 17、Redis 7.0.15）、JUnit 5 + AssertJ + Mockito、Lombok。

**Spec:** `docs/patra/specs/2026-10-22-identity-account-security-design.md`

## Global Constraints

- 全部工作在主检出 `/Users/linqibin/Projects/Products/patra` 的分支 `feat/v0.12-user-center-api` 上，不建 worktree、不切分支、不推送、不开 PR、不动 main。Gradle 命令在仓库根目录执行（`./gradlew`，项目路径以 `:patra-api:` 开头；Bash 里 `cd` 到别处会被重置，一律用绝对路径或在根目录跑）。
- 再认证窗口 `10m`；会话 HASH 字段 `auth_at`（epoch 毫秒）；没有该字段的会话按 `Instant.EPOCH` 读（spec §6.1）。
- 限流（spec §10）：找回密码申请 同邮箱冷却 `60s`、同邮箱每天 5、同 IP 每小时 20；提交重设 同 IP 每分钟 10；改邮箱 同账号每天 3。键名：`idn:pw-reset:cooldown:email:{h}`、`idn:pw-reset:day:email:{h}`、`idn:pw-reset:hour:ip:{h}`、`idn:pw-reset-submit:minute:ip:{h}`、`idn:email-change:day:user:{userId}`，`h` = SHA-256 十六进制前 32 位；IP 为 `null` 时不带 IP 键。重发限流的键名与行为不变。
- 令牌：`PASSWORD_RESET` 1h、`EMAIL_CHANGE_CONFIRM` 1h（`email` = 新地址）、`EMAIL_CHANGE_REVERT` 7d（`email` = 要改回的旧地址，签发时不作废旧的，`revokeAllActive` 不动它）（spec §3、§7.2）。
- 错误契约（spec §12）：403 `ReauthenticationRequiredException`（`FORBIDDEN` + `REAUTH_REQUIRED`，detail「请重新输入密码以继续」）；401 `CurrentPasswordMismatchException`（`UNAUTHORIZED` + `PASSWORD_MISMATCH`，「密码不正确」）；409 `EmailChangePendingException`（`CONFLICT` + `EMAIL_CHANGE_PENDING`，「已有一个待确认的邮箱更改，请先取消或等它过期」）；429 `PasswordResetLimitedException`（「操作过于频繁，请稍后再试」）、`EmailChangeLimitedException`（「今天更改邮箱的次数已用完，请明天再试」），都 `QUOTA_EXCEEDED` + `HasRetryAfter`；字段错误 422 `errors[]` 的字段名是请求体字段名 `currentPassword` / `newPassword` / `newEmail` / `token` / `email`；一个异常只放一个标准特征。
- 响应（spec §8）：`POST /auth/reauth`、`PUT /me/password` 回 200 `{ sessionToken, user }`；`POST /auth/password/forgot` 回 202 `{ email, nextRequestAt }`；`POST /auth/password/reset` 回 200 `{ email }`；`POST /me/email/change` 回 202 `{ newEmail, expiresAt }`；`GET /me/email/change` 回 200 `{ newEmail, expiresAt }` 或 204；`DELETE /me/email/change` 回 204；`POST /auth/email/change/confirm` 回 200 `{ email, emailVerifiedAt }`；`POST /auth/email/change/revert` 回 200 `{ email }`。
- 邮件（spec §9）：七个模板 `password-reset`、`password-changed`、`password-reset-done`、`email-change-confirm`、`email-changed-notice`、`email-changed-confirmation`、`email-change-reverted`，主题按 §9.1 原文；安全通知的 `device` / `ipAddress` / `ipLocation` 传 `null`；`security-notice` 片段不再输出「建议立即到安全设置……」，这句挪进 `password-changed`、`email-changed-notice`、`email-changed-confirmation` 的正文；链接 `{base-url}{路径}#token=…`，路径默认 `/reset-password`、`/confirm-email-change`、`/revert-email-change`。
- 配置键按 spec §13 原样进 jar 的 `application.yml`，Nacos 不加键；`JarConfigOverlapCheckerTest` 守两处不重复。
- 代码规范：Google Java 格式（spotless）、`///` Javadoc 且每行 ≤ 100 列、不用全类名、record ≤ 4 个参数用 `of()`、≥ 5 个用 `@Builder`，集合 `List.of` / `List.copyOf`；domain 不引框架；`toString()` 不输出密码、令牌、链接、收件人。
- 测试规范：单测 `src/test`，集成测试 `src/integrationTest`，方法名 `should_…`；`./gradlew check` 不含 `integrationTest`，两个都跑；跑 `check` 前先 `./gradlew spotlessApply`；Testcontainers 要本机 Docker（OrbStack）在线；`build.gradle.kts` 不改，模块图不变。
- Mockito 不嵌套 stub（先放局部变量）；`TransactionOperations` 的桩照 `ResendEmailVerificationHandlerTest.recording(AtomicBoolean)` 的写法（同步执行回调、记录是否在事务内）。
- 端到端测试用裸 JDK `HttpClient`（`RestTestClient` 会按 `Retry-After` 自动重试 429）；断言用 `new IdentityAssertionSigner(TestSigningKey.key(), clock)` 签；新的 `@SpringBootTest` 复用 `EmailVerificationFlowIT` 的注解组合（同一组 initializers / profile / `@Import(IdentityClockITSpringConfig.class)`）让 Spring 复用上下文。
- 提交：每个任务至少一次提交，格式 `type(scope): 中文 subject (PAP-98)`，subject 用中文动词起头，body 每行 ≤ 100 字符，结尾 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`；只 `git add` 本任务的路径，提交前看 `git diff --cached --stat`（工作区有规划会话的未跟踪文件 `docs/patra/release-specs/v0.12-user-center.md`，不要带上）。
- 写 Entity / Dao / RepositoryAdapter 的任务开工前加载 `patra-backend:patra-jpa`；写 Controller / Handler 的任务开工前加载 `patra-backend:patra-hexagonal`。

## Review Focus

1. 换令牌后旧令牌必须立刻失效，不能有两个令牌同时指向一条会话——Task 1 的 `should_rotate_token_keeping_session_id_and_invalidate_old_token`。
2. 改密码过程中 Redis 失败：密码必须没改（事务回滚），用户重试不会被自己的新密码挡住——Task 11 的 `should_roll_back_password_when_session_rotation_fails`。
3. 重设密码时密码不合规：令牌不能被消费，用户换个密码还能用同一个链接——Task 12 的 `should_not_consume_token_when_new_password_is_invalid`。
4. 旧邮箱的撤销链接必须活过改密码、重设密码、第二次改邮箱——Task 16 `EmailChangeFlowIT` 的 `should_keep_revert_link_alive_through_password_change_and_second_change`。
5. 找回密码的申请对未注册邮箱绝不能回 429 以外的不同信号：限流在查用户之前计数——Task 12 的 `should_count_throttle_before_looking_up_user`。

---

### Task 1: 会话模块：`auth_at`、`rotate`、`find`

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-session/src/main/resources/redis/session-create.lua`
- Create: `patra-api/patra-identity/patra-identity-session/src/main/resources/redis/session-rotate.lua`
- Create: `patra-api/patra-identity/patra-identity-session/src/main/resources/redis/session-find.lua`
- Modify: `patra-api/patra-identity/patra-identity-session/src/main/java/dev/patra/identity/session/StoredSession.java`
- Modify: `patra-api/patra-identity/patra-identity-session/src/main/java/dev/patra/identity/session/RedisSessionStore.java`
- Modify: `patra-api/patra-identity/patra-identity-session/README.md`
- Test: `patra-api/patra-identity/patra-identity-session/src/test/java/dev/patra/identity/session/StoredSessionTest.java`
- Test: `patra-api/patra-identity/patra-identity-session/src/integrationTest/java/dev/patra/identity/session/RedisSessionStoreIT.java`

**Interfaces:**
- Produces: `StoredSession` 加字段 `Instant authenticatedAt`（`@Builder`，非空）。
- Produces: `Optional<SessionToken> RedisSessionStore.rotate(AccountType accountType, long userId, long sessionId)`：换令牌保留会话 ID，`auth_at = last_active_at = now`，其余字段不变，旧键删除、索引指向新哈希；会话不在或已过期为空（并清索引条目）。
- Produces: `Optional<StoredSession> RedisSessionStore.find(AccountType accountType, long userId, long sessionId)`：按索引读，不续期；不在或过期为空。
- Produces: `session-create.lua` 写 `auth_at = ARGV[8]`（= `created_at`）；`toStoredSession` 对缺失的 `auth_at` 用 `Instant.EPOCH`。

- [ ] **Step 1: 写失败的测试**

`StoredSessionTest` 加：

```java
@Test
void should_require_authenticated_at() {
  assertThatThrownBy(() -> StoredSession.builder()./* 其余字段 */.authenticatedAt(null).build())
      .isInstanceOf(NullPointerException.class);
}
```

`RedisSessionStoreIT` 加（沿用 `newSession(userId, sessionId)` 辅助与 `clock`）：

```java
@Test
void should_write_auth_at_equal_to_created_at_on_create() {
  SessionToken token = store.create(newSession(61L, 8101L).build()).token();
  assertThat(store.findAndTouch(token).orElseThrow().authenticatedAt()).isEqualTo(START);
}

@Test
void should_rotate_token_keeping_session_id_and_invalidate_old_token() {
  SessionToken old = store.create(newSession(62L, 8201L).deviceId("dev-1").build()).token();
  clock.advance(Duration.ofMinutes(3));
  SessionToken fresh = store.rotate(AccountType.USER, 62L, 8201L).orElseThrow();
  assertThat(fresh).isNotEqualTo(old);
  assertThat(store.findAndTouch(old)).isEmpty();
  StoredSession session = store.findAndTouch(fresh).orElseThrow();
  assertThat(session.sessionId()).isEqualTo(8201L);
  assertThat(session.createdAt()).isEqualTo(START);
  assertThat(session.expiresAt()).isEqualTo(START.plus(ABSOLUTE));
  assertThat(session.deviceId()).isEqualTo("dev-1");
  assertThat(session.authenticatedAt()).isEqualTo(START.plus(Duration.ofMinutes(3)));
  assertThat(redis.opsForHash().get(RedisSessionStore.indexKey(AccountType.USER, 62L), "8201"))
      .isEqualTo(fresh.hash());
  assertThat(redis.getExpire(RedisSessionStore.sessionKey(AccountType.USER, fresh.hash()), TimeUnit.MILLISECONDS))
      .isBetween(IDLE.toMillis() - TOLERANCE_MILLIS, IDLE.toMillis());
}

@Test
void should_return_empty_on_rotate_when_session_is_gone_or_expired() {
  assertThat(store.rotate(AccountType.USER, 63L, 8301L)).isEmpty();
  store.create(newSession(63L, 8302L).build());
  clock.advance(ABSOLUTE.plusSeconds(1));
  assertThat(store.rotate(AccountType.USER, 63L, 8302L)).isEmpty();
  assertThat(redis.opsForHash().hasKey(RedisSessionStore.indexKey(AccountType.USER, 63L), "8302")).isFalse();
}

@Test
void should_find_by_session_id_without_renewing() {
  SessionToken token = store.create(newSession(64L, 8401L).build()).token();
  clock.advance(Duration.ofMinutes(5));
  StoredSession found = store.find(AccountType.USER, 64L, 8401L).orElseThrow();
  assertThat(found.lastActiveAt()).isEqualTo(START);
  assertThat(found.authenticatedAt()).isEqualTo(START);
  assertThat(store.find(AccountType.USER, 64L, 8499L)).isEmpty();
}

@Test
void should_read_missing_auth_at_as_epoch() {
  SessionToken token = store.create(newSession(65L, 8501L).build()).token();
  redis.opsForHash().delete(RedisSessionStore.sessionKey(AccountType.USER, token.hash()), "auth_at");
  assertThat(store.find(AccountType.USER, 65L, 8501L).orElseThrow().authenticatedAt()).isEqualTo(Instant.EPOCH);
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:test :patra-api:patra-identity:patra-identity-session:integrationTest --tests "*StoredSessionTest*" --tests "*RedisSessionStoreIT*"`
Expected: 编译失败（`authenticatedAt` / `rotate` / `find` 不存在）。

- [ ] **Step 3: 实现**

`session-create.lua` 第 3 步的 `fields` 里加 `'auth_at', ARGV[8]`。

`session-rotate.lua`（`KEYS[1]` 索引、`KEYS[2]` 新会话键；`ARGV[1]` 前缀、`ARGV[2]` 会话 ID、`ARGV[3]` 新哈希、`ARGV[4]` now）：按 spec §6.2 的伪码；返回 `HGETALL` 形式的扁平数组（含更新后的 `auth_at`、`last_active_at`），没有结果 `return {}`。

`session-find.lua`（`KEYS[1]` 索引；`ARGV[1]` 前缀、`ARGV[2]` 会话 ID、`ARGV[3]` now）：按 spec §6.3。

`RedisSessionStore`：两个 `RedisScript<List>` 常量 `ROTATE_SCRIPT`、`FIND_SCRIPT`；`rotate` 先 `SessionToken.generate(accountType, random)`，把 `token.hash()` 作为 `ARGV[3]`，脚本返回空表时 `Optional.empty()`；`toStoredSession` 读 `auth_at`，缺失用 `Instant.EPOCH`。`StoredSession` 加 `authenticatedAt`（`Objects.requireNonNull`）。README：会话字段表加 `auth_at`，操作表加 `rotate`、`find`。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2，再加 `:patra-identity:patra-identity-infra:test --tests "*SessionStoreAdapterTest*"`（它构造 `StoredSession` 的话要补字段）。
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-session
git commit -m "feat(identity): 会话加 auth_at，加换令牌保留会话 ID 与按会话 ID 读两段脚本 (PAP-98)"
```

---

### Task 2: 领域基础：聚合方法、策略、链接、错误特征、异常、字段错误

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/aggregate/User.java`
- Modify: `.../domain/model/aggregate/UserPasswordCredential.java`
- Modify: `.../domain/model/vo/EmailAddress.java`、`.../domain/model/vo/PlainPassword.java`、`.../domain/model/vo/UserFieldViolations.java`
- Modify: `.../domain/policy/PasswordPolicy.java`、`.../domain/policy/MailLinkPolicy.java`
- Create: `.../domain/policy/ReauthenticationPolicy.java`、`PasswordResetPolicy.java`、`EmailChangePolicy.java`
- Modify: `.../domain/exception/IdentityErrorTrait.java`
- Create: `.../domain/exception/ReauthenticationRequiredException.java`、`CurrentPasswordMismatchException.java`、`EmailChangePendingException.java`、`PasswordResetLimitedException.java`、`EmailChangeLimitedException.java`
- Modify（`MailLinkPolicy` 改成 `@Builder` 的连带）: `.../domain/service/EmailVerificationIssuerTest.java`、`patra-identity-infra/src/test/.../mail/ThymeleafMailRendererTest.java`、`patra-identity-boot/src/main/.../config/IdentityConfiguration.java`、`patra-identity-domain/src/test/.../policy/MailLinkPolicyTest.java`
- Test: 对应的 `UserTest`、`UserPasswordCredentialTest`、`EmailAddressTest`、`PlainPasswordTest`、`PasswordPolicyTest`、`MailLinkPolicyTest`、新建 `ReauthenticationPolicyTest`、`PasswordResetPolicyTest`、`EmailChangePolicyTest`、`DomainExceptionTraitsTest`

**Interfaces:**
- Produces: `void User.changeEmail(EmailAddress next, Instant now)`：`DELETED` 抛 `InvalidUserStateTransitionException`；否则 `email = next`、`emailVerifiedAt = now`。
- Produces: `void UserPasswordCredential.rotate(PasswordHash next)`。
- Produces: `String EmailAddress.masked()`：本地部分首字符 + `***@` + 域名（`c***@example.com`）。
- Produces: `Optional<FieldViolation> EmailAddress.validate(String raw, String field)`；原 `validate(raw)` 委托 `field = UserFieldViolations.EMAIL`。`PlainPassword.validate(String raw, String field)` 同理；`PasswordPolicy.validate(String raw, String field)` 替代 `validateForRegistration(raw)`（`RegisterUserHandler` 改调 `validate(raw, UserFieldViolations.PASSWORD)`）。
- Produces: `UserFieldViolations` 常量 `CURRENT_PASSWORD = "currentPassword"`、`NEW_PASSWORD = "newPassword"`、`NEW_EMAIL = "newEmail"`；现有 `emailRequired()` / `emailTooLong()` / `emailInvalidFormat()` / `passwordRequired()` / `passwordInvalidCharacter()` / `passwordTooShort()` / `passwordTooLong()` / `passwordTooCommon()` 各加一个带 `String field` 参数的重载；新增 `newPasswordUnchanged()`（`newPassword` / `UNCHANGED`，「新密码不能与当前密码相同」）、`newEmailUnchanged()`（`newEmail` / `UNCHANGED`，「新邮箱与当前邮箱相同」）。
- Produces: `record ReauthenticationPolicy(Duration window)`：`of(window)`，非正抛 `IllegalArgumentException`；`boolean isRecent(Instant authenticatedAt, Instant now)` = `now.isBefore(authenticatedAt.plus(window))`。
- Produces: `record PasswordResetPolicy(Duration cooldown, int perEmailDaily, int perIpHourly, int submitPerIpMinutely)`：`of(...)`；常量 `HOUR_WINDOW` = 1h、`DAY_WINDOW` = 24h、`MINUTE_WINDOW` = 1m；`Instant nextRequestAt(Instant now)` = `now + cooldown`。
- Produces: `record EmailChangePolicy(int perAccountDaily)`：`of(int)`；常量 `DAY_WINDOW` = 24h。
- Produces: `MailLinkPolicy` 改为 `@Builder` 的 6 字段记录（`baseUrl`、`verifyEmailPath`、`securitySettingsPath`、`resetPasswordPath`、`confirmEmailChangePath`、`revertEmailChangePath`），删 `of()`；加 `resetPasswordUrl(EmailTokenSecret)`、`confirmEmailChangeUrl(EmailTokenSecret)`、`revertEmailChangeUrl(EmailTokenSecret)`。
- Produces: `IdentityErrorTrait` 加 `REAUTH_REQUIRED`、`EMAIL_CHANGE_PENDING`、`PASSWORD_MISMATCH`；五个异常按 Global Constraints 的特征与 detail，两个 429 的实现 `HasRetryAfter`（照 `EmailResendLimitedException`）。

- [ ] **Step 1: 写失败的测试**

```java
// UserTest
@Test
void should_change_email_and_mark_verified_now() {
  User user = activeUser("old@example.com");
  user.changeEmail(EmailAddress.of("new@example.com"), NOW);
  assertThat(user.getEmail()).contains(EmailAddress.of("new@example.com"));
  assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
}
@Test
void should_reject_change_email_when_deleted() { /* DELETED 用户 → InvalidUserStateTransitionException */ }

// UserPasswordCredentialTest
@Test
void should_rotate_hash() { credential.rotate(NEW_HASH); assertThat(credential.getPasswordHash()).isEqualTo(NEW_HASH); }

// EmailAddressTest
@Test
void should_mask_local_part_keeping_first_character() {
  assertThat(EmailAddress.of("chen.yu@example.com").masked()).isEqualTo("c***@example.com");
}
@Test
void should_report_violation_under_given_field() {
  assertThat(EmailAddress.validate("", "newEmail").orElseThrow().field()).isEqualTo("newEmail");
}

// PasswordPolicyTest：validate("short", "newPassword") 的 field 是 newPassword、code 是 TOO_SHORT

// ReauthenticationPolicyTest
@Test
void should_treat_window_end_as_not_recent() {
  ReauthenticationPolicy policy = ReauthenticationPolicy.of(Duration.ofMinutes(10));
  assertThat(policy.isRecent(NOW, NOW.plus(Duration.ofMinutes(10)).minusMillis(1))).isTrue();
  assertThat(policy.isRecent(NOW, NOW.plus(Duration.ofMinutes(10)))).isFalse();
  assertThat(policy.isRecent(Instant.EPOCH, NOW)).isFalse();
}
@Test
void should_reject_non_positive_window() { /* ofMinutes(0) → IllegalArgumentException */ }

// PasswordResetPolicyTest / EmailChangePolicyTest：非正数抛；nextRequestAt = now + cooldown

// MailLinkPolicyTest
@Test
void should_build_three_new_links_with_token_in_fragment() {
  MailLinkPolicy links = MailLinkPolicy.builder().baseUrl("https://patra.example/").verifyEmailPath("/verify-email")
      .securitySettingsPath("/settings/security").resetPasswordPath("/reset-password")
      .confirmEmailChangePath("/confirm-email-change").revertEmailChangePath("/revert-email-change").build();
  EmailTokenSecret secret = new EmailTokenSecret("A".repeat(43));
  assertThat(links.resetPasswordUrl(secret)).isEqualTo("https://patra.example/reset-password#token=" + secret.value());
  assertThat(links.confirmEmailChangeUrl(secret)).isEqualTo("https://patra.example/confirm-email-change#token=" + secret.value());
  assertThat(links.revertEmailChangeUrl(secret)).isEqualTo("https://patra.example/revert-email-change#token=" + secret.value());
}

// DomainExceptionTraitsTest 加：
// ReauthenticationRequiredException 的 traits 含 FORBIDDEN 与 REAUTH_REQUIRED；
// CurrentPasswordMismatchException 含 UNAUTHORIZED 与 PASSWORD_MISMATCH；
// EmailChangePendingException 含 CONFLICT 与 EMAIL_CHANGE_PENDING；
// PasswordResetLimitedException(Duration.ofSeconds(7)).getRetryAfter() 是 7 秒且含 QUOTA_EXCEEDED；EmailChangeLimitedException 同
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test`
Expected: 编译失败。

- [ ] **Step 3: 实现 Interfaces 里的全部签名**

`MailLinkPolicy` 的紧凑构造器对六个字段做同样的校验（`normalizeBaseUrl`、`requirePath`）。`EmailAddress.masked()`：`value` 按 `@` 切，本地部分取 `codePointAt(0)`。改 `MailLinkPolicy.of(...)` 的四个调用点为 builder。`RegisterUserHandler`、`LoginUserHandler` 改调新签名。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test :patra-api:patra-identity:patra-identity-app:test :patra-api:patra-identity:patra-identity-infra:test :patra-api:patra-identity:patra-identity-boot:test`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity
git commit -m "feat(identity): 加再认证、找回密码、改邮箱的策略、链接、错误特征与异常 (PAP-98)"
```

---

### Task 3: 持久层：凭据删除、`findActive`

**Files:**
- Modify: `.../domain/port/repository/UserPasswordCredentialRepository.java`、`EmailTokenRepository.java`
- Modify: `patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/persistence/dao/UserPasswordCredentialDao.java`、`dao/EmailTokenDao.java`
- Modify: `.../infra/adapter/persistence/UserPasswordCredentialRepositoryAdapter.java`、`EmailTokenRepositoryAdapter.java`
- Test: `patra-identity-infra/src/integrationTest/.../persistence/UserPasswordCredentialRepositoryAdapterIT.java`、`EmailTokenRepositoryAdapterIT.java`

**Interfaces:**
- Produces: `boolean UserPasswordCredentialRepository.deleteByUserId(long userId)`；DAO `@Modifying @Query("delete from UserPasswordCredentialEntity c where c.userId = :userId") int deleteByUserId(@Param("userId") Long userId)`，适配器返回 `> 0` 并 `flush`。
- Produces: `Optional<EmailToken> EmailTokenRepository.findActive(long userId, EmailTokenPurpose purpose, Instant now)`；DAO `@Query("select t from EmailTokenEntity t where t.userId = :userId and t.purpose = :purpose and t.consumedAt is null and t.revokedAt is null and t.expiresAt > :now") Optional<EmailTokenEntity> findActive(...)`。

- [ ] **Step 1: 写失败的测试**

```java
// UserPasswordCredentialRepositoryAdapterIT
@Test
void should_delete_credential_by_user_id_and_report_whether_a_row_existed() {
  /* 建用户与凭据 */
  assertThat(adapter.deleteByUserId(userId)).isTrue();
  assertThat(adapter.findByUserId(userId)).isEmpty();
  assertThat(adapter.deleteByUserId(userId)).isFalse();
}

// EmailTokenRepositoryAdapterIT
@Test
void should_find_active_token_only_when_unused_unrevoked_and_unexpired() {
  /* 同一用户：一条已消费、一条已作废、一条已过期（expiresAt = NOW - 1s）、一条有效 EMAIL_CHANGE_CONFIRM */
  assertThat(adapter.findActive(userId, EMAIL_CHANGE_CONFIRM, NOW)).map(EmailToken::getId).contains(activeId);
  assertThat(adapter.findActive(userId, EMAIL_CHANGE_CONFIRM, NOW.plus(Duration.ofHours(2)))).isEmpty();
  assertThat(adapter.findActive(userId, PASSWORD_RESET, NOW)).isEmpty();
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*UserPasswordCredentialRepositoryAdapterIT*" --tests "*EmailTokenRepositoryAdapterIT*"`
Expected: 编译失败。

- [ ] **Step 3: 实现** 两个端口方法、两个 DAO 查询、两个适配器方法。

- [ ] **Step 4: 跑测试确认通过**（同 Step 2）

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain patra-api/patra-identity/patra-identity-infra
git commit -m "feat(identity): 凭据仓储加按用户删除，令牌仓储加查有效令牌 (PAP-98)"
```

---

### Task 4: Redis 侧端口：会话 `rotate` / `findAuthenticatedAt`、登录限制 `clear`

**Files:**
- Modify: `.../domain/port/session/SessionStorePort.java`、`.../domain/port/throttle/LoginThrottlePort.java`
- Modify: `patra-identity-infra/src/main/.../adapter/session/SessionStoreAdapter.java`、`.../adapter/throttle/LoginThrottleAdapter.java`
- Test: `patra-identity-infra/src/test/.../session/SessionStoreAdapterTest.java`、`patra-identity-infra/src/integrationTest/.../throttle/LoginThrottleAdapterIT.java`

**Interfaces:**
- Produces: `Optional<String> SessionStorePort.rotate(AccountType accountType, long userId, long sessionId)`（新令牌原文）；`Optional<Instant> SessionStorePort.findAuthenticatedAt(AccountType accountType, long userId, long sessionId)`（会话不在为空）。适配器分别转 `RedisSessionStore.rotate(...).map(SessionToken::value)`、`find(...).map(StoredSession::authenticatedAt)`。
- Produces: `void LoginThrottlePort.clear(AccountType accountType, EmailAddress email)`：删失败计数键与锁键（`keys(...)` 的第 1、3 个），不动在途登记；Redis 暂时失败转 `TemporarilyUnavailableException`。

- [ ] **Step 1: 写失败的测试**

```java
// SessionStoreAdapterTest（mock RedisSessionStore）
@Test
void should_rotate_and_return_token_value() { /* store.rotate 返回 Optional.of(token) → adapter.rotate 返回 token.value() */ }
@Test
void should_find_authenticated_at_from_stored_session() { /* store.find 返回 authenticatedAt = NOW 的会话 → Optional.of(NOW)；空 → 空 */ }

// LoginThrottleAdapterIT
@Test
void should_clear_failures_and_lock_but_keep_in_flight_entries() {
  /* 连失败 5 次上锁 */
  adapter.clear(AccountType.USER, email);
  assertThat(adapter.begin(AccountType.USER, email)).isNotNull();   // 不再 429
  assertThat(redis.hasKey(LoginThrottleAdapter.keys(AccountType.USER, email).get(0))).isFalse();
  assertThat(redis.hasKey(LoginThrottleAdapter.keys(AccountType.USER, email).get(2))).isFalse();
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests "*SessionStoreAdapterTest*" :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*LoginThrottleAdapterIT*"`

- [ ] **Step 3: 实现**（`clear` 用 `redis.delete(List.of(...))` 包在 `execute` 里）

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain patra-api/patra-identity/patra-identity-infra
git commit -m "feat(identity): 会话端口加换令牌与读再认证时间，登录限制加清零 (PAP-98)"
```

---

### Task 5: `EmailTokenIssuer` 替代 `EmailVerificationIssuer`

**Files:**
- Create: `.../domain/service/EmailTokenIssuer.java`、`.../domain/service/IssuedEmailToken.java`
- Delete: `.../domain/service/EmailVerificationIssuer.java`、`PreparedVerificationMail.java` 及 `EmailVerificationIssuerTest.java`
- Modify: `patra-identity-app/src/main/.../register/RegisterUserHandler.java`、`.../email/ResendEmailVerificationHandler.java` 及二者的单测
- Modify: `patra-identity-boot/src/main/.../config/IdentityConfiguration.java`
- Test: `.../domain/service/EmailTokenIssuerTest.java`

**Interfaces:**
- Produces: `EmailTokenIssuer(EmailTokenRepository tokens, Clock clock, SecureRandom random)`；`IssuedEmailToken issue(EmailTokenPurpose purpose, long userId, EmailAddress email, String requestedIp)`：`purpose != EMAIL_CHANGE_REVERT` 时先 `tokens.revokeActive(userId, purpose, now)`；`tokens.save(EmailToken.issue(purpose, userId, email, secret, now, requestedIp))`。
- Produces: `record IssuedEmailToken(EmailToken token, EmailTokenSecret secret)`，`of()`，`toString()` 不含原文。
- Consumes（注册 / 重发处理器改法）：`IssuedEmailToken issued = emailTokenIssuer.issue(EMAIL_VERIFY, userId, email, clientIp)`；邮件 `EmailVerificationMail.of(email, displayName, mailLinkPolicy.verifyEmailUrl(issued.secret()), issued.token().getExpiresAt())`——两个处理器因此多注入 `MailLinkPolicy`（构造器参数位置：替换原 `EmailVerificationIssuer` 的位置，紧接着加 `MailLinkPolicy links`）。
- Boot：`@Bean EmailTokenIssuer emailTokenIssuer(EmailTokenRepository tokens, Clock clock)` 替代 `emailVerificationIssuer`。

- [ ] **Step 1: 写失败的测试**

```java
// EmailTokenIssuerTest
@Test
void should_revoke_same_purpose_then_save_hashed_token() {
  when(tokens.save(any())).thenAnswer(inv -> inv.getArgument(0));
  IssuedEmailToken issued = issuer.issue(EmailTokenPurpose.PASSWORD_RESET, 42L, EMAIL, "203.0.113.9");
  InOrder order = inOrder(tokens);
  order.verify(tokens).revokeActive(42L, EmailTokenPurpose.PASSWORD_RESET, NOW);
  order.verify(tokens).save(any());
  assertThat(issued.token().getTokenHash()).isEqualTo(issued.secret().hash());
  assertThat(issued.token().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
  assertThat(issued.toString()).doesNotContain(issued.secret().value());
}
@Test
void should_not_revoke_existing_revert_tokens() {
  issuer.issue(EmailTokenPurpose.EMAIL_CHANGE_REVERT, 42L, EMAIL, null);
  verify(tokens, never()).revokeActive(anyLong(), any(), any());
}
```

`RegisterUserHandlerTest` / `ResendEmailVerificationHandlerTest`：把 `EmailVerificationIssuer` 的桩换成 `EmailTokenIssuer`（返回 `IssuedEmailToken.of(token, SECRET)`）+ `MailLinkPolicy`，断言发出的 `EmailVerificationMail.verifyUrl()` 以 `/verify-email#token=` + `SECRET.value()` 结尾。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test :patra-api:patra-identity:patra-identity-app:test`

- [ ] **Step 3: 实现并删除旧服务**

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test :patra-api:patra-identity:patra-identity-app:test :patra-api:patra-identity:patra-identity-boot:test`

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity
git commit -m "refactor(identity): 令牌签发泛化为 EmailTokenIssuer，四种用途共用 (PAP-98)"
```

---

### Task 6: `ReauthenticationGuard`

**Files:**
- Create: `.../domain/service/ReauthenticationGuard.java`
- Test: `.../domain/service/ReauthenticationGuardTest.java`

**Interfaces:**
- Produces: `ReauthenticationGuard(SessionStorePort sessions, ReauthenticationPolicy policy, Clock clock)`；`void requireRecent(CurrentUser current)`：`sessions.findAuthenticatedAt(current.accountType(), current.userId(), current.sessionId())` 空 → `AuthenticationRequiredException`；`!policy.isRecent(at, now)` → `ReauthenticationRequiredException`。

- [ ] **Step 1: 写失败的测试**

```java
@Test
void should_pass_within_window() { when(sessions.findAuthenticatedAt(USER, 42L, 7001L)).thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(9)))); assertThatCode(() -> guard.requireRecent(CURRENT)).doesNotThrowAnyException(); }
@Test
void should_require_reauth_after_window() { /* NOW - 10m → ReauthenticationRequiredException，traits 含 REAUTH_REQUIRED */ }
@Test
void should_treat_epoch_as_never_authenticated() { /* Instant.EPOCH → ReauthenticationRequiredException */ }
@Test
void should_require_login_when_session_is_gone() { /* Optional.empty() → AuthenticationRequiredException */ }
```

- [ ] **Step 2: 跑测试确认失败** Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*ReauthenticationGuardTest*"`

- [ ] **Step 3: 实现**

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain
git commit -m "feat(identity): 加再认证守卫，超窗回 403 REAUTH_REQUIRED (PAP-98)"
```

---

### Task 7: 通用限流脚本与三个适配器

**Files:**
- Create: `.../domain/port/throttle/PasswordResetThrottlePort.java`、`EmailChangeThrottlePort.java`
- Create: `patra-identity-infra/src/main/resources/redis/action-throttle.lua`；Delete: `redis/email-resend-throttle.lua`
- Create: `patra-identity-infra/src/main/.../adapter/throttle/ActionThrottleScript.java`、`PasswordResetThrottleAdapter.java`、`EmailChangeThrottleAdapter.java`
- Modify: `.../adapter/throttle/EmailResendThrottleAdapter.java`
- Test: `patra-identity-infra/src/test/.../throttle/ActionThrottleScriptTest.java`、`PasswordResetThrottleAdapterTest.java`、`EmailChangeThrottleAdapterTest.java`；`src/integrationTest/.../throttle/PasswordResetThrottleAdapterIT.java`、`EmailChangeThrottleAdapterIT.java`；现有 `EmailResendThrottleAdapterTest` / `EmailResendThrottleAdapterIT` 不改。

**Interfaces:**
- Produces: `PasswordResetThrottlePort`：`Optional<Duration> acquireRequest(EmailAddress email, String clientIp, Instant now)`；`Optional<Duration> acquireSubmit(String clientIp, Instant now)`。`EmailChangeThrottlePort`：`Optional<Duration> acquire(long userId, Instant now)`。
- Produces: `action-throttle.lua`：`KEYS[1]` 冷却键（`ARGV[2]` 为 `0` 时不读不写），`KEYS[2..]` 窗口键；`ARGV[1]` now 毫秒、`ARGV[2]` 冷却毫秒、`ARGV[3]` `1` 判且计 / `0` 只计、`ARGV[4]` 成员后缀、之后每个窗口两项（窗口毫秒、上限）；返回放行 `0` 或要等的毫秒（各维度最长）。
- Produces: `final class ActionThrottleScript`：`ActionThrottleScript(StringRedisTemplate redis)`；`record Window(String key, Duration length, int limit)`；`long run(String cooldownKey, Duration cooldown, List<Window> windows, Instant now, boolean enforce)`（`cooldown` 为 `Duration.ZERO` 时 `cooldownKey` 传 `"-"`；暂时失败转 `TemporarilyUnavailableException`；脚本返回 `null` 抛 `TemporarilyUnavailableException`）；`static String keyHash(String value)`（SHA-256 十六进制前 32 位）。
- Produces: `PasswordResetThrottleAdapter(StringRedisTemplate redis, PasswordResetPolicy policy)`（`@Component`）：`static List<String> requestKeys(EmailAddress email, String clientIp)` = `[idn:pw-reset:cooldown:email:{h}, idn:pw-reset:day:email:{h}, idn:pw-reset:hour:ip:{h}]`（IP 为 `null` 时只有前两个）；`static String submitKey(String clientIp)` = `idn:pw-reset-submit:minute:ip:{h}`；`acquireSubmit(null, now)` 直接放行（没有可计的维度）。
- Produces: `EmailChangeThrottleAdapter(StringRedisTemplate redis, EmailChangePolicy policy)`（`@Component`）：`static String key(long userId)` = `idn:email-change:day:user:{userId}`。
- `EmailResendThrottleAdapter`：构造器不变，内部改用 `ActionThrottleScript`，`keys()` 不变，`markSent` 走 `enforce = false`。

- [ ] **Step 1: 写失败的测试**

```java
// ActionThrottleScriptTest（mock(StringRedisTemplate)）
@Test
void should_lay_out_keys_and_argv_in_order() { /* 捕获 redis.execute 的 keys 与 args：keys = [cooldown, w1, w2]，args = [now, cooldownMs, "1", suffix, w1Ms, w1Limit, w2Ms, w2Limit] */ }
@Test
void should_treat_null_result_as_unavailable() { /* execute 返回 null → TemporarilyUnavailableException */ }
@Test
void should_hash_to_32_hex_characters() { assertThat(ActionThrottleScript.keyHash("203.0.113.9")).matches("^[0-9a-f]{32}$"); }

// PasswordResetThrottleAdapterTest
@Test
void should_hash_email_and_ip_into_keys_and_drop_ip_key_when_absent() {
  List<String> keys = PasswordResetThrottleAdapter.requestKeys(EmailAddress.of("chen.yu@example.com"), "203.0.113.9");
  assertThat(keys).hasSize(3);
  assertThat(keys.get(0)).matches("^idn:pw-reset:cooldown:email:[0-9a-f]{32}$").doesNotContain("chen");
  assertThat(keys.get(1)).matches("^idn:pw-reset:day:email:[0-9a-f]{32}$");
  assertThat(keys.get(2)).matches("^idn:pw-reset:hour:ip:[0-9a-f]{32}$").doesNotContain("203.0.113.9");
  assertThat(PasswordResetThrottleAdapter.requestKeys(EmailAddress.of("chen.yu@example.com"), null)).hasSize(2);
  assertThat(PasswordResetThrottleAdapter.submitKey("203.0.113.9")).matches("^idn:pw-reset-submit:minute:ip:[0-9a-f]{32}$");
}

// EmailChangeThrottleAdapterTest：key(42L) 是 idn:email-change:day:user:42

// PasswordResetThrottleAdapterIT（照 EmailResendThrottleAdapterIT 的连接方式；POLICY = of(60s, 5, 20, 10)）
@Test
void should_cool_down_same_email_for_sixty_seconds() { /* T0 放行；T0+10s 等 50s；T0+60s 放行 */ }
@Test
void should_block_sixth_request_per_email_per_day() { /* 每 61s 一次共 5 次放行；第 6 次等到最早一次满 24h */ }
@Test
void should_block_twenty_first_request_per_ip_per_hour() { /* 20 个不同邮箱同一 IP 放行；第 21 个等到最早一次满 1h */ }
@Test
void should_skip_ip_dimension_when_ip_is_unknown() { /* 21 个不同邮箱、ip = null 全部放行 */ }
@Test
void should_block_eleventh_submit_per_ip_per_minute() { /* acquireSubmit 10 次放行，第 11 次等到最早一次满 60s；ip = null 一直放行 */ }

// EmailChangeThrottleAdapterIT：第 4 次 / 天返回等待时长；不同用户互不影响
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:test :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*ThrottleAdapter*"`

- [ ] **Step 3: 实现**

脚本逻辑照 PAP-96 第 10.2 节（`ZREMRANGEBYSCORE` 清窗口、`ZCARD` 判、`ZRANGE 0 0 WITHSCORES` 算等待、放行后 `SET … PX` / `ZADD` + `PEXPIRE`），只把固定的四个键改成按 `#KEYS` 循环；返回值永远是数字。

- [ ] **Step 4: 跑测试确认通过**（含现有 `EmailResendThrottleAdapterIT` 全绿，键名不变）

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain patra-api/patra-identity/patra-identity-infra
git commit -m "feat(identity): 限流脚本参数化，加找回密码与改邮箱的限流适配器 (PAP-98)"
```

---

### Task 8: 七封邮件：记录与模板，片段去掉固定句

**Files:**
- Create（`.../domain/port/mail/`）: `PasswordResetMail.java`、`PasswordChangedMail.java`、`PasswordResetDoneMail.java`、`EmailChangeConfirmMail.java`、`EmailChangedNoticeMail.java`、`EmailChangedConfirmationMail.java`、`EmailChangeRevertedMail.java`
- Create（`patra-identity-infra/src/main/resources/mail/`）: `html/<七个>.html`、`text/<七个>.txt`
- Modify: `mail/html/fragments/security-notice.html`、`mail/text/fragments/security-notice.txt`（去掉「建议立即到安全设置……」一句）
- Test: `.../domain/port/mail/*MailTest.java`（七个）、`patra-identity-infra/src/test/.../mail/ThymeleafMailRendererTest.java`

**Interfaces:**
- Produces（字段 → `template()` / `subject()` / `model()` 键）：
  - `PasswordResetMail(EmailAddress to, String displayName, String resetUrl)` `of()`：`password-reset` /「重设你的 Patra 密码」/ `displayName`、`resetUrl`、`validMinutes`（`PASSWORD_RESET.validity().toMinutes()` = 60）。
  - `PasswordChangedMail(EmailAddress to, String displayName, SecurityNotice notice)` `of()`：`password-changed` /「你的 Patra 密码已修改」/ `displayName`、`notice`。常量 `NOT_ME_ADVICE` = 「如果不是你，你的邮箱可能已被盗：请先找回邮箱，再用『忘记密码』重设密码」。
  - `PasswordResetDoneMail(to, displayName, notice)` `of()`：`password-reset-done` /「你的 Patra 密码已重设」/ 同上；`NOT_ME_ADVICE` 同上。
  - `EmailChangeConfirmMail(to, displayName, String confirmUrl)` `of()`：`email-change-confirm` /「确认你的新 Patra 邮箱」/ `displayName`、`confirmUrl`、`validMinutes`（60）。
  - `EmailChangedNoticeMail(to, displayName, String newEmailMasked, String revertUrl, SecurityNotice notice)` `@Builder`：`email-changed-notice` /「你的 Patra 邮箱已更改」/ `displayName`、`newEmailMasked`、`revertUrl`、`validDays`（`EMAIL_CHANGE_REVERT.validity().toDays()` = 7）、`notice`；`NOT_ME_ADVICE` = 「如果不是你，请点下面的链接撤销：邮箱会改回本地址，所有设备下线，并给你发重设密码的链接」。
  - `EmailChangedConfirmationMail(to, displayName, notice)` `of()`：`email-changed-confirmation` /「本邮箱已绑定你的 Patra 账号」；`NOT_ME_ADVICE` = 「如果不是你，请联系旧邮箱的主人或忽略」。
  - `EmailChangeRevertedMail(to, displayName, String resetUrl, SecurityNotice notice)` `of()`：`email-change-reverted` /「你的 Patra 邮箱已改回，请重设密码」/ `displayName`、`resetUrl`、`validMinutes`（60）、`notice`；`NOT_ME_ADVICE` = 「账号已锁定：所有设备已下线，原密码已作废，请用下面的链接设置新密码」。
- 模板：HTML 照 `email-verification.html` 的骨架（layout header / footer、主按钮 + 完整链接），安全通知用 `<tr th:replace="~{html/fragments/security-notice :: notice(${notice})}">`；文本照 `email-verification.txt`，安全通知用 `[# th:replace="~{text/fragments/security-notice}" /]`。「建议立即到安全设置修改密码并检查登录设备：{securityUrl}」只写在 `password-changed`、`email-changed-notice`、`email-changed-confirmation` 的正文里。

- [ ] **Step 1: 写失败的测试**

七个 `*MailTest` 各两条：`should_expose_template_subject_and_model`（模板名、主题、`model()` 的键与 `validMinutes` / `validDays` 值）、`should_not_leak_recipient_or_links_in_to_string`。

`ThymeleafMailRendererTest` 加：

```java
@Test
void should_render_all_seven_templates_in_text_and_html() {
  for (OutgoingMail mail : SEVEN_SAMPLES) {
    RenderedMail r = renderer.render(mail);
    assertThat(r.text()).contains("陈宇").doesNotContain("[(").doesNotContain("<");
    assertThat(r.html()).contains("陈宇").doesNotContainPattern("\\sth:[a-z]+=").doesNotContain("${");
  }
}
@Test
void should_put_links_in_both_parts() { /* password-reset、email-change-confirm、email-changed-notice、email-change-reverted 的 URL 在 text 与 html（href=）里 */ }
@Test
void should_render_notice_four_items_without_fixed_security_line_in_fragment() {
  RenderedMail reverted = renderer.render(EmailChangeRevertedMail.of(TO, "陈宇", RESET_URL, NOTICE));
  assertThat(reverted.text()).contains("时间：").contains("设备：未知").contains("IP：未知").contains(EmailChangeRevertedMail.NOT_ME_ADVICE).doesNotContain("/settings/security");
  RenderedMail changed = renderer.render(PasswordChangedMail.of(TO, "陈宇", NOTICE));
  assertThat(changed.text()).contains("https://patra.example/settings/security");
}
@Test
void should_mask_new_email_in_old_address_notice() { /* newEmailMasked = "n***@example.com" 在 text 与 html 里，完整新地址不在 */ }
```

现有 `should_render_security_notice_fragment_with_unknown_placeholders` / `..._fields_when_present`：去掉对 `securityUrl` 在片段里的断言。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test :patra-api:patra-identity:patra-identity-infra:test --tests "*Mail*"`

- [ ] **Step 3: 实现**

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain patra-api/patra-identity/patra-identity-infra
git commit -m "feat(identity): 加密码与邮箱变更的七封邮件与模板，安全通知片段去掉固定句 (PAP-98)"
```

---

### Task 9: 启动层配置与装配

**Files:**
- Modify: `patra-identity-boot/src/main/.../config/IdentityProperties.java`、`IdentityConfiguration.java`、`patra-identity-boot/src/main/resources/application.yml`
- Test: `patra-identity-boot/src/test/.../config/IdentityConfigurationTest.java`、`patra-identity-boot/src/test/.../ApplicationConfigTest.java`

**Interfaces:**
- Produces: `IdentityProperties` 加 `@DefaultValue Reauth reauth`（`record Reauth(@DefaultValue("10m") Duration window)`）、`@DefaultValue PasswordReset passwordReset`（`record PasswordReset(@DefaultValue("60s") Duration cooldown, @DefaultValue("5") int perEmailDaily, @DefaultValue("20") int perIpHourly, @DefaultValue("10") int submitPerIpMinutely)`）、`@DefaultValue EmailChange emailChange`（`record EmailChange(@DefaultValue("3") int perAccountDaily)`）；`Links` 加 `@DefaultValue("/reset-password") String resetPasswordPath`、`@DefaultValue("/confirm-email-change") String confirmEmailChangePath`、`@DefaultValue("/revert-email-change") String revertEmailChangePath`。
- Produces: Bean `ReauthenticationPolicy reauthenticationPolicy(IdentityProperties)`、`PasswordResetPolicy passwordResetPolicy(IdentityProperties)`、`EmailChangePolicy emailChangePolicy(IdentityProperties)`、`ReauthenticationGuard reauthenticationGuard(SessionStorePort sessions, ReauthenticationPolicy policy, Clock clock)`；`mailLinkPolicy` 改用 builder 装六个路径。
- `application.yml` 加 spec §13 的键（含注释）。

- [ ] **Step 1: 写失败的测试**

`IdentityConfigurationTest` 加 `should_bind_security_defaults`（三个策略的默认值、`MailLinkPolicy` 三个新路径）、`should_bind_security_overrides`（`patra.identity.reauth.window=30s` 等）、`should_fail_on_non_positive_security_values`（`per-email-daily=0` 启动失败）。`ApplicationConfigTest`：按现有写法断言 `application.yml` 里 `patra.identity.reauth.window` 等键存在。

- [ ] **Step 2: 跑测试确认失败** Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:test`

- [ ] **Step 3: 实现**

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:test :patra-tool:patra-tool-launch:test --tests "*JarConfigOverlapCheckerTest*" --tests "*NacosConfigFilesTest*"`（jar 与 Nacos 不重复）

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-boot
git commit -m "feat(identity): 装配再认证窗口、找回密码与改邮箱限流、新邮件链接路径 (PAP-98)"
```

---

### Task 10: 再认证 `POST /auth/reauth`

**Files:**
- Create: `patra-identity-app/src/main/.../usecase/reauth/ReauthenticateUserCommand.java`、`ReauthenticateUserHandler.java`
- Create: `patra-identity-adapter/src/main/.../rest/auth/request/ReauthRequest.java`
- Modify: `patra-identity-adapter/src/main/.../rest/auth/AuthController.java`
- Test: `patra-identity-app/src/test/.../reauth/ReauthenticateUserHandlerTest.java`、`patra-identity-adapter/src/integrationTest/.../auth/AuthControllerIT.java`

**Interfaces:**
- Produces: `record ReauthenticateUserCommand(String password) implements Command<AuthenticatedUser>`，`of()`，`toString` 遮掩。
- Produces: `ReauthenticateUserHandler(CurrentUserPort currentUserPort, UserRepository users, UserPasswordCredentialRepository credentials, UserReadPort userReadPort, PasswordHashingPort passwordHashing, LoginThrottlePort loginThrottle, SessionStorePort sessions)`，流程 spec §8.1，不开事务。
- Produces: `AuthenticatedUserResponse AuthController.reauth(@RequestBody ReauthRequest request)` → `POST /auth/reauth` 200。

- [ ] **Step 1: 写失败的测试**

```java
// ReauthenticateUserHandlerTest（桩：当前用户 42 / 会话 7001；users.findById 正常用户；credentials 有；passwordHashing.matches 真）
@Test void should_reject_blank_password_with_422() { /* password "" → InvalidUserFieldsException，不调 loginThrottle */ }
@Test void should_require_login_when_user_is_banned_or_deleted() { /* BANNED → AuthenticationRequiredException，不调 loginThrottle */ }
@Test void should_allow_pending_deletion_user() { /* PENDING_DELETION → 正常返回 */ }
@Test void should_rotate_session_and_return_new_token_after_success() {
  when(sessions.rotate(USER, 42L, 7001L)).thenReturn(Optional.of("patra_user_" + "N".repeat(43)));
  AuthenticatedUser result = handler.handle(ReauthenticateUserCommand.of("Correct-Horse-02"));
  InOrder order = inOrder(loginThrottle, sessions);
  order.verify(loginThrottle).begin(USER, EMAIL);
  order.verify(loginThrottle).recordSuccess(any());
  order.verify(sessions).rotate(USER, 42L, 7001L);
  assertThat(result.sessionToken()).startsWith("patra_user_");
}
@Test void should_record_failure_and_throw_password_mismatch() { /* matches 假 → recordFailure；抛 CurrentPasswordMismatchException；sessions 无交互 */ }
@Test void should_return_429_when_failure_locks() { /* recordFailure 返回 Optional.of(15m) → LoginTemporarilyLockedException */ }
@Test void should_reject_when_locked_before_hashing() { /* begin 抛 LoginTemporarilyLockedException → 原样抛；passwordHashing 无交互 */ }
@Test void should_use_dummy_hash_when_credential_is_missing() { /* credentials 空 → verifyAgainstDummy 被调、recordFailure、401 */ }
@Test void should_require_login_when_session_vanished_during_rotation() { /* rotate 空 → AuthenticationRequiredException */ }
@Test void should_cancel_attempt_when_lookup_fails() { /* credentials.findByUserId 抛 RuntimeException → loginThrottle.cancel 被调，异常原样抛 */ }

// AuthControllerIT 加
@Test void should_reauth_with_200_and_new_token() { /* 200，$.sessionToken、$.user.userId */ }
@Test void should_render_password_mismatch_as_401_with_trait() { /* commandBus 抛 CurrentPasswordMismatchException → 401，$.traits 含 "PASSWORD_MISMATCH" */ }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*ReauthenticateUserHandlerTest*" :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*AuthControllerIT*"`

- [ ] **Step 3: 实现**（密码校验 + 结算的写法照 `LoginUserHandler.verify` / `failure` / `cancelQuietly`）

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-app patra-api/patra-identity/patra-identity-adapter
git commit -m "feat(identity): 加再认证接口，校验密码后换令牌并刷新 auth_at (PAP-98)"
```

---

### Task 11: 改密码 `PUT /me/password`

**Files:**
- Create: `patra-identity-app/src/main/.../usecase/password/ChangePasswordCommand.java`、`ChangePasswordHandler.java`
- Create: `patra-identity-adapter/src/main/.../rest/me/request/ChangePasswordRequest.java`
- Modify: `patra-identity-adapter/src/main/.../rest/me/MeController.java`
- Test: `patra-identity-app/src/test/.../password/ChangePasswordHandlerTest.java`、`patra-identity-adapter/src/integrationTest/.../me/MeControllerIT.java`

**Interfaces:**
- Produces: `record ChangePasswordCommand(String currentPassword, String newPassword, String clientIp) implements Command<AuthenticatedUser>`，`of()`，`toString` 遮掩两个密码。
- Produces: `ChangePasswordHandler(CurrentUserPort currentUserPort, UserRepository users, UserPasswordCredentialRepository credentials, UserReadPort userReadPort, PasswordHashingPort passwordHashing, PasswordPolicy passwordPolicy, LoginThrottlePort loginThrottle, EmailTokenRepository tokens, SessionStorePort sessions, UserLoginRecordRepository records, EmailDeliveryPort emailDelivery, TransactionOperations transactions, Clock clock)`，流程 spec §8.2；新旧相同的比较用 `Normalizer.normalize(raw, NFKC)`；发 `PasswordChangedMail.of(email, displayName, SecurityNotice.builder().occurredAt(now).notMeAdvice(PasswordChangedMail.NOT_ME_ADVICE).build())`。
- Produces: `AuthenticatedUserResponse MeController.changePassword(@RequestBody ChangePasswordRequest request, HttpServletRequest http)` → `PUT /me/password` 200。

- [ ] **Step 1: 写失败的测试**

```java
// ChangePasswordHandlerTest
@Test void should_report_both_field_errors_at_once() { /* current "" + new "short" → errors[] 两条：currentPassword/REQUIRED、newPassword/TOO_SHORT；不调 loginThrottle */ }
@Test void should_reject_pending_deletion_with_409() { /* UserPendingDeletionException，不调 loginThrottle */ }
@Test void should_reject_wrong_current_password_with_401_and_count_failure() { /* CurrentPasswordMismatchException；recordFailure；不哈希新密码 */ }
@Test void should_reject_unchanged_password_with_422_without_hashing() { /* current == new（含 NFKC 等价的全角形式）→ newPassword/UNCHANGED；passwordHashing.hash 未调 */ }
@Test void should_change_in_order_rotate_keep_current_and_revoke_the_rest() {
  when(sessions.rotate(USER, 42L, 7001L)).thenReturn(Optional.of(NEW_TOKEN));
  when(sessions.revokeAllExcept(USER, 42L, 7001L)).thenReturn(List.of(7002L, 7003L));
  AuthenticatedUser result = handler.handle(ChangePasswordCommand.of("Correct-Horse-02", "Another-Horse-03", "127.0.0.1"));
  InOrder order = inOrder(credentials, tokens, sessions, records, emailDelivery);
  order.verify(credentials).save(any());
  order.verify(tokens).revokeAllActive(42L, NOW);
  order.verify(sessions).rotate(USER, 42L, 7001L);
  order.verify(sessions).revokeAllExcept(USER, 42L, 7001L);
  order.verify(records, times(2)).save(argThat(r -> r.getEndReason() == LoginEndReason.REVOKED));
  order.verify(emailDelivery).send(any(PasswordChangedMail.class));
  assertThat(result.sessionToken()).isEqualTo(NEW_TOKEN);
}
@Test void should_send_mail_only_after_transaction() { /* inTransaction 在 send 时为假、在 save 时为真 */ }
@Test void should_roll_back_password_when_session_rotation_fails() { /* rotate 抛 SessionStoreUnavailableException → 异常原样抛，emailDelivery 无交互（事务回调抛出即回滚） */ }

// MeControllerIT 加：PUT /me/password 200 带 sessionToken；422 两条 errors；401 traits 含 PASSWORD_MISMATCH；429 Retry-After；命令里 clientIp 是 127.0.0.1
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*ChangePasswordHandlerTest*" :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*MeControllerIT*"`

- [ ] **Step 3: 实现**

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-app patra-api/patra-identity/patra-identity-adapter
git commit -m "feat(identity): 加改密码接口：本机换令牌保留登录、其余会话撤销、发通知邮件 (PAP-98)"
```

---

### Task 12: 找回密码 `POST /auth/password/forgot`、`POST /auth/password/reset`

**Files:**
- Create: `patra-identity-app/src/main/.../usecase/password/RequestPasswordResetCommand.java`、`RequestPasswordResetHandler.java`、`PasswordResetRequested.java`、`ResetPasswordCommand.java`、`ResetPasswordHandler.java`、`PasswordReset.java`
- Create: `patra-identity-adapter/src/main/.../rest/auth/PasswordResetController.java`、`request/ForgotPasswordRequest.java`、`request/ResetPasswordRequest.java`、`response/PasswordResetRequestedResponse.java`、`response/PasswordResetResponse.java`
- Test: `patra-identity-app/src/test/.../password/RequestPasswordResetHandlerTest.java`、`ResetPasswordHandlerTest.java`、`patra-identity-adapter/src/integrationTest/.../auth/PasswordResetControllerIT.java`

**Interfaces:**
- Produces: `record RequestPasswordResetCommand(String email, String clientIp) implements Command<PasswordResetRequested>`；`record PasswordResetRequested(String email, Instant nextRequestAt)`。
- Produces: `RequestPasswordResetHandler(UserRepository users, UserReadPort userReadPort, PasswordResetThrottlePort passwordResetThrottle, PasswordResetPolicy policy, EmailTokenIssuer emailTokenIssuer, MailLinkPolicy links, EmailDeliveryPort emailDelivery, TransactionOperations transactions, Clock clock, SecureRandom random)`，流程 spec §8.3（未注册 / 封禁分支 `EmailTokenSecret.generate(random).hash()` 后丢弃）。
- Produces: `record ResetPasswordCommand(String token, String newPassword, String clientIp) implements Command<PasswordReset>`；`record PasswordReset(String email)`。
- Produces: `ResetPasswordHandler(PasswordResetThrottlePort passwordResetThrottle, PasswordPolicy passwordPolicy, PasswordHashingPort passwordHashing, EmailTokenRepository tokens, UserRepository users, UserPasswordCredentialRepository credentials, UserReadPort userReadPort, SessionStorePort sessions, UserLoginRecordRepository records, LoginThrottlePort loginThrottle, EmailDeliveryPort emailDelivery, TransactionOperations transactions, Clock clock)`，流程 spec §8.4。
- Produces: `PasswordResetController`（`@RequestMapping("/auth/password")`）：`@PostMapping("/forgot") @ResponseStatus(ACCEPTED) PasswordResetRequestedResponse forgot(@RequestBody ForgotPasswordRequest, HttpServletRequest)`；`@PostMapping("/reset") PasswordResetResponse reset(@RequestBody ResetPasswordRequest, HttpServletRequest)`。请求记录 `toString` 遮掩令牌与密码。

- [ ] **Step 1: 写失败的测试**

```java
// RequestPasswordResetHandlerTest
@Test void should_reject_invalid_email_format_before_throttling() { /* "nope" → InvalidUserFieldsException；throttle 无交互 */ }
@Test void should_count_throttle_before_looking_up_user() { InOrder order = inOrder(passwordResetThrottle, users); handler.handle(cmd); order.verify(passwordResetThrottle).acquireRequest(EMAIL, "127.0.0.1", NOW); order.verify(users).findByEmail(EMAIL); }
@Test void should_throw_429_when_throttled() { /* acquireRequest 返回 Optional.of(50s) → PasswordResetLimitedException，users 无交互 */ }
@Test void should_issue_and_send_for_active_user() { /* issue(PASSWORD_RESET, 42, EMAIL, ip) 在事务内；send(PasswordResetMail) 在事务外且 resetUrl 以 /reset-password#token= + secret 结尾；返回 nextRequestAt = NOW + 60s */ }
@Test void should_return_same_result_without_issuing_for_unknown_email() { /* findByEmail 空 → 结果同上；issuer、emailDelivery 无交互 */ }
@Test void should_not_send_for_banned_user() { /* BANNED → 同上 */ }
@Test void should_send_for_pending_deletion_user() { /* PENDING_DELETION → 发 */ }

// ResetPasswordHandlerTest（桩：token 行 PASSWORD_RESET / user 42 / email EMAIL；用户 ACTIVE、邮箱 EMAIL；consume 返回该 token）
@Test void should_throttle_submit_before_anything() { /* acquireSubmit 返回 Optional.of(30s) → PasswordResetLimitedException；tokens 无交互 */ }
@Test void should_not_consume_token_when_new_password_is_invalid() { /* "short" → 422 newPassword/TOO_SHORT；tokens.consume 未调 */ }
@Test void should_reject_wrong_purpose_token() { /* purpose EMAIL_VERIFY → 422 token/INVALID */ }
@Test void should_reject_when_user_is_banned() { /* BANNED → 422 */ }
@Test void should_reject_when_token_email_differs_from_current() { /* 用户邮箱已变 → 422；credentials.save 未调 */ }
@Test void should_reset_in_order_and_clear_login_failures() {
  InOrder order = inOrder(credentials, tokens, users, sessions, loginThrottle, emailDelivery);
  order.verify(credentials).save(argThat(c -> c.getPasswordHash().equals(NEW_HASH)));
  order.verify(tokens).revokeAllActive(42L, NOW);
  order.verify(users).save(argThat(User::isEmailVerified));
  order.verify(sessions).revokeAll(USER, 42L);
  order.verify(loginThrottle).clear(USER, EMAIL);
  order.verify(emailDelivery).send(any(PasswordResetDoneMail.class));
}
@Test void should_create_credential_when_user_has_none() { /* credentials.findByUserId 空 → save 的是 create(42, NEW_HASH) */ }

// PasswordResetControllerIT：forgot 202 字段 + clientIp；forgot 422；forgot 429 Retry-After；reset 200 $.email；reset 422 token/REQUIRED；reset 429
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*PasswordReset*" :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*PasswordResetControllerIT*"`

- [ ] **Step 3: 实现**

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-app patra-api/patra-identity/patra-identity-adapter
git commit -m "feat(identity): 加找回密码的申请与重设接口 (PAP-98)"
```

---

### Task 13: 改邮箱：申请、查看、取消

**Files:**
- Create: `patra-identity-app/src/main/.../usecase/emailchange/RequestEmailChangeCommand.java`、`RequestEmailChangeHandler.java`、`EmailChangeRequested.java`、`CancelEmailChangeCommand.java`、`CancelEmailChangeHandler.java`、`PendingEmailChange.java`
- Modify: `patra-identity-app/src/main/.../user/query/UserQueryService.java`
- Create: `patra-identity-adapter/src/main/.../rest/me/request/ChangeEmailRequest.java`、`response/EmailChangeRequestedResponse.java`、`response/PendingEmailChangeResponse.java`
- Modify: `patra-identity-adapter/src/main/.../rest/me/MeEmailController.java`
- Test: `patra-identity-app/src/test/.../emailchange/RequestEmailChangeHandlerTest.java`、`CancelEmailChangeHandlerTest.java`、`.../user/query/UserQueryServiceTest.java`、`patra-identity-adapter/src/integrationTest/.../me/MeEmailControllerIT.java`

**Interfaces:**
- Produces: `record RequestEmailChangeCommand(String newEmail, String clientIp) implements Command<EmailChangeRequested>`；`record EmailChangeRequested(String newEmail, Instant expiresAt)`。
- Produces: `RequestEmailChangeHandler(CurrentUserPort currentUserPort, UserRepository users, UserReadPort userReadPort, ReauthenticationGuard reauthenticationGuard, EmailTokenRepository tokens, EmailChangeThrottlePort emailChangeThrottle, EmailTokenIssuer emailTokenIssuer, MailLinkPolicy links, EmailDeliveryPort emailDelivery, TransactionOperations transactions, Clock clock)`，流程 spec §8.5（顺序：字段 → 状态 → 再认证 → 待确认 → 占用 409 → 限流 → 事务签发 → 发信）。
- Produces: `record CancelEmailChangeCommand() implements Command<Void>`，`of()`；`CancelEmailChangeHandler(CurrentUserPort, UserRepository, EmailTokenRepository, Clock)` `@Transactional`。
- Produces: `record PendingEmailChange(String newEmail, Instant expiresAt)`；`Optional<PendingEmailChange> UserQueryService.pendingEmailChange()`（`require()` → `tokens.findActive(userId, EMAIL_CHANGE_CONFIRM, now)`；`UserQueryService` 多注入 `EmailTokenRepository tokens`）。
- Produces: `MeEmailController` 加 `@PostMapping("/change") @ResponseStatus(ACCEPTED) EmailChangeRequestedResponse requestChange(@RequestBody ChangeEmailRequest, HttpServletRequest)`；`@GetMapping("/change") ResponseEntity<PendingEmailChangeResponse> pendingChange()`（空 → `noContent()`）；`@DeleteMapping("/change") @ResponseStatus(NO_CONTENT) void cancelChange()`。

- [ ] **Step 1: 写失败的测试**

```java
// RequestEmailChangeHandlerTest
@Test void should_reject_same_as_current_email_with_422() { /* newEmail/UNCHANGED；guard 无交互 */ }
@Test void should_check_in_order_fields_status_reauth_pending_taken_throttle() { /* InOrder: users.findById → reauthenticationGuard.requireRecent → tokens.findActive → users.existsByEmail → emailChangeThrottle.acquire */ }
@Test void should_require_reauth_before_pending_check() { /* guard 抛 ReauthenticationRequiredException → 原样抛；tokens、throttle 无交互 */ }
@Test void should_reject_when_change_is_pending() { /* findActive 有 → EmailChangePendingException；throttle 无交互 */ }
@Test void should_throw_429_when_daily_quota_used() { /* acquire 返回 Optional.of(6h) → EmailChangeLimitedException */ }
@Test void should_issue_and_send_to_new_email() { /* issue(EMAIL_CHANGE_CONFIRM, 42, NEW, ip) 在事务内；send(EmailChangeConfirmMail) 收件人 NEW、confirmUrl 以 /confirm-email-change#token= 结尾；返回 expiresAt = token.expiresAt */ }
@Test void should_reject_taken_email_with_409_before_throttling() { /* existsByEmail 真 → UserIdentifierTakenException，errors[0].field == "email"；throttle、issuer、emailDelivery 无交互 */ }

// CancelEmailChangeHandlerTest：revokeActive(42, EMAIL_CHANGE_CONFIRM, NOW)；0 行也不抛；PENDING_DELETION 409
// UserQueryServiceTest：pendingEmailChange 有 / 无；未登录 401
// MeEmailControllerIT 加：POST /me/email/change 202 字段；403 traits 含 REAUTH_REQUIRED；409 含 EMAIL_CHANGE_PENDING；409 errors[0].field == email（UserIdentifierTakenException）；429；GET 200 字段 / 204；DELETE 204
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*EmailChange*" --tests "*UserQueryServiceTest*" :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*MeEmailControllerIT*"`

- [ ] **Step 3: 实现**

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-app patra-api/patra-identity/patra-identity-adapter
git commit -m "feat(identity): 加改邮箱的申请、查看与取消接口，申请先过再认证 (PAP-98)"
```

---

### Task 14: 改邮箱：确认、撤销

**Files:**
- Create: `patra-identity-app/src/main/.../usecase/emailchange/ConfirmEmailChangeCommand.java`、`ConfirmEmailChangeHandler.java`、`EmailChanged.java`、`RevertEmailChangeCommand.java`、`RevertEmailChangeHandler.java`、`EmailChangeReverted.java`
- Create: `patra-identity-adapter/src/main/.../rest/auth/EmailChangeController.java`、`request/ConfirmEmailChangeRequest.java`、`request/RevertEmailChangeRequest.java`、`response/EmailChangedResponse.java`、`response/EmailChangeRevertedResponse.java`
- Test: `patra-identity-app/src/test/.../emailchange/ConfirmEmailChangeHandlerTest.java`、`RevertEmailChangeHandlerTest.java`、`patra-identity-adapter/src/integrationTest/.../auth/EmailChangeControllerIT.java`

**Interfaces:**
- Produces: `record ConfirmEmailChangeCommand(String token, String clientIp) implements Command<EmailChanged>`；`record EmailChanged(String email, Instant emailVerifiedAt)`。`ConfirmEmailChangeHandler(EmailTokenRepository tokens, UserRepository users, UserReadPort userReadPort, EmailTokenIssuer emailTokenIssuer, MailLinkPolicy links, EmailDeliveryPort emailDelivery, TransactionOperations transactions, Clock clock)`，流程 spec §8.7（事务用 `transactions.execute`，发信在事务外先旧后新）。
- Produces: `record RevertEmailChangeCommand(String token, String clientIp) implements Command<EmailChangeReverted>`；`record EmailChangeReverted(String email)`。`RevertEmailChangeHandler(EmailTokenRepository tokens, UserRepository users, UserReadPort userReadPort, UserPasswordCredentialRepository credentials, SessionStorePort sessions, UserLoginRecordRepository records, EmailTokenIssuer emailTokenIssuer, MailLinkPolicy links, EmailDeliveryPort emailDelivery, TransactionOperations transactions, Clock clock)`，流程 spec §8.8。
- Produces: `EmailChangeController`（`@RequestMapping("/auth/email/change")`）：`@PostMapping("/confirm") EmailChangedResponse confirm(@RequestBody ConfirmEmailChangeRequest, HttpServletRequest)`；`@PostMapping("/revert") EmailChangeRevertedResponse revert(@RequestBody RevertEmailChangeRequest, HttpServletRequest)`。

- [ ] **Step 1: 写失败的测试**

```java
// ConfirmEmailChangeHandlerTest（桩：token EMAIL_CHANGE_CONFIRM / user 42 / email NEW；用户 ACTIVE、邮箱 OLD）
@Test void should_reject_non_active_user_with_422() { /* PENDING_DELETION → token/INVALID；consume 未调 */ }
@Test void should_reject_with_409_when_new_email_taken_meanwhile() { /* existsByEmail(NEW) 真 → UserIdentifierTakenException，errors[0].field == "email" */ }
@Test void should_change_email_revoke_tokens_issue_revert_and_send_two_mails_old_first() {
  InOrder order = inOrder(users, tokens, emailTokenIssuer, emailDelivery);
  order.verify(users).save(argThat(u -> u.getEmail().orElseThrow().equals(NEW) && u.isEmailVerified()));
  order.verify(tokens).revokeAllActive(42L, NOW);
  order.verify(emailTokenIssuer).issue(EmailTokenPurpose.EMAIL_CHANGE_REVERT, 42L, OLD, "127.0.0.1");
  order.verify(emailDelivery).send(argThat(m -> m instanceof EmailChangedNoticeMail n && n.to().equals(OLD) && n.newEmailMasked().equals("n***@example.com") && n.revertUrl().endsWith("/revert-email-change#token=" + SECRET.value())));
  order.verify(emailDelivery).send(argThat(m -> m instanceof EmailChangedConfirmationMail c && c.to().equals(NEW)));
}
// RevertEmailChangeHandlerTest（桩：token EMAIL_CHANGE_REVERT / email OLD；用户邮箱 NEW）
@Test void should_allow_pending_deletion_user() { /* PENDING_DELETION → 成功，users.save 的状态仍是 PENDING_DELETION */ }
@Test void should_reject_with_409_and_keep_token_when_old_email_taken() { /* existsByEmail(OLD) 真 → UserIdentifierTakenException；consume 已调但异常让事务回滚（断言 transactions 回调抛出） */ }
@Test void should_revert_in_order() {
  InOrder order = inOrder(users, tokens, credentials, sessions, emailTokenIssuer, emailDelivery);
  order.verify(users).save(argThat(u -> u.getEmail().orElseThrow().equals(OLD)));
  order.verify(tokens).revokeAllActive(42L, NOW);
  order.verify(tokens).revokeActive(42L, EmailTokenPurpose.EMAIL_CHANGE_REVERT, NOW);
  order.verify(credentials).deleteByUserId(42L);
  order.verify(sessions).revokeAll(USER, 42L);
  order.verify(emailTokenIssuer).issue(EmailTokenPurpose.PASSWORD_RESET, 42L, OLD, "127.0.0.1");
  order.verify(emailDelivery).send(argThat(m -> m instanceof EmailChangeRevertedMail r && r.to().equals(OLD) && r.resetUrl().endsWith("/reset-password#token=" + SECRET.value())));
}

// EmailChangeControllerIT：confirm 200 $.email / $.emailVerifiedAt；422 token/INVALID；409 errors[0].field == email；revert 200 $.email；422；409
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*EmailChangeHandlerTest*" :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*EmailChangeControllerIT*"`

- [ ] **Step 3: 实现**

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-app patra-api/patra-identity/patra-identity-adapter
git commit -m "feat(identity): 加改邮箱的确认与撤销接口，撤销作废密码并发重设链接 (PAP-98)"
```

---

### Task 15: 网关公开 `/auth/password/**`、`/auth/email/change/**`

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/security/GatewaySecurityConfiguration.java`
- Test: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayAuthenticationIT.java`

**Interfaces:**
- Produces: `IDENTITY_PUBLIC_PATHS` 加 `"/patra-identity/auth/password/**"`、`"/patra-identity/auth/email/change/**"`，注释说明（找回密码匿名；改邮箱确认 / 撤销在任何设备点链接）。

- [ ] **Step 1: 写失败的测试**

```java
@Test
void should_forward_password_reset_and_email_change_links_anonymously() {
  identity.stubFor(post(urlPathMatching("/auth/(password|email/change)/.*")).willReturn(okJson("{}")));
  for (String path : List.of("/auth/password/forgot", "/auth/password/reset", "/auth/email/change/confirm", "/auth/email/change/revert")) {
    restClient.post().uri("/patra-identity" + path).exchange().expectStatus().isOk();
    identity.verify(1, postRequestedFor(urlPathEqualTo(path)).withoutHeader(HttpHeaders.AUTHORIZATION));
  }
}

@Test
void should_require_session_for_reauth_password_change_and_email_change_request() {
  identity.stubFor(any(urlPathMatching("/.*")).willReturn(okJson("{}")));
  restClient.post().uri("/patra-identity/auth/reauth").exchange().expectStatus().isUnauthorized();
  restClient.put().uri("/patra-identity/me/password").exchange().expectStatus().isUnauthorized();
  restClient.post().uri("/patra-identity/me/email/change").exchange().expectStatus().isUnauthorized();
  identity.verify(0, postRequestedFor(urlPathEqualTo("/auth/reauth")));
  String token = GatewayITSessions.issue(sessions, 4231L, 9231L);
  restClient.post().uri("/patra-identity/auth/reauth").header(HttpHeaders.AUTHORIZATION, BEARER + token).exchange().expectStatus().isOk();
  identity.verify(1, postRequestedFor(urlPathEqualTo("/auth/reauth")).withHeader(HttpHeaders.AUTHORIZATION, matching("Bearer .+")));
}
```

- [ ] **Step 2: 跑测试确认失败** Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayAuthenticationIT*"`（第一条 401）

- [ ] **Step 3: 实现**

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-gateway-boot
git commit -m "feat(gateway): 公开找回密码与改邮箱确认、撤销的路径 (PAP-98)"
```

---

### Task 16: 端到端流程测试

**Files:**
- Create: `patra-identity-boot/src/integrationTest/java/dev/patra/identity/IdentityFlowITSupport.java`（抽象基类：`EmailVerificationFlowIT` 的 `register` / `login` / `post` / `put` / `get` / `delete` / `rawClient` / `toRaw` / `assertionFor` / `headers` / `waitForMail` / `partText` / `recipients` / `tokenIn` / `sessionKey` / `latestSessionIdOf` / `endReasonOf` / `userIdOf` / `sessionTokenOf` / `RawResponse`，`@BeforeEach reset()`；注解组合与 `EmailVerificationFlowIT` 相同，放在基类上；现有 IT 不改）
- Create: `ReauthenticationFlowIT.java`、`PasswordChangeFlowIT.java`、`PasswordResetFlowIT.java`、`EmailChangeFlowIT.java`

**Interfaces:**
- Consumes: Task 10 ~ 15 的全部端点；`MutableClock clock`；`GreenMail`。

- [ ] **Step 1: 写测试**（spec §15.2 的四个流程，每条断言按 spec 原文；用例名：）

```
ReauthenticationFlowIT
  should_allow_sensitive_action_right_after_register_then_require_reauth_after_ten_minutes
  should_share_login_failure_limit_with_reauth
  should_rotate_token_on_reauth_keeping_session_id
PasswordChangeFlowIT
  should_reject_wrong_current_and_unchanged_password
  should_change_password_keep_current_session_revoke_others_and_send_mail
  should_revoke_pending_reset_token_on_password_change
PasswordResetFlowIT
  should_answer_202_for_registered_and_unknown_emails_alike_and_only_mail_registered（10 + 10 次，中位数之比 < 10）
  should_reset_password_revoke_everything_clear_failures_and_verify_email
  should_expire_reset_token_after_one_hour_and_throttle_submits
EmailChangeFlowIT
  should_request_confirm_and_switch_login_email
  should_revert_within_seven_days_lock_account_and_send_reset_link
  should_keep_revert_link_alive_through_password_change_and_second_change（X → Y，改密码，Y → Z，点 X 的撤销链接仍 200，Y 的 422）
  should_reject_taken_email_with_409_and_leave_no_pending_change
  should_limit_requests_to_three_per_day_and_reject_second_pending
```

- [ ] **Step 2: 跑测试**

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*FlowIT*"`
Expected: 全部 PASS（前序任务各自 TDD 过，这里首跑全绿也算通过，不为凑 RED 改测试）。有失败就是前序任务的缺陷，回到对应任务修。

- [ ] **Step 3: 提交**

```bash
git add patra-api/patra-identity/patra-identity-boot/src/integrationTest
git commit -m "test(identity): 加再认证、改密码、找回密码、改邮箱的端到端流程测试 (PAP-98)"
```

---

### Task 17: 文档、全量回归、Linear 与交接

**Files:**
- Modify: `patra-api/patra-identity/README.md`（spec §17）、`patra-api/patra-identity/patra-identity-session/README.md`（Task 1 已改，核对）
- Modify: `docs/patra/release-specs/v0.12-user-center.md`（spec §19；文件仍由规划会话提交，这里只改内容、不 `git add`）
- Modify: 本 spec 第 16 节「实施结果」列、状态行

- [ ] **Step 1: 写 README 与 release spec 的补充**

- [ ] **Step 2: 全量回归**

Run（在仓库根目录）:
```
./gradlew spotlessApply
./gradlew check
./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest :patra-api:patra-gateway-boot:integrationTest
```
Expected: 全绿；把输出要点和 spec §16 十条实测结论写回 spec。

- [ ] **Step 3: 本机手工验收**（spec §15.4：本机 PG + Redis + Mailpit，`patra.identity.reauth.window` 临时 `30s`，四个流程各走一遍，Mailpit 里看七封邮件），结果写回 spec §16 末尾。

- [ ] **Step 4: 提交**

```bash
git add patra-api/patra-identity/README.md docs/patra/specs/2026-10-22-identity-account-security-design.md docs/patra/plans/2026-10-22-identity-account-security.md
git commit -m "docs(identity): 加账号安全的 README 一节，写回设计的实测结果 (PAP-98)"
```

- [ ] **Step 5: Linear**：PAP-98 推到 Review，评论里逐条贴 AC 证据（测试类名 + 回归命令输出摘要）；PAP-99 / PAP-100 / PAP-102 各加「来自 PAP-98 的契约」（spec §18）。

- [ ] **Step 6: 交接笔记**：在共享笔记目录写 `pap-98-account-security-implementation-handover.md`（产物、裁决、mini 上要做的事）。
