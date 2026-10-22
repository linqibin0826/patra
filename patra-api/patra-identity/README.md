# patra-identity

前台用户的账号服务：注册、登录、会话、登出、「当前用户」、用户名、资料与隐私设置、公开主页、邮箱验证与邮件、再认证、改密码、找回密码、改邮箱、登录失败限制、封禁与解封。

工程设计：`docs/patra/specs/2026-10-05-identity-account-design.md`（账号）、`docs/patra/specs/2026-10-09-identity-session-design.md`（会话）、`docs/patra/specs/2026-10-20-identity-account-rebuild-design.md`（账号重建：用户名、昵称、邮箱验证状态、四态状态机、`V1` 最终形态）、`docs/patra/specs/2026-10-21-identity-email-verification-design.md`（邮件发送与邮箱验证）、`docs/patra/specs/2026-10-22-identity-account-security-design.md`（账号安全：再认证、改密码、找回密码、改邮箱）、`docs/patra/specs/2026-10-22-identity-profile-design.md`（资料、隐私设置与公开主页）。

## 1. 模块

| 模块 | 内容 |
|---|---|
| `patra-identity-domain` | `User`、`UserProfile`、`FormerUsername`、`UserPasswordCredential`、`UserLoginRecord`、`EmailToken` 六个聚合，邮箱、用户名、昵称、密码、客户端信息、令牌密文值对象，资料值对象（`vo/profile/`：简介、链接、职称、科室、ROR ID、MeSH UI、机构、关注领域、隐私设置），用户名 / 昵称 / 资料字段（`ProfilePolicy`，拒绝字符与昵称共用 `ProfileTextRules`）/ 改名 / 密码 / 失败限制 / 重发限流 / 再认证 / 找回密码限流 / 改邮箱限流 / 邮件链接规则、会话策略，端口（含邮件发送 `EmailDeliveryPort`、三个限流端口、向 catalog 取名称快照的 `CatalogReferencePort`），公开主页读模型（`viewFor` 按请求者裁剪），`SessionIssuer`、`EmailTokenIssuer`、`ReauthenticationGuard` 领域服务，七个 `OutgoingMail` 记录，领域异常 |
| `patra-identity-app` | 注册、登录、登出、再认证、改用户名、改密码、找回密码（申请、重设）、改邮箱（申请、取消、确认、撤销）、封禁、解封、重发验证邮件、验证邮箱、清理过期令牌、改资料、改隐私设置十九个处理器，走 CommandBus；`CurrentPasswordVerifier` 给再认证与改密码共用；「当前用户」、本人的资料与隐私设置、待确认的改邮箱和公开主页走 `UserQueryService` |
| `patra-identity-infra` | JPA 持久化（含令牌表）、Argon2id 哈希、常见密码名单、Redis 失败限制与通用限流脚本 `action-throttle.lua`（重发、找回密码、改邮箱三个适配器共用）、会话存储适配器、SMTP 发信适配器与 Thymeleaf 邮件模板（八对）、Flyway 脚本（一份最终形态的 `V1`）、catalog 内部接口适配器 `CatalogReferenceAdapter`（依赖 `patra-catalog-api`） |
| `patra-identity-adapter` | 前台的 `AuthController`、`EmailVerificationController`、`PasswordResetController`、`EmailChangeController`、`MeController`、`MeEmailController`、`MeProfileController`、`PublicUserController`，后台的 `AdminUserController`，XXL-Job 任务 `EmailTokenCleanupScheduleJob` |
| `patra-identity-boot` | 启动类、配置、安全 starter 接入、catalog 两个 HTTP Interface 代理（`CatalogClientConfiguration`，`lb://patra-catalog`） |
| `patra-identity-session` | 与网关共用的会话存储契约（令牌、Redis 键、Lua、`RedisSessionStore`），见它自己的 README |

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
| `GET /users/{username}` | 200 公开主页 `{ username, displayName, avatarUrl, audience, joinedAt, bio, links, professional, researchInterests, indexable }`，按请求者裁剪（第 12 节）；保留期内的旧名 301，`Location: /users/<新名>`，体 `{ username: <新名> }` | 公开（`/users/**` 整体，网关照样翻译令牌） |
| `POST /admin/users/{userId}/ban` | 204 | 拒绝外部访问 |
| `POST /admin/users/{userId}/unban` | 204 | 拒绝外部访问 |

注册请求体 `email`、`username`、`password`，登录请求体 `email`、`password`，两者都可带 `clientType`（不传按 `web`）和 `deviceId`（最长 128 个字符）。

登录、注册响应里的 `user` 与 `/auth/me` 同构：

```json
{
  "userId": "352303128713027974", "email": "chen.yu@example.com", "emailVerified": false,
  "username": "chen-yu", "displayName": "chen-yu", "avatarUrl": null,
  "status": "ACTIVE", "deletionDueAt": null, "nextUsernameChangeAt": null, "accountType": "user"
}
```

- `displayName` 永远非空：资料表里没设昵称就回退成当前用户名。`nextUsernameChangeAt` 是最近一次改名时间加冷却，没改过名为 `null`。`avatarUrl` 本版恒为 `null`（PAP-97 填）。
- `status` 只会出现 `ACTIVE`、`PENDING_DELETION`。登录在密码正确之后分流：`ACTIVE`、`PENDING_DELETION` 都建会话（后者 `deletionDueAt` 有值，门户据此进取消注销页）；`BANNED` 403；`DELETED` 与密码错误完全一样，按失败结算、401。`/auth/me` 对已封禁、已注销用户的会话返回 401。
- 公开身份卡：路径里的用户名先去空白、转小写，语法不合法直接 404。可见的条件是 `audience` 为 `PUBLIC`（`ACTIVE` 且邮箱已验证）或请求者就是本人（`SELF`，只要还能登录）；不可见一律 404，不区分原因。门户请求时带上会话令牌，本人才能看到自己。301 的 `Location` 是 identity 自己的相对路径，门户按体里的 `username` 做临时跳转（307，永久跳转会被浏览器缓存、原主改回旧名后成环），`fetch` 要用 `redirect: "manual"`；301 带 `Cache-Control: no-store`。
- `/admin/**` 本版没有身份校验，只能在内网直连调用；对 `DELETED` 用户封禁、解封都返回 409。

## 3. 账号模型

账号分前台用户（`USER`）和后台账号（`STAFF`，以后建）两类，分开建表；密码和用户也分开，各一张表。理由和调研见账号工程设计第 4 节。

| 表 | 内容 |
|---|---|
| `idn_user` | ID、邮箱（唯一、只能小写，`DELETED` 时为空）、`email_verified_at`、用户名（唯一）、最近改名时间、状态、封禁时间、注销到期时间、注销时间。用户名格式、状态与各时间列的配对都由检查约束守在表上 |
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

表里的 409 是 `InvalidUserStateTransitionException`（「账号当前状态不允许此操作」）。进入 `DELETED` 时邮箱、邮箱验证时间、注销到期时间、封禁时间全部清空，用户名和 ID 留着，不能回退。`/me/**` 的写接口先 `requireActive()`：`PENDING_DELETION` 409，`BANNED`、`DELETED` 401。注销冷静期、到期执行是 PAP-100；到期时资料行置空不删行（读模型要求资料行与用户一对一存在，缺行是 500）。

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
| 改邮箱的新地址已被使用；确认 / 撤销时地址被别人注册了（`errors[]`：`email` / `ALREADY_REGISTERED`） | 409 | `IDN-0409` |
| 找回密码的申请或提交过于频繁（`Retry-After`、`retryAfterSeconds`；文案「操作过于频繁，请稍后再试」） | 429 | `IDN-0429` |
| 改邮箱今天的配额用完（文案「今天更改邮箱的次数已用完，请明天再试」） | 429 | `IDN-0429` |
| 新密码与当前相同、新邮箱与当前相同（`errors[]`：`newPassword` / `UNCHANGED`、`newEmail` / `UNCHANGED`） | 422 | `IDN-0422` |
| 账号已被封禁 | 403 | `IDN-0403` |
| 用户不存在：后台接口；公开身份卡不存在或对请求者不可见 | 404 | `IDN-0404` |
| Redis 不可用（登录限流、会话存储）、哈希排队超时 | 503 | `IDN-0503` |
| 保存资料时 catalog 不可用（连不上、超时、没有实例、回了 404 以外的错误；`traits` 为 `DEP_UNAVAILABLE`，文案「机构与关注领域暂时无法校验，请稍后再试」；不保存） | 503 | `IDN-0503` |

请求本身格式不对时由 Spring 直接处理，返回默认格式的 ProblemDetail，没有 `code`：请求体不是 JSON 对象（`[]`、`"x"`、空请求体、坏 JSON）或字段类型不对返回 400；`Content-Type` 不是 JSON 返回 415；方法不对（比如 GET）返回 405；路径里的 `userId` 不是数字或溢出返回 400，负数和 0 按用户不存在返回 404 `IDN-0404`；`/users/{username}` 路径里带 `%20`、`%25` 等百分号编码时被 Spring Security 的请求防火墙拒绝，返回 400。

原因码：邮箱 `REQUIRED`、`TOO_LONG`、`INVALID_FORMAT`、`ALREADY_REGISTERED`（409）；用户名 `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`INVALID_FORMAT`、`DIGITS_ONLY`、`RESERVED`、`UNCHANGED`（改名）、`ALREADY_TAKEN`（409）；昵称 `INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`INVALID_FORMAT`、`RESERVED`；资料与隐私见第 12 节；密码 `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`TOO_COMMON`（改密码、重设时字段名是 `currentPassword` / `newPassword`，改邮箱时新邮箱字段名是 `newEmail`）；客户端类型 `INVALID_FORMAT`；设备标识 `TOO_LONG`、`INVALID_FORMAT`（含控制字符）。

## 8. 本地运行和测试

- 配置从 mini 的 Nacos 读（`patra-common.yaml`、`patra-identity.yaml`，源文件在 `patra-infra/nacos/dev/`）：连 mini 上的 `patra_identity` 库和 Redis（`PATRA_INFRA_HOST`），和 mini 上的容器共用同一个 Redis 0 号库。Redis 密码在 Nacos 里写成 `${REDIS_PASSWORD}`，本机由 Nacos starter 从 `~/.patra/secrets/redis.env` 读；生成和复制见 `patra-infra/docker/README.md`「密钥」。
- 库结构只有一份 `V1`（`infra/src/main/resources/db/migration/`）。库还是旧结构时启动会因 Flyway 校验失败起不来，推倒重建再启动；开发数据可丢弃，不做 `pg_dump`：

```bash
docker exec patra-postgres psql -U postgres -c 'DROP DATABASE IF EXISTS patra_identity WITH (FORCE);' -c 'CREATE DATABASE patra_identity;'
```

mini 上先 `docker stop patra-identity`，重建后 `docker start patra-identity`。

- 邮件：`spring.mail.host` 缺省跟随 `PATRA_INFRA_HOST`、端口 1025，即 mini core 栈的 Mailpit（UI `http://linqibins-mac-mini:8025`）；本机不用放 `mail.env`。验证链接的域名来自 `patra.identity.mail.links.base-url`（Nacos 里 `${PATRA_PORTAL_BASE_URL:http://linqibins-mac-mini:4000}`），本机起门户时设 `PATRA_PORTAL_BASE_URL=http://localhost:4000`，链接才指向本机门户。
- XXL-Job 执行器端口 6401（服务端口 + 1），回调地址读 `PATRA_ADVERTISE_IP`；本机不想注册到 mini 的调度中心时设 `XXL_JOB_ENABLED=false`。
- 手工验收再认证时不想等 10 分钟：临时把 `patra.identity.reauth.window` 设成 `30s`（环境变量 `PATRA_IDENTITY_REAUTH_WINDOW=30s`）。

- 测试全部用 Testcontainers（PostgreSQL 17、Redis 7.0.15），本机要有 Docker：

```bash
./gradlew spotlessApply && ./gradlew check && ./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest
```

`check` 不包含集成测试，按模块各自跑。

identity 从网关签的身份断言取当前用户，要配网关的公钥：写在 Nacos 公共配置 `patra-common.yaml` 里，和网关共用一份，换密钥时改这一处。集成测试不读这些：安全 starter 的测试支持会自动注入测试公钥。

容器和本机读同一份 Nacos 配置，只差 `PATRA_INFRA_HOST` 等本地变量；容器里的 `REDIS_PASSWORD` 来自 compose 加载的 `~/.patra/secrets/redis.env`。

## 9. 会话

登录、注册成功后签发不透明令牌，会话写进 Redis，登录记录写进 `idn_user_login_record`（ID 就是会话 ID）。
令牌、键和脚本见 `patra-identity-session/README.md`。

策略只在这里配，网关不配：

```yaml
patra:
  identity:
    session:
      max-sessions-per-user: 10      # 超过挤掉最老的，记录标 REPLACED
      lifetime:
        user:
          web:
            idle: 30d                # 不活跃过期；网关每个请求续期，60 秒内只读不写
            absolute: 180d           # 绝对过期
```

缺 `user.web` 这一行、键不认识、`idle` 长于 `absolute`，应用启动失败。

登出删会话并把记录标成 `LOGOUT`；封禁删该用户全部会话并把记录标成 `BANNED`。会话过期不回写记录：
记录里 `ended_at` 为空而 Redis 里没有会话，就是过期了。

登录建会话前在事务里对用户行加锁重读、复核封禁（`UserRepository.findByIdForUpdate`），封禁与登录并发时
要么登录返回 403，要么封禁把这条新会话一起删掉；被封用户拿不到有效会话。

## 10. 邮箱验证与邮件

工程设计：`docs/patra/specs/2026-10-21-identity-email-verification-design.md`。

- **令牌**：32 字节 CSPRNG、base64url 原文 43 个字符，库里只存 SHA-256 十六进制；四种用途共用 `idn_email_token`（`EMAIL_VERIFY` 24 小时、`PASSWORD_RESET` 1 小时、`EMAIL_CHANGE_CONFIRM` 1 小时、`EMAIL_CHANGE_REVERT` 7 天）。签发前在同一事务里作废同用户同用途的旧令牌；消费是一条带条件的 `UPDATE … RETURNING`，并发只有一个赢。封禁按用户批量作废（`EMAIL_CHANGE_REVERT` 例外，可并存、不被批量作废）。令牌原文只出现在邮件里，不进响应、不进日志。
- **链接**：`<base-url><verify-email-path>#token=<原文>`，域名来自配置不来自请求的 Host 头；令牌在 URL 片段里，门户落地页读出后 POST `/auth/email/verify`，GET 预取不会把它用掉。
- **注册与重发**：注册成功、事务提交后异步发首封并计入重发配额；重发先判状态（已验证 409、注销中 409）再限流：冷却 60 秒、每账号每小时 5 次、每天 10 次、每 IP 每小时 30 次（Redis Lua 原子判定，`Retry-After` 取最长的等待）。配置在 `patra.identity.mail.resend.*`。
- **验证**：写 `email_verified_at`；带会话验证时保留当前会话、撤销其余（登录记录标 `REVOKED`），匿名验证撤销全部会话。
- **签发**：四种用途共用 `EmailTokenIssuer.issue(purpose, userId, email, ip)`，返回已落库的聚合与原文；调用方用 `MailLinkPolicy` 拼链接、构造邮件；撤销令牌（`EMAIL_CHANGE_REVERT`）签发时不作废旧的。
- **邮件**：`EmailDeliveryPort.send(OutgoingMail)`，infra 的 `SmtpEmailDeliveryAdapter` 用 Spring Mail 发 multipart（文本 + HTML），虚拟线程异步、并发上限 4，失败只记日志（不含收件人与令牌）；带 `Auto-Submitted` / `X-Auto-Response-Suppress` 头。模板在 `infra/src/main/resources/mail/html/` 与 `mail/text/`，同名一对；新邮件 = 一个 `OutgoingMail` 实现 + 两份模板，公共抬头 / 页脚是片段，安全通知共用 `security-notice` 片段（`SecurityNotice`：时间、设备、IP 与属地、「不是我」该怎么办；PAP-99 之前设备与属地为「未知」；「建议到安全设置……」一句由各模板按场景自己写——改回邮箱后密码已作废的人登不进去）。
- **配置**：服务商坐标 `spring.mail.*`、发件人 `patra.identity.mail.from`、门户地址 `patra.identity.mail.links.base-url` 在 Nacos 的 `patra-identity.yaml`，缺省指向 Mailpit；mini 接真实服务商（Resend）靠 `~/.patra/secrets/mail.env` 覆盖（模板 `patra-infra/docker/secrets/mail.env.example`）。时区 `patra.identity.mail.zone`、保留期 `patra.identity.mail.token-retention` 在 jar 里。SMTP 连通性不计入健康检查（`management.health.mail.enabled: false`）。
- **清理**：XXL-Job 任务 `emailTokenCleanupJob`（执行器 `patra-identity`）删过期超过保留期（默认 30 天）的令牌，每批 1000 行、每批一个事务；mini 上每天 04:30 一次。

## 11. 账号安全：再认证、改密码、找回密码、改邮箱

工程设计：`docs/patra/specs/2026-10-22-identity-account-security-design.md`。

- **再认证**：会话 HASH 多一个 `auth_at`（登录、注册、再认证、改密码时写）。改邮箱、踢设备（PAP-99）、注销（PAP-100）要求 10 分钟内输过密码：`ReauthenticationGuard` 按断言里的会话 ID 读 `auth_at`，会话不在 401，超窗 403 `REAUTH_REQUIRED`。`POST /auth/reauth` 校验密码（共用登录失败限制）后换新令牌保留会话 ID（`RedisSessionStore.rotate`），旧令牌立即失效，门户要换 Cookie；`auth_at` 距今不足宽限期（10 秒）时不换、响应 `sessionToken` 为 `null`、门户保持现有 Cookie——两个标签页并发再认证时先换出的令牌保持有效。本 PR 前建立的会话没有 `auth_at`，视作从未再认证。配置 `patra.identity.reauth.window`（10m）、`patra.identity.reauth.rotation-grace`（10s）。
- **改密码**：每次都输当前密码（不走窗口）；新密码按注册规则、不能与当前相同（NFKC 后比较）；事务里锁行后再比一次凭据哈希是不是刚校验过的那个（并发改密码只有先到的生效，后到的 401）、换哈希、按用户批量作废令牌、撤销其余会话并记 `REVOKED`、最后本机换令牌（刷新 `auth_at`；放最后一步，之前失败不碰旧令牌）；提交后发「密码已修改」。不存历史密码。
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
| 已封禁、已注销 | 404 | 404 | — |

「已登录」是任何带有效会话的人（含邮箱未验证、冷静期中的账号）。专业背景在本人或其可见性为公开时给出，否则 `professional` 为 `null`（四项都没填也是 `null`）；链接只在邮箱已验证时给出（本人也一样）；`indexable` 只在谁都能看且用户允许收录时为真，门户据此输出 `noindex`；`joinedAt` 是 `YYYY-MM`（时区同 `patra.identity.mail.zone`）。响应不含邮箱、ID、状态、任何设置原值。旧名跳转的可见性看原主的当前主页。
- **昵称修改**只打 INFO 日志（`昵称已修改`）；账号审计记录以后单独做成通用的账号事件表。
- **注销（PAP-100）**：到期执行时资料行置空不删行、隐私回默认，两张子表按 `user_id` 删行。
