# identity 对外契约 Implementation Plan（PAP-108）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（本仓库约定内联执行，不用 subagent-driven）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** identity 提供内部批量用户摘要接口（带 `profileVisible`），头像地址改按用户 ID，公开主页带 `userId`。

**Architecture:** 主页可见性规则抽成值对象 `ProfileAccess`，公开主页读模型与新的摘要读模型都委托它；摘要走 `UserReadPort.findSummaries`（两条 IN 查询）→ `UserSummaryQueryService`（校验、去重保序）→ `UserEndpointImpl`（契约在 `patra-identity-api`）。头像地址由 `Avatar` 自带的 `userId` 拼，路由改为 `/users/{userId}/avatar`。

**Tech Stack:** Java 25、Spring Boot 4、Spring Data JPA、JUnit 5 + AssertJ + Mockito、Testcontainers（PG17、Redis）。

**Spec:** `docs/patra/specs/2026-10-24-identity-external-contract-design.md`

## Global Constraints

- 内部接口：`POST /_internal/users/summaries`；`userIds` 上限 100，按去重前长度算；超限 / `null` / 含 `null` → 422，字段 `userIds`，原因 `TOO_MANY` / `REQUIRED` / `INVALID_FORMAT`。
- 响应顺序 = 去重后的请求顺序；identity 不认识的 ID 省略并打 `WARN`（只列 ID）。
- `DELETED`：`username`、`displayName`、`avatarUrl` 为 `null`，`emailVerified`、`profileVisible` 为 `false`；不得走「昵称回退成用户名」。
- `BANNED` 的 `avatarUrl` 为 `null`；`UserStatus.servesAvatar()` 只有 `ACTIVE`、`PENDING_DELETION` 为真。
- `ProfileAccess.isVisibleTo`：`DELETED` 对任何人（含本人）不可见，先于本人判断；其余规则与现状一致。
- 头像地址 `/users/{userId}/avatar?v={hash}`；路由路径变量按 `^[1-9][0-9]{0,18}$` 校验再 `parseLong`，溢出算不合法，一律 404。
- 公开主页 200 响应加 `userId`（第一个字段）；301 体不加。
- 代码规范：`///` Markdown Javadoc 覆盖所有方法；record ≤ 4 参数用 `of()`，≥ 5 参数用 `@Builder`；集合字段紧凑构造器 `copyOf`；测试方法 snake_case。
- 不改网关规则与代码（只改 `GatewayAuthenticationIT` 一处示例路径）、不改迁移脚本、不加模块依赖。
- 每个任务结束不自动提交：提交要用户明确同意（`CLAUDE.md` 禁止自动 commit）。

## Review Focus

1. **改名之后的头像**：用户改名后旧 `avatarUrl` 必须仍 200，且 `/auth/me`、主页、摘要三处给出的地址相同 → Task 8 `AvatarFlowIT` 改名步骤。
2. **路径变量的非常规数字**：`+42`、`042`、`0`、`9223372036854775808`（溢出）、`42abc` 都要 404，不能 500 → Task 2 `AvatarQueryServiceTest`。
3. **已注销用户昵称为空时的回退泄露**：`displayName` 为 `null` 的 `DELETED` 用户，响应体里不得出现其用户名字符串 → Task 3 单测与 Task 8 `UserSummariesFlowIT` 用整串 `contains` 检查。
4. **重复 ID 计入上限**：101 个元素但去重后只有 1 个，仍然 422 → Task 5 单测。
5. **JSON 里的数字 ID**：请求体 `userIds` 用 JSON 数字（interaction 的客户端不走 MVC 的 Long→String 转换），响应里 `userId` 是字符串 → Task 6 `UserEndpointImplIT`。

---

## 文件结构

| 文件 | 动作 | 职责 |
|---|---|---|
| `patra-identity-domain/.../domain/model/read/ProfileAccess.java` | 新建 | 主页可见性规则的唯一实现 |
| `patra-identity-domain/.../domain/model/read/PublicUserReadModel.java` | 改 | `access()`，`audience()` / `isVisibleTo()` 委托；`viewFor` 带 `userId` |
| `patra-identity-domain/.../domain/model/read/PublicUserView.java` | 改 | 加 `userId` |
| `patra-identity-domain/.../domain/model/read/UserSummaryReadModel.java` | 新建 | 摘要原始数据 + `viewFor` |
| `patra-identity-domain/.../domain/model/read/UserSummaryView.java` | 新建 | 按状态与访客处理好的摘要 |
| `patra-identity-domain/.../domain/model/read/AvatarOwner.java` | 改 | Javadoc：按用户 ID |
| `patra-identity-domain/.../domain/model/enums/UserStatus.java` | 改 | `servesAvatar()` |
| `patra-identity-domain/.../domain/model/vo/UserFieldViolations.java` | 改 | `USER_IDS`、`TOO_MANY`、三个工厂方法 |
| `patra-identity-domain/.../domain/port/read/UserReadPort.java` | 改 | `findSummaries`；`findAvatarOwner(long)` |
| `patra-identity-infra/.../infra/adapter/read/UserReadAdapter.java` | 改 | 两个方法的实现 |
| `patra-identity-infra/.../infra/adapter/persistence/dao/UserProfileDao.java` | 改 | `findByUserIdIn` |
| `patra-identity-app/.../app/usecase/user/query/UserSummaryQueryService.java` | 新建 | 校验、去重保序、重排 |
| `patra-identity-app/.../app/usecase/avatar/query/AvatarQueryService.java` | 改 | 按 ID 定位 |
| `patra-identity-app/.../app/usecase/avatar/AvatarChanged.java` | 删 | 只剩头像一个字段，`ChangeAvatarCommand` 结果改为 `Avatar` |
| `patra-identity-app/.../app/usecase/avatar/ChangeAvatarCommand.java`、`ChangeAvatarHandler.java` | 改 | 结果类型 `Avatar` |
| `patra-identity-api/.../api/endpoint/UserEndpoint.java` | 新建 | HTTP Interface |
| `patra-identity-api/.../api/dto/UserSummariesReq.java`、`UserSummariesResp.java`、`UserSummaryResp.java`、`AccountStatus.java` | 新建 | 契约 |
| `patra-identity-adapter/.../adapter/rest/internal/UserEndpointImpl.java` | 新建 | 内部端点 |
| `patra-identity-adapter/.../adapter/rest/AvatarUrls.java` | 改 | `of(Avatar)` |
| `patra-identity-adapter/.../adapter/rest/users/PublicAvatarController.java` | 改 | `/users/{userId}/avatar` |
| `patra-identity-adapter/.../adapter/rest/users/response/PublicUserResponse.java` | 改 | 加 `userId` |
| `patra-identity-adapter/.../adapter/rest/auth/response/CurrentUserResponse.java`、`me/MeAvatarController.java` | 改 | 调新的 `AvatarUrls.of` |
| `patra-identity-boot/src/integrationTest/.../UserSummariesFlowIT.java` | 新建 | AC 1、2 |
| README、PAP-95 / PAP-97 spec | 改 | 第 12 节 |

下文路径省略 `patra-api/patra-identity/` 前缀与 `src/main/java/dev/patra/identity/`（测试为 `src/test/java/...` 或 `src/integrationTest/java/...` 同包）。Gradle 前缀记作 `ID = :patra-api:patra-identity`。

---

### Task 1: 抽出 `ProfileAccess`，`DELETED` 对本人也不可见

**Files:**
- Create: `patra-identity-domain/.../domain/model/read/ProfileAccess.java`
- Modify: `patra-identity-domain/.../domain/model/read/PublicUserReadModel.java:155-182, 222-228`
- Test: `patra-identity-domain/src/test/.../domain/model/read/ProfileAccessTest.java`（新）、`PublicUserReadModelTest.java`（改）

**Interfaces:**
- Produces: `record ProfileAccess(long ownerId, UserStatus status, boolean emailVerified, ProfileVisibility visibility)`；`static ProfileAccess of(...)`；`PublicAudience audience()`；`boolean isVisibleTo(Optional<Long> requesterId)`。`PublicUserReadModel.access()` 返回它。

- [ ] **Step 1: 写失败测试 `ProfileAccessTest`**

```java
@Test void should_be_self_only_when_unverified_or_not_active()  // audience()：未验证、PENDING_DELETION、BANNED、DELETED 都是 SELF
@Test void should_follow_profile_visibility_when_active_and_verified()  // PUBLIC/SIGNED_IN/SELF 一一对应
@Test void should_be_visible_according_to_matrix()
//   (可见性, 匿名, 他人, 本人)：PUBLIC (t,t,t)；SIGNED_IN (f,t,t)；SELF (f,f,t)；
//   未验证 (f,f,t)；PENDING_DELETION (f,f,t)；BANNED (f,f,t)
@Test void should_hide_deleted_account_even_from_owner()
//   ProfileAccess.of(42L, DELETED, true, PUBLIC).isVisibleTo(Optional.of(42L)) 为 false；匿名、他人也为 false
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew ID:patra-identity-domain:test --tests "*ProfileAccessTest*"`
Expected: 编译失败，`ProfileAccess` 不存在。

- [ ] **Step 3: 实现 `ProfileAccess`**：规则从 `PublicUserReadModel.audience()` / `isVisibleTo()` 原样搬过来；`isVisibleTo` 第一行 `if (status == UserStatus.DELETED) return false;`。紧凑构造器 `requireNonNull(status)`、`requireNonNull(visibility)`。

- [ ] **Step 4: 改 `PublicUserReadModel`**：加 `public ProfileAccess access()`；`audience()`、`isVisibleTo()` 委托；删掉私有 `isOwner` 之外不再用到的代码（`viewFor` 判专业背景仍要「是不是本人」，保留 `isOwner`）。

- [ ] **Step 5: 改 `PublicUserReadModelTest`**：`should_be_visible_according_to_matrix` 补两行：`full().status(BANNED)` → `(f,f,t)`，`full().status(DELETED)` → `(f,f,f)`。`should_be_self_only_when_unverified_or_not_active` 不动。

- [ ] **Step 6: 跑 domain 测试**

Run: `./gradlew ID:patra-identity-domain:test`
Expected: PASS。

---

### Task 2: 头像改按用户 ID

**Files:**
- Modify: `domain/model/enums/UserStatus.java`、`domain/port/read/UserReadPort.java:374-378`、`domain/model/read/AvatarOwner.java`（Javadoc）
- Modify: `patra-identity-infra/.../infra/adapter/read/UserReadAdapter.java:447-461`
- Modify: `patra-identity-app/.../app/usecase/avatar/query/AvatarQueryService.java`、`ChangeAvatarCommand.java`、`ChangeAvatarHandler.java`；Delete: `AvatarChanged.java`
- Modify: `patra-identity-adapter/.../adapter/rest/AvatarUrls.java`、`users/PublicAvatarController.java`、`auth/response/CurrentUserResponse.java:48`、`me/MeAvatarController.java:40`、`users/response/PublicUserResponse.java:996`
- Test: `UserStatusTest`（新，domain）、`AvatarQueryServiceTest`、`ChangeAvatarHandlerTest`（app）、`UserReadAdapterIT`（infra）、`PublicAvatarControllerIT`、`MeAvatarControllerIT`、`AuthControllerIT`、`PublicUserControllerIT`（adapter）

**Interfaces:**
- Produces: `boolean UserStatus.servesAvatar()`；`Optional<AvatarOwner> UserReadPort.findAvatarOwner(long userId)`；`AvatarRef AvatarQueryService.locate(String rawUserId, String size, String version)`；`static String AvatarUrls.of(Avatar avatar)`；`ChangeAvatarCommand` 的结果类型 `Avatar`。

- [ ] **Step 1: 写失败测试**
  - `UserStatusTest.should_serve_avatar_only_when_active_or_pending_deletion`：`ACTIVE`、`PENDING_DELETION` 真；`BANNED`、`DELETED` 假。
  - `AvatarQueryServiceTest`：stub 改为 `reader.findAvatarOwner(42L)`；`should_locate_current_avatar` 用 `service.locate("42", "128", HASH)`；删 `should_normalize_username`；`should_404_on_malformed_request` 的 CSV 第一列换成 `abc`、`0`、`+42`、`042`、`42abc`、`9223372036854775808`、`-1`（各配合合法的 size、v），以及原有 size / v 不合法的几行（第一列用 `42`）；`should_404_when_user_missing` 用 `43L`。
  - `UserReadAdapterIT.should_find_avatar_owner_by_user_id`（替换 `..._by_current_username_only`）：按 ID 读到状态与头像；没头像的 `avatar()` 为 `null`；不存在的 ID 为空；**改名后按原 ID 仍读到**（替换原先「改名后按旧名为空」那条）。
  - `PublicAvatarControllerIT`：`URI = "/users/42/avatar?size=128&v=0123456789abcdef"`，`locate("42", ...)`；加一条 `/users/chen-yu/avatar?...` → 服务抛 `AvatarNotFoundException` → 404。
  - `MeAvatarControllerIT:55`、`AuthControllerIT:456`、`PublicUserControllerIT:63`：期望改为 `"/users/42/avatar?v=0123456789abcdef"`（夹具里头像的 `userId` 若不是 42，按夹具实际值）。
  - `ChangeAvatarHandlerTest`：断言结果是 `Avatar`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew ID:patra-identity-domain:test ID:patra-identity-app:test`
Expected: 编译失败（`servesAvatar`、`findAvatarOwner(long)` 不存在）。

- [ ] **Step 3: 实现**
  - `UserStatus.servesAvatar()`。
  - `UserReadPort.findAvatarOwner(long userId)` 替换按用户名的版本；`UserReadAdapter` 用 `userDao.findById`。
  - `AvatarQueryService`：常量 `USER_ID = Pattern.compile("[1-9][0-9]{0,18}")`；不匹配或 `Long.parseLong` 抛 `NumberFormatException` → `AvatarNotFoundException`；状态判断改用 `owner.status().servesAvatar()`；类 Javadoc 改为按用户 ID、删「只认当前用户名」。
  - `PublicAvatarController`：`@GetMapping("/{userId}/avatar")`，参数 `@PathVariable String userId`；Javadoc 同步。
  - `AvatarUrls.of(Avatar avatar)`：spec 6.1 的一行实现；类 Javadoc 写 `/users/{userId}/avatar?v={hash}`。
  - 删 `AvatarChanged`；`ChangeAvatarCommand implements Command<Avatar>`（按现有 Command 泛型写法），`ChangeAvatarHandler` 返回 `avatar`；`MeAvatarController` 用 `AvatarUrls.of(avatar)`。
  - `CurrentUserResponse`、`PublicUserResponse` 改调 `AvatarUrls.of(x.avatar())`。

- [ ] **Step 4: 跑测试**

Run: `./gradlew ID:patra-identity-domain:test ID:patra-identity-app:test ID:patra-identity-infra:integrationTest --tests "*UserReadAdapterIT*" ID:patra-identity-adapter:integrationTest --tests "*Avatar*" --tests "*AuthControllerIT*" --tests "*PublicUserControllerIT*"`
Expected: PASS。

---

### Task 3: 摘要读模型 `UserSummaryReadModel` / `UserSummaryView`

**Files:**
- Create: `domain/model/read/UserSummaryReadModel.java`、`domain/model/read/UserSummaryView.java`
- Test: `patra-identity-domain/src/test/.../domain/model/read/UserSummaryReadModelTest.java`

**Interfaces:**
- Consumes: `ProfileAccess`（Task 1）、`UserStatus.servesAvatar()`（Task 2）、`AvatarPalette.of(long)`。
- Produces:
  - `@Builder record UserSummaryReadModel(long userId, String username, String displayName, Avatar avatar, UserStatus status, boolean emailVerified, ProfileVisibility profileVisibility)`——`displayName` 是资料原值，可为 `null`；`ProfileAccess access()`；`UserSummaryView viewFor(Optional<Long> viewerId)`。
  - `@Builder record UserSummaryView(long userId, String username, String displayName, Avatar avatar, int avatarPalette, UserStatus status, boolean emailVerified, boolean profileVisible)`。

- [ ] **Step 1: 写失败测试 `UserSummaryReadModelTest`**

```java
@Test void should_fall_back_to_username_when_display_name_is_null()      // ACTIVE、displayName null → view.displayName() == username
@Test void should_carry_identity_fields_for_active_user()                // username、avatar、emailVerified 原样；avatarPalette == AvatarPalette.of(userId)
@Test void should_drop_avatar_for_banned_user()                          // BANNED + 有头像 → view.avatar() 为 null，username 仍在
@Test void should_return_placeholder_for_deleted_user()
//   DELETED、displayName null、emailVerified true、有头像 → username/displayName/avatar 全 null，
//   emailVerified false，profileVisible false（viewer 为本人也一样），status DELETED，avatarPalette 仍有值
@Test void should_compute_profile_visible_for_viewer()
//   ACTIVE、已验证、SIGNED_IN：匿名 false、他人 true、本人 true
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew ID:patra-identity-domain:test --tests "*UserSummaryReadModelTest*"`
Expected: 编译失败。

- [ ] **Step 3: 实现两个 record**：`viewFor` 第一步判 `DELETED` 返回占位（先于昵称回退）；`avatar` 取 `status.servesAvatar() ? avatar : null`；`profileVisible = access().isVisibleTo(viewerId)`。紧凑构造器 `requireNonNull(username)`、`requireNonNull(status)`、`requireNonNull(profileVisibility)`。

- [ ] **Step 4: 跑测试**

Run: `./gradlew ID:patra-identity-domain:test`
Expected: PASS。

---

### Task 4: 批量读 `UserReadPort.findSummaries`

**Files:**
- Modify: `domain/port/read/UserReadPort.java`、`infra/adapter/read/UserReadAdapter.java`、`infra/adapter/persistence/dao/UserProfileDao.java`
- Test: `patra-identity-infra/src/integrationTest/.../infra/adapter/read/UserReadAdapterIT.java`

**Interfaces:**
- Produces: `List<UserSummaryReadModel> UserReadPort.findSummaries(Collection<Long> userIds)`——只含存在的，不保证顺序；`List<UserProfileEntity> UserProfileDao.findByUserIdIn(Collection<Long> userIds)`。

- [ ] **Step 1: 写失败测试（`UserReadAdapterIT`）**

```java
@Test void should_read_summaries_in_batch()
//   建 3 个用户（一个设了昵称 + 头像 + 主页 SIGNED_IN，一个什么都没设，一个 DELETED 且昵称为空）；
//   findSummaries(List.of(a, b, c, 999L)) 返回 3 条；按 userId 取出后：
//   a.displayName()=="设的昵称"、a.avatar()!=null、a.profileVisibility()==SIGNED_IN；
//   b.displayName()==null（原值，不回退）、b.profileVisibility()==PUBLIC；c.status()==DELETED
@Test void should_return_empty_for_empty_input()   // findSummaries(List.of()) 为空，不报错
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew ID:patra-identity-infra:integrationTest --tests "*UserReadAdapterIT*"`
Expected: 编译失败。

- [ ] **Step 3: 实现**：空集合直接返回 `List.of()`；`userDao.findAllById(ids)` 与 `profileDao.findByUserIdIn(ids)` 各一次，资料按 `userId` 建 `Map`；缺资料行抛 `IllegalStateException`（同 `profileOf`）；头像用现有 `avatarOf`；`profileVisibility` 取资料行的 `profile_visibility` 列（按 `UserProfileJpaMapper` 现有的枚举转换方式，不经子表）。

- [ ] **Step 4: 跑测试**

Run: `./gradlew ID:patra-identity-infra:integrationTest --tests "*UserReadAdapterIT*"`
Expected: PASS。

---

### Task 5: `UserSummaryQueryService`

**Files:**
- Create: `patra-identity-app/.../app/usecase/user/query/UserSummaryQueryService.java`
- Modify: `domain/model/vo/UserFieldViolations.java`
- Test: `patra-identity-app/src/test/.../app/usecase/user/query/UserSummaryQueryServiceTest.java`

**Interfaces:**
- Consumes: `UserReadPort.findSummaries`（Task 4）、`UserSummaryReadModel.viewFor`（Task 3）。
- Produces: `List<UserSummaryView> summaries(List<Long> userIds, Optional<Long> viewerId)`；常量 `static final int MAX_USER_IDS = 100`；`UserFieldViolations.USER_IDS = "userIds"`、`userIdsRequired()`、`userIdsInvalidFormat()`、`userIdsTooMany()`（原因码 `REQUIRED`、`INVALID_FORMAT`、`TOO_MANY` 已存在，复用）。

- [ ] **Step 1: 写失败测试**（端口用 `mock(UserReadPort.class)`）

```java
@Test void should_reject_null_list()            // InvalidUserFieldsException，violation field "userIds" reason "REQUIRED"
@Test void should_reject_null_element()         // Arrays.asList(1L, null) → reason "INVALID_FORMAT"
@Test void should_reject_more_than_100_even_if_duplicated()  // 101 个 7L → reason "TOO_MANY"；端口未被调用
@Test void should_accept_exactly_100()          // 100 个不同 ID → 端口被调用一次
@Test void should_return_empty_without_reading_for_empty_list()   // verifyNoInteractions(reader)
@Test void should_dedupe_and_keep_request_order()
//   请求 [3, 1, 3, 2]，端口按 [1, 2, 3] 返回 → 结果 userId 顺序 [3, 1, 2]；端口收到的集合是 {3,1,2}
@Test void should_omit_unknown_ids()            // 请求 [1, 999]，端口只返回 1 → 结果只有 1
@Test void should_pass_viewer_to_view()         // viewer Optional.of(1L)，用户 1 主页 SELF → profileVisible true；viewer 空 → false
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew ID:patra-identity-app:test --tests "*UserSummaryQueryServiceTest*"`
Expected: 编译失败。

- [ ] **Step 3: 实现**：`@Service`、`@RequiredArgsConstructor`、`@Slf4j`，只依赖 `UserReadPort`；`LinkedHashSet` 去重；缺席的 ID 打 `log.warn("批量用户摘要里有不存在的用户: userIds={}", missing)`；不经 CommandBus、无事务。三个工厂方法的默认文案：「缺少用户 ID 列表」「用户 ID 不能为空」「一次最多 100 个用户 ID」。

- [ ] **Step 4: 跑测试**

Run: `./gradlew ID:patra-identity-app:test`
Expected: PASS。

---

### Task 6: 契约与内部端点

**Files:**
- Create: `patra-identity-api/.../api/endpoint/UserEndpoint.java`、`api/dto/UserSummariesReq.java`、`api/dto/UserSummariesResp.java`、`api/dto/UserSummaryResp.java`、`api/dto/AccountStatus.java`
- Create: `patra-identity-adapter/.../adapter/rest/internal/UserEndpointImpl.java`
- Modify: `patra-identity-api/build.gradle.kts` 头注释（加「批量用户摘要（interaction 调，PAP-108）」，不改依赖）
- Test: `patra-identity-adapter/src/integrationTest/.../adapter/rest/internal/UserEndpointImplIT.java`

**Interfaces:**
- Consumes: `UserSummaryQueryService.summaries`（Task 5）、`AvatarUrls.of(Avatar)`（Task 2）。
- Produces: spec 5.1 的 `UserEndpoint`、`UserSummariesReq(List<Long> userIds, Long viewerUserId)`（`of`）、`UserSummariesResp(List<UserSummaryResp> users)`（`of`，紧凑构造器 `copyOf`、`null` 视为空）、`@Builder UserSummaryResp(Long userId, String username, String displayName, String avatarUrl, int avatarPalette, AccountStatus accountStatus, boolean emailVerified, boolean profileVisible)`、`enum AccountStatus { ACTIVE, BANNED, PENDING_DELETION, DELETED }`。`UserSummariesReq` 紧凑构造器**不做**拷贝（要让 `null` 与 `null` 元素原样到达服务层校验）。

- [ ] **Step 1: 写失败测试 `UserEndpointImplIT`**（`@WebMvcTest` + `@Import(UserEndpointImpl.class)`，`@MockitoBean UserSummaryQueryService`，形状同 `SessionEndpointImplIT`）

```java
@Test void should_return_summaries_with_string_ids()
//   请求体 {"userIds":[42, 43], "viewerUserId": 7}（JSON 数字）；服务以 (List.of(42L,43L), Optional.of(7L)) 被调用；
//   stub 返回 42 ACTIVE 有头像、43 DELETED 占位；
//   users[0].userId == "42"（字符串）、avatarUrl == "/users/42/avatar?v=…"、accountStatus == "ACTIVE"、profileVisible；
//   users[1].username、displayName、avatarUrl 为 null，accountStatus == "DELETED"
@Test void should_pass_empty_viewer_when_absent()    // 请求体不带 viewerUserId → Optional.empty()
@Test void should_return_422_with_field_errors()
//   服务抛 InvalidUserFieldsException(List.of(UserFieldViolations.userIdsTooMany())) → 422，errors[0].field == "userIds"，reason == "TOO_MANY"
//   （errors[] 的键名按现有 422 响应的实际形状断言，参照 AuthControllerIT 的 422 用例）
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew ID:patra-identity-adapter:integrationTest --tests "*UserEndpointImplIT*"`
Expected: 编译失败。

- [ ] **Step 3: 实现**：契约照 spec 5.1 与 `SessionEndpoint` 的注解写法；`UserEndpointImpl` 的类 Javadoc 写清权限（`/_internal`，网关对外 403，interaction 经服务发现直连）与信任边界（`viewerUserId` 取自请求体）；`AccountStatus.valueOf(view.status().name())`。

- [ ] **Step 4: 跑测试**

Run: `./gradlew ID:patra-identity-api:compileJava ID:patra-identity-adapter:integrationTest --tests "*UserEndpointImplIT*" --tests "*SessionEndpointImplIT*"`
Expected: PASS。

---

### Task 7: 公开主页带 `userId`

**Files:**
- Modify: `domain/model/read/PublicUserView.java`、`PublicUserReadModel.java:191-210`（`viewFor` 填 `userId`）
- Modify: `patra-identity-adapter/.../adapter/rest/users/response/PublicUserResponse.java`
- Test: `PublicUserReadModelTest`（domain）、`PublicUserControllerIT`（adapter，键集合在第 170 行附近）

**Interfaces:**
- Produces: `PublicUserView.userId()`（`long`）；`PublicUserResponse.userId()`（`Long`，第一个组件）。

- [ ] **Step 1: 写失败测试**：`PublicUserReadModelTest.should_carry_user_id_in_view`（`viewFor(...).userId() == 42L`）；`PublicUserControllerIT` 的键集合加 `"userId"`，并断言 `json.get("userId").asString()` 等于夹具用户 ID。
- [ ] **Step 2: 跑测试确认失败**：`./gradlew ID:patra-identity-domain:test --tests "*PublicUserReadModelTest*"` → 编译失败。
- [ ] **Step 3: 实现**：两个 record 加字段；删改「不含用户 ID」「视图里不带用户 ID」「服务端按用户 ID 算好（响应里没有用户 ID）」三处 Javadoc。
- [ ] **Step 4: 跑测试**：`./gradlew ID:patra-identity-domain:test ID:patra-identity-adapter:integrationTest --tests "*PublicUserControllerIT*"` → PASS。

---

### Task 8: 端到端流程测试

**Files:**
- Create: `patra-identity-boot/src/integrationTest/.../UserSummariesFlowIT.java`
- Modify: `AvatarFlowIT.java`、`PublicProfileFlowIT.java`（`PUBLIC_KEYS`）、`PublicIdentityFlowIT.java`（若受影响）

**Interfaces:**
- Consumes: Task 1–7 的全部对外行为；`IdentityFlowITSupport` 与 `PublicProfileFlowIT` 里已有的注册、`assertionFor(userId, sessionId)`、`latestSessionIdOf`、`setPrivacy`、`verifyEmail` 写法（能复用 `IdentityFlowITSupport` 的就复用，不复制）。

- [ ] **Step 1: 写 `UserSummariesFlowIT`**（`RANDOM_PORT`，PG17 + Redis + GreenMail，注解同 `PublicProfileFlowIT`）

```java
@Test void should_return_100_users_in_request_order()
//   注册 3 个真实用户 a、b、c；请求列表 = [c, a, 999999, b, a] + 补足到 100 个元素的不存在 ID（都不同）；
//   响应 users 的 userId 顺序恰为 [c, a, b]
@Test void should_not_leak_username_of_deleted_user()
//   注册 "gone-user"，SQL 改 DELETED（同 PublicProfileFlowIT 的语句，并清 display_name）；
//   响应原文不包含 "gone-user"；accountStatus == "DELETED"
@ParameterizedTest(name = "{0} × {1}") @MethodSource("statusTimesVisibility")
void should_match_public_profile_status_for_every_viewer(String status, String visibility)
//   statusTimesVisibility：{ACTIVE, ACTIVE_UNVERIFIED, PENDING_DELETION, BANNED, DELETED} × {PUBLIC, SIGNED_IN, SELF}；
//   每格：注册 owner（用户名带格子编号避免冲突；other 全类共用一个）→ 先设隐私（owner 的断言调 /me/privacy）→ 除 ACTIVE_UNVERIFIED 外验证邮箱 → 再用 SQL 改状态
//   （DELETED 只改 status、deleted_at、email、email_verified_at，不调 erase，所以设好的隐私保留——真实注销会把隐私恢复成 PUBLIC，矩阵要的正是三档都测）；
//   对三种访客 (匿名, other, owner)：
//     boolean visible = GET /users/{owner} 的状态码 == 200；
//     summary = POST /_internal/users/summaries {userIds:[owner], viewerUserId: 访客 ID 或 null}；
//     assertThat(summary.profileVisible).as(格子+访客).isEqualTo(visible)
//   DELETED 格子：/users/{owner} 对 owner 自己也应 404，summary 为 false
```

- [ ] **Step 2: 改 `AvatarFlowIT`**：`url` 期望改为 `"/users/" + userId + "/avatar?v=" + hash`；新增改名步骤——上传后 `PATCH /me/username` 改名（需要时先满足再认证，照 `UsernameChangeFlowIT` 的做法），再断言 `/auth/me` 与 `/users/{新名}` 的 `avatarUrl` 仍等于 `url`，`GET url&size=128` 仍 200；断言 `GET /users/{旧用户名}/avatar?size=128&v=hash` 为 404。
- [ ] **Step 3: 改 `PublicProfileFlowIT`**：`PUBLIC_KEYS` 加 `"userId"`；`should_expose_only_page_fields_and_no_settings` 加 `json.get("userId").asString()` 等于 `party.ownerId()` 的字符串。
- [ ] **Step 4: 跑 boot 集成测试**

Run: `./gradlew ID:patra-identity-boot:integrationTest`
Expected: PASS（含原有全部流程测试）。若某格不一致，按 superpowers:systematic-debugging 查规则，不改测试去迁就。

---

### Task 9: 文档、网关核对与门控

**Files:**
- Modify: `patra-api/patra-identity/README.md`（spec 第 12 节列出的各处）
- Modify: `docs/patra/specs/2026-10-22-identity-profile-design.md`、`docs/patra/specs/2026-10-22-identity-avatar-design.md`（只追加修订注记）
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayAuthenticationIT.java`（头像转发用例的示例路径）

- [ ] **Step 1: 改 README**：第 1 节模块表、第 2 节接口表（头像行、新增 `POST /_internal/users/summaries` 行：`200 { users: [...] }`，字段与 422 规则；`GET /users/{username}` 键集合加 `userId`）、第 12 节、第 15 节「读取」「`avatarUrl`」、开头工程设计列表加本 spec。
- [ ] **Step 2: 两份旧 spec 加修订注记**：在 profile spec 第 324 行段落后、第 441、473 行对应位置后，avatar spec 第 278、312、465 行对应位置后，各追加一行 `> 修订（2026-10-24，PAP-108，v0.13 决策 E）：……`。
- [ ] **Step 3: 网关核对与示例路径**：`grep -n "_internal\|users/\*\*" patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/security/GatewaySecurityConfiguration.java`，确认 `BLOCKED_PATHS` 含 `/*/_internal/**`、`IDENTITY_PUBLIC_PATHS` 含 `/patra-identity/users/**`；把 `GatewayAuthenticationIT.should_forward_anonymous_avatar_read_with_cache_headers` 里的 `/users/chen-yu/avatar` 示例改成 `/users/42/avatar`（WireMock stub 与请求两处），跑 `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayAuthenticationIT*"` → PASS。
- [ ] **Step 4: 格式化与全模块检查**

Run: `./gradlew spotlessApply && ./gradlew ID:patra-identity-api:check ID:patra-identity-domain:check ID:patra-identity-app:check ID:patra-identity-infra:check ID:patra-identity-adapter:check ID:patra-identity-boot:check`
Expected: BUILD SUCCESSFUL（含 R1–R6、SpotBugs、`dumpModuleGraph` 一致性）。

- [ ] **Step 5: identity 全部集成测试**

Run: `./gradlew ID:patra-identity-infra:integrationTest ID:patra-identity-adapter:integrationTest ID:patra-identity-boot:integrationTest`
Expected: PASS。

- [ ] **Step 6: 向用户报告改动清单与门户配合项（spec 第 10 节），请求提交许可**；得到同意后再 `git add` 本次路径并提交（commit message 中文、动词开头，见 commitlint 约定）。
