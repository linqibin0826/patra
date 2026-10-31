# 提醒偏好与一键退订 Implementation Plan

> **For agentic workers:** 本计划按 superpowers:executing-plans 在当前会话内联执行（`.claude/rules/superpowers.md`、用户 10/21 的决定：不用 subagent-driven）。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** notification 的 `preference` 子域：偏好读写（需登录）、每人一个退订令牌、退订落地页的读写接口、RFC 8058 一键退订接口；identity 新增收件信息内部接口；kernel 的类别、收件信息端口与注销清理扩展点。

**Architecture:** 照 interaction 的写法：domain 纯 Java 聚合 `EmailPreference` 承载第 5 节全部规则；app 用 `TransactionOperations` 包「行锁 → 改 → 按列写回」；infra 用 JdbcClient 原生 SQL；adapter 两个控制器；boot 接安全 starter 与 identity 的 HTTP Interface 代理。

**Tech Stack:** Java 25、Spring Boot 4.0.8、JdbcClient、Flyway、PostgreSQL 17、Testcontainers、WireMock、JUnit 5 + AssertJ + Mockito。

**Spec:** `docs/patra/specs/2026-10-31-notification-preference-unsubscribe-design.md`（下称 spec，节号指它）

## Global Constraints

- 根包 `dev.patra.notification`；分包按 `.claude/rules/tech/package-layout.md`；新类只进 `kernel`、`preference` 两个子域；不加跨子域依赖边。
- 方法全部写 `///` Javadoc（Markdown）；不写全类名；记录参数 ≤ 4 用 `of()`，≥ 5 用 `@Builder`（不同时给 `of()`）；集合字段在紧凑构造器里 `List.copyOf`。
- 档位 `OFF` / `INSTANT` / `DIGEST`，默认 `INSTANT`；退订类别 `REPLY` / `MENTION` / `DIGEST` / `ALL`；`HELPFUL` 永远没有邮件。
- 令牌：32 字节 `SecureRandom`、base64url 无填充、43 字符 `^[A-Za-z0-9_-]{43}$`、存原文；`toString()` 不输出原文。
- 接口路径、字段名、状态码、原因码严格照 spec 第 8 节；原因码 `UNSUBSCRIBE_LINK_INVALID`；错误码前缀 `NOTIFICATION`。
- 写用例：`TransactionOperations`，事务里只放数据库操作，identity 调用在事务外；infra 的加锁读写 `@Transactional(propagation = MANDATORY)`。
- 表只存 `user_id`（R6）；时间用注入的 `Clock`；测试方法 snake_case。
- 不 commit、不 push：全部完成并经编排会话验收后，在任务 11 统一提交到 `claude/v016-preference-2c3cbe`；commit message 中文动词开头，结尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
- 不单独钉 Spring 系依赖版本；改了 `*.gradle.kts` 在汇报里提醒用户在 IDEA 点「加载 Gradle 更改」。

## Review Focus

1. **同一个人并发改两类 / 改档位与一键退订同时到**：后一个不能覆盖前一个 → 任务 2 的 `EmailPreferenceRepositoryAdapterIT.should_serialize_concurrent_changes_on_row_lock`。
2. **首次改档位与 mail 首次取令牌并发**：只能有一行、一个令牌 → 任务 2 的 `EmailSubscriptionReadAdapterIT.should_return_same_token_to_concurrent_first_callers`。
3. **链接被邮件客户端截断或改写**（令牌少一位、带空白、类别被截成 `MEN`、类别小写）→ 任务 3 的 `UnsubscribeHandlerTest` / `EmailPreferenceQueryServiceTest` 各一组参数化用例：都是链接无效、且格式不对时不查库。
4. **用户把「回复我的」改成每日汇总后点旧的逐条邮件退订**：仍然关掉回复 → 任务 1 的 `EmailPreferenceTest.reply_unsubscribe_turns_off_regardless_of_current_mode`。
5. **识别不了的账号**（identity 不认识、已注销且邮箱为空）打开落地页：链接无效而不是 500 → 任务 3 的 `EmailPreferenceQueryServiceTest.should_reject_link_when_recipient_missing_or_deleted`。

---

### Task 1: preference 领域模型与 kernel 类别

**Files:**
- Create: `patra-api/patra-notification/patra-notification-domain/src/main/java/dev/patra/notification/domain/kernel/model/enums/NotificationCategory.java`
- Create（`domain/preference/` 下）: `model/read/EmailMode.java`、`model/enums/UnsubscribeCategory.java`、`model/vo/UnsubscribeToken.java`、`model/vo/MaskedEmail.java`、`model/aggregate/EmailPreference.java`、`exception/PreferenceErrorTrait.java`、`exception/UnsubscribeLinkInvalidException.java`、`exception/InvalidPreferenceRequestException.java`
- Test（`src/test/java/dev/patra/notification/domain/preference/` 下）: `model/vo/UnsubscribeTokenTest.java`、`model/vo/MaskedEmailTest.java`、`model/enums/UnsubscribeCategoryTest.java`、`model/aggregate/EmailPreferenceTest.java`

**Interfaces:**
- Produces:
  - `enum NotificationCategory { REPLY, MENTION, HELPFUL; static Optional<NotificationCategory> parse(String raw) }`（精确匹配名字，`null` / 不认识 → 空）
  - `enum EmailMode { OFF, INSTANT, DIGEST; static Optional<EmailMode> parse(String raw) }`
  - `enum UnsubscribeCategory { REPLY, MENTION, DIGEST, ALL; static Optional<UnsubscribeCategory> parse(String raw); boolean inLinks() }`
  - `record UnsubscribeToken(String value)`：`static UnsubscribeToken generate(SecureRandom)`、`static Optional<UnsubscribeToken> parse(String raw)`（先 `strip`）、`toString()` → `UnsubscribeToken[***]`；构造器只收合法值
  - `final class MaskedEmail { static String of(String email) }`
  - `final class EmailPreference`（Lombok `@Getter`：`getUserId()`、`getUnsubscribeToken()`、`getReply()`、`getMention()`）：`static createDefault(long userId, UnsubscribeToken token)`、`static restore(long userId, UnsubscribeToken token, EmailMode reply, EmailMode mention)`、`EmailMode modeOf(NotificationCategory)`、`void change(NotificationCategory, EmailMode)`、`List<NotificationCategory> unsubscribe(UnsubscribeCategory)`、`boolean isUnsubscribedFrom(UnsubscribeCategory)`、`boolean anyEmailOnAfter(UnsubscribeCategory)`
  - `enum PreferenceErrorTrait implements ErrorTrait { UNSUBSCRIBE_LINK_INVALID }`
  - `UnsubscribeLinkInvalidException()`：`StandardErrorTrait.NOT_FOUND` + `UNSUBSCRIBE_LINK_INVALID`，消息「退订链接无效」
  - `InvalidPreferenceRequestException(String message)`：`StandardErrorTrait.RULE_VIOLATION`

- [ ] **Step 1: 写 `UnsubscribeTokenTest`**

```java
@Test void generate_yields_43_base64url_chars()            // matches ^[A-Za-z0-9_-]{43}$，两次生成不同
@Test void parse_strips_whitespace_and_accepts_valid()     // parse("  " + v + "\n") → Optional.of(new UnsubscribeToken(v))
@ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"短", "42 个字符的串……", "44 个字符", "含+/=的 43 字符"})
void parse_rejects_malformed(String raw)                    // → Optional.empty()
@Test void constructor_rejects_malformed()                  // IllegalArgumentException
@Test void to_string_hides_value()                          // "UnsubscribeToken[***]"
```

- [ ] **Step 2: 写 `MaskedEmailTest`**：`of("chen.yu@example.com")` → `"c***@example.com"`；`of("a@b.cn")` → `"a***@b.cn"`。
- [ ] **Step 3: 写 `UnsubscribeCategoryTest`**：`parse("DIGEST")` 有值；`parse("digest")`、`parse("MEN")`、`parse(null)` 为空；`ALL.inLinks()` 为假，其余为真。
- [ ] **Step 4: 写 `EmailPreferenceTest`**（spec 5.1～5.3，每行一个用例）

```java
@Test void default_is_instant_for_reply_and_mention()
@Test void helpful_mode_is_always_off()
@Test void change_sets_only_that_category()                 // change(REPLY, DIGEST) → reply DIGEST, mention INSTANT
@Test void change_helpful_is_rejected()                     // InvalidPreferenceRequestException
@Test void reply_unsubscribe_turns_off_regardless_of_current_mode()   // reply DIGEST → unsubscribe(REPLY) == [REPLY], reply OFF
@Test void mention_unsubscribe_turns_off_only_mention()
@Test void digest_unsubscribe_turns_off_only_digest_categories()       // reply DIGEST, mention INSTANT → [REPLY]; mention 仍 INSTANT
@Test void all_unsubscribe_turns_off_both_in_order()        // → [REPLY, MENTION]
@Test void repeated_unsubscribe_returns_empty_and_keeps_state()
@Test void already_unsubscribed_follows_spec_5_3()          // REPLY: reply==OFF；DIGEST: 没有类别是 DIGEST
@Test void any_email_on_after_follows_spec_5_3()            // REPLY 看 mention；DIGEST 看有没有 INSTANT；ALL 恒假
```

- [ ] **Step 5: 跑测试确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-domain:test`，预期编译失败（类不存在）。
- [ ] **Step 6: 实现 Task 1 的全部类**。`unsubscribe` 返回的列表按 `REPLY`、`MENTION` 顺序，只含「从非 OFF 变成 OFF」的类别；异常照 interaction 的 `NotificationNotFoundException` / `InvalidNotificationRequestException` 写法。
- [ ] **Step 7: 跑测试确认通过**：同上命令，预期 PASS；再跑 `:patra-notification-domain:check`（R3 纯净）。

### Task 2: preference 存储（迁移、仓储、给 mail 的读端口）

**Files:**
- Create: `patra-notification-infra/src/main/resources/db/migration/preference/V20261031100000__create_preference_schema.sql`、`V20261031100100__create_preference_tables.sql`（表结构照 spec 第 11 节，加英文 `COMMENT`）
- Create: `domain/preference/port/repository/EmailPreferenceRepository.java`、`domain/preference/port/read/EmailSubscriptionReadPort.java`、`domain/preference/model/read/EmailSubscription.java`
- Create: `patra-notification-infra/src/main/java/dev/patra/notification/infra/preference/persistence/EmailPreferenceRepositoryAdapter.java`、`EmailSubscriptionReadAdapter.java`
- Modify: `patra-notification-infra/build.gradle.kts`（加 `api(project(":patra-api:patra-notification:patra-notification-domain"))`）
- Test: `patra-notification-infra/src/integrationTest/java/dev/patra/notification/infra/preference/persistence/EmailPreferenceRepositoryAdapterIT.java`、`EmailSubscriptionReadAdapterIT.java`

**Interfaces:**
- Consumes: Task 1 的 `EmailPreference`、`UnsubscribeToken`、`EmailMode`、`NotificationCategory`
- Produces:
  - `EmailPreferenceRepository`：`Optional<EmailPreference> findByUserId(long)`、`Optional<EmailPreference> findByToken(UnsubscribeToken)`、`Optional<EmailPreference> findByUserIdForUpdate(long)`、`Optional<EmailPreference> findByTokenForUpdate(UnsubscribeToken)`、`void insertIfAbsent(EmailPreference, Instant now)`、`void updateModes(EmailPreference, Instant now)`、`List<Long> findUsersAfter(long afterUserId, int limit)`、`int deleteByUserId(long)`
  - `EmailSubscriptionReadPort.subscriptionOf(long userId) → EmailSubscription`
  - `record EmailSubscription(long userId, EmailMode reply, EmailMode mention, String unsubscribeToken)`：`EmailMode modeOf(NotificationCategory)`（`HELPFUL` → `OFF`）、`toString()` 不含令牌

- [ ] **Step 1: 写 `EmailPreferenceRepositoryAdapterIT`**（`@SpringBootTest(classes = NotificationITBootstrap.class)` + `NotificationITPostgreSQLContainerInitializer`，照 `NotificationMigrationIT`；需要事务的用例用 `TransactionTemplate`）

```java
@Test void insert_if_absent_does_not_overwrite_existing_row()     // 先插 DIGEST/OFF，再插默认 → 仍 DIGEST/OFF、令牌不变
@Test void finds_by_user_and_by_token()
@Test void for_update_reads_require_transaction()                 // 事务外调 → IllegalTransactionStateException
@Test void update_modes_writes_both_modes_and_updated_at()
@Test void should_serialize_concurrent_changes_on_row_lock()      // 两个线程各开事务：一个改 REPLY、一个改 MENTION，都先 findByUserIdForUpdate；最终两项都生效
@Test void find_users_after_respects_cursor_and_limit()           // 用户 3、5、9：after 3 limit 1 → [5]
@Test void delete_by_user_id_returns_rows_and_frees_token()
@Test void constraints_reject_bad_mode_and_duplicate_token()      // 断言约束名 ck_email_preference_reply_mode、uk_email_preference_token
```

- [ ] **Step 2: 写 `EmailSubscriptionReadAdapterIT`**

```java
@Test void first_call_creates_default_row_with_valid_token()      // INSTANT/INSTANT，令牌匹配 43 位正则，表里 1 行
@Test void second_call_returns_same_token_and_current_modes()     // 中间把 mention 改成 OFF → 第二次 mention OFF、令牌相同
@Test void should_return_same_token_to_concurrent_first_callers() // 8 个线程同时对同一 userId 首次调用 → 令牌全相同、表里 1 行
```

- [ ] **Step 3: 跑确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-infra:integrationTest --tests "*preference*"`，预期编译失败。
- [ ] **Step 4: 写两个迁移脚本与两个适配器**。`*ForUpdate` 用 `SELECT … FOR UPDATE` 并标 `@Transactional(propagation = Propagation.MANDATORY)`；`insertIfAbsent` 是 `INSERT … ON CONFLICT (user_id) DO NOTHING`；`subscriptionOf` 先 `INSERT … ON CONFLICT DO NOTHING`（新令牌用适配器自持的 `SecureRandom`，时间取 `Clock`）再按 `user_id` 读回，不开事务；时间写 `instant.atOffset(ZoneOffset.UTC)`（同 `HelpfulRepositoryAdapter`）。
- [ ] **Step 5: 跑确认通过**：同上命令 PASS；`NotificationMigrationIT` 照常 PASS（白名单已含 `preference`）。

### Task 3: preference 用例（改档位、退订、查询）与 kernel 收件信息端口

**Files:**
- Create（`domain/kernel/` 下）: `model/enums/AccountState.java`（`ACTIVE, BANNED, PENDING_DELETION, DELETED`）、`model/read/Recipient.java`、`port/identity/RecipientPort.java`、`exception/IdentityUnavailableException.java`
- Create（`patra-notification-app/src/main/java/dev/patra/notification/app/preference/` 下）: `usecase/query/EmailPreferenceQueryService.java`、`usecase/query/EmailPreferenceView.java`、`usecase/query/UnsubscribeLinkView.java`、`usecase/change/ChangeEmailModeCommand.java`、`usecase/change/ChangeEmailModeHandler.java`、`usecase/unsubscribe/UnsubscribeCommand.java`、`usecase/unsubscribe/UnsubscribeHandler.java`、`usecase/unsubscribe/UnsubscribeResult.java`
- Modify: `patra-notification-app/build.gradle.kts`（`api(domain)`、`api(":patra-tool:patra-tool-cqrs")`、`api("org.springframework:spring-tx")`，同 interaction）
- Test: 对应 `src/test/java/...` 下 `EmailPreferenceQueryServiceTest`、`ChangeEmailModeHandlerTest`、`UnsubscribeHandlerTest`

**Interfaces:**
- Consumes: Task 1、Task 2 的全部类型；tool-core 的 `CurrentUserPort`
- Produces:
  - `@Builder record Recipient(long userId, String email, boolean emailVerified, AccountState state, String username, String displayName)`（`email`、`username`、`displayName` 可空）
  - `interface RecipientPort { int MAX_USERS = 100; Map<Long, Recipient> findAll(Collection<Long> userIds); default Optional<Recipient> find(long userId) }`
  - `IdentityUnavailableException(Throwable cause)`：`StandardErrorTrait.DEP_UNAVAILABLE`
  - `record EmailPreferenceView(EmailMode reply, EmailMode mention)`，`of(...)`、`static from(EmailPreference)`
  - `@Builder record UnsubscribeLinkView(String maskedEmail, UnsubscribeCategory category, EmailMode reply, EmailMode mention, boolean alreadyUnsubscribed, boolean othersEnabled)`
  - `@Builder record UnsubscribeResult(UnsubscribeCategory category, List<NotificationCategory> turnedOff, EmailMode reply, EmailMode mention, boolean othersEnabled)`
  - `EmailPreferenceQueryService`：`EmailPreferenceView mine()`、`UnsubscribeLinkView inspect(String token, String category)`
  - `record ChangeEmailModeCommand(String category, String mode) implements Command<EmailPreferenceView>`，`of(...)`
  - `record UnsubscribeCommand(String token, String category, boolean oneClick) implements Command<UnsubscribeResult>`：`static confirm(token, category)`、`static oneClick(token, category)`；`toString()` 不含令牌

- [ ] **Step 1: 写 `EmailPreferenceQueryServiceTest`**（mock 仓储、`RecipientPort`、`CurrentUserPort`）

```java
@Test void mine_requires_login()                                  // currentUser.require() 抛 → 原样传播
@Test void mine_returns_defaults_when_no_row()
@ParameterizedTest @MethodSource("malformedLinks")                // 令牌 null/少一位/带空白中间、类别 null/"MEN"/"mention"/"ALL"
void inspect_rejects_malformed_link_without_querying(...)         // UnsubscribeLinkInvalidException；verifyNoInteractions(repository, recipients)
@Test void inspect_rejects_unknown_token()
@Test void should_reject_link_when_recipient_missing_or_deleted() // find → empty / DELETED → 链接无效
@Test void inspect_propagates_identity_unavailable()             // IdentityUnavailableException 原样传播
@Test void inspect_returns_masked_email_and_flags()               // MENTION、mention OFF、reply INSTANT → alreadyUnsubscribed true、othersEnabled true、"c***@example.com"
@Test void inspect_accepts_banned_and_pending_deletion()
@Test void inspect_does_not_write()                               // verify 仓储只调 findByToken
```

- [ ] **Step 2: 写 `ChangeEmailModeHandlerTest`**（`TransactionOperations.withoutTransaction()`、固定 `Clock`）

```java
@Test void requires_login()
@ParameterizedTest @ValueSource(strings = {"HELPFUL", "reply", "X"}) void rejects_bad_category(String c)   // InvalidPreferenceRequestException，不碰仓储
@ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"off", "WEEKLY"}) void rejects_bad_mode(String m)
@Test void inserts_default_then_locks_and_updates()               // InOrder：insertIfAbsent → findByUserIdForUpdate → updateModes；返回 view
```

- [ ] **Step 3: 写 `UnsubscribeHandlerTest`**

```java
@ParameterizedTest @MethodSource("malformedLinks") void rejects_malformed_without_querying(...)
@Test void one_click_rejects_all()                                // oneClick(t, "ALL") → 链接无效
@Test void confirm_accepts_all()
@Test void unknown_token_is_invalid()                             // findByTokenForUpdate → empty
@Test void applies_unsubscribe_and_writes_when_changed()          // 结果字段照 spec 8.3
@Test void repeated_call_does_not_write_and_returns_empty_turned_off()
@Test void command_to_string_hides_token()
```

- [ ] **Step 4: 跑确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-app:test`。
- [ ] **Step 5: 实现 kernel 四个类与 app 的全部类**。`inspect` 的顺序：解析令牌与类别（`inLinks()`）→ `findByToken` → 事务外 `recipients.find(userId)` → 缺失、`DELETED` 或 `email == null` 为无效 → `MaskedEmail.of`。两个 Handler 各持 `new SecureRandom()`（同 identity 的 `RequestPasswordResetHandler`）。
- [ ] **Step 6: 跑确认通过**：同上命令 PASS。

### Task 4: identity 的收件信息内部接口

**Files:**
- Create: `patra-identity-api/src/main/java/dev/patra/identity/api/account/dto/UserRecipientsReq.java`、`UserRecipientsResp.java`、`UserRecipientResp.java`
- Modify: `patra-identity-api/.../endpoint/UserEndpoint.java`（加 `@PostExchange("/recipients") UserRecipientsResp recipients(@RequestBody UserRecipientsReq request)`，类注释的端点清单加一行）
- Create: `patra-identity-domain/.../account/model/read/UserRecipientReadModel.java`；Modify: `.../account/port/read/UserReadPort.java`（加 `List<UserRecipientReadModel> findRecipients(Collection<Long> userIds)`）
- Create: `patra-identity-app/.../account/usecase/query/UserRecipientQueryService.java`、`UserIdBatch.java`（`UserSummaryQueryService` 不动）
- Modify: `patra-identity-infra/.../account/persistence/UserReadAdapter.java`；`patra-identity-adapter/.../account/internal/UserEndpointImpl.java`
- Test: `UserIdBatchTest`、`UserRecipientQueryServiceTest`、`UserRecipientReadModelTest`、`UserReadAdapterIT` 新例、`UserEndpointImplIT` 新例；`UserSummaryQueryServiceTest` 保持通过

**Interfaces:**
- Produces:
  - `record UserRecipientsReq(List<Long> userIds)`（`of`）、`record UserRecipientsResp(List<UserRecipientResp> recipients)`（`of`，`copyOf`）、`@Builder record UserRecipientResp(Long userId, String email, boolean emailVerified, AccountStatus accountStatus, String username, String displayName)`
  - `@Builder record UserRecipientReadModel(long userId, String email, boolean emailVerified, UserStatus status, String username, String displayName)`（`displayName` 是资料里的昵称原值，可空）：`UserRecipientReadModel forOutput()`（`DELETED` → `email`、`username`、`displayName` 为 `null`、`emailVerified` 为 `false`；其余昵称为空时回退成用户名）
  - `final class UserIdBatch { static Set<Long> requested(List<Long> userIds) }`：`null`、超过 100、含 `null` 抛 `InvalidUserFieldsException`（沿用 `UserSummaryQueryService` 现有三条违规）；返回去重后保序的 `LinkedHashSet`
  - `UserRecipientQueryService.recipients(List<Long> userIds) → List<UserRecipientReadModel>`：按去重后的请求顺序，不认识的省略并 `WARN`

- [ ] **Step 1: 写 `UserIdBatchTest`**（三条违规与去重保序），**跑** `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*UserIdBatch*"` 确认失败，**实现**后通过。`UserSummaryQueryService` 不动（identity 只增不改）。
- [ ] **Step 2: 写 `UserRecipientReadModelTest`**：`ACTIVE` 原样、昵称为空回退成用户名；`DELETED`（库里用户名仍在）→ `forOutput()` 的 `username`、`displayName`、`email` 为 `null`、`emailVerified` 为 `false`。
- [ ] **Step 3: 写 `UserRecipientQueryServiceTest`**：顺序与去重、不认识的省略、101 个 → `InvalidUserFieldsException`、空列表不查库、输出已经过 `forOutput()`。
- [ ] **Step 4: 写 `UserReadAdapterIT.should_read_recipients`**：正常（邮箱、已验证、用户名）、未验证、已注销（`email` 为 `NULL`）三个用户与一个不存在的 ID → 三条，字段与库一致；空集合返回空。
- [ ] **Step 5: 写 `UserEndpointImplIT.should_return_recipients`**：请求 `{"userIds":[42,43]}` → 响应 `recipients[0].userId` 是字符串 `"42"`、`accountStatus` 是名字、已注销的 `email` 为 `null`。
- [ ] **Step 6: 跑确认失败**：`./gradlew :patra-api:patra-identity:patra-identity-app:test :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*Recipient*" --tests "*UserReadAdapterIT*" --tests "*UserEndpointImplIT*"`。
- [ ] **Step 7: 实现**：读适配器用 `userDao.findAllById` 映射，昵称取资料行（照 `summariesOf` 的取法，资料行一次 `IN` 取齐；已验证看 `email_verified_at` 是否非空）；`UserEndpointImpl.recipients` 映射 `forOutput()` 后的结果。
- [ ] **Step 8: 跑确认通过**：同上命令 PASS。

### Task 5: notification 的收件信息适配器

**Files:**
- Create: `patra-notification-infra/src/main/java/dev/patra/notification/infra/kernel/identity/RecipientAdapter.java`
- Modify: `patra-notification-infra/build.gradle.kts`（`implementation(project(":patra-api:patra-identity:patra-identity-api"))`、`implementation(project(":patra-tool:patra-tool-http-client"))`）
- Test: `patra-notification-infra/src/test/java/dev/patra/notification/infra/kernel/identity/RecipientAdapterTest.java`

**Interfaces:**
- Consumes: Task 3 的 `RecipientPort`、`Recipient`、`AccountState`、`IdentityUnavailableException`；Task 4 的 `UserEndpoint.recipients`
- Produces: `RecipientAdapter implements RecipientPort`（`@Component`）

- [ ] **Step 1: 写 `RecipientAdapterTest`**（照 interaction 的 `UserDirectoryAdapterTest`）：映射五个字段与状态；`RemoteCallException`、`ResourceAccessException`、`IllegalStateException` 都翻成 `IdentityUnavailableException`；空集合或 101 个 → `IllegalArgumentException`；`find(42)` 走 `findAll(List.of(42))`。
- [ ] **Step 2: 跑确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-infra:test --tests "*RecipientAdapter*"`。
- [ ] **Step 3: 实现**，结构照 `UserDirectoryAdapter`（`try` 只包一次代理调用，日志不打邮箱）。
- [ ] **Step 4: 跑确认通过**。

### Task 6: REST 接口、boot 装配与流程测试

**Files:**
- Create（`patra-notification-adapter/src/main/java/dev/patra/notification/adapter/preference/rest/` 下）: `MyEmailPreferenceController.java`、`UnsubscribeController.java`、`request/ChangeEmailModeRequest.java`、`request/UnsubscribeRequest.java`、`response/EmailPreferenceResponse.java`、`response/UnsubscribeLinkResponse.java`、`response/UnsubscribeResponse.java`
- Modify: `patra-notification-adapter/build.gradle.kts`（`api(app)`、`api(":patra-tool:patra-tool-web")`）
- Create: `patra-notification-boot/src/main/java/dev/patra/notification/config/IdentityClientConfiguration.java`（照 interaction，只建 `UserEndpoint` 代理）
- Modify: `patra-notification-boot/build.gradle.kts`（`patra-tool-security`、`patra-tool-http-client`、`patra-identity-api`；`testImplementation(testFixtures(project(":patra-tool:patra-tool-security")))`；删掉 domain、app 两行 `testImplementation`，api 那行保留）
- Modify: `patra-notification-boot/src/main/resources/application.yml`（`patra.http-client.http-interface.groups.identity`：`lb://patra-identity`、连接 1s、读 2s，注释同 interaction）
- Create（`patra-notification-boot/src/integrationTest/java/dev/patra/notification/` 下）: `support/NotificationFlowITSupport.java`、`preference/PreferenceFlowIT.java`、`preference/UnsubscribeFlowIT.java`
- Test: `NotificationArchitectureTest`、`ApplicationConfigTest`、`PatraNotificationApplicationIT` 保持通过

**Interfaces:**
- Consumes: Task 3 的 Query/Command/View/Result；Task 5 的适配器（经容器）
- Produces（HTTP，spec 第 8 节）：
  - `GET /me/notification-preferences` → `EmailPreferenceResponse(EmailMode reply, EmailMode mention)`
  - `PUT /me/notification-preferences/{category}`，体 `ChangeEmailModeRequest(String mode)` → `EmailPreferenceResponse`
  - `GET /unsubscribe?token&category`（都 `required = false`，缺失交给用例判无效）→ `UnsubscribeLinkResponse`（字段同 `UnsubscribeLinkView`）
  - `POST /unsubscribe`，体 `UnsubscribeRequest(String token, String category)`（`toString` 不含令牌）→ `UnsubscribeResponse`（字段同 `UnsubscribeResult`）
  - `POST /unsubscribe/one-click?token&category`，不声明请求体与 `consumes` → `UnsubscribeResponse`

- [ ] **Step 1: 写 `NotificationFlowITSupport`**：照 `InteractionFlowITSupport`（RANDOM_PORT、PG 初始化器、`test` profile、`@AutoConfigureRestTestClient`、每例 `TRUNCATE` 子域表）；identity 用一个 JVM 级 WireMock 经 `spring.cloud.discovery.client.simple.instances.patra-identity[0].uri` 接上；给出 `stubRecipient(long userId, String email, String status)`（按请求体里的 ID 回 `recipients`）、`identityDown()`（503）、`call(method, user, uri, body)`、`form(uri, body)`（`application/x-www-form-urlencoded`）。
- [ ] **Step 2: 写 `PreferenceFlowIT`**

```java
@Test void anonymous_read_and_write_are_rejected()          // GET / PUT 不带断言 → 401
@Test void new_user_gets_defaults_without_row()             // {"reply":"INSTANT","mention":"INSTANT"}；表里 0 行
@Test void put_saves_one_category_and_get_reflects_it()     // PUT REPLY DIGEST → 两项；再 GET 一致；表里 1 行、令牌 43 位
@Test void put_one_category_keeps_the_other()               // 先 MENTION OFF，再 REPLY DIGEST → mention 仍 OFF
@Test void put_helpful_or_bad_mode_is_422()                 // code NOTIFICATION-0422
```

- [ ] **Step 3: 写 `UnsubscribeFlowIT`**（偏好行用 `PUT` 或直接调 `EmailSubscriptionReadPort` 建出，令牌从库里读）

```java
@Test void reading_link_does_not_change_anything()          // 读两次：200、maskedEmail、两个标志；档位与 updated_at 不变
@Test void one_click_works_anonymously_and_is_idempotent()  // form 体 List-Unsubscribe=One-Click、不带断言；两次都 200，第二次 turnedOff 为 []
@Test void one_click_without_body_also_works()
@Test void confirm_reply_turns_off_only_reply()
@Test void confirm_digest_turns_off_only_digest_categories()
@Test void confirm_all_turns_off_everything()               // othersEnabled false
@Test void invalid_links_are_404_with_trait()               // 缺令牌、错一位、库里没有、类别错、一键退订带 ALL、账号 DELETED（读接口）→ 404 + traits 含 UNSUBSCRIBE_LINK_INVALID
@Test void read_is_503_when_identity_down_but_write_still_works()
@Test void get_on_one_click_is_405()
```

- [ ] **Step 4: 跑确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-boot:integrationTest`。
- [ ] **Step 5: 实现控制器、请求 / 响应 record、`IdentityClientConfiguration`、构建脚本与 `application.yml`**。响应 record 带 `static from(...)`（同 interaction 的 `UnreadCountsResponse`）；控制器类注释写明哪些免登录。
- [ ] **Step 6: 跑确认通过**：同上命令 PASS；再跑 `./gradlew :patra-api:patra-notification:patra-notification-boot:test`（架构测试、配置测试）PASS。

### Task 7: 注销清理扩展点与 preference 的实现

**Files:**
- Create: `patra-notification-domain/.../domain/kernel/port/gateway/UserDataCleanupGateway.java`（照 interaction 同名接口：`String subdomain()`、`List<Long> findUsersAfter(long afterUserId, int limit)`、`int deleteAllOf(long userId)`，注释改成 notification 的说法）
- Create: `patra-notification-app/.../app/preference/service/PreferenceUserDataCleanupGatewayImpl.java`
- Test: `patra-notification-app/src/test/java/.../app/preference/service/PreferenceUserDataCleanupGatewayImplTest.java`

- [ ] **Step 1: 写测试**：`subdomain()` 为 `"preference"`；`findUsersAfter(3, 100)` 转调 `findUsersAfter(3, 100)`；`deleteAllOf(7)` 返回 `deleteByUserId(7)` 的行数。
- [ ] **Step 2: 跑确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-app:test --tests "*Cleanup*"`。
- [ ] **Step 3: 实现**；**Step 4: 跑确认通过**。
- [ ] **Step 5（10/31 定为本 Issue 建，查状态走 `/summaries`，kernel 新增 `AccountStatePort` / `AccountStateAdapter`）**：照 interaction 搬 `CleanupDeletedUsersCommand` / `CleanupResult` / `CleanupDeletedUsersHandler`（`app.kernel.usecase.cleanup`，批量取状态用 `AccountStatePort.findStates`，`DELETED` 才删）与 `DeletedUserCleanupScheduleJob`（`adapter.kernel.scheduler`，handler `deletedUserCleanupJob`；adapter 加 `api(libs.xxl.job)`），先写 `CleanupDeletedUsersHandlerTest`（照 interaction 的同名测试）与 boot 的 `DeletedUserCleanupFlowIT`（偏好行的已注销用户被删、正常用户保留），红 → 绿。

### Task 8: 网关集成测试补一例

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/.../GatewayRoutingIT.java`

- [ ] **Step 1: 写 `should_forward_anonymous_one_click_unsubscribe_without_redirect`**：`notification` 桩 `post(urlPathEqualTo("/unsubscribe/one-click"))` 回 `okJson("{}")`；不带 `Authorization` 发 `POST /patra-notification/unsubscribe/one-click?token=abc&category=REPLY`、体 `List-Unsubscribe=One-Click`；断言 200、无 `Location`；`notification.verify(postRequestedFor(...).withQueryParam("token", equalTo("abc")).withoutHeader("Authorization").withRequestBody(equalTo("List-Unsubscribe=One-Click")))`。
- [ ] **Step 2: 跑**：`./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayRoutingIT*"`。现有放行规则下应直接通过（守护测试：以后若有人给 notification 路径加登录要求，这里会红）；在汇报里如实写「未经历红灯」。

### Task 9: 文档与模块图

**Files:**
- Modify: `patra-api/patra-notification/README.md`（preference 子域：表、令牌、接口一览指向 spec；kernel 新增三样；子域依赖不变）
- Modify: `docs/patra/runbooks/v0.16-notification-go-live-runbook.md` 第 7 步加「PAP-142」小节（spec 第 17 节的核对 1～6，第 7 条视第 14 节的决定）
- Modify: `docs/patra/specs/2026-10-31-notification-preference-unsubscribe-design.md` 状态行与实施偏离
- Modify: `patra-infra/cd/module-graph.json`（`./gradlew dumpModuleGraph` 重新生成：notification 多了对 identity-api、tool-security、tool-http-client、tool-cqrs 的依赖）

- [ ] **Step 1: 改三份文档**。
- [ ] **Step 2: `./gradlew dumpModuleGraph`**，`git diff --stat patra-infra/cd/module-graph.json` 只动 notification 相关的依赖与 `impacts`。

### Task 10: 全量门禁

- [ ] **Step 1:** `./gradlew spotlessApply && ./gradlew check`，预期 BUILD SUCCESSFUL（含 R1～R6、架构测试）。
- [ ] **Step 2:** `./gradlew :patra-api:patra-notification:patra-notification-infra:integrationTest :patra-api:patra-notification:patra-notification-boot:integrationTest :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest :patra-api:patra-gateway-boot:integrationTest`，预期全部 PASS（本机负载高时网关慢客户端用例可能抖，按计划缺陷清单单独重跑并在汇报里写明）。
- [ ] **Step 3:** `git status` 与 `git diff --stat`，确认只动了本 Issue 的路径。

### Task 11: 汇报与提交

- [ ] **Step 1:** 向编排会话汇报（做了什么、AC 逐条对照、测试结果、上线步骤、留给后续 Issue 的事、改了 `*.gradle.kts` 要在 IDEA 点加载）。
- [ ] **Step 2:** 得到同意后提交：`git add` 只加本 Issue 的路径，`git diff --cached --stat` 核对后 `git commit`，message 如 `feat(notification): 新增提醒偏好与一键退订 (PAP-142)`，结尾带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`；spotless 改了文件就 `git add -u` 重提。不 push。
