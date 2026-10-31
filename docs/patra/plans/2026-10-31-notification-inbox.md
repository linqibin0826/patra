# 通知服务信箱（PAP-143）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（本仓库约定：当前会话内联执行，不用 subagent-driven）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** patra-notification 消费 interaction 的集成事件写信箱，提供未读数（含 304）、列表、已读接口与在站信号；interaction 删掉 `notification` 子域并提供两个内部接口。

**Architecture:** inbox 子域（领域规则、三张表、四个摄入处理器、查询与已读用例、REST 与 MQ 适配器）＋ kernel 底座（在站信号、名片端口、对象视图端口、摘录渲染）；interaction 侧在 `subject` 底座与 `post` 子域各加一个 `/_internal` 接口，删除 `notification` 子域并用前向迁移删 schema。

**Tech Stack:** Java 25、Spring Boot 4.1.2、Spring AMQP（`patra-tool-events`）、JdbcClient 原生 SQL、PostgreSQL 17（Testcontainers）、RabbitMQ 4.4（Testcontainers）、JUnit 5 + AssertJ + Mockito、WireMock。

**Spec:** `docs/patra/specs/2026-10-31-notification-inbox-design.md`

## Global Constraints

- 包根 `dev.patra.notification`；inbox 只依赖 kernel，不新增跨子域依赖边（`NotificationArchitectureTest` 不改依赖声明）。
- 所有方法写 `///` Javadoc（Markdown）；不用全类名；参数 ≤ 4 的 record 用 `of()`，≥ 5 用 `@Builder`；record 里的集合在紧凑构造器里 `List.copyOf` / `Set.copyOf`。
- 命令处理器不加 `@Transactional`，注入 `TransactionOperations` 只包写库段；单测用 `TransactionOperations.withoutTransaction()`。
- 时间一律取注入的 `Clock`；库里时间列 `timestamptz(6)`，写入用 UTC `OffsetDateTime`。
- 迁移脚本版本号为写脚本时的 14 位时间戳；只存用户 ID（R6）。
- 去重消费方名（= 队列名）`notification.inbox`；交换机 `interaction.events`；路由键 `comment.published`、`comment.edited`、`comment.deleted`、`post.deleted`。
- 站内摘录 120 字素、单行；保留期北京时间 5 个自然月；每页 20 条；批量调用上限 100 个 ID。
- 错误码前缀 `NOTIFICATION`；traits `NOTIFICATION_NOT_FOUND`。
- 测试方法 snake_case；单元测试 `{类}Test`，集成测试 `{类或场景}IT`。
- 每个任务结束时 `./gradlew :patra-api:patra-notification:<模块>:check` 绿，再提交（中文 message，subject 以中文动词开头）。

## Review Focus

1. 19 位的 `<@9999999999999999999>`（超出 long）：渲染时当普通文字原样输出，不抛异常 —— Task 1 `should_keep_overflowing_mention_as_literal`。
2. 评论正文全是 `\n` 或尾部空白：单行渲染后没有前后空白、截断后去尾空白 —— Task 1 `should_collapse_newlines_and_strip_when_cut`。
3. 同一页里多条提醒指向同一对象 / 同一触发人：对象视图与名片只查一次（去重后调用） —— Task 8 `should_query_each_subject_and_user_once`。
4. 收件人既是被回复的人又是经验作者（回复了经验作者的根评论下的楼）：只一条，类型取第一个 —— Task 2 `should_notify_owner_as_reply_when_replied_directly`。
5. interaction 返回的对象类型是不认识的值（如以后加的 `JOURNAL` 下的评论）：不抛异常，`subject.type` 原样不映射时整条 `subject` 为空 —— Task 6 `should_skip_unknown_subject_type`。

---

## 阶段 A：信箱核心（基线 `f46edcdc0`，已含 PAP-142）

### Task 1: 底座的名片模型与摘录渲染

**Files:**
- Create: `patra-notification-domain/src/main/java/dev/patra/notification/domain/kernel/model/read/UserCard.java`
- Create: `.../domain/kernel/model/read/RenderedExcerpt.java`
- Create: `.../domain/kernel/policy/MentionNames.java`
- Create: `.../domain/kernel/policy/ExcerptRenderer.java`
- Test: `patra-notification-domain/src/test/java/dev/patra/notification/domain/kernel/policy/MentionNamesTest.java`、`ExcerptRendererTest.java`

（以下路径省略前缀 `patra-api/patra-notification/`。）

**Interfaces:**
- Produces:
  - `record UserCard(long userId, String username, String displayName, String avatarUrl, int avatarPalette, AccountState state, boolean profileVisible)`，`@Builder`
  - `record RenderedExcerpt(String text, boolean truncated)`，`of(text, truncated)`
  - `MentionNames.DELETED_USER = "已注销用户"`；`static String of(UserCard card)`（`null` → 已注销用户；`profileVisible && username != null` → username；否则 `displayName`，为空 → 已注销用户）
  - `ExcerptRenderer.MENTION = Pattern.compile("<@([1-9][0-9]{0,18})>")`；`static RenderedExcerpt render(String plainText, Map<Long, String> names, int maxGraphemes, boolean singleLine)`

- [ ] **Step 1: 写失败的测试**

`MentionNamesTest`：`should_use_username_when_profile_visible`（可见 + `zhou` → `zhou`）、`should_use_display_name_when_profile_hidden`（不可见 → `周敏`）、`should_name_unknown_or_deleted_user`（`null` 卡片、昵称为空 → `已注销用户`）。

`ExcerptRendererTest`（`names` 里 `1L → "zhou"`）：
- `should_replace_mentions_with_names`：`"谢谢 <@1> 分享"` → `"谢谢 @zhou 分享"`、未截断
- `should_name_missing_user_as_deleted`：`"<@2>"` 不在 `names` → `"@已注销用户"`
- `should_collapse_newlines_when_single_line`：`"a\nb"` 单行 → `"a b"`；多行 → `"a\nb"`
- `should_cut_to_max_graphemes`：130 个 `"字"`、上限 120 → 120 个字、`truncated` 为真；emoji `"👍🏽"` 算 1 个字素
- `should_stop_before_mention_that_does_not_fit`：118 个 `"字"` + `"<@1>"`（`@zhou` 占 5）、上限 120 → 只剩 118 个字、截断
- `should_collapse_newlines_and_strip_when_cut`：`"字…字 \n\n后面"`（119 个字后跟空格与换行）、上限 120 单行 → 结尾没有空白、截断
- `should_keep_overflowing_mention_as_literal`：`"<@9999999999999999999>"` → 原样输出

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :patra-api:patra-notification:patra-notification-domain:test --tests "*MentionNamesTest" --tests "*ExcerptRendererTest"`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现**

`ExcerptRenderer.render`：先把文本切成「字面段 / 提及段」（正则匹配，`Long.parseLong` 失败的按字面），单行时字面段里的 `\n` 换成空格（在数字素之前做）；逐段累计字素（`BreakIterator.getCharacterInstance()`），提及段按 `"@" + 名字` 的字素数整体放入或停止；截了时 `stripTrailing()`。

- [ ] **Step 4: 运行，确认通过**（同 Step 2 命令，PASS）

- [ ] **Step 5: 提交** `git commit -m "feat(notification): 新增底座的名片模型与摘录渲染规则 (PAP-143)"`

### Task 2: 信箱的提醒类型与收件人规则

**Files:**
- Create: `.../domain/kernel/model/enums/SubjectType.java`
- Create: `.../domain/inbox/model/read/NotificationType.java`（公开包：mail 要按它写动作文案）、`.../model/enums/InboxTab.java`
- Create: `.../domain/inbox/model/vo/CommentFacts.java`、`Recipient.java`
- Create: `.../domain/inbox/policy/RecipientRules.java`
- Create: `.../domain/inbox/exception/InboxErrorTrait.java`、`InvalidInboxRequestException.java`、`NotificationNotFoundException.java`
- Test: `.../domain/inbox/policy/RecipientRulesTest.java`、`.../model/enums/InboxTabTest.java`、`.../kernel/model/enums/SubjectTypeTest.java`

**Interfaces:**
- Produces:
  - `enum SubjectType { PUBLICATION, POST }`，`static Optional<SubjectType> parse(String raw)`（不认识 → 空）
  - `enum NotificationType { COMMENT_REPLY(REPLY), POST_COMMENT(REPLY), MENTION(MENTION) }`，`NotificationCategory category()`，`static List<NotificationType> of(NotificationCategory)`
  - `enum InboxTab { REPLIES("replies", REPLY), MENTIONS("mentions", MENTION), HELPFUL("helpful", HELPFUL) }`，`category()`、`static InboxTab fromParam(String)`（空 → REPLIES；不认识 → `InvalidInboxRequestException`）
  - `@Builder record CommentFacts(long commentId, int version, long subjectId, SubjectType subjectType, Long subjectOwnerId, Long parentCommentId, Long parentAuthorId, long authorId, boolean authorAnonymous, List<Long> mentionedUserIds, String plainText, Instant occurredAt)`；`subjectType` 可空（不认识的类型）；`mentionedUserIds` 空值视为空列表
  - `record Recipient(long userId, NotificationType type)`，`of(...)`
  - `RecipientRules.forPublished(CommentFacts) → List<Recipient>`；`RecipientRules.forEdited(CommentFacts, List<Long> addedMentionedUserIds) → List<Long>`
  - `enum InboxErrorTrait implements ErrorTrait { NOTIFICATION_NOT_FOUND }`；`NotificationNotFoundException()`（`NOT_FOUND` + `NOTIFICATION_NOT_FOUND`，消息「提醒不存在」）；`InvalidInboxRequestException(String)`（`RULE_VIOLATION`）

- [ ] **Step 1: 写失败的测试**（作者 9、被回复者 2、经验作者 3、@ 4）

`RecipientRulesTest`（搬 interaction `NotificationRulesTest` 的 8 例并补新规则）：
- `should_notify_parent_author_as_reply` → `[Recipient(2, COMMENT_REPLY)]`
- `should_not_notify_self`：被回复者是 9 → 空
- `should_send_one_reply_when_parent_author_is_also_mentioned`：回复 2 且 @2、@4 → `[(2, COMMENT_REPLY), (4, MENTION)]`
- `should_notify_post_owner_on_root_comment`：POST、根评论、owner 3 → `[(3, POST_COMMENT)]`
- `should_not_notify_post_owner_on_reply_to_others`：POST、回复 2 → 只有 2
- `should_notify_owner_as_reply_when_replied_directly`：POST、回复的就是 3 → `[(3, COMMENT_REPLY)]`
- `should_ignore_owner_on_publication`：PUBLICATION、根评论、owner 非空 → 空
- `should_ignore_owner_when_subject_type_unknown`：`subjectType` 为空 → 空
- `should_notify_only_added_mentions_on_edit`：added `[4, 9]` → `[4]`
- `should_skip_parent_author_and_post_owner_on_edit`：回复 2、added `[2, 4]` → `[4]`；POST 根评论、owner 3、added `[3, 4]` → `[4]`；POST 非根、owner 3、added `[3]` → `[3]`

`InboxTabTest`：`replies` / `mentions` / `helpful` / 空 → 对应；`"x"` 抛 `InvalidInboxRequestException`。`SubjectTypeTest`：`"POST"` → POST；`"JOURNAL"`、`null` → 空。

- [ ] **Step 2: 运行确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-domain:test --tests "*RecipientRulesTest" --tests "*InboxTabTest" --tests "*SubjectTypeTest"`

- [ ] **Step 3: 实现**（`forPublished` 用 `LinkedHashMap.putIfAbsent` 保序；`forEdited` 用 `LinkedHashSet` 去作者、被回复者、根评论在 POST 下的经验作者）

- [ ] **Step 4: 运行确认通过**

- [ ] **Step 5: 提交** `feat(notification): 搬入收件人规则并让编辑的 @ 与到达顺序无关 (PAP-143)`

### Task 3: 信箱的读模型、游标、保留期、事件与端口

**Files:**
- Create: `.../domain/inbox/model/read/InboxCursor.java`（公开包：读端口签名用它）、`.../model/vo/NotificationDraft.java`
- Create: `.../domain/inbox/model/read/InboxEntry.java`、`UnreadCounts.java`、`CreatedNotification.java`
- Create: `.../domain/inbox/policy/InboxRetention.java`
- Create: `.../domain/inbox/event/UnreadCountsChangedEvent.java`、`NotificationsCreatedEvent.java`
- Create: `.../domain/inbox/port/repository/NotificationRepository.java`、`CommentContentRepository.java`、`SubjectTombstoneRepository.java`
- Create: `.../domain/inbox/port/read/InboxReadPort.java`
- Test: `InboxCursorTest`、`UnreadCountsTest`、`InboxRetentionTest`

**Interfaces:**
- Produces:
  - `record InboxCursor(Instant occurredAt, long notificationId)`：`of(...)`、`String encode()`（`"{epoch 微秒}_{id}"` 的 Base64URL、无填充）、`static InboxCursor parse(String)`（空 → `null`；坏 → `InvalidInboxRequestException("cursor 不合法")`）
  - `@Builder record NotificationDraft(long recipientId, NotificationType type, long actorId, boolean actorAnonymous, long subjectId, Long commentId, Instant occurredAt)`
  - `@Builder record InboxEntry(long notificationId, NotificationType type, long actorId, boolean actorAnonymous, long subjectId, Long commentId, String plainText, Instant occurredAt, Instant readAt)`，`boolean read()`
  - `record UnreadCounts(int replies, int mentions, int helpful)`：`of(...)`、`int total()`（= replies + mentions）、`String etag()`（`"\"r-m-h\""`）
  - `record CreatedNotification(long notificationId, long recipientId, NotificationCategory category, Instant occurredAt)`，`of(...)`
  - `InboxRetention.MONTHS = 5`、`ZONE = Asia/Shanghai`、`static Instant cutoff(Instant now)`
  - `record UnreadCountsChangedEvent(Set<Long> recipientIds)`，`of(Collection<Long>)`；`record NotificationsCreatedEvent(List<CreatedNotification> notifications)`，`of(...)`
  - `NotificationRepository`：
    - `List<CreatedNotification> insertAll(List<NotificationDraft> drafts)`（撞 `uk_notification_comment` 的跳过，返回真写入的）
    - `Set<Long> deleteByComment(long commentId)`、`Set<Long> deleteBySubject(long subjectId)`（返回受影响的收件人）
    - `MarkReadOutcome markRead(long recipientId, long notificationId, Instant readAt)`：`enum MarkReadOutcome { NOT_FOUND, ALREADY_READ, MARKED }` 放 `domain.inbox.model.enums`；已读的不改时刻
    - `int markAllRead(long recipientId, Instant readAt)`
    - `int deleteOccurredBefore(Instant cutoff)`、`int deleteAllOf(long recipientId)`、`List<Long> findRecipientsAfter(long afterUserId, int limit)`
  - `CommentContentRepository`：
    - `boolean saveIfNewer(long commentId, long subjectId, int version, String plainText, Instant now)`：upsert；返回 `false` 表示这条评论已是墓碑
    - `void markDeleted(long commentId, long subjectId, Instant deletedAt, Instant now)`
    - `boolean isDeleted(long commentId)`、`int deleteBySubject(long subjectId)`、`int purgeUnreferencedBefore(Instant cutoff)`
  - `SubjectTombstoneRepository`：`boolean exists(long subjectId)`、`void save(long subjectId, Instant deletedAt)`（幂等）、`int purgeBefore(Instant cutoff)`
  - `InboxReadPort`：`List<InboxEntry> page(long recipientId, NotificationCategory category, InboxCursor after, int limit)`、`UnreadCounts unreadCounts(long recipientId)`

- [ ] **Step 1: 写失败的测试**
  - `InboxCursorTest`：`should_round_trip`（`Instant.parse("2026-10-31T02:15:30.123456Z")`、ID `1849` → encode 再 parse 相等）；`should_return_null_for_blank`；`should_reject_garbage`（`"abc"`、`Base64("x_y")`、`Base64("1_-5")` 抛异常）
  - `UnreadCountsTest`：`of(2,1,4)` → `total()` 3、`etag()` `"\"2-1-4\""`
  - `InboxRetentionTest`：`2026-07-31T16:00:00Z`（北京 8/1 0:00）→ `2026-02-28T16:00:00Z`；`2026-10-31T00:00:00Z` → `2026-05-31T00:00:00Z`
- [ ] **Step 2: 运行确认失败** `--tests "*InboxCursorTest" --tests "*UnreadCountsTest" --tests "*InboxRetentionTest"`
- [ ] **Step 3: 实现**（端口只有接口；`etag` 带双引号）
- [ ] **Step 4: 运行确认通过**；再跑 `:patra-notification-boot:test --tests "*NotificationArchitectureTest"`（端口命名、事件包）
- [ ] **Step 5: 提交** `feat(notification): 定义信箱的读模型、游标、保留期、事件与端口 (PAP-143)`

### Task 4: 信箱的表与持久化适配器

**Files:**
- Create: `patra-notification-infra/src/main/resources/db/migration/inbox/V20261031<时分秒>__create_inbox_schema.sql`、`V…__create_inbox_tables.sql`
- Create: `.../infra/inbox/persistence/NotificationRepositoryAdapter.java`、`CommentContentRepositoryAdapter.java`、`SubjectTombstoneRepositoryAdapter.java`、`InboxReadAdapter.java`、`InboxRows.java`
- Test（infra `src/integrationTest`）: `.../infra/inbox/persistence/InboxPersistenceITBootstrap.java`、`InboxPersistenceITSupport.java`、`NotificationRepositoryAdapterIT.java`、`CommentContentRepositoryAdapterIT.java`、`SubjectTombstoneRepositoryAdapterIT.java`、`InboxReadAdapterIT.java`

**Interfaces:**
- Consumes: Task 3 的三个仓储与读端口。
- Produces: 表结构见 spec 6.2（列名、CHECK、六条索引照抄）；`InboxRows.utc(Instant) → OffsetDateTime`、`InboxRows.instant(ResultSet, String)`。

关键 SQL：
- `insertAll`：多行 `INSERT … ON CONFLICT (recipient_id, comment_id) WHERE category IN ('REPLY', 'MENTION') DO NOTHING RETURNING notification_id, recipient_id, category, occurred_at`；ID 用 `SnowflakeIdGenerator.getId()`。
- `saveIfNewer`：`INSERT INTO inbox.comment AS c … ON CONFLICT (comment_id) DO UPDATE SET version = CASE WHEN c.deleted_at IS NULL AND c.version < EXCLUDED.version THEN EXCLUDED.version ELSE c.version END,`（`plain_text`、`updated_at` 同理）`RETURNING c.deleted_at IS NULL`。
- `markDeleted`：`ON CONFLICT DO UPDATE SET plain_text = NULL, deleted_at = COALESCE(c.deleted_at, EXCLUDED.deleted_at), updated_at = EXCLUDED.updated_at`。
- `purgeUnreferencedBefore`：`DELETE FROM inbox.comment c WHERE c.updated_at < :cutoff AND NOT EXISTS (SELECT 1 FROM inbox.notification n WHERE n.comment_id = c.comment_id)`。
- `page`：`LEFT JOIN inbox.comment c ON c.comment_id = n.comment_id`，`WHERE n.recipient_id = :r AND n.category = :category AND (CAST(:at AS timestamptz) IS NULL OR (n.occurred_at, n.notification_id) < (:at, :id)) ORDER BY n.occurred_at DESC, n.notification_id DESC LIMIT :limit`。
- `markRead`：`UPDATE … SET read_at = :at WHERE recipient_id = :r AND notification_id = :id AND read_at IS NULL`，更新 1 行 → `MARKED`；0 行时再 `SELECT 1 … WHERE recipient_id = :r AND notification_id = :id` 区分 `ALREADY_READ` / `NOT_FOUND`。

- [ ] **Step 1: 写失败的集成测试**（`@SpringBootTest(classes = InboxPersistenceITBootstrap.class)` + `NotificationITPostgreSQLContainerInitializer`，照 `PreferencePersistenceITSupport`）
  - `NotificationRepositoryAdapterIT`：`should_skip_second_reply_or_mention_for_same_comment`（同一人同一评论 REPLY 后再 MENTION → 第二次返回空、库里 1 行）；`should_return_inserted_rows`；`should_delete_by_comment_for_all_recipients`（返回两个收件人）；`should_delete_by_subject`；`should_mark_read_once`（MARKED → ALREADY_READ，时刻不变；别人的 → NOT_FOUND）；`should_mark_all_read_across_categories`；`should_delete_occurred_before`；`should_list_recipients_after`
  - `CommentContentRepositoryAdapterIT`：`should_keep_newer_version`（存 v2 再来 v1 → 正文仍是 v2、返回 true）；`should_overwrite_older_version`；`should_report_tombstone`（`markDeleted` 后 `saveIfNewer` 返回 false、正文为空）；`should_create_tombstone_when_absent`；`should_purge_only_unreferenced`
  - `SubjectTombstoneRepositoryAdapterIT`：`should_save_idempotently_and_report_exists`；`should_purge_before`
  - `InboxReadAdapterIT`：`should_page_by_occurred_at_then_id`（三行、两行同一时刻，按游标翻两页不重不漏）；`should_join_comment_text_and_hide_tombstone`；`should_count_unread_per_category`（REPLY 2、MENTION 1、已读 1 → `UnreadCounts(2,1,0)`）
- [ ] **Step 2: 运行确认失败** `./gradlew :patra-api:patra-notification:patra-notification-infra:integrationTest --tests "*inbox*"`
- [ ] **Step 3: 写迁移与四个适配器**
- [ ] **Step 4: 运行确认通过**；再跑 `NotificationMigrationIT`
- [ ] **Step 5: 提交** `feat(notification): 建信箱的表并实现持久化适配器 (PAP-143)`

### Task 5: 在站信号

**Files:**
- Create: `.../db/migration/kernel/V20261031<时分秒>__create_kernel_presence.sql`（`CREATE SCHEMA kernel` + `kernel.presence`）
- Create: `.../domain/kernel/port/gateway/PresenceGateway.java`、`.../domain/kernel/port/repository/PresenceRepository.java`
- Create: `.../infra/kernel/persistence/PresenceRepositoryAdapter.java`
- Create: `.../app/kernel/service/PresenceGatewayImpl.java`、`KernelUserDataCleanupGatewayImpl.java`
- Test: `PresenceRepositoryAdapterIT`（infra）、`PresenceGatewayImplTest`、`KernelUserDataCleanupGatewayImplTest`（app）

**Interfaces:**
- Produces:
  - `PresenceGateway`：`void markSeen(long userId)`；`Map<Long, Instant> lastSeen(Collection<Long> userIds)`
  - `PresenceRepository`：`void touch(long userId, Instant seenAt)`（`GREATEST`）；`Map<Long, Instant> find(Collection<Long> userIds)`；`int deleteAllOf(long userId)`；`List<Long> findUsersAfter(long afterUserId, int limit)`
  - `KernelUserDataCleanupGatewayImpl.subdomain()` = `"kernel"`

- [ ] **Step 1: 写失败的测试**：`PresenceRepositoryAdapterIT.should_keep_latest_seen_at`（先 10:05 再 10:00 → 10:05）、`should_find_only_known_users`、`should_delete_and_page_users`；`PresenceGatewayImplTest.should_mark_seen_at_clock_instant`（固定时钟 → `touch(7, 时钟时刻)`）、`should_return_empty_for_no_users`（空集合不查库）
- [ ] **Step 2: 运行确认失败**
- [ ] **Step 3: 实现**（`markSeen` 包 `TransactionOperations` 一段）
- [ ] **Step 4: 运行确认通过**（app `test` + infra `integrationTest --tests "*Presence*"`）
- [ ] **Step 5: 提交** `feat(notification): 新增在站信号的存储与底座端口 (PAP-143)`

### Task 6: 底座的名片端口与对象视图端口（调 identity、interaction）

**Files:**
- Create: `.../domain/kernel/port/identity/UserCardPort.java`
- Create: `.../domain/kernel/port/interaction/SubjectViewPort.java`、`.../domain/kernel/model/read/SubjectView.java`、`ExperienceOutline.java`、`.../domain/kernel/exception/InteractionUnavailableException.java`
- Create: `patra-api/patra-interaction/patra-interaction-api/src/main/java/dev/patra/interaction/api/subject/endpoint/SubjectEndpoint.java`、`.../api/subject/dto/SubjectViewsReq.java`、`SubjectViewsResp.java`、`SubjectViewResp.java`
- Create: `.../interaction/api/post/endpoint/PostEndpoint.java`、`.../api/post/dto/ExperienceOutlinesReq.java`、`ExperienceOutlinesResp.java`、`ExperienceOutlineResp.java`
- Create: `.../infra/kernel/identity/UserCardAdapter.java`、`.../infra/kernel/interaction/SubjectViewAdapter.java`
- Create: `patra-notification-boot/src/main/java/dev/patra/notification/config/InteractionClientConfiguration.java`
- Modify: `patra-notification-infra/build.gradle.kts`（加 `implementation(project(":patra-api:patra-interaction:patra-interaction-api"))`）、boot `build.gradle.kts`（同）、boot `application.yml`（分组 `interaction`：`lb://patra-interaction`、1s / 2s）
- Test: `UserCardAdapterTest`、`SubjectViewAdapterTest`（infra 单元，mock 代理）

**Interfaces:**
- Produces:
  - `UserCardPort.MAX_USERS = 100`；`Map<Long, UserCard> findCards(Collection<Long> userIds, Long viewerId)`
  - `SubjectViewPort.MAX_SUBJECTS = 100`；`Map<Long, SubjectView> findViews(Collection<Long> subjectIds)`
  - `@Builder record SubjectView(long subjectId, SubjectType type, String key, boolean available, String title, String journal, String abbreviation, String publishedDate, Long ownerId, boolean ownerAnonymous, ExperienceOutline experience)`
  - `record ExperienceOutline(String firstDecision, String finalOutcome, int firstRoundDays, Integer totalDays)`，`of(...)`
  - `InteractionUnavailableException(Throwable cause)`（`DEP_UNAVAILABLE`，消息「讨论信息暂时无法获取」）
  - interaction-api：`SubjectEndpoint`（`@HttpExchange(url = "/_internal/subjects", …)`，`@PostExchange("/views") SubjectViewsResp views(@RequestBody SubjectViewsReq)`）；`record SubjectViewsReq(List<Long> subjectIds)`；`record SubjectViewsResp(List<SubjectViewResp> subjects)`；`@Builder record SubjectViewResp(Long subjectId, String type, String key, boolean available, String title, String journal, String publishedDate, String abbreviation, Long ownerId, boolean ownerAnonymous)`
  - `PostEndpoint`（`url = "/_internal/posts"`，`@PostExchange("/outlines") ExperienceOutlinesResp outlines(@RequestBody ExperienceOutlinesReq)`）；`record ExperienceOutlinesReq(List<Long> postIds)`；`record ExperienceOutlinesResp(List<ExperienceOutlineResp> outlines)`；`@Builder record ExperienceOutlineResp(Long postId, String firstDecision, String finalOutcome, int firstRoundDays, Integer totalDays)`

- [ ] **Step 1: 写失败的测试**
  - `UserCardAdapterTest`：`should_map_summaries_with_viewer`（请求带 `viewerUserId` 7；`profileVisible` 透传）；`should_reject_more_than_100`；`should_translate_failures`（`RemoteCallException`、`ResourceAccessException`、`IllegalStateException` → `IdentityUnavailableException`）
  - `SubjectViewAdapterTest`：`should_merge_post_outlines_by_key`（两个视图：PUBLICATION 与 POST key `"55"`；outlines 只按 `[55]` 调一次 → POST 视图带结果）；`should_not_call_outlines_without_posts`；`should_skip_unknown_subject_type`（`"JOURNAL"` 不进结果）；`should_skip_post_with_non_numeric_key`（key 为空 → 视图在、`experience` 为空）；`should_translate_failures`（任一调用失败 → `InteractionUnavailableException`）
- [ ] **Step 2: 运行确认失败** `./gradlew :patra-api:patra-notification:patra-notification-infra:test --tests "*UserCardAdapterTest" --tests "*SubjectViewAdapterTest"`
- [ ] **Step 3: 实现**（`try` 只包一次代理调用，同 interaction 的 `UserDirectoryAdapter`；代理在 `InteractionClientConfiguration` 里用 `RestClientFactory` 建，分组 `interaction`）
- [ ] **Step 4: 运行确认通过**；`./gradlew :patra-api:patra-interaction:patra-interaction-api:check :patra-api:patra-notification:patra-notification-boot:test`（`ApplicationConfigTest` 若校验分组也要过）
- [ ] **Step 5: 提交** `feat(notification): 新增名片与对象视图的底座端口及 interaction 内部接口契约 (PAP-143)`

### Task 7: 摄入四种事件的处理器

**Files:**
- Create: `.../app/inbox/usecase/ingest/InboxConsumer.java`（`NAME = "notification.inbox"`）
- Create: `RecordCommentPublishedCommand/Handler`、`RecordCommentEditedCommand/Handler`、`RecordCommentDeletedCommand/Handler`、`RecordPostDeletedCommand/Handler`（同包）
- Modify: `patra-notification-app/build.gradle.kts`（若 `ProcessedEventPort` 的 tool-core 未传入则加；它随 domain 插件已在）
- Test: 四个 `*HandlerTest`

**Interfaces:**
- Consumes: Task 2 `CommentFacts`、`RecipientRules`；Task 3 仓储、事件；`ProcessedEventPort`（`dev.patra.tool.core.event`）；`ApplicationEventPublisher`。
- Produces（都 `implements Command<IngestOutcome>`，`IngestOutcome { DUPLICATE, DISCARDED, APPLIED }` 放同包）：
  - `RecordCommentPublishedCommand(UUID eventId, CommentFacts facts)`，`of(...)`
  - `RecordCommentEditedCommand(UUID eventId, CommentFacts facts, List<Long> addedMentionedUserIds)`，`of(...)`（`List.copyOf`）
  - `RecordCommentDeletedCommand(UUID eventId, long commentId, long subjectId, Instant deletedAt)`，`of(...)`
  - `RecordPostDeletedCommand(UUID eventId, long subjectId, Instant deletedAt)`，`of(...)`

- [ ] **Step 1: 写失败的测试**（mock 仓储、`ProcessedEventPort`、`ApplicationEventPublisher`；`TransactionOperations.withoutTransaction()`；固定 `Clock`）
  - 发表：`should_skip_duplicate_event`（`markProcessed` false → 不碰仓储、返回 DUPLICATE）；`should_discard_when_subject_tombstoned`；`should_discard_when_comment_tombstoned`（`saveIfNewer` false → 不写提醒）；`should_write_recipients_and_publish_events`（回复 2、@4 → `insertAll` 两条草稿，`occurredAt` = 事件时刻、`actorAnonymous` 透传；发布 `NotificationsCreatedEvent` 与 `UnreadCountsChangedEvent([2,4])`）；`should_not_publish_when_nothing_inserted`
  - 编辑：`should_write_mentions_for_added_users_only`（类型都是 MENTION）；`should_still_save_content_when_no_recipients`
  - 删除：`should_tombstone_and_delete_notifications`（`markDeleted` 与 `deleteByComment`，收件人 `{2}` → 发 `UnreadCountsChangedEvent`）；`should_not_publish_when_none_deleted`
  - 经验删除：`should_tombstone_subject_and_delete_everything_under_it`（`save`、`deleteBySubject`（提醒）、`CommentContentRepository.deleteBySubject`）
- [ ] **Step 2: 运行确认失败** `./gradlew :patra-api:patra-notification:patra-notification-app:test --tests "*ingest*"`
- [ ] **Step 3: 实现**：事务内第一步 `processed.markProcessed(InboxConsumer.NAME, eventId)`；事件在事务内 `publishEvent`
- [ ] **Step 4: 运行确认通过**
- [ ] **Step 5: 提交** `feat(notification): 新增摄入评论与经验事件的处理器 (PAP-143)`

### Task 8: 查询、已读、拉未读数、保留期与注销清理用例

**Files:**
- Create: `.../app/inbox/usecase/query/InboxQueryService.java`、`NotificationPage.java`、`NotificationView.java`、`SubjectRefView.java`
- Create: `.../app/inbox/usecase/read/MarkNotificationReadCommand/Handler`、`MarkAllNotificationsReadCommand/Handler`
- Create: `.../app/inbox/usecase/poll/PollUnreadCountsCommand/Handler`
- Create: `.../app/inbox/usecase/purge/PurgeExpiredInboxCommand/Handler`、`PurgeResult.java`
- Create: `.../app/inbox/service/InboxUserDataCleanupGatewayImpl.java`
- Test: 对应 `*Test`

**Interfaces:**
- Consumes: Task 1、3、5、6。`CurrentUserPort`（`dev.patra.tool.core.security`）。
- Produces:
  - `InboxQueryService.PAGE_SIZE = 20`；`NotificationPage page(String tab, String cursor)`
  - `record NotificationPage(List<NotificationView> items, String nextCursor)`，`of(...)`
  - `@Builder record NotificationView(long notificationId, NotificationType type, boolean read, Instant occurredAt, UserCard actor, boolean actorAnonymous, SubjectRefView subject, Long commentId, String excerpt, boolean excerptTruncated)`
  - `@Builder record SubjectRefView(SubjectType type, String key, boolean available, String title, String journal, String abbreviation, String submittedMonth, boolean mine, ExperienceOutline experience)`
  - `MarkNotificationReadCommand(long notificationId)` → `UnreadCounts`；`MarkAllNotificationsReadCommand()` → `UnreadCounts`；`PollUnreadCountsCommand()` → `UnreadCounts`；`PurgeExpiredInboxCommand()` → `PurgeResult(int notifications, int comments, int tombstones)`
  - `InboxUserDataCleanupGatewayImpl.subdomain()` = `"inbox"`

- [ ] **Step 1: 写失败的测试**
  - `InboxQueryServiceTest`：`should_render_page_with_cards_and_subjects`（一行 REPLY：actor 卡片、POST 视图 owner = 收件人 → `mine` 真、`submittedMonth` = `publishedDate`、`experience` 透传；摘录换名 120 单行）；`should_hide_actor_when_anonymous`（不查他的名片）；`should_degrade_subject_when_interaction_unavailable`（`subject` 与 `excerpt` 为空，不抛异常）；`should_hide_excerpt_when_subject_unavailable`；`should_propagate_identity_failure`；`should_query_each_subject_and_user_once`（两行同对象、同触发人 → 各调一次、参数去重）；`should_return_next_cursor_only_when_more`（读 21 行 → 20 条 + 游标 = 第 20 条的 `InboxCursor.encode()`）；`should_reject_bad_tab_and_cursor`
  - `MarkNotificationReadHandlerTest`：`should_return_counts_and_publish_when_marked`；`should_not_publish_when_already_read`；`should_throw_not_found`
  - `MarkAllNotificationsReadHandlerTest`：`should_publish_only_when_marked_any`
  - `PollUnreadCountsHandlerTest`：`should_mark_presence_then_read_counts`（`InOrder`）
  - `PurgeExpiredInboxHandlerTest`：`should_purge_with_beijing_five_month_cutoff`（三个仓储都用同一个截止时刻）
  - `InboxUserDataCleanupGatewayImplTest`：委托 `findRecipientsAfter` / `deleteAllOf`
- [ ] **Step 2: 运行确认失败**
- [ ] **Step 3: 实现**（名片按 `UserCardPort.MAX_USERS` 分批；对象视图一页 ≤ 20 个一次调完；`InteractionUnavailableException` 打 WARN 后当空表）
- [ ] **Step 4: 运行确认通过**：`./gradlew :patra-api:patra-notification:patra-notification-app:check`
- [ ] **Step 5: 提交** `feat(notification): 新增信箱的列表、已读、未读数、保留期与注销清理用例 (PAP-143)`

## 阶段 B：门户接口与任务

### Task 9: REST 接口与保留期任务

**Files:**
- Create: `.../adapter/inbox/rest/MyNotificationController.java`
- Create: `.../adapter/inbox/rest/response/UnreadCountsResponse.java`、`NotificationPageResponse.java`、`NotificationResponse.java`、`ActorResponse.java`、`SubjectRefResponse.java`、`ExperienceResponse.java`
- Create: `.../adapter/inbox/scheduler/InboxRetentionScheduleJob.java`（`@XxlJob("inboxRetentionJob")`）
- Test: `adapter/src/test/.../inbox/rest/MyNotificationControllerTest.java`（`@WebMvcTest` 切片，照 PAP-142 的 controller 测试写法）、`InboxRetentionScheduleJobTest.java`
- Test: `boot/src/integrationTest/.../inbox/InboxRestFlowIT.java`（继承 `NotificationFlowITSupport`，WireMock 扮 identity 与 interaction；`support` 里加 `patra-interaction` 的 simple-discovery 实例与打桩方法）

**Interfaces:**
- Consumes: Task 8 的命令与查询。
- Produces: spec 第 8 节的 JSON；`UnreadCountsResponse(int total, int replies, int mentions, int helpful)`；`SubjectRefResponse.type` = `publication` / `post`。

- [ ] **Step 1: 写失败的测试**
  - `MyNotificationControllerTest`：`should_return_counts_with_etag_and_cache_control`（`ETag: "2-1-0"`、`Cache-Control` 含 `no-cache` 与 `private`、`total` 3、`helpful` 0）；`should_return_304_when_etag_matches`（`If-None-Match: W/"2-1-0", "x"` → 304、无体、头同上）；`should_treat_star_as_match`；`should_render_page_json`（`occurredAt`、`commentId` 字符串、`subject.experience`）；`should_mark_read_and_all_read`
  - `InboxRestFlowIT`：`should_require_login`（401）；`should_record_presence_on_304`（第二次带 ETag → 304，`kernel.presence` 有这人且时刻更新）；`should_list_with_subject_from_interaction`（直接往表里插一行提醒与快照，WireMock 打桩 `/_internal/subjects/views`、`/_internal/users/summaries`）；`should_degrade_when_interaction_down`；`should_404_for_others_notification`
- [ ] **Step 2: 运行确认失败**
- [ ] **Step 3: 实现**（ETag 与 `If-None-Match` 匹配照 interaction 的 `MyNotificationController.matches`）
- [ ] **Step 4: 运行确认通过**：adapter `check`、boot `integrationTest --tests "*InboxRestFlowIT"`
- [ ] **Step 5: 提交** `feat(notification): 新增提醒的门户接口与保留期任务 (PAP-143)`

## 阶段 C：消费事件与 interaction 侧（基线须含 PAP-141）

开工前：向编排会话要含 PAP-141 的集成分支 SHA，`git rebase --onto <新基线> f46edcdc0` 挪本分支提交。

### Task 10: interaction 的两个内部接口

**Files:**
- Create: `patra-interaction-app/.../app/subject/usecase/query/SubjectViewQueryService.java`
- Create: `patra-interaction-app/.../app/post/usecase/query/ExperienceOutlineQueryService.java`
- Modify: `patra-interaction-domain/.../domain/post/port/read/PostReadPort.java`（加 `Map<PostId, ExperienceOutline> findOutlines(Collection<PostId> ids)`）、`patra-interaction-infra/.../infra/post/persistence/PostReadAdapter.java`
- Create: `patra-interaction-adapter/.../adapter/subject/internal/SubjectEndpointImpl.java`、`.../adapter/post/internal/PostEndpointImpl.java`（各带 `*ApiConverter`）
- Modify: adapter / app 的 `build.gradle.kts`（`patra-interaction-api` 依赖，若尚未声明）
- Test: `SubjectViewQueryServiceTest`、`ExperienceOutlineQueryServiceTest`、`PostReadAdapterIT` 加例、`SubjectEndpointImplIT`、`PostEndpointImplIT`（adapter `integrationTest`，照 identity 的 `UserEndpointImplIT`）

**Interfaces:**
- Consumes: Task 6 的 interaction-api 契约；PAP-141 的 `ExperienceOutline`（`domain.post.model.read`）。
- Produces: spec 11.1 的两个接口。

- [ ] **Step 1: 写失败的测试**：`should_keep_request_order_and_omit_unknown`；`should_reject_empty_null_or_over_100`（422）；`should_map_available_from_state`；`should_include_deleted_posts`（outlines）
- [ ] **Step 2–4: 失败 → 实现 → 通过**（`./gradlew :patra-api:patra-interaction:patra-interaction-app:test :patra-interaction-adapter:integrationTest --tests "*EndpointImplIT"`）
- [ ] **Step 5: 提交** `feat(interaction): 新增对象视图与经验结果的内部接口 (PAP-143)`

### Task 11: 消费 interaction 的事件

**Files:**
- Create: `.../adapter/inbox/mq/InboxEventListener.java`
- Create: `patra-notification-boot/.../config/InboxQueueConfiguration.java`（`Declarables`：`EventQueue.named(InboxConsumer.NAME).bind(InteractionExchange.NAME, CommentPublished.ROUTING_KEY, CommentEdited.ROUTING_KEY, CommentDeleted.ROUTING_KEY, PostDeleted.ROUTING_KEY).declarables()`）
- Modify: adapter `build.gradle.kts`（`implementation("org.springframework.boot:spring-boot-starter-amqp")`、`implementation(project(":patra-api:patra-interaction:patra-interaction-api"))`）；boot `application.yml`（`patra.events.consumer.trusted-packages: [dev.patra.interaction.api.discussion.event, dev.patra.interaction.api.post.event]`）；boot IT 的 `application-test.yml` 关监听自启（`spring.rabbitmq.listener.simple.auto-startup: false`），只在 MQ 流程测试里打开
- Test: `InboxEventListenerTest`（映射：`SubjectContext.type` 字符串 → `SubjectType.parse`；`publishedAt` → `occurredAt`）、`boot/src/integrationTest/.../inbox/InboxEventFlowIT.java`（PG + `RabbitMQContainerInitializer`）

**Interfaces:**
- Consumes: PAP-141 的 `CommentPublished`、`CommentEdited`、`CommentDeleted`、`PostDeleted`、`InteractionExchange`；Task 7 的命令。

- [ ] **Step 1: 写失败的测试**
  - `InboxEventFlowIT`（`RabbitTemplate.convertAndSend("interaction.events", 路由键, record, m -> { m.getMessageProperties().setMessageId(eventId); return m; })`，`Awaitility` 等库里出现行）：`should_create_reply_notification_from_published_event`；`should_create_one_notification_when_delivered_twice`；`should_not_create_when_deleted_arrives_first`；`should_keep_newer_text_when_old_edit_arrives_late`；`should_remove_notifications_under_deleted_post`
- [ ] **Step 2–4: 失败 → 实现 → 通过**（`./gradlew :patra-api:patra-notification:patra-notification-boot:integrationTest --tests "*InboxEventFlowIT"`）
- [ ] **Step 5: 提交** `feat(notification): 消费 interaction 的评论与经验事件写信箱 (PAP-143)`；改了 `*.gradle.kts`，同一提交带上 `./gradlew dumpModuleGraph` 生成的 `patra-infra/cd/module-graph.json`

### Task 12: interaction 删除 `notification` 子域

**Files:**
- Delete: interaction 四层 `…/notification/**` 与对应测试；`boot/src/integrationTest/.../notification/**`；`infra/src/integrationTest/.../NotificationDeletedCleanupMigrationIT.java`
- Create: `patra-interaction-infra/src/main/resources/db/migration/notification/V20261031<时分秒>__drop_notification_schema.sql`
- Modify: `InteractionArchitectureTest`（子域清单、两条 `allowingSubscription`）、`InteractionMigrationIT`（白名单）、`ExperienceFlowIT`（删两例）、领域事件去掉 `body` 与旧注释、`README.md` 第 4.4 节与清单、infra `build.gradle.kts` 头注释、`HelpfulWriteFailedException` Javadoc

- [ ] **Step 1: 先改测试**：`InteractionMigrationIT` 白名单去掉 `notification`（跑 → 失败：schema 还在）
- [ ] **Step 2: 加 drop 迁移，删代码与测试**
- [ ] **Step 3: 运行** `./gradlew :patra-api:patra-interaction:check :patra-api:patra-interaction:patra-interaction-infra:integrationTest :patra-api:patra-interaction:patra-interaction-boot:integrationTest` → 绿；`grep -rn "domain.notification\|app.notification" patra-api/patra-interaction` 无结果
- [ ] **Step 4: 提交** `refactor(interaction): 删除提醒子域与它的表，提醒交给通知服务 (PAP-143)`

### Task 13: 文档、runbook 与全量门禁

**Files:**
- Modify: `patra-api/patra-notification/README.md`（子域表 inbox 一行补实现、存储、定时任务 `inboxRetentionJob`）
- Modify: `docs/patra/runbooks/v0.16-notification-go-live-runbook.md`（spec 第 14 节，合并后步骤）
- Modify: spec 状态行与实施记录

- [ ] **Step 1:** 写文档
- [ ] **Step 2:** `./gradlew check integrationTest --continue` 全绿（个别 IT 超时先单独重跑）
- [ ] **Step 3:** 提交 `docs(notification): 补信箱的说明与上线步骤 (PAP-143)`
