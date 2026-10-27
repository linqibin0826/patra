# identity 提及支撑 实施计划（PAP-118）

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（本仓库约定在当前会话内联执行，不用 subagent-driven）。步骤用 `- [ ]` 跟踪。

**Goal:** identity 给 interaction 两个内部接口：按用户名前缀联想（只出主页对访客可见的正常账号），按用户名批量解析（含 90 天保留期内的旧名）。

**Architecture:** 契约加在 `patra-identity-api` 的 `UserEndpoint`；app 层两个只读查询服务；主页可见性只经 `UserSummaryReadModel.access()`（`ProfileAccess`）判；前缀查询走 Flyway `V2` 建的 `COLLATE "C"` 部分 btree 索引，查询写范围谓词。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Data JPA（Hibernate）、PostgreSQL 17、Flyway、JUnit 5 + AssertJ + Mockito、Testcontainers。

**Spec:** `docs/patra/specs/2026-10-27-identity-mention-support-design.md`

## Global Constraints

- 端点：`POST /_internal/users/suggest`、`POST /_internal/users/resolve/by-usernames`，都在 `UserEndpoint`（`@HttpExchange(url = "/_internal/users")`）上。
- 联想：`limit` 必填、1–20（`MAX_LIMIT = 20`）；候选上限 `CANDIDATE_CAP = 200`；只认 `ACTIVE`；排除访客本人；可见性只用 `UserSummaryReadModel.access().isVisibleTo(viewerId)`；按用户名字节序升序。
- 前缀语法：`Username.normalize` 之后长度 1–30、整串匹配 `^[a-z0-9]([_-]?[a-z0-9])*[_-]?$`；不满足 → 空结果、不查库。
- 解析：`usernames` 最多 100（`MAX_USERNAMES = 100`，按去重前长度）；列表 `null` → 422 `REQUIRED`，超限 → 422 `TOO_MANY`；`null` 元素与 `Username.violation` 不为空的元素跳过；按原文去重、回显原文；先当前名、再保留期内旧名（`reserved_until > now`，取 `replaced_at` 最大的一条）；四种状态原样返回。
- 422 一律 `InvalidUserFieldsException`；字段名 `prefix`、`limit`、`usernames`；原因码 `REQUIRED`、`INVALID_FORMAT`、`TOO_MANY`（都已存在）。
- 索引：`V2__add_mention_prefix_indexes.sql` 只有 `CREATE INDEX idx_idn_user_username_prefix ON idn_user (username COLLATE "C") WHERE status = 'ACTIVE';`；查询写 `username COLLATE "C" >= :from AND username COLLATE "C" < :to`，不用 `LIKE`。
- 两个服务只读，不经 CommandBus，没有事务。
- 代码规范：每个方法写 `///` Javadoc（Markdown，行宽 ≤ 100，见 gjf 折行坑）；≤ 4 个参数的 record 用 `of()`；集合用 `List.of()` / `List.copyOf()`；不写全类名；Lombok `@RequiredArgsConstructor`。
- 测试：方法名 snake_case `should_…`；单测 JUnit 5 + AssertJ + Mockito；集成测试类名 `*IT`。

## Review Focus

- **前缀里的 `_` 与 `-`**：`chen_` 不能命中 `chena`（`LIKE` 会当通配符），`chen-` 不能命中 `chen0`、`chen_li`。→ 任务 2 `should_not_treat_separators_as_wildcards`。
- **旧名的原主已不是正常账号**（改名后被封禁、进冷静期或注销）：解析出原主 ID 与原主**当前**状态，不能当成 `ACTIVE`。→ 任务 6 `should_fall_back_to_reserved_former_username_with_owner_status`。
- **同一个人的不同写法**（`Chen-Yu`、` chen-yu `、`chen-yu`）：各按原文回显，`userId` 相同；只查一次库。→ 任务 6 `should_keep_request_order_dedupe_exact_text_and_echo_each_spelling`、任务 8 解析流程。
- **访客本人**：即使主页仅自己可见、邮箱未验证，也不出现在自己的联想里。→ 任务 5 `should_drop_viewer_and_accounts_whose_profile_the_viewer_cannot_see`、任务 8 矩阵的「本人」一列。
- **一个字符的前缀命中大量账号**：只扫 200 个候选、按字节序、结果不超过 `limit`。→ 任务 5 `should_read_normalized_prefix_with_candidate_cap`、`should_keep_reader_order_and_cut_to_limit`；任务 2 `should_cut_prefix_candidates_to_limit`。

## 执行约定

- 每个任务里的测试**逐条**红绿：一次只写一个失败的测试，跑红，写最少的实现，跑绿，再写下一条。
- **本会话不提交**：任务结束只跑验证；提交等用户同意后统一做，只加本 Issue 的路径。到时的提交信息：`feat(identity): 新增 @ 提及支撑的前缀联想与用户名批量解析 (PAP-118)`（subject 以中文动词开头，body 每行 ≤ 100 字符）。
- 在 worktree 根目录 `/Users/linqibin/Projects/Products/patra/.claude/worktrees/v014-identity-mention-482011` 跑命令，路径一律绝对，不 `cd`；不用 JetBrains MCP。
- RED 预期只写「编译失败或断言失败」，不点名具体符号。
- 跑 `check` 之前先 `./gradlew spotlessApply`；`check` 不含 `integrationTest`，IT 单独跑；identity 的 IT 超过 2 分钟的放后台跑。
- 下文的模块前缀：`ID=:patra-api:patra-identity`，如 `$ID:patra-identity-domain:test` 即 `:patra-api:patra-identity:patra-identity-domain:test`。

## 文件结构

| 文件（`patra-api/patra-identity/` 下，网关除外） | 动作 | 职责 |
|---|---|---|
| `patra-identity-domain/.../domain/model/vo/UsernamePrefix.java` | 新建 | 前缀值对象（任务 1） |
| `patra-identity-domain/.../domain/port/read/UserReadPort.java` | 改 | 两个读方法（任务 2、3） |
| `patra-identity-domain/.../domain/port/repository/FormerUsernameRepository.java` | 改 | 批量查保留旧名（任务 4） |
| `patra-identity-domain/.../domain/model/vo/UserFieldViolations.java` | 改 | 三个字段名、五个工厂方法（任务 5、6） |
| `patra-identity-infra/.../infra/adapter/persistence/dao/UserDao.java` | 改 | 范围查询、按用户名 `IN`（任务 2、3） |
| `patra-identity-infra/.../infra/adapter/read/UserReadAdapter.java` | 改 | 两个读方法的实现（任务 2、3） |
| `patra-identity-infra/.../infra/adapter/persistence/dao/FormerUsernameDao.java` | 改 | 按名字 `IN` 查未到期（任务 4） |
| `patra-identity-infra/.../infra/adapter/persistence/FormerUsernameRepositoryAdapter.java` | 改 | 按名字取最近一条（任务 4） |
| `patra-identity-infra/src/main/resources/db/migration/V2__add_mention_prefix_indexes.sql` | 新建 | 前缀索引（任务 2） |
| `patra-identity-app/.../app/usecase/user/query/UserSuggestionQueryService.java` | 新建 | 联想（任务 5） |
| `patra-identity-app/.../app/usecase/user/query/UserResolutionQueryService.java`、`ResolvedUser.java` | 新建 | 解析（任务 6） |
| `patra-identity-api/.../api/endpoint/UserEndpoint.java` | 改 | 两个方法（任务 7） |
| `patra-identity-api/.../api/dto/UserSuggestReq.java`、`UserSuggestionsResp.java`、`UserResolveByUsernamesReq.java`、`UserResolveResp.java`、`UserResolutionResp.java` | 新建 | DTO（任务 7） |
| `patra-identity-adapter/.../adapter/rest/internal/UserEndpointImpl.java` | 改 | 两个端点（任务 7） |
| `patra-identity-boot/src/integrationTest/.../IdentityFlowITSupport.java`、`UserSummariesFlowIT.java` | 改 | 共用的改状态、改隐私、验邮箱辅助方法挪进基座（任务 8） |
| `patra-identity-boot/src/integrationTest/.../UserSuggestionsFlowIT.java`、`UserResolutionFlowIT.java` | 新建 | 完整流程（任务 8） |
| `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayBlockedPathsIT.java` | 改 | 拒绝名单加两条（任务 9） |
| `README.md` | 改 | 第 1、2 节与新第 17 节（任务 10） |

---

### Task 1: 前缀值对象 `UsernamePrefix`

**Files:**
- Create: `patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/UsernamePrefix.java`
- Test: `patra-identity-domain/src/test/java/dev/patra/identity/domain/model/vo/UsernamePrefixTest.java`

**Interfaces:**
- Produces: `public record UsernamePrefix(String value)`；`public static Optional<UsernamePrefix> parse(String raw)`。紧凑构造器只接受已规范化且合法的值，否则 `IllegalArgumentException`。

- [ ] **Step 1: 逐条写失败的测试**（`class UsernamePrefixTest`，Javadoc：`/// UsernamePrefix 单元测试：可能是某个合法用户名开头的才算前缀（PAP-118 spec 4.4）。`）

```java
@ParameterizedTest
@ValueSource(strings = {"a", "ab", "chen-", "chen_y", "123", "a-b_c"})
void should_accept_possible_username_beginnings(String raw) {
  assertThat(UsernamePrefix.parse(raw)).map(UsernamePrefix::value).contains(raw);
}

@Test
void should_accept_up_to_30_characters() {
  assertThat(UsernamePrefix.parse("a".repeat(30))).isPresent();
  assertThat(UsernamePrefix.parse("a".repeat(31))).isEmpty();
}

@Test
void should_normalize_before_checking() {
  assertThat(UsernamePrefix.parse(" Chen-Y ")).map(UsernamePrefix::value).contains("chen-y");
}

@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {"   ", "-a", "_a", "a--", "a-_", "a b", "a%", "陈", "ｃｈｅｎ", "@chen"})
void should_reject_impossible_beginnings(String raw) {
  assertThat(UsernamePrefix.parse(raw)).isEmpty();
}

@Test
void should_only_construct_from_normalized_valid_value() {
  assertThatThrownBy(() -> new UsernamePrefix("Chen")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> new UsernamePrefix("a--")).isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: 跑红** — `./gradlew $ID:patra-identity-domain:test --tests "*UsernamePrefixTest"`，预期编译失败。
- [ ] **Step 3: 实现** — 规则：`raw == null` → 空；`Username.normalize(raw)` 后先判长度 `≤ Username.MAX_LENGTH`、再判正则（长度先判，正则只跑在 ≤ 30 个字符上）；构造器复用同一个判定。Javadoc 写清「比用户名语法宽、不看保留字」。
- [ ] **Step 4: 跑绿** — 同上命令，PASS。

---

### Task 2: 前缀读端口、范围查询与 `V2` 索引

**Files:**
- Modify: `patra-identity-domain/src/main/java/dev/patra/identity/domain/port/read/UserReadPort.java`
- Modify: `patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/persistence/dao/UserDao.java`
- Modify: `patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/read/UserReadAdapter.java`
- Create: `patra-identity-infra/src/main/resources/db/migration/V2__add_mention_prefix_indexes.sql`
- Test: `patra-identity-infra/src/integrationTest/java/dev/patra/identity/infra/adapter/read/UserReadAdapterIT.java`

**Interfaces:**
- Consumes: `UsernamePrefix`（任务 1）。
- Produces: `UserReadPort.findActiveSummariesByUsernamePrefix(UsernamePrefix prefix, int limit)` → `List<UserSummaryReadModel>`：只含 `ACTIVE`，按用户名字节序升序，最多 `limit` 个。

- [ ] **Step 1: 逐条写失败的测试**（加在 `UserReadAdapterIT`；新增私有辅助：`saveWithProfile(String username)` 返回 ID——`User.register` + `UserProfile.create`；`saveWithStatus(String username, String status)`——先 `saveWithProfile`，再用 SQL 改状态（同 `UserSummariesFlowIT.applyStatus` 的三条 `UPDATE`），之后 `entityManager.clear()`；`prefix(String raw)` = `UsernamePrefix.parse(raw).orElseThrow()`）

```java
@Test
void should_find_active_summaries_by_username_prefix_in_byte_order() {
  for (String name : List.of("chena", "chen_li", "chen9", "chen-yu", "chen", "chex")) {
    saveWithProfile(name);
  }
  saveWithStatus("chen-banned", "BANNED");
  saveWithStatus("chen-wait", "PENDING_DELETION");
  saveWithStatus("chen-gone", "DELETED");

  assertThat(reader.findActiveSummariesByUsernamePrefix(prefix("chen"), 10))
      .extracting(UserSummaryReadModel::username)
      .containsExactly("chen", "chen-yu", "chen9", "chen_li", "chena");
}

@Test
void should_not_treat_separators_as_wildcards() {
  for (String name : List.of("chen-yu", "chen_li", "chena", "chen0")) {
    saveWithProfile(name);
  }

  assertThat(reader.findActiveSummariesByUsernamePrefix(prefix("chen_"), 10))
      .extracting(UserSummaryReadModel::username)
      .containsExactly("chen_li");
  assertThat(reader.findActiveSummariesByUsernamePrefix(prefix("chen-"), 10))
      .extracting(UserSummaryReadModel::username)
      .containsExactly("chen-yu");
}

@Test
void should_cut_prefix_candidates_to_limit() {
  for (String name : List.of("chen9", "chen", "chen-yu")) {
    saveWithProfile(name);
  }

  assertThat(reader.findActiveSummariesByUsernamePrefix(prefix("chen"), 2))
      .extracting(UserSummaryReadModel::username)
      .containsExactly("chen", "chen-yu");
}

@Test
void should_index_active_usernames_in_byte_order() {
  String definition =
      jdbcTemplate.queryForObject(
          "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_idn_user_username_prefix'",
          String.class);

  assertThat(definition).contains("(username COLLATE \"C\")").contains("'ACTIVE'");
}
```

- [ ] **Step 2: 跑红** — `./gradlew $ID:patra-identity-infra:integrationTest --tests "*UserReadAdapterIT"`，预期编译失败。
- [ ] **Step 3: 实现**
  - `V2__add_mention_prefix_indexes.sql`：文件头注释写清 PAP-118、为什么 `COLLATE "C"`（库默认 `en_US.utf8`）、为什么部分索引、查询必须写范围谓词；正文只有 Global Constraints 里那条 `CREATE INDEX`。
  - `UserDao.findActiveByUsernameRange(@Param("from") String from, @Param("to") String to, @Param("limit") int limit)` → `List<UserEntity>`：`@Query(nativeQuery = true)`，SQL 用普通字符串拼（不用文本块，见坑清单）：`SELECT * FROM idn_user WHERE status = 'ACTIVE' AND username COLLATE "C" >= :from AND username COLLATE "C" < :to ORDER BY username COLLATE "C" LIMIT :limit`。`status` 必须写字面量，部分索引才在通用计划下也能匹配。
  - `UserReadAdapter.findActiveSummariesByUsernamePrefix`：`from = prefix.value()`，`to` 由私有静态方法 `upperBound(String prefix)` 算（末字符码位加一）。把 `findSummaries` 里「资料 `IN` 一次取齐 + 逐行 `toSummary`」抽成私有方法 `summariesOf(List<UserEntity> users)`（按传入的顺序输出），`findSummaries` 与本方法共用；本方法要的是 DAO 的字节序，所以顺序由 `summariesOf` 保证。
- [ ] **Step 4: 跑绿** — 同上命令，PASS（含原有用例）。

---

### Task 3: 按用户名批量读摘要

**Files:**
- Modify: `UserReadPort.java`、`UserDao.java`、`UserReadAdapter.java`（同任务 2）
- Test: `UserReadAdapterIT.java`

**Interfaces:**
- Produces: `UserReadPort.findSummariesByUsernames(Collection<Username> usernames)` → `List<UserSummaryReadModel>`：存在的那些（四种状态都有），不保证顺序；空集合直接返回 `List.of()`。

- [ ] **Step 1: 逐条写失败的测试**

```java
@Test
void should_read_summaries_by_usernames() {
  long active = saveWithProfile("by-name-a");
  long banned = saveWithStatus("by-name-b", "BANNED");

  Map<String, UserSummaryReadModel> found =
      reader
          .findSummariesByUsernames(
              List.of(Username.of("by-name-a"), Username.of("by-name-b"), Username.of("by-name-x")))
          .stream()
          .collect(Collectors.toMap(UserSummaryReadModel::username, Function.identity()));

  assertThat(found).containsOnlyKeys("by-name-a", "by-name-b");
  assertThat(found.get("by-name-a").userId()).isEqualTo(active);
  assertThat(found.get("by-name-b").userId()).isEqualTo(banned);
  assertThat(found.get("by-name-b").status()).isEqualTo(UserStatus.BANNED);
}

@Test
void should_return_empty_summaries_for_no_usernames() {
  assertThat(reader.findSummariesByUsernames(List.of())).isEmpty();
}
```

- [ ] **Step 2: 跑红** — 同任务 2 的命令，预期编译失败。
- [ ] **Step 3: 实现** — `UserDao.findByUsernameIn(Collection<String> usernames)`（派生查询）；`findSummariesByUsernames` 空集合直接 `List.of()`，否则 `summariesOf(userDao.findByUsernameIn(值))`（任务 2 抽出的私有方法）。
- [ ] **Step 4: 跑绿** — 同上，PASS。

---

### Task 4: 批量查保留期内的旧名

**Files:**
- Modify: `patra-identity-domain/src/main/java/dev/patra/identity/domain/port/repository/FormerUsernameRepository.java`
- Modify: `patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/persistence/dao/FormerUsernameDao.java`
- Modify: `patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/persistence/FormerUsernameRepositoryAdapter.java`
- Test: `patra-identity-infra/src/integrationTest/java/dev/patra/identity/infra/adapter/persistence/FormerUsernameRepositoryAdapterIT.java`

**Interfaces:**
- Produces: `FormerUsernameRepository.findAllReserved(Collection<Username> usernames, Instant now)` → `List<FormerUsername>`：此刻未到期的记录，每个名字最多一条（`replacedAt` 最大的），不保证顺序。

- [ ] **Step 1: 逐条写失败的测试**

```java
@Test
@DisplayName("批量：每个名字取未到期里改名时间最近的一条；过期的、没保留过的不返回")
void should_find_latest_reserved_record_for_each_name() {
  long a = saveUser("former-d@example.com");
  long b = saveUser("former-e@example.com");
  Duration period = Duration.ofDays(90);
  formerUsernames.save(FormerUsername.reserve(a, Username.of("dup"), T0, period));
  formerUsernames.save(
      FormerUsername.reserve(b, Username.of("dup"), T0.plus(Duration.ofDays(10)), period));
  formerUsernames.save(
      FormerUsername.reserve(a, Username.of("kept"), T0.plus(Duration.ofDays(5)), period));
  formerUsernames.save(
      FormerUsername.reserve(a, Username.of("gone"), T0.minus(Duration.ofDays(100)), period));

  Map<String, Long> owners =
      formerUsernames
          .findAllReserved(
              List.of(
                  Username.of("dup"), Username.of("kept"), Username.of("gone"), Username.of("free")),
              T0.plus(Duration.ofDays(20)))
          .stream()
          .collect(Collectors.toMap(f -> f.getUsername().value(), FormerUsername::getUserId));

  assertThat(owners).containsOnly(Map.entry("dup", b), Map.entry("kept", a));
}

@Test
@DisplayName("批量：到期当刻不再保留")
void should_not_return_record_at_its_expiry_instant() {
  long a = saveUser("former-f@example.com");
  formerUsernames.save(FormerUsername.reserve(a, Username.of("edge"), T0, Duration.ofDays(90)));
  Instant expiry = T0.plus(Duration.ofDays(90));

  assertThat(formerUsernames.findAllReserved(List.of(Username.of("edge")), expiry)).isEmpty();
  assertThat(formerUsernames.findAllReserved(List.of(Username.of("edge")), expiry.minusMillis(1)))
      .hasSize(1);
}
```

- [ ] **Step 2: 跑红** — `./gradlew $ID:patra-identity-infra:integrationTest --tests "*FormerUsernameRepositoryAdapterIT"`，预期编译失败。
- [ ] **Step 3: 实现** — `FormerUsernameDao.findByUsernameInAndReservedUntilAfter(Collection<String> usernames, Instant now)`；适配器按 `username` 分组取 `replacedAt` 最大的一条，映射成聚合。
- [ ] **Step 4: 跑绿** — 同上，PASS。

---

### Task 5: 联想服务 `UserSuggestionQueryService`

**Files:**
- Modify: `patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/UserFieldViolations.java`
- Create: `patra-identity-app/src/main/java/dev/patra/identity/app/usecase/user/query/UserSuggestionQueryService.java`
- Test: `patra-identity-app/src/test/java/dev/patra/identity/app/usecase/user/query/UserSuggestionQueryServiceTest.java`

**Interfaces:**
- Consumes: `UsernamePrefix.parse`（任务 1）；`UserReadPort.findActiveSummariesByUsernamePrefix`（任务 2）。
- Produces: `UserFieldViolations.PREFIX = "prefix"`、`LIMIT = "limit"`；`prefixRequired()`（`REQUIRED`，「缺少前缀」）、`limitRequired()`（`REQUIRED`，「缺少条数上限」）、`limitOutOfRange()`（`INVALID_FORMAT`，「条数上限是 1 ~ 20」）。`@Service @RequiredArgsConstructor class UserSuggestionQueryService(UserReadPort reader)`：`public static final int MAX_LIMIT = 20`、`CANDIDATE_CAP = 200`；`public List<UserSummaryView> suggest(String prefix, Optional<Long> viewerId, Integer limit)`。

- [ ] **Step 1: 逐条写失败的测试**（辅助：`user(long id, ProfileVisibility visibility, boolean emailVerified)` 造 `ACTIVE`、用户名 `"u-" + id` 的 `UserSummaryReadModel`；`assertRejected(prefix, limit, field, code)` 断言 `InvalidUserFieldsException` 的第一条违例）

```java
@Test
void should_reject_missing_prefix() {
  assertRejected(null, 10, "prefix", "REQUIRED");
}

@Test
void should_reject_missing_limit() {
  assertRejected("chen", null, "limit", "REQUIRED");
}

@ParameterizedTest
@ValueSource(ints = {-1, 0, 21})
void should_reject_limit_out_of_range(int limit) {
  assertRejected("chen", limit, "limit", "INVALID_FORMAT");
  verifyNoInteractions(reader);
}

@Test
void should_report_missing_prefix_and_limit_together() {
  assertThatThrownBy(() -> service.suggest(null, Optional.empty(), null))
      .isInstanceOfSatisfying(
          InvalidUserFieldsException.class,
          e -> assertThat(e.getFieldViolations())
              .extracting(FieldViolation::field)
              .containsExactly("prefix", "limit"));
}

@ParameterizedTest
@ValueSource(strings = {"", "-a", "a--", "陈", "a b"})
void should_return_empty_without_reading_for_impossible_prefix(String prefix) {
  assertThat(service.suggest(prefix, Optional.of(7L), 10)).isEmpty();
  verifyNoInteractions(reader);
}

@Test
void should_read_normalized_prefix_with_candidate_cap() {
  when(reader.findActiveSummariesByUsernamePrefix(any(), anyInt())).thenReturn(List.of());

  service.suggest(" Chen ", Optional.of(7L), 10);

  verify(reader).findActiveSummariesByUsernamePrefix(UsernamePrefix.parse("chen").orElseThrow(), 200);
}

@ParameterizedTest
@ValueSource(ints = {1, 20})
void should_accept_limit_bounds(int limit) {
  when(reader.findActiveSummariesByUsernamePrefix(any(), anyInt())).thenReturn(List.of());

  assertThat(service.suggest("chen", Optional.empty(), limit)).isEmpty();
}

@Test
void should_drop_viewer_and_accounts_whose_profile_the_viewer_cannot_see() {
  List<UserSummaryReadModel> candidates =
      List.of(
          user(7L, ProfileVisibility.PUBLIC, true),
          user(8L, ProfileVisibility.SELF, true),
          user(9L, ProfileVisibility.PUBLIC, false),
          user(10L, ProfileVisibility.SIGNED_IN, true),
          user(11L, ProfileVisibility.PUBLIC, true));
  when(reader.findActiveSummariesByUsernamePrefix(any(), anyInt())).thenReturn(candidates);

  assertThat(service.suggest("u", Optional.of(7L), 10))
      .extracting(UserSummaryView::userId)
      .containsExactly(10L, 11L);
  assertThat(service.suggest("u", Optional.empty(), 10))
      .extracting(UserSummaryView::userId)
      .containsExactly(7L, 11L);
}

@Test
void should_keep_reader_order_and_cut_to_limit() {
  List<UserSummaryReadModel> candidates =
      List.of(
          user(3L, ProfileVisibility.PUBLIC, true),
          user(1L, ProfileVisibility.PUBLIC, true),
          user(2L, ProfileVisibility.PUBLIC, true));
  when(reader.findActiveSummariesByUsernamePrefix(any(), anyInt())).thenReturn(candidates);

  assertThat(service.suggest("u", Optional.empty(), 2))
      .extracting(UserSummaryView::userId)
      .containsExactly(3L, 1L);
}
```

- [ ] **Step 2: 跑红** — `./gradlew $ID:patra-identity-app:test --tests "*UserSuggestionQueryServiceTest"`，预期编译失败。
- [ ] **Step 3: 实现** — 先收齐 `prefix`、`limit` 的违例一起抛；再 `UsernamePrefix.parse`，空则 `List.of()`；读候选；`filter(候选不是访客 && access().isVisibleTo(viewerId))`、`map(viewFor(viewerId))`、`limit(limit)`。类 Javadoc 写清：只读、不经 CommandBus、没有事务；规则引 spec 4.3。
- [ ] **Step 4: 跑绿** — 同上，PASS。

---

### Task 6: 解析服务 `UserResolutionQueryService`

**Files:**
- Modify: `UserFieldViolations.java`
- Create: `patra-identity-app/src/main/java/dev/patra/identity/app/usecase/user/query/ResolvedUser.java`
- Create: `patra-identity-app/src/main/java/dev/patra/identity/app/usecase/user/query/UserResolutionQueryService.java`
- Test: `patra-identity-app/src/test/java/dev/patra/identity/app/usecase/user/query/UserResolutionQueryServiceTest.java`

**Interfaces:**
- Consumes: `UserReadPort.findSummariesByUsernames`（任务 3）、`UserReadPort.findSummaries`（已有）、`FormerUsernameRepository.findAllReserved(Collection, Instant)`（任务 4）。
- Produces: `UserFieldViolations.USERNAMES = "usernames"`；`usernamesRequired()`（`REQUIRED`，「缺少用户名列表」）、`usernamesTooMany()`（`TOO_MANY`，「一次最多 100 个用户名」）。`public record ResolvedUser(String username, long userId, UserStatus status)` + `of(...)`（`username` 是请求原文）。`@Service @RequiredArgsConstructor class UserResolutionQueryService(UserReadPort reader, FormerUsernameRepository formerUsernames, Clock clock)`：`public static final int MAX_USERNAMES = 100`；`public List<ResolvedUser> resolveByUsernames(List<String> usernames)`。

- [ ] **Step 1: 逐条写失败的测试**（`NOW = 2026-10-27T08:00:00Z`，`Clock.fixed(NOW, ZoneOffset.UTC)`；辅助 `user(long id, String username, UserStatus status)` 造邮箱已验证、`PUBLIC` 的 `UserSummaryReadModel`；`assertRejected(usernames, code)` 断言字段 `usernames`）

```java
@Test
void should_reject_null_list() {
  assertRejected(null, "REQUIRED");
}

@Test
void should_reject_more_than_100_even_if_duplicated() {
  assertRejected(Collections.nCopies(101, "chen"), "TOO_MANY");
  verifyNoInteractions(reader, formerUsernames);
}

@Test
void should_return_empty_without_reading_when_nothing_is_a_username() {
  assertThat(service.resolveByUsernames(Arrays.asList(null, "", "ab", "-x", "陈", "a b", "@chen")))
      .isEmpty();
  assertThat(service.resolveByUsernames(List.of())).isEmpty();
  verifyNoInteractions(reader, formerUsernames);
}

@Test
void should_resolve_current_usernames_with_their_status() {
  when(reader.findSummariesByUsernames(any()))
      .thenReturn(List.of(user(1L, "chen-yu", UserStatus.ACTIVE), user(2L, "wang", UserStatus.BANNED)));

  assertThat(service.resolveByUsernames(List.of("chen-yu", "wang")))
      .containsExactly(
          ResolvedUser.of("chen-yu", 1L, UserStatus.ACTIVE),
          ResolvedUser.of("wang", 2L, UserStatus.BANNED));
  verifyNoInteractions(formerUsernames);
}

@Test
void should_fall_back_to_reserved_former_username_with_owner_status() {
  when(reader.findSummariesByUsernames(any())).thenReturn(List.of());
  FormerUsername former =
      FormerUsername.restore(
          100L, 3L, Username.of("old-name"), NOW.minus(Duration.ofDays(1)),
          NOW.plus(Duration.ofDays(89)), 0L);
  when(formerUsernames.findAllReserved(any(), any())).thenReturn(List.of(former));
  when(reader.findSummaries(any()))
      .thenReturn(List.of(user(3L, "new-name", UserStatus.PENDING_DELETION)));

  assertThat(service.resolveByUsernames(List.of("old-name")))
      .containsExactly(ResolvedUser.of("old-name", 3L, UserStatus.PENDING_DELETION));
  verify(reader).findSummaries(argThat(ids -> new ArrayList<>(ids).equals(List.of(3L))));
}

@Test
void should_look_up_former_names_only_for_misses_at_clock_instant() {
  when(reader.findSummariesByUsernames(any()))
      .thenReturn(List.of(user(1L, "chen-yu", UserStatus.ACTIVE)));
  when(formerUsernames.findAllReserved(any(), any())).thenReturn(List.of());

  assertThat(service.resolveByUsernames(List.of("chen-yu", "nobody")))
      .containsExactly(ResolvedUser.of("chen-yu", 1L, UserStatus.ACTIVE));
  verify(formerUsernames)
      .findAllReserved(
          argThat(names -> new ArrayList<>(names).equals(List.of(Username.of("nobody")))),
          eq(NOW));
  verify(reader, never()).findSummaries(any());
}

@Test
void should_keep_request_order_dedupe_exact_text_and_echo_each_spelling() {
  when(reader.findSummariesByUsernames(any()))
      .thenReturn(List.of(user(1L, "chen-yu", UserStatus.ACTIVE), user(2L, "wang", UserStatus.ACTIVE)));

  assertThat(service.resolveByUsernames(List.of("wang", "Chen-Yu", "wang", " chen-yu ")))
      .containsExactly(
          ResolvedUser.of("wang", 2L, UserStatus.ACTIVE),
          ResolvedUser.of("Chen-Yu", 1L, UserStatus.ACTIVE),
          ResolvedUser.of(" chen-yu ", 1L, UserStatus.ACTIVE));
  verify(reader)
      .findSummariesByUsernames(
          argThat(
              names ->
                  new ArrayList<>(names).equals(List.of(Username.of("wang"), Username.of("chen-yu")))));
}
```

- [ ] **Step 2: 跑红** — `./gradlew $ID:patra-identity-app:test --tests "*UserResolutionQueryServiceTest"`，预期编译失败。
- [ ] **Step 3: 实现** — 算法（签名与测试定不下来的部分）：

```text
校验：null → usernamesRequired；size > MAX_USERNAMES → usernamesTooMany（都抛 InvalidUserFieldsException）
requested = 原文按出现顺序去重（LinkedHashSet），只留 Username.violation(raw) 为空的
requested 为空 → List.of()
names = requested 规范化后去重（保序），Username.of(raw)
current = reader.findSummariesByUsernames(names)，按 username 建索引
misses = names 里不在 current 的
former = misses 为空 ? 空 : formerUsernames.findAllReserved(misses, clock.instant())，按 username 建索引
owners = former 为空 ? 空 : reader.findSummaries(former 的 userId 去重)，按 userId 建索引
逐个 raw ∈ requested：name = Username.of(raw)
  current 有 → ResolvedUser.of(raw, 该用户 ID, 状态)
  否则 former 有且 owners 有原主 → ResolvedUser.of(raw, 原主 ID, 原主状态)
  否则跳过
```

  类 Javadoc 写清：只读、不经 CommandBus、没有事务；解析不到属正常情况，不打日志。
- [ ] **Step 4: 跑绿** — 同上，PASS。

---

### Task 7: 契约 DTO 与 `UserEndpointImpl`

**Files:**
- Modify: `patra-identity-api/src/main/java/dev/patra/identity/api/endpoint/UserEndpoint.java`
- Create: `patra-identity-api/src/main/java/dev/patra/identity/api/dto/UserSuggestReq.java`、`UserSuggestionsResp.java`、`UserResolveByUsernamesReq.java`、`UserResolveResp.java`、`UserResolutionResp.java`
- Modify: `patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/internal/UserEndpointImpl.java`
- Test: `patra-identity-adapter/src/integrationTest/java/dev/patra/identity/adapter/rest/internal/UserEndpointImplIT.java`

**Interfaces:**
- Consumes: 任务 5、6 的两个服务与 `ResolvedUser`。
- Produces（契约，见 spec 4.1、5.1）：
  - `record UserSuggestReq(String prefix, Long viewerUserId, Integer limit)` + `of`
  - `record UserSuggestionsResp(List<UserSummaryResp> users)` + `of`，紧凑构造器 `copyOf`、`null` 视为空
  - `record UserResolveByUsernamesReq(List<String> usernames)` + `of`，**不**做防御性拷贝（Javadoc 照 `UserSummariesReq` 写理由）
  - `record UserResolveResp(List<UserResolutionResp> users)` + `of`，紧凑构造器 `copyOf`、`null` 视为空
  - `record UserResolutionResp(String username, Long userId, AccountStatus accountStatus)` + `of`
  - `UserEndpoint`：`@PostExchange("/suggest") UserSuggestionsResp suggest(@RequestBody UserSuggestReq request)`；`@PostExchange("/resolve/by-usernames") UserResolveResp resolveByUsernames(@RequestBody UserResolveByUsernamesReq request)`；接口 Javadoc 的端点列表与「用途」改为三个端点。

- [ ] **Step 1: 逐条写失败的测试**（`UserEndpointImplIT` 加两个 `@MockitoBean`：`UserSuggestionQueryService`、`UserResolutionQueryService`；把现有私有 `post(String json)` 改成 `post(String uri, String json)`，原有三个用例改调新签名；新增 `postExpecting422(String uri, String json)` 返回 `errors[0]`）

```java
@Test
@DisplayName("联想：三个字段原样到服务；响应项是摘要卡片，用户 ID 是字符串")
void should_return_suggestions_as_summary_cards() {
  when(userSuggestionQueryService.suggest("chen", Optional.of(7L), 10))
      .thenReturn(
          List.of(
              UserSummaryView.builder()
                  .userId(42L)
                  .username("chen-yu")
                  .displayName("陈宇")
                  .avatar(Avatar.of(42L, "0123456789abcdef"))
                  .avatarPalette(5)
                  .status(UserStatus.ACTIVE)
                  .emailVerified(true)
                  .profileVisible(true)
                  .build()));

  JsonNode user =
      post(SUGGEST, "{\"prefix\":\"chen\",\"viewerUserId\":7,\"limit\":10}").get("users").get(0);

  assertThat(user.get("userId").isString()).isTrue();
  assertThat(user.get("userId").asString()).isEqualTo("42");
  assertThat(user.get("username").asString()).isEqualTo("chen-yu");
  assertThat(user.get("displayName").asString()).isEqualTo("陈宇");
  assertThat(user.get("avatarUrl").asString()).isEqualTo("/users/42/avatar?v=0123456789abcdef");
  assertThat(user.get("avatarPalette").asInt()).isEqualTo(5);
  assertThat(user.get("accountStatus").asString()).isEqualTo("ACTIVE");
  assertThat(user.get("profileVisible").asBoolean()).isTrue();
}

@Test
@DisplayName("联想不带访客：按匿名算")
void should_pass_empty_viewer_to_suggestions_when_absent() {
  when(userSuggestionQueryService.suggest(any(), any(), any())).thenReturn(List.of());

  post(SUGGEST, "{\"prefix\":\"chen\",\"limit\":10}");

  verify(userSuggestionQueryService).suggest("chen", Optional.empty(), 10);
}

@Test
@DisplayName("联想条数越界：422，errors 带字段名与原因码")
void should_return_422_for_bad_suggestion_limit() {
  when(userSuggestionQueryService.suggest(any(), any(), any()))
      .thenThrow(new InvalidUserFieldsException(List.of(UserFieldViolations.limitOutOfRange())));

  JsonNode error = postExpecting422(SUGGEST, "{\"prefix\":\"chen\",\"limit\":21}");

  assertThat(error.get("field").asString()).isEqualTo("limit");
  assertThat(error.get("code").asString()).isEqualTo("INVALID_FORMAT");
}

@Test
@DisplayName("解析：回显请求原文，用户 ID 是字符串，状态是名字")
void should_return_resolutions_echoing_requested_text() {
  when(userResolutionQueryService.resolveByUsernames(List.of("Chen-Yu")))
      .thenReturn(List.of(ResolvedUser.of("Chen-Yu", 42L, UserStatus.BANNED)));

  JsonNode user = post(RESOLVE, "{\"usernames\":[\"Chen-Yu\"]}").get("users").get(0);

  assertThat(user.get("username").asString()).isEqualTo("Chen-Yu");
  assertThat(user.get("userId").isString()).isTrue();
  assertThat(user.get("userId").asString()).isEqualTo("42");
  assertThat(user.get("accountStatus").asString()).isEqualTo("BANNED");
}

@Test
@DisplayName("解析：含 null 的列表原样到服务")
void should_pass_null_elements_to_resolution_service() {
  when(userResolutionQueryService.resolveByUsernames(any())).thenReturn(List.of());

  post(RESOLVE, "{\"usernames\":[\"chen\",null]}");

  verify(userResolutionQueryService).resolveByUsernames(Arrays.asList("chen", null));
}
```

  常量：`SUGGEST = "/_internal/users/suggest"`、`RESOLVE = "/_internal/users/resolve/by-usernames"`。

- [ ] **Step 2: 跑红** — `./gradlew $ID:patra-identity-adapter:integrationTest --tests "*UserEndpointImplIT"`，预期编译失败。
- [ ] **Step 3: 实现** — `UserEndpointImpl` 注入两个服务；`suggest`：`Optional.ofNullable(request.viewerUserId())`，结果用现有的 `toResp` 映射；`resolveByUsernames`：`ResolvedUser → UserResolutionResp.of(username, userId, AccountStatus.valueOf(status.name()))`。类 Javadoc 改为三个端点、信任边界不变。
- [ ] **Step 4: 跑绿** — 同上，PASS（含原有三个用例）。

---

### Task 8: 完整流程（boot）

**Files:**
- Modify: `patra-identity-boot/src/integrationTest/java/dev/patra/identity/IdentityFlowITSupport.java`
- Modify: `patra-identity-boot/src/integrationTest/java/dev/patra/identity/UserSummariesFlowIT.java`
- Create: `patra-identity-boot/src/integrationTest/java/dev/patra/identity/UserSuggestionsFlowIT.java`
- Create: `patra-identity-boot/src/integrationTest/java/dev/patra/identity/UserResolutionFlowIT.java`

**Interfaces:**
- Consumes: 任务 7 的两个端点。
- Produces（基座里的包级辅助方法，从 `UserSummariesFlowIT` 原样挪过来）：`void setPrivacy(HttpHeaders owner, String visibility)`、`void applyStatus(long userId, String status)`（`ACTIVE`、`ACTIVE_UNVERIFIED` 不动）、`void markDeleted(long userId)`；新增 `void markEmailVerified(long userId)`（`UPDATE idn_user SET email_verified_at = now() WHERE id = ?`），`UserSummariesFlowIT` 里那行内联 SQL 改调它。

- [ ] **Step 1: 挪辅助方法** — 改完跑 `./gradlew $ID:patra-identity-boot:integrationTest --tests "*UserSummariesFlowIT"`，PASS（行为不变）。
- [ ] **Step 2: 逐条写失败的测试：`UserSuggestionsFlowIT extends IdentityFlowITSupport`**（`SUGGEST` 常量；`suggest(String prefix, Long viewerId, int limit)` 发请求、断言 200、返回 `users`；`values(JsonNode users, String field)` 取某字段的字符串列表；用户名用短代号拼，保证 ≤ 30 字符且不和别的 IT 撞：状态 `ACTIVE→act`、`ACTIVE_UNVERIFIED→unv`、`PENDING_DELETION→pnd`、`BANNED→ban`、`DELETED→del`，可见性 `PUBLIC→pub`、`SIGNED_IN→sin`、`SELF→slf`；矩阵数据源 `statusTimesVisibility()` 同 `UserSummariesFlowIT`，在本类里定义）

```java
@ParameterizedTest(name = "{0} × {1}")
@MethodSource("statusTimesVisibility")
@DisplayName("出现在联想里 ⇔ ACTIVE、不是本人、且公开主页对该访客 200：匿名、他人、本人")
void should_suggest_only_active_accounts_whose_profile_the_viewer_can_see(
    String status, String visibility) {
  String username = "sg-" + CODES.get(status) + "-" + CODES.get(visibility);
  long ownerId = userIdOf(register(username + "@example.com", username));
  long otherId = userIdOf(register(username + ".o@example.com", username + "-o"));
  HttpHeaders owner = assertionFor(ownerId, latestSessionIdOf(ownerId));
  HttpHeaders other = assertionFor(otherId, latestSessionIdOf(otherId));
  setPrivacy(owner, visibility);
  if (!status.equals("ACTIVE_UNVERIFIED")) {
    markEmailVerified(ownerId);
  }
  applyStatus(ownerId, status);

  assertSuggestedIffVisible(username, ownerId, status, noHeaders(), null, "匿名");
  assertSuggestedIffVisible(username, ownerId, status, other, otherId, "他人");
  assertSuggestedIffVisible(username, ownerId, status, owner, ownerId, "本人");
}

/// 同一位访客：主人出现在联想里，当且仅当 ACTIVE、访客不是主人、且主页对访客 200。
private void assertSuggestedIffVisible(
    String username, long ownerId, String status, HttpHeaders requester, Long viewerId, String label) {
  boolean pageVisible = get("/users/" + username, requester).status() == 200;
  boolean expected = pageVisible && status.startsWith("ACTIVE") && !Long.valueOf(ownerId).equals(viewerId);
  boolean suggested = values(suggest(username, viewerId, 20), "userId").contains(String.valueOf(ownerId));
  assertThat(suggested).as(label).isEqualTo(expected);
}

@Test
@DisplayName("响应项是摘要卡片；按用户名字节序；截到 limit；前缀的大小写与空白不影响")
void should_return_cards_in_byte_order_and_cut_to_limit() {
  Map<String, Long> ids = new HashMap<>();
  for (String name : List.of("sgo", "sgo-b", "sgo9", "sgo_c", "sgoa")) {
    long id = userIdOf(register(name + "@example.com", name));
    markEmailVerified(id);
    ids.put(name, id);
  }

  JsonNode all = suggest(" SGO ", null, 20);

  assertThat(values(all, "username")).containsExactly("sgo", "sgo-b", "sgo9", "sgo_c", "sgoa");
  assertThat(values(suggest("sgo", null, 2), "username")).containsExactly("sgo", "sgo-b");
  JsonNode first = all.get(0);
  assertThat(first.get("userId").asString()).isEqualTo(String.valueOf(ids.get("sgo")));
  assertThat(first.get("displayName").asString()).isEqualTo("sgo");
  assertThat(first.get("avatarUrl").isNull()).isTrue();
  assertThat(first.get("accountStatus").asString()).isEqualTo("ACTIVE");
  assertThat(first.get("emailVerified").asBoolean()).isTrue();
  assertThat(first.get("profileVisible").asBoolean()).isTrue();
}

@Test
@DisplayName("不可能是用户名开头的前缀：200 空结果；条数越界、缺前缀：422")
void should_answer_empty_for_impossible_prefix_and_422_for_bad_request() {
  assertThat(values(suggest("-sgo", null, 10), "userId")).isEmpty();
  assertThat(values(suggest("陈", null, 10), "userId")).isEmpty();

  RawResponse tooMany = post(SUGGEST, Map.of("prefix", "sgo", "limit", 21), noHeaders());
  assertThat(tooMany.status()).as(tooMany.body()).isEqualTo(422);
  assertThat(json(tooMany).get("errors").get(0).get("field").asString()).isEqualTo("limit");
  RawResponse noPrefix = post(SUGGEST, Map.of("limit", 10), noHeaders());
  assertThat(noPrefix.status()).as(noPrefix.body()).isEqualTo(422);
  assertThat(json(noPrefix).get("errors").get(0).get("field").asString()).isEqualTo("prefix");
}
```

- [ ] **Step 3: 跑** — `./gradlew $ID:patra-identity-boot:integrationTest --tests "*UserSuggestionsFlowIT"`，PASS（端点在任务 7 已实现，这里是端到端证据；若失败按 systematic-debugging 查）。
- [ ] **Step 4: 逐条写测试：`UserResolutionFlowIT extends IdentityFlowITSupport`**（`RESOLVE` 常量；`resolve(List<String> usernames)` 发请求、断言 200、返回 `users`；`values` 同上）

```java
@Test
@DisplayName("当前名与保留期内的旧名都解析到原用户，到期当刻旧名不再解析；不存在与不合语法的省略")
void should_resolve_current_and_reserved_former_names_until_expiry() {
  long id = userIdOf(register("rs.alpha@example.com", "rs-alpha"));
  RawResponse renamed =
      exchange(
          HttpMethod.PATCH,
          "/me/username",
          Map.of("username", "rs-beta"),
          assertionFor(id, latestSessionIdOf(id)));
  assertThat(renamed.status()).as(renamed.body()).isEqualTo(200);

  JsonNode users = resolve(List.of("rs-alpha", "RS-Beta", "rs-nobody", "rs alpha"));

  assertThat(values(users, "username")).containsExactly("rs-alpha", "RS-Beta");
  assertThat(values(users, "userId")).containsOnly(String.valueOf(id));
  assertThat(values(users, "accountStatus")).containsOnly("ACTIVE");

  clock.set(T0.plus(Duration.ofDays(90)));
  assertThat(values(resolve(List.of("rs-alpha", "rs-beta")), "username")).containsExactly("rs-beta");
}

@ParameterizedTest
@ValueSource(strings = {"BANNED", "PENDING_DELETION", "DELETED"})
@DisplayName("非正常账号带状态返回")
void should_resolve_non_active_accounts_with_their_status(String status) {
  String username = "rs-" + status.toLowerCase(Locale.ROOT).replace('_', '-');
  long id = userIdOf(register(username + "@example.com", username));
  applyStatus(id, status);

  JsonNode user = resolve(List.of(username)).get(0);

  assertThat(user.get("userId").asString()).isEqualTo(String.valueOf(id));
  assertThat(user.get("accountStatus").asString()).isEqualTo(status);
}

@Test
@DisplayName("全部不合语法：200 空结果；101 个：422")
void should_answer_empty_for_no_usernames_and_422_for_too_many() {
  assertThat(values(resolve(List.of("ab", "-x", "陈")), "userId")).isEmpty();

  RawResponse tooMany =
      post(RESOLVE, Map.of("usernames", Collections.nCopies(101, "rs-alpha")), noHeaders());
  assertThat(tooMany.status()).as(tooMany.body()).isEqualTo(422);
  assertThat(json(tooMany).get("errors").get(0).get("code").asString()).isEqualTo("TOO_MANY");
}
```

- [ ] **Step 5: 跑** — `./gradlew $ID:patra-identity-boot:integrationTest --tests "*UserResolutionFlowIT" --tests "*UserSuggestionsFlowIT" --tests "*UserSummariesFlowIT"`，PASS。

---

### Task 9: 网关拒绝名单的证据

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayBlockedPathsIT.java`

- [ ] **Step 1: 加两条** — `should_answer_403_for_blocked_paths_whether_anonymous_or_logged_in` 的 `blocked` 列表里，紧跟 `/patra-identity/_internal/sessions/introspect` 之后加 `/patra-identity/_internal/users/suggest`、`/patra-identity/_internal/users/resolve/by-usernames`。只改测试：规则 `/*/_internal/**` 已覆盖。
- [ ] **Step 2: 跑** — `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayBlockedPathsIT"`，PASS（匿名与带令牌都 403、下游 0 次请求）。

---

### Task 10: README 与全量门控

**Files:**
- Modify: `patra-api/patra-identity/README.md`

- [ ] **Step 1: README**（按 spec 第 9 节）：开头工程设计列表加本 spec（「提及支撑：前缀联想、用户名批量解析」）；第 1 节 domain 行加「用户名前缀 `UsernamePrefix`」，app 行加「@ 提及的前缀联想走 `UserSuggestionQueryService`、用户名批量解析走 `UserResolutionQueryService`」，infra 行加「`V2` 用户名前缀索引」，adapter 行的内部接口处补两个端点，api 行加两个方法与 DTO；第 2 节接口表在 summaries 行后加两行（响应、请求体、「拒绝外部访问；interaction 经服务发现直连」）；新增第 17 节「提及支撑（内部接口）」：设计链接，**用途**、**联想请求 / 结果**、**解析请求 / 结果**、**读法与索引**（`COLLATE "C"` 部分索引、范围谓词、不用 `LIKE` 的原因一句话）、**调用方的义务**（spec 第 10 节）。
- [ ] **Step 2: 格式化** — `./gradlew spotlessApply`；`git -C <worktree> status --short` 只应出现本计划「文件结构」表里的路径（加 spec、plan 两个文档）。
- [ ] **Step 3: identity 全部模块 `check`** — `./gradlew $ID:patra-identity-api:check $ID:patra-identity-domain:check $ID:patra-identity-app:check $ID:patra-identity-infra:check $ID:patra-identity-adapter:check $ID:patra-identity-boot:check`，BUILD SUCCESSFUL（含 SpotBugs、R6、模块图一致性）。
- [ ] **Step 4: identity 全部集成测试**（后台跑）— `./gradlew $ID:patra-identity-infra:integrationTest $ID:patra-identity-adapter:integrationTest $ID:patra-identity-boot:integrationTest`，BUILD SUCCESSFUL。
- [ ] **Step 5: 下游编译** — `./gradlew :patra-api:patra-interaction:patra-interaction-infra:compileTestJava :patra-api:patra-interaction:patra-interaction-infra:compileIntegrationTestJava :patra-api:patra-interaction:patra-interaction-boot:compileJava`，BUILD SUCCESSFUL（`UserEndpoint` 多了方法，interaction 只有 mock，确认不受影响）。
- [ ] **Step 6: 停下汇报** — 改动清单、测试结果、两个接口的契约形状；等用户同意再提交。

---

## 补充任务（2026-10-27 用户拍板：联想加按昵称前缀匹配；修终审 Minor 1、2、4）

> 编排会话转达用户原话「都按你的建议，加昵称匹配，修完提交合回」。spec 第 4 节已重写、第 12 节已补。Minor 3（网关只发 GET）不改。任务 1–10 已完成，以下接着做；做完重跑 identity 六个模块的 `check` 与集成测试，全过后提交一个 commit（不合回、不 push）。

### 补充的 Global Constraints

- 昵称前缀：`DisplayNamePrefix.parse(raw)` = `DisplayName.normalize` → 非空、`ProfileTextRules.utf8Length ≤ DisplayNamePolicy.MAX_BYTES`（120）、`!ProfileTextRules.hasRejectedCharacter(normalized, false)` → `value = normalized.toLowerCase(Locale.ROOT)`；否则空。
- 昵称查询：`SELECT u.* FROM idn_user u JOIN idn_user_profile p ON p.user_id = u.id WHERE u.status = 'ACTIVE' AND p.display_name IS NOT NULL AND lower(p.display_name) COLLATE "C" >= :from AND lower(p.display_name) COLLATE "C" < :to ORDER BY lower(p.display_name) COLLATE "C", u.username COLLATE "C" LIMIT :limit`。
- 昵称索引：`CREATE INDEX idx_idn_user_profile_display_name_prefix ON idn_user_profile ((lower(display_name)) COLLATE "C") WHERE display_name IS NOT NULL;`，与用户名索引同放 `V2__add_mention_prefix_indexes.sql`（原 `V2__add_username_prefix_index.sql` 改名，未合入，不另起 `V3`）。
- 上界：末**码点**加一（`Character.toChars`），两路共用；被拒字符已排除代理项与未分配码点，不会越界。
- 合并：用户名一路在前、昵称一路在后，按用户 ID 去重保留先出现的；筛完用户名一路已够 `limit` 时不调昵称一路的端口。两路都解析不出前缀时不查库。

### Task 11: 昵称前缀值对象 `DisplayNamePrefix`

**Files:** Create `patra-identity-domain/.../domain/model/vo/DisplayNamePrefix.java`；Test `patra-identity-domain/.../domain/model/vo/DisplayNamePrefixTest.java`

**Interfaces:** Produces `public record DisplayNamePrefix(String value)`（紧凑构造器拒绝 `null` 与空串）、`public static Optional<DisplayNamePrefix> parse(String raw)`。

- [ ] **Step 1: 逐条写失败的测试**

```java
@ParameterizedTest
@CsvSource({"张, 张", "Chen, chen", "' Chen  Yu ', chen yu", "-a, -a"})
void should_normalize_and_lowercase(String raw, String value) {
  assertThat(DisplayNamePrefix.parse(raw)).map(DisplayNamePrefix::value).contains(value);
}

@Test
void should_keep_supplementary_characters() {
  assertThat(DisplayNamePrefix.parse("🍎")).map(DisplayNamePrefix::value).contains("🍎");
}

@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {"   ", "a\u0007", "", "\uD800", "a​b"})
void should_reject_impossible_beginnings(String raw) {
  assertThat(DisplayNamePrefix.parse(raw)).isEmpty();
}

@Test
void should_accept_up_to_120_bytes() {
  assertThat(DisplayNamePrefix.parse("张".repeat(40))).isPresent();
  assertThat(DisplayNamePrefix.parse("张".repeat(41))).isEmpty();
}
```

- [ ] **Step 2: 跑红** — `./gradlew $ID:patra-identity-domain:test --tests "*DisplayNamePrefixTest"`，预期编译失败。
- [ ] **Step 3: 实现**（见补充的 Global Constraints）。
- [ ] **Step 4: 跑绿**。

### Task 12: 昵称读端口、范围查询与第二个索引（含 Minor 1、2）

**Files:** Modify `UserReadPort.java`、`UserDao.java`、`UserReadAdapter.java`；Rename + Modify `db/migration/V2__add_mention_prefix_indexes.sql`；Test `UserReadAdapterIT.java`

**Interfaces:** Consumes `DisplayNamePrefix`（任务 11）。Produces `UserReadPort.findActiveSummariesByDisplayNamePrefix(DisplayNamePrefix prefix, int limit)` → `List<UserSummaryReadModel>`；`UserDao.findActiveByDisplayNameRange(String from, String to, int limit)`。`findSummariesByUsernames` 的端口 Javadoc 写明「空集合返回空」（Minor 2，只改文档）。

- [ ] **Step 1: 逐条写失败的测试**（辅助 `saveWithDisplayName(String username, String displayName)`：`saveWithProfile` 后 `UserProfile.edit(ProfileContent.builder().displayName(new DisplayName(…)).build())` 再存；`nickname(String raw)` = `DisplayNamePrefix.parse(raw).orElseThrow()`）

```java
@Test
void should_match_single_character_username_prefix() {        // Minor 1
  for (String name : List.of("cat", "c-ow", "czar", "dog", "bat")) {
    saveWithProfile(name);
  }
  assertThat(reader.findActiveSummariesByUsernamePrefix(prefix("c"), 10))
      .extracting(UserSummaryReadModel::username)
      .containsExactly("c-ow", "cat", "czar");
}

@Test
void should_find_active_summaries_by_display_name_prefix_in_code_point_order() {
  saveWithDisplayName("u-zw", "张伟");
  saveWithDisplayName("u-zsf", "张三丰");
  saveWithDisplayName("u-zs", "张三");
  saveWithDisplayName("u-zz", "章子");
  saveWithStatus(saveWithDisplayName("u-zf", "张飞"), "BANNED");   // 先设昵称再改状态
  saveWithProfile("u-zn");                                        // 没设昵称
  assertThat(reader.findActiveSummariesByDisplayNamePrefix(nickname("张"), 10))
      .extracting(UserSummaryReadModel::displayName)
      .containsExactly("张三", "张三丰", "张伟");
}

@Test
void should_match_display_names_case_insensitively() {
  saveWithDisplayName("u-cy", "Chen Yu");
  saveWithDisplayName("u-cl", "CHEN Li");
  saveWithDisplayName("u-cg", "Cheng");
  saveWithDisplayName("u-cx", "Chex");
  assertThat(reader.findActiveSummariesByDisplayNamePrefix(nickname("Chen"), 10))
      .extracting(UserSummaryReadModel::displayName)
      .containsExactly("CHEN Li", "Chen Yu", "Cheng");
}

@Test
void should_break_display_name_ties_by_username() {
  saveWithDisplayName("u-same-b", "张三");
  saveWithDisplayName("u-same-a", "张三");
  assertThat(reader.findActiveSummariesByDisplayNamePrefix(nickname("张三"), 10))
      .extracting(UserSummaryReadModel::username)
      .containsExactly("u-same-a", "u-same-b");
}

@Test
void should_bound_supplementary_character_prefix_by_next_code_point() {
  // 实施时改用 U+1F3FF：它的低位代理是 \uDFFF，按 char 加一会拆坏代理对，才逼得出按码点算的上界
  // （🍎 U+1F34E 按 char 加一碰巧也对，测不出区别）
  saveWithDisplayName("u-dark", "\uD83C\uDFFFDark");
  saveWithDisplayName("u-rat", "\uD83D\uDC00Rat");   // U+1F400，正是上界，不含
  assertThat(reader.findActiveSummariesByDisplayNamePrefix(nickname("\uD83C\uDFFF"), 10))
      .extracting(UserSummaryReadModel::username)
      .containsExactly("u-dark");
}

@Test
void should_cut_display_name_candidates_to_limit() { … 三个「张」开头，limit 2 → 前两个 … }

@Test
void should_index_display_names_in_byte_order() {
  // pg_indexes 里 idx_idn_user_profile_display_name_prefix 的定义含 `lower((display_name)::text)`、`COLLATE "C"`、`display_name IS NOT NULL`
}
```

  `saveWithStatus` 改为接受已存的用户 ID 的重载（或拆出 `applyStatus(long id, String status)`），以便先设昵称再改状态。

- [ ] **Step 2: 跑红** — `./gradlew $ID:patra-identity-infra:integrationTest --tests "*UserReadAdapterIT"`，预期编译失败（单字符用例首跑即绿：代码本来就对，用变异验证）。
- [ ] **Step 3: 实现** — 端口方法与 Javadoc；DAO 原生查询（普通字符串拼接，`status`、`IS NOT NULL` 写字面量）；适配器 `upperBound` 改为按码点；`V2` 改名并加第二个 `CREATE INDEX` 与注释（写清为什么做不到只覆盖 `ACTIVE`）。
- [ ] **Step 4: 跑绿**；再用临时 postgres:17 对改名后的 `V2` 跑一次两个索引的通用计划 `EXPLAIN`，结果与 spec 4.5 的记录核对。

### Task 13: 联想服务合并两路

**Files:** Modify `UserSuggestionQueryService.java`；Test `UserSuggestionQueryServiceTest.java`

**Interfaces:** Consumes 任务 11、12。

- [ ] **Step 1: 改既有用例** — `should_return_empty_without_reading_for_impossible_prefix` 的取值换成两路都不可能的：`""`、`"   "`、`"a\u0007"`、`""`（`-a`、`a--`、`陈`、`a b` 现在都能是昵称开头）。
- [ ] **Step 2: 逐条写失败的测试**（先实现「两路都查」，最后再写「够数就不查昵称」那条，让它真红）

```java
@Test
void should_query_only_display_names_for_chinese_prefix() {
  service.suggest("张", Optional.of(7L), 10);
  verify(reader).findActiveSummariesByDisplayNamePrefix(DisplayNamePrefix.parse("张").orElseThrow(), 200);
  verify(reader, never()).findActiveSummariesByUsernamePrefix(any(), anyInt());
}

@Test
void should_query_only_display_names_for_prefix_with_space() {
  service.suggest(" Chen Yu", Optional.of(7L), 10);
  verify(reader).findActiveSummariesByDisplayNamePrefix(DisplayNamePrefix.parse("chen yu").orElseThrow(), 200);
  verify(reader, never()).findActiveSummariesByUsernamePrefix(any(), anyInt());
}

@Test
void should_put_username_hits_first_and_dedupe_by_user_id() {
  // 用户名一路返回 [1, 2]，昵称一路返回 [3, 1] → [1, 2, 3]
}

@Test
void should_not_query_display_names_when_usernames_fill_the_limit() {
  // limit 2，用户名一路返回两个可见的 → verify(reader, never()).findActiveSummariesByDisplayNamePrefix(any(), anyInt())
}
```

- [ ] **Step 3: 跑红 → 实现 → 跑绿**，每条一轮；`./gradlew $ID:patra-identity-app:test --tests "*UserSuggestionQueryServiceTest"`。

### Task 14: 昵称的完整流程、文档（含 Minor 4）、门控与提交

**Files:** Modify `UserSuggestionsFlowIT.java`、`UserSuggestionsResp.java`、`UserSuggestReq.java`、`UserEndpoint.java`、`README.md`

- [ ] **Step 1: 流程测试**（辅助 `registerVerified(String username)`；`setDisplayName(long id, String name)` = `PUT /me/profile` `{ displayName }`，断言 200）

```java
@Test
void should_suggest_by_display_name_after_username_hits_without_duplicates() {
  long dup = registerVerified("kunpeng-dup");
  setDisplayName(dup, "Kunpeng Dup");
  registerVerified("kunpeng-z");
  long nick = registerVerified("nk-kp");
  setDisplayName(nick, "Kunpeng Li");

  assertThat(values(suggest("KUNPENG", null, 20), "username"))
      .containsExactly("kunpeng-dup", "kunpeng-z", "nk-kp");
}

@Test
void should_apply_visibility_and_status_rules_to_display_name_hits() {
  // 鲲鹏（公开、正常）出现；鲲鱼（封禁）、鲲影（主页仅自己）不出现；访客是另一个登录用户
}
```

- [ ] **Step 2: 文档** — `UserSuggestionsResp` Javadoc 与 README 第 17 节写明 `emailVerified` 也恒为真（Minor 4）；`UserSuggestReq`、`UserEndpoint`、`UserSuggestionQueryService` 的 Javadoc 与 README 第 1、17 节改成「用户名或昵称前缀」，写明两路的排序、大小写、昵称索引。
- [ ] **Step 3: 门控** — `./gradlew spotlessApply`；identity 六个模块 `check`；identity 三个模块 `integrationTest`（后台跑）；`:patra-api:patra-gateway-boot:integrationTest --tests "*GatewayBlockedPathsIT"`；interaction 编译。
- [ ] **Step 4: 提交** — `git -C <worktree> add` 逐个列出本 Issue 的路径（`git status --short` 里的全部，含 spec、plan），`git diff --cached --stat` 核对；提交信息 `feat(identity): 新增 @ 提及支撑的前缀联想（用户名与昵称）与用户名批量解析 (PAP-118)`，body 每行 ≤ 100 字符，末尾带 `Co-Authored-By`；pre-commit 改了文件就 `git add` 同样的路径重提一次，不绕过钩子。提交后删掉本计划的 `.superpowers/sdd/` 工作区，汇报 commit SHA。不合回、不 push。
