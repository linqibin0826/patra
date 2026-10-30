# 讨论串统计与「最有帮助」排序 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（本仓库约定内联执行，不用 subagent-driven）。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讨论子域维护楼统计与对象评论总数（同事务 ±1），评论 / 楼 / 经验的响应带有帮助数、「我点过没有」、回复数与最后回复时间，讨论列表与期刊页经验列表支持「最有帮助」排序。

**Architecture:** 两张新统计表归 discussion，由发表、删除两个 Handler 在原事务里维护；读适配器用 SQL 关联 `reaction.target_stats` / `reaction.helpful`，经验经 `subject.registry` 两跳关联 `discussion.subject_stats`。「最有帮助」用底座值对象 `RankCursor` 做三元组游标。

**Tech Stack:** Java 25 / Spring Boot 4 / JdbcClient 原生 SQL / PostgreSQL 17 / Flyway / JUnit 5 + AssertJ + Mockito / Testcontainers

**Spec:** `docs/patra/specs/2026-10-29-discussion-stats-design.md`

## Global Constraints

- 只改 `patra-api/patra-interaction`；ArchUnit 不加子域、不加边（跨 schema 只在读适配器 SQL 里）。
- 「未删除」= `state = 'ACTIVE'`；没有统计行 = 0 / `NULL`，读方一律 `LEFT JOIN … COALESCE`。
- 类型码用字符串 `'COMMENT'` / `'POST'`，不 import reaction 的类；经验对象：`subject.registry.target_type = 3 AND source_id = post_id`。
- 锁顺序：配额锁 → 楼锁 → 评论行 → `thread_stats` 行 → `subject_stats` 行 → 发事件。
- `helpful`（我点过没有）没有会话为 `null`；占位评论 `helpfulCount = 0`、`helpful = null`。
- 「最有帮助」：`ORDER BY 有帮助数 DESC, created_at DESC, id DESC`；游标对外 `{有帮助数}.{自 EPOCH 微秒}.{ID}`。
- 方法一律 `///` Markdown JavaDoc；record ≤4 参数用 `of()`、≥5 用 `@Builder`；集合字段 `copyOf`；测试方法 snake_case `should_…`。
- infra 写方法 / 加锁方法 `@Transactional(propagation = MANDATORY)`；app 层用 `TransactionOperations`。
- **不提交**：本 Issue 完成后停在本地等用户验收，所有任务都不 commit。
- 每个任务结束跑该模块的 `spotlessApply` 再跑测试（spotless 先于 check）。

## Review Focus

1. 楼里只剩一条回复时删掉它 → 统计变成 `(0, NULL)`，不能撞 `ck_thread_stats_last`（Task 2 `should_reset_to_zero_when_last_reply_removed`）。
2. 后拿到楼锁、时间更早的回复不能把 `last_reply_at` 往回拨（Task 2 `should_keep_latest_time_when_older_reply_recorded`）。
3. 有回复的根评论删成占位、再删光回复级联去掉根 → 对象总数只减一次、楼统计行被删（Task 7 `should_cascade_root_removal_without_double_decrement`）。
4. 拿 `asc` 的两段式游标请求 `sort=helpful`（或反过来）→ 422「游标不合法」，不是 500（Task 6 `should_reject_cursor_of_other_sort`，Task 9 同理）。
5. 匿名访客读期刊页经验列表 / 讨论列表 → `helpful` 为 `null`，计数照常；期刊未登记 → 空页、`total = 0`（Task 7、Task 9 的流程测试）。

运行命令前缀：`G=/Users/linqibin/Projects/Products/patra/.claude/worktrees/v014-catalog-e0526e/gradlew`，项目路径 `:patra-api:patra-interaction:patra-interaction-<层>`，例 `$G -p /Users/linqibin/Projects/Products/patra/.claude/worktrees/v014-catalog-e0526e :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*DiscussionStatsMigrationIT*"`。

---

### Task 1: 迁移与回填

**Files:**
- Create: `patra-interaction-infra/src/main/resources/db/migration/discussion/V20261029210000__create_discussion_stats.sql`
- Create: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/DiscussionStatsMigrationIT.java`
- Modify: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/discussion/persistence/DiscussionPersistenceITSupport.java`（`TRUNCATE` 加两张统计表）

**Interfaces:**
- Produces: 表 `discussion.thread_stats(root_id PK, reply_count INTEGER, last_reply_at timestamptz(6) NULL)`、`discussion.subject_stats(subject_id PK, comment_count INTEGER)`，约束名 `ck_thread_stats_count`、`ck_thread_stats_last`、`ck_subject_stats_count`。

- [ ] **Step 1: 写失败的迁移 IT**（照 `NotificationDeletedCleanupMigrationIT`：独立库，`BEFORE = "20261029200200"`，迁到 BEFORE → 造数 → 迁到最新）。造数：对象 11 下根 1（ACTIVE）有回复 2（ACTIVE, T0+1s）、3（DELETED 占位, T0+2s）、4（ACTIVE, 回复 2, T0+3s）；根 5（DELETED 占位）有回复 6（ACTIVE, T0+4s）；对象 12 下根 7（ACTIVE）无回复。测试：
  - `should_backfill_thread_stats_from_active_replies`：`thread_stats` = {1: (2, T0+3s), 5: (1, T0+4s)}，没有 7 的行。
  - `should_backfill_subject_stats_from_active_comments`：{11: 4, 12: 1}。
  - `should_match_recount_after_backfill`：两张表与 spec 第 4 节的 `GROUP BY` 查询结果逐行相等。
  - `should_reject_inconsistent_thread_stats`：插 `(9, 0, now())` 与 `(9, 1, NULL)` 都抛 `DataIntegrityViolationException`，消息含 `ck_thread_stats_last`；`(9, -1, NULL)` 含 `ck_thread_stats_count`；`subject_stats` 负数含 `ck_subject_stats_count`。
- [ ] **Step 2: 跑，确认因表不存在失败。**
- [ ] **Step 3: 写迁移**：建表（列注释英文，照现有迁移风格）+ spec 第 4 节两条幂等 upsert 原文。
- [ ] **Step 4: 跑该 IT 与 `InteractionMigrationIT`，全绿。**

### Task 2: 统计写方法

**Files:**
- Modify: `patra-interaction-domain/.../domain/discussion/port/repository/CommentRepository.java`
- Modify: `patra-interaction-infra/.../infra/discussion/persistence/CommentRepositoryAdapter.java`
- Test: `patra-interaction-infra/src/integrationTest/.../discussion/persistence/CommentRepositoryAdapterIT.java`

**Interfaces:**
- Produces（`CommentRepository`，都要求在事务里）:
  - `void recordReply(long rootId, Instant createdAt)` — upsert `reply_count + 1`、`last_reply_at = GREATEST(last_reply_at, :createdAt)`
  - `void removeReply(long rootId)` — `SELECT … FOR UPDATE` 锁行，再一条 `UPDATE` 同时设 `reply_count = reply_count - 1` 与 `last_reply_at = (SELECT max(created_at) FROM discussion.comment WHERE root_id = :rootId AND parent_id IS NOT NULL AND state = 'ACTIVE')`
  - `void dropThreadStats(long rootId)`
  - `void incrementCommentCount(SubjectId subjectId)` — upsert `+1`
  - `void decrementCommentCount(SubjectId subjectId)` — `UPDATE … - 1`

- [ ] **Step 1: 写失败的 IT**（统计断言直接 `jdbcTemplate` 读两张表）：
  - `should_record_replies_and_keep_latest_time`：两次 `recordReply(root, T0+1s)`、`recordReply(root, T0+2s)` → `(2, T0+2s)`。
  - `should_keep_latest_time_when_older_reply_recorded`：先 T0+2s 再 T0+1s → `last_reply_at = T0+2s`。
  - `should_fall_back_to_previous_reply_when_latest_removed`：插回复 a(T0+1s)、b(T0+2s) 并 `recordReply` 两次；`markDeleted(b)` 后 `removeReply(root)` → `(1, T0+1s)`。
  - `should_reset_to_zero_when_last_reply_removed`：一条回复，`delete` 后 `removeReply` → `(0, NULL)`，不抛异常。
  - `should_drop_thread_stats`：`dropThreadStats` 后无行。
  - `should_count_comments_per_subject`：S1 `increment` 两次、`decrement` 一次 → 1；S2 无行。
  - 在已有的 `should_reject_lock_outside_transaction` 里补：`removeReply(1L)` 在事务外抛 `IllegalTransactionStateException`。
- [ ] **Step 2: 跑，确认编译失败（方法不存在）。**
- [ ] **Step 3: 实现五个方法**（`removeReply` 加 `@Transactional(MANDATORY)`；其余写方法照现有风格不加注解，由调用方事务包住）。
- [ ] **Step 4: 跑 `CommentRepositoryAdapterIT`，全绿。**

### Task 3: 发表与删除维护统计

**Files:**
- Modify: `patra-interaction-app/.../app/discussion/usecase/comment/PublishCommentHandler.java`
- Modify: `patra-interaction-app/.../app/discussion/usecase/comment/DeleteCommentHandler.java`
- Test: `patra-interaction-app/src/test/.../discussion/usecase/comment/PublishCommentHandlerTest.java`、`DeleteCommentHandlerTest.java`

**Interfaces:**
- Consumes: Task 2 的五个方法。

- [ ] **Step 1: 写失败的单测**（照各自现有测试的 mock 与 `RecordingTransactions` 写法，用 `InOrder` 断言顺序）：
  - Publish `should_count_root_comment_before_publishing_event`：根评论 → `insert` → `incrementCommentCount(subjectId)` → `publishEvent`；不调 `recordReply`。
  - Publish `should_count_reply_in_thread_before_publishing_event`：回复 → `insert` → `recordReply(rootId, now)` → `incrementCommentCount` → `publishEvent`，全在事务内。
  - Delete `should_uncount_reply_turned_placeholder`：回复有子回复 → `markDeleted` → `removeReply(rootId)` → `decrementCommentCount` → `publishEvent`。
  - Delete `should_uncount_root_turned_placeholder`：根有回复 → `markDeleted` → `decrementCommentCount`；不调 `removeReply`。
  - Delete `should_drop_thread_stats_when_root_removed`：没回复的根 → `delete` → `decrementCommentCount` → `publishEvent` → `dropThreadStats(rootId)`。
  - Delete `should_drop_thread_stats_when_cascade_reaches_root`：删一条回复、父是占位根且再无回复 → `removeReply`、`decrementCommentCount` 各一次，级联删根后 `dropThreadStats(rootId)`。
  - Delete `should_not_touch_stats_when_already_placeholder`：`NONE` 时五个统计方法都不调。
- [ ] **Step 2: 跑，确认失败。**
- [ ] **Step 3: 实现**：Publish 在 `comments.insert` 之后、`events.publishEvent` 之前，`locked != null` 时 `recordReply(locked.getRootId(), now)`，总是 `incrementCommentCount(subjectId)`。Delete 加私有方法 `uncount(CommentAggregate comment)`（回复则 `removeReply(rootId)`，再 `decrementCommentCount`），在 `PLACEHOLDER` / `REMOVE` 两支改评论行之后、发事件之前调用；`removeOrphanedAncestors` 返回的 ID 含 `rootId` 时调 `dropThreadStats(rootId)`。类注释补一句统计维护。
- [ ] **Step 4: 跑 app 模块 `test`，全绿。**

### Task 4: `RankCursor` 与 `ThreadSort.HELPFUL`

**Files:**
- Create: `patra-interaction-domain/.../domain/subject/model/vo/RankCursor.java`
- Modify: `patra-interaction-domain/.../domain/discussion/model/enums/ThreadSort.java`
- Test: `patra-interaction-domain/src/test/.../subject/model/vo/RankCursorTest.java`、`.../discussion/model/enums/ThreadSortTest.java`

**Interfaces:**
- Produces:
  - `record RankCursor(int helpfulCount, Instant createdAt, long id)`，`static RankCursor of(int, Instant, long)`，`static Optional<RankCursor> parse(String raw)`（只认非空原文；格式 `([0-9]{1,10})\.([0-9]{1,19})\.([1-9][0-9]{0,18})`，数字溢出或不匹配返回空），`String format()`
  - `ThreadSort.HELPFUL`，`fromParam("helpful")`

- [ ] **Step 1: 写失败的测试**：`should_round_trip_with_microsecond_precision`（`of(3, T0.plusNanos(1_000), 42).format()` 解析回来相等）；`should_return_empty_for_malformed`（`"1.2"`、`"a.b.c"`、`"1.2.0"`、`"99999999999.1.1"` 都为空）；`ThreadSortTest` 补 `"helpful"` → `HELPFUL`。
- [ ] **Step 2: 跑 domain `test`，确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑 domain `check`（含六边形纯净性），全绿。**

### Task 5: 讨论读端

**Files:**
- Create: `patra-interaction-domain/.../domain/discussion/model/read/ThreadStats.java`
- Modify: `CommentRecord.java`（加 `int helpfulCount`）、`DiscussionReadPort.java`（类注释改为「查 discussion schema，并关联 reaction 的有帮助统计」）
- Modify: `patra-interaction-infra/.../discussion/persistence/CommentRows.java`、`DiscussionReadAdapter.java`
- Test: `DiscussionReadAdapterIT.java`

**Interfaces:**
- Produces（`DiscussionReadPort`）:
  - `List<CommentRecord> findRootsByHelpful(SubjectId subjectId, RankCursor after, int limit)`
  - `Set<Long> findHelpfulMarked(long viewerId, Collection<Long> commentIds)`（空集合直接返回 `Set.of()`）
  - `Map<Long, ThreadStats> findThreadStats(Collection<Long> rootIds)`
  - `int countComments(SubjectId subjectId)`
  - `record ThreadStats(int replyCount, Instant lastReplyAt)` + `of()`
  - `CommentRecord.helpfulCount()`（`RECORD_SELECT` 里 `LEFT JOIN reaction.target_stats hs … COALESCE(hs.helpful_count, 0) AS helpful_count`）

- [ ] **Step 1: 写失败的 IT**（`DiscussionPersistenceITSupport` 的 `TRUNCATE` 补 `reaction.target_stats, reaction.helpful`；加 `protected void helpful(long commentId, int count)` 直接插 `target_stats`、`protected void marked(long userId, long commentId)` 直接插 `reaction.helpful`）：
  - `should_carry_helpful_count`：一条有 3、一条没统计行 → 3 / 0。
  - `should_order_roots_by_helpful_then_newest`：r1(2, T0)、r2(2, T0+1s)、r3(0, T0+2s)、r4(5, T0) → `[r4, r2, r1, r3]`；`size = 1` 逐页用 `RankCursor.of(helpfulCount, createdAt, commentId)` 翻完，拼起来相同、无重复。
  - `should_find_helpful_marked_by_viewer`：用户 7 点过 c1、用户 8 点过 c2 → `findHelpfulMarked(7, [c1, c2, c3]) = {c1}`。
  - `should_find_thread_stats_and_comment_count`：直接插两张统计表，`findThreadStats` 只返回有行的、`countComments` 没行为 0。
- [ ] **Step 2: 跑，确认失败。**
- [ ] **Step 3: 实现**（`findRootsByHelpful` 条件 `(COALESCE(hs.helpful_count, 0), c.created_at, c.comment_id) < (:helpful, :at, :id)`；`RECORD_SELECT` 的 JOIN 带上 `hs` 别名供排序复用）。
- [ ] **Step 4: 跑 `DiscussionReadAdapterIT` 与整个 infra `integrationTest`，全绿。**

### Task 6: 讨论的视图与查询服务

**Files:**
- Modify: `app/discussion/usecase/query/{CommentView, ThreadView, ThreadPageView, DiscussionQueryService}.java`、`app/discussion/service/CommentViews.java`、`app/discussion/usecase/comment/{PublishCommentHandler, EditCommentHandler}.java`
- Test: `CommentViewsTest`、`DiscussionQueryServiceTest`

**Interfaces:**
- Consumes: Task 4、Task 5。
- Produces:
  - `CommentView` 加 `int helpfulCount`、`Boolean helpful`
  - `CommentViews.assemble(List<CommentRecord> comments, SubjectView subject, Long viewerId, Map<Long, UserCard> knownCards, Set<Long> helpfulMarked)`：`viewerId == null` → `helpful = null`；否则 `helpfulMarked.contains(id)`；占位 → `0` / `null`。发表、编辑传 `Set.of()`。
  - `ThreadView.of(CommentView root, List<CommentView> replies, boolean hasMoreReplies, String repliesCursor, int replyCount, Instant lastReplyAt)`（6 个参数，改 `@Builder`，删 `of`）
  - `ThreadPageView.of(List<ThreadView> items, String nextCursor, int total)`
  - `DiscussionQueryService.threads` 签名不变；`sort=helpful` 走 `findRootsByHelpful` + `RankCursor`，下一页游标 `RankCursor.of(last.helpfulCount(), last.createdAt(), last.commentId()).format()`；`thread()` 带楼统计；对象未登记 `total = 0`。

- [ ] **Step 1: 写失败的单测**：
  - `CommentViewsTest`：`should_leave_helpful_null_for_anonymous_viewer`、`should_mark_helpful_from_set`、`should_zero_helpful_on_placeholder`。
  - `DiscussionQueryServiceTest`：`should_page_by_helpful_with_rank_cursor`（mock `findRootsByHelpful`，第二页传入的游标解析出的三元组等于第一页最后一条）；`should_attach_thread_stats_and_total`（有统计的楼带 `(n, t)`，没统计的 `(0, null)`，`total` 来自 `countComments`）；`should_reject_cursor_of_other_sort`（`sort=helpful` 配两段式游标、`sort=asc` 配三段式游标都抛 `InvalidDiscussionRequestException`）；`should_query_helpful_marks_only_with_viewer`（匿名不调 `findHelpfulMarked`）。
- [ ] **Step 2: 跑，确认失败。**
- [ ] **Step 3: 实现**（`assemble` 私有方法在有访客时一次查 `findHelpfulMarked`；`ThreadCursor.parse` 不变，`helpful` 时用 `RankCursor.parse(raw).orElseThrow(() -> new InvalidDiscussionRequestException("游标不合法"))`）。
- [ ] **Step 4: 跑 app `test`，全绿。**

### Task 7: 讨论接口与流程测试

**Files:**
- Modify: `adapter/discussion/rest/response/{CommentResponse, ThreadResponse, ThreadPageResponse}.java`、`DiscussionController.java`（`threads` 的 JavaDoc 写 `sort`：`asc` / `latest` / `helpful`）
- Create: `patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/discussion/DiscussionStatsFlowIT.java`（继承 `DiscussionFlowITSupport`）

**Interfaces:**
- Produces（JSON）：评论 `helpfulCount`、`helpful`；楼 `replyCount`、`lastReplyAt`；列表页 `total`。

- [ ] **Step 1: 写失败的流程 IT**（点有帮助用 `call(HttpMethod.PUT, user, "/comments/{id}/helpful", null)`）：
  - `should_track_reply_count_and_last_reply_time`：发 3 条回复 → `replyCount = 3`、`lastReplyAt` = 第 3 条的 `createdAt`；删第 3 条 → 2、回退到第 2 条；全删 → `0`、`null`；`total` 依次 4 → 3 → 1。
  - `should_exclude_placeholders_from_counts`：根有回复时删根 → 根成占位、`total − 1`、楼仍带 `replyCount`；回复 b 有子回复 c 时删 b → 楼内 −1。
  - `should_cascade_root_removal_without_double_decrement`：根有一条回复，先删根（占位，`total` 2 → 1），再删回复 → 根被级联去掉，`total = 0`，`discussion.thread_stats` 没有该根的行。
  - `should_sort_by_helpful_with_replies_in_time_order`：三楼，B、C 给第 2 楼根点、B 给第 3 楼根点、B 给第 1 楼的一条回复点 → `sort=helpful` 顺序 [2, 3, 1]；第 1 楼回复仍按时间正序；`size=1` 翻三页拼起来同序不重复。
  - `should_return_helpful_only_with_session`：匿名读 → 每条 `helpful` 为 `null`；B 读 → 点过的 `true`、没点的 `false`，`helpfulCount` 与 `reaction.helpful` 条数一致。
- [ ] **Step 2: 跑，确认失败。**
- [ ] **Step 3: 实现响应字段。**
- [ ] **Step 4: 跑 boot `integrationTest --tests "*Discussion*"` 与 `*HelpfulFlowIT*`，全绿。**

### Task 8: 经验读端

**Files:**
- Create: `domain/post/model/enums/ExperienceSort.java`、`domain/post/model/read/PostStats.java`
- Modify: `domain/post/port/read/PostReadPort.java`、`infra/post/persistence/PostReadAdapter.java`
- Test: `patra-interaction-domain/src/test/.../post/model/enums/ExperienceSortTest.java`、infra `PostReadAdapterIT.java`

**Interfaces:**
- Produces:
  - `enum ExperienceSort { LATEST, HELPFUL }`，`fromParam(String)`：空 / `latest` → `LATEST`，`helpful` → `HELPFUL`，其他抛 `InvalidPostRequestException`
  - `record PostStats(int helpfulCount, int replyCount, Boolean helpful)` + `of()`
  - `Map<PostId, PostStats> findStats(Collection<PostId> postIds, Long viewerId)`（SQL 见 spec 7.1；空集合返回 `Map.of()`；每个传入的 ID 都在结果里）
  - `List<PostAggregate> pageByJournalHelpful(SubjectId journal, RankCursor after, int limit)`（只 `status = 'ACTIVE'`）
  - `long countByJournal(SubjectId journal)`

- [ ] **Step 1: 写失败的测试**：`ExperienceSortTest`（三种取值 + 未知抛异常）；IT（`PostPersistenceITSupport` 的 `TRUNCATE` 补 `reaction.target_stats, reaction.helpful, discussion.subject_stats, subject.registry`；登记表行按 `NotificationDeletedCleanupMigrationIT` 的写法直接插）：
  - `should_find_stats_with_two_hop_reply_count`：经验 p1 有帮助 2、登记为对象 31、`subject_stats(31) = 4`，访客 7 点过 p1；p2 什么都没有 → `findStats([p1, p2], 7)` = {p1: (2, 4, true), p2: (0, 0, false)}；`viewerId = null` 时 `helpful` 为 `null`。
  - `should_page_journal_by_helpful_then_newest`：三条经验有帮助 1 / 1 / 3、发布时间不同 → [3 的那条, 1 里新的, 1 里旧的]；`size = 1` 翻完不重复；已删除的不出现。
  - `should_count_active_experiences_of_journal`：J1 两条（其中一条已删）+ J2 一条 → `countByJournal(J1) = 1`。
- [ ] **Step 2: 跑，确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑 domain `test` 与 infra `integrationTest --tests "*Post*"`，全绿。**

### Task 9: 经验的视图、查询、接口与流程测试

**Files:**
- Create: `app/post/usecase/query/JournalExperiencePage.java`、`adapter/post/rest/response/JournalExperiencePageResponse.java`
- Modify: `app/post/usecase/query/{ExperienceItemView, ExperienceQueryService}.java`、`app/post/service/ExperienceViews.java`、`adapter/post/rest/JournalPostController.java`、`adapter/post/rest/response/ExperienceResponse.java`
- Test: `ExperienceViewsTest`、`ExperienceQueryServiceTest`、boot `ExperienceFlowIT`

**Interfaces:**
- Consumes: Task 4、Task 8。
- Produces:
  - `ExperienceItemView` 加 `int helpfulCount`、`Boolean helpful`、`int replyCount`
  - `ExperienceViews`：`items`、`withoutAuthor`、`detail`、`authored` 都经 `reads.findStats` 填三项（`withoutAuthor` 改成批量 `withoutAuthor(List<PostAggregate> posts, Long viewer)`，一页一次查询）
  - `record JournalExperiencePage(List<ExperienceItemView> items, String nextCursor, long total)` + `of()`
  - `ExperienceQueryService.journalExperiences(String journalKey, String sort, String cursor, Integer limit)` → `JournalExperiencePage`；`LATEST` 游标仍是经验 ID；`HELPFUL` 游标 `RankCursor`（经验用 `createdAt` 与 `postId`），格式不对抛 `InvalidPostRequestException("游标不合法")`；期刊未登记 → 空页、`total = 0`
  - `GET /journals/{journalId}/posts?sort=` → `JournalExperiencePageResponse(items, nextCursor, total)`；`ExperienceResponse` 加三项

- [ ] **Step 1: 写失败的测试**：
  - `ExperienceViewsTest`：`should_fill_stats_for_items`、`should_fill_stats_for_detail_and_authored`（mock `findStats`）。
  - `ExperienceQueryServiceTest`：`should_page_by_helpful_with_rank_cursor`、`should_return_total_for_both_sorts`、`should_reject_cursor_of_other_sort`、`should_return_empty_page_with_zero_total_for_unregistered_journal`。
  - `ExperienceFlowIT`：`should_sort_journal_experiences_by_helpful`（A 写 e1、C 写 e2、D 写 e3；B、C 点 e1，B 点 e3 → `sort=helpful` 为 [e1, e3, e2]，不传 `sort` 时顺序与现有断言一致，两种都 `total = 3`）；`should_carry_helpful_and_reply_count_on_experiences`（e1 下发 2 条评论、删 1 条 → 期刊列表、主页列表、经验页的 `replyCount = 1`；B 读时 `helpful = true`，匿名读为 `null`）。
- [ ] **Step 2: 跑，确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑 app `test` 与 boot `integrationTest --tests "*Experience*"`，全绿。**

### Task 10: 全量验证与交接

**Files:**
- Modify: 共享笔记 `pap-132-discussion-stats-interface.md`（状态行改为「实现完成，未提交」；删回复的写法补成 Task 2 的最终形态）

- [ ] **Step 1: 跑 interaction 全部模块的 `spotlessApply`，再跑 `check`**：`$G -p <worktree> :patra-api:patra-interaction:patra-interaction-domain:check :patra-api:patra-interaction:patra-interaction-app:check :patra-api:patra-interaction:patra-interaction-infra:check :patra-api:patra-interaction:patra-interaction-adapter:check :patra-api:patra-interaction:patra-interaction-boot:check`，期望 BUILD SUCCESSFUL（`check` 不含 integrationTest）。
- [ ] **Step 2: 跑两套集成测试**：`… -infra:integrationTest … -boot:integrationTest`，期望全绿（后台跑，超过 2 分钟）。
- [ ] **Step 3: 对照 Issue 的 6 条 AC 逐条指出覆盖它的测试名，更新共享笔记。不提交。**
