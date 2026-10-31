# 有帮助提醒 Implementation Plan

> **For agentic workers:** 本计划按 superpowers:executing-plans 在当前会话内联执行（`.claude/rules/superpowers.md`、用户 10/21 的决定：不用 subagent-driven）。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** notification 的 `inbox` 子域消费 `helpful.marked`，按「被点的内容 × 北京时间一天」写合并条目进 PAP-143 的信箱；列表带人数与经验正文摘录，未读数的 `helpful` 生效、不进铃铛。

**Architecture:** 领域聚合 `HelpfulNotification` 承载第 6 节全部合并规则（「人 → 第一次点的时刻」取最小、最近一位取最晚、经验正文时刻最晚者为准）；app 处理器用 `TransactionOperations` 包「去重 → 墓碑 → 锁行读 → absorb → 写回 → 发事件」；infra 用 JdbcClient 原生 SQL 写 `inbox.notification` 与附表 `inbox.helpful_actor`（级联外键）；adapter 在 PAP-143 的 `InboxEventListener` 加一个 `@RabbitHandler`。

**Tech Stack:** Java 25、Spring Boot 4.1.2、Spring AMQP、JdbcClient、Flyway、PostgreSQL 17、Testcontainers（PG + RabbitMQ）、WireMock、JUnit 5 + AssertJ + Mockito。

**Spec:** `docs/patra/specs/2026-10-31-notification-helpful-design.md`（下称 spec，节号指它）

## Global Constraints

- 根包 `dev.patra.notification`；新类只进 `inbox` 子域；不加跨子域依赖边；分包按 `.claude/rules/tech/package-layout.md`。
- 方法全部写 `///` Javadoc（Markdown）；不写全类名；record 参数 ≤ 4 用 `of()`，≥ 5 用 `@Builder`（不同时给 `of()`）；集合字段防御性拷贝（`Map.copyOf` 只用于对外返回，聚合内部用可变 `HashMap`）。
- 北京时间：`ZoneId.of("Asia/Shanghai")`；切天只用事件的 `markedAt`，不读「现在」。处理时刻（信箱行 `created_at`、评论快照 `updated_at`）取注入的 `Clock`，处理器里只取一次。
- 类型值 `COMMENT_HELPFUL` / `POST_HELPFUL`，类别 `HELPFUL`；队列名 `notification.inbox`；路由键取 `HelpfulMarked.ROUTING_KEY`（= `helpful.marked`）；信任包 `dev.patra.interaction.api.reaction.event`。
- 响应新字段名 `actorCount`（每行都有；有帮助 = 附表行数，其余 = 1）。
- 表只存用户 ID（R6）；迁移脚本放 `patra-notification-infra/src/main/resources/db/migration/inbox/`，版本号取写脚本时的时间戳 `yyyyMMddHHmmss`，晚于 PAP-143 的 inbox 脚本。
- infra 的加锁读与写 `@Transactional(propagation = MANDATORY)`；事务由 app 处理器开。
- 测试方法 snake_case；单元 `*Test`，集成 `*IT`；运行命令 `./gradlew :patra-api:patra-notification:patra-notification-<层>:test|integrationTest --tests "*X*"`。
- 只在任务 7 统一提交到 `claude/v016-pap144-7e8705`，只提交自己的路径（提交前核对 `git diff --cached`）；message 中文动词开头，结尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`；不 push、不开 PR。
- 改了 `*.gradle.kts` 就重跑 `./gradlew dumpModuleGraph` 并一起提交 `patra-infra/cd/module-graph.json`，汇报里提醒用户在 IDEA 点「加载 Gradle 更改」。

## Review Focus

1. **经验没有正文**（`targetPlainText` 为空）：条目 `post_plain_text` 为空、列表 `excerpt` 为空；之后更晚的点击带来正文（作者补了正文）要换上，更晚的点击带来空（作者删了正文）也要换成空 → 任务 1 `PostTextTest.null_text_from_later_mark_replaces_older_text`。
2. **不认识的 `targetType`**（例如将来 interaction 加了 `PUBLICATION`）：确认、打 WARN、不抛异常、去重记录照写 → 任务 3 `RecordHelpfulMarkedHandlerTest.unknown_target_type_is_acknowledged_and_skipped`。
3. **两个人在同一微秒点**：最近一位取用户 ID 大的，两种到达顺序结果相同 → 任务 1 `HelpfulNotificationTest.same_instant_ties_break_on_larger_user_id`。
4. **评论快照已存在且版本更新**：有帮助事件里的旧正文不能覆盖它 → 任务 6 `HelpfulInboxFlowIT.helpful_text_does_not_overwrite_newer_comment_snapshot`（发表 v1、编辑 v2，再来带 v1 正文的有帮助 → 列表摘录是 v2）。
5. **匿名经验作者给自己经验下别人的评论点**：列表里这一位不给名片、`actorAnonymous` 为真；随后别人再点、最近一位换人后恢复显示名片 → 任务 1 `HelpfulNotificationTest.anonymous_owner_is_hidden_only_while_latest` + 任务 5 `InboxQueryServiceTest.helpful_row_hides_anonymous_owner`。
6. **评论没有文字**（PAP-141 02:52：`targetPlainText` 对评论也可能为空）：不能用空正文补快照（`inbox.comment` 的约束会让整条消息失败、进死信），改为只查墓碑；条目照写、摘录为空 → 任务 3 `RecordHelpfulMarkedHandlerTest.comment_without_text_checks_tombstone_without_seeding` + 任务 6 `HelpfulInboxFlowIT.helpful_on_textless_comment_creates_entry_without_excerpt`。

---

### Task 0: 对齐基线、核对 PAP-143 的名字

**Files:** 只改本计划下面的「名字对照」表。

- [ ] **Step 1**：工作区干净时 `git -C <worktree> reset --hard <编排会话给的 SHA>`；确认 `patra-interaction-api` 里有 `dev.patra.interaction.api.reaction.event.HelpfulMarked`（带 `ROUTING_KEY`），`patra-notification` 里有 `InboxEventListener`、`inbox.notification` 的建表脚本。缺任何一个就停下问编排会话。
- [ ] **Step 1b**：按 PAP-141 的最终代码（`HelpfulMarked` record 与 `docs/patra/specs/2026-10-31-interaction-integration-events-design.md` 第 5 节）核对一次字段名、可空性与路由键；与 spec 第 4 节、本计划任务 3、4 不一致的地方先改 spec 与计划，影响前端的同步改共享笔记 `PAP-144-helpful-contract.md` 并告诉编排会话。
- [ ] **Step 2**：跑基线 `./gradlew :patra-api:patra-notification:patra-notification-boot:test :patra-api:patra-notification:patra-notification-infra:integrationTest`，预期全绿（红了先单独重跑，再判断是不是基线问题）。
- [ ] **Step 3**：读 PAP-143 的代码，复核下表（中列按它分支 10/31 已提交的代码预填），右列填最终名字；之后各任务一律用右列。

| 计划里的叫法 | 预填（10/31 PAP-143 分支） | 实际 |
|---|---|---|
| 提醒类型枚举 | `domain.inbox.model.enums.NotificationType`（构造参数是类别，`of(NotificationCategory)`） | `dev.patra.notification.domain.inbox.model.read.NotificationType`（读模型包） |
| 评论快照：插 `version = 0` 并判墓碑 | `CommentContentRepository.saveIfNewer(long commentId, long subjectId, int version, String plainText, Instant now)` → 墓碑返回 `false` | 同左；`isDeleted(long)` 同在 |
| 对象墓碑 | `SubjectTombstoneRepository.exists(long subjectId)` | 同左 |
| 删除 / 保留期 / 注销清理（级联测试用） | `NotificationRepository.deleteByComment`、`deleteBySubject`、`deleteOccurredBefore(Instant)`、`deleteAllOf(long)` | 同左（`NotificationRepositoryAdapter`，都是单条 `DELETE`） |
| 类型约束 | `ck_notification_type`、`ck_notification_category_type` | 同左；另有 `ck_notification_category`、`ck_notification_comment`（只管回复与 @，不动） |
| 读模型 / 读适配器 | `domain.inbox.model.read.InboxEntry`（`@Builder` record）、`infra.inbox.persistence.InboxReadAdapter` | 同左；列表 SQL 是常量 `PAGE`、映射是 `ENTRY` |
| 去重端口调用与队列名常量 | | `processed.markProcessed(InboxConsumer.NAME, eventId)`（`app.inbox.usecase.ingest.InboxConsumer`） |
| 「评论已发表」处理器的用例族包 `app.inbox.usecase.<族>` | | `app.inbox.usecase.ingest`；命令 `implements Command<IngestOutcome>`，处理器返回 `IngestOutcome`（`DUPLICATE` / `DISCARDED` / `APPLIED`） |
| `UnreadCountsChangedEvent` 的发布方式 | | `ApplicationEventPublisher.publishEvent(UnreadCountsChangedEvent.of(List<Long>))`（`NotificationWriter` 的写法；有帮助不走 `NotificationWriter`，它还会发 `NotificationsCreatedEvent`） |
| 查询服务 / 视图 / 响应 | | `InboxQueryService`、`NotificationView`（`@Builder`）、`NotificationResponse.from(view)` |
| 队列声明 bean 所在类、`trusted-packages` 所在 yml | | `boot/config/InboxQueueConfiguration.inboxQueue()`；`patra-notification-boot/src/main/resources/application.yml` 的 `patra.events.consumer.trusted-packages` |
| 信箱流程 IT 的基类 / 容器初始化器 / WireMock 桩 | | `support.NotificationFlowITSupport`（PG + RabbitMQ、`call` / `json` / `status`、`stubSubjectViews` / `stubPostOutlines` / `stubSummaries`，每例 TRUNCATE 全部业务表）；发消息 `rabbit.convertAndSend(InteractionExchange.NAME, 路由键, 事件)`；infra 侧 `InboxPersistenceITSupport` 的 TRUNCATE 要加 `inbox.helpful_actor`（外键引用 `inbox.notification`） |

---

### Task 1: 领域：北京日、合并条目

**Files:**
- Create（`patra-notification-domain/src/main/java/dev/patra/notification/domain/inbox/`）：`policy/HelpfulDay.java`、`model/enums/HelpfulTargetType.java`、`model/vo/HelpfulTarget.java`、`model/vo/HelpfulMark.java`、`model/vo/SubjectOwner.java`、`model/vo/PostText.java`、`model/enums/HelpfulChange.java`、`model/aggregate/HelpfulNotification.java`
- Modify：PAP-143 的提醒类型枚举加 `COMMENT_HELPFUL`、`POST_HELPFUL`（类别 `HELPFUL`）
- Test（同包镜像到 `src/test/java`）：`HelpfulDayTest`、`HelpfulTargetTypeTest`、`PostTextTest`、`HelpfulNotificationTest`

**Interfaces:**
- Produces:
  - `HelpfulDay.ZONE`（`Asia/Shanghai`）；`static LocalDate HelpfulDay.of(Instant markedAt)`
  - `enum HelpfulTargetType { COMMENT, POST }`：`static Optional<HelpfulTargetType> parse(String raw)`（只认大写全名）；`NotificationType notificationType()`（`COMMENT_HELPFUL` / `POST_HELPFUL`）
  - `record HelpfulTarget(HelpfulTargetType type, long targetId, long subjectId)`：`of(...)`；`Long commentId()`（`COMMENT` 时为 `targetId`，否则 `null`）
  - `record HelpfulMark(long actorId, Instant markedAt)`：`of(...)`
  - `record SubjectOwner(Long ownerId, boolean anonymous)`：`of(...)`；`boolean hides(long actorId)` = `anonymous && ownerId != null && ownerId == actorId`
  - `record PostText(String value, Instant markedAt)`：`of(...)`；`PostText advance(String value, Instant at)`（`at` 晚于 `markedAt` 返回新值，否则返回自己；`value` 可空）
  - `enum HelpfulChange { NONE, STORED_ONLY, VISIBLE }`
  - `final class HelpfulNotification`：
    - `static HelpfulNotification open(long recipientId, HelpfulTarget target, SubjectOwner owner, HelpfulMark mark, String plainText)`（未读；`POST` 时 `postText = PostText.of(plainText, mark.markedAt())`，`COMMENT` 时为空；`notificationId` 为空）
    - `static HelpfulNotification restore(long notificationId, long recipientId, HelpfulTarget target, SubjectOwner owner, LocalDate day, Map<Long, Instant> marks, Instant readAt, PostText postText)`
    - `HelpfulChange absorb(HelpfulMark mark, String plainText)`
    - 读：`Long notificationId()`、`long recipientId()`、`HelpfulTarget target()`、`LocalDate day()`、`int actorCount()`、`long latestActorId()`、`Instant occurredAt()`、`boolean latestActorAnonymous()`、`Instant firstMarkedAt(long actorId)`、`Instant readAt()`、`PostText postText()`（`COMMENT` 为空）

- [ ] **Step 1: 写失败的测试**
  - `HelpfulDayTest`：`2026-10-30T15:59:59.999999Z` → `2026-10-30`；`2026-10-30T16:00:00Z` → `2026-10-31`；`2026-10-31T15:59:59Z` 与 `2026-10-30T16:00:00Z` 同为 `2026-10-31`。
  - `HelpfulTargetTypeTest`：`parse("COMMENT")`、`parse("POST")` 有值；`parse("comment")`、`parse("PUBLICATION")`、`parse(null)` 为空；映射到两个类型值。
  - `PostTextTest.later_mark_replaces_text`、`earlier_mark_keeps_text`、`null_text_from_later_mark_replaces_older_text`（Review Focus 1）。
  - `HelpfulNotificationTest`（时刻用 `T0 = 2026-10-31T06:00:00Z` 加分钟；C = 11、D = 12、E = 13）：
    - `open_creates_unread_entry_with_one_actor`：`actorCount() == 1`，`latestActorId() == 11`，`occurredAt() == T0`，`readAt() == null`，`day() == 2026-10-31`。
    - `new_later_actor_becomes_latest_and_reunreads`：已读后 D 在 T0+5 → `VISIBLE`，`actorCount 2`，`latest 12`，`occurredAt T0+5`，`readAt null`。
    - `new_earlier_actor_adds_count_but_keeps_latest`：D 在 T0+5 建，C 在 T0 后到 → `VISIBLE`，`actorCount 2`，`latest 12`，`occurredAt T0+5`，`readAt null`。
    - `same_actor_later_mark_changes_nothing`：→ `NONE`，已读状态不变。
    - `same_actor_earlier_mark_moves_his_time_back`：C@T0+3 建、D@T0+2 并入、C@T0 后到 → `VISIBLE`，`latest 12`，`occurredAt T0+2`，`firstMarkedAt(11) == T0`，`readAt` 不变。
    - `same_instant_ties_break_on_larger_user_id`（Review Focus 3）：C、D 同在 T0，两种顺序 `latestActorId()` 都是 12。
    - `post_text_follows_latest_event_time`：`POST` 目标，C@T0 带「旧」开条目；D@T0+5 带「新」→ `VISIBLE`、正文「新」；C@T0+9（同一人更晚）带「更新」→ `VISIBLE`（人没变、正文值变了）、正文「更新」、最近一位仍是 D；C@T0+10 带「更新」→ `STORED_ONLY`（只推进正文时刻）；D@T0+1 带「很旧」→ 正文不变。
    - `anonymous_owner_is_hidden_only_while_latest`（Review Focus 5）：`SubjectOwner.of(11L, true)`，C(11) 点 → `latestActorAnonymous()` 为真；D 更晚点 → 为假。
    - `result_is_independent_of_arrival_order`：事件 `[C@T0+1, C@T0+3（取消后再点）, D@T0+2, E@T0+4, D@T0+2（重复）]`，`POST` 目标、每条带不同正文；对全部 120 种排列，`open` 第一条再依次 `absorb`，断言 `actorCount`、`latestActorId`、`occurredAt`、`firstMarkedAt` 三人、`postText` 全部与按时间顺序处理的结果相同。
- [ ] **Step 2: 跑确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-domain:test --tests "*Helpful*" --tests "*PostText*"`，预期编译失败。
- [ ] **Step 3: 实现**。`absorb` 的判定：

```
prev = marks.get(actor)
changedPeople = prev == null || at.isBefore(prev)
if (prev == null) readAt = null
if (changedPeople) marks.put(actor, prev == null ? at : min(prev, at))
newText = postText == null ? null : postText.advance(plainText, at)
textStored = newText != postText；textVisible = textStored && !Objects.equals(newText.value(), postText.value())
返回：changedPeople || textVisible → VISIBLE；textStored → STORED_ONLY；否则 NONE
```

最近一位 = `marks` 里按（时刻, 用户 ID）取最大；`latestActorAnonymous()` = `owner.hides(latestActorId())`。
- [ ] **Step 4: 跑确认通过**：同 Step 2 的命令，预期 PASS；再跑 `./gradlew :patra-api:patra-notification:patra-notification-domain:check`（domain 纯净）。

---

### Task 2: 存储：迁移与合并条目仓储

**Files:**
- Create：`patra-notification-infra/src/main/resources/db/migration/inbox/V{时间戳}__add_helpful_notification.sql`
- Create：`patra-notification-domain/.../domain/inbox/port/repository/HelpfulNotificationRepository.java`
- Create：`patra-notification-infra/src/main/java/dev/patra/notification/infra/inbox/persistence/HelpfulNotificationRepositoryAdapter.java`
- Test：`patra-notification-infra/src/integrationTest/java/dev/patra/notification/infra/inbox/persistence/HelpfulNotificationRepositoryAdapterIT.java`；PAP-143 的迁移 IT（若断言了列 / 表清单）同步改

**Interfaces:**
- Consumes：任务 1 的 `HelpfulNotification`、`HelpfulTarget`、`HelpfulMark`、`SubjectOwner`、`PostText`。
- Produces（`HelpfulNotificationRepository`）：
  - `Optional<HelpfulNotification> findForUpdate(long recipientId, HelpfulTarget target, LocalDate day, SubjectOwner owner)`：锁信箱行（`FOR UPDATE`）并读附表；按 `comment_id = ?` 或 `comment_id IS NULL` 分两种 SQL，不用 `IS NOT DISTINCT FROM`
  - `long insert(HelpfulNotification notification, Instant createdAt)`：`SnowflakeIdGenerator.getId()` 生成并返回新条目 ID（聚合不回填，处理器之后用不到它）；写信箱行（`category = 'HELPFUL'`、`type`、`helpful_day`、`created_at`、派生列）与附表一行
  - `void update(HelpfulNotification notification, HelpfulMark mark)`：附表 upsert 这个人（`ON CONFLICT (recipient_id, notification_id, actor_id) DO UPDATE SET first_marked_at = LEAST(…)`），信箱行更新 `actor_id`、`actor_anonymous`、`occurred_at`、`read_at`、`post_plain_text`、`post_plain_text_at`

- [ ] **Step 1: 写迁移脚本**：按 spec 7.1 的三列、约束（`ck_notification_type`、`ck_notification_category_type` 删了重建加有帮助；形状三条；类型—评论两条）、`uk_notification_helpful … NULLS NOT DISTINCT WHERE category = 'HELPFUL'`、附表 `inbox.helpful_actor`（主键、`ON DELETE CASCADE` 外键）；表与列加英文 `COMMENT ON`（同 PAP-143 脚本风格）。
- [ ] **Step 2: 写失败的集成测试** `HelpfulNotificationRepositoryAdapterIT`（PAP-143 的 infra IT 基座，真 PG）：
  - `insert_then_find_restores_entry`：插一条 `POST` 条目，`findForUpdate` 读回人数、最近一位、时间、未读、正文与时刻、`day`。
  - `update_upserts_actor_and_keeps_earliest_time`：同一人先 T0+3 后 T0 → 附表 `first_marked_at == T0`。
  - `merge_key_is_unique_even_without_comment_id`：同收件人、同经验、同一天直接 SQL 插第二行（两行 `comment_id` 都为空、`notification_id` 不同，避免撞主键）→ 抛 `DuplicateKeyException`（断言约束名 `uk_notification_helpful`）。
  - `different_days_and_targets_are_separate_rows`：同经验不同 `day`、同 `day` 不同评论各一行，`findForUpdate` 各自命中。
  - `shape_checks_reject_bad_rows`：`HELPFUL` 行缺 `helpful_day`、`POST_HELPFUL` 行带 `comment_id`、`COMMENT_REPLY` 行带 `post_plain_text` 各被拒（断言约束名）。
  - `deleting_parent_rows_cascades_to_actors`：分别调用 PAP-143 仓储里**实际的** `deleteByComment`、`deleteBySubject`、`deleteAllOf`（注销清理）、`deleteOccurredBefore`（保留期）之后（不手写 `DELETE`，它们可能用子查询或 `USING`），`inbox.helpful_actor` 计数为 0。
- [ ] **Step 3: 跑确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-infra:integrationTest --tests "*HelpfulNotificationRepositoryAdapterIT*"`。
- [ ] **Step 4: 实现端口与适配器**（JdbcClient；读附表一次取全该条目的行组成 `Map`）。
- [ ] **Step 5: 跑确认通过**：同 Step 3；再跑整个 infra `integrationTest`（PAP-143 的迁移 IT、仓储 IT 不受影响）。

---

### Task 3: 应用：处理 `helpful.marked`

**Files:**
- Create（`patra-notification-app/.../app/inbox/usecase/<族>/`）：`RecordHelpfulMarkedCommand.java`、`RecordHelpfulMarkedHandler.java`
- Test：同包 `RecordHelpfulMarkedHandlerTest.java`

**Interfaces:**
- Consumes：任务 1、2；PAP-143 的去重端口、评论快照仓储、对象墓碑仓储、`UnreadCountsChangedEvent`。
- Produces：
  - `@Builder record RecordHelpfulMarkedCommand(UUID eventId, String targetType, long targetId, long targetAuthorId, long actorId, Instant markedAt, long subjectId, Long subjectOwnerId, boolean subjectOwnerAnonymous, String targetPlainText) implements Command<Void>`（`Command` 的实际接口名照 `patra-tool-cqrs`）
  - `RecordHelpfulMarkedHandler implements CommandHandler<RecordHelpfulMarkedCommand, Void>`：依赖 `TransactionOperations`、`Clock`，流程按 spec 8.2 第 1～7 步

- [ ] **Step 1: 写失败的测试**（mock 端口，`TransactionOperations` 用直接执行回调的桩，`Clock` 固定在 `NOW = 2026-10-31T08:00:00Z`）：
  - `duplicate_event_does_nothing`：`markProcessed` 为假 → 不查墓碑、不读写条目。
  - `unknown_target_type_is_acknowledged_and_skipped`（Review Focus 2）：`targetType = "PUBLICATION"` → 不抛、不读写条目、`markProcessed` 被调过。
  - `post_tombstone_drops_mark`：对象墓碑存在 → 不碰评论快照、不读写条目（点经验与点评论各一例）。
  - `comment_snapshot_is_seeded_at_version_zero`：点评论 → `saveIfNewer(commentId, subjectId, 0, targetPlainText, NOW)` 被调一次；点经验 → 不调。
  - `comment_tombstone_drops_mark`：`saveIfNewer` 返回假 → 不读写条目。
  - `comment_without_text_checks_tombstone_without_seeding`（Review Focus 6）：点评论、`targetPlainText` 为空 → 不调 `saveIfNewer`；`isDeleted` 为真 → 不读写条目；为假 → 照常 `insert`。
  - `first_mark_of_the_day_opens_entry_and_publishes_counts`：`findForUpdate` 为空 → `insert(条目, NOW)` 一次；发 `UnreadCountsChangedEvent(Set.of(targetAuthorId))`；不发 `NotificationsCreatedEvent`。
  - `mark_merges_into_existing_entry_of_same_beijing_day`：`findForUpdate` 收到的 `day` = `HelpfulDay.of(markedAt)`（用 `2026-10-30T16:30:00Z` → `2026-10-31`）；返回已有条目 → `update(条目, mark)` 一次、发事件。
  - `unchanged_entry_is_not_written`：`absorb` 为 `NONE`（同一人更晚再点）→ 不 `update`、不发事件。
  - `stored_only_change_writes_without_event`：`POST` 同一人更晚再点、正文相同 → `update` 一次、不发事件。
  - `owner_hides_anonymous_author`：`subjectOwnerId == actorId && subjectOwnerAnonymous` → 写入的条目 `latestActorAnonymous()` 为真。
- [ ] **Step 2: 跑确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-app:test --tests "*RecordHelpfulMarkedHandlerTest*"`。
- [ ] **Step 3: 实现**：不认识的类型打 WARN（带事件 ID 与原值）。
- [ ] **Step 4: 跑确认通过**：同 Step 2。

---

### Task 4: 接线：监听、绑定、信任包

**Files:**
- Modify：`patra-notification-adapter/.../adapter/inbox/mq/InboxEventListener.java`（加 `@RabbitHandler void on(HelpfulMarked event)`）
- Modify：PAP-143 的队列声明（加 `HelpfulMarked.ROUTING_KEY`）、yml 的 `patra.events.consumer.trusted-packages`（加 `dev.patra.interaction.api.reaction.event`）
- Test：PAP-143 的监听映射测试与队列声明测试里各加一例（没有就新建 `InboxEventListenerTest`、`<队列声明类>Test`）

**Interfaces:**
- Consumes：任务 3 的命令；`HelpfulMarked` 的字段（`eventId`、`targetType`、`targetId`、`targetAuthorId`、`userId`、`markedAt`、`subject.subjectId/ownerId/ownerAnonymous`、`targetPlainText`）。

- [ ] **Step 1: 写失败的测试**：
  - `should_map_helpful_marked_to_command`：命令总线 mock 捕获命令，逐字段断言（`actorId` 取自 `userId`，`subjectId` 等取自 `subject`）。
  - `inbox_queue_binds_five_routing_keys`：队列声明的绑定恰为 `comment.published`、`comment.edited`、`comment.deleted`、`post.deleted`、`helpful.marked`，没有 `helpful.unmarked`。
  - `trusted_packages_include_reaction_events`：读 boot 的配置（PAP-143 若已有 `ApplicationConfigTest` 类的断言就加一行）。
- [ ] **Step 2: 跑确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-adapter:test :patra-api:patra-notification:patra-notification-boot:test`。
- [ ] **Step 3: 实现**。
- [ ] **Step 4: 跑确认通过**：同 Step 2（含 `NotificationArchitectureTest`）。

---

### Task 5: 读：人数与经验正文摘录

**Files:**
- Modify：`InboxEntry`（`@Builder` record，加 `int actorCount`，由读适配器按下面的 SQL 填）、`InboxReadAdapter`（列表 SQL）、查询服务的视图、`NotificationResponse`（加 `actorCount`）
- Test：`InboxReadAdapterIT`、`InboxQueryServiceTest`、控制器切片测试各加用例

**Interfaces:**
- Produces：`InboxEntry.actorCount()`；视图与响应字段 `actorCount`（int，非空）。
- 列表 SQL 两处：摘录列 `COALESCE(c.plain_text, n.post_plain_text)`；人数 `CASE WHEN n.category = 'HELPFUL' THEN (SELECT count(*) FROM inbox.helpful_actor a WHERE a.recipient_id = n.recipient_id AND a.notification_id = n.notification_id) ELSE 1 END`。

- [ ] **Step 1: 写失败的测试**：
  - `InboxReadAdapterIT.helpful_rows_carry_actor_count_and_post_text`：一条 `POST_HELPFUL`（3 人、正文「经验正文」）、一条 `COMMENT_HELPFUL`（评论快照「评论正文」）→ `actorCount` 3 / n、`plainText` 分别为两段正文、`POST_HELPFUL` 的 `commentId` 为空；`tab=helpful` 只出这两条、按 `occurredAt` 倒序。
  - `InboxReadAdapterIT.reply_rows_report_one_actor`。
  - `InboxQueryServiceTest.helpful_row_hides_anonymous_owner`（Review Focus 5）：`actorAnonymous` 为真 → 视图 `actor` 为空、不去 identity 取他的名片。
  - `InboxQueryServiceTest.post_helpful_without_text_has_no_excerpt`：`plainText` 为空 → `excerpt` 为空，`subject.experience` 照常给出。
  - 控制器切片：响应 JSON 每行有 `actorCount`，值为数字。
- [ ] **Step 2: 跑确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-infra:integrationTest --tests "*InboxReadAdapterIT*" :patra-api:patra-notification:patra-notification-app:test :patra-api:patra-notification:patra-notification-adapter:test`。
- [ ] **Step 3: 实现**。
- [ ] **Step 4: 跑确认通过**：同 Step 2。

---

### Task 6: 端到端：真 RabbitMQ + 真 PG + REST

**Files:**
- Create：`patra-notification-boot/src/integrationTest/java/dev/patra/notification/inbox/HelpfulInboxFlowIT.java`（用 PAP-143 信箱流程 IT 的基座、容器初始化器与 WireMock 桩；向 `interaction.events` 发 `HelpfulMarked` 的 JSON，`__TypeId__` 设为它的全类名、`messageId` 设为 `eventId`）

- [ ] **Step 1: 写失败的测试**（A = 作者 101，C = 102，D = 103，E = 104；以 A 登录调接口；等写入用 Awaitility）：
  - `same_day_marks_merge_into_one_entry_named_after_latest`（AC1）：C@T0、D@T0+5（同一北京日，点经验）→ `tab=helpful` 一条，`type` `POST_HELPFUL`，`actorCount` 2，`actor.userId` = D，`read` 假，`occurredAt` = T0+5。
  - `new_marker_after_read_moves_entry_to_top`（AC1）：先建经验 P1 的条目（T0），再建经验 P2 的条目（T0+10，雪花 ID 更大）；A 读 P1 后 E 在 T0+20 点 P1 → P1 变未读、排在 P2 前。
  - `marks_across_beijing_midnight_create_two_entries`（AC2）：`2026-10-30T15:59:59Z` 与 `2026-10-30T16:00:01Z` → 两条。
  - `duplicate_delivery_and_remark_do_not_add_people`（AC3）：同一条消息发两次 + C 用新 `eventId` 再点 → `actorCount` 1；`processed_event` 有两行（两个事件 ID）。
  - `bell_total_excludes_helpful_unread`（AC4）：只有两条有帮助未读 → `unread-count` 为 `{"total":0,"replies":0,"mentions":0,"helpful":2}`。
  - `deleting_marked_comment_removes_entry`：点评论 → 发 `comment.deleted` → 列表为空、`helpful` 为 0、`inbox.helpful_actor` 为 0 行。
  - `mark_after_post_deleted_is_dropped`：先发 `post.deleted` 再发点该经验的 `helpful.marked` → 没有条目、`notification.inbox.dlq` 为空。
  - `helpful_text_does_not_overwrite_newer_comment_snapshot`（Review Focus 4）：发 `comment.published` v1「原文」、`comment.edited` v2「改后」，再发带「原文」的 `helpful.marked` → `tab=helpful` 的摘录是「改后」。
  - `helpful_on_unseen_comment_shows_event_text`：没发过任何评论事件，直接点评论（正文「上线前的评论」）→ 摘录是「上线前的评论」。
  - `helpful_on_textless_comment_creates_entry_without_excerpt`（Review Focus 6）：没发过评论事件，点评论且 `targetPlainText` 为空 → 有一条 `COMMENT_HELPFUL`、`excerpt` 为空、`notification.inbox.dlq` 为空。
- [ ] **Step 2: 跑确认失败**：`./gradlew :patra-api:patra-notification:patra-notification-boot:integrationTest --tests "*HelpfulInboxFlowIT*"`。
- [ ] **Step 3: 修到通过**（这一步只应暴露接线问题；改到前面任务的代码时，先在对应任务的测试里补复现用例）。
- [ ] **Step 4: 跑确认通过**：同 Step 2。

---

### Task 7: 文档、全量验证、独立评审、提交

**Files:**
- Modify：`patra-api/patra-notification/README.md`（inbox 子域一行补「有帮助按内容 × 北京日合并、附表 `helpful_actor`」）
- Modify：`docs/patra/runbooks/v0.16-notification-go-live-runbook.md`（按 spec 第 11 节追加到「合并后：其他 Issue 的步骤」）
- Modify：spec 状态行；偏离写进 spec 新增的「实施中的偏离」一节

- [ ] **Step 1**：改文档。
- [ ] **Step 2**：`./gradlew spotlessApply` 后跑全量 `./gradlew check integrationTest --continue`，预期 BUILD SUCCESSFUL；个别 IT 超时或缓存锁先单独重跑那个任务再判断。
- [ ] **Step 3**：派独立评审子代理（`model: "opus"`），只给 spec 路径与 `git diff f46edcdc0..`（或基线 SHA）涉及的文件清单，不给我的结论；重要问题先写复现测试再修，回到 Step 2。
- [ ] **Step 4**：`git add` 只加本 Issue 的路径，`git diff --cached --stat` 核对后提交：

```bash
git commit -m "feat(notification): 新增有帮助提醒，按内容与北京时间一天合并 (PAP-144)" -m "…正文…" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 5**：给编排会话发验收汇报：提交 SHA、AC 逐条对应的测试、全量结果、评审发现与处理、留下的小问题、上线步骤位置。
