# identity 对外契约工程设计（PAP-108）

> 状态：待评审（写代码前由用户在本地让 Codex 评审）
> 日期：2026-10-24
> Issue：PAP-108（GitHub #278），Linear Project P-PAP-10 v0.13 Library
> 依据：release spec `docs/patra/release-specs/v0.13-library.md` 2.1.2、2.1.5、决策 E、F、G；PAP-95 工程 spec `2026-10-22-identity-profile-design.md`；PAP-97 工程 spec `2026-10-22-identity-avatar-design.md`

## 1. 要解决的问题

v0.13 新建的 patra-interaction 要在收藏夹响应里嵌入所有者的信息，并判断公开收藏夹对某位访客是否可见。用户数据只归 identity，可见性规则也只能在 identity 里有一份。为此 identity 要做三件事：

1. 新增内部接口 `POST /_internal/users/summaries`：一次最多 100 个用户 ID，可带访客 ID；返回每个用户的摘要，以及 identity 按自己的主页隐私规则替这位访客算出的 `profileVisible`。已注销的用户返回占位，不带用户名。
2. 头像地址改为按用户 ID 拼：`/users/{userId}/avatar?v={hash}`。改用户名之后地址不变；按用户名的旧路由删除。
3. 公开主页响应 `GET /users/{username}` 带上 `userId`。门户要靠它向 interaction 取该用户的公开收藏夹。

②③ 是对 v0.12 两处约定的有意修订（release spec 决策 E）：PAP-97 定的「`avatarUrl` 按当前用户名拼」和 PAP-95 定的「公开主页不含用户 ID」。Snowflake 用户 ID 能推出注册时间，已接受。

## 2. 范围

**做**（全部在 identity 内）：

- `patra-identity-api`：`UserEndpoint`（HTTP Interface）与三个 DTO、一个枚举。
- domain：把主页可见性规则抽成 `ProfileAccess`，`PublicUserReadModel` 与新的 `UserSummaryReadModel` 都调它；`UserReadPort` 加批量读、头像归属改按 ID 读；`UserStatus.servesAvatar()`。
- app：`UserSummaryQueryService`；`AvatarQueryService` 改按 ID 定位；`AvatarChanged` 删除（地址不再要用户名，它只剩头像一个字段），`ChangeAvatarCommand` 的结果改为 `Avatar`，`ChangeAvatarHandler` 里的 `Swapped.username` 一并删。
- infra：`UserReadAdapter` 两条 IN 查询的批量读；`UserProfileDao.findByUserIdIn`。
- adapter：`UserEndpointImpl`；`PublicAvatarController` 路由改为 `/users/{userId}/avatar`；`AvatarUrls` 改为按头像里的用户 ID 拼；`PublicUserResponse` 加 `userId`。
- 文档：identity README；PAP-95、PAP-97 两份工程 spec 加修订注记。

**不做**：

- 网关：不改规则与代码，只把一条 IT 里的示例路径改成新形态（第 9 节）。`/patra-identity/users/**` 已在公开名单里，新头像路径落在其中；`/*/_internal/**` 已在拒绝名单里，新内部接口对外 403（第 9 节）。
- 网关身份断言：不加字段（release spec 决策 F）。
- Outbox、事件：不建（release spec 决策 G）。
- 门户的头像转发路由与展示、主页取 `userId`：属于 PAP-114，本 Issue 只在第 10 节写清楚门户要跟着改什么。
- interaction 侧的客户端装配：属于 PAP-113（PAP-111 建骨架）。本 Issue 只交付 `patra-identity-api` 里的契约。

## 3. 已定的前提

| 来源 | 内容 |
|---|---|
| release spec 决策 E | 内部接口路径、上限 100、可带访客 ID、返回字段集合、已注销返回占位不带用户名；头像按用户 ID；公开主页带用户 ID |
| release spec 决策 F | interaction 只透传访客 ID，不自己实现隐私规则；公开收藏夹可见当且仅当 `profileVisible` 为真 |
| release spec 决策 G | interaction 的注销清理任务分批回查用户状态，`DELETED` 的物理删除其数据——也用本接口 |
| Issue To-Do | 入参保序去重；契约放 `patra-identity-api`；`profileVisible` 复用 `PublicUserReadModel` 的规则，不另写一份；删按用户名的旧头像路由 |
| Issue AC 2 | 同一用户对匿名、对登录的他人、对本人返回的 `profileVisible`，与 `GET /users/{username}` 的 404 / 200 一致；集成测试覆盖四种账号状态 × 三档主页可见性 |
| 仓库规则 | 用户名只存 identity，其他服务只存 `user_id`（`.claude/rules/project-info.md`） |
| 代码事实 | 用户名不能是纯数字（`Username` 的 `DIGITS_ONLY`），所以 `/users/{纯数字}/avatar` 与 `/users/{username}` 不会混淆 |
| 代码事实 | 下游服务的安全链对 `/_internal/**` 不做认证，是否需要登录由 `CurrentUserPort.require()` 保证（`SecurityServletAutoConfiguration`），新接口不用登记 |
| 代码事实 | 注销执行（`ExecuteDueAccountDeletionsHandler`）时 `UserProfile.erase()` 清空昵称、资料、头像键并把隐私恢复默认（主页 `PUBLIC`），同一处再撤销全部会话；`idn_user` 行保留，用户名永不释放 |

## 4. 主页可见性规则：抽成 `ProfileAccess`

### 4.1 现状

`PublicUserReadModel.audience()` 与 `isVisibleTo(requesterId)` 是现在唯一的主页可见性规则：

- 账号不是 `ACTIVE` 或邮箱未验证 → 只有本人（`SELF`）；否则按用户设的主页可见性（`PUBLIC` / `SIGNED_IN` / `SELF`）。
- 本人永远能看；`PUBLIC` 谁都能看；`SIGNED_IN` 要有请求者；`SELF` 别人看不到。

批量摘要如果直接加载 100 个完整的 `PublicUserReadModel`，每人还要多查链接、关注领域两张子表，而摘要用不到它们。

### 4.2 抽取

新值对象 `domain/model/read/ProfileAccess`（record，4 个参数，`of()`）：

```java
public record ProfileAccess(
    long ownerId, UserStatus status, boolean emailVerified, ProfileVisibility visibility) {

  /// 实际能被谁看到（规则原样搬自 PublicUserReadModel.audience()）。
  public PublicAudience audience() { ... }

  /// 请求者能不能看到。DELETED 对任何人都不可见（含本人），先于本人判断；其余同现状。
  public boolean isVisibleTo(Optional<Long> requesterId) { ... }
}
```

`PublicUserReadModel` 加 `access()` 返回 `ProfileAccess.of(userId, status, emailVerified, privacy.profile())`，`audience()`、`isVisibleTo()` 改为委托它。`UserSummaryReadModel`（第 5 节）同样委托。规则只在 `ProfileAccess` 里有一份。

### 4.3 有意的行为修订：`DELETED` 对本人也不可见

现状下 `isVisibleTo` 对本人无条件返回真，所以「本人」身份请求已注销用户的主页会得到 200。摘要接口对 `DELETED` 必须返回占位、`profileVisible` 为假；两边要一致（AC 2），所以规则改为 `DELETED` 先于本人判断，一律不可见。

实际影响为零：注销执行时会话已全部撤销、凭据已删，已注销的用户拿不到身份断言，「本人看已注销的自己」在线上不会发生；只有测试里伪造断言才能触达。`BANNED` 不改：封禁时会话同样全被撤销，本人分支同样触达不到，保持现状避免无谓的改动。

`PublicUserReadModelTest` 的 `should_be_visible_according_to_matrix` 现在只有 `PENDING_DELETION` 一行非 ACTIVE 用例，补两行：`BANNED` 本人可见、`DELETED` 本人不可见。`audience()` 不变（`DELETED` 仍是 `SELF`）。

## 5. 内部接口 `POST /_internal/users/summaries`

### 5.1 契约（`patra-identity-api`）

```java
@HttpExchange(url = "/_internal/users", accept = "application/json", contentType = "application/json")
public interface UserEndpoint {
  @PostExchange("/summaries")
  UserSummariesResp summaries(@RequestBody UserSummariesReq request);
}

public record UserSummariesReq(List<Long> userIds, Long viewerUserId) { of(...) }
public record UserSummariesResp(List<UserSummaryResp> users) { of(...)；紧凑构造器 copyOf }

@Builder
public record UserSummaryResp(
    Long userId,
    String username,       // 只用来拼主页链接 /u/{username}；DELETED 为 null
    String displayName,    // 昵称，没设时已回退成用户名；DELETED 为 null
    String avatarUrl,      // /users/{userId}/avatar?v={hash}；没头像、BANNED、DELETED 为 null
    int avatarPalette,     // 默认头像色号 0–11
    AccountStatus accountStatus,
    boolean emailVerified, // DELETED 为 false
    boolean profileVisible) {}

public enum AccountStatus { ACTIVE, BANNED, PENDING_DELETION, DELETED }
```

- 字段名用 `displayName` 而不是 Issue 写的 `nickname`：identity 的全部响应（`/auth/me`、`GET /users/{username}`）都叫 `displayName`，同一个概念不起两个名字。
- `avatarPalette` 是 Issue 没列的字段：门户画默认头像（首字 + 底色）要它，色号算法 `AvatarPalette.of(userId)` 只在 identity 里，收藏夹所有者没上传头像时门户没有别的来源。
- `AccountStatus` 放在 `patra-identity-api`：api 模块不能依赖 domain，按名字与 `UserStatus` 一一映射。
- 请求、响应的 `userId` 都是 Snowflake `Long`。identity 的 MVC 层把 `Long` 序列化成 JSON 字符串（`patra-tool-web`），调用方的 HTTP Interface 客户端反序列化回 `Long`，网关调 `SessionEndpoint` 已经这么用。

### 5.2 请求规则

| 输入 | 结果 |
|---|---|
| `userIds` 为 `null` | 422，字段 `userIds`，原因 `REQUIRED` |
| `userIds` 含 `null` 元素 | 422，字段 `userIds`，原因 `INVALID_FORMAT` |
| `userIds` 长度 > 100（按原始长度算，去重之前） | 422，字段 `userIds`，原因 `TOO_MANY` |
| `userIds` 为空列表 | 200 `{ "users": [] }`，不查库 |
| 有重复 ID | 按第一次出现的位置保留一个 |
| `viewerUserId` 为 `null` | 按匿名访客算 |

上限按原始长度算：调用方能预知会不会被拒，不依赖输入里有多少重复。错误走现有的 `InvalidUserFieldsException`（422，`IDN-0422`，`errors[]`）。

### 5.3 响应规则

- 顺序：按去重后的请求顺序。
- **identity 不认识的 ID 从结果里省略**，不报错、不返回占位；打一条 `WARN`（只列 ID）。identity 从不物理删除用户行（已注销也保留），不认识的 ID 只可能是调用方的数据错误。调用方按 `userId` 配对，配不上的自行处理；interaction 的注销清理任务不能把「缺席」当成「已注销」（第 11 节交接）。
- 每个用户按状态出字段：

| 状态 | `username` | `displayName` | `avatarUrl` | `emailVerified` | `profileVisible` |
|---|---|---|---|---|---|
| `ACTIVE` | 当前用户名 | 昵称，没设时回退成用户名 | 有头像时给 | 实际值 | `ProfileAccess.isVisibleTo(viewer)` |
| `PENDING_DELETION` | 同上 | 同上 | 同上 | 实际值 | 同上（规则得出：别人看不到，本人能看到） |
| `BANNED` | 同上 | 同上 | `null` | 实际值 | 同上（规则得出：别人看不到） |
| `DELETED` | `null` | `null` | `null` | `false` | `false` |

- `BANNED` 的 `avatarUrl` 为 `null`：头像路由对 `BANNED` 回 404（第 6 节），给出地址只会让门户拿到一张坏图。规则写成 `UserStatus.servesAvatar()`（`ACTIVE`、`PENDING_DELETION` 为真），摘要和头像路由共用。
- `DELETED` 的 `displayName` 不能走「没设昵称就回退成用户名」：`erase()` 已清空昵称，回退会把用户名吐出去。占位分支在读模型的 `viewFor` 里先于回退判断。
- 「已注销用户」这类文案由门户渲染，identity 只给 `accountStatus = DELETED` 和全空的身份字段。

### 5.4 信任边界

`viewerUserId` 取自请求体，不取自调用方转发的身份断言：identity 信任内部调用方传来的访客 ID。理由：`/_internal/**` 在网关一律 403，只有服务发现直连能到；interaction 的注销清理任务没有访客；以后别的服务替某个访客问时也是同一形状。代价：interaction 若把访客 ID 传错，可见性就会算错——这是 interaction 的正确性责任，在 PAP-113 用它自己从断言里取的当前用户 ID 填。

### 5.5 关于头像地址外泄

PAP-97 已定：头像读取不看主页可见性，版本号 `v` 是 64 位随机量，「只出现在请求者有权看到的响应里」所以猜不到。摘要接口无论 `profileVisible` 真假都给 `avatarUrl`（`BANNED`、`DELETED` 除外），把「只在有权看到时下发」的责任交给调用方：interaction 对 `profileVisible` 为假的所有者，不能把它的头像地址放进给这位访客的响应。本版 interaction 只在两处嵌入所有者信息——本人的文献库（本人永远可见）和公开收藏夹页（`profileVisible` 为假时整页 404），满足这条。

### 5.6 分层

| 层 | 类 | 职责 |
|---|---|---|
| domain | `UserSummaryReadModel`（record，`@Builder`：`userId`、`username`、`displayName`（资料原值，可空）、`avatar`、`status`、`emailVerified`、`profileVisibility`） | `access()`；`viewFor(Optional<Long> viewerId)` → `UserSummaryView` |
| domain | `UserSummaryView`（record，`@Builder`：`userId`、`username`、`displayName`、`avatar`、`avatarPalette`、`status`、`emailVerified`、`profileVisible`） | 第 5.3 节表格的结果，已按状态与访客处理好 |
| domain | `UserReadPort.findSummaries(Collection<Long> userIds)` | 返回存在的那些，不保证顺序 |
| infra | `UserReadAdapter.findSummaries` | `userDao.findAllById(ids)` + `profileDao.findByUserIdIn(ids)` 两条 `IN` 查询，按 `userId` 拼；资料行缺失照现有做法抛 `IllegalStateException` |
| app | `UserSummaryQueryService.summaries(List<Long> userIds, Optional<Long> viewerId)`（`app/usecase/user/query/`） | 校验（5.2）、保序去重、读、按请求顺序重排、缺席记 `WARN`、逐个 `viewFor` |
| adapter | `UserEndpointImpl implements UserEndpoint`（`adapter/rest/internal/`） | 请求 → 服务 → `UserSummaryView` 映射成 `UserSummaryResp`（`avatarUrl` 用 `AvatarUrls.of(avatar)`，`accountStatus` 按名字映射） |

字段违例常量加在 `UserFieldViolations`：字段名 `USER_IDS = "userIds"` 与三个工厂方法；原因码 `REQUIRED`、`INVALID_FORMAT`、`TOO_MANY` 都已存在。上限 `100` 是 `UserSummaryQueryService.MAX_USER_IDS`。

不经 CommandBus，没有事务，与 `UserQueryService` 同一做法。

## 6. 头像改按用户 ID

### 6.1 地址形态

`avatarUrl`：`/users/{userId}/avatar?v={hash}`，仍是 identity 相对路径，带版本、不带尺寸和基地址（PAP-97 决策 G 不变）。`Avatar` 记录本身带 `userId`，`AvatarUrls.of(Avatar avatar)` 只要一个参数：

```java
public static String of(Avatar avatar) {
  return avatar == null ? null : "/users/" + avatar.userId() + "/avatar?v=" + avatar.hash();
}
```

三处调用方一起改：`CurrentUserResponse`（`/auth/me` 及同构的 `user`）、`MeAvatarController`（`POST /me/avatar`，拼好后放进 `AvatarResponse`）、`PublicUserResponse`；新增的 `UserEndpointImpl` 也用它。

### 6.2 路由

`GET /users/{userId}/avatar?size=512|128&v={hash}`，替换 `GET /users/{username}/avatar`（旧路由删除，不留跳转）。

- 路径变量先按 `^[1-9][0-9]{0,18}$` 校验再 `Long.parseLong`，溢出也算不合法；不合法一律 404。不直接 `parseLong`：它接受 `+1`、`007`，同一个头像会有多个地址，破坏「一个地址一份内容」。
- 其余规则不变：`v` 必须等于当前哈希；`size` 只认 `512`、`128`；`UserStatus.servesAvatar()` 为假（`BANNED`、`DELETED`）404；不看主页可见性；`Cache-Control: public, max-age=31536000, immutable`；`ETag: "{hash}-{size}"`；`If-None-Match` 命中 304。
- 缓存头不用改：`ETag` 本来就不含用户名，地址按 ID 后改名不再让地址失效，`immutable` 的前提（同一地址内容不变）反而更稳。集成测试在新路径上重新钉住这几个头。
- 只认当前用户名、旧名 404 这条随之消失：地址里没有用户名了，改名后旧地址照常可用——这正是 AC 3。

### 6.3 读端口

`UserReadPort.findAvatarOwner(Username)` 改为 `findAvatarOwner(long userId)`：`userDao.findById` + 资料行里的头像键，不读子表。`AvatarOwner` 的 Javadoc 改为「按用户 ID 找到的用户」。`AvatarQueryService.locate(String rawUserId, String size, String version)`。

## 7. 公开主页带 `userId`

- `PublicUserView` 加 `userId`（`long`），`PublicUserReadModel.viewFor` 填入。
- `PublicUserResponse` 加 `userId`（`Long`，JSON 里是字符串），放在第一个字段。
- 只在 200 的主页响应里加；旧名 301 的 `MovedUserResponse` 不加（门户只用其中的新用户名做跳转）。
- `PublicUserView`、`PublicUserResponse`、`PublicUserReadModel` 里「不含用户 ID」「视图里不带用户 ID」的 Javadoc 删改。`avatarPalette` 仍由服务端算好下发，不让门户自己按 ID 算（算法只有一份）。

## 8. 测试

### 8.1 单元测试

| 测试 | 覆盖 |
|---|---|
| `ProfileAccessTest`（新） | 四种状态 × 邮箱是否验证 × 三档可见性的 `audience()`；匿名 / 他人 / 本人的 `isVisibleTo`；`DELETED` 对本人也不可见 |
| `PublicUserReadModelTest`（改） | 委托后的行为不变；可见性矩阵补 `BANNED` 本人可见、`DELETED` 本人不可见两行；`viewFor` 带出 `userId` |
| `UserSummaryReadModelTest`（新） | 第 5.3 节表格逐行：`DELETED` 占位不带用户名、不回退昵称；`BANNED` 无头像；昵称回退；`avatarPalette`；`profileVisible` 委托 |
| `UserSummaryQueryServiceTest`（新） | `null` / 含 `null` / 101 个 → 422 且原因码对；空列表不调端口；去重保序；端口乱序返回时按请求顺序重排；缺席的被省略 |
| `AvatarQueryServiceTest`（改） | 按 ID 定位；`abc`、`0`、`+1`、`007`、超长、溢出 → 404；`BANNED` / `DELETED` 404 |
| `UserStatusTest`（新，或并入现有） | `servesAvatar()` 四个状态 |

### 8.2 集成测试

| 测试 | 覆盖 |
|---|---|
| `UserReadAdapterIT`（改） | `findSummaries`：多个 ID 一次读出、不存在的不返回、资料原值（昵称为空时 `displayName` 为 `null`）；`findAvatarOwner(long)` |
| `UserEndpointImplIT`（新，adapter 切片，对照 `SessionEndpointImplIT`） | 请求体 / 响应体的 JSON 形状（`userId` 是字符串、`accountStatus` 是名字）；422 的 `errors[]` |
| `PublicAvatarControllerIT`、`MeAvatarControllerIT`、`PublicUserControllerIT`、`AuthControllerIT`（改） | 新路径与新 `avatarUrl` 形态；公开主页带 `userId` |
| `UserSummariesFlowIT`（新，boot，`RANDOM_PORT`，PG17 + Redis） | ① 100 个 ID（含重复与不存在的）按请求顺序返回；② 已注销用户占位：响应体里不出现该用户名的字符串；③ **AC 2 矩阵**：四种状态（`ACTIVE` 已验证、`PENDING_DELETION`、`BANNED`、`DELETED`）× 三档主页可见性 × 三种访客（匿名、登录的他人、本人），每格同时请求摘要接口与 `GET /users/{username}`，断言 `profileVisible == (状态码 == 200)`；另加 `ACTIVE` 未验证一行（release spec 2.1.2 点名了「未验证」）。状态用 SQL 改（同 `PublicProfileFlowIT`），本人 / 他人的断言用 `assertionFor` 伪造 |
| `AvatarFlowIT`（改） | `/auth/me` 与 `/users/{u}` 的 `avatarUrl` 一致且是按 ID 的形态；**改用户名后**：`avatarUrl` 不变、按原地址 `GET` 仍 200（AC 3）；旧的按用户名地址 404（`/users/{username}/avatar` 不再匹配任何路由的头像语义：路径变量不是纯数字即 404） |
| `PublicProfileFlowIT`（改） | `PUBLIC_KEYS` 加 `userId`，值等于注册时的用户 ID（AC 4） |
| `PublicIdentityFlowIT`（改，若断言了响应键集合或头像地址） | 同步 |

AC 2 矩阵里「本人看已注销的自己」与「本人看被封禁的自己」两格在线上触达不到（会话已撤销），测试照样跑，钉住两个接口在这两格上也一致。

### 8.3 门控

`./gradlew spotlessApply` → `./gradlew :patra-api:patra-identity:...:check` 各模块（含 R6 `checkMigrationColumns`、`dumpModuleGraph` 一致性；本 Issue 不改迁移脚本、不改模块依赖）→ identity 各模块 `integrationTest`。PR 前的全量 `./gradlew check` 在后端统一 PR 时跑。

## 9. 网关

不改代码。核对两点并在计划里留一条验证：

- `GatewaySecurityConfiguration.BLOCKED_PATHS` 含 `/*/_internal/**`：`/patra-identity/_internal/users/summaries` 对外 403。
- `IDENTITY_PUBLIC_PATHS` 含 `/patra-identity/users/**`：`/patra-identity/users/{userId}/avatar` 匿名可达。

`GatewayAuthenticationIT.should_forward_anonymous_avatar_read_with_cache_headers` 里头像转发的路径 `/users/chen-yu/avatar` 是 WireMock 回放的示例字符串，不依赖 identity 的路由形态；为了不误导读者，把它改成纯数字 ID 的形态（只改字符串，不改断言）。

## 10. 门户要跟着改的（PAP-114，本 Issue 不做）

| 位置 | 现在 | 要改成 |
|---|---|---|
| `patra-portal/src/lib/identity/avatar.ts` 的 `AVATAR_URL` | `^\/users\/([A-Za-z0-9_-]+)\/avatar\?v=([0-9a-f]{16})$`，捕获用户名 | `^\/users\/([1-9][0-9]{0,18})\/avatar\?v=([0-9a-f]{16})$`，捕获用户 ID；`avatarSrc` 拼 `/api/users/{userId}/avatar?...` |
| `patra-portal/src/app/api/users/[username]/avatar/route.ts` | 路由段 `[username]`，用户名正则 | 目录改为 `[userId]`，按 `^[1-9][0-9]{0,18}$` 校验，转发 `/patra-identity/users/{userId}/avatar?size=&v=` |
| `patra-portal/src/lib/identity/public-profile.ts` 的 schema | 无 `userId` | 加 `userId: z.string()`（JSON 里是字符串），主页「收藏夹」栏用它向 interaction 取公开收藏夹 |
| 相关单测 / e2e | 断言按用户名的头像地址 | 改为按 ID |

过渡期：后端先合入时门户还没改，`avatar.ts` 与转发路由的正则 `[A-Za-z0-9_-]+` 也能匹配纯数字，转发出去的正是新路径，mini 上头像不会断；PAP-114 只需把正则收紧、路由段改名，不是救火。

`/auth/me` 的 `user` 原本就带 `userId`，门户不受影响。

## 11. 交接给 interaction（PAP-111 / PAP-113）

- 依赖 `patra-identity-api`，按 `SessionEndpoint` 在网关里的做法建 HTTP Interface 客户端，分组 `patra.http-client.http-interface.groups.identity`，`base-url: lb://patra-identity`。
- 一次最多 100 个，超过要自己分批；去重可交给 identity。
- 结果按 `userId` 配对；缺席的 ID 不等于已注销，注销清理任务只删 `accountStatus == DELETED` 的。
- `profileVisible` 为假的所有者：不要把其头像地址、用户名放进给这位访客的响应（第 5.5 节）。
- `viewerUserId` 填当前请求断言里的用户 ID，匿名填 `null`；清理任务填 `null`。

## 12. 文档

| 文件 | 改动 |
|---|---|
| `patra-api/patra-identity/README.md` | 第 1 节模块表加 `UserEndpointImpl`、`UserSummaryQueryService`、`ProfileAccess`；第 2 节接口表：头像行改路径、加 `POST /_internal/users/summaries` 行、`GET /users/{username}` 的键集合加 `userId`；第 12 节公开主页键集合；第 15 节「读取」与「`avatarUrl`」两条改为按用户 ID；开头的工程设计列表加本 spec |
| `docs/patra/specs/2026-10-22-identity-profile-design.md`（PAP-95） | 「不含邮箱、用户 ID、状态」处与 AC 6 对照表处加修订注记：2026-10-24 PAP-108 起公开主页带 `userId`，依据 v0.13 决策 E；`DELETED` 对本人也不可见 |
| `docs/patra/specs/2026-10-22-identity-avatar-design.md`（PAP-97） | 8.3 节「旧用户名 404」、`avatarUrl` 形态、第 17 节已拍板 3 处加修订注记：2026-10-24 PAP-108 起按用户 ID |

修订注记只追加、不改写原文（原文是当时的决定）。

## 13. 验收对照

| AC | 证据 |
|---|---|
| 一次请求 100 个 ID 时按请求顺序返回；已注销用户不暴露用户名 | `UserSummariesFlowIT` ①②；`UserSummaryQueryServiceTest`；`UserSummaryReadModelTest` |
| 匿名 / 他人 / 本人的 `profileVisible` 与主页 404 / 200 一致（四种状态 × 三档可见性） | `UserSummariesFlowIT` ③ |
| 改用户名后头像地址不变且可访问；按用户名的旧头像路由已删除 | `AvatarFlowIT`；`PublicAvatarControllerIT` |
| 公开主页响应包含 `userId` | `PublicProfileFlowIT`；`PublicUserControllerIT` |

## 14. 由我定的点与待拍板的点

**由我定的**（评审时请重点看）：

1. **`DELETED` 对本人也不可见**（4.3）：现有公开主页行为的修订，线上不可触达，为了让 AC 2 的两个接口在所有格子上一致。
2. **字段名 `displayName`** 而不是 Issue 写的 `nickname`（5.1）。
3. **多给一个 `avatarPalette`**（5.1）：Issue 没列，门户画默认头像要它。
4. **不认识的 ID 省略**，不返回占位、不报错（5.3）。
5. **上限按去重前的原始长度算**；空列表 200 空结果（5.2）。
6. **`BANNED` 的 `avatarUrl` 为 `null`**，与头像路由一致（5.3）。
7. **头像路径仍在 `/users/` 下**（`/users/{userId}/avatar`），网关零改动；靠「用户名不能纯数字」区分（6.2）。
8. **`viewerUserId` 信任请求体**，不取转发的断言（5.4）。
9. **`AccountStatus` 枚举放 `patra-identity-api`**（5.1）。

**待拍板**：无。
