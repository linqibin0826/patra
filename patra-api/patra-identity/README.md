# patra-identity

前台用户的账号服务：注册、登录、会话、登出、「当前用户」、用户名、资料与隐私设置、公开主页、邮箱验证与邮件、再认证、改密码、找回密码、改邮箱、登录设备与新设备提醒、注销账号、登录失败限制、封禁与解封。

工程设计：`docs/patra/specs/2026-10-05-identity-account-design.md`（账号）、`docs/patra/specs/2026-10-09-identity-session-design.md`（会话）、`docs/patra/specs/2026-10-20-identity-account-rebuild-design.md`（账号重建：用户名、昵称、邮箱验证状态、四态状态机、`V1` 最终形态）、`docs/patra/specs/2026-10-21-identity-email-verification-design.md`（邮件发送与邮箱验证）、`docs/patra/specs/2026-10-22-identity-account-security-design.md`（账号安全：再认证、改密码、找回密码、改邮箱）、`docs/patra/specs/2026-10-22-identity-profile-design.md`（资料、隐私设置与公开主页）、`docs/patra/specs/2026-10-22-identity-login-devices-design.md`（登录设备：受信转发链路、设备与属地、会话列表、踢设备、新设备邮件、记录清理）、`docs/patra/specs/2026-10-22-identity-account-deletion-design.md`（注销账号：冷静期、取消、到期执行与匿名化、提醒）、`docs/patra/specs/2026-10-23-session-introspection-design.md`（会话存储归 identity 独占、网关查会话的内部接口）、`docs/patra/specs/2026-10-24-identity-external-contract-design.md`（对外契约：批量用户摘要、头像按用户 ID、公开主页带用户 ID）、`docs/patra/specs/2026-10-27-identity-mention-support-design.md`（提及支撑：前缀联想、用户名批量解析）。

## 1. 模块

| 模块 | 内容 |
|---|---|
| `patra-identity-domain` | `User`、`UserProfile`、`FormerUsername`、`UserPasswordCredential`、`UserLoginRecord`、`EmailToken` 六个聚合，邮箱、用户名、昵称、密码、客户端信息、令牌密文值对象，资料值对象（`vo/profile/`：简介、链接、职称、科室、ROR ID、MeSH UI、机构、关注领域、隐私设置），用户名 / 昵称 / 资料字段（`ProfilePolicy`，拒绝字符与昵称共用 `ProfileTextRules`）/ 改名 / 密码 / 失败限制 / 重发限流 / 再认证 / 找回密码限流 / 改邮箱限流 / 邮件链接规则、会话策略，端口（含邮件发送 `EmailDeliveryPort`、三个限流端口、向 catalog 取名称快照的 `CatalogReferencePort`），主页可见性规则 `ProfileAccess`（公开主页与批量用户摘要共用的唯一一份），公开主页读模型（`viewFor` 按请求者裁剪）与批量用户摘要读模型 `UserSummaryReadModel`，@ 联想用的用户名、昵称前缀值对象 `UsernamePrefix`、`DisplayNamePrefix`，`SessionIssuer`、`EmailTokenIssuer`、`ReauthenticationGuard` 领域服务，`SessionTerminator`（撤销会话并按原因结束登录记录）、`ClientOriginResolver`（请求来源 → 设备名、端、属地，内网判定 `IpAddressScope`）、`UserAgentParsingPort` / `IpGeolocationPort`，新设备提醒规则 `NewDeviceLoginPolicy` 与限流端口，注销规则 `AccountDeletionPolicy`（冷静期、提醒提前量），头像（`vo/avatar/`：`Avatar`、两档尺寸、两档字节，`AvatarPalette` 默认头像色号、`AvatarPolicy`，出图端口 `AvatarImagePort` 与存储端口 `AvatarStoragePort`），十三个 `OutgoingMail` 记录，领域异常 |
| `patra-identity-app` | 注册、登录、登出、再认证、改用户名、改密码、找回密码（申请、重设）、改邮箱（申请、取消、确认、撤销）、封禁、解封、重发验证邮件、验证邮箱、清理过期令牌、改资料、改隐私设置、踢单个设备、踢其他设备、清理登录记录、发起注销、取消注销、注销到期执行、注销到期前提醒、换头像、移除头像二十八个处理器，走 CommandBus；`CurrentPasswordVerifier` 给再认证与改密码共用；「当前用户」、本人的资料与隐私设置、待确认的改邮箱和公开主页走 `UserQueryService`，登录设备列表走 `SessionQueryService`，网关查会话走 `SessionIntrospectionService`（不经 CommandBus），头像读取走 `AvatarQueryService`，批量用户摘要走 `UserSummaryQueryService`，@ 联想走 `UserSuggestionQueryService`，用户名批量解析走 `UserResolutionQueryService` |
| `patra-identity-infra` | JPA 持久化（含令牌表）、Argon2id 哈希、常见密码名单、Redis 失败限制与通用限流脚本 `action-throttle.lua`（重发、找回密码、改邮箱三个适配器共用）、会话存储（`RedisSessionStore`、八段 Lua、令牌格式，第 9 节）与适配器、新设备提醒限流适配器，SMTP 发信适配器与 Thymeleaf 邮件模板（十三对），Yauaa 解析 UA（`YauaaUserAgentParsingAdapter`）、ip2region 查属地（`Ip2RegionGeolocationAdapter`）、Flyway 脚本（一份最终形态的 `V1`，加上 `V2` 的用户名与昵称前缀索引）、catalog 内部接口适配器 `CatalogReferenceAdapter`（依赖 `patra-catalog-api`），头像出图 `AvatarImageAdapter`（TwelveMonkeys 解码、`webp-imageio` 编码）与魔数识别 `AvatarFormatSniffer`、头像存储 `AvatarStorageAdapter`（依赖 `patra-tool-object-storage`）；测试夹具 `AvatarImageFixture`（`testFixtures`，boot 的流程测试也用） |
| `patra-identity-adapter` | 前台的 `AuthController`、`LogoutController`、`EmailVerificationController`、`PasswordResetController`、`EmailChangeController`、`MeController`、`MeEmailController`、`MeProfileController`、`MeSessionController`、`MeDeletionController`、`MeAvatarController`、`PublicUserController`、`PublicAvatarController`，后台的 `AdminUserController`，内部接口 `SessionEndpointImpl`（网关查会话）、`UserEndpointImpl`（批量用户摘要、@ 联想、用户名批量解析，interaction 调），XXL-Job 任务 `EmailTokenCleanupScheduleJob`、`LoginRecordCleanupScheduleJob`、`AccountDeletionScheduleJob`、`AccountDeletionReminderScheduleJob` |
| `patra-identity-boot` | 启动类、配置、安全 starter 接入、catalog 两个 HTTP Interface 代理（`CatalogClientConfiguration`，`lb://patra-catalog`） |
| `patra-identity-api` | 服务间契约：网关查会话的 `SessionEndpoint`（PAP-106）；interaction 调的 `UserEndpoint`：批量用户摘要（PAP-108）、@ 联想与用户名批量解析（PAP-118）；各自的请求、响应 DTO 与 `AccountStatus` |

### 子域与包

每层模块下先按子域、再按组件类型分包，子域内部的分包按全项目统一的分包规范（`.claude/rules/tech/package-layout.md`），由 `IdentityArchitectureTest`（`patra-tool-test` 的 `SubdomainArchitectureRules`）守住：

```
dev.patra.identity.{domain|app|infra|adapter|api}.{session|account}..
dev.patra.identity                  启动类
dev.patra.identity.config           装配（XXL-Job 执行器、catalog 客户端、配置属性），不属于任何子域
```

| 子域 | 职责 |
|---|---|
| `session`（底座） | 会话签发与撤销（`SessionIssuer`、`SessionTerminator`）、会话存储（`SessionStorePort`，Redis 实现在 `infra.session.store`）、登录记录 `UserLoginRecord`、再认证守卫与会话生命周期策略、登出、踢设备、登录设备列表、网关查会话、登录记录清理；以及被两个子域共用的东西：客户端与设备值（`ClientOrigin`、`LoginClient`、`DeviceId`、`DeviceClass`、`LoginEndReason`、`IpAddressScope`）、安全通知 `SecurityNotice`、字段校验 `UserFieldViolations` 与 `InvalidUserFieldsException`、`TemporarilyUnavailableException` 等共用异常与服务级 `IdentityErrorTrait`、新设备提醒规则 `NewDeviceLoginPolicy` 与限流端口、限流脚本 `ActionThrottleScript` 与 Redis 故障判定 `TransientRedisFailures`（`infra.session.support`） |
| `account` | 用户与围绕用户的全部流程：注册、登录、再认证、密码、用户名、邮箱验证与更换、封禁、注销、资料与隐私、头像、公开主页、批量用户摘要与提及支撑、邮件（`EmailDeliveryPort` 与十三种邮件 `domain.account.model.vo.mail`） |

- 底座 `session` 对 account 完全公开，自己不依赖 account；account 只依赖底座，没有其他依赖边。
- 底座只放被两个子域共用的东西：只被 account 用的（请求来源 `RequestOrigin`、UA 解析结果 `DeviceProfile`、邮件公共接口 `OutgoingMail`）归 account。

## 2. 接口

| 路径 | 成功 | 网关规则（PAP-65 / PAP-93） |
|---|---|---|
| `POST /auth/register` | 201 `{ sessionToken, user }` | 公开 |
| `POST /auth/login` | 200 `{ sessionToken, user }` | 公开 |
| `POST /auth/logout` | 204，有当前用户就删会话，没有也 204 | 公开 |
| `GET /auth/me` | 200 `user`（下面的结构） | 需要登录 |
| `PATCH /me/username` | 200 `{ username, nextUsernameChangeAt }` | 需要登录（`/me/**` 整体） |
| `POST /me/email/verification/resend` | 202 `{ email, nextResendAt }`，无请求体 | 需要登录 |
| `POST /auth/email/verify` | 200 `{ email, emailVerifiedAt }`，请求体 `{ token }` | 公开（匿名设备点链接也能验证；带会话时网关照样翻译令牌） |
| `POST /auth/reauth` | 200 `{ sessionToken, user }`，请求体 `{ password }`；新令牌，旧令牌立即失效，会话 ID 不变；上次密码验证距今不足 10 秒时 `sessionToken` 为 `null`，沿用现有令牌 | 需要登录 |
| `PUT /me/password` | 200 `{ sessionToken, user }`，请求体 `{ currentPassword, newPassword }`；本机换新令牌，其他会话全部撤销 | 需要登录 |
| `POST /auth/password/forgot` | 202 `{ email, nextRequestAt }`，请求体 `{ email }`；注册与否一样 | 公开 |
| `POST /auth/password/reset` | 200 `{ email }`，请求体 `{ token, newPassword }`；不自动登录 | 公开 |
| `POST /me/email/change` | 202 `{ newEmail, expiresAt }`，请求体 `{ newEmail }` | 需要登录 + 再认证 |
| `GET /me/email/change` | 200 `{ newEmail, expiresAt }`，没有待确认 204 | 需要登录 |
| `DELETE /me/email/change` | 204 | 需要登录 |
| `POST /auth/email/change/confirm` | 200 `{ email, emailVerifiedAt }`，请求体 `{ token }` | 公开 |
| `POST /auth/email/change/revert` | 200 `{ email }`，请求体 `{ token }` | 公开 |
| `GET /me/profile` | 200 本人资料原值（第 12 节） | 需要登录 |
| `PUT /me/profile` | 200 保存后的资料；整份替换，省略即清空 | 需要登录 |
| `GET /me/privacy` | 200 `{ profileVisibility, professionalVisibility, searchIndexing }` | 需要登录 |
| `PUT /me/privacy` | 200 同上，三项必填 | 需要登录 |
| `GET /me/sessions` | 200 `{ sessions: [{ id, current, deviceName, deviceClass, ipAddress, ipRegion, privateNetwork, signedInAt, lastActiveAt }] }`，只含活会话，本机第一（第 13 节） | 需要登录 |
| `DELETE /me/sessions/{id}` | 200 `{ revokedSessions }`（0 或 1，会话已不在也 200）；踢本机 409 | 需要登录 + 再认证 |
| `DELETE /me/sessions/others` | 200 `{ revokedSessions }` | 需要登录 + 再认证 |
| `GET /users/{username}` | 200 公开主页 `{ userId, username, displayName, avatarUrl, avatarPalette, audience, joinedAt, bio, links, professional, researchInterests, indexable }`，按请求者裁剪（第 12 节）；保留期内的旧名 301，`Location: /users/<新名>`，体 `{ username: <新名> }` | 公开（`/users/**` 整体，网关照样翻译令牌） |
| `POST /me/avatar` | 200 `{ avatarUrl }`，multipart，part 名 `file`；超过 5 MB 413，其余拒绝 422（第 15 节） | 需要登录 |
| `DELETE /me/avatar` | 204；没有头像也是 204 | 需要登录 |
| `GET /users/{userId}/avatar?size=512\|128&v={hash}` | 200 `image/webp`，`Cache-Control: public, max-age=31536000, immutable`、`ETag`；`If-None-Match` 命中 304；对不上一律 404 | 公开（`/users/**` 整体） |
| `POST /me/deletion` | 200 `{ deletionDueAt }`，请求体 `{ username }`（不分大小写）；进入 15 天冷静期，本机在内全部会话下线（第 14 节） | 需要登录 + 再认证 |
| `DELETE /me/deletion` | 200 `user`（与 `/auth/me` 同构）；本来就正常时照样 200 | 需要登录 |
| `POST /_internal/sessions/introspect` | 200 `{ active, userId, sessionId, accountType, clientType }`，查不到 `{ active: false }`；Redis 暂时不可用 503（第 9 节） | 拒绝外部访问；网关经服务发现直连 |
| `POST /_internal/users/summaries` | 200 `{ users: [{ userId, username, displayName, avatarUrl, avatarPalette, accountStatus, emailVerified, profileVisible }] }`，请求体 `{ userIds, viewerUserId }`（第 16 节） | 拒绝外部访问；interaction 经服务发现直连 |
| `POST /_internal/users/suggest` | 200 `{ users: [...] }`，每项与批量用户摘要同形；请求体 `{ prefix, viewerUserId, limit }`（第 17 节） | 拒绝外部访问；interaction 经服务发现直连 |
| `POST /_internal/users/resolve/by-usernames` | 200 `{ users: [{ username, userId, accountStatus }] }`，请求体 `{ usernames }`（第 17 节） | 拒绝外部访问；interaction 经服务发现直连 |
| `POST /admin/users/{userId}/ban` | 204 | 拒绝外部访问 |
| `POST /admin/users/{userId}/unban` | 204 | 拒绝外部访问 |

注册请求体 `email`、`username`、`password`，登录请求体 `email`、`password`，两者都可带 `clientType`（不传按 `web`）和 `deviceId`（门户种的设备标识 Cookie：恰好 43 个 base64url 字符，其他格式 422）。客户端 IP 取 `getRemoteAddr()`、UA 取 `User-Agent` 头，经受信转发链路都是浏览器的值（第 13 节）。

登录、注册响应里的 `user` 与 `/auth/me` 同构：

```json
{
  "userId": "352303128713027974", "email": "chen.yu@example.com", "emailVerified": false,
  "username": "chen-yu", "displayName": "chen-yu",
  "avatarUrl": "/users/352303128713027974/avatar?v=3f9a1c0d2b4e5f60", "avatarPalette": 7,
  "status": "ACTIVE", "deletionDueAt": null, "nextUsernameChangeAt": null, "accountType": "user"
}
```

- `displayName` 永远非空：资料表里没设昵称就回退成当前用户名。`nextUsernameChangeAt` 是最近一次改名时间加冷却，没改过名为 `null`。`avatarUrl` 是 identity 的相对路径（带版本、不带尺寸），没上传头像为 `null`；`avatarPalette` 是默认头像色号 0–11，有没有头像都给（第 15 节）。
- `status` 只会出现 `ACTIVE`、`PENDING_DELETION`。登录在密码正确之后分流：`ACTIVE`、`PENDING_DELETION` 都建会话（后者 `deletionDueAt` 有值，门户据此进取消注销页）；`BANNED` 403；`DELETED` 与密码错误完全一样，按失败结算、401。`/auth/me` 对已封禁、已注销用户的会话返回 401。
- 公开身份卡：路径里的用户名先去空白、转小写，语法不合法直接 404。可见的条件是 `audience` 为 `PUBLIC`（`ACTIVE` 且邮箱已验证）或请求者就是本人（`SELF`，只要还能登录）；不可见一律 404，不区分原因。门户请求时带上会话令牌，本人才能看到自己。301 的 `Location` 是 identity 自己的相对路径，门户按体里的 `username` 做临时跳转（307，永久跳转会被浏览器缓存、原主改回旧名后成环），`fetch` 要用 `redirect: "manual"`；301 带 `Cache-Control: no-store`。
- `/admin/**` 本版没有身份校验，只能在内网直连调用；对 `DELETED` 用户封禁、解封都返回 409。

## 3. 账号模型

账号分前台用户（`USER`）和后台账号（`STAFF`，以后建）两类，分开建表；密码和用户也分开，各一张表。理由和调研见账号工程设计第 4 节。

| 表 | 内容 |
|---|---|
| `idn_user` | ID、邮箱（唯一、只能小写，`DELETED` 时为空）、`email_verified_at`、用户名（唯一）、最近改名时间、状态、封禁时间、注销到期时间、到期前提醒的发送时间（只在冷静期可能有值）、注销时间。用户名格式、状态与各时间列的配对都由检查约束守在表上；冷静期的到期时间上有部分索引 |
| `idn_user_password_credential` | 用户 ID（唯一）、Argon2id 编码串 |
| `idn_user_profile` | 用户 ID（唯一）、昵称（可空，空表示跟随用户名）、简介、身份大类、职称、机构（ROR ID + catalog 名称快照，或无 ROR 的手填名称）、科室、三个隐私开关 |
| `idn_user_profile_link` | 用户 ID、顺序（0 ~ 4）、标题（可空）、URL；`(user_id, lower(url))` 唯一 |
| `idn_user_research_interest` | 用户 ID、顺序（0 ~ 9）、MeSH UI、英文名快照；`(user_id, mesh_ui)` 唯一，`mesh_ui` 上有索引 |
| `idn_user_former_username` | 用户 ID、旧用户名、替换时间、保留到期时间 |
| `idn_user_login_record` | 会话 ID、用户 ID、客户端信息、结束原因（第 9 节） |
| `idn_email_token` | 用途、用户 ID、发往的邮箱、令牌哈希（SHA-256 十六进制，唯一）、过期 / 消费 / 撤销时间、申请方 IP。同一用户同一用途只留一个有效令牌，由部分唯一索引守住（改邮箱撤销令牌例外） |

改邮箱确认与撤销直接改 `idn_user.email` 并把 `email_verified_at` 写成当时（点链接的人证明了对地址的控制）；重设密码不改邮箱，只写 `email_verified_at`。「待确认的改邮箱」不是表列，是一条未过期的 `EMAIL_CHANGE_CONFIRM` 令牌。

### 状态机

| 方法 | `ACTIVE` | `BANNED` | `PENDING_DELETION` | `DELETED` |
|---|---|---|---|---|
| `ban(now)` | → `BANNED` | 不变 | → `BANNED`，清注销到期时间 | 409 |
| `unban()` | 不变 | → `ACTIVE` | 不变 | 409 |
| `requestDeletion(now, coolingPeriod)` | → `PENDING_DELETION` | 409 | 不变 | 409 |
| `cancelDeletion()` | 不变 | 409 | → `ACTIVE` | 409 |
| `completeDeletion(now)` | 409 | → `DELETED` | → `DELETED` | 不变（幂等） |
| `markEmailVerified(now)` | 记第一次 | 记第一次 | 记第一次 | 409 |
| `changeUsername(...)` | 见第 4 节 | 401 | 409（`traits` 含 `PENDING_DELETION`） | 401 |

表里的 409 是 `InvalidUserStateTransitionException`（「账号当前状态不允许此操作」）。进入 `DELETED` 时邮箱、邮箱验证时间、注销到期时间、提醒标记、封禁时间全部清空，用户名和 ID 留着，不能回退。`/me/**` 的写接口先 `requireActive()`：`PENDING_DELETION` 409，`BANNED`、`DELETED` 401。注销的发起、取消、到期执行见第 14 节；到期时资料行置空不删行（读模型要求资料行与用户一对一存在，缺行是 500）。

### 用户名规则

输入去首尾空白、转小写后依次查，只报第一条：为空 `REQUIRED`；含 `[a-z0-9_-]` 之外的字符 `INVALID_CHARACTER`；少于 3 个字符 `TOO_SHORT`；多于 30 个 `TOO_LONG`；首尾不是字母数字，或 `_` / `-` 连续 `INVALID_FORMAT`；纯数字 `DIGITS_ONLY`；命中保留字 `RESERVED`。

保留字在 `domain/policy/ReservedUsernames`，三组：路由段、角色词、站名与数据源名。匹配前做同形归一（`0→o`、`1→i`、`3→e`、`5→s`）并去掉 `_` 和 `-`，整词比较：`adm1n`、`a_d_m_i_n` 都命中 `admin`，`admin2` 不命中。前端用同一条正则 `^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$`，保留字只在后端判。

### 昵称规则

改昵称在 `PUT /me/profile` 里（传空即清空、回到跟随用户名），规则在 `DisplayNamePolicy`。规范化：NFC、连续空格类字符折叠成一个空格、再去首尾空白。校验依次：为空 `REQUIRED`；控制字符、行段分隔符、私用区、未分配码点、孤立代理项、格式字符（emoji 组合里的 ZWJ 和标签字符除外）、以组合用字符开头的字素簇 `INVALID_CHARACTER`；少于 2 个字素 `TOO_SHORT`；多于 30 个字素或超过 120 字节 `TOO_LONG`；网址、邮箱、手机号、微信号形态 `INVALID_FORMAT`；拒绝词 `RESERVED`（`domain/policy/RejectedDisplayNameWords`：中文词包含即命中、拉丁词整词命中，站名与数据源名复用保留字第三组）。字素按 `BreakIterator` 数，汉字、带变音符的字母、肤色 / ZWJ 组合的 emoji、国旗各计 1。

资料表里不存默认昵称：空就跟随用户名，改名后没设过昵称的人昵称自动跟着变。

## 4. 用户名

- 改名 `PATCH /me/username`：校验新名 → 加行锁读用户 → 状态守卫、与当前相同 422 `UNCHANGED`、冷却期内 409 → 查占用（`idn_user` 里有人用，或别人的旧名未到期）409 → 保存，旧名写进 `idn_user_former_username`。不做再认证，不撤销会话，不碰资料表。
- 冷却：两次改名间隔不少于 30 天，改回旧名也算一次；从未改过名的第一次没有冷却。
- 保留：旧名保留 90 天，期间别人不能注册或改成它，原主可以改回；`GET /users/<旧名>` 301 到新名。到期自动失效，不用任务清理（查询按 `reserved_until` 过滤）。
- 配置：

```yaml
patra:
  identity:
    username:
      change-cooldown: 30d       # 两次改名的最短间隔
      reservation-period: 90d    # 旧名保留多久
```

非正数启动失败。mini 上验收时不想等 30 天，可以在 `patra-identity.yaml` 临时改短。

## 5. 密码

- 规则：8 到 64 个码点，不去空格，不能在常见密码名单里（注册、改密码、重设密码同一条规则）。登录只要求非空、不含孤立代理项；超过 1024 个码点的密码直接按密码错误处理，不做哈希。与 NIST SP 800-63B-4「单因子至少 15 位」的有意偏离按 ASVS 7.1.1 记录在账号安全设计第 14 节。
- 哈希：Argon2id，`m=19456 KiB, t=2, p=1`，盐 16 字节、输出 32 字节；哈希和校验前都做 NFKC。开发机上单次约 20 毫秒（mini 上的耗时等 PAP-66 部署后实测）。
- 并发：同时进行的哈希计算最多 4 个，排队超过 3 秒返回 503。
- 常见密码名单：`infra/src/main/resources/password/common-passwords.txt.gz`，取自 Django 6.0 的 `django/contrib/auth/common-passwords.txt.gz`（Royce Williams 整理，19640 行），SHA-256 `3c1baed62596de36860824eb3f436d5932d37ca8b06e59df78f5a44ec175afe4`。许可证见同目录的 `common-passwords.LICENSE`（BSD 3-Clause）。比较时对密码做 NFKC、转小写、去掉首尾空白。

## 6. 登录失败限制

- 按「账号类型 + 邮箱」计数，没注册过的邮箱也一样。15 分钟内失败 5 次锁 15 分钟，成功一次清零。再认证、改密码时的当前密码校验共用这把锁：错计失败，锁定期内 429；重设密码成功后清零并解锁。找回密码的申请与提交不计入。
- 只有确认的失败才计数；正在校验的尝试单独登记，「失败数 + 在途数」到上限时返回 429（1 秒），不上锁。
- Redis 键：`idn:login-failures:user:{邮箱 SHA-256}`、`idn:login-inflight:…`、`idn:login-lock:…`，脚本在 `infra/src/main/resources/redis/`。
- 配置：

```yaml
patra:
  identity:
    login-throttle:
      max-failures: 5
      window: 15m
      lock-duration: 15m
      in-flight-ttl: 30s
    password-hashing:
      max-concurrent: 4
      wait-timeout: 3s
```

## 7. 错误码

| 场景 | 状态 | 错误码 |
|---|---|---|
| 字段不合法（`errors[]` 带字段名和原因码） | 422 | `IDN-0422` |
| 邮箱已注册、用户名已被使用（`errors[]`：`email` / `ALREADY_REGISTERED`、`username` / `ALREADY_TAKEN`，注册时可同时两条；含别人的旧名未到期、并发撞唯一约束；文案「邮箱或用户名已被使用」） | 409 | `IDN-0409` |
| 账号注销中不能做写操作（`traits` 为 `CONFLICT` + `PENDING_DELETION`，文案「账号正在注销中，取消注销后再操作」） | 409 | `IDN-0409` |
| 状态不允许的迁移（后台对 `DELETED` 用户封禁 / 解封，文案「账号当前状态不允许此操作」） | 409 | `IDN-0409` |
| 封禁、解封撞上乐观锁（文案「用户正被其他操作修改，请重试」） | 409 | `IDN-0409` |
| 邮箱或密码错误（含已注销账号） | 401 | `IDN-0401` |
| 需要登录：`/auth/me`、`/me/**` 没有当前用户、用户不存在、已封禁、已注销（文案 `Authentication required`） | 401 | `IDN-0401` |
| 登录被暂时限制（`Retry-After`、`retryAfterSeconds`） | 429 | `IDN-0429` |
| 重发验证邮件过于频繁（`traits` 为 `QUOTA_EXCEEDED`，`Retry-After`、`retryAfterSeconds` 是三层限流里最长的那个等待；文案「发送过于频繁，请稍后再试」） | 429 | `IDN-0429` |
| 邮箱已经验证过了（`traits` 为 `CONFLICT` + `EMAIL_ALREADY_VERIFIED`，文案「邮箱已经验证过了」） | 409 | `IDN-0409` |
| 验证令牌为空、格式不对、不存在、过期、已用或已作废（`errors[]`：`token` / `REQUIRED` 或 `INVALID`，文案「验证链接无效或已过期」；不区分原因） | 422 | `IDN-0422` |
| 改名冷却期内（`traits` 为 `CONFLICT` + `USERNAME_CHANGE_COOLDOWN`，不带 `Retry-After`，下次可改时间看 `/auth/me` 的 `nextUsernameChangeAt`；文案「用户名修改过于频繁，请稍后再试」） | 409 | `IDN-0409` |
| 再认证超时（`traits` 为 `FORBIDDEN` + `REAUTH_REQUIRED`，文案「请重新输入密码以继续」；门户弹密码框、`POST /auth/reauth` 后重试） | 403 | `IDN-0403` |
| 再认证、改密码时当前密码不正确（`traits` 为 `UNAUTHORIZED` + `PASSWORD_MISMATCH`，文案「密码不正确」；门户不把它当登出） | 401 | `IDN-0401` |
| 已有待确认的改邮箱（`traits` 为 `CONFLICT` + `EMAIL_CHANGE_PENDING`，文案「已有一个待确认的邮箱更改，请先取消或等它过期」） | 409 | `IDN-0409` |
| 在登录设备列表里踢本机（`traits` 为 `CONFLICT` + `CURRENT_SESSION`，文案「不能踢掉本机，请用退出登录」） | 409 | `IDN-0409` |
| 改邮箱的新地址已被使用；确认 / 撤销时地址被别人注册了（`errors[]`：`email` / `ALREADY_REGISTERED`） | 409 | `IDN-0409` |
| 找回密码的申请或提交过于频繁（`Retry-After`、`retryAfterSeconds`；文案「操作过于频繁，请稍后再试」） | 429 | `IDN-0429` |
| 改邮箱今天的配额用完（文案「今天更改邮箱的次数已用完，请明天再试」） | 429 | `IDN-0429` |
| 新密码与当前相同、新邮箱与当前相同（`errors[]`：`newPassword` / `UNCHANGED`、`newEmail` / `UNCHANGED`） | 422 | `IDN-0422` |
| 注销确认的用户名为空或与当前不一致（`errors[]`：`username` / `REQUIRED`、`MISMATCH`，后者文案「和你的用户名不一致」） | 422 | `IDN-0422` |
| 已在注销冷静期又发起注销（`traits` 为 `CONFLICT` + `PENDING_DELETION`） | 409 | `IDN-0409` |
| 账号已被封禁 | 403 | `IDN-0403` |
| 用户不存在：后台接口；公开身份卡不存在或对请求者不可见 | 404 | `IDN-0404` |
| 头像超过 5 MB（multipart 上限，没有 `errors[]`） | 413 | `IDN-0413` |
| 头像读取时用户名、尺寸、版本对不上，账号封禁或已注销，或对象不在 | 404 | `IDN-0404` |
| Redis 不可用（登录限流、会话存储）、哈希排队超时；头像对象写入失败（不改资料）、头像出图名额 10 秒内等不到 | 503 | `IDN-0503` |
| 保存资料时 catalog 不可用（连不上、超时、没有实例、回了 404 以外的错误；`traits` 为 `DEP_UNAVAILABLE`，文案「机构与关注领域暂时无法校验，请稍后再试」；不保存） | 503 | `IDN-0503` |

请求本身格式不对时由 Spring 直接处理，返回默认格式的 ProblemDetail，没有 `code`：请求体不是 JSON 对象（`[]`、`"x"`、空请求体、坏 JSON）或字段类型不对返回 400；`Content-Type` 不是 JSON 返回 415；方法不对（比如 GET）返回 405；路径里的 `userId` 不是数字或溢出返回 400，负数和 0 按用户不存在返回 404 `IDN-0404`；`/users/{username}` 路径里带 `%20`、`%25` 等百分号编码时被 Spring Security 的请求防火墙拒绝，返回 400。

原因码：邮箱 `REQUIRED`、`TOO_LONG`、`INVALID_FORMAT`、`ALREADY_REGISTERED`（409）；用户名 `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`INVALID_FORMAT`、`DIGITS_ONLY`、`RESERVED`、`UNCHANGED`（改名）、`ALREADY_TAKEN`（409）；昵称 `INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`INVALID_FORMAT`、`RESERVED`；资料与隐私见第 12 节；头像（字段 `file`）`REQUIRED`、`UNSUPPORTED_TYPE`、`TOO_SMALL`、`TOO_MANY_PIXELS`、`UNREADABLE`；密码 `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`TOO_COMMON`（改密码、重设时字段名是 `currentPassword` / `newPassword`，改邮箱时新邮箱字段名是 `newEmail`）；客户端类型 `INVALID_FORMAT`；设备标识 `INVALID_FORMAT`（不是 43 个 base64url 字符）。

## 8. 本地运行和测试

- 配置从 mini 的 Nacos 读（`patra-common.yaml`、`patra-identity.yaml`，源文件在 `patra-infra/nacos/dev/`）：连 mini 上的 `patra_identity` 库和 Redis（`PATRA_INFRA_HOST`），和 mini 上的容器共用同一个 Redis 0 号库。Redis 密码在 Nacos 里写成 `${REDIS_PASSWORD}`，本机由 Nacos starter 从 `~/.patra/secrets/redis.env` 读；生成和复制见 `patra-infra/docker/README.md`「密钥」。
- 库结构只有一份 `V1`（`infra/src/main/resources/db/migration/`）。库还是旧结构时启动会因 Flyway 校验失败起不来，推倒重建再启动；开发数据可丢弃，不做 `pg_dump`：

```bash
docker exec patra-postgres psql -U postgres -c 'DROP DATABASE IF EXISTS patra_identity WITH (FORCE);' -c 'CREATE DATABASE patra_identity;'
```

mini 上先 `docker stop patra-identity`，重建后 `docker start patra-identity`。

- 邮件：`spring.mail.host` 缺省跟随 `PATRA_INFRA_HOST`、端口 1025，即 mini core 栈的 Mailpit（UI `http://linqibins-mac-mini:8025`）；本机不用放 `mail.env`。验证链接的域名来自 `patra.identity.mail.links.base-url`（Nacos 里 `${PATRA_PORTAL_BASE_URL:http://linqibins-mac-mini:4000}`），本机起门户时设 `PATRA_PORTAL_BASE_URL=http://localhost:4000`，链接才指向本机门户。
- XXL-Job 执行器端口 6401（服务端口 + 1），回调地址读 `PATRA_ADVERTISE_IP`；本机不想注册到 mini 的调度中心时设 `XXL_JOB_ENABLED=false`。
- IP 属地的离线库：先跑一次 `bash patra-infra/scripts/fetch-ip2region.sh`，把两个数据文件下到 `~/.patra/data/ip2region/`（第 13 节）；缺文件 identity 启动失败。
- 手工验收再认证时不想等 10 分钟：临时把 `patra.identity.reauth.window` 设成 `30s`（环境变量 `PATRA_IDENTITY_REAUTH_WINDOW=30s`）。

- 测试全部用 Testcontainers（PostgreSQL 17、Redis 7.0.15、MinIO `pgsty/minio`），本机要有 Docker：

```bash
./gradlew spotlessApply && ./gradlew check && ./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest
```

`check` 不包含集成测试，按模块各自跑。

identity 从网关签的身份断言取当前用户，要配网关的公钥：写在 Nacos 公共配置 `patra-common.yaml` 里，和网关共用一份，换密钥时改这一处。集成测试不读这些：安全 starter 的测试支持会自动注入测试公钥。

容器和本机读同一份 Nacos 配置，只差 `PATRA_INFRA_HOST` 等本地变量；容器里的 `REDIS_PASSWORD` 来自 compose 加载的 `~/.patra/secrets/redis.env`。

## 9. 会话

登录、注册成功后签发不透明令牌，会话写进 Redis，登录记录写进 `idn_user_login_record`（ID 就是会话 ID）。
会话存储只有 identity 读写：网关每个带令牌的请求调 `POST /_internal/sessions/introspect` 查会话（PAP-106，
设计 `docs/patra/specs/2026-10-23-session-introspection-design.md`），不连 Redis。

策略只在这里配，网关不配：

```yaml
patra:
  identity:
    session:
      max-sessions-per-user: 10      # 超过挤掉最老的，记录标 REPLACED
      record-retention: 190d         # 登录记录结束或过期满这么久后删除（第 13 节）
      lifetime:
        user:
          web:
            idle: 30d                # 不活跃过期；每次查会话续期，60 秒内只读不写
            absolute: 180d           # 绝对过期
```

缺 `user.web` 这一行、键不认识、`idle` 长于 `absolute`，应用启动失败。

登出删会话并把记录标成 `LOGOUT`；其他撤销都经 `SessionTerminator`，结束原因按来由记（第 13 节的表）。
会话过期不回写记录：记录里 `ended_at` 为空而 Redis 里没有会话，就是过期了。

登录建会话前在事务里对用户行加锁重读、复核封禁（`UserRepository.findByIdForUpdate`），封禁与登录并发时
要么登录返回 403，要么封禁把这条新会话一起删掉；被封用户拿不到有效会话。

### 令牌

`patra_user_` + 43 个 base64url 字符（32 字节 `SecureRandom`），总长 54。前缀只说明账号类型，据它决定查哪个
键空间。`SessionToken.parse` 对格式不对的值返回空，查会话直接回 `active: false`、不碰 Redis。
Redis 里只存 `SessionToken.hash()`：整个令牌 UTF-8 的 SHA-256，小写十六进制。令牌不进日志，`toString()` 只有前缀。

### Redis 键

| 键 | 类型 | 内容 | TTL |
|---|---|---|---|
| `idn:session:user:<哈希>` | HASH | 一条会话 | `min(idle_timeout_ms, expires_at − now)`，续期时重算 |
| `idn:user-sessions:user:<userId>` | HASH | `session_id → <哈希>` | 不小于该用户最新会话的绝对有效期 |

会话字段（都是字符串）：`user_id`、`session_id`、`account_type`（`user`）、`client_type`（`web`）、
`device_id`（可选，没有就没这个字段）、`created_at`、`last_active_at`、`auth_at`（最近一次用密码证明身份：
登录、注册、再认证、改密码；必填，缺失按数据损坏报错）、`expires_at`（epoch 毫秒）、`idle_timeout_ms`。

### 操作（`RedisSessionStore`）

| 方法 | 脚本 | 做什么 |
|---|---|---|
| `create(NewSession)` | `session-create.lua` | 清掉索引里指向不存在会话的条目；到上限按会话 ID 从小到大挤掉多出来的；写会话和索引。返回令牌和被挤掉的 ID |
| `findAndTouch(SessionToken)` | `session-touch.lua` | 网关查会话：读会话；过了绝对过期返回空并删键；距上次写入满 60 秒（`RENEW_INTERVAL`）才写回 `last_active_at` 和 TTL |
| `delete(accountType, userId, sessionId)` | `session-delete.lua` | 按索引找到哈希删会话；真的删掉了返回 `true` |
| `deleteAll(accountType, userId)` | `session-delete-all.lua` | 删该用户全部会话和索引；返回真的删掉了的会话 ID |
| `rotate(accountType, userId, sessionId, grace)` | `session-rotate.lua` | 换令牌保留会话 ID（再认证、改密码）：其余字段不变，`auth_at` 与 `last_active_at` 刷新，旧令牌立即失效，索引指向新哈希；`auth_at` 距今不足 `grace` 时原子地判定不换、返回「没换」（并发再认证时先换出的令牌保持有效，`grace` 为零则每次都换）；会话不在或已过绝对期返回空 |
| `find(accountType, userId, sessionId)` | `session-find.lua` | 按会话 ID 读，不续期（再认证守卫读 `auth_at`）；不在或已过绝对期返回空 |
| `deleteAllExcept(accountType, userId, keepSessionId)` | `session-delete-others.lua` | 删该用户除指定一条外的全部会话（验证邮箱、改密码保留本机、踢其他设备）；指定的会话不存在时等于全删；返回真的删掉了的会话 ID |
| `list(accountType, userId)` | `session-list.lua` | 按用户列出还活着的会话（登录设备列表）：不续期、不改 `last_active_at`；顺手清掉悬空条目和已过绝对期的会话；顺序不保证 |

时间由 Java 按注入的 `Clock` 传进脚本，脚本不调 `TIME`。会话 ID 的大小按「字符串长度，再字典序」比，
雪花 ID 转成 Lua 的 double 会丢精度。脚本「没有结果」时返回空表而不是 `false`：Lettuce 把 `false`
解成 `[null]`，Java 侧拿到的不是空列表。

### 错误与限制

Redis 连不上、超时、`LOADING` / `READONLY` / `BUSY` / `MASTERDOWN`，以及连接层的 `RedisException`
（Redis 重启瞬间在途命令的「Connection closed」、命令被中断）转成 `SessionStoreUnavailableException`
（`DEP_UNAVAILABLE`，503）；网关据此对带令牌的请求回 503。判定在 `TransientRedisFailures`，登录限流也用它。
其他服务端错误回复（脚本写错、`WRONGTYPE`、`NOAUTH`）是缺陷或配置错，原样抛出。

只支持单机 Redis：会话键在脚本里由前缀拼出来，Redis Cluster 要求所有键事先声明。索引里过期会话留下的条目
在该用户下一次登录时清理，封禁时整键删除。

## 10. 邮箱验证与邮件

工程设计：`docs/patra/specs/2026-10-21-identity-email-verification-design.md`。

- **令牌**：32 字节 CSPRNG、base64url 原文 43 个字符，库里只存 SHA-256 十六进制；四种用途共用 `idn_email_token`（`EMAIL_VERIFY` 24 小时、`PASSWORD_RESET` 1 小时、`EMAIL_CHANGE_CONFIRM` 1 小时、`EMAIL_CHANGE_REVERT` 7 天）。签发前在同一事务里作废同用户同用途的旧令牌；消费是一条带条件的 `UPDATE … RETURNING`，并发只有一个赢。封禁按用户批量作废（`EMAIL_CHANGE_REVERT` 例外，可并存、不被批量作废）。令牌原文只出现在邮件里，不进响应、不进日志。
- **链接**：`<base-url><verify-email-path>#token=<原文>`，域名来自配置不来自请求的 Host 头；令牌在 URL 片段里，门户落地页读出后 POST `/auth/email/verify`，GET 预取不会把它用掉。
- **注册与重发**：注册成功、事务提交后异步发首封并计入重发配额；重发先判状态（已验证 409、注销中 409）再限流：冷却 60 秒、每账号每小时 5 次、每天 10 次、每 IP 每小时 30 次（Redis Lua 原子判定，`Retry-After` 取最长的等待）。配置在 `patra.identity.mail.resend.*`。
- **验证**：写 `email_verified_at`；带会话验证时保留当前会话、撤销其余（登录记录标 `EMAIL_VERIFIED`），匿名验证撤销全部会话。
- **签发**：四种用途共用 `EmailTokenIssuer.issue(purpose, userId, email, ip)`，返回已落库的聚合与原文；调用方用 `MailLinkPolicy` 拼链接、构造邮件；撤销令牌（`EMAIL_CHANGE_REVERT`）签发时不作废旧的。
- **邮件**：`EmailDeliveryPort.send(OutgoingMail)`，infra 的 `SmtpEmailDeliveryAdapter` 用 Spring Mail 发 multipart（文本 + HTML），虚拟线程异步、并发上限 4，失败只记日志（不含收件人与令牌）；带 `Auto-Submitted` / `X-Auto-Response-Suppress` 头。模板在 `infra/src/main/resources/mail/html/` 与 `mail/text/`，同名一对；新邮件 = 一个 `OutgoingMail` 实现 + 两份模板，公共抬头 / 页脚是片段，安全通知共用 `security-notice` 片段（`SecurityNotice`：时间、设备、IP 与属地、「不是我」该怎么办；PAP-99 之前设备与属地为「未知」；「建议到安全设置……」一句由各模板按场景自己写——改回邮箱后密码已作废的人登不进去）。
- **配置**：服务商坐标 `spring.mail.*`、发件人 `patra.identity.mail.from`、门户地址 `patra.identity.mail.links.base-url` 在 Nacos 的 `patra-identity.yaml`，缺省指向 Mailpit；mini 接真实服务商（Resend）靠 `~/.patra/secrets/mail.env` 覆盖（模板 `patra-infra/docker/secrets/mail.env.example`）。时区 `patra.identity.mail.zone`、保留期 `patra.identity.mail.token-retention` 在 jar 里。SMTP 连通性不计入健康检查（`management.health.mail.enabled: false`）。
- **清理**：XXL-Job 任务 `emailTokenCleanupJob`（执行器 `patra-identity`）删过期超过保留期（默认 30 天）的令牌，每批 1000 行、每批一个事务；mini 上每天 04:30 一次。

## 11. 账号安全：再认证、改密码、找回密码、改邮箱

工程设计：`docs/patra/specs/2026-10-22-identity-account-security-design.md`。

- **再认证**：会话 HASH 多一个 `auth_at`（登录、注册、再认证、改密码时写）。改邮箱、踢设备（PAP-99）、注销（PAP-100）要求 10 分钟内输过密码：`ReauthenticationGuard` 按断言里的会话 ID 读 `auth_at`，会话不在 401，超窗 403 `REAUTH_REQUIRED`。`POST /auth/reauth` 校验密码（共用登录失败限制）后换新令牌保留会话 ID（`RedisSessionStore.rotate`），旧令牌立即失效，门户要换 Cookie；`auth_at` 距今不足宽限期（10 秒）时不换、响应 `sessionToken` 为 `null`、门户保持现有 Cookie——两个标签页并发再认证时先换出的令牌保持有效。本 PR 前建立的会话没有 `auth_at`，视作从未再认证。配置 `patra.identity.reauth.window`（10m）、`patra.identity.reauth.rotation-grace`（10s）。
- **改密码**：每次都输当前密码（不走窗口）；新密码按注册规则、不能与当前相同（NFKC 后比较）；事务里锁行后再比一次凭据哈希是不是刚校验过的那个（并发改密码只有先到的生效，后到的 401）、换哈希、按用户批量作废令牌、撤销其余会话并记 `PASSWORD_CHANGED`、最后本机换令牌（刷新 `auth_at`；放最后一步，之前失败不碰旧令牌）；提交后发「密码已修改」。不存历史密码。
- **找回密码**：申请一律 202、异步发信，未注册与已封禁不发（未注册分支做同样的随机数与哈希计算）；限流在查用户之前计数（同邮箱冷却 60 秒、每天 5；同 IP 每小时 20）。提交先过 IP 每分钟 10 的限制，密码不合规时令牌不被消费；成功后换哈希（没有凭据行就新建）、批量作废令牌（含待确认的改邮箱）、写 `email_verified_at`、撤销全部会话、清零登录失败计数与锁，发「密码已重设」，不自动登录。令牌 1 小时。
- **改邮箱**：申请先过再认证；已有待确认 409 `EMAIL_CHANGE_PENDING`，新地址已被别人使用 409（和注册一致）；每账号每天 3 次。向新邮箱发 1 小时的确认链接；确认后立即生效（改 `email`、写验证时间、批量作废旧地址的令牌），旧邮箱收到含 7 天「不是我」链接的通知（新地址遮掩成 `c***@example.com`），新邮箱收到「已绑定」；确认不撤销会话。撤销：改回旧地址、作废全部令牌（撤销链接只作废比它更晚签发的，更早的保留——X → Y → Z 时攻击者先点 Y 的也废不掉原主 X 的）、删凭据（原密码作废）、撤销全部会话、向改回的地址发重设链接；注销冷静期内也能撤，撤销不碰注销状态。撤销令牌可并存，不被改密码 / 重设 / 再次改邮箱作废。
- **限流**：四个适配器共用 `action-throttle.lua`（一个可选冷却 + 任意个滑动窗口，时间由 Java 传入）。键：`idn:pw-reset:cooldown:email:{h}`、`idn:pw-reset:day:email:{h}`、`idn:pw-reset:hour:ip:{h}`、`idn:pw-reset-submit:minute:ip:{h}`、`idn:email-change:day:user:{userId}`，`h` 是 SHA-256 前 32 位，IP 取不到时是 `unknown`（所有未知来源共用一个桶，不放行）。受信转发（PAP-99）接通前 IP 是门户地址，两个 IP 维度等于全站配额。配置 `patra.identity.password-reset.*`、`patra.identity.email-change.per-account-daily`。
- **邮件**：七封——`password-reset`（重设链接）、`password-changed`、`password-reset-done`、`email-change-confirm`（确认链接）、`email-changed-notice`（旧邮箱，含撤销链接）、`email-changed-confirmation`（新邮箱）、`email-change-reverted`（改回的地址，含重设链接）。落地页路径 `patra.identity.mail.links.reset-password-path`（`/reset-password`）、`confirm-email-change-path`（`/confirm-email-change`）、`revert-email-change-path`（`/revert-email-change`），链接格式同验证邮件。
- **令牌、会话、密码的联动**：

| 操作 | 令牌 | 会话 | 密码 | 登录失败计数 |
|---|---|---|---|---|
| 再认证 | — | 本机换令牌 | — | 共用 |
| 改密码 | 批量作废 | 本机换令牌 + 其余撤销 | 换 | 共用 |
| 找回密码：申请 | 作废旧的重设令牌再签发 | — | — | 不计 |
| 找回密码：重设 | 批量作废 | 全部撤销 | 换或新建 | 清零 |
| 改邮箱：申请 / 取消 | 作废旧的确认令牌（再签发） | — | — | — |
| 改邮箱：确认 | 批量作废，再签发撤销令牌 | 不动 | — | — |
| 改邮箱：撤销 | 批量作废 + 更晚签发的撤销令牌 | 全部撤销 | 删凭据 | — |

## 12. 资料、隐私设置与公开主页

工程设计：`docs/patra/specs/2026-10-22-identity-profile-design.md`。

- **字段**（release spec 2.1.2，规则在 `ProfilePolicy`，错误一次报全）：简介 ≤ 200 字素 / 800 字节 / 3 行；链接 ≤ 5 条，标题可空、≤ 30 字素，URL ≤ 300 码点、只收 `http` / `https`、有主机名、不带用户信息、不重复；身份大类 `PHYSICIAN` / `NURSE` / `PHARMACIST` / `TECHNICIAN` / `RESEARCHER` / `STUDENT` / `INDUSTRY` / `OTHER`；职称 ≤ 50；科室 ≤ 100；机构 `{ rorId }`（裸 9 位，大写折成小写）或 `{ name }`（手填 ≤ 200）；关注领域 ≤ 10 个 MeSH UI。字节上限都是字素上限 × 4，与昵称同比例；拒绝的字符同昵称（简介多放行换行）。和库里存的值规范化后相同的文本不重新校验，规则收紧不连累存量。
- **原因码**：`REQUIRED`、`INVALID_CHARACTER`、`INVALID_FORMAT`、`TOO_LONG`、`TOO_MANY`（链接、关注领域）、`TOO_MANY_LINES`（简介）、`DUPLICATE`（链接 URL、关注领域）、`NOT_FOUND`（catalog 里没有）；字段路径 `bio`、`links[1].url`、`links[1].title`、`occupation`、`jobTitle`、`organization.rorId`、`organization.name`、`department`、`researchInterests[3]`，隐私是 `profileVisibility`、`professionalVisibility`、`searchIndexing`。
- **名称快照**（决策 F）：带 ROR ID 时机构名来自 catalog（`zhName` 优先，没有用 `displayName`），请求里的名称被忽略；关注领域存 MeSH 英文名。只对新选的机构、新加的主题词调 catalog，已存的沿用库里的快照（机构在 catalog 里停用了也不挡只改简介的保存）。catalog 回 404 + `NOT_FOUND` → 422；其余失败 → 503，不保存。调用在事务外，之后加用户行锁复核状态再写库；快照不回刷。
- **catalog 客户端**：`OrganizationEndpoint`、`MeshEndpoint` 经 `lb://patra-catalog` 直连 catalog 的 `/_internal/**`（不经网关），分组 `patra.http-client.http-interface.groups.catalog`，连接 1s / 读 2s。
- **隐私**：主页可见性 `PUBLIC` / `SIGNED_IN` / `SELF`、专业背景可见性 `PUBLIC` / `SELF`、收录开关，默认公开 / 公开 / 关；邮箱永不展示。
- **公开主页**（`PublicUserReadModel.viewFor`）：

| 情况 | 匿名 | 已登录的别人 | 本人 |
|---|---|---|---|
| 正常、已验证、主页公开 | 200 `PUBLIC` | 200 `PUBLIC` | 200 `PUBLIC` |
| 主页仅登录用户 | 404 | 200 `SIGNED_IN` | 200 `SIGNED_IN` |
| 主页仅自己 | 404 | 404 | 200 `SELF` |
| 邮箱未验证、注销冷静期 | 404 | 404 | 200 `SELF` |
| 已封禁 | 404 | 404 | — |
| 已注销 | 404 | 404 | 404 |

「已登录」是任何带有效会话的人（含邮箱未验证、冷静期中的账号）。专业背景在本人或其可见性为公开时给出，否则 `professional` 为 `null`（四项都没填也是 `null`）；链接只在邮箱已验证时给出（本人也一样）；`indexable` 只在谁都能看且用户允许收录时为真，门户据此输出 `noindex`；`joinedAt` 是 `YYYY-MM`（时区同 `patra.identity.mail.zone`）。响应不含邮箱、状态、任何设置原值；带 `userId`（PAP-108 起，门户凭它取该用户的公开收藏夹）。旧名跳转的可见性看原主的当前主页。规则只在 `ProfileAccess` 里有一份，批量用户摘要的 `profileVisible` 用同一份（第 16 节）；已注销对本人也不可见（会话已撤销，线上触达不到，为两处一致而定）。
- **昵称修改**只打 INFO 日志（`昵称已修改`）；账号审计记录以后单独做成通用的账号事件表。
- **注销（第 14 节）**：到期执行时资料行置空不删行、隐私回默认，两张子表按 `user_id` 删行。

## 13. 登录设备、新设备提醒与登录记录清理

设计：`docs/patra/specs/2026-10-22-identity-login-devices-design.md`（PAP-99）。

**受信转发链路**：门户（BFF）代表浏览器请求网关时带 `X-Forwarded-For`（一个值：浏览器 IP）和 `User-Agent`（浏览器原值）；网关只对 `server.tomcat.remoteip.internal-proxies`（环境变量 `PATRA_GATEWAY_TRUSTED_PROXIES`，mini 的 compose 里是门户容器的固定地址 `192.168.97.250`）发来的 `X-Forwarded-For` 采用（Tomcat `RemoteIpValve`），其余来源带的一律剥掉，出站按认定的客户端地址重写 `X-Forwarded-For` / `Forwarded`；identity 用 `forward-headers-strategy: framework` 读，`getRemoteAddr()` 就是浏览器 IP。见网关 README「受信代理与入站转发头」。

**请求来源**：`RequestOrigin` 只收合法 IP 字面量（受信门户可能转来浏览器谎报的值、tailnet 上直连 6400 时也可伪造），UA 去控制字符后截到 512；`ClientOriginResolver` 用 Yauaa 得设备名（「浏览器主版本 · 系统族」，如 `Chrome 141 · macOS`）与端（`DESKTOP` / `MOBILE` / `TABLET` / `UNKNOWN`），公网地址用 ip2region 查属地（中国到国家 / 省 / 市并去「省」「市」后缀，境外只到国家并转中文名，如 `中国 广东 深圳`、`美国`），回环、私网段、CGNAT（`100.64.0.0/10`，tailnet 用它）、IPv6 ULA 等内网地址不查属地。登录、注册把这些写进 `idn_user_login_record`（`user_agent`、`device_name`、`device_class`、`client_ip`、`ip_region`）；改密码、重设密码、确认 / 撤销改邮箱与新设备提醒的安全通知也用它，内网的属地写「内网」。

**会话列表与踢设备**：`GET /me/sessions` = Redis 里活着的会话（登录时间、最后活跃；网关续期时距上次写入满 60 秒才写）⋈ 登录记录（设备与属地）；本机第一，其余按最后活跃倒序；记录缺失照样列出、设备字段为空。`DELETE /me/sessions/{id}`、`DELETE /me/sessions/others` 先判「不是本机」（409 `CURRENT_SESSION`），再过再认证守卫（403 `REAUTH_REQUIRED`），撤销会话并把记录结束为 `REVOKED`，再清掉被踢设备的新设备提醒冷却；踢设备不锁用户行、不判账号状态、不发邮件。

**结束原因**：

| 取值 | 谁写 |
|---|---|
| `LOGOUT` | 登出 |
| `BANNED` | 后台封禁 |
| `REPLACED` | 超过会话上限被挤掉 |
| `REVOKED` | 本人在设备列表里踢掉 |
| `EMAIL_VERIFIED` | 验证邮箱撤销其他会话 |
| `PASSWORD_CHANGED` | 改密码撤销其他会话 |
| `PASSWORD_RESET` | 重设密码撤销全部会话 |
| `EMAIL_REVERTED` | 撤销改邮箱撤销全部会话 |
| `DELETION_REQUESTED` | 发起注销撤销全部会话 |
| `DELETION_COMPLETED` | 注销到期执行撤销冷静期内登录的会话 |

**新设备提醒**：只在登录时判（注册不判）。事务里、建会话之前查这个账号有没有「见过」这台设备：同设备标识、`COALESCE(ended_at, expires_at)` 在 180 天内、结束原因不是 `REVOKED`，而且不早于这台设备任何一条 `REVOKED` 记录（按设备算：踢掉一台设备后，它之前的登出、过期记录也不再算见过，只认踢掉之后的登录）；按结束或过期的时刻算，常用设备在会话绝对过期（180 天）后被迫重新登录不会误报。没见过（含没带设备标识）就在提交后取 24 小时限流（键 `idn:new-device-mail:user:<userId>:<设备标识哈希 | none>`），放行才发「你的 Patra 账号在新设备上登录」；限流的 Redis 不可用只记日志，登录照常成功。踢设备时清掉被踢设备的冷却键，它再登录立即提醒，不等 24 小时。注册时已记下设备，同一个浏览器注册后再登录不发；换设备首次登录照发。

```yaml
patra:
  identity:
    new-device-mail:
      lookback: 180d     # 这么久内用过的设备标识不算新设备
      cooldown: 24h      # 同一账号同一设备两封提醒的最短间隔
    ip-geolocation:
      directory: ${user.home}/.patra/data/ip2region   # 容器里由 compose 设成 /data/ip2region
```

**登录记录清理**：XXL-Job `loginRecordCleanupJob`（`LoginRecordCleanupScheduleJob`）删掉 `COALESCE(ended_at, expires_at)` 早于「现在 − `record-retention`（190 天）」的记录，每批 1000 行一个事务；还活着的会话的绝对过期在未来，不会被删。mini 的 xxl-job-admin 里建任务：执行器 `patra-identity`，handler `loginRecordCleanupJob`，Cron `0 40 4 * * ?`（令牌清理 04:30 之后）。

**依赖与数据文件**：

| 项 | Yauaa | ip2region |
|---|---|---|
| 坐标 | `nl.basjes.parse.useragent:yauaa:8.2.0` | `org.lionsoul:ip2region:3.3.7`，数据取仓库 tag `v3.18.0` |
| 许可 | Apache-2.0 | 代码 Apache-2.0 OR MIT；数据文件没有单独的许可声明，所以不在本仓库与镜像里再分发 |
| 体积与内存 | jar 2.8 MB + caffeine；分析器常驻约 108 MB 堆（本机实测），整个 JVM 只建一个（静态持有类，启动时初始化约 1 秒） | jar 36 KB；数据 `ip2region_v4.xdb` 10.6 MB、`ip2region_v6.xdb` 35.6 MB，`VIndexCache` 模式每库常驻 512 KB，查询读文件 |
| 数据在哪 | 规则在 jar 里 | 宿主机 `~/.patra/data/ip2region/`，只读挂进容器 `/data/ip2region`；缺文件或不兼容启动失败 |
| 更新 | 改 `gradle/libs.versions.toml` 的版本，跑 `YauaaUserAgentParsingAdapterTest` | 按季度看一次上游：改 `patra-infra/scripts/ip2region.properties` 的 tag 与两个 SHA-256 → 跑 `Ip2RegionGeolocationAdapterTest`（`downloadIp2RegionData` 任务按同一文件下载测试数据）→ 提交 → mini 上 `git pull` 后重跑 `fetch-ip2region.sh`、重启 identity |

测试：identity-infra 的 `downloadIp2RegionData` 下到 Gradle 用户目录的 `caches/patra/ip2region/<tag>/`（校验 SHA-256；文件已在且校验和对得上直接跳过，`clean` 不用重下；连接 30 秒、读取 60 秒超时），经 consumable configuration `ip2regionData` 提供给 identity-boot；两个模块的测试任务用系统属性 `patra.identity.ip-geolocation.directory` 指过去，目录登记为测试输入。

## 14. 注销账号

设计：`docs/patra/specs/2026-10-22-identity-account-deletion-design.md`（PAP-100）。

- **发起** `POST /me/deletion`：字段 → `requireActive()`（冷静期再发起 409 `PENDING_DELETION`）→ 用户名比对（去首尾空白、转小写，不一致 422 `MISMATCH`；放在再认证之前，门户本来先比一遍）→ 再认证（403 `REAUTH_REQUIRED`）→ 事务里锁行、转 `PENDING_DELETION`（到期 = 现在 + 冷静期）、`revokeAllActive` 作废验证 / 重设 / 改邮箱确认令牌、撤销全部会话（含本机，记 `DELETION_REQUESTED`）→ 提交后发「注销已提交」（到期时刻、怎么取消，带安全通知片段）。**改邮箱的撤销令牌不作废**：冷静期内原主仍能用「不是我」把邮箱抢回来，再用重设链接登录后取消注销。邮箱未验证也能注销。Redis 失败整个回滚、503。
- **冷静期**：照常能登录（响应与 `/auth/me` 的 `status` 为 `PENDING_DELETION`、带 `deletionDueAt`）。放行：取消注销、登出、再认证、踢设备、找回密码与重设、撤销改邮箱（不碰注销状态）；其余写接口 409 `PENDING_DELETION`（已有的 `requireActive()`），读接口不拦；公开主页对别人 404、对本人 `SELF`。
- **取消** `DELETE /me/deletion`：锁行，冷静期回到 `ACTIVE`（清到期时间与提醒标记）；本来正常时什么都不改（幂等）；封禁、已注销 401。不要求再认证，不动会话；只有真的取消了才发「已取消注销」。到期之后、任务执行之前也能取消。
- **到期执行**（XXL-Job `accountDeletionJob`）：按 ID 游标每批 100 个扫 `status = 'PENDING_DELETION' AND deletion_due_at <= now`；每个用户一个事务，锁内复核仍到期才做：`completeDeletion`（邮箱、验证时间、到期时间、提醒标记、封禁时间置空，记 `deleted_at`，ID 与用户名留着、用户名永不释放）、删密码凭据、`UserProfile.erase()`（资料行保留、内容清空、隐私回默认，链接与关注领域子表删行）、令牌按用户删行（行里有邮箱和 IP）、撤销冷静期内登录的会话（`DELETION_COMPLETED`）；提交后给注销前的邮箱发「已注销」。单个用户失败只记日志、计数，留在冷静期等下次；有失败时任务回写失败。登录记录不删，按第 13 节的 190 天清理；旧用户名行不动，保留期到了自然失效。同一邮箱立刻可以注册与旧账号无关的新账号；旧用户名注册、改名都 409 `ALREADY_TAKEN`。
- **到期前提醒**（XXL-Job `accountDeletionReminderJob`）：扫冷静期、`deletion_reminded_at` 为空、`now < 到期 <= now + 提前量` 的用户，锁内复核后先记 `deletion_reminded_at` 再发「将在 N 天后删除」，只发一次；发信被执行器拒收时把标记清回去、下次重发。取消、封禁、完成注销都清掉标记。
- **头像**：`erase()` 同时清头像键；到期执行在加锁事务之前按前缀 `avatar/{userId}/` 删对象，删之前不加锁复读一次、仍到期才删（扫描之后刚取消的用户不丢头像）；删失败按该用户失败、下次重试（第 15 节）。
- **邮件**：`account-deletion-requested`、`account-deletion-reminder`、`account-deletion-cancelled`、`account-deletion-completed`；都不放链接（怎么取消写成文字）；「将在 N 天后删除」与「已注销」两封不是请求触发的，不带安全通知片段。到期时刻按 `patra.identity.mail.zone` 显示（渲染器把模型里的 `Instant` 统一格式化）。
- **配置**（jar 里的默认值，Nacos 不加键；非正数或提前量不短于冷静期启动失败）：

```yaml
patra:
  identity:
    deletion:
      cooling-period: 15d   # 冷静期
      reminder-lead: 3d     # 到期前多久提醒
```

- **mini 的 xxl-job-admin**：执行器 `patra-identity`，handler `accountDeletionJob` Cron `0 5 * * * ?`、`accountDeletionReminderJob` Cron `0 15 * * * ?`（都每小时一次：取消注销页给的是精确到分钟的到期时刻）。验收不想等 15 天：把库里的 `deletion_due_at` 改到过去，在调度中心手动触发。

## 15. 头像

设计：`docs/patra/specs/2026-10-22-identity-avatar-design.md`（PAP-97）。

- **上传** `POST /me/avatar`（multipart，part 名 `file`）：预检 `requireActive()`（冷静期 409）→ 出图 → 写新对象 → 事务里锁行、复核状态、换资料里的键 → 提交后删旧对象。出图与写对象都在事务外；提交失败删掉新对象；删旧对象失败只记日志，旧对象留在本用户前缀下，注销时一并删。同一张图重复上传哈希相同，直接返回原地址，不写对象、不动库。
- **出图**：按魔数识别（扩展名与 `Content-Type` 不看），只收 JPEG / PNG / WebP，GIF、APNG、动画 WebP 拒收；读头校验短边 ≥ 128、宽 × 高 ≤ 4096²（不解码就拒；WebP 与渐进式 JPEG 解码时整张图进内存，下采样封不住）；出图最多 2 路并发，等 10 秒拿不到名额回 503；居中取方，按区域加整数倍下采样解码（解出边长 1024–2047，内存封顶）；解码有任何告警（截断、数据段损坏）算读不出；灰度图按 sRGB 值复制成 r = g = b（解码器标成线性灰度，走色彩转换会发白），其他非 sRGB 先转 sRGB；逐次减半再双三次到 512，128 从 512 出，小于 512 的放大到 512；WebP 有损 q=0.82，有透明保留透明。编码器只拿到像素，输出没有 EXIF / XMP / ICC。原图 5 MB 由 `spring.servlet.multipart.max-file-size` 守，超过 413；Tomcat `max-swallow-size: 32MB`（网关同样设置，会话过期的上传才拿得到 401）让客户端拿到 413 而不是连接被重置；更大的只靠门户本地校验。
- **库**：解码用 TwelveMonkeys `imageio-jpeg` / `imageio-webp` 3.15.2 与 JDK 的 PNG（WebP、PNG 纯 Java；JPEG 的 DCT 由 TwelveMonkeys 委托给 JDK 自带的原生 libjpeg）；编码用 `com.github.usefulness:webp-imageio` 0.10.0（Apache-2.0，JNI 包装 libwebp，BSD-3），jar 自带 Linux x86_64 / aarch64、macOS x86_64 / aarch64 的原生库。没有可用的纯 Java WebP 编码器（2026-10-22 定）。读取器从 ImageIO 注册表按类名前缀挑（TwelveMonkeys 的 JPEG 读取器要经注册拿到 JDK 的委托，注册表里还有一个不能用来解码上传的原生 WebP 读取器），构造时 `ImageIO.scanForPlugins()`。
- **原生访问**：`webp-imageio` 用 `System.load`，JDK 25 对未命名模块只告警、以后默认拦截。identity 的可执行 jar 清单带 `Enable-Native-Access: ALL-UNNAMED`（`patra-identity-boot/build.gradle.kts`），镜像分层解包后仍在，`java -jar` 启动生效。
- **存储**：桶 `patra-identity-avatar`（`patra.identity.avatar.bucket`，私有，不开匿名访问、不用预签名）；键 `avatar/{userId}/{hash}-512.webp` 与 `-128.webp`，`hash` 是 512 档字节 SHA-256 的前 16 位小写十六进制。资料表 `idn_user_profile.avatar_object_key` 只存 512 档的键（约束前缀里的用户 ID 等于本行），128 档同哈希换后缀。桶由 `patra-storage` 栈的 `minio-init` 建，对象存储库第一次上传时也会按需建。
- **读取** `GET /users/{userId}/avatar?size=512|128&v={hash}`：按用户 ID（PAP-108 起；用户名不能是纯数字，与 `/users/{username}` 不混淆），改用户名后地址照常可用；用户 ID 只认规范写法 `[1-9][0-9]{0,18}`（溢出也 404），同一张头像只有一个地址；`UserStatus.servesAvatar()` 为真才下发（`ACTIVE` 与 `PENDING_DELETION`，冷静期本人还要显示头像），`BANNED` / `DELETED` 404；`v` 必须等于当前哈希，所以同一地址内容永远不变，`Cache-Control: public, max-age=31536000, immutable`、`ETag: "{hash}-{size}"`，`If-None-Match` 命中直接 304 不碰 MinIO。不看主页可见性：响应是公开缓存，`v` 只出现在请求者有权看到的响应里，猜不到（2026-10-22 定）。返回 `InputStreamResource`（不用 `StreamingResponseBody`）。
- **`avatarUrl`**：identity 相对路径 `/users/{userId}/avatar?v={hash}`（`AvatarUrls.of(avatar)`），在 `/auth/me`（及登录、注册、再认证、改密码、取消注销响应里的 `user`）、`GET /users/{username}`、`POST /me/avatar`、批量用户摘要里；门户换成自己的同源地址并追加 `size`（36 / 40 px 用 128，72 px 以上用 512）。`GET /me/profile` 不带：头像不随资料表单保存。
- **`avatarPalette`**：`AvatarPalette.of(userId)`，SplitMix64 最终混合后对 12 取余，0–11 对应门户 12 色板。**上线后不能改**：一改所有人的默认头像都变色；`AvatarPaletteTest` 钉了固定向量。
- **与注销**：见第 14 节「头像」。

## 16. 批量用户摘要（内部接口）

设计：`docs/patra/specs/2026-10-24-identity-external-contract-design.md`（PAP-108）。契约在 `patra-identity-api`：`UserEndpoint`、`UserSummariesReq` / `UserSummariesResp` / `UserSummaryResp`、`AccountStatus`。

- **用途**：interaction 拼收藏夹所有者的信息，并判断公开收藏夹对某位访客是否可见；注销清理任务按 `accountStatus` 找已注销的用户。调用方只透传访客 ID，主页隐私规则只在 identity 里（`ProfileAccess`）。
- **请求** `{ userIds, viewerUserId }`：`userIds` 最多 100 个（按去重前长度算），`null` / 含 `null` / 超限 422（字段 `userIds`，原因 `REQUIRED` / `INVALID_FORMAT` / `TOO_MANY`）；空列表回空结果；重复的保留第一次出现。`viewerUserId` 匿名或后台任务为 `null`，取自请求体（identity 信任内部调用方，不看转发的断言）。
- **响应**：按去重后的请求顺序；identity 不认识的 ID 省略（记 `WARN`），不等于已注销。已注销是占位：`username`、`displayName`、`avatarUrl` 为 `null`，`emailVerified`、`profileVisible` 为假，文案由门户出；已封禁不给 `avatarUrl`（头像路由同样 404）。JSON 里 `userId` 是字符串，HTTP Interface 客户端反序列化回 `Long`。
- **读法**：`UserReadPort.findSummaries` 用户表、资料表各一条 `IN` 查询，不读子表；`UserSummaryQueryService` 校验、去重、按请求顺序重排；`UserSummaryReadModel.viewFor` 出占位、昵称回退与 `profileVisible`。不经 CommandBus，没有事务。
- **调用方的义务**：`profileVisible` 为假的人，不要把其 `avatarUrl`、`username` 放进给这位访客的响应（头像地址里的版本号是「只出现在有权看到的响应里」的凭证，第 15 节）。

## 17. 提及支撑（内部接口）

设计：`docs/patra/specs/2026-10-27-identity-mention-support-design.md`（PAP-118）。契约在 `patra-identity-api`：`UserEndpoint` 的 `suggest` 与 `resolveByUsernames`，DTO `UserSuggestReq` / `UserSuggestionsResp`、`UserResolveByUsernamesReq` / `UserResolveResp` / `UserResolutionResp`。

- **用途**：interaction 的讨论区要 @ 同行。门户只发 `@用户名`，interaction 写评论时调解析把它换成 `<@用户ID>`；@ 联想由 interaction 对外提供，先列本讨论参与者，再并上这里的全站前缀结果。网关对 `/_internal/**` 一律 403，门户调不到这两个接口。
- **联想请求** `{ prefix, viewerUserId, limit }`：`prefix` 缺失 422（`REQUIRED`）；`limit` 必填、1 ~ 20（缺失 `REQUIRED`、越界 `INVALID_FORMAT`）。同一个 `prefix` 同时当用户名前缀（按用户名规则规范化；长度 1 ~ 30，首字符字母或数字、`_` `-` 不连续，末尾可以有一个连接符）和昵称前缀（按昵称规则规范化，非空、≤ 120 字节、不含昵称里不可能出现的字符，再转小写）用；两种都不成立时 200 空结果、不查库。`viewerUserId` 匿名为 `null`，取自请求体。
- **联想结果**：两路候选，每路最多扫 200 个：用户名以前缀开头的 `ACTIVE` 账号（按用户名字节序），已设昵称且昵称以前缀开头（不分大小写）的 `ACTIVE` 账号（按小写昵称码点序，同名按用户名）。用户名一路在前、昵称一路在后，按用户 ID 去重；去掉访客本人和主页对访客不可见的（规则只用 `ProfileAccess`），截到 `limit`，用户名一路已够时不查昵称一路。每项与批量用户摘要同形，`accountStatus` 恒为 `ACTIVE`，`emailVerified`、`profileVisible` 恒为真。没设昵称的人昵称跟随用户名，由用户名一路覆盖；只匹配开头。
- **解析请求** `{ usernames }`：`@` 后面的原文，最多 100 个（按去重前长度算），`null` 列表 422（`REQUIRED`）、超限 422（`TOO_MANY`）；`null` 元素和不合用户名语法的元素跳过，全部被跳过时不查库。
- **解析结果**：先按当前用户名查，查不到再查此刻仍在 90 天保留期内的旧名（多条取改名时间最近的），给原主；四种状态原样带上，是不是正常账号由调用方判；不看主页可见性。按请求顺序，`username` 回显请求原文：原文相同的只出一行，原文不同而规范化后相同的各出一行。不存在、旧名已过期的省略。
- **读法与索引**：两路都写范围谓词 `列 COLLATE "C" >= 前缀 AND < 前缀末码点加一`，不用 `LIKE`（`_` 是通配符，参数化的 `LIKE` 在通用计划下推不出索引范围）；库的排序规则是 `en_US.utf8`，按它的索引做不了前缀范围。用户名一路走 `UserDao.findActiveByUsernameRange` 与 `V2` 的部分索引 `idx_idn_user_username_prefix`（`username COLLATE "C"`，`WHERE status = 'ACTIVE'`）；昵称一路走 `UserDao.findActiveByDisplayNameRange`，联用户表，对应 `idx_idn_user_profile_display_name_prefix`（`(lower(display_name)) COLLATE "C"`，`WHERE display_name IS NOT NULL`）——状态在用户表，资料表上的索引做不到只收 `ACTIVE`，由联表过滤。解析是用户表、资料表、旧名表的 `IN` 查询。两个服务都不经 CommandBus，没有事务。
- **调用方的义务**：只把 `accountStatus == ACTIVE` 的解析结果当成提及；「一条最多 @ 10 人」按解析出的不同 `userId` 计。联想结果已排除访客本人，参与者列表最好同样排除。渲染 `<@userId>` 时取名片走批量摘要，`profileVisible` 为假的人不要放出用户名与头像地址（第 16 节）。
