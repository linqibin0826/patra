# 提醒邮件：离站逐条与每日汇总 Implementation Plan

> **For agentic workers:** 本计划按 superpowers:executing-plans 在当前会话内联执行（`.claude/rules/superpowers.md`、用户 10/21 的决定：不用 subagent-driven），每个任务严格 TDD。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** notification 的 `mail` 子域发「回复我的」「@ 我的」提醒邮件：离站逐条（入队、到点复查、同类合并）与每日汇总（8:00 建名单），带 RFC 8058 退订头；依赖不可用时退避重试、不丢。

**Architecture:** 同步 `@EventListener` 在信箱写入事务里入队（`mail.reminder_queue`）；XXL `reminderMailJob` 每分钟先把到点的合成投递记录（`mail.reminder_delivery`），再逐条装配（检查、取名字与对象、拼内容），经 PAP-105 的 `MailDeliveryPort` 按模板名渲染并发出；`dailyDigestJob` 在 8 点这一小时里每 10 分钟补建汇总投递。规则全在 domain 的纯 Java 策略里，app 编排、infra 用 JdbcClient 与 Thymeleaf。

**Tech Stack:** Java 25、Spring Boot 4.1.2、JdbcClient、Flyway、PostgreSQL 17、Thymeleaf、XXL-Job、Testcontainers、WireMock、GreenMail、JUnit 5 + AssertJ + Mockito。

**Spec:** `docs/patra/specs/2026-10-31-notification-reminder-mail-design.md`（下称 spec，节号指它）

## Global Constraints

- 根包 `dev.patra.notification`；分包照 `.claude/rules/tech/package-layout.md`；新类进 `mail` 子域，另在 inbox、preference、kernel 只**加**方法（spec 第 9 节）；`NotificationArchitectureTest` 加 `allowingDependency` 两条：`mail → inbox`、`mail → preference`。
- 方法全写 `///` Javadoc（Markdown）；不写全类名；record 参数 ≤ 4 用 `of()`、≥ 5 用 `@Builder`（不同时给 `of()`）；集合字段紧凑构造器 `List.copyOf` / `Map.copyOf`。
- 时间一律取注入的 `Clock`；显示与 8:00 边界用 `patra.notification.mail.zone`（默认 `Asia/Shanghai`）。
- 等待 `Duration.ofMinutes(10)`；退避 `min(2^(attempts−1), 30)` 分钟；调用过发信端口之后最多重试到 `first_sent_at + 24h`，之后 `EXPIRED`；一封最多列 10 条；单条摘录 300 字素保留换行、列表 140 字素单行；文献标题单条 ≤ 200、列表 ≤ 64；预览 60 / 50；投递记录留 7 天；名单分页 500；投递分页 50；到点收件人分页 100。
- 幂等键 = 投递 UUID（`OutgoingMail.deliveryId`；端口用它写 `Resend-Idempotency-Key` 与 `Message-ID`）；`List-Unsubscribe: <{门户}/unsubscribe/one-click?token=…&category=…>`；`List-Unsubscribe-Post: List-Unsubscribe=One-Click`；底部确认页 `{门户}/unsubscribe#token=…&category=…`；类别 `REPLY` / `MENTION` / `DIGEST`。
- 文案逐字照 spec 8.4（含全角标点与空格写法，如 `@ 了你`、`10 月 30 日 15:02`）。
- 事务：外部调用（identity、interaction、SMTP）都不在事务里；入队用 `@Transactional(propagation = MANDATORY)`；其余写入用 `TransactionOperations` 包短事务。
- 表只存用户 ID（R6）；测试方法 snake_case；domain 测试不依赖 Spring。
- 提交：代码在全部完成、门禁全绿后，在任务 15 一次提交到 `claude/v016-pap146-43d680`（spec、plan 的提交在它前面，实施记录随代码一起提交），只提交本 Issue 的路径（`git diff --cached` 核对）；message 中文动词开头，结尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`；不 push、不开 PR。改了 `*.gradle.kts` 要重跑 `./gradlew dumpModuleGraph`，并提交 `patra-infra/cd/module-graph.json`。
- **开工前提**：编排会话给出含 PAP-143、PAP-105 的新 SHA。spec 与本计划已在基线 `f46edcdc0` 之上单独提交，用 `git rebase --onto <新 SHA> f46edcdc0` 挪过去，不要 `reset --hard`。任务 5 起会用到它们的类，每个任务开头先核对要用的类名与签名，和本计划不一致时以代码为准，并记进 spec 第 16 节「实施记录」。

## Review Focus

1. **同一条提醒入队两次**（PAP-143 的重复投递防线漏了，或同一事件重放）：只排一行、只进一封 → 任务 4 的 `ReminderQueueRepositoryAdapterIT.enqueue_is_idempotent_per_notification`。
2. **8:00 跑过之后 8:10 又跑一次**：一个窗口只有一封汇总 → 任务 4 的 `ReminderDeliveryRepositoryAdapterIT.digest_insert_is_unique_per_window`，以及任务 13 的 `DailyDigestFlowIT.rerun_in_eight_oclock_hour_does_not_send_twice`。
3. **SMTP 一直发不出去、期间内容变了（新回复、部分已读）**：重试用同一个投递 ID；上一次其实已被接受时端口报 `ALREADY_ACCEPTED`，记已发、不发第二封；全读了就不发；第一次调用满 24 小时过期 → 任务 9 的 `SendPendingDeliveriesHandlerTest.retry_reuses_delivery_id`、`…already_accepted_counts_as_sent`、`…expires_24_hours_after_first_send`，以及任务 13 的 `ReminderMailFlowIT.transient_failure_retries_with_same_delivery_id`。
4. **摘录里有字面的 `<b>`、`<script>`、`&amp;`，或者标题带 `<i>`**：HTML 里转义输出，不执行、不变形 → 任务 11 的 `ReminderMailTemplateTest.escapes_literal_markup_in_excerpt_and_title`。
5. **跑到一半 identity 挂了**：已发的不重发；当前这条推迟，本轮停下；下一轮接着发 → 任务 9 的 `SendPendingDeliveriesHandlerTest.identity_down_defers_current_and_stops_run`。

---

### Task 1: mail 领域：投递记录与排队行

**Files:**
- Create（`patra-notification-domain/src/main/java/dev/patra/notification/domain/mail/` 下）：`model/enums/DeliveryKind.java`、`model/enums/DeliveryStatus.java`、`model/enums/SkipReason.java`、`model/enums/UnsubscribeLinkCategory.java`、`model/vo/QueuedReminder.java`、`model/aggregate/ReminderDelivery.java`
- Test（`patra-notification-domain/src/test/java/dev/patra/notification/domain/mail/` 下）：`model/vo/QueuedReminderTest.java`、`model/aggregate/ReminderDeliveryTest.java`、`model/enums/UnsubscribeLinkCategoryTest.java`

**Interfaces:**
- Produces:
  - `enum DeliveryKind { INSTANT, DIGEST }`、`enum DeliveryStatus { PENDING, SENT, SKIPPED, EXPIRED; boolean finished() }`、`enum SkipReason { MODE, NOTHING_UNREAD, RECIPIENT, REJECTED }`
  - `enum UnsubscribeLinkCategory { REPLY, MENTION, DIGEST; static UnsubscribeLinkCategory of(NotificationCategory c) }`（HELPFUL 抛 `IllegalArgumentException`）
  - `@Builder record QueuedReminder(long notificationId, long recipientId, NotificationCategory category, Instant queuedAt, Instant dueAt)`；`static final Duration WAIT = Duration.ofMinutes(10)`；`static QueuedReminder queue(long notificationId, long recipientId, NotificationCategory category, Instant now)`（`dueAt = now + WAIT`；`category.hasEmail()` 为假时抛 `IllegalArgumentException`）
  - `final class ReminderDelivery`（Lombok `@Getter`；`@Builder(builderMethodName = "restore")` 在全参构造器上）：字段 `UUID deliveryId, long recipientId, DeliveryKind kind, NotificationCategory category, List<Long> notificationIds, Instant windowEnd, DeliveryStatus status, SkipReason skipReason, int attempts, Instant nextAttemptAt, Instant createdAt, Instant firstSentAt, Instant finishedAt`
    - `static ReminderDelivery instant(UUID id, long recipientId, NotificationCategory category, List<Long> notificationIds, Instant now)`（PENDING、`nextAttemptAt = now`）
    - `static ReminderDelivery digest(UUID id, long recipientId, Instant windowEnd, Instant now)`
    - `void recordSendAttempt(Instant now)`：`firstSentAt` 为空时记下 `now`，已有就不变
    - `void markSent(Instant now)`、`void skip(SkipReason reason, Instant now)`、`void defer(Instant now)`（`attempts + 1`、`nextAttemptAt = now + backoff(attempts)`；`firstSentAt` 非空且 `now ≥ firstSentAt + 24h` 时改为 `EXPIRED`）
    - 结束（SENT / SKIPPED / EXPIRED）时 `finishedAt = now`；已结束的再调任何方法抛 `IllegalStateException`
    - `static final Duration SEND_WINDOW = Duration.ofHours(24)`
    - `static Duration backoff(int attempts)` → `Duration.ofMinutes(min(1L << (attempts − 1), 30))`，`attempts < 1` 抛 `IllegalArgumentException`

- [ ] **Step 1: 写测试**

```java
// QueuedReminderTest
@Test void queue_sets_due_ten_minutes_after_now()          // dueAt == now.plus(10m)
@Test void queue_rejects_helpful()                          // HELPFUL → IllegalArgumentException
// ReminderDeliveryTest
@Test void backoff_doubles_and_caps_at_thirty_minutes()     // 1..8 → 1,2,4,8,16,30,30,30 分钟
@Test void defer_counts_attempt_and_moves_next_attempt()    // attempts 0→1、nextAttemptAt = now+1m；再 defer → 2、now2+2m
@Test void instant_requires_category_and_ids()              // notificationIds 空 → IllegalArgumentException
@Test void finished_delivery_cannot_change()                // markSent 后 defer / skip / markSent 都 IllegalStateException
@Test void first_send_attempt_is_recorded_once()           // 两次 recordSendAttempt → firstSentAt 是第一次的时刻
@Test void never_sent_defers_forever()                      // firstSentAt 为空，defer 到 30 天后仍 PENDING
@Test void defer_expires_24_hours_after_first_send()        // firstSentAt+23h59m defer → PENDING；+24h → EXPIRED、finishedAt
// UnsubscribeLinkCategoryTest
@Test void maps_reply_and_mention_and_rejects_helpful()
```

- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-notification:patra-notification-domain:test --tests "*mail*"`，预期编译失败（类不存在）。
- [ ] **Step 3: 实现上面的类型**
- [ ] **Step 4: 跑测试确认通过**（同上命令，预期 BUILD SUCCESSFUL）

### Task 2: mail 领域：合并窗口与汇总名额

**Files:**
- Create（`domain/mail/` 下）：`model/vo/ReminderBatch.java`、`model/vo/FlushPlan.java`、`model/vo/DigestWindow.java`、`model/vo/DigestSlots.java`、`policy/ReminderWindowPolicy.java`、`policy/DigestSlotPolicy.java`
- Test：`policy/ReminderWindowPolicyTest.java`、`policy/DigestSlotPolicyTest.java`、`model/vo/DigestWindowTest.java`

**Interfaces:**
- Consumes: Task 1 的 `QueuedReminder`
- Produces:
  - `record ReminderBatch(NotificationCategory category, List<Long> notificationIds)`（`of`；ID 按 `queuedAt`、`notificationId` 升序）
  - `record FlushPlan(List<Long> dropped, List<ReminderBatch> batches)`（`of`）；`List<Long> consumed()` = dropped ∪ 各 batch 的 ID
  - `final class ReminderWindowPolicy { static FlushPlan plan(List<QueuedReminder> rows, Set<Long> unreadIds, Instant lastSeen /* 可空 */, Instant now) }`：规则照 spec 5.1 第 2～4 步
  - `record DigestWindow(Instant from, Instant to)`：`static DigestWindow endingAt(Instant end)`（`from = end − 24h`）；`static Instant scheduledEnd(Instant now, ZoneId zone)`（`now` 所在日的 08:00；`now` 早于当日 08:00 时取前一日 08:00）；`static Instant manualEnd(Instant now)`（截到分钟）
  - `record DigestSlots(int replies, int mentions)`（`of`）
  - `final class DigestSlotPolicy { static final int MAX_ITEMS = 10; static DigestSlots allocate(int replyTotal, int replyListable, int mentionTotal, int mentionListable) }`：两节都有时回复 `min(ceil(10 × r ÷ (r + m)), 9)`、@ 拿剩下，再按各自能列的条数截短，余额补给另一节；只有一节时 `min(10, 能列的)`

- [ ] **Step 1: 写测试**

```java
// ReminderWindowPolicyTest（now = T0+10m；行的 queuedAt 用 T0、T0+3m、T0+7m）
@Test void read_or_deleted_rows_are_dropped()               // unreadIds 不含 → dropped
@Test void rows_seen_after_queueing_are_dropped()           // lastSeen = T0+5m → T0、T0+3m 丢；T0+7m 留（不在 batches，未到点）
@Test void due_category_takes_all_its_waiting_rows()        // 三条 REPLY，最早到点 → 一个 batch 含三条
@Test void reply_and_mention_due_together_are_two_batches()
@Test void category_not_yet_due_stays_queued()              // MENTION 最早一条 T0+5m 入队 → 不在 dropped、不在 batches
@Test void lastSeen_null_means_never_seen()
// DigestSlotPolicyTest
@Test void both_sections_split_by_share()                   // (9,9,5,5) → (7,3)
@Test void small_section_keeps_at_least_one()               // (13,10,1,1) → (9,1)
@Test void reply_share_rounds_up()                          // (1,1,13,10) → (1,9)
@Test void short_section_gives_slots_to_other()             // (12,10,6,1)：@ 只能列 1 → (9,1)
@Test void single_section_takes_up_to_ten()                 // (0,0,14,10) → (0,10)；(3,3,0,0) → (3,0)
// DigestWindowTest
@Test void scheduled_end_is_eight_oclock_beijing()          // 2026-10-31T00:00:00Z（北京 08:00）→ 同值；00:10Z → 00:00Z；2026-10-30T23:59Z（北京 07:59）→ 2026-10-30T00:00Z
@Test void manual_end_truncates_to_minute()
```

- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-notification:patra-notification-domain:test --tests "*mail*"`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**

### Task 3: mail 领域：链接、标题、结果段、时间写法

**Files:**
- Create（`domain/mail/policy/` 下）：`ReminderLinks.java`、`PublicationTitleText.java`、`ExperienceOutcomeText.java`、`ReminderTimeText.java`
- Test：同名 `*Test.java`

**Interfaces:**
- Produces:
  - `record ReminderLinks(String baseUrl)`：`static ReminderLinks of(String baseUrl)`（去掉末尾 `/`）；`String comment(boolean publication, String key, long commentId)`（`/papers/{key}?comment=` 或 `/posts/{key}?comment=`）；`String inbox(NotificationCategory c)`（REPLY → `/notifications`，MENTION → `/notifications/mentions`）；`String inboxHome()`；`String settings()`；`String unsubscribeConfirm(String token, UnsubscribeLinkCategory c)`；`String unsubscribeOneClick(String token, UnsubscribeLinkCategory c)`
  - `final class PublicationTitleText { static String plain(String raw); static String truncate(String text, int maxGraphemes) }`：规则照 spec 8.3（白名单标签去掉、`annotation` 连内容去掉、六个命名实体加数字实体解码；截断按 `BreakIterator.getCharacterInstance()`，截了加 `…`；`raw` 为空或空白 → `一篇文献的讨论`）
  - `final class ExperienceOutcomeText { static Optional<String> of(String firstDecision, String finalOutcome) }`：中文名与「首轮即终局」照 spec 8.3；任一值不认识 → 空
  - `record ReminderTimeText(ZoneId zone)`：`String full(Instant t)` → `10 月 30 日 15:02`；`String clock(Instant t)` → `15:02`；`String date(Instant t)` → `10 月 31 日`；`boolean sameDay(Instant a, Instant b)`

- [ ] **Step 1: 写测试**

```java
// ReminderLinksTest
@Test void trailing_slash_is_removed()                      // of("https://patra.linqibin.dev/").settings() == "https://patra.linqibin.dev/settings/notifications"
@Test void unsubscribe_links_match_contract()               // 确认页 "…/unsubscribe#token=T&category=MENTION"；一键 "…/unsubscribe/one-click?token=T&category=DIGEST"
@Test void comment_permalinks()                             // ("/papers/18?comment=7", "/posts/32?comment=9")
@Test void inbox_tabs()
// PublicationTitleTextTest
@Test void strips_formatting_tags_and_decodes_entities()    // "Effect of <i>E. coli</i> &amp; CO<sub>2</sub>" → "Effect of E. coli & CO2"
@Test void keeps_unknown_tags_and_bare_lt_literally()       // "p < 0.05 <custom>" 原样
@Test void drops_math_annotation()                          // "<math><mi>x</mi><annotation>x</annotation></math>" → "x"
@Test void truncates_by_grapheme_and_adds_ellipsis()        // 65 字素 → 64 + "…"；含 emoji 不切半
@Test void blank_title_falls_back()
// ExperienceOutcomeTextTest
@Test void revision_then_outcome()                          // (MAJOR_REVISION, ACCEPTED) → "大修 → 录用"
@Test void terminal_first_decision_only()                   // (DESK_REJECT, REJECTED) → "直接拒稿"
@Test void unknown_values_are_empty()
// ReminderTimeTextTest（zone Asia/Shanghai）
@Test void formats_beijing_time()                           // 2026-10-30T07:02:00Z → full "10 月 30 日 15:02"、clock "15:02"、date "10 月 30 日"
```

- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**

### Task 4: 存储：迁移与两个仓储

**Files:**
- Create：`patra-notification-infra/src/main/resources/db/migration/mail/V{写脚本时的时间戳}__create_reminder_tables.sql`（`CREATE SCHEMA IF NOT EXISTS mail` + spec 6.1、6.2 两张表，列、CHECK、索引、英文 `COMMENT` 照 preference 脚本的写法）
- Create：`domain/mail/port/repository/ReminderQueueRepository.java`、`ReminderDeliveryRepository.java`；`infra/mail/persistence/ReminderQueueRepositoryAdapter.java`、`ReminderDeliveryRepositoryAdapter.java`
- Test：`patra-notification-infra/src/integrationTest/java/dev/patra/notification/infra/mail/persistence/ReminderQueueRepositoryAdapterIT.java`、`ReminderDeliveryRepositoryAdapterIT.java`
- Modify：`NotificationMigrationIT`（若它列举表或 schema，加 `mail` 的两张表）

**Interfaces:**
- Consumes: Task 1
- Produces:
  - `ReminderQueueRepository`：`void enqueue(List<QueuedReminder> rows)`（`ON CONFLICT (notification_id) DO NOTHING`，`@Transactional(propagation = MANDATORY)`）；`List<Long> recipientsDue(Instant now, long afterRecipientId, int limit)`（升序、去重）；`List<QueuedReminder> findByRecipient(long recipientId)`；`int delete(Collection<Long> notificationIds)`
  - `ReminderDeliveryRepository`：`void insert(ReminderDelivery d)`；`boolean insertDigestIfAbsent(ReminderDelivery d)`（`ON CONFLICT DO NOTHING`，返回是否插入）；`List<ReminderDelivery> findDue(Instant now, int limit)`（PENDING、`next_attempt_at ≤ now`、按 `next_attempt_at`、`created_at` 升序）；`void update(ReminderDelivery d)`（写 `status`、`skip_reason`、`attempts`、`next_attempt_at`、`first_sent_at`、`finished_at`）；`int deleteFinishedBefore(Instant cutoff)`
  - 实现用 JdbcClient 原生 SQL；`notification_ids` 以 `Long[]` 读写；时间 `atOffset(UTC)`
  - 坑：部分唯一索引只能 `CREATE UNIQUE INDEX … WHERE kind = 'DIGEST'`，不能写成 `CONSTRAINT`；`ON CONFLICT` 要重复谓词：`ON CONFLICT (recipient_id, window_end) WHERE kind = 'DIGEST' DO NOTHING`

- [ ] **Step 1: 写 IT**

```java
// ReminderQueueRepositoryAdapterIT
@Test void enqueue_is_idempotent_per_notification()         // 同一 notification_id 两次 → 1 行
@Test void recipients_due_pages_in_ascending_order()        // 三人，一人未到点 → 只列两人；afterRecipientId 生效
@Test void find_by_recipient_returns_all_categories_including_not_due()
@Test void enqueue_outside_transaction_fails()              // IllegalTransactionStateException
// ReminderDeliveryRepositoryAdapterIT
@Test void instant_round_trips()                            // insert → findDue 读回各字段与 notificationIds 顺序
@Test void digest_insert_is_unique_per_window()             // 同人同 windowEnd 第二次 → false；不同 windowEnd → true
@Test void find_due_skips_future_and_finished()
@Test void update_persists_attempts_and_finish()
@Test void delete_finished_before_keeps_pending_and_recent()
@Test void check_constraints_reject_mixed_kinds()           // INSTANT 带 window_end → 违反约束（断言约束名）
@Test void first_sent_at_round_trips()
```

- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-notification:patra-notification-infra:integrationTest --tests "*Reminder*"`
- [ ] **Step 3: 写迁移、端口与适配器**
- [ ] **Step 4: 跑测试确认通过**（同上，另跑 `--tests "*NotificationMigrationIT*"`）

### Task 5: 往 inbox、preference、kernel 加方法，声明子域边

**Files:**
- Modify：`domain/inbox/port/read/InboxReadPort.java`、`infra/inbox/persistence/InboxReadAdapter.java`（类名以 PAP-143 为准）；Create `domain/inbox/model/read/UnreadWindow.java`
- Modify：`domain/inbox/model/read/InboxEntry.java`（加 `Instant createdAt`，读适配器一并取 `created_at`；PAP-143 已有的调用方补参数）
- Modify：`domain/preference/port/read/EmailSubscriptionReadPort.java`、`infra/preference/persistence/EmailSubscriptionReadAdapter.java`；Create `domain/preference/model/read/EmailModes.java`
- Modify：`domain/kernel/policy/ExcerptRenderer.java`（PAP-143 写成 `final class` + 静态方法、公开 `MENTION` 正则）；Create `domain/kernel/model/read/SegmentedExcerpt.java`（和 `RenderedExcerpt` 同包）
- Modify（PAP-143 若没挪）：`NotificationType` 从 `domain.inbox.model.enums` 挪到 `domain.inbox.model.read`（`InboxEntry.type()` 返回它，mail 要引用；已请编排会话转告 PAP-143）
- Modify：`patra-notification-boot/src/test/java/dev/patra/notification/architecture/NotificationArchitectureTest.java`（两条 `allowingDependency`，更新注释）
- Test：`InboxReadAdapterIT`（补用例）、`EmailSubscriptionReadAdapterIT`（补用例）、`ExcerptRendererTest`（补用例）

**Interfaces:**
- Produces:
  - `List<InboxEntry> findUnread(long recipientId, Collection<Long> notificationIds)`：仍存在、`read_at IS NULL`，按 `occurred_at`、`notification_id` 升序；空集合直接返回空
  - `record UnreadWindow(int total, List<InboxEntry> newest)`（`of`）；`UnreadWindow unreadBetween(long recipientId, NotificationCategory category, Instant from, Instant to, int limit)`：`created_at ∈ [from, to)`（写入信箱的时刻，不是 `occurred_at`），`newest` 按 `occurred_at` 降序
  - `List<EmailSubscription> digestSubscribers(long afterUserId, int limit)`：`reply_mode = 'DIGEST' OR mention_mode = 'DIGEST'`，`user_id > afterUserId` 升序，不插行
  - `record EmailModes(EmailMode reply, EmailMode mention)`（`of`；`EmailMode modeOf(NotificationCategory)`，HELPFUL → OFF；`boolean anyDigest()`）；`EmailModes findSubscription(long userId)`：只读、不插行，没有行返回 `EmailModes.of(INSTANT, INSTANT)`
  - `record SegmentedExcerpt(List<Segment> segments, boolean truncated)`，`record Segment(String text, boolean mention)`；`static SegmentedExcerpt renderSegments(String plainText, Map<Long, String> names, int maxGraphemes, boolean singleLine)`；原 `static RenderedExcerpt render(...)` 改为拼接 `renderSegments` 的结果，返回值不变

- [ ] **Step 1: 写测试**

```java
// InboxReadAdapterIT
@Test void find_unread_excludes_read_and_missing()
@Test void find_unread_is_scoped_to_recipient()            // 别人的 ID 不返回
@Test void unread_between_counts_and_lists_newest_first()  // 窗口外、已读、别的类别都不算；limit 生效、total 不受 limit 影响
@Test void unread_between_filters_on_created_at_not_occurred_at() // occurred_at 在窗口前、created_at 在窗口内 → 算在内
// EmailSubscriptionReadAdapterIT
@Test void digest_subscribers_lists_only_digest_and_pages()
@Test void digest_subscribers_does_not_insert_rows()
@Test void find_subscription_defaults_without_inserting()   // 没有行 → INSTANT/INSTANT，表里仍没有行
// InboxReadAdapterIT 另补
@Test void entries_carry_created_at()
// ExcerptRendererTest
@Test void segments_mark_mentions_and_join_to_render()     // renderSegments 拼接 == render 的 text；truncated 一致
@Test void segments_never_split_a_mention()
```

- [ ] **Step 2: 跑测试确认失败**（infra `integrationTest --tests "*InboxReadAdapterIT*" --tests "*EmailSubscriptionReadAdapterIT*"`、domain `test --tests "*ExcerptRendererTest*"`）
- [ ] **Step 3: 实现**；改 `NotificationArchitectureTest`
- [ ] **Step 4: 跑测试确认通过**，另跑 `./gradlew :patra-notification:patra-notification-boot:test --tests "*NotificationArchitectureTest*"`

### Task 6: 拼装规则：`ReminderMailComposer`

**Files:**
- Create（`domain/mail/` 下）：`model/vo/content/ExcerptPart.java`、`ReminderPlace.java`、`ReminderItem.java`、`DigestSection.java`、`ReminderMailContent.java`、`ReminderFacts.java`、`DigestSectionFacts.java`；`policy/ReminderMailComposer.java`
- Test：`policy/ReminderMailComposerTest.java`

**Interfaces:**
- Consumes: Task 2（`DigestWindow`、`DigestSlotPolicy`）、Task 3（全部）；kernel 的 `ExcerptRenderer`（Task 5 加的 `renderSegments`）、`SubjectView`；inbox 的 `NotificationType`（类名以 PAP-143 代码为准）
- Produces:
  - `record ExcerptPart(String text, boolean mention)`（`of`）
  - `record ReminderPlace(String label, String text, String line)`（`of`；label / text 给单条，line 给列表）
  - `@Builder record ReminderItem(String actorName, String action, String time, List<ExcerptPart> excerpt, ReminderPlace place, String link)`
  - `record DigestSection(String title, List<ReminderItem> items)`（`of`）
  - `enum` 嵌在内容里：`ReminderMailContent.Layout { SINGLE, BATCH, DIGEST }`
  - `@Builder record ReminderMailContent(Layout layout, String subject, String preheader, String eyebrow, String heading, String lead, ReminderItem single, List<ReminderItem> items, List<DigestSection> sections, String overflow, String buttonLabel, String buttonUrl, String why, String unsubscribeLabel, String unsubscribeUrl, String settingsUrl, String oneClickUrl)`
  - `@Builder record ReminderFacts(long notificationId, NotificationType type, String actorName, Instant occurredAt, String plainText, SubjectView subject, long commentId, boolean mine)`
  - `record DigestSectionFacts(int total, List<ReminderFacts> listable)`（`of`）
  - `final class ReminderMailComposer`：构造器 `(ReminderLinks links, ReminderTimeText time)`；
    - `ReminderMailContent instant(NotificationCategory category, List<ReminderFacts> entries, Map<Long, String> mentionNames, String token, Instant now)`：1 条 → SINGLE，多条 → BATCH（列前 10 条，按 `occurredAt` 升序）；`entries` 为空抛 `IllegalArgumentException`
    - `ReminderMailContent digest(DigestWindow window, Set<NotificationCategory> digestCategories, DigestSectionFacts replies, DigestSectionFacts mentions, Map<Long, String> mentionNames, String token)`：两节合计 0 抛 `IllegalArgumentException`
  - 文案、截断长度、名字去重与「等 N 人」、跨天时间、预览、卡片外一句全部照 spec 8.2～8.4

- [ ] **Step 1: 写测试**（每个断言用 spec 8.4 的原文；示例数据照简报：王立、周敏、林知远，Marik 2017 标题，JAHA）

```java
@Test void single_reply_copy()                    // subject "王立 回复了你的评论"、heading "王立 回复了你"、eyebrow "回复 · REPLY"、place ("文献讨论", 标题, …)、button ("查看并回复", 永久链接)、why 回复逐条原文、unsubscribeLabel "退订这类邮件"、oneClickUrl category=REPLY
@Test void single_post_comment_on_my_experience() // place ("你的投稿经验", "J Am Heart Assoc · 2026 年 3 月投稿 · 大修 → 录用")
@Test void single_mention_in_others_experience()  // subject "林知远 在评论里 @ 了你"；place label "投稿经验讨论"
@Test void single_excerpt_keeps_newlines_and_marks_mentions() // 300 字素、"\n" 保留、@名字 段 mention=true
@Test void preheader_single_is_first_sixty_graphemes()
@Test void batch_reply_copy()                     // subject "3 条新回复：王立、周敏、林知远"、heading "3 条新回复"、lead 原文、items 时间 "15:02"、button ("打开提醒页", "/notifications")
@Test void batch_names_over_three()               // 5 人 → "王立、周敏等 5 人"；同一人两条只算一次
@Test void batch_mention_copy()                   // "2 条评论 @ 了你：林知远、周敏"、button 链 "/notifications/mentions"
@Test void batch_over_ten_lists_ten_and_overflow()// 12 条 → items 10 条，overflow "还有 2 条没有列出，到提醒页查看。"
@Test void batch_across_days_uses_full_time()
@Test void batch_list_excerpt_is_single_line_140()
@Test void anonymous_and_deleted_actor_names_pass_through() // actorName 由调用方给，组合进主题
@Test void digest_both_sections_copy()            // subject "每日汇总：5 条未读的回复和 @（10 月 31 日）"、heading "过去 24 小时有 5 条未读"、eyebrow "每日汇总 · 10 月 31 日"、lead 原文、节标题 "回复我的 · 3 条" / "@ 我的 · 2 条"、unsubscribeLabel "退订每日汇总"、category=DIGEST
@Test void digest_mentions_only_copy()            // "每日汇总：2 条未读的 @（10 月 31 日）"、"过去 24 小时有 2 条 @ 没看"、why 只写「@ 我的」
@Test void digest_replies_only_copy()
@Test void digest_overflow_counts_per_section()   // 9/5 → "还有 4 条没有列出（回复 2 条、@ 2 条），到提醒页查看。"
@Test void digest_section_newest_first()
```

- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**

### Task 7: 入队监听

**Files:**
- Create：`app/mail/listener/ReminderQueueListener.java`
- Test：`patra-notification-app/src/test/java/dev/patra/notification/app/mail/listener/ReminderQueueListenerTest.java`；`patra-notification-boot/src/integrationTest/java/dev/patra/notification/mail/ReminderQueueingIT.java`

**Interfaces:**
- Consumes: PAP-143 的 `NotificationsCreatedEvent(List<CreatedNotification>)`；Task 4 的 `ReminderQueueRepository.enqueue`
- Produces: `@EventListener @Transactional(propagation = MANDATORY) void on(NotificationsCreatedEvent event)`：只把 `category.hasEmail()` 的条目按 `QueuedReminder.queue(notificationId, recipientId, category, createdAt)` 入队（`createdAt` 取事件条目里的写入时刻，PAP-143 契约 02:14 版）；一条都没有时不调仓储

- [ ] **Step 1: 写测试**

```java
// ReminderQueueListenerTest
@Test void queues_reply_and_mention_at_created_at()           // queuedAt == 条目的 createdAt，dueAt == createdAt + 10m
@Test void ignores_helpful()
@Test void nothing_to_queue_skips_repository()
// ReminderQueueingIT（经信箱真实写入路径：调 PAP-143 写提醒的 app 入口，取其 IT 的写法）
@Test void reply_notification_is_queued_in_same_transaction()   // 写入后 mail.reminder_queue 有一行、due_at = clock+10m
@Test void inbox_rollback_rolls_back_queue()                    // 让入队抛异常（如重复构造的非法行）→ inbox 与 queue 都没有行
```

- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**

### Task 8: 到点合并：`FlushDueRemindersHandler`

**Files:**
- Create：`app/mail/usecase/flush/FlushDueRemindersCommand.java`、`FlushDueRemindersHandler.java`、`FlushResult.java`
- Test：`app/mail/usecase/flush/FlushDueRemindersHandlerTest.java`

**Interfaces:**
- Consumes: Task 2 `ReminderWindowPolicy.plan`；Task 4 两个仓储；`InboxReadPort.findUnread`；`PresenceGateway.lastSeen`
- Produces:
  - `record FlushDueRemindersCommand()`（`static of()`）；`record FlushResult(int recipients, int dropped, int deliveries)`（`of`）
  - `CommandHandler<FlushDueRemindersCommand, FlushResult>`：按 100 人一页取 `recipientsDue(now, cursor, 100)`，每人一个 `TransactionOperations` 事务：读行 → `findUnread` → `lastSeen` → `plan` → `insert` 每个 batch 为 `ReminderDelivery.instant(UUID.randomUUID(), …)` → `delete(plan.consumed())`；单人抛意外异常时记 ERROR、回滚这一人、继续下一人

- [ ] **Step 1: 写测试**（Mockito；`TransactionOperations` 用直接执行回调的桩）

```java
@Test void due_batch_becomes_one_delivery_and_rows_are_deleted()
@Test void dropped_rows_are_deleted_without_delivery()
@Test void not_due_category_is_left_alone()
@Test void pages_through_recipients()                       // 第一页满 100 → 以最后一个 ID 为游标再取
@Test void one_recipient_failing_does_not_stop_others()
```

- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-notification:patra-notification-app:test --tests "*FlushDueReminders*"`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**

### Task 9: 发送：装配与 `SendPendingDeliveriesHandler`

**Files:**
- Create：`app/mail/service/ReminderDeliveryAssembler.java`、`app/mail/service/AssembledReminder.java`、`app/mail/service/ReminderMailModel.java`、`app/mail/service/AsideLink.java`；`app/mail/usecase/send/SendPendingDeliveriesCommand.java`、`SendPendingDeliveriesHandler.java`、`SendResult.java`
- Test：`app/mail/service/ReminderDeliveryAssemblerTest.java`、`app/mail/usecase/send/SendPendingDeliveriesHandlerTest.java`

**Interfaces:**
- Consumes: Task 1、4、5、6；`InboxReadPort.findUnread` / `unreadBetween`、`PresenceGateway.lastSeen`、`EmailSubscriptionReadPort.findSubscription` / `subscriptionOf`、`RecipientPort.find`、`SubjectViewPort.findViews`、`UserCardPort.findCards`、`MentionNames.of`；PAP-105 的 `MailDeliveryPort#deliver(OutgoingMail)`（`domain.mail.port.delivery`）、`OutgoingMail`（`domain.mail.model.vo`，`@Builder`：`deliveryId` UUID、`to`、`template`、`subject`、`model` 值非空、`headers`）、`MailDeliveryFailedException#failure()` → `MailDeliveryFailure { ALREADY_ACCEPTED, REJECTED, TRANSIENT }`
- Produces:
  - `sealed interface AssembledReminder permits AssembledReminder.Ready, AssembledReminder.Skip`：`record Ready(String to, ReminderMailContent content)`、`record Skip(SkipReason reason)`
  - `ReminderDeliveryAssembler.assemble(ReminderDelivery d, Instant now)` → `AssembledReminder`；只处理 PENDING；顺序照 spec 7.3：`findUnread` → `lastSeen`（逐条去掉 `createdAt ≤ lastSeen`；汇总不看）→ `findSubscription` → `RecipientPort.find` → `subscriptionOf`（取令牌）→ `findViews` → `findCards`；跳过原因照 5.3；抛 `IdentityUnavailableException` / `InteractionUnavailableException`
  - `record SendPendingDeliveriesCommand()`（`of`）、`record SendResult(int sent, int skipped, int deferred, boolean stoppedEarly)`（`of`）
  - Handler：每次 `findDue(now, 50)` 直到取空；每条 `assemble` → Skip：`skip` + `update`；Ready：先 `recordSendAttempt(now)` + `update`（短事务），再在事务外 `deliver(OutgoingMail.builder().deliveryId(d.getDeliveryId()).to(to).template(模板名).subject(content.subject()).model(ReminderMailModel.of(content)).headers(Map.of("List-Unsubscribe", "<" + content.oneClickUrl() + ">", "List-Unsubscribe-Post", "List-Unsubscribe=One-Click")).build())`：正常返回或 `ALREADY_ACCEPTED` → `markSent`；`REJECTED` → `skip(REJECTED)`、继续；`TRANSIENT`、identity / interaction 不可用 → `defer`（可能变 EXPIRED）、停止本轮；其他 `RuntimeException` → `defer`、记 ERROR、继续。模板名：SINGLE `notification-single`、BATCH `notification-batch`、DIGEST `notification-digest`
  - `final class ReminderMailModel { static Map<String, Object> of(ReminderMailContent c) }`（`app/mail/service/`）：`mail` → `c`、`preheader` → `c.preheader()`、`footnote` → `此邮件由 Patra 系统自动发出，直接回复不会出现在讨论里。`、`asideLinks` → `[{label: c.unsubscribeLabel(), url: c.unsubscribeUrl()}, {label: "通知设置", url: c.settingsUrl()}]`（`record AsideLink(String label, String url)`）；值为 null 的键不放；不用 `subject`、`siteName`、`notice` 三个键（PAP-105 渲染器保留）

- [ ] **Step 1: 写测试**

```java
// ReminderDeliveryAssemblerTest
@Test void instant_all_read_skips_without_calling_identity()      // NOTHING_UNREAD；verifyNoInteractions(recipients)
@Test void instant_seen_after_created_drops_those_entries()       // lastSeen ≥ 某条 createdAt → 去掉；全去掉 → NOTHING_UNREAD
@Test void subscription_token_is_fetched_only_after_recipient_passes() // RECIPIENT 跳过时 verify(subscriptions, never()).subscriptionOf(anyLong())
@Test void mode_not_instant_skips()                                // MODE
@Test void digest_without_digest_categories_skips()                // MODE
@Test void recipient_missing_banned_pending_deletion_unverified_skip() // 参数化 → RECIPIENT
@Test void items_without_subject_or_key_are_dropped_and_all_dropped_skips()
@Test void actor_names_anonymous_deleted_and_display_name()       // "匿名作者" / "已注销用户" / displayName
@Test void mention_names_use_mention_names_rule()
@Test void mine_is_owner_equals_recipient()
@Test void digest_uses_window_and_slots()
// SendPendingDeliveriesHandlerTest
@Test void ready_is_delivered_with_template_model_headers_and_delivery_id()
// ReminderMailModelTest
@Test void model_has_mail_and_layout_keys_without_reserved_or_null_values() // 不含 subject / siteName / notice；preheader 为空时不放
@Test void send_attempt_is_recorded_before_delivering()    // InOrder：update(firstSentAt) → deliver
@Test void skip_is_recorded_with_reason()
@Test void identity_down_defers_current_and_stops_run()
@Test void interaction_down_defers_and_stops()
@Test void transient_failure_defers_and_stops()
@Test void already_accepted_counts_as_sent()
@Test void rejected_skips_and_continues()
@Test void unexpected_error_defers_and_continues()
@Test void retry_reuses_delivery_id()
@Test void expires_24_hours_after_first_send()
@Test void retry_after_everything_read_skips()
@Test void retry_after_mode_turned_off_skips()
```

- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**

### Task 10: 每日汇总名单：`ScheduleDailyDigestHandler`

**Files:**
- Create：`app/mail/usecase/digest/ScheduleDailyDigestCommand.java`、`ScheduleDailyDigestHandler.java`、`DigestScheduleResult.java`
- Test：`app/mail/usecase/digest/ScheduleDailyDigestHandlerTest.java`

**Interfaces:**
- Consumes: Task 2 `DigestWindow`；Task 4 `insertDigestIfAbsent`、`deleteFinishedBefore`；Task 5 `digestSubscribers`
- Produces: `record ScheduleDailyDigestCommand(Set<Long> manualUsers)`（`static scheduled()` → 空集合，`static manual(Set<Long> users)` → 非空）；`record DigestScheduleResult(Instant windowEnd, int subscribers, int created, int purged)`（`of`）；Handler：定时：`windowEnd = scheduledEnd(now, zone)`，`digestSubscribers` 500 人一页；手动：`windowEnd = manualEnd(now)`，只对列出的、`findSubscription(id).anyDigest()` 的人建；两种都 `deleteFinishedBefore(now − 7 天)`

- [ ] **Step 1: 写测试**

```java
@Test void scheduled_run_uses_today_eight_oclock()
@Test void rerun_creates_nothing_new()                       // insertDigestIfAbsent 返回 false → created 0
@Test void manual_run_ends_now_and_only_targets_listed_digest_users()  // 列出的人里档位不含 DIGEST 的不建；不调 digestSubscribers
@Test void pages_subscribers_and_purges_old_records()
```

- [ ] **Step 2–4: 失败 → 实现 → 通过**

### Task 11: 渲染、模板与配置

**Files:**
- Create：模板（由 PAP-105 的端口按模板名渲染，模型只有 `mail` → `ReminderMailContent`）`patra-notification-infra/src/main/resources/mail/html/notification-single.html`、`notification-batch.html`、`notification-digest.html`、`mail/text/notification-single.txt`、`notification-batch.txt`、`notification-digest.txt`
- Modify：`mail/html/fragments/layout.html`（spec 8.5 的三个可选变量；`text/fragments/footer.txt` 不动，纯文本模板各自写页脚）
- Create（照 identity 的 `IdentityProperties` + `IdentityConfiguration`，放 boot 的 `dev.patra.notification.config`）：`NotificationMailProperties.java`（record，`@ConfigurationProperties("patra.notification.mail")` + `@Validated`：`links.baseUrl` 必须是绝对 http(s) 地址、`zone` 默认 `Asia/Shanghai`；若 PAP-105 已有这个前缀的属性类，就把字段加进它）、`ReminderMailConfiguration.java`（`@Bean` 装配 `ReminderLinks`、`ReminderTimeText`、`ReminderMailComposer`；`ScheduleDailyDigestHandler` 经 `ReminderTimeText.zone()` 取时区）
- Modify：`patra-infra/nacos/dev/patra-notification.yaml`（加 `patra.notification.mail.links.base-url: ${PATRA_PORTAL_BASE_URL:http://linqibins-mac-mini:4000}`）；boot `src/integrationTest/resources/application-test.yml`（测试用门户地址 `http://portal.test`）
- Test：`patra-notification-infra/src/test/java/dev/patra/notification/infra/mail/template/ReminderMailTemplateTest.java`（用 PAP-105 渲染器的同一套模板引擎配置渲染三个模板，写法照它的模板测试）；安全邮件模板已有的渲染测试必须照旧通过

**Interfaces:**
- Consumes: Task 6 `ReminderMailContent`；PAP-105 的模板渲染器（类名以它的代码为准）

- [ ] **Step 1: 写测试**

```java
@Test void single_html_has_quote_block_button_and_aside_links()   // 含 "讨论提醒"、"查看并回复 →" 链到永久链接、两个底部链接、"直接回复不会出现在讨论里"、preheader 隐藏 div
@Test void batch_html_lists_items_with_their_links()
@Test void digest_html_has_two_sections_and_overflow()
@Test void mention_segments_use_link_color_span()
@Test void escapes_literal_markup_in_excerpt_and_title()          // "<script>" 出现为 "&lt;script&gt;"
@Test void text_parts_follow_brief_layout()                        // 单条纯文本逐行对照 spec / 简报 §3.11
@Test void security_layout_without_new_variables_is_unchanged()    // 不设 preheader / footnote / asideLinks 时输出与改动前一致（对一封安全邮件模板）
```

- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-notification:patra-notification-infra:test --tests "*ReminderMailTemplateTest*"`
- [ ] **Step 3: 写模板、改 layout、写配置**；三种邮件各生成一份 HTML 到 scratchpad，用 portal 的 Playwright 离线截图（600px、375px），对照 `Mail.dc.html` / `Digest.dc.html` 自查
- [ ] **Step 4: 跑测试确认通过**（含安全邮件已有的渲染测试）

### Task 12: XXL 任务入口

**Files:**
- Create：`adapter/mail/scheduler/ReminderMailScheduleJob.java`（`@XxlJob("reminderMailJob")`：先 `FlushDueRemindersCommand`、再 `SendPendingDeliveriesCommand`；`stoppedEarly` 为真时 `handleFail`，其余 `handleSuccess` 带计数）、`adapter/mail/scheduler/DailyDigestScheduleJob.java`（`@XxlJob("dailyDigestJob")`：参数为空 → `scheduled()`；`users=123,456`（去空白、逗号分隔、正整数、1～100 个）→ `manual(…)`；其余 `handleFail("参数应为空或 users=用户ID,…")`）
- Test：`patra-notification-adapter/src/test/java/dev/patra/notification/adapter/mail/scheduler/ReminderMailScheduleJobTest.java`、`DailyDigestScheduleJobTest.java`（`XxlJobHelper` 的静态方法用 `mockStatic`，同 `DeletedUserCleanupScheduleJob` 的测试写法）

- [ ] **Step 1: 写测试**

```java
@Test void reminder_job_flushes_then_sends()                // InOrder
@Test void reminder_job_marks_fail_when_stopped_early()
@Test void digest_job_parses_users_param()
@Test void digest_job_rejects_bad_param()                 // "now"、"users="、"users=a,1"、"users=0" → handleFail，不调 commandBus
```

- [ ] **Step 2–4: 失败 → 实现 → 通过**

### Task 13: 流程集成测试（AC）

**Files:**
- Create：`patra-notification-boot/src/integrationTest/java/dev/patra/notification/mail/ReminderMailFlowIT.java`、`DailyDigestFlowIT.java`、`ReminderMailITSupport.java`（继承 `NotificationFlowITSupport`；加 `GreenMailSmtpInitializer`、`MutableClock` 配置，interaction 桩照 PAP-143 的 IT 写法；提供「写一条回复 / @ 提醒」「标已读」「记在站」「拨时钟」「跑一次 reminderMailJob 的两个 Handler」等辅助方法；GreenMail 是单例，PAP-105 的 `SecurityMailFlowIT` 也在用，`@BeforeEach` 里 `GreenMailSmtpInitializer.purge()`，并把时钟拨回起点）
- Modify：如需，`NotificationFlowITSupport`（只加不改）

- [ ] **Step 1: 写 IT**（spec 第 11 节表格逐条）

```java
// ReminderMailFlowIT
@Test void instant_reply_is_sent_after_ten_minutes()       // 9m59s 无信；10m 收到一封：主题、摘录、?comment= 链接
@Test void read_before_due_is_not_sent()
@Test void seen_during_wait_is_not_sent()                  // 期间 GET /me/notifications/unread-count
@Test void reply_and_mention_due_together_are_two_mails()
@Test void later_reply_in_window_joins_the_first()         // T0、T0+3m 两条 → T0+10m 一封「2 条新回复」
@Test void unverified_email_is_skipped()                   // 另有 banned、pending_deletion、mode OFF 参数化
@Test void headers_carry_one_click_unsubscribe()           // List-Unsubscribe 地址 + category=REPLY、List-Unsubscribe-Post、`Resend-Idempotency-Key` 等于投递 UUID、正文底部确认页链接
@Test void identity_down_defers_then_sends()               // 503 → PENDING、attempts=1 → 恢复 + 拨 1 分钟 → 一封，且只一封
@Test void transient_failure_retries_with_same_delivery_id() // 发信端口先抛 TRANSIENT（GreenMail 各上下文共用、不宜停，用 @TestConfiguration 包一层会失败 N 次的替身）→ PENDING、attempts=1、first_sent_at 已记 → 恢复 + 拨过退避 → 一封，Resend-Idempotency-Key 与 Message-ID 等于投递 UUID 对应的值
// DailyDigestFlowIT
@Test void lists_only_unread_in_window()                   // 窗口外、已读的不列；两节
@Test void nothing_unread_sends_nothing()
@Test void rerun_in_eight_oclock_hour_does_not_send_twice() // 8:00 跑 + 发；拨到 8:10 再跑 + 发 → 仍只一封
@Test void manual_run_only_targets_listed_users()
@Test void digest_unsubscribe_link_uses_digest_category()
```

- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-notification:patra-notification-boot:integrationTest --tests "*ReminderMailFlowIT*" --tests "*DailyDigestFlowIT*"`
- [ ] **Step 3: 修到通过**（实现已在前面任务里；这里只补装配缺口）
- [ ] **Step 4: 跑测试确认通过**

### Task 14: 文档、上线手册与门禁

**Files:**
- Modify：`patra-api/patra-notification/README.md`（`mail` 子域一行补「提醒邮件：排队表、投递记录、两个 XXL 任务」；子域依赖表标明已声明）
- Modify：`docs/patra/runbooks/v0.16-notification-go-live-runbook.md`（第 7 步加「PAP-146：提醒邮件」小节，照 spec 第 12 节）
- Modify：spec 末尾加「16. 实施记录」：与本计划和 spec 的偏离、门禁结果

- [ ] **Step 1:** `./gradlew spotlessApply`，然后 `./gradlew check integrationTest --continue`，预期 BUILD SUCCESSFUL；偶发的缓存锁或 IT 超时先单独重跑那个任务
- [ ] **Step 2:** 改过 `*.gradle.kts` 时，重跑 `./gradlew dumpModuleGraph`
- [ ] **Step 3:** 派一个独立评审子代理（`model: "opus"`），只给 spec 路径与改动文件清单，不给结论；重要问题先写复现测试再修，修完重跑 Step 1

### Task 15: 提交与汇报

- [ ] **Step 1:** `git add` 本 Issue 的路径（spec、plan、notification 各层、`patra-infra/nacos/dev/patra-notification.yaml`、runbook、README，以及可能的 `module-graph.json`）；`git diff --cached --stat` 核对没有别人的文件
- [ ] **Step 2:** 提交，例如 `feat(notification): 新增提醒邮件：离站逐条与每日汇总、一键退订头 (PAP-146)`，结尾带 Co-Authored-By
- [ ] **Step 3:** SendMessage 给编排会话：提交 SHA、AC 与测试的对应、全量测试结果、独立评审发现与处理、留下的小问题、上线步骤；接口摘要写进共享笔记 `PAP-146-reminder-mail-report.md`
