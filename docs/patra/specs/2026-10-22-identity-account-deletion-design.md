# identity 注销账号工程设计（PAP-100）

> **Issue**：[PAP-100](https://linear.app/papertrace/issue/PAP-100)（GitHub #266）
> **版本**：[v0.12 User Center](../release-specs/v0.12-user-center.md)（2.1.9 注销账号、2.1.10 安全通知邮件、决策 H）
> **前置设计**：[identity 账号重建（PAP-93）](2026-10-20-identity-account-rebuild-design.md)（四态状态机、`requireActive()`、`V1`、第 16 节）、[identity 邮件与邮箱验证（PAP-96）](2026-10-21-identity-email-verification-design.md)（`OutgoingMail`、`SecurityNotice`、令牌表、XXL-Job、第 18 节）、[identity 账号安全（PAP-98）](2026-10-22-identity-account-security-design.md)（`ReauthenticationGuard`、撤销改邮箱对冷静期放行、第 18 节）、[identity 资料与公开主页（PAP-95）](2026-10-22-identity-profile-design.md)（资料清理清单、第 15 节）、[identity 登录设备（PAP-99）](2026-10-22-identity-login-devices-design.md)（`SessionTerminator`、`ClientOriginResolver`、结束原因、第 21 节）
> **设计简报**：`patra-portal/docs/design-briefs/v0.12/user-center.md` §3.10 卡片 3「危险区」、§3.15 注销对话框、§3.16 取消注销页、§9 附录 A
> **日期**：2026-10-22
> **状态**：已实施（2026-10-22，分支 `feat/v0.12-user-center-api`，提交 `1dc34b66f`..本节所在提交）。实施结果、偏离与评审见第 24 节

## 1. 要解决的问题

用户现在没有办法离开 Patra：账号、资料、邮箱会一直留在库里。本 Issue 交付完整的注销：

1. **发起**：`POST /me/deletion`，过再认证、输入自己的用户名确认；账号进入 15 天冷静期（`PENDING_DELETION`），全部会话下线、全部待用令牌作废、主页对别人消失，发「注销已提交」邮件（第 7.1 节）。
2. **冷静期**：期内照常能登录，登录后门户进取消注销页；除取消注销、登出和少数安全操作外，写接口一律 409（第 8 节）。`DELETE /me/deletion` 取消，回到 `ACTIVE`，发「已取消」邮件（第 7.2 节）。
3. **到期执行**：XXL-Job 任务每小时扫一次到期的账号，逐个匿名化：删凭据、关注领域、链接、令牌，资料行置空，邮箱置空，状态转 `DELETED`，`idn_user` 行与用户名永久保留；发「已完成」邮件（第 9、11 节）。
4. **到期前提醒**：另一个任务在到期前 3 天发一封提醒，只发一次，靠 `idn_user` 上新加的一列记住发过（第 10 节）。

本 Issue 建立在 PAP-93 已经交付的模型上：聚合的 `requestDeletion` / `cancelDeletion` / `completeDeletion`、`deletion_due_at` / `deleted_at` 的检查约束、`requireActive()` 的 409、登录对四种状态的分流、`/auth/me` 的 `status` 与 `deletionDueAt` 都已就位；公开主页对冷静期与已注销的 404 已由 PAP-95 做完。这里只写处理器、任务、邮件和少量模型补充。

设计过程中定下的、影响别的 Issue 的事：

- **改邮箱的撤销令牌在发起注销时保留，到期执行时删除**（第 12 节）。release spec 2.1.7 已写明撤销令牌「不被批量作废，7 天内始终有效」、「注销冷静期内也能撤」，现有 `revokeAllActive` 也本来就跳过它；PAP-96 第 18 节「发起时另调 `revokeActive(EMAIL_CHANGE_REVERT)`」的旧约定与之矛盾，以 release spec 为准，本 Issue 不调那一行，并回写 PAP-96 spec 的契约表。
- 头像对象的删除由 PAP-97 接入，本 Issue 不预建端口；契约写进 PAP-97 的 Linear（第 14 节）。
- 邮件从三封变成四封：「注销已提交」「即将删除提醒」「已取消」「已完成」（第 13 节）；release spec 2.1.10 的清单要加「即将删除提醒」。

**完成标准**：PAP-100 的 Acceptance Criteria，逐条对应见第 19.4 节。

## 2. 范围

做：

- 领域：`User` 加 `deletionRemindedAt`、`markDeletionReminded` 与 `clearDeletionReminder`，`cancelDeletion` / `ban` / `completeDeletion` 同时清掉它；到期与待提醒两个判定方法；`AccountDeletionPolicy`（冷静期、提醒提前量）；`UserProfile.erase()`；`LoginEndReason` 加 `DELETION_REQUESTED`、`DELETION_COMPLETED`；`UserRepository` 两个按批扫 ID 的查询；`EmailTokenRepository.deleteAllByUserId`；四个 `OutgoingMail` 记录；字段原因码 `MISMATCH`。
- 应用：`RequestAccountDeletionHandler`、`CancelAccountDeletionHandler`、`ExecuteDueAccountDeletionsHandler`、`SendAccountDeletionRemindersHandler`。
- 基础设施：`UserEntity` 与映射加列，两个扫描查询，令牌按用户删行，四对邮件模板，渲染器把模板变量里的 `Instant` 统一按时区格式化。
- 适配器：`MeDeletionController`（`POST` / `DELETE /me/deletion`）；两个 XXL-Job 入口。
- boot：`patra.identity.deletion.*` 配置与 `AccountDeletionPolicy` 装配。
- `V1`：`idn_user` 加 `deletion_reminded_at` 列、约束、部分索引；登录记录结束原因约束加两个取值。
- 网关：路由规则不用改（`/patra-identity/me/**` 本来就需要登录），加一条集成测试钉住。
- 文档：本 spec、plan、identity README；Linear PAP-97 / PAP-102 的契约评论。

不做：

- 后台注销被封禁的账号（`BANNED → DELETED`）：聚合方法已有，没有后台入口（release spec 边界 H）。
- 头像对象删除的实现：PAP-97 还没有头像（第 14 节）。
- 外部身份表：第三方登录那一版建表时，按第 11 节的规则在同一步删行。
- 社区内容的「已注销用户」展示：以后的 interaction 读 `idn_user.status`。
- 登录记录的提前删除：按 2.1.8 的留存规则由 `loginRecordCleanupJob` 清理（release spec 2.1.9）。
- 门户的注销对话框与取消注销页：PAP-102。

## 3. 已定的前提

| 来源 | 内容 |
|---|---|
| release spec 2.1.9 / 决策 H | 冷静期 15 天；发起要再认证 + 输入用户名，不要求邮件确认；期内登录进确认页，点了才恢复；到期删凭据、关注领域、头像、令牌、以后的外部身份，资料行置空不删，邮箱置空可立即再注册，`idn_user` 行保留，用户名永久占用；封禁账号不能自助注销；登录记录不随注销删 |
| release spec 2.1.10 | 注销已提交（写明到期日和怎么取消）、已取消、已完成；邮件不放登录链接 |
| PAP-93 | 聚合三方法与约束已在；`requireActive()` 冷静期 409（`traits` 含 `PENDING_DELETION`）、封禁 / 已注销 401；登录对 `PENDING_DELETION` 建会话，`DELETED` 按密码错误；Redis 会话不带状态 |
| PAP-95 | 资料行置空不删、隐私回默认、链接与关注领域按 `user_id` 删行；冷静期内资料与隐私的写接口已 409，公开主页对别人 404、对本人 200 `SELF` |
| PAP-96 | 一种新邮件 = 一个 `OutgoingMail` 记录 + `html` / `text` 两份同名模板；任务照 `EmailTokenCleanupScheduleJob` 写 `@XxlJob`，执行器 `patra-identity` 已注册 |
| PAP-98 | 发起注销先过 `ReauthenticationGuard.requireRecent`；撤销改邮箱对冷静期放行且不碰注销状态（要不要顺带取消归本 Issue 定，见第 23 节第 2 条） |
| PAP-99 | 发起时 `SessionTerminator.terminateAll(userId, DELETION_REQUESTED, now)`；安全通知用 `ClientOrigin.toNotice`，命令带 `clientIp` 与 `userAgent` |
| PAP-92 | 发起响应带 `deletionDueAt`（结果屏要写到期日，本机会话那时已被撤销）；取消响应回与 `/auth/me` 同构的 `user` |
| 用户（2026-10-22） | 冷静期 15 天；冷静期内登录进入取消注销确认页、点了才恢复；用户名注销后永久占用；邮箱立即可重新注册 |

## 4. 业界参照

- **冷静期**：微信 15 天、X 30 天、GitHub 立即（release spec 决策 H 的调研）。都是「期内登录即可撤销」，Patra 不静默撤销而是让用户点一次确认（用户定），因为冷静期内可能是别人用偷来的密码登录。
- **匿名化保留行**：Discourse `UserAnonymizer` 把用户名换成 `anonN`、清资料、删邮箱，行保留；Mastodon 删账号后用户名永不释放。Patra 取 Mastodon 的「用户名永不释放」，不改名：不需要释放表，也没有冒名风险（决策 H）。
- **提醒只发一次**：一般做法是给状态行加一个「已提醒时间」列，任务只挑没提醒过的（PAP-93 spec 第 6 节预告了这一列）。不靠「任务每天跑一次、只挑恰好落在某个窗口里的」：任务漏跑一天就漏发、重跑一次就重发。

## 5. 模块与改动面

| 模块 | 改动 |
|---|---|
| `patra-identity-domain` | `User`（新列、`markDeletionReminded`、`clearDeletionReminder`、`isDeletionDue`、`needsDeletionReminder`）、`AccountDeletionPolicy`、`UserProfile.erase()`、`LoginEndReason` 两个取值、`UserRepository` 两个查询、`EmailTokenRepository.deleteAllByUserId`、`UserFieldViolations` 的 `MISMATCH` 与 `deletionUsername…`、四个邮件记录 |
| `patra-identity-app` | `usecase/deletion/` 下四个处理器与命令、结果记录 |
| `patra-identity-infra` | `UserEntity` / `UserJpaMapper` / `UserDao` / `UserRepositoryAdapter`、`EmailTokenDao` / 适配器、`ThymeleafMailRenderer`（`Instant` 变量格式化）、四对模板、`V1` |
| `patra-identity-adapter` | `MeDeletionController` 与请求 / 响应、`AccountDeletionScheduleJob`、`AccountDeletionReminderScheduleJob` |
| `patra-identity-boot` | `IdentityProperties.Deletion`、`IdentityConfiguration` 装配策略 |
| `patra-gateway-boot` | 只加集成测试断言 |

模块之间没有新依赖，`module-graph.json` 不变（实施时 `dumpModuleGraph` 核对）。

## 6. 生命周期

```
ACTIVE ──POST /me/deletion──▶ PENDING_DELETION ──到期任务──▶ DELETED（不可回退）
   ▲                              │      │
   └──────DELETE /me/deletion─────┘      └──后台封禁──▶ BANNED（注销申请作废）

PENDING_DELETION 期间（deletion_due_at = 发起时间 + 15 天）：
  发起 ──────────── … ──── 到期前 3 天：提醒任务发一封（记 deletion_reminded_at）── … ── 到期：执行任务
```

- `deletion_due_at` 精确到秒，到期执行任务每小时一次，账号最多在到期后约一小时内被删。
- 到期之后、任务执行之前登录并取消，照样成功（第 23 节第 3 条）：执行任务在行锁下复核状态，取消先提交就跳过。
- 冷静期内被后台封禁：`ban()` 已把到期时间清空（PAP-93），本 Issue 让它同时清 `deletion_reminded_at`，两个任务都不会再碰它。

## 7. 接口

### 7.1 `POST /me/deletion`：发起

请求体 `{ "username": "chen-yu" }`。成功 200：

```json
{ "deletionDueAt": "2026-11-06T08:00:00Z" }
```

处理顺序（`RequestAccountDeletionHandler`，照 `RequestEmailChangeHandler` 的分层）：

1. `currentUserPort.require()`；校验字段：`username` 为空或全空白 → 422 `username` / `REQUIRED`。
2. 读用户；状态守卫 `requireActive()`：冷静期内再发起 409 `PENDING_DELETION`（门户据此跳取消注销页）；封禁、已注销 401。
3. 用户名比对：输入去首尾空白、转小写（`Locale.ROOT`）后与当前用户名相等，否则 422 `username` / `MISMATCH`。不走 `UsernamePolicy`：格式不合法的输入也只是「不一致」。
4. 再认证：`reauthenticationGuard.requireRecent(current)`，超窗 403 `REAUTH_REQUIRED`。
5. 事务（`TransactionOperations`）：
   1. `findByIdForUpdate` 加锁重读，再 `requireActive()`（并发的第二次发起在这里 409）。
   2. `user.requestDeletion(now, policy.coolingPeriod())`，保存。
   3. `tokens.revokeAllActive(userId, now)`：作废验证、重设、改邮箱确认令牌；**不动**改邮箱撤销令牌（第 12 节）。
   4. `sessionTerminator.terminateAll(userId, DELETION_REQUESTED, now)`：包括发起请求的这一条会话。
   5. `userReadPort.findAccount(userId)` 取昵称给邮件。
6. 提交后：解析请求来源（`ClientOriginResolver`），发「注销已提交」（第 13 节），日志 `前台用户已申请注销: userId, deletionDueAt, revokedSessions`。
7. 返回到期时间。

- Redis 失败整个事务回滚，503，状态不变，用户重试（与封禁一致）。
- 第 3 步放在再认证之前：门户本来就先比对用户名，错了直接提示，不必先弹密码框（同改邮箱把「与当前相同」放在再认证之前）。
- 响应时本机会话已撤销；门户用本地数据渲染结果屏后清 Cookie（PAP-92）。
- 邮箱未验证的账号同样可以注销，邮件照发到当前邮箱（丢了邮箱的人也要能注销，release spec 2.1.9）。

### 7.2 `DELETE /me/deletion`：取消

无请求体。成功 200，体是与 `/auth/me` 同构的 `user`（`status` 为 `ACTIVE`、`deletionDueAt` 为 `null`）。

1. `currentUserPort.require()`。
2. 事务：`findByIdForUpdate`；封禁、已注销 401（这两种状态没有有效会话，与 `requireActive()` 一致）；`user.cancelDeletion()`；本来就是 `ACTIVE` 时什么都不改（幂等）；保存；`findAccount` 取响应体。
3. 提交后，只有真的从 `PENDING_DELETION` 回到 `ACTIVE` 才发「已取消」邮件并打日志 `前台用户已取消注销: userId`；幂等的重复取消不发信。

不要求再认证：取消是往安全方向走，冷静期内的会话本来就是刚输过密码登录得来的。会话不动：取消之后用户留在当前设备上，冷静期内其他设备上的登录也保留（它们同样是输过密码的）。

### 7.3 公开主页、`/auth/me`、登录

不用改：冷静期对别人 404、对本人 200 `SELF`（PAP-95）；`/auth/me` 与登录响应带 `status`、`deletionDueAt`（PAP-93）；`DELETED` 登录按密码错误（PAP-93，邮箱已置空，按邮箱本来就查不到）。

## 8. 冷静期内的接口矩阵

「冷静期账号」指带有效会话、状态为 `PENDING_DELETION` 的用户（发起时会话全部撤销，期内再登录才有会话）。

| 接口 | 冷静期内 | 依据 |
|---|---|---|
| `DELETE /me/deletion` 取消注销 | 放行 | 本 Issue |
| `POST /auth/logout` | 放行 | 登出永远可达 |
| `POST /auth/login` | 建会话，响应 `status` 为 `PENDING_DELETION` | PAP-93 |
| `POST /auth/reauth` | 放行 | PAP-98（取消注销不需要它，但门户的通用拦截可能触发） |
| `DELETE /me/sessions/{id}`、`DELETE /me/sessions/others` | 放行（不判账号状态） | PAP-99 第 11.4 节：踢设备是止损，冷静期里更需要 |
| `POST /me/deletion` | 409 `PENDING_DELETION` | 本 Issue |
| `PATCH /me/username`、`PUT /me/profile`、`PUT /me/privacy`、`PUT /me/password`、`POST` / `DELETE /me/email/change`、`POST /me/email/verification/resend` | 409 `PENDING_DELETION` | 已有的 `requireActive()` |
| `GET /auth/me`、`GET /me/profile`、`GET /me/privacy`、`GET /me/sessions`、`GET /me/email/change` | 200 | 读不拦 |
| `GET /users/{username}` | 别人 404，本人 200 `SELF` | PAP-95 |
| `POST /auth/password/forgot`、`POST /auth/password/reset` | 放行，重设不碰注销状态 | PAP-98：忘了密码的人要能登录进来取消 |
| `POST /auth/email/change/revert` | 放行，不碰注销状态 | PAP-98（第 23 节第 2 条） |
| `POST /auth/email/verify`、`POST /auth/email/change/confirm` | 发起时这两种令牌已作废，冷静期内也签发不出新的（重发、改邮箱都 409），实际只会回 422 | PAP-96 / PAP-98 |

改密码在冷静期内 409 是 PAP-93 起就有的行为，保留：要改密码先取消注销。

## 9. 到期执行任务

### 9.1 调度

XXL-Job，handler `accountDeletionJob`（`AccountDeletionScheduleJob`），无参数；mini 上 Cron `0 5 * * * ?`（每小时第 5 分）。与 PAP-96 / PAP-99 的两个清理任务同一做法：命令总线发 `ExecuteDueAccountDeletionsCommand`，`XxlJobHelper.handleSuccess` / `handleFail` 回写结果。用 XXL-Job 而不是 `@Scheduled` 的理由同 PAP-96 第 10 节：D6 验收要「把到期时间改到过去再手动触发」，调度中心的手动触发和执行日志正好用上。

每小时而不是每天：取消注销页给用户的是精确到分钟的到期时刻（简报 §3.16），每天跑一次会让账号在到期后最多再活 24 小时。任务本身只扫一个部分索引，空跑的代价是一条索引扫描。

### 9.2 扫描

```sql
SELECT id FROM idn_user
WHERE status = 'PENDING_DELETION' AND deletion_due_at <= :now AND id > :afterId
ORDER BY id LIMIT :limit
```

- `now` 在一次运行开始时取一次；按 `id` 键集翻页，每页 100 个，直到一页不满。
- 按 `id` 而不是 `deletion_due_at` 翻页：某个用户执行失败时它还留在条件里，按 `id` 往后翻不会反复撞上它，也不会死循环；失败的下次运行再试。
- 部分索引 `idx_idn_user_deletion_due_at ON idn_user (deletion_due_at) WHERE status = 'PENDING_DELETION'`：冷静期内的行很少，索引也很小。

### 9.3 每个用户一个事务

```
事务 {
  user = findByIdForUpdate(id)
  若 !user.isDeletionDue(now) → 跳过（取消、封禁先提交了；或另一次运行已经做完）
  email = user.email；displayName = findAccount(id).displayName   // 给「已完成」邮件
  第 11 节的匿名化清单
  sessionTerminator.terminateAll(id, DELETION_COMPLETED, now)      // 冷静期内登录的会话
}
提交后：发「已完成」邮件到 email；日志 `前台用户已注销: userId`
```

- **单个失败不影响其他**：循环里对每个用户 `try / catch RuntimeException`，失败记 `log.error`（带用户 ID，不带邮箱）、计数，接着处理下一个。事务回滚，用户仍在冷静期，下次运行再试。
- **幂等**：已经 `DELETED` 的不在扫描条件里；锁内复核挡住并发的两次运行；`completeDeletion` 对 `DELETED` 本来就是空操作。重跑任务不会重发邮件，因为发信只在真的完成了迁移的那个事务之后。
- **与登录并发**：登录在事务里对用户行加锁（PAP-93 第 9 节）。任务先提交，登录随后读到 `DELETED`，按密码错误 401；登录先提交，任务的 `terminateAll` 把新会话一起删掉。与封禁同一个模式。
- **「已完成」邮件被发信执行器拒绝**（`EmailDeliveryPort.send` 返回 `false`：队列满或停机）：邮箱已置空，没法重试；记 `log.warn`（带用户 ID），不计入 `failed`——注销本身已经完成。
- **结果**：`DeletionRunResult(completed, failed)`。`failed > 0` 时任务回写 `handleFail`（调度中心标红，执行日志里能看到），否则 `handleSuccess`；两种都写清数字。
- 邮件是至多一次：提交后、发信前进程崩了，这封就没了（和所有邮件一样，PAP-96 第 9 节）。

## 10. 到期前提醒任务

### 10.1 调度

handler `accountDeletionReminderJob`（`AccountDeletionReminderScheduleJob`），mini 上 Cron `0 15 * * * ?`（每小时第 15 分）。分成两个任务而不是一个任务做两件事：一个出错不拖另一个，执行日志也各自清楚。

### 10.2 谁该收到

`status = 'PENDING_DELETION' AND deletion_reminded_at IS NULL AND deletion_due_at > :now AND deletion_due_at <= :now + 提前量（3 天）`，同样按 `id` 键集翻页。

- `deletion_due_at > now`：已经到期的不提醒了，交给执行任务。
- 冷静期 15 天、提前量 3 天：发起后第 12 天左右收到。配置的提前量必须短于冷静期，否则启动失败（发起即提醒没有意义）。

### 10.3 只发一次

每个用户一个事务：加锁重读 → `user.needsDeletionReminder(now, lead)` 复核 → `user.markDeletionReminded(now)` → 保存 → 提交后发「即将删除」邮件。

- 标记和发信的顺序是「先标记后发」：不会重复。发信被执行器拒绝（`send` 返回 `false`）时，另开一个短事务把 `deletion_reminded_at` 清回空（`user.clearDeletionReminder()`，只在仍是冷静期时清），下次运行重发，这个用户计入 `failed`——与新设备提醒被拒时退回冷却同一做法（PAP-99）。被接下之后真正发送失败只记日志（PAP-96），不重发。
- 取消注销清掉 `deletion_reminded_at`：再次发起后的新一轮冷静期照样会提醒。封禁、完成注销也清掉（约束要求：只有冷静期可以有值）。
- 失败处理、结果回写同第 9 节（`ReminderRunResult(sent, failed)`）。

## 11. 匿名化清单

到期执行时在同一个事务里做（第 9.3 节）：

| 数据 | 处理 | 怎么做 |
|---|---|---|
| `idn_user` | 行保留：ID、用户名、`username_changed_at`、`created_at` 留着；`email`、`email_verified_at`、`deletion_due_at`、`deletion_reminded_at`、`banned_at` 置空，`status = DELETED`，`deleted_at = now` | `user.completeDeletion(now)`（`deletion_reminded_at` 由本 Issue 加进去）+ `users.save` |
| `idn_user_password_credential` | 删行 | `credentials.deleteByUserId`（PAP-98 已有） |
| `idn_user_profile` | 行保留；昵称、简介、身份大类、职称、机构（ROR ID 与名称）、科室置空；三个隐私开关回默认（`PUBLIC` / `PUBLIC` / `false`） | `profile.erase()` + `profiles.save` |
| `idn_user_profile_link`、`idn_user_research_interest` | 删行 | `erase()` 把集合清空，仓储保存时整组替换成空（PAP-95 的写法） |
| `idn_email_token` | 删行（含改邮箱撤销令牌） | `tokens.deleteAllByUserId`：令牌行里有发往的邮箱和申请方 IP，作废不够，要删 |
| Redis 会话 | 全部删 | `terminateAll(…, DELETION_COMPLETED, …)` |
| 头像对象与资料里的头像键 | 删 | PAP-97 接入（第 14 节） |
| 以后的外部身份表 | 按 `user_id` 删行 | 第三方登录那一版在这一步加一行 |
| `idn_user_login_record` | **不删**，按 2.1.8 的留存规则 190 天后清理 | release spec 2.1.9；《网络安全法》日志留存 |
| `idn_user_former_username` | **不动**：旧名按原保留期自然失效 | 旧名是公开信息，不是个人数据；保留期内 `/users/<旧名>` 跟原主当前主页走，已注销即 404 |
| Redis 里的登录失败计数、新设备提醒冷却 | 不动，按 TTL 过期 | 键里只有邮箱哈希 / 用户 ID，存活不超过 24 小时 |

结果：同一个邮箱立刻可以注册新账号（`email` 唯一约束只对非空值生效），新账号拿到新 ID，和旧行没有任何关联；旧用户名还在 `idn_user` 里，注册与改名都 409 `ALREADY_TAKEN`（PAP-93 的占用查询本来就查 `idn_user` 全表）。

`UserProfile.erase()` 是一个领域方法而不是在处理器里拼一个空的 `ProfileContent`：「注销时资料要清成什么样」是资料聚合自己的规则，以后资料加字段（PAP-97 的头像键）时在同一处补。

## 12. 令牌、会话、登录记录的联动

| 时刻 | 令牌 | 会话（结束原因） | 凭据 |
|---|---|---|---|
| 发起 | `revokeAllActive`：验证、重设、改邮箱确认作废；**改邮箱撤销令牌保留** | 全部撤销（`DELETION_REQUESTED`） | 不动 |
| 冷静期内找回密码 | 照常签发重设令牌 | 重设时全部撤销（`PASSWORD_RESET`） | 换 |
| 冷静期内撤销改邮箱 | PAP-98 的规则 | 全部撤销（`EMAIL_REVERTED`） | 删（原主用重设链接设新密码） |
| 取消 | 不动 | 不动 | 不动 |
| 到期执行 | 全部删行 | 全部撤销（`DELETION_COMPLETED`） | 删 |

**撤销令牌为什么发起时保留**：PAP-96 的原约定是「发起与到期都作废撤销令牌，注销后邮箱为空，撤回已无意义」。发起时邮箱还在，账号也还能救回来；PAP-98 又定了「冷静期内也能撤销改邮箱」（2026-10-22 用户拍板），这条只有在撤销令牌活过发起时才有用。要防的场景：攻击者拿到密码 → 把邮箱改成自己的（原主旧邮箱收到 7 天有效的「不是我」）→ 立刻申请注销。若发起时作废撤销令牌，原主手里的链接失效，登录不了（邮箱已不是他的，密码可能也被改了），15 天后账号连同资料被删。保留的话，原主点「不是我」→ 邮箱改回、会话清空、收到重设链接 → 设新密码登录 → 取消注销页 → 恢复。到期执行时邮箱置空，撤销令牌随所有令牌一起删除。

**新结束原因**：`DELETION_REQUESTED`（PAP-99 预留的名字）与 `DELETION_COMPLETED`（冷静期内登录、没有取消的会话在到期时被删）。新设备判定只认 `REVOKED`（PAP-99），两者都不影响它；账号已注销，判定也不会再发生。

## 13. 邮件

四封，各一个 `OutgoingMail` 记录 + `mail/html`、`mail/text` 下同名模板，结构照 `password-changed`。称呼用 `displayName`（发起与提醒、取消时现读；完成时在匿名化之前读）。

| 模板 | 记录 | 收件人 | 主题 | 要点 | 安全通知片段 |
|---|---|---|---|---|---|
| `account-deletion-requested` | `AccountDeletionRequestedMail` | 当前邮箱 | 你的 Patra 账号将被注销 | 到期时刻；所有设备已退出；到期前用邮箱和密码登录、按页面提示即可取消；到期后会删什么、用户名不会释放、邮箱可以重新注册 | 有（请求来源）；不是我：「如果不是你，说明有人知道了你的密码：请在到期前登录并取消注销，然后立即修改密码；登录不了就用『忘记密码』重设」 |
| `account-deletion-reminder` | `AccountDeletionReminderMail` | 当前邮箱 | 你的 Patra 账号将在 3 天后删除 | 到期时刻；想保留就在此之前登录并取消 | 无：不是某次请求触发的，没有设备与 IP |
| `account-deletion-cancelled` | `AccountDeletionCancelledMail` | 当前邮箱 | 你的 Patra 账号已取消注销 | 账号已恢复，资料都在 | 有；不是我：「如果不是你，说明有人知道了你的密码：请立即修改密码，并在登录设备里退出不认识的设备；仍想注销可以重新申请」 |
| `account-deletion-completed` | `AccountDeletionCompletedMail` | 注销前的邮箱 | 你的 Patra 账号已注销 | 账号已删除；用户名 `@x` 不会再被任何人使用；这个邮箱可以重新注册，新账号与旧账号无关 | 无 |

- 「不放登录链接」（2.1.10）：「怎么取消」写成文字（「用邮箱和密码登录 Patra」），不给链接。页脚已有站名。
- 到期时刻的写法与安全通知的时间一致：`2026-11-06 08:00 (UTC+8)`。做法是渲染器把模板变量里所有 `Instant` 值按 `patra.identity.mail.zone` 格式化（复用 `SecurityNoticeView.formatTime`），邮件记录里直接放 `Instant`；渲染器仍然不认识具体邮件类型。现有八封邮件的变量里没有 `Instant`，不受影响。
- 「即将删除」的主题写「3 天后」：提前量可配，主题从记录里按提前量的天数拼（`Duration.toDays()`），不写死。
- 发信都在事务提交之后，`EmailDeliveryPort.send` 异步、失败只记日志（PAP-96）。`send` 返回 `false`（执行器拒绝）时：发起、取消、已完成三封记 `log.warn` 后照常返回，不回滚业务；提醒那封按第 10.3 节退回标记。

## 14. 头像（PAP-97 的契约）

PAP-97 还没做，资料表里还没有头像键，MinIO 里也没有头像对象。本 Issue **不预建**头像端口（没有调用方的接口是预防性代码，项目规则禁止）；在 PAP-97 的 Linear 上写清它要接入的地方：

1. `UserProfile.erase()` 同时清掉头像键。
2. 到期执行在**事务之前**按前缀 `avatar/{userId}/` 删光对象（`ExecuteDueAccountDeletionsHandler` 每个用户的那一步里，加锁事务之前）：对象存储不参与数据库事务，先删对象、后提交数据库，失败时用户还在冷静期，下次运行重试，重试删的是空前缀，天然幂等；反过来先提交后删对象，删失败的那些对象就再也没人管了。
3. 删除失败按单个用户失败处理（第 9.3 节）。

## 15. `V1` 改动

`idn_user`：

```sql
deletion_reminded_at  timestamptz(6)  NULL,
…
CONSTRAINT ck_idn_user_deletion_reminded_at
    CHECK (deletion_reminded_at IS NULL OR status = 'PENDING_DELETION')
```

```sql
CREATE INDEX idx_idn_user_deletion_due_at ON idn_user (deletion_due_at)
    WHERE status = 'PENDING_DELETION';
COMMENT ON COLUMN idn_user.deletion_reminded_at IS
    'Set when PENDING_DELETION and the pre-deletion reminder has been sent (UTC); cleared on cancel, ban and completion';
```

`idn_user_login_record`：`ck_idn_user_login_record_end_reason` 加 `'DELETION_REQUESTED', 'DELETION_COMPLETED'`（列宽 32 够用），列注释同步。

本机与 mini 的 `patra_identity` 按新 `V1` 重建（决策 J，dev 数据可丢）。

## 16. 网关路由

不用改：`/patra-identity/me/**` 不在公开名单里，默认需要登录。在网关现有的 identity 路由集成测试里加一条：匿名 `POST /patra-identity/me/deletion` 与 `DELETE` 都是 401。

## 17. 错误契约

| 场景 | 状态 | 错误码 / 字段 |
|---|---|---|
| 发起时用户名为空 | 422 | `IDN-0422`，`errors[]`：`username` / `REQUIRED` |
| 发起时用户名与当前不一致 | 422 | `IDN-0422`，`errors[]`：`username` / `MISMATCH` |
| 发起时已在冷静期 | 409 | `IDN-0409`，`traits` 含 `PENDING_DELETION` |
| 发起时再认证超时 | 403 | `IDN-0403`，`traits` 含 `REAUTH_REQUIRED` |
| 没有当前用户；用户已封禁或已注销（两个接口） | 401 | `IDN-0401` |
| Redis 不可用（撤销会话、再认证读 `auth_at`） | 503 | `IDN-0503` |

「封禁账号不能自助注销」在接口层表现为 401 而不是 403：封禁会撤销全部会话、登录对封禁账号回 403（PAP-93），被封禁的人手里不会有能走到这个接口的会话；万一并发撞上（会话刚通过网关、封禁随即提交），按 `requireActive()` 的统一口径回 401。门户的「被封禁的账号不能自助注销」一句就是说登录这一步的 403（第 23 节第 4 条）。

## 18. 配置

```yaml
patra:
  identity:
    deletion:
      cooling-period: 15d   # 冷静期
      reminder-lead: 3d     # 到期前多久提醒
```

在 jar 的 `IdentityProperties` 里给默认值，Nacos 不加键。非正数、提前量不短于冷静期，启动失败（`AccountDeletionPolicy` 的紧凑构造器校验）。mini 上做 D6 验收不需要改它：把库里的 `deletion_due_at` 改到过去再手动触发任务（release spec D6）。

两个任务的批大小（100）写成处理器常量，同两个清理任务。

## 19. 测试策略

### 19.1 单元测试（不起 Spring）

- `UserTest`：`markDeletionReminded` 只在冷静期可调、重复调用保留第一次，`clearDeletionReminder` 清回空；`cancelDeletion` / `ban` / `completeDeletion` 清掉它；`isDeletionDue`、`needsDeletionReminder` 的边界（恰好等于到期时刻、恰好等于提前量、已提醒、已到期）。
- `AccountDeletionPolicyTest`：非正数、提前量不短于冷静期抛异常。
- `UserProfileTest`：`erase()` 后所有内容为空、隐私回默认。
- 四个处理器的单测（Mockito，`MutableClock` 或 `Clock.fixed`）：
  - 发起：字段为空 422、冷静期 409、用户名大小写与空白不敏感、不一致 422、未再认证 403 且什么都没改；成功时按顺序调了状态迁移、`revokeAllActive`（没有调 `revokeActive(EMAIL_CHANGE_REVERT)`）、`terminateAll(DELETION_REQUESTED)`，提交后才发信，返回 `now + 15d`。
  - 取消：冷静期 → 发信；已是 `ACTIVE` → 不发信、照常返回；封禁 401。
  - 到期执行：锁内复核不到期就跳过；一个用户抛异常，另一个照样完成，结果 `(1, 1)`；键集翻页（第一页满 100 个时取第二页）。
  - 提醒：只对需要提醒的标记并发信；锁内复核已提醒则跳过；`send` 返回 `false` 时标记被清回、计入失败。
- `ThymeleafMailRendererTest`：四封邮件的文本与 HTML 都含到期时刻（按时区格式化）、昵称；有通知片段的两封含设备与 IP；没有登录链接。
- `MeDeletionControllerTest`（`@WebMvcTest` + `@Import`）：请求体映射、200 体形状、`User-Agent` 与客户端 IP 进命令。

### 19.2 集成测试（Testcontainers：PG 17、Redis 7、GreenMail）

- `patra-identity-infra`：`UserRepositoryAdapterIT` 两个扫描查询（状态、到期、已提醒、键集、上限）；`V1` 约束：非冷静期写 `deletion_reminded_at` 被拒、两个新结束原因能写入；`EmailTokenRepositoryAdapterIT.deleteAllByUserId` 只删该用户的；`UserProfileRepositoryAdapterIT`：`erase` 后保存，子表行没了、资料行还在。
- `patra-identity-boot`：`AccountDeletionFlowIT`（`RANDOM_PORT`、真实过滤器链、`IdentityClockITSpringConfig` 的可拨时钟、`GreenMailSmtpInitializer`）：
  1. 注册、设资料与关注领域、改邮箱发出撤销令牌 → 两台设备登录 → 不再认证发起 403 → 再认证后用户名写错 422 → 正确发起 200，`deletionDueAt = 时钟 + 15d`；两台设备的令牌都 401；匿名看主页 404；收到「注销已提交」，含到期时刻；撤销令牌行仍有效、其余令牌已作废；登录记录结束原因 `DELETION_REQUESTED`。
  2. 冷静期内登录：响应与 `/auth/me` 的 `status` 为 `PENDING_DELETION`；`PUT /me/profile` 409 含 `PENDING_DELETION`；再次发起 409；取消 200 体 `status` 为 `ACTIVE`；资料写接口恢复 200；收到「已取消」。
  3. 再次发起 → 时钟拨到到期前 3 天内 → 触发提醒任务两次：只收到一封「即将删除」。
  4. 时钟拨过到期 → 冷静期内登录一台设备 → 触发执行任务两次：状态 `DELETED`、邮箱为空、行保留；凭据、链接、关注领域、令牌行都没了；资料行在且全空、隐私为默认；那台设备 401，记录结束原因 `DELETION_COMPLETED`；只收到一封「已完成」；同一邮箱注册新账号 201 且新 ID 不同；用旧用户名注册 409 `ALREADY_TAKEN`；用旧邮箱旧密码登录 401。
  5. 封禁用户登录 403（已有）；执行任务里一个用户失败不影响另一个——用 `@MockitoSpyBean` 让某个用户的 `credentials.deleteByUserId` 抛异常，另一个照样 `DELETED`，失败的那个仍是冷静期（单独一个类 `AccountDeletionIsolationIT`，与 `PasswordChangeRollbackIT` 同一个手法）。
- `IdentityConfigRefreshIT`：如新增 bean 需要 mock，按 PAP-96 的先例补。

### 19.3 网关

现有 identity 路由的集成测试加两条断言（第 16 节）。

### 19.4 AC 对应

| AC | 证据 |
|---|---|
| 发起后全部会话 401、公开主页 404、收到「注销已提交」（含到期日与取消方式）；未再认证或用户名不符被拒 | `AccountDeletionFlowIT` 第 1 步 |
| 冷静期内登录：拿到会话且 `status` 为 `PENDING_DELETION`；写接口 409；取消后恢复 `ACTIVE`、写接口恢复、收到「已取消」 | 第 2 步 |
| 到期任务：`DELETED`，凭据 / 关注领域 / 头像对象 / 令牌均不存在，资料行置空，邮箱为空，行保留；同一邮箱可注册新账号且无关联；旧用户名注册被拒；收到「已完成」 | 第 4 步；头像对象由 PAP-97 接入后补断言（第 14 节） |
| 提醒任务在到期前 3 天发一封且只发一封（固定时钟） | 第 3 步 + 处理器单测 |
| `BANNED` 用户无法发起；`DELETED` 用户登录按密码错误 | 已有 `UserStatusFlowIT` 的封禁登录 403 + 本 Issue 处理器单测（封禁 401）；第 4 步旧邮箱登录 401 |
| 任务重复执行幂等 | 第 3、4 步各触发两次；`AccountDeletionIsolationIT` |
| `./gradlew check` + `integrationTest` 全绿 | 第 19.5 节命令的输出 |

### 19.5 回归

```bash
./gradlew spotlessApply && ./gradlew check && ./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest :patra-api:patra-gateway-boot:integrationTest
```

## 20. 实施时要实测的点

1. `UserProfileRepositoryAdapter.save` 对空集合确实删掉两张子表的行（PAP-95 契约说是），以 IT 为准。
2. 渲染器的 `Instant` 格式化不影响现有八封邮件（它们的模型里没有 `Instant`，以 `ThymeleafMailRendererTest` 全绿为准）。
3. 流程测试拨时钟后，Redis 会话的 TTL 走真实时间不受影响（PAP-64 的坑）：第 4 步那台设备是拨钟之后才登录的，没问题；如果会话的绝对过期按拨过的时钟算出来在「过去」，改成拨钟前登录。
4. `deletion_due_at` 的部分索引在空表上规划器不一定用，不影响正确性，不做执行计划断言。

## 21. README

identity README：

- 第 1 节模块表：处理器数、四个新邮件、两个新任务、`MeDeletionController`。
- 第 2 节接口表：`POST /me/deletion`、`DELETE /me/deletion`。
- 第 3 节：`idn_user` 多一列；状态机表下面一句「注销冷静期、到期执行是 PAP-100」改成指向新节。
- 第 7 节错误码：`username` / `MISMATCH`、发起时冷静期 409。
- 第 13 节结束原因表加两行。
- 新增第 14 节「注销账号」：流程、冷静期矩阵（精简）、两个任务与 mini 的 Cron、匿名化清单、配置。

## 22. 交给其他 Issue 的约束与要同步的文档

| 对象 | 内容 |
|---|---|
| Linear PAP-97 | 第 14 节三条：`erase()` 清头像键、到期执行在事务前按前缀删对象、删失败按单个用户失败处理；AC「头像对象不存在」由 PAP-97 补断言 |
| Linear PAP-102 | `POST /me/deletion` 请求 `{ username }`、200 `{ deletionDueAt }`、422 `username` / `MISMATCH`、409 `PENDING_DELETION`（已在冷静期，跳取消页）、403 `REAUTH_REQUIRED`；成功后本机会话已撤销，渲染结果屏后清 Cookie；`DELETE /me/deletion` 200 `user`（与 `/auth/me` 同构），取消不需要再认证；冷静期内踢设备照常可用 |
| Linear PAP-100 | To-Do / AC 按本 spec 措辞对齐（四封邮件、撤销令牌保留、头像由 PAP-97 接入） |
| PAP-96 spec 第 18 节 / 第 7.6 节契约表 | 「注销：发起与到期都 `revokeActive(EMAIL_CHANGE_REVERT)`」改为「发起时不动撤销令牌，到期删行」（tracked 文件，实施时随本 Issue 提交） |
| release spec 2.1.9 / 2.1.10 | 2.1.10 清单加「即将删除提醒」（共十三封）；2.1.9 注明「改邮箱撤销令牌活过发起、到期删除」与「到期任务每小时一次」（文件未跟踪，由规划会话随后端 PR 提交） |
| mini 部署（随后端 PR） | 重建 `patra_identity`；xxl-job-admin 建 `accountDeletionJob`（`0 5 * * * ?`）与 `accountDeletionReminderJob`（`0 15 * * * ?`），执行器 `patra-identity`；D6 按 release spec 手法验收 |

## 23. 设计中由我定的点与待拍板的点

**已由 release spec 定下、本 spec 照办**（PAP-98 / PAP-96 留下的两个开口）：

1. **发起时保留改邮箱撤销令牌**（第 12 节）：release spec 2.1.7「改邮箱撤销令牌例外：可并存、不被批量作废」。PAP-96 第 18 节「发起时另调 `revokeActive(EMAIL_CHANGE_REVERT)`」与之矛盾，按 release spec 不调；到期执行删行。
2. **冷静期内撤销改邮箱不顺带取消注销**（PAP-98 第 20 节第 4 条）：release spec 2.1.7「注销冷静期内也能撤，撤销不碰注销状态」。`RevertEmailChangeHandler` 不改。

**我定的（判错的代价在括号里）**：

3. 到期之后、任务执行之前登录仍可取消（用户多活不到一小时，无实害）。
4. 被封禁用户走到接口时回 401 而不是 403（第 17 节；门户只会在登录时看到 403，无影响）。
5. 用户名比对放在再认证之前（第 7.1 节；只影响错误出现的先后）。
6. 取消注销不要求再认证、不动会话（第 7.2 节；冷静期会话都是输过密码得来的）。
7. 两个任务每小时一次，分开两个 handler（第 9.1、10.1 节；改 Cron 即可）。
8. 「即将删除」与「已完成」不带安全通知片段（不是请求触发的，没有设备与 IP；只差邮件里少一块）。
9. 到期执行删令牌行而不是作废（行里有邮箱与 IP；release spec 原文就是「删除令牌」）。
10. 旧用户名行不随注销删除（第 11 节；保留期到了自然失效）。
11. 头像不预建端口，契约写到 PAP-97（第 14 节）。
12. 发起时再发起返回 409 而不是幂等 200（门户据此跳取消页；聚合的 `requestDeletion` 仍保持幂等）。

## 24. 实施结果（2026-10-22）

**提交**：`1dc34b66f`（设计与计划）→ `110838662` 用户提醒标记 → `40d4426db` 资料清空 → `023cefe2f` 落库与扫描 → `31a0fd389` 结束原因与令牌删行 → `2f122b018` 四封邮件 → `a9da1a427` 发起 → `27ed60771` 取消 → `7aba6b85c` 到期执行 → `01892702e` 提醒 → `829279dca` 配置 → `4b14e1883` 接口 → `f7da72275` 任务入口 → `5e9e2361f` 端到端测试 → 评审修正与文档。

**回归**（`./gradlew spotlessApply && ./gradlew check` + 各模块 `integrationTest`，全绿；`dumpModuleGraph` 后 `module-graph.json` 无变化）：

| 模块 | 单元测试 | 集成测试 |
|---|---|---|
| identity-domain | 458 | — |
| identity-app | 216 | — |
| identity-infra | 108 | 151 |
| identity-adapter | 12 | 79 |
| identity-boot | 23 | 88 |
| identity-session | — | 29 |
| gateway-boot | — | 61 |

**与计划的偏离**（账本 `Ruling:`）：

1. 结束原因的约束测试用原始 SQL 写入两个新取值并读回，没走仓储的 `end` + `save`：要验证的是约束，枚举名映射是通用的。
2. 四封邮件的记录没单独建单测文件，在 `ThymeleafMailRendererTest` 里覆盖。
3. 取消、到期执行、提醒三个处理器的单测用 `TransactionOperations.withoutTransaction()`。
4. 端到端测试：两个任务扫全库，同库别的用例留下的冷静期用户会被一起处理，所以对任务结果只断言「至少 1」与「重跑为 0」，对用户逐项断言；失败隔离测试断言恰好 1 个失败。首跑因测试自己用 JDBC 绑定 `Instant` 报错，改用 SQL `now()` 后全绿。
5. 第 19.2 节点名的 `UserProfileRepositoryAdapterIT`（`erase` 后保存）没有补：链接与关注领域子表删行由 `AccountDeletionFlowIT` 端到端覆盖，仓储整组替换的行为 PAP-95 的 IT 已覆盖。

**整分支评审**（opus，`1dc34b66f..5e9e2361f`）：0 Critical。Important 1 条：「全量门控没跑、文档没提交」——门控在评审前已跑（上表），文档随本节提交。补修：

- 发起注销对封禁用户 401 的单测原先缺失（第 19.4 节表里写了它），补 `RequestAccountDeletionHandlerTest.should_require_authentication_for_banned_user`（行为由 `requireActive()` 早已提供，首跑即绿，作为 AC 证据）。
- README 两处措辞、两处把 PAP-97 尚未实现的行为写成现在时的注释。

**延后的 Minor**：

- 提醒邮件主题的天数取配置的提前量，不是实际剩余天数：任务停摆后补发时主题仍写「3 天后」，提前量配成 12 小时时写「0 天后」；正文的精确时刻是对的。
- 退回提醒标记这一步本身失败时（数据库刚好不可用），这个用户以后不会再被提醒，只有 `log.error`。
- 用户名比对手写了 `strip().toLowerCase(Locale.ROOT)`，与 `Username` 的规范化重复。

**mini 上要做的**（随后端 PR 部署）：重建 `patra_identity`；xxl-job-admin 建 `accountDeletionJob`（`0 5 * * * ?`）与 `accountDeletionReminderJob`（`0 15 * * * ?`），执行器 `patra-identity`；D6 按 release spec 的手法（把 `deletion_due_at` 改到过去再手动触发）验收。本机没有起 jar 做手工验收，四条路径由 `AccountDeletionFlowIT`（PG + Redis + GreenMail + 真实过滤器链）覆盖。
