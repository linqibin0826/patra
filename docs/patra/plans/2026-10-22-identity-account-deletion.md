# identity 注销账号 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 PAP-100：`POST` / `DELETE /me/deletion`、15 天冷静期、每小时的到期执行与到期前 3 天提醒两个 XXL-Job 任务、匿名化、四封邮件。

**Architecture:** 在 PAP-93 已有的四态状态机上补一列 `deletion_reminded_at` 与几个判定方法；app 层四个处理器（发起、取消、到期执行、提醒），任务处理器按 `id` 键集分批、每个用户一个事务、单个失败不影响其他；邮件走现有 `OutgoingMail` + Thymeleaf 模板，渲染器把模型里的 `Instant` 统一按时区格式化。

**Tech Stack:** Java 25 / Spring Boot 4 / Spring Data JPA / PostgreSQL 17 / Redis / Thymeleaf / XXL-Job / JUnit 5 + AssertJ + Mockito / Testcontainers + GreenMail

**Spec:** `docs/patra/specs/2026-10-22-identity-account-deletion-design.md`（下文「spec §n」）

## Global Constraints

- **并发前提**：PAP-99 会话的未提交改动（`EmailDeliveryPort.send` 返回 `boolean`、`SessionQueryService` 移到 `session/query/`、`IdentityProperties` 删 `session.recordRetention` 等）必须先由它提交。开工前 `git status --short` 里除本 Issue 的 spec / plan 与三份不属于本 Issue 的未跟踪文档（`docs/patra/release-specs/v0.12-user-center.md`、`patra-portal/docs/design-briefs/v0.12/`、`docs/patra/design/snapshots/2026-10-22-user-center-hifi.zip`）外不能有任何改动；不满足就停下问用户。三份文档永远不 `git add`。
- 分支 `feat/v0.12-user-center-api`，不切分支、不建 worktree、不推送、不开 PR、不动 main。
- 每次提交前 `git diff --cached --name-only` 核对只有本任务的路径；提交信息 `type(identity): <中文动词起头> (PAP-100)`，body 每行 ≤ 100 字符，结尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。spotless 改了文件要 `git add` 后重提。
- 代码规范：`///` Markdown Javadoc（每个方法）、不写全类名、record ≤ 4 参数用 `of()`、≥ 5 用 `@Builder`、集合字段 `copyOf`、注入 `Clock`。
- 配置值（spec §18）：`patra.identity.deletion.cooling-period` 默认 `15d`，`reminder-lead` 默认 `3d`；非正数或 `reminder-lead >= cooling-period` 启动失败。批大小常量 `BATCH_SIZE = 100`。
- 错误契约（spec §17）：`username` / `REQUIRED`、`username` / `MISMATCH`（文案「和你的用户名不一致」）422；冷静期再发起 409 `PENDING_DELETION`；超窗 403 `REAUTH_REQUIRED`；封禁 / 已注销 401。
- 邮件主题（spec §13）：「你的 Patra 账号将被注销」「你的 Patra 账号将在 N 天后删除」「你的 Patra 账号已取消注销」「你的 Patra 账号已注销」；不放登录链接。
- XXL-Job handler 名：`accountDeletionJob`、`accountDeletionReminderJob`。
- PAP-99 提交 `453c2f824` 之后的两处测试要点：`EmailDeliveryPort` 的 mock 默认 `send` 返回 `false`（＝被拒），处理器单测的成功路径要 `when(emailDelivery.send(any())).thenReturn(true)`，否则会走进「被拒」分支；用真 `SessionTerminator` + mock 仓储时，结束记录走 `UserLoginRecordRepository.findAllByIds`，要对它打桩。
- 测试运行：单测 `./gradlew :patra-api:patra-identity:<模块>:test --tests "*X*"`；集成测试 `:integrationTest --tests "*X*"`；遇到 `NoClassDefFoundError` / `bad class file` 是 IDEA 抢 `build/classes`，重跑一次。

## Review Focus

1. 用户名确认带首尾空白、大写（`"  Chen-Yu "`）→ 视为一致 —— Task 6 `should_accept_username_ignoring_case_and_surrounding_spaces`。
2. 两个标签页先后点「取消注销」→ 第二次 200、不再发信 —— Task 7 `should_be_idempotent_for_active_user_without_mail`。
3. 扫描之后、加锁之前用户刚取消 → 跳过、不发信、不计失败 —— Task 8 `should_skip_user_no_longer_due_after_lock`。
4. 提醒任务漏跑到已过期 → 不再发提醒，交给执行任务 —— Task 3 `should_not_pick_overdue_users_for_reminder`。
5. 发起时还有一个待确认的改邮箱 → 之后点确认链接 422（令牌已作废），撤销链接仍有效 —— Task 13 流程第 1 步的断言。

---

## Before Task 1: 前提核对与提交文档

- [x] **Step 1: 核对工作区**

Run: `git status --short && git log -1 --format='%h %ci %s'`
Expected: 只有本 Issue 的 spec、plan 和三份不属于本 Issue 的未跟踪文档；否则停下问用户。

- [x] **Step 2: 提交 spec 与 plan**

```bash
git add docs/patra/specs/2026-10-22-identity-account-deletion-design.md docs/patra/plans/2026-10-22-identity-account-deletion.md
git diff --cached --name-only
git commit -m "docs(identity): 加注销账号的设计与计划 (PAP-100)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 3: Linear PAP-100 推到 BE（In Progress 类状态）**

---

### Task 1: `User` 的提醒标记与到期判定、`AccountDeletionPolicy`

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/aggregate/User.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/AccountDeletionPolicy.java`
- Test: `.../domain/src/test/java/dev/patra/identity/domain/model/aggregate/UserTest.java`、`.../domain/src/test/java/dev/patra/identity/domain/policy/AccountDeletionPolicyTest.java`

**Interfaces:**
- Produces:
  - `User` 新字段 `Instant deletionRemindedAt`（`@Getter`；`restore()` 构建器多一个同名参数）
  - `void markDeletionReminded(Instant now)`：只在 `PENDING_DELETION` 可调，否则 `InvalidUserStateTransitionException`；已有值时保留第一次
  - `void clearDeletionReminder()`：冷静期内置空，其他状态什么都不做
  - `boolean isDeletionDue(Instant now)`：冷静期且 `deletionDueAt <= now`
  - `boolean needsDeletionReminder(Instant now, Duration lead)`：冷静期、未提醒、`now < deletionDueAt <= now + lead`
  - `cancelDeletion()`、`ban(now)`、`completeDeletion(now)` 都把 `deletionRemindedAt` 置空
  - `record AccountDeletionPolicy(Duration coolingPeriod, Duration reminderLead)` + `static of(…)`；紧凑构造器：两者非空且为正、`reminderLead < coolingPeriod`，否则 `IllegalArgumentException`

- [x] **Step 1: 写失败的测试**（`UserTest`，`T = Instant.parse("2026-10-20T08:00:00Z")`，冷静期 `Duration.ofDays(15)`）

```java
@Test void should_mark_reminder_once_and_keep_first_time()          // mark(T+12d) 再 mark(T+13d) → getDeletionRemindedAt() == T+12d
@Test void should_reject_reminder_mark_outside_pending_deletion()  // ACTIVE 调 mark → InvalidUserStateTransitionException
@Test void should_clear_reminder_on_cancel_ban_and_complete()      // 三条路径各自 mark 之后 → getDeletionRemindedAt() 为 null
@Test void should_clear_reminder_explicitly_only_while_pending()   // pending: clear → null；ACTIVE: clear 不抛
@Test void should_be_due_exactly_at_due_time()                     // isDeletionDue(T+15d) 为 true，isDeletionDue(T+15d-1s) 为 false，ACTIVE 恒 false
@Test void should_need_reminder_only_inside_lead_window()          // lead 3d：T+12d 为 true，T+12d-1s 为 false，T+15d（已到期）为 false，mark 之后为 false
```

`AccountDeletionPolicyTest`：

```java
@Test void should_reject_non_positive_durations()                  // ZERO、负数 → IllegalArgumentException
@Test void should_reject_lead_not_shorter_than_cooling_period()    // of(15d, 15d) → IllegalArgumentException
@Test void should_accept_defaults()                                // of(15d, 3d) 两个访问器原样返回
```

- [x] **Step 2: 运行确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*UserTest*" --tests "*AccountDeletionPolicyTest*"`
Expected: 编译失败（缺 `markDeletionReminded`、`AccountDeletionPolicy`）

- [x] **Step 3: 实现上面的签名**；类注释的状态迁移说明补一句提醒标记的清除规则。

- [x] **Step 4: 运行确认通过**（同 Step 2 命令，Expected: PASS；`UserTest` 原有用例不受影响）

- [x] **Step 5: 提交** `feat(identity): 给用户加注销提醒标记与到期判定 (PAP-100)`

---

### Task 2: `UserProfile.erase()`

**Files:**
- Modify: `.../domain/src/main/java/dev/patra/identity/domain/model/aggregate/UserProfile.java`
- Test: `.../domain/src/test/java/dev/patra/identity/domain/model/aggregate/UserProfileTest.java`

**Interfaces:**
- Produces: `void erase()`：内容整体换成空的 `ProfileContent`（昵称、简介、链接、身份大类、职称、机构、科室、关注领域都空），隐私换成 `PrivacySettings.defaults()`；Javadoc 写明「注销到期时用，PAP-97 在这里补头像键」

- [x] **Step 1: 写失败的测试** `should_erase_content_and_reset_privacy`：先 `edit` 一份带昵称、简介、两条链接、机构、两个关注领域的内容并 `changePrivacy` 成 `SELF` / `SELF` / `true`，`erase()` 后所有 `getXxx()` 为空 / 空列表、`getPrivacy()` 等于 `PrivacySettings.defaults()`、`getUserId()` 不变。
- [x] **Step 2: 运行确认失败**：`./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*UserProfileTest*"` → 编译失败
- [x] **Step 3: 实现**（复用私有 `apply(ProfileContent.builder().build())`）
- [x] **Step 4: 运行确认通过**
- [x] **Step 5: 提交** `feat(identity): 加资料的注销清空方法 (PAP-100)`

---

### Task 3: `deletion_reminded_at` 落库与两个扫描查询

**Files:**
- Modify: `.../infra/src/main/resources/db/migration/V1__init_identity_schema.sql`（`idn_user` 段）
- Modify: `.../infra/.../persistence/entity/UserEntity.java`、`converter/mapper/UserJpaMapper.java`、`dao/UserDao.java`、`UserRepositoryAdapter.java`
- Modify: `.../domain/.../port/repository/UserRepository.java`
- Test: `.../infra/src/integrationTest/java/dev/patra/identity/infra/adapter/persistence/UserRepositoryAdapterIT.java`

**Interfaces:**
- Consumes: Task 1 的 `deletionRemindedAt`、`markDeletionReminded`
- Produces:
  - `List<Long> UserRepository.findIdsDueForDeletion(Instant now, long afterId, int limit)`：`status = 'PENDING_DELETION' AND deletion_due_at <= now AND id > afterId ORDER BY id LIMIT limit`
  - `List<Long> UserRepository.findIdsDueForDeletionReminder(Instant now, Instant dueUntil, long afterId, int limit)`：`status = 'PENDING_DELETION' AND deletion_reminded_at IS NULL AND deletion_due_at > now AND deletion_due_at <= dueUntil AND id > afterId ORDER BY id LIMIT limit`
  - `V1`：列 `deletion_reminded_at timestamptz(6) NULL`（放在 `deletion_due_at` 后）、约束 `ck_idn_user_deletion_reminded_at CHECK (deletion_reminded_at IS NULL OR status = 'PENDING_DELETION')`、部分索引 `idx_idn_user_deletion_due_at ON idn_user (deletion_due_at) WHERE status = 'PENDING_DELETION'`、列注释（spec §15 原文）

- [x] **Step 1: 写失败的测试**（`UserRepositoryAdapterIT`，用户经仓储 `register` + `requestDeletion` 造出）

```java
@Test void should_round_trip_deletion_reminded_at()                 // mark 后 save，findById 读回同一时刻；cancel 后读回 null
@Test void should_reject_reminded_at_outside_pending_deletion()     // jdbc 直接 UPDATE 一个 ACTIVE 行的 deletion_reminded_at → DataIntegrityViolationException
@Test void should_find_due_ids_in_id_order_after_cursor()           // 3 个到期 + 1 个未到期 + 1 个 ACTIVE：limit 2 取前两个，afterId=第二个 取第三个
@Test void should_find_reminder_ids_inside_window_only()            // 窗口内未提醒的入选；已提醒的、窗口外的不入选
@Test void should_not_pick_overdue_users_for_reminder()             // deletion_due_at <= now 的不入选（Review Focus 4）
```

- [x] **Step 2: 运行确认失败**：`./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*UserRepositoryAdapterIT*"` → 编译失败
- [x] **Step 3: 实现**：`UserDao` 两个 `@Query`（JPQL 或原生，`Limit` / `Pageable.ofSize(limit)` 二选一，注意文本块行尾空格坑）；实体加列；映射器双向带上；适配器委托。
- [x] **Step 4: 运行确认通过**；再跑整个 `patra-identity-infra:integrationTest` 确认 `V1` 改动没破坏其他 IT。
- [x] **Step 5: 提交** `feat(identity): 落库注销提醒标记并加到期扫描查询 (PAP-100)`

---

### Task 4: 两个结束原因与令牌按用户删行

**Files:**
- Modify: `.../domain/.../model/enums/LoginEndReason.java`
- Modify: `V1__init_identity_schema.sql`（`ck_idn_user_login_record_end_reason` 与 `end_reason` 列注释）
- Modify: `.../domain/.../port/repository/EmailTokenRepository.java`、`.../infra/.../dao/EmailTokenDao.java`、`EmailTokenRepositoryAdapter.java`
- Test: `.../infra/src/integrationTest/.../persistence/UserLoginRecordRepositoryAdapterIT.java`、`EmailTokenRepositoryAdapterIT.java`

**Interfaces:**
- Produces:
  - `LoginEndReason.DELETION_REQUESTED`（「申请注销，全部会话被撤销」）、`LoginEndReason.DELETION_COMPLETED`（「注销到期执行，冷静期内的会话被撤销」）
  - `int EmailTokenRepository.deleteAllByUserId(long userId)`：删该用户全部令牌行（含撤销令牌），返回行数；DAO 用 `@Modifying @Query` 批量删

- [x] **Step 1: 写失败的测试**

```java
// UserLoginRecordRepositoryAdapterIT
@Test void should_end_records_with_deletion_reasons()   // 两条记录分别 end(DELETION_REQUESTED / DELETION_COMPLETED) 后 save、读回原因一致（CHECK 放行）
// EmailTokenRepositoryAdapterIT
@Test void should_delete_all_tokens_of_one_user_only()  // 用户 A 有验证、重设、撤销三条，用户 B 一条 → deleteAllByUserId(A) == 3，B 的仍在
```

- [x] **Step 2: 运行确认失败**（infra `integrationTest`，两个类）→ 编译失败
- [x] **Step 3: 实现**
- [x] **Step 4: 运行确认通过**
- [x] **Step 5: 提交** `feat(identity): 加注销的两个结束原因与令牌按用户删除 (PAP-100)`

---

### Task 5: 四封邮件与渲染器的时间格式化

**Files:**
- Create: `.../domain/.../port/mail/AccountDeletionRequestedMail.java`、`AccountDeletionReminderMail.java`、`AccountDeletionCancelledMail.java`、`AccountDeletionCompletedMail.java`
- Modify: `.../infra/.../mail/ThymeleafMailRenderer.java`
- Create: `.../infra/src/main/resources/mail/html/account-deletion-{requested,reminder,cancelled,completed}.html` 与 `mail/text/` 下同名 `.txt`
- Test: `.../infra/src/test/java/dev/patra/identity/infra/adapter/mail/ThymeleafMailRendererTest.java`、各记录的单测放 `.../domain/src/test/.../port/mail/`（若已有同类测试文件就照写，否则只在渲染器测试里覆盖）

**Interfaces:**
- Produces（结构照 `PasswordChangedMail`：紧凑构造器判空、`TEMPLATE`、`SUBJECT`、`model()`、`toString()` 不含收件人）：
  - `AccountDeletionRequestedMail(EmailAddress to, String displayName, Instant deletionDueAt, SecurityNotice notice)` + `of`；`TEMPLATE = "account-deletion-requested"`；`SUBJECT = "你的 Patra 账号将被注销"`；`NOT_ME_ADVICE = "如果不是你，说明有人知道了你的密码：请在到期前登录并取消注销，然后立即修改密码；登录不了就用『忘记密码』重设"`；模型 `displayName`、`deletionDueAt`、`notice`
  - `AccountDeletionReminderMail(EmailAddress to, String displayName, Instant deletionDueAt, long daysLeft)` + `of`；`TEMPLATE = "account-deletion-reminder"`；`subject()` = `"你的 Patra 账号将在 " + daysLeft + " 天后删除"`；模型 `displayName`、`deletionDueAt`、`daysLeft`
  - `AccountDeletionCancelledMail(EmailAddress to, String displayName, SecurityNotice notice)` + `of`；`TEMPLATE = "account-deletion-cancelled"`；`SUBJECT = "你的 Patra 账号已取消注销"`；`NOT_ME_ADVICE = "如果不是你，说明有人知道了你的密码：请立即修改密码，并在登录设备里退出不认识的设备；仍想注销可以重新申请"`
  - `AccountDeletionCompletedMail(EmailAddress to, String displayName, String username)` + `of`；`TEMPLATE = "account-deletion-completed"`；`SUBJECT = "你的 Patra 账号已注销"`；模型 `displayName`、`username`
  - 渲染器：`notice` 之外，模型里值是 `Instant` 的变量换成 `SecurityNoticeView.formatTime(instant, zone)` 的字符串
- 模板文案要点见 spec §13 表格；有通知片段的两封引用 `~{html/fragments/security-notice :: notice(${notice})}` / `text/fragments/security-notice`；四封都不出现 `http` 链接（页脚固定内容除外）。

- [x] **Step 1: 写失败的测试**（`ThymeleafMailRendererTest`，时区 `Asia/Shanghai`，到期 `2026-11-04T00:00:00Z`）

```java
@Test void should_render_deletion_requested_with_due_time_and_notice()   // 文本与 HTML 都含 "2026-11-04 08:00 (UTC+8)"、昵称、设备、IP；主题原样
@Test void should_render_deletion_reminder_with_days_left_subject()      // daysLeft 3 → 主题 "你的 Patra 账号将在 3 天后删除"；含到期时刻；不含 "设备："
@Test void should_render_deletion_cancelled_with_notice()                // 含 NOT_ME_ADVICE 原文
@Test void should_render_deletion_completed_with_username()              // 含 "@chen-yu"；不含 "设备："
@Test void should_not_link_anywhere_in_deletion_mails()                  // 四封的文本部分都不含 "#token=" 与门户 baseUrl
```

- [x] **Step 2: 运行确认失败**：`./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests "*ThymeleafMailRendererTest*"` → 编译失败
- [x] **Step 3: 实现四个记录、渲染器改动、八个模板**
- [x] **Step 4: 运行确认通过**（整个 `ThymeleafMailRendererTest`，现有八封的用例保持绿，验证 spec §20 第 2 条）
- [x] **Step 5: 提交** `feat(identity): 加注销的四封邮件 (PAP-100)`

---

### Task 6: 发起注销 `RequestAccountDeletionHandler`

**Files:**
- Create: `.../app/src/main/java/dev/patra/identity/app/usecase/deletion/RequestAccountDeletionCommand.java`、`AccountDeletionRequested.java`、`RequestAccountDeletionHandler.java`
- Modify: `.../domain/.../model/vo/UserFieldViolations.java`
- Test: `.../app/src/test/java/dev/patra/identity/app/usecase/deletion/RequestAccountDeletionHandlerTest.java`

**Interfaces:**
- Consumes: Task 1 `AccountDeletionPolicy`；Task 4 `LoginEndReason.DELETION_REQUESTED`；Task 5 `AccountDeletionRequestedMail`
- Produces:
  - `UserFieldViolations.MISMATCH = "MISMATCH"`、`static FieldViolation usernameMismatch()` → `FieldViolation.of(USERNAME, MISMATCH, "和你的用户名不一致")`
  - `record RequestAccountDeletionCommand(String username, String clientIp, String userAgent) implements Command<AccountDeletionRequested>` + `of`
  - `record AccountDeletionRequested(Instant deletionDueAt)` + `of`
  - `RequestAccountDeletionHandler`（`@Component @RequiredArgsConstructor`），依赖：`CurrentUserPort`、`UserRepository`、`UserReadPort`、`ReauthenticationGuard`、`EmailTokenRepository`、`SessionTerminator`、`ClientOriginResolver`、`EmailDeliveryPort`、`AccountDeletionPolicy`、`TransactionOperations`、`Clock`
- 顺序严格按 spec §7.1 第 1–7 步；事务里只调 `tokens.revokeAllActive(userId, now)`，**不调** `revokeActive(…, EMAIL_CHANGE_REVERT, …)`；`send` 返回 `false` 时 `log.warn` 后照常返回。

- [x] **Step 1: 写失败的测试**（Mockito；`Clock.fixed(T)`；`TransactionOperations` 用直接执行回调的桩）

```java
@Test void should_reject_blank_username_with_required()            // "  " → InvalidUserFieldsException，errors 含 username/REQUIRED；没碰仓储
@Test void should_reject_mismatched_username_before_reauth()        // "someone-else" → username/MISMATCH；reauthenticationGuard 没被调
@Test void should_accept_username_ignoring_case_and_surrounding_spaces() // "  Chen-Yu " 对 chen-yu 成功（Review Focus 1）
@Test void should_reject_when_already_pending_deletion()            // 冷静期用户 → UserPendingDeletionException；守卫没被调
@Test void should_propagate_reauth_required_without_changes()       // 守卫抛 ReauthenticationRequiredException → 无 save、无 terminateAll、无发信
@Test void should_request_deletion_revoke_tokens_and_all_sessions() // 成功：save 的用户状态 PENDING_DELETION、到期 T+15d；revokeAllActive(userId, T)；
                                                                     //   never revokeActive(anyLong(), eq(EMAIL_CHANGE_REVERT), any())；terminateAll(userId, DELETION_REQUESTED, T)；
                                                                     //   InOrder：事务回调结束后才 send；邮件是 AccountDeletionRequestedMail、deletionDueAt = T+15d；返回值 = T+15d
@Test void should_still_succeed_when_mail_rejected()                // send 返回 false → 照常返回到期时间
```

- [x] **Step 2: 运行确认失败**：`./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*RequestAccountDeletionHandlerTest*"` → 编译失败
- [x] **Step 3: 实现**
- [x] **Step 4: 运行确认通过**
- [x] **Step 5: 提交** `feat(identity): 加发起注销的处理器 (PAP-100)`

---

### Task 7: 取消注销 `CancelAccountDeletionHandler`

**Files:**
- Create: `.../app/.../usecase/deletion/CancelAccountDeletionCommand.java`、`CancelAccountDeletionHandler.java`
- Test: `.../app/src/test/.../usecase/deletion/CancelAccountDeletionHandlerTest.java`

**Interfaces:**
- Produces:
  - `record CancelAccountDeletionCommand(String clientIp, String userAgent) implements Command<UserAccountReadModel>` + `of`
  - `CancelAccountDeletionHandler`，依赖：`CurrentUserPort`、`UserRepository`、`UserReadPort`、`ClientOriginResolver`、`EmailDeliveryPort`、`TransactionOperations`、`Clock`
- 行为见 spec §7.2：锁行；封禁 / 已注销抛 `AuthenticationRequiredException`；`cancelDeletion()`；只有原来是 `PENDING_DELETION` 才在提交后发 `AccountDeletionCancelledMail`；返回 `findAccount` 的结果。

- [x] **Step 1: 写失败的测试**

```java
@Test void should_cancel_pending_deletion_and_send_mail()           // save 的用户 ACTIVE、到期为 null；发信一次；返回 findAccount 的结果
@Test void should_be_idempotent_for_active_user_without_mail()      // ACTIVE → 不 save（或 save 无变化均可，断言状态）、不发信、照常返回（Review Focus 2）
@Test void should_require_authentication_for_banned_or_deleted()    // BANNED、DELETED → AuthenticationRequiredException，不发信
```

会话不动由构造器保证：处理器不依赖 `SessionTerminator` / `SessionStorePort`。

- [x] **Step 2: 运行确认失败** → 编译失败
- [x] **Step 3: 实现**
- [x] **Step 4: 运行确认通过**
- [x] **Step 5: 提交** `feat(identity): 加取消注销的处理器 (PAP-100)`

---

### Task 8: 到期执行 `ExecuteDueAccountDeletionsHandler`

**Files:**
- Create: `.../app/.../usecase/deletion/ExecuteDueAccountDeletionsCommand.java`、`DeletionRunResult.java`、`ExecuteDueAccountDeletionsHandler.java`
- Test: `.../app/src/test/.../usecase/deletion/ExecuteDueAccountDeletionsHandlerTest.java`

**Interfaces:**
- Consumes: Task 1 `isDeletionDue`；Task 2 `erase()`；Task 3 `findIdsDueForDeletion`；Task 4 `deleteAllByUserId`、`DELETION_COMPLETED`；Task 5 `AccountDeletionCompletedMail`
- Produces:
  - `record ExecuteDueAccountDeletionsCommand() implements Command<DeletionRunResult>` + `of()`
  - `record DeletionRunResult(int completed, int failed)` + `of`
  - `ExecuteDueAccountDeletionsHandler`，`static final int BATCH_SIZE = 100`；依赖：`UserRepository`、`UserReadPort`、`UserPasswordCredentialRepository`、`UserProfileRepository`、`EmailTokenRepository`、`SessionTerminator`、`EmailDeliveryPort`、`TransactionOperations`、`Clock`
- 算法（spec §9.2、§9.3、§11）：

```
now = clock.instant(); afterId = 0
loop:
  ids = users.findIdsDueForDeletion(now, afterId, BATCH_SIZE)
  for id in ids:
    afterId = id
    try:
      done = transactions.execute(锁行 → !isDeletionDue(now) 则返回 empty →
               取 email 与 findAccount 的 displayName、username →
               completeDeletion(now) + save → credentials.deleteByUserId →
               profiles.findByUserId(id).ifPresent(erase + save) → tokens.deleteAllByUserId →
               sessionTerminator.terminateAll(id, DELETION_COMPLETED, now) → 返回 Completed(email, displayName, username))
      done.ifPresent(发 AccountDeletionCompletedMail；send 为 false 只 log.warn；completed++)
    catch RuntimeException: log.error("注销到期执行失败: userId={}", id, e); failed++
  until ids.size() < BATCH_SIZE
log.info 汇总；return DeletionRunResult.of(completed, failed)
```

- [x] **Step 1: 写失败的测试**

```java
@Test void should_anonymize_due_user_and_send_completed_mail()   // 状态 DELETED、邮箱空；deleteByUserId、erase 后 save、deleteAllByUserId、terminateAll(id, DELETION_COMPLETED, now)；
                                                                  //   邮件收件人是注销前的邮箱、带用户名；结果 (1, 0)
@Test void should_skip_user_no_longer_due_after_lock()           // 锁内读到 ACTIVE（刚取消）→ 不 save、不发信、结果 (0, 0)（Review Focus 3）
@Test void should_continue_after_one_user_fails()                // 两个 ID，第一个 credentials.deleteByUserId 抛 → 第二个照样完成，结果 (1, 1)
@Test void should_page_by_id_cursor()                             // 第一页 100 个 ID → 以最后一个 ID 为 afterId 再查第二页；第二页 1 个后停止
@Test void should_count_completed_even_if_mail_rejected()        // send 返回 false → 结果 (1, 0)
```

- [x] **Step 2: 运行确认失败** → 编译失败
- [x] **Step 3: 实现**
- [x] **Step 4: 运行确认通过**
- [x] **Step 5: 提交** `feat(identity): 加注销到期执行的处理器 (PAP-100)`

---

### Task 9: 到期前提醒 `SendAccountDeletionRemindersHandler`

**Files:**
- Create: `.../app/.../usecase/deletion/SendAccountDeletionRemindersCommand.java`、`ReminderRunResult.java`、`SendAccountDeletionRemindersHandler.java`
- Test: `.../app/src/test/.../usecase/deletion/SendAccountDeletionRemindersHandlerTest.java`

**Interfaces:**
- Consumes: Task 1 `needsDeletionReminder`、`markDeletionReminded`、`clearDeletionReminder`、`AccountDeletionPolicy`；Task 3 `findIdsDueForDeletionReminder`；Task 5 `AccountDeletionReminderMail`
- Produces:
  - `record SendAccountDeletionRemindersCommand() implements Command<ReminderRunResult>` + `of()`
  - `record ReminderRunResult(int sent, int failed)` + `of`
  - `SendAccountDeletionRemindersHandler`，`BATCH_SIZE = 100`；依赖：`UserRepository`、`UserReadPort`、`AccountDeletionPolicy`、`EmailDeliveryPort`、`TransactionOperations`、`Clock`
- 每个用户：事务 {锁行 → `needsDeletionReminder(now, lead)` 否则空 → `markDeletionReminded(now)` + save → 取邮箱、昵称、到期} → 提交后发 `AccountDeletionReminderMail.of(email, displayName, dueAt, lead.toDays())`；`send` 为 `false` 时另一个事务 {锁行 → `clearDeletionReminder()` + save}，计 `failed`；异常同 Task 8 计 `failed`。扫描用 `findIdsDueForDeletionReminder(now, now.plus(lead), afterId, BATCH_SIZE)`。

- [x] **Step 1: 写失败的测试**

```java
@Test void should_mark_and_send_reminder()                     // save 的用户 deletionRemindedAt == now；邮件 daysLeft 3；结果 (1, 0)
@Test void should_skip_when_already_reminded_after_lock()      // 锁内已有标记 → 不 save、不发信
@Test void should_unmark_when_mail_rejected()                  // send false → 第二次 save 的用户 deletionRemindedAt 为 null；结果 (0, 1)
@Test void should_continue_after_one_user_fails()              // 同 Task 8
```

- [x] **Step 2: 运行确认失败** → 编译失败
- [x] **Step 3: 实现**
- [x] **Step 4: 运行确认通过**
- [x] **Step 5: 提交** `feat(identity): 加注销到期前提醒的处理器 (PAP-100)`

---

### Task 10: 配置 `patra.identity.deletion.*`

**Files:**
- Modify: `.../boot/src/main/java/dev/patra/identity/config/IdentityProperties.java`、`IdentityConfiguration.java`
- Test: `.../boot/src/test/java/dev/patra/identity/config/IdentityConfigurationTest.java`

**Interfaces:**
- Produces: `IdentityProperties.Deletion(@DefaultValue("15d") Duration coolingPeriod, @DefaultValue("3d") Duration reminderLead)`，`IdentityProperties` 加 `@DefaultValue Deletion deletion`（Javadoc `@param deletion 注销：冷静期与提醒提前量`）；`@Bean AccountDeletionPolicy accountDeletionPolicy(IdentityProperties properties)`

- [x] **Step 1: 写失败的测试**（照 `IdentityConfigurationTest` 现有的 `ApplicationContextRunner` 写法）

```java
@Test void should_bind_deletion_defaults()                    // 不配 → policy 为 (15d, 3d)
@Test void should_fail_startup_when_lead_not_shorter()        // cooling-period=3d, reminder-lead=3d → 上下文启动失败
```

- [x] **Step 2: 运行确认失败**：`./gradlew :patra-api:patra-identity:patra-identity-boot:test --tests "*IdentityConfigurationTest*"`
- [x] **Step 3: 实现**
- [x] **Step 4: 运行确认通过**
- [x] **Step 5: 提交** `feat(identity): 加注销冷静期与提醒提前量配置 (PAP-100)`

---

### Task 11: `MeDeletionController`

**Files:**
- Create: `.../adapter/src/main/java/dev/patra/identity/adapter/rest/me/MeDeletionController.java`、`request/RequestAccountDeletionRequest.java`、`response/AccountDeletionRequestedResponse.java`
- Test: `.../adapter/src/integrationTest/java/dev/patra/identity/adapter/rest/me/MeDeletionControllerIT.java`

**Interfaces:**
- Consumes: Task 6、7 的命令与结果；`CurrentUserResponse.from(UserAccountReadModel)`
- Produces:
  - `@RequestMapping("/me/deletion")`，`@Tag(name = "Me")`
  - `@PostMapping AccountDeletionRequestedResponse request(@RequestBody RequestAccountDeletionRequest request, HttpServletRequest http, @RequestHeader(value = USER_AGENT, required = false) String userAgent)` → 200
  - `@DeleteMapping CurrentUserResponse cancel(HttpServletRequest http, @RequestHeader(value = USER_AGENT, required = false) String userAgent)` → 200
  - `record RequestAccountDeletionRequest(String username)`；`record AccountDeletionRequestedResponse(Instant deletionDueAt)` + `static from(AccountDeletionRequested)`

- [x] **Step 1: 写失败的测试**（照 `MeEmailControllerIT`：`@WebMvcTest @Import(MeDeletionController.class) @AutoConfigureRestTestClient`，`@MockitoBean CommandBus`）

```java
@Test void should_request_deletion_with_200_and_due_time()   // 体 {"deletionDueAt":"2026-11-04T08:00:00Z"}；ArgumentCaptor：命令 username "chen-yu"、userAgent 原值、clientIp 非空
@Test void should_cancel_deletion_with_200_and_user()        // 体含 "status":"ACTIVE"、"deletionDueAt":null
@Test void should_map_mismatch_to_422()                      // 命令总线抛 InvalidUserFieldsException(usernameMismatch()) → 422，errors[0] username/MISMATCH
@Test void should_map_pending_deletion_to_409()              // 抛 UserPendingDeletionException → 409，traits 含 PENDING_DELETION
```

- [x] **Step 2: 运行确认失败**：`./gradlew :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*MeDeletionControllerIT*"`
- [x] **Step 3: 实现**
- [x] **Step 4: 运行确认通过**
- [x] **Step 5: 提交** `feat(identity): 加注销与取消注销接口 (PAP-100)`

---

### Task 12: 两个 XXL-Job 入口

**Files:**
- Create: `.../adapter/src/main/java/dev/patra/identity/adapter/scheduler/job/AccountDeletionScheduleJob.java`、`AccountDeletionReminderScheduleJob.java`
- Test: `.../adapter/src/test/java/dev/patra/identity/adapter/scheduler/job/AccountDeletionScheduleJobTest.java`、`AccountDeletionReminderScheduleJobTest.java`

**Interfaces:**
- Consumes: Task 8、9 的命令与结果
- Produces: `@XxlJob("accountDeletionJob") void execute()`、`@XxlJob("accountDeletionReminderJob") void execute()`；`failed > 0` → `XxlJobHelper.handleFail("已注销 N 个，失败 M 个")`，否则 `handleSuccess("已注销 N 个")`（提醒：「已提醒 N 个，失败 M 个」）；命令总线抛异常 → `handleFail("执行失败：…")`。Javadoc 写 mini Cron（`0 5 * * * ?` / `0 15 * * * ?`）。

- [x] **Step 1: 写失败的测试**（照 `EmailTokenCleanupScheduleJobTest` 对 `XxlJobHelper` 的处理方式）

```java
@Test void should_report_success_when_nothing_failed()
@Test void should_report_failure_when_some_users_failed()
@Test void should_report_failure_when_command_throws()
```

- [x] **Step 2–4: 失败 → 实现 → 通过**（`./gradlew :patra-api:patra-identity:patra-identity-adapter:test --tests "*AccountDeletion*JobTest*"`）
- [x] **Step 5: 提交** `feat(identity): 加注销到期执行与提醒的定时任务入口 (PAP-100)`

---

### Task 13: 端到端流程与网关断言

**Files:**
- Create: `.../boot/src/integrationTest/java/dev/patra/identity/AccountDeletionFlowIT.java`（`extends IdentityFlowITSupport`）、`AccountDeletionIsolationIT.java`
- Modify（若需要）：`.../boot/src/integrationTest/.../config/IdentityConfigRefreshIT.java`（新 bean 的 mock，PAP-96 先例）
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/**/GatewayAuthenticationIT.java`

**Interfaces:**
- Consumes: 全部前序任务；`IdentityFlowITSupport` 的 `register`、`login`、`exchange`、`assertionFor`、`latestSessionIdOf`、`endReasonOf`、`activeTokenPurposesOf`、`mailFor`、`redis`、`sessionKey`、`clock`（`MutableClock`，起点 `T0`）、`jdbcTemplate`；`@Autowired CommandBus commandBus` 跑两个任务命令

- [x] **Step 1: 写测试**（每条一个 `@Test`，spec §19.2）

```java
@Test void should_request_deletion_after_reauth_and_username_check()
  // 注册 A（用户名 u-del-req）→ 另登录一台 B → POST /me/email/change 拿到发往新邮箱的确认令牌（不确认）
  // clock.advance(11m) 后 POST /me/deletion → 403 REAUTH_REQUIRED；clock.set(T0)
  // 用户名 "nope" → 422 username/MISMATCH
  // 正确 → 200，deletionDueAt == T0 + 15d
  // redis.hasKey(sessionKey(tokenA)) 与 B 都为 false；endReasonOf(两条) == DELETION_REQUESTED
  // 匿名 GET /users/u-del-req → 404；收到主题「你的 Patra 账号将被注销」，文本含到期时刻
  // 改邮箱确认令牌 POST /auth/email/change/confirm → 422（Review Focus 5）
@Test void should_keep_revert_link_alive_after_request()
  // 完成一次改邮箱（确认），旧邮箱拿到撤销令牌 → 发起注销 → activeTokenPurposesOf 只剩 EMAIL_CHANGE_REVERT
@Test void should_allow_login_and_cancel_during_cooling_period()
  // 发起后 login → 200 且 user.status PENDING_DELETION；/auth/me 同；PUT /me/profile 409 traits PENDING_DELETION；
  // 再次 POST /me/deletion 409；DELETE /me/deletion 200 status ACTIVE；PUT /me/profile 200；收到「已取消注销」
@Test void should_send_reminder_once()
  // 发起 → clock.advance(12d) → commandBus.handle(SendAccountDeletionRemindersCommand.of()) 两次 → 结果 (1,0)、(0,0)；
  // 收件箱里主题「…将在 3 天后删除」恰好一封
@Test void should_anonymize_on_due_and_free_email_but_not_username()
  // 设资料（昵称、简介、链接、关注领域需 catalog：只设不需要 catalog 的字段 + 直接 jdbc 插一条 research_interest）→ 发起
  // → clock.advance(15d) → login（冷静期会话）→ 执行任务两次：(1,0)、(0,0)
  // jdbc：status DELETED、email NULL、行在；credential / link / research_interest / email_token 行数 0；
  //   profile 行在且 display_name、bio 为 NULL，隐私 PUBLIC/PUBLIC/false
  // 冷静期会话的 redis 键不在、endReasonOf == DELETION_COMPLETED；「已注销」恰好一封，发到原邮箱
  // 原邮箱原密码 login → 401；register(原邮箱大写形式, 新用户名) → 201 且 userId 不同；register(新邮箱, 原用户名) → 409 username/ALREADY_TAKEN
```

`AccountDeletionIsolationIT`（`@MockitoSpyBean UserPasswordCredentialRepository credentials`）：

```java
@Test void should_finish_other_users_when_one_fails()
  // 两个用户都发起并到期；doThrow(new IllegalStateException()).when(credentials).deleteByUserId(第一个 ID)
  // → 执行任务结果 (1,1)；第一个仍 PENDING_DELETION、邮箱仍在；第二个 DELETED
```

`GatewayAuthenticationIT`：匿名 `POST /patra-identity/me/deletion`、`DELETE /patra-identity/me/deletion` 都 401（照该类「未列出的 identity 路径要登录」那条用例）。

- [x] **Step 2: 运行**：`./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*AccountDeletion*"` 与 `:patra-api:patra-gateway-boot:integrationTest --tests "*GatewayAuthenticationIT*"`
Expected: 全过（前序任务已各自 TDD 过；首跑全绿按 PAP-96 先例不为凑 RED 改测试，失败则按 systematic-debugging 处理）。注意 spec §20 第 3 条：冷静期会话在拨钟之后登录。
- [x] **Step 3: 提交** `test(identity): 加注销的端到端流程测试 (PAP-100)`（含网关测试文件）

---

### Task 14: 文档、全量回归、Linear

**Files:**
- Modify: `patra-api/patra-identity/README.md`（spec §21 列的六处）
- Modify: `docs/patra/specs/2026-10-21-identity-email-verification-design.md`（第 7.6 节表与第 18 节表里注销那一行，改为「发起时不动撤销令牌，到期删行」）
- Modify: `docs/patra/specs/2026-10-22-identity-account-deletion-design.md`（状态改「已实施」，末尾加「实施结果」一节：偏离、实测、回归数字）
- Modify: `docs/patra/plans/2026-10-22-identity-account-deletion.md`（勾选）

- [x] **Step 1: 改 README 与两份 spec**
- [x] **Step 2: 模块图核对**：`./gradlew dumpModuleGraph` 后 `git diff --stat patra-infra/cd/module-graph.json`，Expected: 无变化（有变化就一并提交）
- [x] **Step 3: 全量回归**

Run（后台跑，> 2 分钟）：

```bash
./gradlew spotlessApply && ./gradlew check && ./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest :patra-api:patra-gateway-boot:integrationTest
```

Expected: `BUILD SUCCESSFUL`；记下各模块 IT 数写进 spec 的实施结果。spotless 改了文件就补一个 `style(identity): 按 gjf 格式化 (PAP-100)` 提交（只含本 Issue 的路径）。

- [x] **Step 4: 提交** `docs(identity): 写回注销账号的 README 与实施结果 (PAP-100)`
- [x] **Step 5: Linear**
  - PAP-100：To-Do / AC 措辞按 spec §22 对齐并勾选；评论附 AC 证据（spec §19.4 的表 + 回归输出摘要 + 提交范围）；状态推到 Review。
  - PAP-97：评论「来自 PAP-100 的契约」（spec §14 三条）。
  - PAP-102：评论「来自 PAP-100 的契约」（spec §22 PAP-102 行）。
- [x] **Step 6: 交接**：在共享笔记目录写 `pap-100-account-deletion-implementation-handover.md`（产物、回归、裁决、mini 上要做的：重建库、xxl-job-admin 建两个任务与 Cron、D6 验收手法；release spec 2.1.9 / 2.1.10 待同步）。
