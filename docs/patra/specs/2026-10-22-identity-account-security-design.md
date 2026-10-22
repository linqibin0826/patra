# identity 账号安全工程设计（PAP-98）

> **Issue**：[PAP-98](https://linear.app/papertrace/issue/PAP-98)（GitHub #264）
> **版本**：[v0.12 User Center](../release-specs/v0.12-user-center.md)（2.1.7 账号安全、2.1.10 安全通知邮件、决策 E / K）
> **前置设计**：[identity 账号重建（PAP-93）](2026-10-20-identity-account-rebuild-design.md)（`IdentityErrorTrait`、状态守卫、第 16 节交给本 Issue 的约束）、[identity 邮件发送与邮箱验证（PAP-96）](2026-10-21-identity-email-verification-design.md)（令牌表、批量作废、安全通知邮件框架、限流脚本、第 18 节交给本 Issue 的约束）、[identity 会话（PAP-64）](2026-10-09-identity-session-design.md)（会话 HASH、Lua 约定）、[网关鉴权（PAP-65）](2026-10-09-gateway-auth-design.md)（公开名单、断言里的 `sid`）
> **日期**：2026-10-22
> **状态**：已实施（2026-10-22，分支 `feat/v0.12-user-center-api`，六条待拍板都按建议定）；实测结果见第 16 节，mini 上的验收（D4）随后端 PR 部署时做

## 1. 要解决的问题

账号模型（PAP-93）和发信链路（PAP-96）都有了，用户还是管不了自己的账号：密码改不了、忘了找不回、邮箱换不掉。本 Issue 把这四件事做成闭环，并建立一条以后所有敏感操作都要走的门槛——再认证。

- **再认证**：会话里记「最近一次输密码的时间」，敏感操作要求它在 10 分钟内；超时回 403 `REAUTH_REQUIRED`，门户弹密码框、调 `POST /auth/reauth` 后重试。本 Issue 只有改邮箱走这道门；PAP-99 的踢设备、PAP-100 的注销、以后的绑定 / 解绑第三方登录复用同一个守卫。
- **改密码**：每次都输当前密码；本机换新令牌保留登录，其他会话全部撤销；发「密码已修改」邮件。
- **找回密码**：申请一律 202、未注册不发信；1 小时令牌；重设后撤销全部会话与令牌、取消待生效的改邮箱、清零登录失败计数、视为邮箱已验证；发「密码已重设」邮件。
- **改邮箱**：再认证 → 新邮箱 1 小时确认链接 → 点通立即生效 → 旧邮箱收到含 7 天「不是我」链接的通知 → 点「不是我」改回旧地址、撤销全部会话、作废密码并发重设链接。
- **限流**：找回密码、提交重设、改邮箱各自的配额，429 带 `Retry-After`。

设计过程中定下的、影响别的 Issue 的事：

- 再认证守卫放在 identity 内部（`ReauthenticationGuard` 领域服务），不放安全 starter：只有 identity 和网关能读 Redis 会话（PAP-64 决策），而需要再认证的操作全在 identity（第 7.3 节）。
- 会话 HASH 加 `auth_at`；登录、注册、再认证、改密码都写它。`RedisSessionStore` 加「换令牌保留会话 ID」和「按会话 ID 读会话」两个操作（第 6 节）。
- 再认证和改密码成功后响应里带新令牌，旧令牌立即失效：门户拿到 `sessionToken` 就要换掉 Cookie（第 8.1、8.2 节；PAP-102）。
- 「待确认的改邮箱」没有新表、没有新列：它就是一条未过期的 `EMAIL_CHANGE_CONFIRM` 令牌（第 7.4 节）。
- 四种限流共用一段参数化的 Lua（`action-throttle.lua`，冷却 + 任意个滑动窗口），PAP-96 的重发限流改到它上面（第 10 节）。
- 验证邮箱令牌的签发服务泛化成 `EmailTokenIssuer`，四种用途共用（第 7.2 节）。
- 找回密码、改邮箱确认、撤销这三个落地页的路径进 `MailLinkPolicy`（第 9.4 节；PAP-102）。
- 当前密码错误是 401 + `traits` 含 `PASSWORD_MISMATCH`，门户据此区分「密码错」和「会话没了」（第 12 节；PAP-102）。
- `security-notice` 片段不再固定追加「建议立即到安全设置……」一句，由各模板按场景决定（第 9.2 节；PAP-99 / PAP-100 的通知邮件同样）。

**完成标准**：PAP-98 的 Acceptance Criteria，逐条对应见第 15.5 节。

## 2. 范围

做：

- 会话模块：HASH 加 `auth_at`；`rotate`（换令牌保留会话 ID、刷新 `auth_at`）与 `find`（按会话 ID 读，不续期）两段 Lua；`StoredSession` 加 `authenticatedAt`。
- identity：`POST /auth/reauth`；`ReauthenticationGuard`；`PUT /me/password`；`POST /auth/password/forgot`、`POST /auth/password/reset`；`POST /me/email/change`、`GET /me/email/change`、`DELETE /me/email/change`、`POST /auth/email/change/confirm`、`POST /auth/email/change/revert`；两个新的限流端口与一段通用 Lua；七个邮件模板；`EmailTokenIssuer`；`User.changeEmail`、`UserPasswordCredential.rotate`、凭据删除；`LoginThrottlePort.clear`；`EmailTokenRepository.findActive`。
- 网关：公开 `/patra-identity/auth/password/**`、`/patra-identity/auth/email/change/**`。
- 文档：本 spec 的 NIST 偏离说明（决策 K）；identity README、session README；release spec 第 19 节的补充。

本 Issue 不做：

- 设备、IP 属地进安全通知（PAP-99 接通受信转发后填；本 Issue 的通知里这两项显示「未知」）。
- 踢设备、注销走再认证（PAP-99 / PAP-100 复用守卫）。
- 门户的密码框、落地页、Cookie 换令牌（PAP-102）。
- 历史密码、密码过期、二次验证（release spec 2.1.7 明确不存历史密码；其余不在本版）。
- 新邮箱已被占用时通知该地址的现有账号主（业界常见做法，本版不做，见第 20 节待拍板）。
- 撤销改邮箱时同时取消注销申请（PAP-100 的事，见第 20 节待拍板）。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 会话 HASH 加 `auth_at`；`POST /auth/reauth` 校验密码（共用登录失败限制）后换新令牌并刷新；窗口 10 分钟；identity 按断言里的会话 ID 读 `auth_at`，超时 403 `REAUTH_REQUIRED`；网关不改；改密码不走窗口、每次输当前密码 | release spec 2.1.7、决策 E |
| 改密码：新旧不同、不存历史密码、本机换新令牌保留登录、其他会话全部撤销、作废全部待用重设令牌、发「密码已修改」 | release spec 2.1.7 |
| 找回密码：一律 202 异步发信、未注册不发；令牌 1 小时；重设后撤销全部会话与令牌、取消待生效的改邮箱、不自动登录、清零该邮箱的失败计数、可发给未验证邮箱、重设成功视为已验证；申请与提交不计入登录失败 | release spec 2.1.7、决策 C |
| 改邮箱：再认证 → 新邮箱 1 小时确认 → 立即生效 → 旧邮箱 7 天「不是我」；撤销则改回、撤销全部会话、作废密码并发重设链接；同一时间只能一个待确认、可取消；新邮箱已被别人使用回 409（2026-10-22 拍板，改 release spec 原来的「也回已发送」）；生效后新旧各一封 | release spec 2.1.7、2.1.10 |
| 限流：找回密码同邮箱 60 秒冷却、每天 ≤ 5；同 IP 每小时 ≤ 20；提交令牌每 IP 每分钟 ≤ 10；改邮箱每账号每天 ≤ 3 | release spec 2.1.7 |
| 密码规则 8 ~ 64 个码点 + 常见密码名单 + 登录失败限制，不改；与 NIST SP 800-63B-4 的偏离按 ASVS 7.1.1 记录 | release spec 决策 K |
| 四种令牌共用 `idn_email_token`；`PASSWORD_RESET` 1h、`EMAIL_CHANGE_CONFIRM` 1h（`email` 列存新邮箱）、`EMAIL_CHANGE_REVERT` 7d（`email` 列存要改回的旧邮箱）；撤销令牌可并存、不被 `revokeAllActive` 作废；消费一条撤销令牌后作废该用户其余的撤销令牌 | PAP-96 第 7.4、18 节 |
| `revokeAllExcept` 保留本机，结束原因 `REVOKED`；三封通知邮件各一个 `OutgoingMail` 记录 + 两个模板，复用 `security-notice` 片段；`MailLinkPolicy` 加路径 | PAP-96 第 7.5、9.4、18 节 |
| `403 REAUTH_REQUIRED` = `FORBIDDEN` + `IdentityErrorTrait.REAUTH_REQUIRED`；`/me/**` 写处理器先 `findByIdForUpdate` 再 `requireActive()`；登录响应的 `user` 结构不变，`/auth/reauth` 换令牌后也返回它 | PAP-93 第 10、16 节 |
| 邮件链接 `{base-url}{路径}#token=…`，门户落地页读片段后 POST 消费；域名只来自配置 | PAP-96 第 9.5 节 |
| 加锁顺序：先锁用户行，再动令牌行 | PAP-96 第 8.3 节（builder check） |
| 时间来自注入的 `Clock`；Lua 不调 `TIME`；脚本不返回 `false` | PAP-64、PAP-96 |

## 4. 业界参照

- **再认证（sudo 模式）**：GitHub 的 sudo mode（敏感操作要求 2 小时内输过密码，在会话里记时间戳）、Google 的「重新验证」（改密码、看已保存密码前重输密码）。Patra 的窗口取 10 分钟（release spec 定）。都是「会话里记时间，服务端按它判」，不是单独的「sudo 令牌」。
- **改密码保留本机**：GitHub、Google、Microsoft 改密码后其他设备下线、当前设备保留。Django 用 `update_session_auth_hash` 让当前会话跟着密码哈希走。Patra 用「换令牌保留会话 ID」：本机的会话 ID 不变（登录记录、设备列表都不变），但令牌换了。
- **找回密码的防枚举**：OWASP Forgot Password Cheat Sheet：申请一律同样的响应、同样量级的耗时；令牌一次性、短期；重设成功让所有会话失效、通知用户。Django、Devise 都不区分未注册。
- **改邮箱的「不是我」**：GitHub 改主邮箱后给旧邮箱发通知；Google、Apple 给旧邮箱发含「恢复」链接的通知，窗口 7 ~ 14 天。「撤回后锁住账号、发重设链接」是 Apple ID 的做法（撤回后要重设密码）。
- **令牌放 URL 片段**：沿用 PAP-96。

## 5. 模块与改动面

| 模块 | 改动 |
|---|---|
| `patra-identity-session` | `session-create.lua` 写 `auth_at`；新增 `session-rotate.lua`、`session-find.lua`；`RedisSessionStore.rotate` / `find`；`StoredSession` 加 `authenticatedAt`；README |
| `patra-identity-domain` | `User.changeEmail`；`UserPasswordCredential.rotate`；`UserPasswordCredentialRepository.deleteByUserId`；`EmailTokenRepository.findActive`；`SessionStorePort.rotate` / `findAuthenticatedAt`；`LoginThrottlePort.clear`；`ReauthenticationPolicy`、`PasswordResetPolicy`、`EmailChangePolicy`；`PasswordResetThrottlePort`、`EmailChangeThrottlePort`；`EmailTokenIssuer`（替代 `EmailVerificationIssuer`）、`ReauthenticationGuard`；`MailLinkPolicy` 加三个路径与三个链接方法；七个 `OutgoingMail` 记录；`IdentityErrorTrait` 加 `REAUTH_REQUIRED`、`EMAIL_CHANGE_PENDING`、`PASSWORD_MISMATCH`；异常 `ReauthenticationRequiredException`、`CurrentPasswordMismatchException`、`EmailChangePendingException`、`PasswordResetLimitedException`、`EmailChangeLimitedException`；`UserFieldViolations` 加 `currentPassword` / `newPassword` / `newEmail` 的字段名与工厂 |
| `patra-identity-app` | `ReauthenticateUserHandler`、`ChangePasswordHandler`、`RequestPasswordResetHandler`、`ResetPasswordHandler`、`RequestEmailChangeHandler`、`CancelEmailChangeHandler`、`ConfirmEmailChangeHandler`、`RevertEmailChangeHandler` 及各自的命令 / 结果；`UserQueryService.pendingEmailChange`；注册与重发处理器改用 `EmailTokenIssuer` |
| `patra-identity-infra` | `action-throttle.lua`（通用）与 `ActionThrottleScript`（共用执行逻辑）；`EmailResendThrottleAdapter` 改到它上面；`PasswordResetThrottleAdapter`、`EmailChangeThrottleAdapter`；`LoginThrottleAdapter.clear`；`SessionStoreAdapter` 两个方法；`EmailTokenDao.findActive`；`UserPasswordCredentialDao.deleteByUserId`；七对邮件模板；`security-notice` 片段去掉固定句 |
| `patra-identity-adapter` | `AuthController` 加 `POST /auth/reauth`；新建 `PasswordResetController`（`/auth/password`）、`EmailChangeController`（`/auth/email/change`）；`MeController` 加 `PUT /me/password`；`MeEmailController` 加 `POST` / `GET` / `DELETE /me/email/change`；请求、响应记录 |
| `patra-identity-boot` | `IdentityProperties` 加 `reauth`、`passwordReset`、`emailChange`、`links` 的三个路径；`IdentityConfiguration` 装配三个策略、`EmailTokenIssuer`、`ReauthenticationGuard`；`application.yml` |
| `patra-gateway-boot` | `IDENTITY_PUBLIC_PATHS` 加两项；集成测试 |
| 文档 | 本 spec；identity README、session README；release spec 第 19 节 |

不动：`V1`（表结构够用：令牌表已有四种用途，`idn_user` 已有 `email_verified_at`）、`patra-tool-*`、门户。

## 6. 会话存储（`patra-identity-session`）

### 6.1 `auth_at`

会话 HASH 加一个字段 `auth_at`（epoch 毫秒）：最近一次用密码证明身份的时间。

| 时机 | 值 |
|---|---|
| 登录、注册建会话（`session-create.lua`） | `= created_at`：刚输过密码 |
| 再认证（`session-rotate.lua`） | `= now` |
| 改密码（`session-rotate.lua`） | `= now`：输过当前密码 |
| 续期（`session-touch.lua`） | 不动 |

`StoredSession` 加 `Instant authenticatedAt`：没有 `auth_at` 字段的会话（本 PR 部署前建立的）按 `Instant.EPOCH` 读出——视作从未再认证，第一次做敏感操作时要输一次密码。不回填、不迁移。网关的 `toCurrentUser()` 不读它，网关代码不改。

### 6.2 `session-rotate.lua`：换令牌保留会话 ID

```
KEYS[1] 索引  KEYS[2] 新会话键
ARGV[1] 前缀  ARGV[2] 会话 ID  ARGV[3] 新令牌哈希  ARGV[4] now
hash = HGET index sid；没有 → return {}
fields = HGETALL prefix..hash；空 → HDEL index sid；return {}
now >= expires_at → DEL 旧键；HDEL index sid；return {}
fields.auth_at = now；fields.last_active_at = now
HSET 新键 fields…；PEXPIRE 新键 min(idle_timeout_ms, expires_at − now)
DEL 旧键；HSET index sid 新哈希
return fields（HGETALL 形式）
```

- 会话 ID、`created_at`、`expires_at`、`idle_timeout_ms`、`device_id` 全部保留：登录记录不变，设备列表（PAP-99）里还是同一台。绝对过期不重置——换令牌不是重新登录。
- 旧令牌在脚本返回的那一刻失效。网关没有缓存，下一个带旧令牌的请求是 401。
- 新令牌由 Java 生成（`SessionToken.generate`），脚本只拿哈希。

`RedisSessionStore.rotate(accountType, userId, sessionId) → Optional<IssuedSession>`：返回新令牌（`replacedSessionIds` 为空）；会话不在或已过期时为空。

### 6.3 `session-find.lua`：按会话 ID 读，不续期

```
KEYS[1] 索引
ARGV[1] 前缀  ARGV[2] 会话 ID  ARGV[3] now
hash = HGET index sid；没有 → return {}
fields = HGETALL；空 → HDEL index sid；return {}
now >= expires_at → DEL；HDEL；return {}
return fields
```

`RedisSessionStore.find(accountType, userId, sessionId) → Optional<StoredSession>`。再认证守卫用它读 `auth_at`：不续期，不改 `last_active_at`——守卫是读，不是活动。

### 6.4 为什么不直接让 identity 读索引再读会话

两步读在 Java 侧要处理「索引有、会话没了」的悬空条目，而脚本里顺手就清掉了；和 `session-delete.lua` 的写法一致。

## 7. 领域模型

### 7.1 聚合与值对象

| 名称 | 改动 |
|---|---|
| `User` | 加 `changeEmail(EmailAddress next, Instant now)`：`DELETED` 抛 `InvalidUserStateTransitionException`；换邮箱，`emailVerifiedAt = now`（点确认链接或撤销链接的人证明了对这个地址的控制）。其他状态不拦：调用方（确认、撤销处理器）自己判状态 |
| `UserPasswordCredential` | 加 `rotate(PasswordHash next)`：换哈希。不存旧哈希 |
| `EmailToken` | 不变 |
| `ReauthenticationPolicy` | 值对象：`window`（10 分钟），构造时校验正数；`isRecent(Instant authenticatedAt, Instant now)` = `now < authenticatedAt + window` |
| `PasswordResetPolicy` | 值对象：`cooldown`（60 秒）、`perEmailDaily`（5）、`perIpHourly`（20）、`submitPerIpMinutely`（10）；正数校验 |
| `EmailChangePolicy` | 值对象：`perAccountDaily`（3）；正数校验 |
| `MailLinkPolicy` | 加 `resetPasswordPath`、`confirmEmailChangePath`、`revertEmailChangePath`（都以 `/` 开头）；`resetPasswordUrl(secret)`、`confirmEmailChangeUrl(secret)`、`revertEmailChangeUrl(secret)`，格式同 `verifyEmailUrl` |
| `IdentityErrorTrait` | 加 `REAUTH_REQUIRED`（403）、`EMAIL_CHANGE_PENDING`（409）、`PASSWORD_MISMATCH`（401） |
| `UserFieldViolations` | 加字段名 `CURRENT_PASSWORD = "currentPassword"`、`NEW_PASSWORD = "newPassword"`、`NEW_EMAIL = "newEmail"`；`PasswordPolicy.validateForRegistration(raw)` 改为 `validate(raw, field)`（注册传 `PASSWORD`，改密码与重设传 `NEW_PASSWORD`）；`EmailAddress.validate(raw)` 同样加 `field` 参数（改邮箱传 `NEW_EMAIL`）；加 `newPasswordUnchanged()`（`newPassword` / `UNCHANGED`）、`newEmailUnchanged()`（`newEmail` / `UNCHANGED`） |

### 7.2 `EmailTokenIssuer`：四种用途共用的签发服务

替代 PAP-96 的 `EmailVerificationIssuer`（只管 `EMAIL_VERIFY`、顺带拼邮件）。泛化后签发和拼邮件分开：

`issue(EmailTokenPurpose purpose, long userId, EmailAddress email, String requestedIp) → IssuedEmailToken(EmailToken token, EmailTokenSecret secret)`：

1. `purpose != EMAIL_CHANGE_REVERT` 时 `tokens.revokeActive(userId, purpose, now)`（同用途只留一个）；撤销令牌不作废旧的（可并存，PAP-96 第 7.4 节）。
2. 生成 `EmailTokenSecret`，`tokens.save(EmailToken.issue(purpose, userId, email, secret, now, requestedIp))`。
3. 返回已保存的令牌和原文。原文只在返回值里出现一次；调用方用 `MailLinkPolicy` 拼链接、构造邮件。

必须在调用方的事务里执行，且调用方已经锁了用户行（加锁顺序）。注册、重发处理器改为 `issuer.issue(EMAIL_VERIFY, …)` + `EmailVerificationMail.of(…)`；`PreparedVerificationMail` 删除。

### 7.3 `ReauthenticationGuard`：再认证守卫

领域服务，依赖 `SessionStorePort`、`ReauthenticationPolicy`、`Clock`。

`requireRecent(CurrentUser current)`：

1. `sessions.findAuthenticatedAt(current.accountType(), current.userId(), current.sessionId())`：空（会话已不在——网关的断言最多 60 秒前签的，这期间被踢、被封、过期）→ `AuthenticationRequiredException`（401）。
2. `!policy.isRecent(authenticatedAt, now)` → `ReauthenticationRequiredException`（403，`FORBIDDEN` + `REAUTH_REQUIRED`）。

放 identity 的领域层而不是安全 starter：starter 给所有下游用，下游不碰 Redis（PAP-64 第 4 节三个备选里选的 A）；需要再认证的操作本版和可见的将来都在 identity。要是以后别的服务也要，再把「断言里带 `auth_at`」提上议程——那是网关和 starter 的改动，不是守卫的。

处理器里的位置：在字段校验和状态守卫之后、限流之前（第 8.5 节）。先 422 / 409 再 403：门户弹出密码框之前，用户已经把能改的输入改对了。

### 7.4 「待确认的改邮箱」就是一条令牌

不加表、不加列。待确认 = 该用户一条未消费、未作废、未过期的 `EMAIL_CHANGE_CONFIRM` 令牌，`email` 列是新地址。

- 「同一时间只能一个」：`tokens.findActive(userId, EMAIL_CHANGE_CONFIRM, now)` 有 → 409 `EMAIL_CHANGE_PENDING`。
- 「取消」：`revokeActive(userId, EMAIL_CHANGE_CONFIRM, now)`。
- 「重设密码取消待生效的改邮箱」：`revokeAllActive` 顺带作废它，不用专门写。
- 「到期自动消失」：`findActive` 带 `expires_at > now`，过期的令牌不算待确认，不用清理任务。
- 门户要显示「正在等 X 确认，HH:MM 前有效」：`GET /me/email/change` 读同一条令牌（第 8.6 节）。

### 7.5 哪些操作作废令牌、撤销会话、动密码

| 操作 | 令牌 | 会话 | 密码 | 失败计数 |
|---|---|---|---|---|
| 再认证 | — | `rotate`（本机换令牌） | — | 共用登录失败限制：错计失败、锁定期 429 |
| 改密码 | `revokeAllActive`（重设、验证、待确认的改邮箱全废） | `rotate` 本机 + `revokeAllExcept` 其余（`REVOKED`） | `rotate` | 共用登录失败限制 |
| 找回密码：申请 | `revokeActive(PASSWORD_RESET)` 再签发 | — | — | 不计 |
| 找回密码：重设 | `revokeAllActive`（含待确认的改邮箱） | `revokeAll`（`REVOKED`） | `rotate`（没有凭据行时新建） | `clear`：清零并解锁 |
| 改邮箱：申请 | `revokeActive(EMAIL_CHANGE_CONFIRM)` 再签发 | — | — | — |
| 改邮箱：取消 | `revokeActive(EMAIL_CHANGE_CONFIRM)` | — | — | — |
| 改邮箱：确认 | `revokeAllActive`（旧地址的验证、重设令牌全废）再签发 `EMAIL_CHANGE_REVERT` | 不动（本人刚再认证过） | — | — |
| 改邮箱：撤销 | `revokeAllActive` + `revokeRevertTokensIssuedAfter`（只作废更晚签发的撤销链接）再签发 `PASSWORD_RESET` | `revokeAll`（`REVOKED`） | `deleteByUserId`（作废：登录按「凭据不存在」走假哈希路径 401） | — |

「作废密码」用删凭据行而不是写一个随机哈希：登录处理器已经处理「凭据不存在」（`verifyAgainstDummy` 后 401），重设处理器对没有凭据行的用户新建一行；状态也更诚实——库里没有可用的密码，就没有那一行。

### 7.6 端口

| 端口 | 改动 | 实现 |
|---|---|---|
| `SessionStorePort` | 加 `Optional<String> rotate(accountType, userId, sessionId)`（新令牌原文；会话不在为空）、`Optional<Instant> findAuthenticatedAt(accountType, userId, sessionId)`（会话不在为空） | `SessionStoreAdapter` → `RedisSessionStore.rotate` / `find` |
| `LoginThrottlePort` | 加 `clear(accountType, email)`：删失败计数与锁（在途登记不动，自然过期） | `LoginThrottleAdapter`：`DEL` 两个键 |
| `UserPasswordCredentialRepository` | 加 `deleteByUserId(userId) → boolean` | `UserPasswordCredentialRepositoryAdapter` |
| `EmailTokenRepository` | 加 `findActive(userId, purpose, now) → Optional<EmailToken>`：未消费、未作废、`expires_at > now` 的一条（同用途至多一条，撤销令牌不用它查）；加 `revokeRevertTokensIssuedAfter(userId, tokenId, now)`：只作废 ID 大于（即更晚签发）指定那条的有效撤销令牌 | `EmailTokenDao` JPQL |
| `PasswordResetThrottlePort`（新，`port/throttle/`） | `acquireRequest(EmailAddress email, String clientIp, Instant now) → Optional<Duration>`：冷却 + 同邮箱每天 + 同 IP 每小时；`acquireSubmit(String clientIp, Instant now) → Optional<Duration>`：同 IP 每分钟。IP 为 `null` 时跳过 IP 维度 | `PasswordResetThrottleAdapter` |
| `EmailChangeThrottlePort`（新） | `acquire(long userId, Instant now) → Optional<Duration>`：同账号每天 | `EmailChangeThrottleAdapter` |
| `EmailResendThrottlePort` | 接口不变 | 适配器改到通用脚本上 |
| `UserReadPort` | 不变 | — |

三个限流端口的语义和 `EmailResendThrottlePort` 一样：放行即已计数，之后的步骤失败不退还；Redis 暂时不可用抛 `TemporarilyUnavailableException`（503）。

## 8. 接口与流程

路径前缀经网关是 `/patra-identity`。所有处理器的时间来自注入的 `Clock`；客户端 IP 来自 `HttpServletRequest.getRemoteAddr()`（PAP-96 第 10.4 节：受信转发接通前是门户地址）。

### 8.1 再认证 `POST /auth/reauth`

请求体 `{ "password" }`。网关：需登录（`/patra-identity/**` 默认规则已覆盖，不用加名单）。`ReauthenticateUserHandler`：

1. `currentUserPort.require()`。
2. `PlainPassword.validate` → 422 `password` / `REQUIRED` 或 `INVALID_CHARACTER`。
3. 不加锁读用户：不存在、`BANNED`、`DELETED` → 401 `AuthenticationRequiredException`；`ACTIVE`、`PENDING_DELETION` 继续（冷静期内能登录就能再认证——取消注销以后要是也要再认证，这里不能拦）。
4. `loginThrottle.begin(USER, 用户当前邮箱)`：锁定期 → 429 `Retry-After`。密码超过 `MAX_LOGIN_LENGTH` → 按失败结算。
5. `credentials.findByUserId` + `passwordHashing.matches`：凭据不存在（撤销改邮箱后）→ `verifyAgainstDummy` 后按失败结算；不匹配 → `recordFailure` → 锁定则 429，否则 401 `CurrentPasswordMismatchException`（`UNAUTHORIZED` + `PASSWORD_MISMATCH`）。中途异常 → `cancel`。
6. `recordSuccess`。
7. `userReadPort.findAccount`（不存在 → 401）。
8. `sessions.rotate(USER, userId, sessionId)`（最后一步）：空（会话这期间没了）→ 401 `AuthenticationRequiredException`。返回 `AuthenticatedUser(新令牌, account)`。

不开事务：没有数据库写。封禁并发：封禁的 `revokeAll` 和这里的 `rotate` 都是 Redis 原子脚本，谁后执行谁生效——封禁后执行的 `rotate` 找不到索引条目返回空（401）；`rotate` 后执行的封禁按索引里的新哈希删掉新会话。

响应 `200`，和登录同构：

```json
{ "sessionToken": "patra_user_…", "user": { "...": "同 /auth/me" } }
```

门户收到后换 Cookie 里的令牌（PAP-102）。再认证失败不动会话：旧令牌照常可用。

### 8.2 改密码 `PUT /me/password`

请求体 `{ "currentPassword", "newPassword" }`。网关：`/me/**` 需登录。`ChangePasswordHandler`：

1. `currentUserPort.require()`。
2. 两个字段一起校验、一次报全（422）：`currentPassword` 用 `PlainPassword.validate`；`newPassword` 用 `PasswordPolicy.validate(raw, NEW_PASSWORD)`（8 ~ 64、常见名单）。
3. 不加锁读用户，`requireActive()`：`PENDING_DELETION` 409、`BANNED` / `DELETED` 401。
4. 校验当前密码：同 8.1 第 4 ~ 6 步（共用登录失败限制，错是 401 + `PASSWORD_MISMATCH`，锁定 429）。
5. 新旧相同 → 422 `newPassword` / `UNCHANGED`。比较的是 NFKC 规范化后的原文（哈希前做的同一个规范化），不做第二次 Argon2。
6. `passwordHashing.hash(new)`（事务外）。
7. 事务里：`users.findByIdForUpdate` → `requireActive()` → `credentials.findByUserId`（不存在 → 401，这期间被撤销改邮箱删掉了）→ `credential.rotate(hash)`、`save` → `tokens.revokeAllActive(userId, now)` → `sessions.revokeAllExcept(USER, userId, sessionId)`，被删会话的记录 `end(REVOKED, now)` → 读账号信息 → **最后** `sessions.rotate(USER, userId, sessionId)`（空 → 401；评审修正：换令牌放事务最后一步，之前任何一步失败都不碰旧令牌，用户还能在原会话里重试；只剩提交本身失败这一种情况会丢令牌）。Redis 失败整个事务回滚、503：密码没改成。
8. 事务提交后：`emailDelivery.send(PasswordChangedMail)`。

响应 `200`，和登录同构（`sessionToken` 是新令牌）。

作废的是该用户全部有效令牌（撤销令牌除外）：release spec 2.1.7 的令牌行和 AC 第 5 条都写「改密码时按用户批量作废」，改密码那一行的「作废所有待用的重设令牌」是它的子集。代价是改密码前申请的改邮箱、重发的验证邮件要重新来一遍——改密码的人多半是觉得账号不对劲，把悬着的事全部清掉是对的。

`rotate` 也刷新 `auth_at`：输过当前密码就是一次再认证，改完密码紧接着改邮箱不用再输一遍。

### 8.3 找回密码：申请 `POST /auth/password/forgot`

请求体 `{ "email" }`。网关：公开。`RequestPasswordResetHandler`：

1. `EmailAddress.validate` → 422 `email` / `REQUIRED` 等。格式错误不是枚举信号。
2. `passwordResetThrottle.acquireRequest(email, clientIp, now)`：有值 → 429 `PasswordResetLimitedException`。**在查用户之前、对任何邮箱都计数**：只给已注册的计数，429 本身就成了「已注册」的信号。
3. `users.findByEmail(email)`：
   - 查到且 `ACTIVE` / `PENDING_DELETION`：事务里 `users.findByIdForUpdate` → `issuer.issue(PASSWORD_RESET, userId, email, clientIp)` → 读昵称；事务提交后 `send(PasswordResetMail)`。
   - 查不到（含 `DELETED`：邮箱为空查不到）或 `BANNED`：不发信。被封禁的账号重设了密码也登不进去，发信只是浪费配额；要不要告诉他「账号被封」是登录接口的事。为了耗时同量级，这条分支照样生成一次 `EmailTokenSecret` 并计算哈希，只是不写库、不发信。
4. 返回 `PasswordResetRequested(email, nextRequestAt = now + cooldown)`。

响应 `202`，两条分支完全一样：

```json
{ "email": "chen.yu@example.com", "nextRequestAt": "2026-10-22T08:01:00Z" }
```

耗时：两条分支的差别是两条本地 SQL（作废 + 插入，各不到一毫秒）；发信是异步的，不计入。残差远小于网络抖动，且同邮箱每天 5 次、同 IP 每小时 20 次的配额让按耗时枚举不可行。集成测试只断言两条分支的中位数在同一量级（第 15.2 节）。

不计入登录失败：这条路径不碰 `LoginThrottlePort`。

### 8.4 找回密码：重设 `POST /auth/password/reset`

请求体 `{ "token", "newPassword" }`。网关：公开。`ResetPasswordHandler`：

1. `passwordResetThrottle.acquireSubmit(clientIp, now)` → 429。先于一切：这是对猜令牌的限制，格式不对也计。
2. 字段一起校验（422）：`token` 为空 `REQUIRED`、格式不对 `INVALID`（不查库）；`newPassword` 按 `PasswordPolicy.validate(raw, NEW_PASSWORD)`。密码不合规时令牌不被消费，用户换个密码再提交同一个链接。
3. `passwordHashing.hash(new)`（事务外）。
4. 事务里：
   1. `tokens.findByTokenHash`：空或用途不是 `PASSWORD_RESET` → 422 `token` / `INVALID`。
   2. `users.findByIdForUpdate(token.userId)`：不存在、`BANNED`、`DELETED` → 422 `INVALID`（令牌签发后被封禁：封禁时 `revokeAllActive` 已经作废它，这里是兜底）；`PENDING_DELETION` 继续。
   3. `tokens.consume(hash, PASSWORD_RESET, now)`：空 → 422。
   4. 令牌的 `email` ≠ 用户当前邮箱（期间改邮箱生效了）→ 422。
   5. `credentials.findByUserId`：有 → `rotate(hash)`；没有（撤销改邮箱后）→ `UserPasswordCredential.create(userId, hash)`；`save`。
   6. `tokens.revokeAllActive(userId, now)`：其余重设令牌、旧的验证令牌、待确认的改邮箱全部作废。
   7. `user.markEmailVerified(now)`（幂等）、`users.save`。
   8. `sessions.revokeAll(USER, userId)`，记录 `end(REVOKED, now)`。
   9. `loginThrottle.clear(USER, email)`：清零失败计数、解锁。
5. 事务提交后：`send(PasswordResetDoneMail)`。
6. 返回 `PasswordReset(email)`。

响应 `200`：`{ "email": "chen.yu@example.com" }`。不自动登录，门户据此跳登录页并预填邮箱。

### 8.5 改邮箱：申请 `POST /me/email/change`

请求体 `{ "newEmail" }`。网关：`/me/**` 需登录。`RequestEmailChangeHandler`：

1. `currentUserPort.require()`。
2. `EmailAddress.validate(raw, NEW_EMAIL)` → 422；规范化后等于当前邮箱 → 422 `newEmail` / `UNCHANGED`。
3. 不加锁读用户，`requireActive()`（409 / 401）。
4. `reauthenticationGuard.requireRecent(current)` → 401 / 403 `REAUTH_REQUIRED`。
5. `tokens.findActive(userId, EMAIL_CHANGE_CONFIRM, now)` 有 → 409 `EmailChangePendingException`（`CONFLICT` + `EMAIL_CHANGE_PENDING`），`detail` 不含待确认的地址（门户用 `GET /me/email/change` 取）。
6. `users.existsByEmail(newEmail)`（别人 `ACTIVE` / `BANNED` / `PENDING_DELETION` 在用）→ 409 `UserIdentifierTakenException`（`errors[]`：`email` / `ALREADY_REGISTERED`），和注册、确认时被抢注同一个形状。放在限流之前：填了别人的地址不消耗配额。
7. `emailChangeThrottle.acquire(userId, now)` → 429 `EmailChangeLimitedException`。
8. 事务里：`users.findByIdForUpdate` → `requireActive()` → 再次 `findActive`（锁后重判，两次并发申请串行）→ `issuer.issue(EMAIL_CHANGE_CONFIRM, userId, newEmail, clientIp)`；事务提交后 `send(EmailChangeConfirmMail)` 到新邮箱。
9. 返回 `EmailChangeRequested(newEmail, expiresAt)`。

响应 `202`：

```json
{ "newEmail": "new@example.com", "expiresAt": "2026-10-22T09:00:00Z" }
```

被占用直接 409 而不是按 release spec 原来的「也回已发送」伪装（2026-10-22 拍板）：注册接口本来就会回 `email` / `ALREADY_REGISTERED`，改邮箱这道伪装只堵「用改邮箱接口当探针」这一条路，代价却是用户填了别人的地址后会看到一个永远等不到邮件的「待确认」；builder check 还指出只在响应上伪装会被 `GET` 与第二次申请的 409 漏出来。

### 8.6 改邮箱：查看与取消 `GET` / `DELETE /me/email/change`

`GET`：`UserQueryService.pendingEmailChange()` → `currentUserPort.require()` → `tokens.findActive(userId, EMAIL_CHANGE_CONFIRM, now)`：有 → `200 { "newEmail", "expiresAt" }`；没有 → `204`。不要求再认证（只读自己的状态）。

`DELETE`：`CancelEmailChangeHandler`，`@Transactional`：`require()` → `users.findByIdForUpdate` → `requireActive()` → `tokens.revokeActive(userId, EMAIL_CHANGE_CONFIRM, now)` → `204`，没有待确认也 204（幂等）。不要求再认证：取消是把门关上，不是打开。

### 8.7 改邮箱：确认 `POST /auth/email/change/confirm`

请求体 `{ "token" }`。网关：公开（新邮箱在哪台设备点都行）。`ConfirmEmailChangeHandler`，`@Transactional`：

1. `token` 空 / 格式错 → 422。
2. `tokens.findByTokenHash`：空或用途不是 `EMAIL_CHANGE_CONFIRM` → 422。
3. `users.findByIdForUpdate`：不存在或非 `ACTIVE` → 422（`PENDING_DELETION` 也拒：注销中不做写操作；令牌一小时内过期，取消注销后重新申请）。
4. `tokens.consume(hash, EMAIL_CHANGE_CONFIRM, now)`：空 → 422。
5. `users.existsByEmail(newEmail)`（一小时内被别人注册了）→ 409 `UserIdentifierTakenException`（`errors[]`：`email` / `ALREADY_REGISTERED`）。点链接的人就是新地址的主人，告诉他「这个地址已被注册」不泄露别人的信息。`users.save` 撞唯一约束同样是这个 409。
6. `oldEmail = user.getEmail()`；`user.changeEmail(newEmail, now)`；`users.save`。
7. `tokens.revokeAllActive(userId, now)`：发给旧地址的验证令牌、重设令牌全废。
8. `issuer.issue(EMAIL_CHANGE_REVERT, userId, oldEmail, clientIp)`（不作废已有的撤销令牌）。
9. 事务提交后：`send(EmailChangedNoticeMail)` 到旧邮箱（含撤销链接）；`send(EmailChangedConfirmationMail)` 到新邮箱。
10. 返回 `EmailChanged(newEmail, emailVerifiedAt)`。

响应 `200`：`{ "email": "new@example.com", "emailVerifiedAt": "…" }`。

会话不动：申请时本人刚再认证过，确认的是他自己发起的事。登录限流键按邮箱哈希，新邮箱的计数从零开始。

### 8.8 改邮箱：撤销 `POST /auth/email/change/revert`

请求体 `{ "token" }`。网关：公开。`RevertEmailChangeHandler`，`@Transactional`：

1. `token` 空 / 格式错 → 422。
2. `tokens.findByTokenHash`：空或用途不是 `EMAIL_CHANGE_REVERT` → 422。
3. `users.findByIdForUpdate`：不存在、`BANNED`、`DELETED` → 422；`ACTIVE`、`PENDING_DELETION` 继续——攻击者拿到账号后申请注销是很自然的事，原主必须能撤回邮箱；撤回不碰注销状态（第 20 节待拍板）。
4. `tokens.consume(hash, EMAIL_CHANGE_REVERT, now)`：空 → 422。
5. `restore = token.email`。`users.existsByEmail(restore)`（7 天内被别人注册了）→ 409 `email` / `ALREADY_REGISTERED`，事务回滚，令牌没被消费、链接还能用；用户只能找管理员。极端情况，本版不做更多。
6. `user.changeEmail(restore, now)`；`users.save`（撞唯一约束同上 409）。
7. `tokens.revokeAllActive(userId, now)` + `tokens.revokeRevertTokensIssuedAfter(userId, consumed.id, now)`：只作废**比本条更晚签发**的撤销链接，更早的保留（评审修正：原来的「其余全废」让攻击者 X → Y → Z 后先点 Y 的链接就能废掉原主 X 的，7 天保护形同虚设）。越早的地址越可信：原主先点 X 的，Y 的随之作废；攻击者先点 Y 的，X 的仍然活着，原主随后点它照样胜出。
8. `credentials.deleteByUserId(userId)`：作废密码。
9. `sessions.revokeAll(USER, userId)`，记录 `end(REVOKED, now)`。
10. `issuer.issue(PASSWORD_RESET, userId, restore, clientIp)`。
11. 事务提交后：`send(EmailChangeRevertedMail)` 到 `restore`（含重设链接）。
12. 返回 `EmailChangeReverted(email = restore)`。

响应 `200`：`{ "email": "old@example.com" }`。

不通知被踢掉的新地址：那是攻击者的地址，或是用户自己改错的地址（他本人在旧邮箱里点的「不是我」）。

### 8.9 处理器内的统一顺序

写处理器按这个顺序排检查，错误码从便宜到贵：

```
登录（401）→ 字段（422）→ 状态（409 / 401）→ 再认证（403）→ 唯一性 / 待确认（409）→ 限流（429）→ 事务（锁用户行 → 重判 → 写）→ 提交后发信
```

公开的令牌消费接口：

```
提交限流（429，只有重设）→ 字段（422）→ 事务（查令牌 → 锁用户 → 判状态 → 消费 → 写）→ 提交后发信
```

## 9. 邮件

### 9.1 清单

| 模板 | 收件人 | 主题 | 类型 | 变量 | `notMeAdvice` |
|---|---|---|---|---|---|
| `password-reset` | 申请的邮箱 | 重设你的 Patra 密码 | 带链接，不是安全通知 | `displayName`、`resetUrl`、`validMinutes`（60） | —（正文：「如果不是你申请的，忽略即可，密码不会变」） |
| `password-changed` | 当前邮箱 | 你的 Patra 密码已修改 | 安全通知 | `displayName`、`notice` | 「如果不是你，你的邮箱可能已被盗：请先找回邮箱，再用『忘记密码』重设密码」 |
| `password-reset-done` | 当前邮箱 | 你的 Patra 密码已重设 | 安全通知 | `displayName`、`notice` | 同上 |
| `email-change-confirm` | 新邮箱 | 确认你的新 Patra 邮箱 | 带链接 | `displayName`、`confirmUrl`、`validMinutes`（60） | —（正文：「如果不是你申请的，忽略即可，邮箱不会变」） |
| `email-changed-notice` | 旧邮箱 | 你的 Patra 邮箱已更改 | 安全通知 + 链接 | `displayName`、`newEmailMasked`、`revertUrl`、`validDays`（7）、`notice` | 「如果不是你，请点下面的链接撤销：邮箱会改回本地址，所有设备下线，并给你发重设密码的链接」 |
| `email-changed-confirmation` | 新邮箱 | 本邮箱已绑定你的 Patra 账号 | 安全通知 | `displayName`、`notice` | 「如果不是你，请联系旧邮箱的主人或忽略」 |
| `email-change-reverted` | 改回的旧邮箱 | 你的 Patra 邮箱已改回，请重设密码 | 安全通知 + 链接 | `displayName`、`resetUrl`、`validMinutes`（60）、`notice` | 「账号已锁定：所有设备已下线，原密码已作废，请用下面的链接设置新密码」 |

每个模板一个实现 `OutgoingMail` 的记录（≤ 4 个字段用 `of()`，≥ 5 个用 `@Builder`），`html/<模板>.html` 与 `text/<模板>.txt` 各一份，安全通知的正文里 `th:replace="~{html/fragments/security-notice :: notice(${notice})}"`（文本模板整文件 `th:replace`，PAP-96 第 16 节第 5 条）。

`newEmailMasked`：`c***@example.com`（本地部分留首字符）——通知旧邮箱时不把新地址完整写出来，万一通知落到了别人手里。

### 9.2 `SecurityNotice` 的内容与片段的固定句

`occurredAt = now`；`device`、`ipAddress`、`ipLocation` 本 Issue 都传 `null`（显示「未知」）：今天 `getRemoteAddr()` 是门户容器的地址，写进邮件会误导用户；PAP-99 接通受信转发后改为传真实 IP 与属地。`requested_ip` 列照样存（审计）。

PAP-96 第 9.4 节让 `security-notice` 片段固定追加「建议立即到安全设置修改密码并检查登录设备：{securityUrl}」。这句话对本 Issue 的两封邮件不成立：「已改回，请重设密码」的收件人密码已作废、登不进去；「密码已重设」的收件人刚把其他设备全踢了，再让他去检查是废话。改法：片段只输出时间、设备、IP、`notMeAdvice` 四项；那句话挪进需要它的模板（`password-changed`、`email-changed-notice`、`email-changed-confirmation`）的正文里，`securityUrl` 变量照旧由渲染器提供。`SecurityNotice` 记录不变。

### 9.3 发信时机与失败

全部在事务提交之后、由 `EmailDeliveryPort` 异步发出；发信失败只记日志（PAP-96 第 9.1 节）。改邮箱确认那一步两封邮件先旧后新。

### 9.4 链接

`MailLinkPolicy` 加三个路径，默认值 `/reset-password`、`/confirm-email-change`、`/revert-email-change`，格式同验证邮件：`{base-url}{路径}#token=<43 字符>`。PAP-102 做三个落地页。

## 10. 限流

### 10.1 维度与键

| 操作 | 维度 | 规则 | 键 |
|---|---|---|---|
| 找回密码：申请 | 冷却 | 同邮箱 60 秒 | `idn:pw-reset:cooldown:email:{h(email)}` |
| | 窗口 | 同邮箱每天 ≤ 5 | `idn:pw-reset:day:email:{h(email)}` |
| | 窗口 | 同 IP 每小时 ≤ 20 | `idn:pw-reset:hour:ip:{h(ip)}` |
| 找回密码：提交 | 窗口 | 同 IP 每分钟 ≤ 10 | `idn:pw-reset-submit:minute:ip:{h(ip)}` |
| 改邮箱：申请 | 窗口 | 同账号每天 ≤ 3 | `idn:email-change:day:user:{userId}` |
| 重发验证邮件（PAP-96） | 不变 | 不变 | 不变 |

`h(x)` = SHA-256 十六进制前 32 位（邮箱、IP 都哈希，PAP-96 第 10.1 节）。IP 未知时跳过 IP 维度。

**PAP-99 之前的 IP 维度**：受信转发接通前 `getRemoteAddr()` 是门户容器的地址，找回密码的「同 IP 每小时 20 次」实际是全站每小时 20 次、提交的「每分钟 10 次」是全站每分钟 10 次。PAP-99 和本 Issue 在同一个版本、同一个 PR 里合入，上线时 IP 已经是真实地址；合入前的开发期只有一两个人在用，够。不为这段时间加开关、不调大阈值。

### 10.2 通用脚本 `action-throttle.lua`

PAP-96 的 `email-resend-throttle.lua` 写死「冷却 + 小时 + 天 + IP 小时」四个键；本 Issue 要的组合各不相同，改成参数化：

```
KEYS[1]        冷却键（ARGV[2] 为 0 时不用）
KEYS[2..n]     窗口键，按顺序
ARGV[1] now 毫秒  ARGV[2] 冷却毫秒（0 = 没有冷却）  ARGV[3] 1 判且计 / 0 只计  ARGV[4] 成员后缀
ARGV[5], ARGV[6]   第 1 个窗口的毫秒数、上限
ARGV[7], ARGV[8]   第 2 个窗口 …（窗口数 = (#ARGV − 4) / 2，必须等于 #KEYS − 1）
```

逻辑和 PAP-96 第 10.2 节一样（先判冷却与每个窗口、取最长等待，放行后一次计数），返回值永远是数字。infra 里一个 `ActionThrottleScript` 类封装「拼参数、执行、把暂时失败转 503、`null` 结果视为拒绝」，四个适配器只负责算键和参数。`EmailResendThrottleAdapter` 改到它上面，键名和行为不变（`EmailResendThrottleAdapterIT` 全部照旧通过）。

### 10.3 异常

| 异常 | 状态 | `detail` |
|---|---|---|
| `PasswordResetLimitedException(Duration)` | 429 `QUOTA_EXCEEDED` + `Retry-After` | 操作过于频繁，请稍后再试 |
| `EmailChangeLimitedException(Duration)` | 429 同上 | 今天更改邮箱的次数已用完，请明天再试 |
| `EmailResendLimitedException` | 不变 | 不变 |

## 11. 网关路由规则

| 路径 | 规则 | 改动 |
|---|---|---|
| `/patra-identity/auth/password/**` | 公开 | 加进 `IDENTITY_PUBLIC_PATHS`。集成测试：匿名 POST `forgot`、`reset` 被转发且不带 `Authorization` |
| `/patra-identity/auth/email/change/**` | 公开（照样翻译令牌） | 加进名单。集成测试：匿名 POST `confirm`、`revert` 被转发 |
| `/patra-identity/auth/reauth` | 需登录 | 默认规则已覆盖。集成测试：匿名 401 且下游没收到；带会话被转发且带断言 |
| `/patra-identity/me/password`、`/me/email/change` | 需登录 | 默认规则已覆盖。集成测试同上（各一条） |

`/auth/email/change/**` 公开但 `/me/email/**` 需登录：两组路径不重叠，名单用 `**` 不会误放 `/me/`。

## 12. 错误契约

前缀 `IDN`。新增和改动：

| 场景 | 异常 | 特征 | 状态 | 额外信息 | `detail` |
|---|---|---|---|---|---|
| 再认证超时 | `ReauthenticationRequiredException`（新） | `FORBIDDEN` + `REAUTH_REQUIRED` | 403 | `traits` 含 `REAUTH_REQUIRED` | 请重新输入密码以继续 |
| 当前密码错误（再认证、改密码） | `CurrentPasswordMismatchException`（新） | `UNAUTHORIZED` + `PASSWORD_MISMATCH` | 401 | `traits` 含 `PASSWORD_MISMATCH` | 密码不正确 |
| 再认证 / 改密码时登录限制锁定 | `LoginTemporarilyLockedException`（已有） | `QUOTA_EXCEEDED` | 429 | `Retry-After` | 尝试次数过多，请稍后再试 |
| 已有待确认的改邮箱 | `EmailChangePendingException`（新） | `CONFLICT` + `EMAIL_CHANGE_PENDING` | 409 | `traits` 含 `EMAIL_CHANGE_PENDING` | 已有一个待确认的邮箱更改，请先取消或等它过期 |
| 找回密码 / 提交重设 / 改邮箱过于频繁 | 第 10.3 节的两个异常 | `QUOTA_EXCEEDED` | 429 | `Retry-After`、`retryAfterSeconds` | 见第 10.3 节 |
| 新密码与当前相同 | `InvalidUserFieldsException` | `RULE_VIOLATION` | 422 | `errors[]`：`newPassword` / `UNCHANGED` | 请求参数不合法 |
| 新密码不合规 | 同上 | 同上 | 422 | `newPassword` / `TOO_SHORT`、`TOO_LONG`、`TOO_COMMON`、`REQUIRED`、`INVALID_CHARACTER` | 同上 |
| 当前密码为空 | 同上 | 同上 | 422 | `currentPassword` / `REQUIRED` | 同上 |
| 新邮箱不合法 / 与当前相同 | 同上 | 同上 | 422 | `newEmail` / `REQUIRED`、`TOO_LONG`、`INVALID_FORMAT`、`UNCHANGED` | 同上 |
| 令牌为空 / 不可用（重设、确认、撤销） | 同上 | 同上 | 422 | `token` / `REQUIRED` 或 `INVALID` | 同上 |
| 确认 / 撤销时地址已被别人注册 | `UserIdentifierTakenException`（已有） | `CONFLICT` | 409 | `errors[]`：`email` / `ALREADY_REGISTERED` | 邮箱或用户名已被使用 |
| 再认证 / 改密码时会话已不在 | `AuthenticationRequiredException` | `UNAUTHORIZED` | 401 | — | Authentication required |

**为什么密码错是 401 还要带 `PASSWORD_MISMATCH`**：门户对 401 的通用处理是「会话失效 → 登出」。再认证和改密码的 401 发生在已登录的接口上，门户不能把它当会话失效。Issue 的 AC 要求 401，就用 `traits` 区分（和 PAP-93 的 409 原因码同一机制）。

**为什么再认证超时是 403 而不是 401**：用户是登录着的，缺的不是会话而是一道额外的证明；401 会被门户当成登出。

## 13. 配置

jar 的 `application.yml` 加：

```yaml
patra:
  identity:
    reauth:
      window: 10m                      # 再认证窗口：敏感操作要求这么久内输过密码
    password-reset:
      cooldown: 60s                    # 同邮箱两次申请的最短间隔
      per-email-daily: 5
      per-ip-hourly: 20
      submit-per-ip-minutely: 10       # 提交重设令牌
    email-change:
      per-account-daily: 3
    mail:
      links:
        reset-password-path: /reset-password
        confirm-email-change-path: /confirm-email-change
        revert-email-change-path: /revert-email-change
```

`IdentityProperties` 加 `Reauth(window)`、`PasswordReset(cooldown, perEmailDaily, perIpHourly, submitPerIpMinutely)`、`EmailChange(perAccountDaily)`，`Links` 加三个路径，都给默认值；`IdentityConfiguration` 把它们变成三个策略对象（非正数启动失败），装配 `EmailTokenIssuer`（替代 `emailVerificationIssuer` Bean）、`ReauthenticationGuard`。Nacos 不加键：这些都随代码变，不随环境变。

## 14. 决策 K：密码规则与 NIST SP 800-63B-4 的偏离（按 ASVS 7.1.1 记录）

| 项 | 内容 |
|---|---|
| 偏离的要求 | NIST SP 800-63B-4（2025-07）§3.1.1.2：单因子认证的口令至少 15 个字符；多因子下至少 8 个 |
| Patra 的规则 | 8 ~ 64 个码点（按码点计，全 Unicode 可用，NFKC 后哈希）；拒绝常见密码名单；不强制字符类别、不定期更换、不存历史密码 |
| 满足的标准 | OWASP ASVS 5.0 V6.2.1（≥ 8 字符）、V6.2.2（≥ 64 字符、不截断）、V6.2.3（不强制字符类别）、V6.2.4（常见密码名单）、V6.2.8（不定期更换）；NIST 的其余要求（不做提示、不做安全问题、允许粘贴、Argon2id）都满足 |
| 偏离的理由 | 注册的门口不拦人：Patra 的用户是科研人员和临床医生，15 位的最短长度会在注册页流失用户；账号里没有支付信息和病历 |
| 补偿控制 | 常见密码名单（线上同一份）；Argon2id 哈希；登录失败限制（按邮箱计数、锁定）；改密码与再认证共用失败限制；敏感操作再认证（10 分钟窗口）；改密码 / 重设密码撤销其他会话并发通知邮件；改邮箱 7 天可撤销；找回密码四维度限流 |
| 风险 | 8 ~ 14 位且不在名单里的密码在离线破解下弱于 15 位。线上破解被失败限制挡住；离线破解要先拿到库，Argon2id 的参数（PAP-63）按 OWASP 推荐值 |
| 复核时机 | 做多因子（边界外）或接入第三方登录（v0.13+）时重新评估；用户群扩大到有付费 / 实名时重新评估 |
| 记录位置 | 本节；release spec 决策 K 指向这里 |

## 15. 测试策略

全部时间走 `Clock`（`MutableClock`）；Redis 容器、PG 容器、GreenMail 照 PAP-96。端到端测试用裸 JDK `HttpClient`（`RestTestClient` 会按 `Retry-After` 自动重试 429）；断言用测试时钟签。

### 15.1 单元测试（不起 Spring）

- `User`：`changeEmail` 换地址并写验证时间；`DELETED` 抛。
- `UserPasswordCredential.rotate`。
- `ReauthenticationPolicy`：窗口边界（`authenticatedAt + window` 当刻不算最近）；非正数抛。`PasswordResetPolicy`、`EmailChangePolicy`：非正数抛。
- `MailLinkPolicy`：三个新链接的格式；路径不以 `/` 开头抛。
- `EmailTokenIssuer`：`EMAIL_VERIFY` / `PASSWORD_RESET` / `EMAIL_CHANGE_CONFIRM` 先作废后保存；`EMAIL_CHANGE_REVERT` 不作废；保存的聚合只有哈希；返回的原文哈希等于保存的哈希。
- `ReauthenticationGuard`：会话不在 401；超窗 403 且 `traits` 含 `REAUTH_REQUIRED`；窗内通过；`EPOCH` 视作超窗。
- 七个邮件记录：`template()`、`subject()`、`model()` 的键；`toString` 不含收件人与链接；`newEmailMasked` 的遮掩。
- `ThymeleafMailRenderer`：七个模板两部分都渲染、含链接 / 片段四项、无残留 `th:`、昵称转义；片段不再输出安全设置链接，`email-change-reverted` 两部分都不含 `securityUrl`。
- 八个处理器（Mockito，`TransactionOperations` 桩同步执行）：
  - `ReauthenticateUserHandler`：422；封禁 / 注销 401；锁定 429 不查凭据；密码错 `recordFailure` + 401 `PASSWORD_MISMATCH`；凭据不存在走假哈希后 401；成功 `recordSuccess` + `rotate`；`rotate` 空 401；中途异常 `cancel`。
  - `ChangePasswordHandler`：两字段一起报；注销中 409；密码错 401；新旧相同 422 且不哈希；成功顺序（锁 → 换哈希 → `revokeAllActive` → rotate → revokeAllExcept → 记录 REVOKED）；发信在事务后。
  - `RequestPasswordResetHandler`：格式 422 不限流；限流 429 不查用户；未注册 / 封禁不签发不发信但返回同样的结果；注册用户签发 + 发信；不碰 `LoginThrottlePort`。
  - `ResetPasswordHandler`：提交限流先于一切；密码不合规时令牌不消费；令牌用途不符 422；封禁 422；邮箱不符 422；成功顺序（换哈希或新建 → `revokeAllActive` → `markEmailVerified` → `revokeAll` → `clear`）；发信在事务后。
  - `RequestEmailChangeHandler`：顺序（字段 → 状态 → 再认证 → 待确认 → 占用 → 限流）；与当前相同 422；超窗 403 不限流；待确认 409 不限流；被占用 409 `email` / `ALREADY_REGISTERED` 不限流、不签发；成功签发 + 发到新邮箱。
  - `CancelEmailChangeHandler`：有无待确认都 204。
  - `ConfirmEmailChangeHandler`：非 `ACTIVE` 422；被抢注 409；成功顺序（改邮箱 + 验证时间 → `revokeAllActive` → 签发撤销令牌）；两封邮件先旧后新；会话不动。
  - `RevertEmailChangeHandler`：`PENDING_DELETION` 可撤；被抢注 409 且令牌未消费；成功顺序（改回 → 作废全部含撤销 → 删凭据 → `revokeAll` → 签发重设）；发到改回的地址。
- `UserQueryService.pendingEmailChange`：有 / 无。
- `IdentityConfiguration`：三组配置非正数启动失败。
- `ActionThrottleScript`：参数拼装（窗口数与键数一致）、`null` 结果拒绝、暂时失败转 503。

### 15.2 集成测试

session（Redis 容器）：

- `RedisSessionStoreIT`：`create` 写 `auth_at = created_at`；`rotate` 返回新令牌、旧令牌 `findAndTouch` 为空、新令牌能查到且 `session_id` / `created_at` / `expires_at` / `device_id` 不变、`auth_at` 为 `now`、索引指向新哈希；`rotate` 对不存在 / 过期的会话为空且清掉索引条目；`find` 不改 `last_active_at`；没有 `auth_at` 的旧格式会话读出 `EPOCH`。

infra：

- `EmailTokenRepositoryAdapterIT`：`findActive` 只返回未消费、未作废、未过期的；`findActive` 对撤销令牌不用。
- `UserPasswordCredentialRepositoryAdapterIT`：`deleteByUserId` 删行并返回真；没有行返回假。
- `PasswordResetThrottleAdapterIT`（Redis 容器，时间直接传）：冷却 60 秒；同邮箱第 6 次 / 天；同 IP 第 21 次 / 小时；提交同 IP 第 11 次 / 分钟；IP 为空跳过；多维度取最长。
- `EmailChangeThrottleAdapterIT`：第 4 次 / 天 429。
- `EmailResendThrottleAdapterIT`：不改用例，改到通用脚本后照样绿。
- `LoginThrottleAdapterIT`：`clear` 后锁消失、失败计数归零。

adapter 切片（`@WebMvcTest`）：

- `AuthControllerIT`：`POST /auth/reauth` 200 的字段；401 的 `traits` 含 `PASSWORD_MISMATCH`；403 含 `REAUTH_REQUIRED`（用改邮箱接口）。
- `MeControllerIT`：`PUT /me/password` 200；422 `errors[]` 两条；401 `PASSWORD_MISMATCH`；429 `Retry-After`。
- `MeEmailControllerIT`：`POST /me/email/change` 202；403；409 `EMAIL_CHANGE_PENDING`；429；`GET` 200 / 204；`DELETE` 204。
- `PasswordResetControllerIT`：`forgot` 202、422、429；`reset` 200、422 `token` / `newPassword`、429。
- `EmailChangeControllerIT`：`confirm` 200、422、409；`revert` 200、422、409。

boot 端到端（PG + Redis + GreenMail，`RANDOM_PORT`，`MutableClock`）：

- `ReauthenticationFlowIT`：注册 → 立刻改邮箱申请通过（`auth_at = created_at`）→ 拨 10 分钟 → 403 `REAUTH_REQUIRED` → 错密码 `reauth` 401 `PASSWORD_MISMATCH`，连错到上限 429 且登录接口同样 429（共用限制）→ 拨过锁定 → 对密码 `reauth` 200，响应有新令牌、旧令牌下一个请求 401、`/auth/me` 的会话 ID 不变（登录记录条数不变）→ 改邮箱申请通过（AC 1）。
- `PasswordChangeFlowIT`：两个会话 → 错当前密码 401 → 新旧相同 422 → 改密码 200：本机拿到新令牌且旧令牌 401、另一会话 401 且记录 `REVOKED`、本机用新令牌 `/auth/me` 200 → GreenMail 收到「密码已修改」（两部分，含安全通知四项）→ 用新密码登录 200、旧密码 401（AC 2）；改密码前申请找回密码签的令牌在改密码后 422（AC 5）。
- `PasswordResetFlowIT`：已注册与未注册邮箱各 10 次 `forgot`（拨时钟绕冷却）→ 都 202、响应体结构一致、GreenMail 只收到已注册的、两组耗时中位数之比 < 10（AC 3）；令牌重设 200 → 全部会话 401、记录 `REVOKED`、`email_verified_at` 已写、登录失败计数清零（先连错两次再重设，重设后仍可登录且计数为 0）、待确认的改邮箱令牌被作废、同一令牌再提交 422 → 收到「密码已重设」→ 用新密码登录（AC 3、5）；拨 61 分钟后令牌 422；提交 11 次 429。
- `EmailChangeFlowIT`：再认证 → 申请：新邮箱收到确认邮件、`GET /me/email/change` 200、登录仍只认旧邮箱 → 第二次申请 409 → 确认 200：`/auth/me` 是新邮箱且已验证、新邮箱能登录、旧邮箱登录 401、旧邮箱收到含撤销链接的通知、新邮箱收到「已绑定」→ 拨 6 天 → 撤销 200：`/auth/me`（任一会话）401、邮箱改回、旧密码登录 401（凭据已删）、改回的邮箱收到含重设链接的邮件、重设后能登录（AC 4、5）；拨 8 天后撤销链接 422；X → Y → Z 两条撤销链接并存，点 X 的后 Y 的 422；被占用的新邮箱申请 409 `errors[0].field == email`、不发信、`GET` 204；每天第 4 次申请 429；取消后 `GET` 204；改密码后待确认的改邮箱消失（`GET` 204）。
- 四种限流（找回密码同邮箱、找回密码同 IP、提交重设同 IP、改邮箱同账号）各自触发 429 并带 `Retry-After`（AC 6）：同邮箱与同账号在流程测试里，两个 IP 维度在 `PasswordResetThrottleAdapterIT` 里（流程测试里所有请求同一个 IP，走到第 21 次之前先撞同邮箱的天配额）。

网关（`GatewayAuthenticationIT`）：第 11 节的用例。

### 15.3 回归

`./gradlew spotlessApply`，`./gradlew check`，identity 有 `src/integrationTest` 的模块（infra、adapter、boot、session）与 gateway 的 `integrationTest`。`build.gradle.kts` 不改，模块图不变。

### 15.4 本机手工验收

照 PAP-96 第 16 节的做法（本机 PG + Redis + Mailpit，identity 与 gateway 用 jar 起）：注册 → 拨不了时钟就等 10 分钟或把 `patra.identity.reauth.window` 临时设成 `30s` → 走一遍四个流程，看 Mailpit 里的七封邮件和链接。

### 15.5 AC 对应

| AC | 证据 |
|---|---|
| 再认证：10 分钟内通过、超时 403 `REAUTH_REQUIRED`、`/auth/reauth` 后恢复；密码错计入登录失败限制（固定时钟） | `ReauthenticationFlowIT`、`ReauthenticationGuardTest`、`ReauthenticateUserHandlerTest` |
| 改密码：当前密码错 401；新旧相同 422；成功后其他会话 401、本机仍登录且令牌已换；收到邮件 | `PasswordChangeFlowIT`、`ChangePasswordHandlerTest` |
| 找回密码：已注册与未注册都 202、未注册不发信、耗时同量级；重设后全部会话 401、全部令牌失效、失败计数清零、`email_verified_at` 已写；收到邮件 | `PasswordResetFlowIT`、两个处理器的单测 |
| 改邮箱：确认前登录标识仍是旧邮箱；确认后新邮箱可登录、旧邮箱收到含撤销链接的通知；7 天内撤销后邮箱改回、全部会话 401、旧密码不可用并收到重设邮件；第二个申请被拒；新邮箱已占用回 409（偏离 AC 原文的「仍回已发送」，2026-10-22 拍板） | `EmailChangeFlowIT`、四个处理器的单测 |
| 改密码 / 重设 / 改邮箱生效时按用户批量作废令牌（集成测试） | `PasswordChangeFlowIT`（AC 5 步）、`PasswordResetFlowIT`、`EmailChangeFlowIT`：各自断言 `idn_email_token` 里其他令牌的 `revoked_at` |
| 四种限流（找回密码同邮箱、找回密码同 IP、提交重设同 IP、改邮箱同账号）各自 429 并带 `Retry-After` | `PasswordResetThrottleAdapterIT`、`EmailChangeThrottleAdapterIT`、`PasswordResetFlowIT`、`EmailChangeFlowIT` |
| `./gradlew check` + `integrationTest` 全绿 | 第 15.3 节命令输出，写回第 16 节末尾 |

## 16. 实施时要实测的点

| # | 要实测的 | 不成立时的替代 | 实施结果 |
|---|---|---|---|
| 1 | Lua 里 `HSET` 一次写入 `HGETALL` 拿到的扁平数组（`unpack(fields)`）：字段数 ≤ Lua 的 `unpack` 上限（默认 8000），当前 10 个字段没问题 | 循环 `HSET` | 成立：`session-rotate.lua` 用 `unpack`，`RedisSessionStoreIT` 23 个用例全绿 |
| 2 | `rotate` 返回 `HGETALL` 形式的字段后，Java 侧复用 `toStoredSession`；新令牌由 Java 生成、脚本只收哈希，脚本返回值不含令牌 | — | 成立；`rotate` 只返回 `Optional<SessionToken>`，字段不用再转 |
| 3 | `LoginThrottleAdapter.clear` 只 `DEL` 失败计数与锁；`login-throttle-begin.lua` 对缺失的键按 0 处理（PAP-63 已如此） | 写一段 `clear` 脚本 | 成立：`LoginThrottleAdapterIT.should_clear_failures_and_lock_but_keep_in_flight_entries` |
| 4 | `PasswordPolicy.validate(raw, field)` 改签名后 `RegisterUserHandlerTest`、`AccountFlowIT` 的 `errors[].field` 仍是 `password` | — | 成立：注册传 `UserFieldViolations.PASSWORD`，现有用例未改照样绿 |
| 5 | `ActionThrottleScript` 参数化后 `EmailResendThrottleAdapterIT` 全绿、键名不变 | 保留旧脚本给重发单独用 | 成立：9 个用例未改全绿，`email-resend-throttle.lua` 已删 |
| 6 | `RestTestClient`（切片测试）对 `PUT` 带体正常；`DELETE /me/email/change` 无体 204 | — | 成立：`MeControllerIT`、`MeEmailControllerIT` |
| 7 | 耗时对比用例在 CI（核数少）稳定：10 次中位数之比 < 10 | 放宽到 < 20 或只断言结构一致 | 本机成立（5 + 5 次，比值远小于 10）；CI 上要看本 PR 的运行结果 |
| 8 | `UserIdentifierTakenException` 在确认 / 撤销接口上的 409 体里 `errors[0].field` 是 `email` | — | 成立：`EmailChangeControllerIT`、`EmailChangeFlowIT` |
| 9 | 改密码事务里 `rotate` 后 `revokeAllExcept(keep = sessionId)`：索引已指向新哈希，保留的是新会话 | 调换顺序：先 `revokeAllExcept` 再 `rotate` | 成立：`PasswordChangeFlowIT` 断言新令牌的键还在、另一会话的键没了 |
| 10 | GreenMail 对同一事务后连发两封（确认改邮箱）都收到且顺序先旧后新 | 只断言集合 | 两封都收到；发信走 4 条虚拟线程，到达顺序不保证，流程测试按收件人 + 主题找邮件，不按顺序 |

**回归结果（2026-10-22，本机）**：`./gradlew spotlessApply` 后 `./gradlew check`——identity 六个模块与 gateway 的单测全绿（domain 288、app 148、infra 45、adapter 4、boot 20、session 25、gateway 35）；`patra-tool-jpa`、`patra-tool-batch` 各一条 `ApplicationContextRunner` 用例在 `check` 并行跑满机器时 15 秒超时，单独重跑通过，与本 Issue 无关（没动这两个模块）。`integrationTest`：session 23、infra 96、adapter 57、boot 48（含四个账号安全流程 12 个用例）全绿；gateway 53 个用例里 `GatewayFailureIT` 的「慢客户端大文件下载」计时用例在同一轮负载下失败一次，单独重跑通过。`build.gradle.kts` 没改，模块图不变。本机没有起 jar 做手工验收（第 15.4 节），端到端由四个流程测试覆盖；mini 上的 D4 随后端 PR 部署时做。

## 17. README

`patra-api/patra-identity/README.md`：

1. 第 1 节模块表：domain 加三个策略、两个限流端口、`EmailTokenIssuer`、`ReauthenticationGuard`、七个邮件记录；app 加八个处理器；infra 加通用限流脚本与两个适配器、七对模板；adapter 加两个控制器。
2. 第 2 节接口表：加九条路径（`POST /auth/reauth`、`PUT /me/password`、`POST /auth/password/forgot`、`POST /auth/password/reset`、`POST` / `GET` / `DELETE /me/email/change`、`POST /auth/email/change/confirm`、`POST /auth/email/change/revert`），标网关规则。
3. 新增「账号安全」一节：再认证机制（`auth_at`、窗口、守卫、哪些操作要过）、改密码、找回密码、改邮箱的流程与时序、令牌用途表、邮件清单、限流表、第 7.5 节的作废 / 撤销矩阵。
4. 第 7 节错误码表：第 12 节的新行。
5. 第 8 节本地运行：`reauth.window` 临时调短的提示。
6. 决策 K 的偏离说明指向本 spec 第 14 节。

`patra-identity-session/README.md`：会话字段加 `auth_at`；操作表加 `rotate`、`find`。

## 18. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| 踢设备（单个 / 其他全部）先 `reauthenticationGuard.requireRecent(current)`；`SecurityNotice` 的 `device` / `ipAddress` / `ipLocation` 由 Yauaa / ip2region 填后，本 Issue 的四封安全通知把 `null` 换成真实值（`ChangePasswordHandler`、`ResetPasswordHandler`、`ConfirmEmailChangeHandler`、`RevertEmailChangeHandler` 各一处） | PAP-99 |
| 注销申请先过再认证守卫；撤销改邮箱对 `PENDING_DELETION` 用户放行且不碰注销状态（要不要顺带取消注销见第 20 节）；注销时 `revokeActive(EMAIL_CHANGE_REVERT)` 的约定不变 | PAP-100 |
| 门户：403 且 `traits` 含 `REAUTH_REQUIRED` → 弹密码框 → `POST /auth/reauth` → 换 Cookie 里的令牌 → 重试原请求；`PUT /me/password` 200 同样换令牌；401 且 `traits` 含 `PASSWORD_MISMATCH` 是密码错、不是登出；三个落地页 `/reset-password`（`POST /auth/password/reset`，成功跳登录页预填 `email`）、`/confirm-email-change`（`POST /auth/email/change/confirm`，409 显示「地址已被注册」）、`/revert-email-change`（`POST /auth/email/change/revert`，成功提示「已改回，请查收重设密码邮件」）；安全设置页用 `GET /me/email/change` 显示待确认状态与「取消」；找回密码页 429 读 `retryAfterSeconds` | PAP-102 |

## 19. 要同步改的文档和 Issue

本设计评审通过后一起改：

- release spec 2.1.7：再认证行补「登录、注册、改密码也写 `auth_at`；本 PR 前的会话视作从未再认证」「再认证与改密码的密码错误是 401 + `PASSWORD_MISMATCH`」；改邮箱行补「待确认状态就是未过期的确认令牌，`GET /me/email/change` 可查」「确认时地址已被抢注回 409」「撤销后原密码作废是删凭据」；找回密码行补「被封禁的账号不发信」；一次性令牌行补「签发统一走 `EmailTokenIssuer`」；决策 K 指向本 spec 第 14 节。
- release spec 2.1.10：邮件清单与第 9.1 节对齐（七封：重设链接、密码已修改、密码已重设、确认新邮箱、旧邮箱通知、新邮箱已绑定、已改回并重设）。
- Linear PAP-98：To-Do 注明 `GET /me/email/change`；AC 不变。
- Linear PAP-99 / PAP-100 / PAP-102：第 18 节的契约各加一段。
- identity README、session README：第 17 节。

## 20. 设计中由我定的点与待拍板的点

**我定的（builder 可直接采用，用户看到不对就推翻）**：

1. 再认证守卫在 identity 领域层（`ReauthenticationGuard`），不在安全 starter；断言不带 `auth_at`。
2. 换令牌保留会话 ID（`rotate`）：登录记录不变、绝对过期不重置、旧令牌立即失效；改密码也用它，并刷新 `auth_at`。
3. 本 PR 前建立的会话没有 `auth_at`，按 `EPOCH` 读出，第一次敏感操作要输一次密码；不回填。
4. 再认证对 `PENDING_DELETION` 放行；改密码、改邮箱申请对它 409（`requireActive`）；撤销改邮箱对它放行。
5. 当前密码错误是 401 + `traits` 含 `PASSWORD_MISMATCH`（Issue 定了 401，trait 让门户不把它当登出）；再认证超时是 403 而不是 401。
6. 改密码的当前密码校验共用登录失败限制（和再认证一样）：连错会锁登录。
7. 新旧密码相同用 NFKC 后的原文比较，不做第二次 Argon2。
8. 找回密码：被封禁的账号不发信；未注册分支做同样的随机数与哈希计算；限流在查用户之前对任何邮箱计数。
9. 重设密码时密码不合规不消费令牌；提交限流先于字段校验。
10. 「待确认的改邮箱」= 一条未过期的 `EMAIL_CHANGE_CONFIRM` 令牌，不加表列；加 `GET /me/email/change` 读它（Issue 没列，门户的安全设置页需要）。
11. 改邮箱申请的检查顺序：字段 → 状态 → 再认证 → 待确认 → 占用 → 限流 → 签发；被占用 409、不计限流。
19. 改密码按用户批量作废令牌（`revokeAllActive`），不只作废重设令牌：release spec 的令牌行与 AC 第 5 条都这么写，改密码行的措辞是子集。
20. `security-notice` 片段去掉固定的「到安全设置」一句，挪进需要它的模板正文；`SecurityNotice` 记录不变。
21. 写处理器沿用 PAP-96 重发处理器的写法：先不加锁读一次做状态守卫与再认证（便宜的拒绝不占行锁），事务里 `findByIdForUpdate` 后再 `requireActive()` 重判；PAP-93 第 16 节「先锁再守卫」的约定由后一半满足。
22. PAP-99 之前的 IP 维度等于全站配额，接受（同版本同 PR 合入），不加开关。
12. 确认改邮箱不撤销任何会话；确认时地址被抢注回 409 `email` / `ALREADY_REGISTERED`（点链接的人就是该地址的主人，不算泄露）。
13. 撤销改邮箱：作废密码 = 删凭据行；改回的地址被抢注回 409 且令牌不消费；不通知被踢掉的新地址；消费一条撤销链接只作废比它更晚签发的（X → Y → Z 点 X 后 Y 的作废；点 Y 后 X 的保留），这是对 PAP-96 第 18 节「作废该用户其余的撤销令牌」的修正（整分支评审查出）。
14. `EmailVerificationIssuer` 泛化为 `EmailTokenIssuer`（签发与拼邮件分开）；`email-resend-throttle.lua` 泛化为 `action-throttle.lua`（冷却 + 任意个窗口），重发限流改到它上面。
15. 安全通知的 `device` / `ipAddress` / `ipLocation` 本 Issue 传 `null`（显示「未知」），PAP-99 换成真实值；旧邮箱通知里新地址遮掩成 `c***@example.com`。
16. 七封邮件、三个落地页路径（`/reset-password`、`/confirm-email-change`、`/revert-email-change`）作为配置项给默认值。
17. 三组限流参数与再认证窗口进 jar 的 `application.yml`，Nacos 不加键。
18. 重设密码后 `clear` 登录失败计数只删失败数与锁，不动在途登记。

**已拍板（2026-10-22，六条都按建议）**：1 选 (b) 放弃伪装、被占用直接 409（已写进第 8.5 节，release spec 2.1.7 随之改）；2 加 `GET /me/email/change`；3 不通知现有账号主；4 不顺带取消注销；5 共用登录失败限制；6 被封禁账号不发信。原文保留：

1. **新邮箱已被占用时的「已发送」伪装要不要保留**（第 8.5 节）。注册接口本来就会告诉你一个邮箱是否已注册（PAP-93 定的 409 `ALREADY_REGISTERED`），所以改邮箱这道伪装只堵「用改邮箱接口当探针」这一条路，而代价是用户填了别人的地址后会看到一个永远等不到邮件的「待确认」，要等一小时或手动取消。两条路：(a) 按 release spec 2.1.7 的字面保留伪装（本 spec 现在的写法：签发不发信）；(b) 放弃伪装，被占用直接回 409 `email` / `ALREADY_REGISTERED`，和注册、确认时被抢注一致，处理器与测试都简单一截。建议 (b)：枚举的门已经在注册接口上开着，这里单独关一扇窗换不来安全，只换来一个让用户困惑的状态。选 (b) 要改 release spec 2.1.7 的改邮箱行。
2. **`GET /me/email/change`**（我定的第 10 条）：Issue 的接口清单里没有它；不加的话门户只能靠申请时的响应记住待确认状态，刷新页面就没了。建议加。
3. **新邮箱已被占用时要不要通知该地址的现有账号主**（「有人试图把你的邮箱绑到另一个账号，如果是你本人请先登录那个账号……」）。GitHub、Google 都发。本版默认不发：多一个模板、多一条被滥用来骚扰别人的路径（攻击者可以反复用别人的邮箱申请，受每账号每天 3 次限制）。
4. **撤销改邮箱时账号处于 `PENDING_DELETION`，要不要顺带取消注销**。本版默认不取消（撤销只管邮箱、会话、密码）；原主重设密码登录后看到「取消注销」页自己取消，15 天冷静期足够。要顺带取消的话是 PAP-100 的事：撤销处理器多一行 `user.cancelDeletion()`。
5. **改密码的当前密码错误共用登录失败限制**（我定的第 6 条）：代价是本人在设置页连错 5 次当前密码，登录也会被锁一段时间。不共用的话改密码接口对已登录的攻击者（偷到会话的人）没有猜密码的限制——他猜中就能换掉密码把原主踢掉。建议共用。
6. **找回密码对被封禁账号不发信**（我定的第 8 条）：另一个选项是照发，让他重设后登录时看到 403「已封禁」。建议不发。
