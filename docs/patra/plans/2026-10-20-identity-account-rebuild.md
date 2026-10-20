# identity 账号重建实施计划（PAP-93）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 identity 的账号模型一次改成最终形态——用户名、昵称、邮箱验证状态、四态状态机、改名规则——重写 Flyway `V1`，改造注册 / 登录 / `/auth/me`，加 `PATCH /me/username` 与 `GET /users/{username}`，补网关规则。

**Architecture:** `User` 聚合持有用户名、邮箱（`DELETED` 时为空）、验证时间和四态；昵称在独立聚合 `UserProfile`（本 Issue 只有 `display_name`）；改名留下的旧名是独立聚合 `FormerUsername`，按时间判断保留期。规则对象 `UsernamePolicy` / `DisplayNamePolicy` / `UsernameChangePolicy` 装成 Bean。登录、注册、`/auth/me` 共用读模型 `UserAccountReadModel`，响应结构 `{ sessionToken, user }`。409 的原因码：唯一性冲突走 `errors[]`，状态冲突走 `traits[]`。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Data JPA + Flyway、PostgreSQL 17 与 Redis 7.0.15 的 Testcontainers、Lombok、MapStruct、JUnit 5 + AssertJ + Mockito。

**Spec:** `docs/patra/specs/2026-10-20-identity-account-rebuild-design.md`

## Global Constraints

- 全部工作在主检出 `/Users/linqibin/Projects/Products/patra` 的分支 `feat/v0.12-user-center-api` 上，不建 worktree、不切分支、不 push。命令在仓库根目录执行。
- 用户名：`^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$`、3 ~ 30 个字符、不纯数字、输入去首尾空白后按 `Locale.ROOT` 转小写；保留字匹配先同形归一（`0→o`、`1→i`、`3→e`、`5→s`）再去掉 `_` `-`，整词比较；原因码顺序 `REQUIRED` → `INVALID_CHARACTER` → `TOO_SHORT` → `TOO_LONG` → `INVALID_FORMAT` → `DIGITS_ONLY` → `RESERVED`，只报第一条（spec §7.3）。
- 昵称：NFC、去首尾空白、空格类字符折叠成一个 U+0020；2 ~ 30 个字素（`BreakIterator.getCharacterInstance(Locale.ROOT)`）且 UTF-8 ≤ 120 字节；原因码顺序 `REQUIRED` → `INVALID_CHARACTER` → `TOO_SHORT` → `TOO_LONG` → `INVALID_FORMAT` → `RESERVED`（spec §7.4）。
- 改名：冷却 `30d`、旧名保留 `90d`，配置键 `patra.identity.username.change-cooldown` / `reservation-period`（spec §11）。
- 状态：`ACTIVE`、`BANNED`、`PENDING_DELETION`、`DELETED`；迁移表见 spec §7.2；`DELETED` 时 `email`、`email_verified_at`、`deletion_due_at`、`banned_at` 全空、`deleted_at` 非空。
- 错误：`UserIdentifierTakenException` 409 + `errors[]`（`email` / `ALREADY_REGISTERED`、`username` / `ALREADY_TAKEN`）；`UsernameChangeCooldownException` 429 + `Retry-After`；`UserPendingDeletionException` 409 + `traits` 含 `PENDING_DELETION`；`InvalidUserStateTransitionException` 409；公开身份卡 404 用 `UserNotFoundException`；每个异常只放一个标准特征（spec §10）。
- 响应：登录 / 注册 `{ sessionToken, user }`，`user` 与 `/auth/me` 同构：`userId`（字符串）、`email`、`emailVerified`、`username`、`displayName`（昵称为空时回退成用户名，永远非空）、`avatarUrl`（恒 `null`）、`status`、`deletionDueAt`、`nextUsernameChangeAt`、`accountType`（spec §8.1）。`GET /users/{username}` 的 200 带 `audience`（`PUBLIC` / `SELF`，本人看自己的主页不受 404 规则约束），301 带 `Location: /users/<新名>` 和 JSON 体 `{ "username" }`（spec §8.5）。
- 代码规范：Google Java 格式（spotless）、`///` Javadoc 且每行不超过 100 列（gjf 会把超长的 `///` 折成 `//`）、不用全类名、record ≤ 4 个参数用 `of()`、≥ 5 个用 `@Builder`，集合用 `List.of` / `List.copyOf`。
- 测试规范：单测在 `src/test`，集成测试在 `src/integrationTest`，方法名 snake_case `should_…`；`./gradlew check` 不含 `integrationTest`，两个都要跑；Testcontainers 要本机 Docker（OrbStack）在线；Gradle 不加 `-q`；跑 `check` 前先 `./gradlew spotlessApply`。
- 写 Entity / Dao / JpaMapper / RepositoryAdapter 的任务（Task 5、6、7）开工前加载 `patra-backend:patra-jpa` 技能。
- 提交：每个任务至少一次提交，格式 `type(scope): 中文 subject (PAP-93)`，subject 用中文动词起头，结尾加 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`；只 `git add` 本任务的路径，提交前看 `git diff --cached --stat`（工作区可能有别的会话的改动）。
- Mockito 不嵌套 stub（`when(...)` 的参数里不再调 mock）。
- 新的 `@SpringBootTest` 尽量复用已有的注解组合，让 Spring 复用上下文；每多一个上下文就多一份 Hikari 池（CI 上才会报 too many clients）。

## Review Focus

1. 只由组合用字符组成的昵称（如 `"́́"`）渲染出来是空白，但字素数是 1 ~ 2：要按 `INVALID_CHARACTER` 拒绝——Task 3 的 `should_reject_name_made_of_combining_marks_only`。
2. 同一个旧名先后属于两个人（A 的记录已过期，B 的记录未过期）：查保留和跳转都要取 B——Task 6 的 `should_return_latest_unexpired_record_when_same_name_reused`。
3. 用户连续改名 A → B → C：`/users/a` 要 301 到 `c`，不是中间名 `b`——Task 9 的 `should_redirect_to_current_name_after_two_renames`。
4. 路径里的用户名带大写或首尾空白（`/users/Chen-Yu`）：折成小写后查到同一个人；`/users/Chen Yu` 这种不合法的直接 404 不查库——Task 9 的 `should_fold_path_username_to_lowercase`、`should_return_404_for_syntactically_invalid_username_without_querying`。
5. 会话还在 60 秒断言窗口内而用户已被封禁或注销：`PATCH /me/username` 要 401 而不是 409 或 500——Task 10 的 `should_return_401_when_user_is_banned_or_deleted`。

---

### Task 1: 可拨动的测试时钟 `MutableClock`

**Files:**
- Create: `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/clock/MutableClock.java`
- Test: `patra-tool/patra-tool-test/src/test/java/dev/patra/tool/test/clock/MutableClockTest.java`

**Interfaces:**
- Produces: `public final class MutableClock extends Clock`：`static MutableClock startingAt(Instant start)`、`void advance(Duration duration)`、`void set(Instant instant)`、`Instant instant()`、`ZoneId getZone()` 恒 `ZoneOffset.UTC`、`Clock withZone(ZoneId)` 返回自己。Task 6、9、10 的集成测试把它声明成 `@Primary` Bean（`LaunchAutoConfiguration` 的 `Clock` 是 `@ConditionalOnMissingBean`）。

- [ ] **Step 1: 写失败的测试**

```java
@DisplayName("MutableClock 单元测试")
class MutableClockTest {
  private static final Instant START = Instant.parse("2026-10-20T08:00:00Z");

  @Test
  void should_start_at_given_instant_in_utc() {
    MutableClock clock = MutableClock.startingAt(START);
    assertThat(clock.instant()).isEqualTo(START);
    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    assertThat(clock.withZone(ZoneId.of("Asia/Shanghai"))).isSameAs(clock);
  }

  @Test
  void should_advance_and_set() {
    MutableClock clock = MutableClock.startingAt(START);
    clock.advance(Duration.ofDays(30));
    assertThat(clock.instant()).isEqualTo(START.plus(Duration.ofDays(30)));
    clock.set(START);
    assertThat(clock.instant()).isEqualTo(START);
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-tool:patra-tool-test:test --tests "*MutableClockTest*"`
Expected: FAIL，编译错误 `cannot find symbol: class MutableClock`

- [ ] **Step 3: 实现 `MutableClock`**

照 `patra-identity-session` 集成测试里的 `AdjustableClock` 写法，加 `set` 与静态工厂；`advance` 对 `null` 抛 `NullPointerException`。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-tool:patra-tool-test:test --tests "*MutableClockTest*"`
Expected: PASS，`BUILD SUCCESSFUL`

- [ ] **Step 5: 提交**

```bash
git add patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/clock patra-tool/patra-tool-test/src/test/java/dev/patra/tool/test/clock
git commit -m "feat(tool-test): 加可拨动的测试时钟 MutableClock (PAP-93)"
```

---

### Task 2: `Username` 值对象、`UsernamePolicy` 与保留字

**Files:**
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/Username.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/UsernamePolicy.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/ReservedUsernames.java`
- Modify: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/UserFieldViolations.java`
- Test: `…/patra-identity-domain/src/test/java/dev/patra/identity/domain/model/vo/UsernameTest.java`、`…/domain/policy/UsernamePolicyTest.java`、`…/domain/policy/ReservedUsernamesTest.java`

**Interfaces:**
- Consumes: `FieldViolation.of(field, code, message)`、`InvalidUserFieldsException(List<FieldViolation>)`。
- Produces:
  - `record Username(String value)`：常量 `MIN_LENGTH = 3`、`MAX_LENGTH = 30`；`static String normalize(String raw)`（`strip()` + `toLowerCase(Locale.ROOT)`）；`static Optional<FieldViolation> violation(String raw)`（spec §7.3 的前六条，不含保留字）；`static Username of(String raw)`（有错抛 `InvalidUserFieldsException`）；紧凑构造器只接受已规范化且 `violation` 为空的值，否则 `IllegalArgumentException`。
  - `final class UsernamePolicy`：`Optional<FieldViolation> validate(String raw)` = `Username.violation(raw)`，为空再查 `ReservedUsernames.contains(Username.normalize(raw))` → `usernameReserved()`。
  - `final class ReservedUsernames`：`static String fold(String normalized)`（同形归一 + 去 `_` `-`）；`static boolean contains(String normalized)`；三组清单按 spec §7.3 原样写成 `Set<String>` 常量（存折叠后的形式）。
  - `UserFieldViolations` 新增：字段名 `USERNAME = "username"`、`DISPLAY_NAME = "displayName"`；原因码 `DIGITS_ONLY`、`RESERVED`、`UNCHANGED`、`ALREADY_TAKEN`、`ALREADY_REGISTERED`；方法 `usernameRequired()`、`usernameInvalidCharacter()`、`usernameTooShort()`、`usernameTooLong()`、`usernameInvalidFormat()`、`usernameDigitsOnly()`、`usernameReserved()`、`usernameUnchanged()`、`usernameAlreadyTaken()`、`emailAlreadyRegistered()`。文案：「请输入用户名」「用户名只能包含小写字母、数字、_ 和 -」「用户名至少 3 个字符」「用户名最长 30 个字符」「用户名首尾须是字母或数字，_ 和 - 不能连续」「用户名不能全是数字」「这个用户名不能使用」「新用户名与当前相同」「该用户名已被使用」「该邮箱已注册」。

- [ ] **Step 1: 写失败的测试**

`UsernamePolicyTest`（AC 1 的全部边界）：

```java
@DisplayName("UsernamePolicy 单元测试")
class UsernamePolicyTest {
  private final UsernamePolicy policy = new UsernamePolicy();

  static Stream<Arguments> rejected() {
    return Stream.of(
        Arguments.of(null, "REQUIRED"), Arguments.of("   ", "REQUIRED"),
        Arguments.of("陈宇", "INVALID_CHARACTER"), Arguments.of("chen yu", "INVALID_CHARACTER"),
        Arguments.of("chen.yu", "INVALID_CHARACTER"), Arguments.of("chen@yu", "INVALID_CHARACTER"),
        Arguments.of("ab", "TOO_SHORT"), Arguments.of("a".repeat(31), "TOO_LONG"),
        Arguments.of("_abc", "INVALID_FORMAT"), Arguments.of("abc-", "INVALID_FORMAT"),
        Arguments.of("a__b", "INVALID_FORMAT"), Arguments.of("a--b", "INVALID_FORMAT"),
        Arguments.of("a_-b", "INVALID_FORMAT"), Arguments.of("123456", "DIGITS_ONLY"),
        Arguments.of("admin", "RESERVED"), Arguments.of("ADMIN", "RESERVED"),
        Arguments.of("adm1n", "RESERVED"), Arguments.of("0fficial", "RESERVED"),
        Arguments.of("ncb1", "RESERVED"), Arguments.of("ad-min", "RESERVED"),
        Arguments.of("a_d_m_i_n", "RESERVED"), Arguments.of("patra", "RESERVED"),
        Arguments.of("settings", "RESERVED"), Arguments.of("deleted-user", "RESERVED"));
  }

  @ParameterizedTest
  @MethodSource("rejected")
  void should_report_first_violation(String raw, String code) {
    assertThat(policy.validate(raw)).hasValueSatisfying(v -> {
      assertThat(v.field()).isEqualTo("username");
      assertThat(v.code()).isEqualTo(code);
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "a".repeat(30), "chen-yu_01", "Chen_Yu", "admin2", "hello", "patra-dev", " chen_yu "})
  void should_accept_valid_usernames(String raw) {
    assertThat(policy.validate(raw)).isEmpty();
  }
}
```

`UsernameTest`：`Username.of(" Chen_Yu ")` 的 `value()` 是 `chen_yu`；`Username.of("ab")` 抛 `InvalidUserFieldsException` 且 `errors` 只有一条 `TOO_SHORT`；`new Username("Chen")` 和 `new Username("ab")` 抛 `IllegalArgumentException`；`Username.of("admin")` 不抛（保留字不在值对象里判）。

`ReservedUsernamesTest`：`fold("Ad_m-1n".toLowerCase())` 是 `admin`；`fold("hello")` 是 `hello`（`l` 不归一）；`contains("deleteduser")` 为真；三组各抽一个 `contains` 为真。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*UsernamePolicyTest*" --tests "*UsernameTest*" --tests "*ReservedUsernamesTest*"`
Expected: FAIL，编译错误 `cannot find symbol`

- [ ] **Step 3: 实现三个类并扩展 `UserFieldViolations`**

`Username.violation` 的顺序：`null` / 空白 → `REQUIRED`；规范化后任一字符不在 `[a-z0-9_-]` → `INVALID_CHARACTER`；长度 < 3 / > 30；不匹配 `^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$` → `INVALID_FORMAT`；`^[0-9]+$` → `DIGITS_ONLY`。`ReservedUsernames.fold` 用 `String.replace` 做四个映射再去掉 `_` `-`。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/Username.java patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/UserFieldViolations.java patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/UsernamePolicy.java patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/ReservedUsernames.java patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/model/vo/UsernameTest.java patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/policy
git commit -m "feat(identity): 加用户名值对象、规则与保留字清单 (PAP-93)"
```

---

### Task 3: `DisplayName` 值对象、`DisplayNamePolicy` 与拒绝词

**Files:**
- Create: `…/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/DisplayName.java`
- Create: `…/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/DisplayNamePolicy.java`
- Create: `…/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/RejectedDisplayNameWords.java`
- Modify: `…/domain/model/vo/UserFieldViolations.java`（昵称的六个方法）
- Test: `…/patra-identity-domain/src/test/java/dev/patra/identity/domain/model/vo/DisplayNameTest.java`、`…/domain/policy/DisplayNamePolicyTest.java`

**Interfaces:**
- Consumes: Task 2 的 `ReservedUsernames.fold`。
- Produces:
  - `record DisplayName(String value)`：`static String normalize(String raw)`（`Normalizer.normalize(raw, NFC)` → `strip()` → 连续的 `Character.isSpaceChar` 码点折成一个 U+0020）；紧凑构造器要求 `value` 非空且等于 `normalize(value)`。没有「默认值」工厂：昵称为空时由读模型回退成用户名（spec §7.4）。
  - `final class DisplayNamePolicy`：常量 `MIN_GRAPHEMES = 2`、`MAX_GRAPHEMES = 30`、`MAX_BYTES = 120`；`Optional<FieldViolation> validate(String raw)`（内部先 `normalize`）；`DisplayName parse(String raw)`（有错抛 `InvalidUserFieldsException`）；`static int graphemeCount(String normalized)`。
  - `final class RejectedDisplayNameWords`：`static boolean matches(String normalized)`。中文词按包含匹配（NFKC、去掉空白与 `·` 之后）；拉丁词按整词匹配（NFKC 小写后用 `[^\p{L}\p{N}]+` 切词，每个词经 `ReservedUsernames.fold`）；两组清单按 spec §7.4 原样写，第三组复用 `ReservedUsernames` 的站名与数据源名。
  - `UserFieldViolations` 新增 `displayNameRequired()`、`displayNameInvalidCharacter()`、`displayNameTooShort()`、`displayNameTooLong()`、`displayNameInvalidFormat()`、`displayNameReserved()`，字段 `displayName`。文案：「请输入昵称」「昵称包含不允许的字符」「昵称至少 2 个字」「昵称最长 30 个字」「昵称不能是网址、邮箱、手机号或微信号」「这个昵称不能使用」。

- [ ] **Step 1: 写失败的测试**

`DisplayNamePolicyTest`（AC 2 的全部边界）：

```java
@DisplayName("DisplayNamePolicy 单元测试")
class DisplayNamePolicyTest {
  private static final String FAMILY = "👨‍👩‍👧"; // 👨‍👩‍👧，18 字节
  private static final String FLAG_ENGLAND = "🏴󠁧󠁢󠁥󠁮󠁧󠁿";
  private final DisplayNamePolicy policy = new DisplayNamePolicy();

  static Stream<Arguments> rejected() {
    return Stream.of(
        Arguments.of(null, "REQUIRED"), Arguments.of("  　 ", "REQUIRED"),
        Arguments.of("a\tb", "INVALID_CHARACTER"), Arguments.of("a\nb", "INVALID_CHARACTER"),
        Arguments.of("a​b", "INVALID_CHARACTER"), Arguments.of("a‍b", "INVALID_CHARACTER"),
        Arguments.of("a‮b", "INVALID_CHARACTER"), Arguments.of("a﻿b", "INVALID_CHARACTER"),
        Arguments.of("a­b", "INVALID_CHARACTER"), Arguments.of("a b", "INVALID_CHARACTER"),
        Arguments.of("́́", "INVALID_CHARACTER"),
        Arguments.of("张", "TOO_SHORT"), Arguments.of(FAMILY, "TOO_SHORT"),
        Arguments.of("汉".repeat(31), "TOO_LONG"), Arguments.of("汉".repeat(41), "TOO_LONG"),
        Arguments.of(FAMILY.repeat(7), "TOO_LONG"),
        Arguments.of("patra.dev", "INVALID_FORMAT"), Arguments.of("https://x", "INVALID_FORMAT"),
        Arguments.of("www.x", "INVALID_FORMAT"), Arguments.of("a@b.cn", "INVALID_FORMAT"),
        Arguments.of("13800138000", "INVALID_FORMAT"), Arguments.of("138 1234 5678", "INVALID_FORMAT"),
        Arguments.of("１３８００１３８０００", "INVALID_FORMAT"), Arguments.of("加微信abc", "INVALID_FORMAT"),
        Arguments.of("wx: abc123", "INVALID_FORMAT"), Arguments.of("vx：abc", "INVALID_FORMAT"),
        Arguments.of("张医生", "RESERVED"), Arguments.of("Dr. Wang", "RESERVED"),
        Arguments.of("0fficial", "RESERVED"), Arguments.of("Patra 官方", "RESERVED"),
        Arguments.of("admin", "RESERVED"), Arguments.of("王 教 授", "RESERVED"));
  }

  @ParameterizedTest
  @MethodSource("rejected")
  void should_report_first_violation(String raw, String code) {
    assertThat(policy.validate(raw)).hasValueSatisfying(v -> {
      assertThat(v.field()).isEqualTo("displayName");
      assertThat(v.code()).isEqualTo(code);
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"张三", "Chen Yu", "张三·李四", "医学生", "Andrea Wang", "J.R.R. Tolkien", "🇨🇳🇨🇳", "👍🏽👍🏽"})
  void should_accept_valid_names(String raw) {
    assertThat(policy.validate(raw)).isEmpty();
  }

  @Test
  void should_count_graphemes_and_bytes_at_the_boundary() {
    assertThat(policy.validate("汉".repeat(30))).isEmpty();              // 30 字素、90 字节
    assertThat(policy.validate(FAMILY.repeat(6))).isEmpty();            // 6 字素、108 字节
    assertThat(policy.validate(FAMILY + FAMILY)).isEmpty();             // ZWJ 序列整体算一个字素
    assertThat(policy.validate(FLAG_ENGLAND + FLAG_ENGLAND)).isEmpty(); // 标签字符是 emoji 组件
  }
}
```

字素上限和字节上限分别由参数表里的 `"汉".repeat(31)`（31 字素）、`"汉".repeat(41)`（123 字节）和 `FAMILY.repeat(7)`（7 字素但 126 字节）压住。

再加：`should_normalize_to_nfc_and_collapse_spaces`：`policy.parse("  陈　　宇  ")` 的值是 `"陈 宇"`；`policy.parse("éé")` 的值是 `"éé"`（NFC）。`should_reject_name_made_of_combining_marks_only` 已在参数表里（`"́́"`）。

`DisplayNameTest`：`new DisplayName("陈 宇")` 合法；`new DisplayName(" a ")`、`new DisplayName("")` 抛 `IllegalArgumentException`；`new DisplayNamePolicy().validate("weixin123")` 是 `INVALID_FORMAT`（合法用户名不一定是合法昵称，所以昵称不存用户名的拷贝）。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*DisplayName*"`
Expected: FAIL，编译错误

- [ ] **Step 3: 实现**

`validate` 的顺序照 spec §7.4。拒绝字符的判定按字素簇遍历（`BreakIterator`）：簇里任一码点类型为 `CONTROL`、`LINE_SEPARATOR`、`PARAGRAPH_SEPARATOR`、`PRIVATE_USE`、`UNASSIGNED`、`SURROGATE` → 拒绝；类型为 `FORMAT` 时，只有「`Character.isEmojiComponent(cp)` 且该簇含 `Character.isExtendedPictographic` 的码点」才放过；簇的第一个码点类型是 `NON_SPACING_MARK`、`ENCLOSING_MARK`、`COMBINING_SPACING_MARK` → 拒绝（Review Focus 1）。形态检查在 `Normalizer.normalize(value, NFKC)` 的副本上做；顶级域清单与微信规则照 spec §7.4 的正则。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/DisplayName.java patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/UserFieldViolations.java patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/DisplayNamePolicy.java patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/RejectedDisplayNameWords.java patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/model/vo/DisplayNameTest.java patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/policy/DisplayNamePolicyTest.java
git commit -m "feat(identity): 加昵称值对象、规则与拒绝词清单 (PAP-93)"
```

---

### Task 4: 四态枚举、identity 错误特征、新异常、改名策略与配置

**Files:**
- Modify: `…/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/enums/UserStatus.java`
- Create: `…/domain/exception/IdentityErrorTrait.java`、`UserIdentifierTakenException.java`、`UsernameChangeCooldownException.java`、`UserPendingDeletionException.java`、`InvalidUserStateTransitionException.java`
- Create: `…/domain/policy/UsernameChangePolicy.java`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/java/dev/patra/identity/config/IdentityProperties.java`、`IdentityConfiguration.java`、`patra-identity-boot/src/main/resources/application.yml`
- Test: `…/patra-identity-domain/src/test/java/dev/patra/identity/domain/exception/DomainExceptionTraitsTest.java`（加用例）、`…/domain/policy/UsernameChangePolicyTest.java`、`patra-identity-boot/src/test/java/dev/patra/identity/config/IdentityConfigurationTest.java`（加用例）

**Interfaces:**
- Produces:
  - `enum UserStatus { ACTIVE, BANNED, PENDING_DELETION, DELETED }`。
  - `enum IdentityErrorTrait implements ErrorTrait { PENDING_DELETION }`。
  - `UserIdentifierTakenException(List<FieldViolation>) extends DomainException implements HasFieldViolations`，特征 `CONFLICT`，文案「邮箱或用户名已被使用」，空列表抛 `IllegalArgumentException`，`getFieldViolations()` 返回副本。
  - `UsernameChangeCooldownException(Duration retryAfter) implements HasRetryAfter`，特征 `QUOTA_EXCEEDED`，文案「用户名修改过于频繁，请稍后再试」。
  - `UserPendingDeletionException()`，特征 `CONFLICT` + `IdentityErrorTrait.PENDING_DELETION`，文案「账号正在注销中，取消注销后再操作」。
  - `InvalidUserStateTransitionException(UserStatus from, String action)`，特征 `CONFLICT`，文案「账号当前状态不允许此操作」（`from`、`action` 只进 `getMessage()` 之外的字段，不进 detail）。
  - `record UsernameChangePolicy(Duration cooldown, Duration reservationPeriod)`：`static of(cooldown, reservationPeriod)`，非正数抛 `IllegalArgumentException`；`Optional<Duration> remainingCooldown(Instant changedAt, Instant now)`（`changedAt` 为 `null` 或已过冷却返回空）；`Instant nextChangeAt(Instant changedAt)`（`null` 进 `null` 出）。
  - `IdentityProperties` 加第三个成员 `@DefaultValue Username username`，`record Username(@DefaultValue("30d") Duration changeCooldown, @DefaultValue("90d") Duration reservationPeriod)`。
  - `IdentityConfiguration` 新增三个 Bean：`UsernamePolicy usernamePolicy()`、`DisplayNamePolicy displayNamePolicy()`、`UsernameChangePolicy usernameChangePolicy(IdentityProperties)`（非正数让 `of` 抛出，启动失败）。
  - `application.yml` 的 `patra.identity` 下加 `username: { change-cooldown: 30d, reservation-period: 90d }`。

- [ ] **Step 1: 写失败的测试**

`DomainExceptionTraitsTest` 加四条：`UserIdentifierTakenException` 的特征只有 `CONFLICT` 且 `getFieldViolations()` 回传入的两条；`UserPendingDeletionException` 的特征是 `{CONFLICT, PENDING_DELETION}`，且 `IdentityErrorTrait.PENDING_DELETION.name()` 是 `"PENDING_DELETION"`；`UsernameChangeCooldownException(Duration.ofMillis(1500))` 的 `getRetryAfterSeconds()` 是 2；`InvalidUserStateTransitionException(UserStatus.DELETED, "unban")` 的特征是 `CONFLICT`、消息是「账号当前状态不允许此操作」。

`UsernameChangePolicyTest`：

```java
@Test
void should_compute_remaining_cooldown() {
  UsernameChangePolicy policy = UsernameChangePolicy.of(Duration.ofDays(30), Duration.ofDays(90));
  Instant changedAt = Instant.parse("2026-10-20T08:00:00Z");
  assertThat(policy.remainingCooldown(null, changedAt)).isEmpty();
  assertThat(policy.remainingCooldown(changedAt, changedAt.plus(Duration.ofDays(10))))
      .contains(Duration.ofDays(20));
  assertThat(policy.remainingCooldown(changedAt, changedAt.plus(Duration.ofDays(30)))).isEmpty();
  assertThat(policy.nextChangeAt(changedAt)).isEqualTo(changedAt.plus(Duration.ofDays(30)));
  assertThat(policy.nextChangeAt(null)).isNull();
}

@Test
void should_reject_non_positive_periods() {
  assertThatThrownBy(() -> UsernameChangePolicy.of(Duration.ZERO, Duration.ofDays(90)))
      .isInstanceOf(IllegalArgumentException.class);
}
```

`IdentityConfigurationTest` 加：默认属性下 `usernameChangePolicy` 的冷却是 30 天、保留 90 天；`change-cooldown` 为 `0s` 时构造 Bean 抛 `IllegalArgumentException`（照该测试类里会话策略用例的写法）。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*DomainExceptionTraitsTest*" --tests "*UsernameChangePolicyTest*" :patra-api:patra-identity:patra-identity-boot:test --tests "*IdentityConfigurationTest*"`
Expected: FAIL，编译错误

- [ ] **Step 3: 实现**

`UserStatus` 加两个值并更新 Javadoc。`IdentityErrorTrait` 放在 `domain/exception/`。`IdentityProperties` 是 record，第三个成员加 `@DefaultValue`，否则不配时为 `null`。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/enums/UserStatus.java patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/exception patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/UsernameChangePolicy.java patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/exception/DomainExceptionTraitsTest.java patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/policy/UsernameChangePolicyTest.java patra-api/patra-identity/patra-identity-boot/src/main patra-api/patra-identity/patra-identity-boot/src/test/java/dev/patra/identity/config/IdentityConfigurationTest.java
git commit -m "feat(identity): 加四态状态、identity 错误特征、改名策略与配置 (PAP-93)"
```

---

### Task 5: `UserProfile` 聚合骨架与 `idn_user_profile`

**Files:**
- Create: `…/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/aggregate/UserProfile.java`
- Create: `…/domain/port/repository/UserProfileRepository.java`
- Modify: `patra-api/patra-identity/patra-identity-infra/src/main/resources/db/migration/V1__init_identity_schema.sql`（加表）
- Create: `…/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/persistence/entity/UserProfileEntity.java`、`…/dao/UserProfileDao.java`、`…/converter/mapper/UserProfileJpaMapper.java`、`…/persistence/UserProfileRepositoryAdapter.java`
- Test: `…/patra-identity-domain/src/test/java/dev/patra/identity/domain/model/aggregate/UserProfileTest.java`、`…/patra-identity-infra/src/integrationTest/java/dev/patra/identity/infra/adapter/persistence/UserProfileRepositoryAdapterIT.java`、`IdentitySchemaConstraintsIT.java`（加两条）

**Interfaces:**
- Consumes: Task 3 的 `DisplayName`。
- Produces:
  - `final class UserProfile`：`static UserProfile create(long userId)`（昵称为空）；`static UserProfile restore(Long id, Long userId, DisplayName displayName, Long version, Instant createdAt, Instant updatedAt)`（`displayName` 可为 `null`）；`void rename(DisplayName displayName)`；`Optional<DisplayName> getDisplayName()`；getter `getId`、`getUserId`、`getVersion`、`getCreatedAt`、`getUpdatedAt`。
  - `interface UserProfileRepository { UserProfile save(UserProfile); Optional<UserProfile> findByUserId(long userId); }`，`save` 对新对象分配雪花 ID。
  - 表 `idn_user_profile`（spec §6.3）：`id`、`user_id`（`uk_idn_user_profile_user` 唯一、`fk_idn_user_profile_user` 外键）、`display_name VARCHAR(120) NULL`（空 = 跟随用户名）、`ck_idn_user_profile_display_name CHECK (display_name IS NULL OR (octet_length(display_name) BETWEEN 2 AND 120 AND display_name = btrim(display_name)))`，审计列、触发器、注释照 `idn_user_password_credential`。
  - `UserProfileDao extends JpaRepository<UserProfileEntity, Long>`：`Optional<UserProfileEntity> findByUserId(Long userId)`。

- [ ] **Step 1: 写失败的测试**

`UserProfileTest`：`create(7L)` 的 `getId()` 为 `null`、`getUserId()` 是 7、`getDisplayName()` 为空；`rename(new DisplayName("陈宇"))` 后昵称是 `陈宇`；`restore` 保留全部字段，`displayName` 传 `null` 时 `getDisplayName()` 为空。

`IdentitySchemaConstraintsIT` 加：`should_reject_profile_display_name_over_120_bytes`（插 41 个汉字，报 `ck_idn_user_profile_display_name`）、`should_accept_profile_without_display_name`（`display_name` 为 `NULL` 不抛）、`should_reject_profile_of_missing_user`（报 `fk_idn_user_profile_user`）。

`UserProfileRepositoryAdapterIT`（`@DataJpaTest`，照 `UserPasswordCredentialRepositoryAdapterIT` 的注解）：先存一个用户拿 ID，`save(UserProfile.create(userId))` 后 `getId()` 非空、`version` 是 0；`findByUserId` 回来的 `getDisplayName()` 为空；`rename` 后再 `save`，`findByUserId` 回 `陈宇`；第二次 `save` 新建的同一用户资料抛 `DataIntegrityViolationException`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*UserProfileTest*" :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*UserProfileRepositoryAdapterIT*" --tests "*IdentitySchemaConstraintsIT*"`
Expected: FAIL，编译错误；约束用例报没有这张表

- [ ] **Step 3: 实现聚合、建表、实体、Dao、Mapper、适配器**

`UserProfileEntity extends BaseJpaEntity`，`@Table(name = "idn_user_profile")`，列 `user_id`、`display_name`（`nullable = true`）。Mapper 照 `UserPasswordCredentialJpaMapper`，昵称 `null` 与 `Optional` 互转手写。适配器照 `UserPasswordCredentialRepositoryAdapter`（`saveAndFlush`、雪花 ID、乐观锁转 `UserModifiedConcurrentlyException`）。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/aggregate/UserProfile.java patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/port/repository/UserProfileRepository.java patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/model/aggregate/UserProfileTest.java patra-api/patra-identity/patra-identity-infra/src/main patra-api/patra-identity/patra-identity-infra/src/integrationTest
git commit -m "feat(identity): 加资料聚合骨架与 idn_user_profile 表 (PAP-93)"
```

---

### Task 6: `FormerUsername` 聚合与 `idn_user_former_username`

**Files:**
- Create: `…/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/aggregate/FormerUsername.java`
- Create: `…/domain/port/repository/FormerUsernameRepository.java`
- Modify: `…/patra-identity-infra/src/main/resources/db/migration/V1__init_identity_schema.sql`（加表）
- Create: `…/infra/adapter/persistence/entity/FormerUsernameEntity.java`、`…/dao/FormerUsernameDao.java`、`…/converter/mapper/FormerUsernameJpaMapper.java`、`…/persistence/FormerUsernameRepositoryAdapter.java`
- Test: `…/domain/model/aggregate/FormerUsernameTest.java`、`…/infra/adapter/persistence/FormerUsernameRepositoryAdapterIT.java`、`IdentitySchemaConstraintsIT.java`（加两条）

**Interfaces:**
- Consumes: Task 2 的 `Username`。
- Produces:
  - `final class FormerUsername`：`static FormerUsername reserve(long userId, Username username, Instant replacedAt, Duration reservationPeriod)`（`reservedUntil = replacedAt + period`）；`static FormerUsername restore(Long id, Long userId, Username username, Instant replacedAt, Instant reservedUntil, Long version)`；`boolean isReservedAt(Instant now)`（`now.isBefore(reservedUntil)`）；getter。
  - `interface FormerUsernameRepository`：`FormerUsername save(FormerUsername)`；`Optional<FormerUsername> findReserved(Username username, Instant now)`（`reserved_until > now` 的记录里 `replaced_at` 最新的一条）；`boolean existsReserved(Username username, Instant now)`；`boolean existsReservedByOther(Username username, long userId, Instant now)`。
  - 表 `idn_user_former_username`（spec §6.4）：`user_id`（外键 `fk_idn_user_former_username_user`）、`username VARCHAR(30)`（`ck_idn_user_former_username_format` 同 `idn_user` 的格式检查）、`replaced_at`、`reserved_until`、`ck_idn_user_former_username_period CHECK (reserved_until > replaced_at)`；索引 `idx_idn_user_former_username_username (username, reserved_until)`、`idx_idn_user_former_username_user_id (user_id)`。
  - `FormerUsernameDao`：`Optional<FormerUsernameEntity> findFirstByUsernameAndReservedUntilAfterOrderByReplacedAtDesc(String, Instant)`、`boolean existsByUsernameAndReservedUntilAfter(String, Instant)`、`boolean existsByUsernameAndUserIdNotAndReservedUntilAfter(String, Long, Instant)`。

- [ ] **Step 1: 写失败的测试**

`FormerUsernameTest`：`reserve(7L, Username.of("old-name"), T0, Duration.ofDays(90))` 的 `getReservedUntil()` 是 `T0 + 90d`；`isReservedAt(T0 + 89d)` 真、`isReservedAt(T0 + 90d)` 假（到期当刻不再保留）。

`FormerUsernameRepositoryAdapterIT`（`@DataJpaTest`）：

```java
@Test
void should_find_reserved_record_before_expiry_only() {
  long userId = saveUser("a@example.com", "alice");
  repository.save(FormerUsername.reserve(userId, Username.of("old-name"), T0, Duration.ofDays(90)));
  assertThat(repository.findReserved(Username.of("old-name"), T0.plus(Duration.ofDays(89))))
      .hasValueSatisfying(f -> assertThat(f.getUserId()).isEqualTo(userId));
  assertThat(repository.findReserved(Username.of("old-name"), T0.plus(Duration.ofDays(90)))).isEmpty();
  assertThat(repository.existsReserved(Username.of("old-name"), T0)).isTrue();
  assertThat(repository.existsReservedByOther(Username.of("old-name"), userId, T0)).isFalse();
  assertThat(repository.existsReservedByOther(Username.of("old-name"), userId + 1, T0)).isTrue();
}

@Test
void should_return_latest_unexpired_record_when_same_name_reused() {
  long a = saveUser("a@example.com", "alice");
  long b = saveUser("b@example.com", "bob");
  repository.save(FormerUsername.reserve(a, Username.of("shared"), T0, Duration.ofDays(90)));
  repository.save(FormerUsername.reserve(b, Username.of("shared"), T0.plus(Duration.ofDays(100)), Duration.ofDays(90)));
  assertThat(repository.findReserved(Username.of("shared"), T0.plus(Duration.ofDays(120))))
      .hasValueSatisfying(f -> assertThat(f.getUserId()).isEqualTo(b));
}
```

`saveUser` 在本任务里仍用现有的 `User.register(EmailAddress)`（Task 7 改签名时一并改这里）。

`IdentitySchemaConstraintsIT` 加：`should_reject_former_username_with_reservation_not_after_replacement`（`reserved_until = replaced_at`，报 `ck_idn_user_former_username_period`）、`should_reject_former_username_with_bad_format`（`Old-Name`，报 `ck_idn_user_former_username_format`）。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*FormerUsernameTest*" :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*FormerUsernameRepositoryAdapterIT*" --tests "*IdentitySchemaConstraintsIT*"`
Expected: FAIL

- [ ] **Step 3: 实现**

实体列 `user_id`、`username`、`replaced_at`、`reserved_until`；适配器把 Dao 的三个派生查询转成端口方法，`save` 分配雪花 ID。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/aggregate/FormerUsername.java patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/port/repository/FormerUsernameRepository.java patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/model/aggregate/FormerUsernameTest.java patra-api/patra-identity/patra-identity-infra/src/main patra-api/patra-identity/patra-identity-infra/src/integrationTest
git commit -m "feat(identity): 加旧用户名聚合与 idn_user_former_username 表 (PAP-93)"
```

---

### Task 7: 账号模型重建——`User` 聚合、`idn_user` 的 `V1`、注册带用户名、登录与当前用户的四态分流

这是最大的一个任务：`User.register` 的签名变了之后，仓储、映射、三个处理器、Controller、所有注册过用户的测试都要跟着改，拆不开。按下面的顺序做，中途允许编译不过，任务结束时全绿。

**Files:**
- Modify: `…/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/aggregate/User.java`（重写）、`…/domain/port/repository/UserRepository.java`
- Delete: `…/domain/exception/EmailAlreadyRegisteredException.java`
- Modify: `…/patra-identity-infra/src/main/resources/db/migration/V1__init_identity_schema.sql`（`idn_user` 重写、`V2` 的登录记录并入）
- Delete: `…/patra-identity-infra/src/main/resources/db/migration/V2__add_user_login_record.sql`
- Modify: `…/infra/adapter/persistence/entity/UserEntity.java`、`…/dao/UserDao.java`、`…/converter/mapper/UserJpaMapper.java`、`…/persistence/UserRepositoryAdapter.java`、`…/infra/adapter/read/UserReadAdapter.java`
- Modify: `…/patra-identity-app/src/main/java/dev/patra/identity/app/usecase/register/RegisterUserCommand.java`、`RegisterUserHandler.java`、`…/usecase/login/LoginUserHandler.java`、`…/usecase/user/query/UserQueryService.java`
- Modify: `…/patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/auth/request/RegisterRequest.java`、`…/auth/AuthController.java`
- Test（改）: `UserTest`（重写）、`DomainExceptionTraitsTest`、`UserRepositoryAdapterIT`、`UserReadAdapterIT`、`UserPasswordCredentialRepositoryAdapterIT`、`UserLoginRecordRepositoryAdapterIT`、`UserProfileRepositoryAdapterIT`、`FormerUsernameRepositoryAdapterIT`、`IdentitySchemaConstraintsIT`、`RegisterUserHandlerTest`、`LoginUserHandlerTest`、`UserQueryServiceTest`、`RegisterRequestTest`、`AuthControllerIT`、`AccountFlowIT`、`SessionFlowIT`、`RedisUnavailableIT`、`PasswordHashingConnectionIT`（所有调 `/auth/register` 的地方加 `username`）

**Interfaces:**
- Consumes: Task 2 ~ 6 的全部类型。
- Produces:
  - `User`：`static User register(EmailAddress email, Username username)`；`@Builder(builderMethodName = "restore")` 放在私有构造器 `User(Long id, EmailAddress email, Instant emailVerifiedAt, Username username, Instant usernameChangedAt, UserStatus status, Instant bannedAt, Instant deletionDueAt, Instant deletedAt, Long version, Instant createdAt, Instant updatedAt)` 上，构造器校验 `id`、`username`、`status` 非空、`status != DELETED` 时 `email` 非空；`Optional<EmailAddress> getEmail()`（字段 `@Getter(AccessLevel.NONE)`）；`Instant getEmailVerifiedAt()`、`boolean isEmailVerified()`；`Username getUsername()`、`Instant getUsernameChangedAt()`；`isActive()`、`isBanned()`、`isPendingDeletion()`、`isDeleted()`；`void ban(Instant now)`、`void unban()`、`void requestDeletion(Instant now, Duration coolingPeriod)`、`void cancelDeletion()`、`void completeDeletion(Instant now)`、`void markEmailVerified(Instant now)`、`void requireActive()`、`Username changeUsername(Username next, Instant now, UsernameChangePolicy policy)`（返回旧名）。语义照 spec §7.2、§7.5。
  - `UserRepository` 加 `Optional<User> findByUsername(Username)`、`boolean existsByUsername(Username)`；`save` 的 Javadoc 改为抛 `UserIdentifierTakenException`。
  - `UserEntity` 列：`email`（`nullable = true`）、`email_verified_at`、`username`（`length = 30`）、`username_changed_at`、`status`、`banned_at`、`deletion_due_at`、`deleted_at`。`UserDao` 加 `findByUsername(String)`、`existsByUsername(String)`。
  - `UserRepositoryAdapter`：常量 `USERNAME_UNIQUE_CONSTRAINT = "uk_idn_user_username"`；`save` 撞 `uk_idn_user_email` 抛 `UserIdentifierTakenException(List.of(emailAlreadyRegistered()))`，撞 `uk_idn_user_username` 抛 `…(List.of(usernameAlreadyTaken()))`。
  - `RegisterUserCommand`：`@Builder record RegisterUserCommand(String email, String username, String password, String clientType, String deviceId)`（去掉 `of()`，`toString` 遮密码）。
  - `RegisterUserHandler` 新依赖：`UsernamePolicy`、`UserProfileRepository`、`FormerUsernameRepository`、`Clock`；顺序照 spec §8.1 第 1 ~ 4 步；结果暂仍是 `RegisterUserResult.of(token, id, email)`（Task 8 换）。
  - `LoginUserHandler`：密码正确后、`recordSuccess` 之前 `if (verified.isDeleted()) throw failure(attempt);`；事务里加锁重读后 `isDeleted()` 抛 `InvalidCredentialsException`、`isBanned()` 抛 `UserBannedException`。
  - `UserQueryService.currentAccount()`：`BANNED` 和 `DELETED` 都抛 `AuthenticationRequiredException`。
  - `RegisterRequest(String email, String username, String password, String clientType, String deviceId)`；`AuthController.register` 用 `RegisterUserCommand.builder()`。

- [ ] **Step 1: 重写 `UserTest`（AC 5）并确认失败**

```java
@DisplayName("User 单元测试")
class UserTest {
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final Username USERNAME = Username.of("chen-yu");
  private static final Instant T0 = Instant.parse("2026-10-20T08:00:00Z");
  private static final UsernameChangePolicy POLICY = UsernameChangePolicy.of(Duration.ofDays(30), Duration.ofDays(90));

  @Test
  void should_register_active_unverified_user() {
    User user = User.register(EMAIL, USERNAME);
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getEmail()).contains(EMAIL);
    assertThat(user.isEmailVerified()).isFalse();
    assertThat(user.getUsername()).isEqualTo(USERNAME);
    assertThat(user.getUsernameChangedAt()).isNull();
  }

  @Test
  void should_walk_active_banned_active() { /* ban(T0) → BANNED + bannedAt；ban(T0+1h) 不变；unban() → ACTIVE、bannedAt null；unban() 再次不变 */ }

  @Test
  void should_walk_active_pending_deletion_active() { /* requestDeletion(T0, 15d) → PENDING_DELETION、deletionDueAt = T0+15d；再次 requestDeletion 不改到期；cancelDeletion() → ACTIVE、deletionDueAt null */ }

  @Test
  void should_walk_pending_deletion_to_deleted_and_clear_identity() {
    User user = User.register(EMAIL, USERNAME);
    user.markEmailVerified(T0);
    user.requestDeletion(T0, Duration.ofDays(15));
    user.completeDeletion(T0.plus(Duration.ofDays(15)));
    assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(user.getEmail()).isEmpty();
    assertThat(user.getEmailVerifiedAt()).isNull();
    assertThat(user.getDeletionDueAt()).isNull();
    assertThat(user.getDeletedAt()).isEqualTo(T0.plus(Duration.ofDays(15)));
    assertThat(user.getUsername()).isEqualTo(USERNAME);
    user.completeDeletion(T0.plus(Duration.ofDays(16)));   // 幂等
    assertThat(user.getDeletedAt()).isEqualTo(T0.plus(Duration.ofDays(15)));
  }

  @Test
  void should_allow_banned_to_deleted_and_ban_to_void_pending_deletion() { /* BANNED.completeDeletion → DELETED；PENDING_DELETION.ban → BANNED 且 deletionDueAt null */ }

  @Test
  void should_reject_every_transition_out_of_deleted() {
    User user = deletedUser();
    assertThatThrownBy(() -> user.ban(T0)).isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(user::unban).isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(() -> user.requestDeletion(T0, Duration.ofDays(15))).isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(user::cancelDeletion).isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(() -> user.markEmailVerified(T0)).isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(() -> user.changeUsername(Username.of("other"), T0, POLICY)).isInstanceOf(AuthenticationRequiredException.class);
  }

  @Test
  void should_reject_invalid_transitions_from_active_and_banned() { /* ACTIVE.completeDeletion 抛 InvalidUserStateTransitionException；BANNED.requestDeletion、BANNED.cancelDeletion 抛同类 */ }

  @Test
  void should_guard_self_service_writes_by_status() {
    /* ACTIVE.requireActive() 不抛；PENDING_DELETION 抛 UserPendingDeletionException；BANNED 和 DELETED 抛 AuthenticationRequiredException */
  }

  @Test
  void should_change_username_without_cooldown_on_first_change() {
    User user = User.register(EMAIL, USERNAME);
    Username previous = user.changeUsername(Username.of("chen-yu-2"), T0, POLICY);
    assertThat(previous).isEqualTo(USERNAME);
    assertThat(user.getUsername().value()).isEqualTo("chen-yu-2");
    assertThat(user.getUsernameChangedAt()).isEqualTo(T0);
  }

  @Test
  void should_reject_username_change_within_cooldown_with_remaining_time() {
    User user = User.register(EMAIL, USERNAME);
    user.changeUsername(Username.of("chen-yu-2"), T0, POLICY);
    assertThatThrownBy(() -> user.changeUsername(USERNAME, T0.plus(Duration.ofDays(10)), POLICY))
        .isInstanceOfSatisfying(UsernameChangeCooldownException.class,
            e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofDays(20)));
    user.changeUsername(USERNAME, T0.plus(Duration.ofDays(30)), POLICY);   // 到期当刻可改
  }

  @Test
  void should_reject_unchanged_username() {
    User user = User.register(EMAIL, USERNAME);
    assertThatThrownBy(() -> user.changeUsername(Username.of("CHEN-YU"), T0, POLICY))
        .isInstanceOfSatisfying(InvalidUserFieldsException.class,
            e -> assertThat(e.getFieldViolations()).singleElement().extracting(FieldViolation::code).isEqualTo("UNCHANGED"));
  }

  @Test
  void should_keep_first_email_verification_time() { /* markEmailVerified(T0) 再 markEmailVerified(T0+1d) 仍是 T0 */ }

  @Test
  void should_restore_all_fields_via_builder() { /* User.restore().id(42L)…build()，逐字段断言；status DELETED 且 email null 合法；status ACTIVE 且 email null 抛 IllegalArgumentException */ }
}
```

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*UserTest*"`
Expected: FAIL，编译错误

- [ ] **Step 2: 重写 `User`，删除 `EmailAlreadyRegisteredException`，改 `UserRepository`、`DomainExceptionTraitsTest`**

`changeUsername` 的顺序：`requireActive()` → `next.equals(username)` 抛 `InvalidUserFieldsException(List.of(usernameUnchanged()))` → `policy.remainingCooldown(usernameChangedAt, now)` 有值抛 `UsernameChangeCooldownException` → 换名。`InvalidUserStateTransitionException` 的 `action` 传方法名。

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test`
Expected: PASS（domain 全绿；其他模块此时编译不过是预期的）

- [ ] **Step 3: 重写 `IdentitySchemaConstraintsIT` 并确认失败**

把现有五条改成对新 `V1` 的断言，并补齐：

| 用例 | 插入 | 期望约束名 |
|---|---|---|
| `should_reject_uppercase_username` | `username = 'Chen'` | `ck_idn_user_username_format` |
| `should_reject_consecutive_separators` / `leading_separator` / `digits_only` / `two_char` / `thirty_one_char` | `a--b`、`-ab`、`123456`、`ab`、31 个 `a` | `ck_idn_user_username_format` |
| `should_reject_duplicate_username` | 两行同名 | `uk_idn_user_username` |
| `should_reject_unknown_status` | `status = 'FROZEN'` | `ck_idn_user_status` |
| `should_require_banned_at_when_banned` / `reject_banned_at_when_active` | 照旧 | `ck_idn_user_banned_at` |
| `should_require_deletion_due_at_when_pending` / `reject_deletion_due_at_otherwise` | | `ck_idn_user_deletion_due_at` |
| `should_require_deleted_at_when_deleted` / `reject_deleted_at_otherwise` | | `ck_idn_user_deleted_at` |
| `should_require_null_email_when_deleted` / `reject_null_email_when_active` | | `ck_idn_user_email_deleted` |
| `should_reject_verified_at_without_email` | `DELETED` 行带 `email_verified_at` | `ck_idn_user_email_verified_at` |
| `should_accept_two_deleted_users_without_email` | 两行 `email NULL` | 不抛 |

`INSERT_USER` 改成 `INSERT INTO idn_user (id, email, username, status, banned_at, deletion_due_at, deleted_at, email_verified_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`。Task 5、6 加的用例保留。

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*IdentitySchemaConstraintsIT*"`
Expected: FAIL（编译错误或约束名不存在）

- [ ] **Step 4: 重写 `V1`，删 `V2`**

`idn_user` 按 spec §6.1 写：

```sql
CREATE TABLE idn_user
(
    id                  BIGINT          NOT NULL,
    email               VARCHAR(254)    NULL,
    email_verified_at   timestamptz(6)  NULL,
    username            VARCHAR(30)     NOT NULL,
    username_changed_at timestamptz(6)  NULL,
    status              VARCHAR(16)     NOT NULL,
    banned_at           timestamptz(6)  NULL,
    deletion_due_at     timestamptz(6)  NULL,
    deleted_at          timestamptz(6)  NULL,
    -- 审计列同现有脚本
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_email UNIQUE (email),
    CONSTRAINT uk_idn_user_username UNIQUE (username),
    CONSTRAINT ck_idn_user_email_lowercase CHECK (email IS NULL OR email = lower(email)),
    CONSTRAINT ck_idn_user_email_deleted CHECK ((status = 'DELETED') = (email IS NULL)),
    CONSTRAINT ck_idn_user_email_verified_at CHECK (email IS NOT NULL OR email_verified_at IS NULL),
    CONSTRAINT ck_idn_user_username_format CHECK (
        username ~ '^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$'
        AND char_length(username) BETWEEN 3 AND 30
        AND username !~ '^[0-9]+$'),
    CONSTRAINT ck_idn_user_status CHECK (status IN ('ACTIVE', 'BANNED', 'PENDING_DELETION', 'DELETED')),
    CONSTRAINT ck_idn_user_banned_at CHECK ((status = 'BANNED') = (banned_at IS NOT NULL)),
    CONSTRAINT ck_idn_user_deletion_due_at CHECK ((status = 'PENDING_DELETION') = (deletion_due_at IS NOT NULL)),
    CONSTRAINT ck_idn_user_deleted_at CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);
```

列注释逐列补上；`idn_user_login_record` 整段从 `V2` 搬过来放在凭据表之后；Task 5、6 加的两张表保留。

Run: 同 Step 3
Expected: PASS

- [ ] **Step 5: 改实体、Dao、Mapper、仓储适配器、读适配器，并改 `UserRepositoryAdapterIT` 确认通过**

`UserJpaMapper.toEntity` 的映射：`email` 用 `expression = "java(user.getEmail().map(EmailAddress::value).orElse(null))"`，`username` 用 `user.getUsername().value()`，`status` 用 `name()`，其余同名。`toAggregate` 手写成 `User.restore().id(…)…build()`，`email` 为 `null` 时传 `null`。

`UserRepositoryAdapterIT` 加：`should_translate_username_unique_violation`（第二个用户同用户名，抛 `UserIdentifierTakenException` 且 `errors` 是 `username` / `ALREADY_TAKEN`）；现有邮箱冲突用例改成断言 `email` / `ALREADY_REGISTERED`；`should_find_and_check_username_of_deleted_user`（用 SQL 把用户改成 `DELETED` 并置空邮箱，`existsByUsername` 仍真，`findByUsername` 回来的 `getEmail()` 为空、`isDeleted()` 真）。其他四个 IT 的 `User.register(email)` 改成 `User.register(email, Username.of("…"))`。

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest`
Expected: PASS

- [ ] **Step 6: 改 `RegisterUserHandlerTest`、`LoginUserHandlerTest`、`UserQueryServiceTest` 并确认失败**

`RegisterUserHandlerTest` 新用例：

```java
@Test
void should_report_all_five_field_violations_at_once() { /* email、username、password、clientType、deviceId 各错一处 → 一次 422，errors 五条，不查库不哈希 */ }

@Test
void should_report_both_taken_identifiers_in_one_409() {
  when(users.existsByEmail(any())).thenReturn(true);
  when(users.existsByUsername(any())).thenReturn(true);
  assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "chen-yu")))
      .isInstanceOfSatisfying(UserIdentifierTakenException.class, e ->
          assertThat(e.getFieldViolations()).extracting(FieldViolation::field).containsExactly("email", "username"));
  verify(passwordHashing, never()).hash(any());
}

@Test
void should_treat_reserved_former_username_as_taken() { /* existsByUsername 假、formerUsernames.existsReserved 真 → 409 只有 username 一条 */ }

@Test
void should_create_empty_profile_in_the_transaction() {
  /* 成功路径：users.save → profiles.save(captor) 的 getDisplayName() 为空、userId 等于保存后的 ID → credentials.save → sessionIssuer.issue */
}
```

`LoginUserHandlerTest` 新用例：`should_settle_deleted_user_as_failure_without_recording_success`（查到的用户 `isDeleted()`，密码匹配也抛 `InvalidCredentialsException`，`recordFailure` 被调、`recordSuccess` 没被调）；`should_login_pending_deletion_user`（照常建会话）。

`UserQueryServiceTest` 新用例：`should_reject_deleted_user_with_401`。

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test`
Expected: FAIL，编译错误

- [ ] **Step 7: 改命令、两个处理器、查询服务，确认 app 测试通过**

`RegisterUserHandler.handle` 的骨架：

```java
List<FieldViolation> violations = new ArrayList<>();
EmailAddress.validate(command.email()).ifPresent(violations::add);
usernamePolicy.validate(command.username()).ifPresent(violations::add);
passwordPolicy.validateForRegistration(command.password()).ifPresent(violations::add);
violations.addAll(LoginClient.validate(command.clientType(), command.deviceId()));
if (!violations.isEmpty()) throw new InvalidUserFieldsException(violations);
EmailAddress email = EmailAddress.of(command.email());
Username username = Username.of(command.username());
Instant now = clock.instant();
List<FieldViolation> taken = new ArrayList<>();
if (users.existsByEmail(email)) taken.add(UserFieldViolations.emailAlreadyRegistered());
if (users.existsByUsername(username) || formerUsernames.existsReserved(username, now)) taken.add(UserFieldViolations.usernameAlreadyTaken());
if (!taken.isEmpty()) throw new UserIdentifierTakenException(taken);
// 哈希（事务外）→ 事务：users.save → profiles.save(UserProfile.create(id)) → credentials.save → sessionIssuer.issue
```

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test`
Expected: PASS

- [ ] **Step 8: 改 `RegisterRequest`、`AuthController`、`RegisterRequestTest`、`AuthControllerIT`，确认 adapter 通过**

`AuthControllerIT`：注册用例请求体加 `"username": " Chen-Yu "`，用 `ArgumentCaptor` 断言命令里是原始输入；加 `should_return_409_with_field_errors_when_identifiers_taken`（mock 抛 `UserIdentifierTakenException` 两条，断言 `code` 是 `IDN-0409`、`errors[0].field` 是 `email`、`errors[1].code` 是 `ALREADY_TAKEN`、`rejectedValue` 为 `null`）；删掉对 `EmailAlreadyRegisteredException` 的引用。

Run: `./gradlew :patra-api:patra-identity:patra-identity-adapter:test :patra-api:patra-identity:patra-identity-adapter:integrationTest`
Expected: PASS

- [ ] **Step 9: 改 boot 的端到端测试，补 AC 3 的用例，确认通过**

`AccountFlowIT` 的 `register(email, password)` 改成 `register(email, username, password)`，现有用例各给一个用户名；`SessionFlowIT`、`RedisUnavailableIT`、`PasswordHashingConnectionIT` 里调 `/auth/register` 的请求体加 `username`。`AccountFlowIT` 新增：

```java
@Test
void should_return_422_with_reason_code_when_username_missing() {
  EntityExchangeResult<String> result = post("/auth/register", Map.of("email", "no-name@example.com", "password", "Correct-Horse-01"));
  assertThat(result.getStatus().value()).isEqualTo(422);
  JsonNode errors = JSON.readTree(result.getResponseBody()).get("errors");
  assertThat(errors).singleElement().satisfies(e -> {
    assertThat(e.get("field").asString()).isEqualTo("username");
    assertThat(e.get("code").asString()).isEqualTo("REQUIRED");
  });
}

@Test
void should_distinguish_duplicate_username_from_duplicate_email() {
  register("dup@example.com", "dup-user", "Correct-Horse-01");
  JsonNode sameEmail = JSON.readTree(register("dup@example.com", "another", "Correct-Horse-01").getResponseBody());
  JsonNode sameName = JSON.readTree(register("other@example.com", "DUP-USER", "Correct-Horse-01").getResponseBody());
  JsonNode both = JSON.readTree(register("dup@example.com", "dup-user", "Correct-Horse-01").getResponseBody());
  assertThat(sameEmail.get("errors").get(0).get("field").asString()).isEqualTo("email");
  assertThat(sameName.get("errors").get(0).get("field").asString()).isEqualTo("username");
  assertThat(both.get("errors")).hasSize(2);
}

@Test
void should_reject_email_of_user_pending_deletion_but_accept_email_of_deleted_user() {
  /* 注册 → SQL 改 PENDING_DELETION（deletion_due_at = now + 15d）→ 同邮箱注册 409；再 SQL 改 DELETED（email NULL、deleted_at、删凭据）→ 同邮箱注册 201，同用户名注册 409 */
}
```

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest`
Expected: PASS

- [ ] **Step 10: 全量回归并提交**

Run: `./gradlew spotlessApply && ./gradlew :patra-api:patra-identity:patra-identity-domain:check :patra-api:patra-identity:patra-identity-app:check :patra-api:patra-identity:patra-identity-infra:check :patra-api:patra-identity:patra-identity-adapter:check :patra-api:patra-identity:patra-identity-boot:check`
Expected: `BUILD SUCCESSFUL`

```bash
git add patra-api/patra-identity
git commit -m "feat(identity): 重建账号模型：用户名、邮箱验证时间、四态状态机与 V1 重写 (PAP-93)"
```

---

### Task 8: 读模型、`/auth/me` 新字段、登录与注册响应嵌套 `user`、四态登录的端到端测试

**Files:**
- Modify: `…/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/read/UserAccountReadModel.java`、`…/domain/port/read/UserReadPort.java`
- Modify: `…/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/read/UserReadAdapter.java`
- Create: `…/patra-identity-app/src/main/java/dev/patra/identity/app/usecase/auth/AuthenticatedUser.java`
- Delete: `…/app/usecase/register/RegisterUserResult.java`、`…/app/usecase/login/LoginUserResult.java`
- Modify: `RegisterUserCommand`、`RegisterUserHandler`、`LoginUserCommand`、`LoginUserHandler`、`UserQueryService`（无改动则不动）
- Modify: `…/patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/auth/response/CurrentUserResponse.java`、`AuthenticatedUserResponse.java`、`AuthController.java`
- Create: `…/patra-identity-boot/src/integrationTest/java/dev/patra/identity/UserStatusFlowIT.java`
- Test（改）: `UserReadAdapterIT`、`RegisterUserHandlerTest`、`LoginUserHandlerTest`、`AuthControllerIT`、`AccountFlowIT`、`SessionFlowIT`（读 `userId` 的地方改成 `user.userId`）

**Interfaces:**
- Consumes: Task 4 的 `UsernameChangePolicy`、Task 5 的 `UserProfileDao`。
- Produces:
  - `@Builder record UserAccountReadModel(long userId, String email, boolean emailVerified, String username, String displayName, UserStatus status, Instant deletionDueAt, Instant nextUsernameChangeAt)`：`email` 只在 `DELETED` 为 `null`；`username`、`displayName`、`status` 非空。
  - `UserReadAdapter.findAccount(userId)`：查 `UserDao` + `UserProfileDao.findByUserId`，资料行缺失抛 `IllegalStateException("用户 {id} 没有资料行")`；`displayName` 取资料的 `display_name`，为 `null` 时用 `username`；`nextUsernameChangeAt = usernameChangePolicy.nextChangeAt(entity.getUsernameChangedAt())`（构造器注入 `UsernameChangePolicy`）。
  - `record AuthenticatedUser(String sessionToken, UserAccountReadModel account)`，`of()`，`toString` 遮令牌。`RegisterUserCommand implements Command<AuthenticatedUser>`、`LoginUserCommand implements Command<AuthenticatedUser>`；两个处理器在事务里（注册）/ 加锁重读后（登录）用 `userReadPort.findAccount(id).orElseThrow()` 取读模型。
  - `@Builder record CurrentUserResponse(Long userId, String email, boolean emailVerified, String username, String displayName, String avatarUrl, UserStatus status, Instant deletionDueAt, Instant nextUsernameChangeAt, String accountType)`，`static CurrentUserResponse from(UserAccountReadModel account)`（`avatarUrl` 恒 `null`，`accountType` 是 `AccountType.USER.getCode()`）。
  - `record AuthenticatedUserResponse(String sessionToken, CurrentUserResponse user)`，`of()`。

- [ ] **Step 1: 写失败的测试**

`UserReadAdapterIT`：`should_fall_back_to_username_when_display_name_is_empty`（存用户 + 空资料，`findAccount` 的 `displayName` 等于用户名、`emailVerified` 假、`nextUsernameChangeAt` 为 `null`）；`should_use_display_name_and_compute_next_username_change`（资料 `rename` 成 `陈宇`，SQL 把 `username_changed_at` 设成 `T0`，`displayName` 是 `陈宇`、`nextUsernameChangeAt` 是 `T0 + 30d`）；`should_fail_loudly_when_profile_missing`。

`AuthControllerIT`：注册、登录的 200 / 201 响应体断言 `sessionToken` 与 `user.userId`（字符串）、`user.username`、`user.displayName`、`user.emailVerified`、`user.status`、`user.avatarUrl`（键存在且为 `null`）、`user.accountType = "user"`；`/auth/me` 断言同一组字段加 `deletionDueAt`、`nextUsernameChangeAt`。

`UserStatusFlowIT`（`@SpringBootTest(webEnvironment = RANDOM_PORT)`，注解组合照 `SessionFlowIT`，AC 6）：

```java
@Test
void should_branch_login_by_status() {
  String userId = registerAndGetUserId("status@example.com", "status-user", "Correct-Horse-01");
  setStatus(userId, "PENDING_DELETION");               // deletion_due_at = now + 15d
  EntityExchangeResult<String> pending = login("status@example.com", "Correct-Horse-01");
  assertThat(pending.getStatus().value()).isEqualTo(200);
  JsonNode user = JSON.readTree(pending.getResponseBody()).get("user");
  assertThat(user.get("status").asString()).isEqualTo("PENDING_DELETION");
  assertThat(user.get("deletionDueAt").isNull()).isFalse();
  assertThat(me(tokenOf(pending)).getStatus().value()).isEqualTo(200);

  setStatus(userId, "BANNED");
  assertThat(login("status@example.com", "Correct-Horse-01").getStatus().value()).isEqualTo(403);
  assertThat(me(tokenOf(pending)).getStatus().value()).isEqualTo(401);

  setDeleted(userId);                                  // status DELETED、email NULL、deleted_at、删凭据
  EntityExchangeResult<String> deleted = login("status@example.com", "Correct-Horse-01");
  EntityExchangeResult<String> wrong = login("nobody@example.com", "Wrong-Pass-01");
  assertThat(deleted.getStatus().value()).isEqualTo(401);
  assertThat(comparable(deleted.getResponseBody())).isEqualTo(comparable(wrong.getResponseBody()));
  assertThat(me(tokenOf(pending)).getStatus().value()).isEqualTo(401);
}
```

`setStatus` / `setDeleted` 用 `JdbcTemplate` 直接改列；`me(token)` 带 `Authorization: Bearer` 调 `/auth/me`（`RANDOM_PORT` 下网关不在，用 `TestIdentity.headers(userId)` 签断言，照 `SessionFlowIT`）。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*UserReadAdapterIT*" :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*AuthControllerIT*" :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*UserStatusFlowIT*"`
Expected: FAIL

- [ ] **Step 3: 实现读模型、适配器、结果与响应，改两个处理器和 Controller**

处理器单测把对 `RegisterUserResult` / `LoginUserResult` 的断言换成 `AuthenticatedUser.account()`，并 stub `userReadPort.findAccount`。`AccountFlowIT`、`SessionFlowIT` 读 `userId` 的地方改读 `user.userId`。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew spotlessApply && ./gradlew :patra-api:patra-identity:patra-identity-app:check :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity
git commit -m "feat(identity): 登录、注册与当前用户共用账号读模型，响应嵌套 user (PAP-93)"
```

---

### Task 9: 公开身份卡 `GET /users/{username}` 与旧名跳转

**Files:**
- Create: `…/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/read/PublicUserReadModel.java`、`PublicAudience.java`、`PublicUserLookup.java`
- Modify: `…/domain/port/read/UserReadPort.java`、`…/domain/exception/UserNotFoundException.java`（Javadoc：也用于公开身份卡）
- Modify: `…/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/read/UserReadAdapter.java`
- Modify: `…/patra-identity-app/src/main/java/dev/patra/identity/app/usecase/user/query/UserQueryService.java`
- Create: `…/patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/users/PublicUserController.java`、`…/users/response/PublicUserResponse.java`、`…/users/response/MovedUserResponse.java`
- Create: `…/patra-identity-boot/src/integrationTest/java/dev/patra/identity/PublicIdentityFlowIT.java`
- Test: `UserQueryServiceTest`（加）、`UserReadAdapterIT`（加）、`…/patra-identity-adapter/src/integrationTest/java/dev/patra/identity/adapter/rest/users/PublicUserControllerIT.java`

**Interfaces:**
- Consumes: Task 6 的 `FormerUsernameRepository.findReserved`；`CurrentUserPort.current()`（`UserQueryService` 已注入）。
- Produces:
  - `enum PublicAudience { PUBLIC, SELF }`。
  - `@Builder record PublicUserReadModel(long userId, String username, String displayName, UserStatus status, boolean emailVerified)`：`PublicAudience audience()`（`ACTIVE` 且已验证 → `PUBLIC`，否则 `SELF`）；`boolean isVisibleTo(Optional<Long> requesterId)`（`audience() == PUBLIC`，或 `requesterId` 等于 `userId`）。`displayName` 已回退。
  - `sealed interface PublicUserLookup permits Found, Moved`：`record Found(PublicUserReadModel user)`、`record Moved(Username current)`。
  - `UserReadPort` 加 `Optional<PublicUserReadModel> findPublicIdentity(Username username)`、`Optional<PublicUserReadModel> findPublicIdentityByUserId(long userId)`。
  - `UserQueryService.publicIdentity(String rawUsername) → PublicUserLookup`，新依赖 `FormerUsernameRepository`、`Clock`；请求者 = `currentUserPort.current().map(CurrentUser::userId)`；三条路径照 spec §8.5，对请求者不可见与不存在都抛 `UserNotFoundException`；`Username.violation(raw)` 非空时直接抛、不查库。
  - `PublicUserController`：`@RequestMapping("/users")`、`@Tag(name = "Users")`；`@GetMapping("/{username}") ResponseEntity<Object> byUsername(@PathVariable String username)`：`Found` → 200 `PublicUserResponse(username, displayName, avatarUrl, audience)`；`Moved` → `ResponseEntity.status(301).location(URI.create("/users/" + current)).body(MovedUserResponse.of(current))`。

- [ ] **Step 1: 写失败的测试**

`UserQueryServiceTest` 加：

```java
@Test void should_return_found_with_public_audience_for_visible_user() { /* 匿名请求，ACTIVE + 已验证 → Found，audience PUBLIC */ }
@Test void should_throw_404_for_unverified_or_non_active_user_when_requested_by_others() { /* 匿名与别的登录用户各试：未验证、PENDING_DELETION、BANNED */ }
@Test void should_return_found_with_self_audience_for_owner() { /* currentUserPort.current() 是本人：未验证 → Found 且 audience SELF；PENDING_DELETION → Found */ }
@Test void should_return_moved_for_reserved_former_username() { /* findPublicIdentity 空、findReserved 有、findPublicIdentityByUserId 可见 → Moved(当前名) */ }
@Test void should_redirect_owner_requesting_own_former_username_even_when_unverified() { }
@Test void should_throw_404_when_redirect_target_is_not_visible() { }
@Test void should_throw_404_when_former_username_expired() { /* findReserved 空 */ }
@Test void should_return_404_for_syntactically_invalid_username_without_querying() { /* "Chen Yu" → 404，userReadPort 零交互 */ }
@Test void should_fold_path_username_to_lowercase() { /* "Chen-Yu" → findPublicIdentity 收到 chen-yu */ }
```

`PublicUserControllerIT`（`@WebMvcTest` + `@Import(PublicUserController.class)` + `@MockitoBean UserQueryService`）：200 的四个字段（`audience` 是 `PUBLIC`）且 `avatarUrl` 为 `null`；301 的 `Location` 是 `/users/new-name`、体是 `{"username":"new-name"}`；`UserNotFoundException` → 404 `IDN-0404`。

`PublicIdentityFlowIT`（boot，`RANDOM_PORT`）：注册 `chen-yu` → 匿名 `GET /users/chen-yu` 404（未验证）→ 本人带断言（`TestIdentity.headers(userId)`）200 且 `audience` 是 `SELF` → SQL 写 `email_verified_at` → 匿名 200、`displayName` 是 `chen-yu`、`audience` 是 `PUBLIC` → SQL 把用户名改成 `chen-yu-2` 并插旧名 `chen-yu`（`reserved_until` 未来）→ 匿名 `GET /users/chen-yu-2` 的 `displayName` 跟着变成 `chen-yu-2`（昵称为空，回退）→ `GET /users/chen-yu` 301 且体的 `username` 是 `chen-yu-2` → 封禁 → `/users/chen-yu-2` 与 `/users/chen-yu` 都 404。再加 `should_redirect_to_current_name_after_two_renames`：SQL 把用户名改成 `chen-yu-3` 并插两条旧名 `chen-yu`、`chen-yu-2`，`GET /users/chen-yu` 的体是 `chen-yu-3`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*UserQueryServiceTest*" :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*PublicUserControllerIT*" :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*PublicIdentityFlowIT*"`
Expected: FAIL

- [ ] **Step 3: 实现**

`UserReadAdapterIT` 加 `findPublicIdentity` / `findPublicIdentityByUserId` 的字段断言。`RestTestClient` 对 301 不跟随，直接断言状态与体。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2，加 `:patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*UserReadAdapterIT*"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity
git commit -m "feat(identity): 加公开身份卡接口与旧用户名 301 跳转 (PAP-93)"
```

---

### Task 10: `PATCH /me/username`

**Files:**
- Create: `…/patra-identity-app/src/main/java/dev/patra/identity/app/usecase/username/ChangeUsernameCommand.java`、`ChangeUsernameResult.java`、`ChangeUsernameHandler.java`
- Create: `…/patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/me/MeController.java`、`…/me/request/ChangeUsernameRequest.java`、`…/me/response/ChangeUsernameResponse.java`
- Create: `…/patra-identity-boot/src/integrationTest/java/dev/patra/identity/config/IdentityClockITSpringConfig.java`、`…/patra-identity-boot/src/integrationTest/java/dev/patra/identity/UsernameChangeFlowIT.java`
- Test: `…/patra-identity-app/src/test/java/dev/patra/identity/app/usecase/username/ChangeUsernameHandlerTest.java`、`…/patra-identity-adapter/src/integrationTest/java/dev/patra/identity/adapter/rest/me/MeControllerIT.java`、`UserStatusFlowIT`（加一条）

**Interfaces:**
- Consumes: `User.changeUsername`、`FormerUsernameRepository`、`UsernameChangePolicy`、`UsernamePolicy`、`CurrentUserPort`、Task 1 的 `MutableClock`。
- Produces:
  - `record ChangeUsernameCommand(String username) implements Command<ChangeUsernameResult>`，`of()`。
  - `record ChangeUsernameResult(String username, Instant nextUsernameChangeAt)`，`of()`。
  - `ChangeUsernameHandler`（`@Transactional`）依赖 `CurrentUserPort`、`UsernamePolicy`、`UserRepository`、`FormerUsernameRepository`、`UsernameChangePolicy`、`Clock`；顺序照 spec §8.4 第 1 ~ 6 步。
  - `MeController`：`@RequestMapping("/me")`、`@Tag(name = "Me")`；`@PatchMapping("/username") ChangeUsernameResponse changeUsername(@RequestBody ChangeUsernameRequest)`。
  - `IdentityClockITSpringConfig`：`@TestConfiguration(proxyBeanMethods = false)`，`@Bean @Primary MutableClock clock()` 从 `2026-10-20T08:00:00Z` 开始。

- [ ] **Step 1: 写失败的测试**

`ChangeUsernameHandlerTest`（端口用 mock，`Clock.fixed`）：

```java
@Test void should_change_name_and_reserve_previous() { /* 加锁查到 ACTIVE 用户 → save(user) 的用户名是新名 → formerUsernames.save 的旧名是原名、reservedUntil = now + 90d → 结果 nextUsernameChangeAt = now + 30d */ }
@Test void should_reject_invalid_username_before_touching_repository() { /* "ab" → InvalidUserFieldsException，users 零交互 */ }
@Test void should_reject_taken_or_reserved_by_other() { /* existsByUsername 真 → 409 username/ALREADY_TAKEN；existsReservedByOther 真 → 同上；都假且是自己的旧名 → 成功 */ }
@Test void should_propagate_cooldown_and_status_exceptions() { /* 聚合抛 UsernameChangeCooldownException / UserPendingDeletionException 原样抛出，不保存 */ }
@Test void should_return_401_when_user_row_missing() { }
```

`MeControllerIT`（`@WebMvcTest` + `@Import(MeController.class)` + `@MockitoBean CommandBus`）：200 的两个字段；422 `UNCHANGED`；409 `errors[]`；409 `traits` 同时含 `CONFLICT` 与 `PENDING_DELETION`；429 带 `Retry-After: 1728000`（20 天）与 `retryAfterSeconds`；`AuthenticationRequiredException` → 401。

`UsernameChangeFlowIT`（boot，`@Import(IdentityClockITSpringConfig.class)`，AC 4）：

```java
@Test
void should_enforce_cooldown_reservation_and_redirect() {
  String token = registerAndVerify("alice@example.com", "alice", "Correct-Horse-01");   // 注册 + SQL 写 email_verified_at
  String bob = registerAndVerify("bob@example.com", "bob", "Correct-Horse-01");

  assertThat(changeUsername(token, "alice-2").getStatus().value()).isEqualTo(200);
  EntityExchangeResult<String> tooSoon = changeUsername(token, "alice-3");
  assertThat(tooSoon.getStatus().value()).isEqualTo(429);
  assertThat(JSON.readTree(tooSoon.getResponseBody()).get("retryAfterSeconds").asLong()).isEqualTo(Duration.ofDays(30).toSeconds());
  assertThat(JSON.readTree(me(token).getResponseBody()).get("nextUsernameChangeAt").asString()).isEqualTo("2026-11-19T08:00:00Z");

  assertThat(changeUsername(bob, "alice").getStatus().value()).isEqualTo(409);            // 别人改成保留中的旧名
  assertThat(register("carol@example.com", "alice", "Correct-Horse-01").getStatus().value()).isEqualTo(409);   // 别人注册
  EntityExchangeResult<String> moved = get("/users/alice");
  assertThat(moved.getStatus().value()).isEqualTo(301);
  assertThat(JSON.readTree(moved.getResponseBody()).get("username").asString()).isEqualTo("alice-2");

  clock.advance(Duration.ofDays(30));
  assertThat(changeUsername(token, "alice").getStatus().value()).isEqualTo(200);         // 原主改回

  clock.advance(Duration.ofDays(31));                                                     // 距第二次改名 31 天，alice-2 仍在保留期
  assertThat(register("carol@example.com", "alice-2", "Correct-Horse-01").getStatus().value()).isEqualTo(409);
  clock.advance(Duration.ofDays(60));                                                     // 距第二次改名 91 天，释放
  assertThat(register("carol@example.com", "alice-2", "Correct-Horse-01").getStatus().value()).isEqualTo(201);
  assertThat(get("/users/alice-2").getStatus().value()).isEqualTo(404);                  // carol 未验证邮箱
}
```

`changeUsername(token, name)` 用 `TestIdentity.headers(userId)` 签断言调 `PATCH /me/username`。拨时钟只影响 identity 的 `Clock`，Redis 的 TTL 走真实时间，本用例不依赖会话过期。

`UserStatusFlowIT` 加：`should_return_401_when_user_is_banned_or_deleted`（`PATCH /me/username` 带有效断言，用户被 SQL 改成 `BANNED` / `DELETED` 后各 401）；`should_return_409_with_pending_deletion_trait`（`PENDING_DELETION` 时 409 且 `traits` 含 `PENDING_DELETION`）。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*ChangeUsernameHandlerTest*" :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*MeControllerIT*" :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*UsernameChangeFlowIT*" --tests "*UserStatusFlowIT*"`
Expected: FAIL

- [ ] **Step 3: 实现命令、处理器、Controller、时钟配置**

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity
git commit -m "feat(identity): 加改用户名接口：冷却、旧名保留与占用检查 (PAP-93)"
```

---

### Task 11: 网关路由规则

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/security/GatewaySecurityConfiguration.java`（`IDENTITY_PUBLIC_PATHS` 加 `"/patra-identity/users/**"`，Javadoc 加「公开身份卡与头像」）
- Test: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayAuthenticationIT.java`

**Interfaces:**
- Consumes: 本类现有的 WireMock `identity` 实例与建会话的 helper。

- [ ] **Step 1: 写失败的测试**

```java
@Test
void should_forward_public_user_page_anonymously() {
  identity.stubFor(get(urlPathEqualTo("/users/chen-yu")).willReturn(okJson("{\"username\":\"chen-yu\"}")));
  restClient.get().uri("/patra-identity/users/chen-yu").exchange().expectStatus().isOk();
  identity.verify(getRequestedFor(urlPathEqualTo("/users/chen-yu")));
}

@Test
void should_pass_through_301_from_identity() {
  identity.stubFor(get(urlPathEqualTo("/users/old-name")).willReturn(aResponse().withStatus(301)
      .withHeader("Location", "/users/new-name").withHeader("Content-Type", "application/json")
      .withBody("{\"username\":\"new-name\"}")));
  restClient.get().uri("/patra-identity/users/old-name").exchange()
      .expectStatus().isEqualTo(301)
      .expectHeader().valueEquals("Location", "/users/new-name")
      .expectBody().jsonPath("$.username").isEqualTo("new-name");
}

@Test
void should_require_session_for_me_paths() {
  identity.stubFor(patch(urlPathEqualTo("/me/username")).willReturn(okJson("{}")));
  restClient.patch().uri("/patra-identity/me/username").exchange().expectStatus().isUnauthorized();
  identity.verify(0, patchRequestedFor(urlPathEqualTo("/me/username")));
  restClient.patch().uri("/patra-identity/me/username").header(HttpHeaders.AUTHORIZATION, BEARER + issueToken()).exchange().expectStatus().isOk();
  identity.verify(1, patchRequestedFor(urlPathEqualTo("/me/username")).withHeader("X-Patra-Identity-Assertion", matching(".+")));
}
```

断言头的名字以本类现有用例为准。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayAuthenticationIT*"`
Expected: 第一、二条 FAIL（401），第三条 PASS

- [ ] **Step 3: 加公开路径**

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/security/GatewaySecurityConfiguration.java patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayAuthenticationIT.java
git commit -m "feat(gateway): 公开 identity 的 /users/** 路径 (PAP-93)"
```

---

### Task 12: README、设计文档写回、全量回归、库重建

**Files:**
- Modify: `patra-api/patra-identity/README.md`（spec §15 的五处）
- Modify: `docs/patra/specs/2026-10-20-identity-account-rebuild-design.md`（第 14 节第 4 ~ 10 条写回实测结果；状态行改「已实施」）
- Modify: `docs/patra/release-specs/v0.12-user-center.md`（spec §17 的三处补充）

- [ ] **Step 1: 改 README 与设计文档**

README 的接口表照 spec §8 的最终形状写；错误码表照 spec §10；加「用户名」一节（规则、保留字在 `ReservedUsernames`、冷却与保留的配置键）；本地运行一节加库重建的两条 SQL。

- [ ] **Step 2: 全量回归**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: `BUILD SUCCESSFUL`

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest :patra-api:patra-gateway-boot:integrationTest`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 本机重建 `patra_identity` 并启动 identity 验证 Flyway 只跑 `V1`**

```bash
docker exec patra-postgres psql -U postgres -c 'DROP DATABASE IF EXISTS patra_identity WITH (FORCE);' -c 'CREATE DATABASE patra_identity;'
```

然后在 IDEA 里启动 `PatraIdentityApplication`，日志里 Flyway 只应用 `V1`，`flyway_schema_history` 只有一行；用 curl 走一遍：注册（带 `username`）→ `/auth/me` → `PATCH /me/username` → `GET /users/<旧名>` 301。

- [ ] **Step 4: 提交**

```bash
git add patra-api/patra-identity/README.md docs/patra/specs/2026-10-20-identity-account-rebuild-design.md docs/patra/release-specs/v0.12-user-center.md
git commit -m "docs(identity): 更新 README 与账号重建设计的实测结果 (PAP-93)"
```

- [ ] **Step 5: mini 上重建库（部署时执行，随后端 PR 合并、CD 部署 identity 之后）**

```bash
ssh mini 'docker stop patra-identity; docker exec patra-postgres psql -U postgres -c "DROP DATABASE IF EXISTS patra_identity WITH (FORCE);" -c "CREATE DATABASE patra_identity;"; docker start patra-identity; docker logs --tail 50 patra-identity'
```

旧库还在时新镜像会因 Flyway 校验失败（`V1` 校验和不同、`V2` 已删）起不来，重建库后 `docker start` 即可。不做 `pg_dump`：开发数据可丢弃（决策 J）。之后按 spec §17 同步 Linear（PAP-93 / 95 / 98 / 100 / 101 / 102 的描述）。

---

## 自查记录

- **Spec 覆盖**：§6 五张表 → Task 5、6、7；§7.1 ~ 7.6 → Task 2 ~ 7；§8.1 注册 → Task 7、8；§8.2 登录分流 → Task 7、8；§8.3 → Task 8；§8.4 → Task 10；§8.5 → Task 9；§8.6 后台 409 → Task 7（聚合）；§9 → Task 11；§10 错误契约 → Task 4、7、10；§11 配置 → Task 4；§12 预留实体只在文档里，不需任务；§13 测试 → 各任务；§14 写回、§15 README、§13.3 库重建 → Task 12。
- **类型一致**：`UserFieldViolations` 的方法名在 Task 2、3、7、10 一致；`UserReadPort` 的三个方法在 Task 8、9 一致；`AuthenticatedUser` / `CurrentUserResponse` / `AuthenticatedUserResponse` 在 Task 8 定义、Task 10 的 IT 复用。
- **Review Focus** 五条分别落在 Task 3、6、9、9、10。
- **比例**：代码块只有测试和 `idn_user` 的 DDL（spec 固定的值），实现步骤给签名和顺序。
