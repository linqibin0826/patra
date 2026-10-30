# 对象统计与获赞接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（本仓库约定内联执行，不派子代理）。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收藏人数统计表与同事务维护、底座批量对象统计接口、用户获赞接口、手动触发的按明细重算任务与上线 runbook（PAP-133）。

**Architecture:** 收藏人数由 `LibraryRepositoryAdapter` 的四个写方法在同事务里按 `RETURNING` 的行 ±1；批量接口挂在底座读端，读适配器只用 SQL 读收藏、讨论两张统计表；重算是底座定义的 `StatsRecountGateway<K>` 扩展点，每个计数一个实现，底座 Handler 一键一事务地「先锁、再数、不一致才写」。

**Tech Stack:** Java 25、Spring Boot 4、`JdbcClient` 原生 SQL、PostgreSQL 17、Flyway、XXL-Job、JUnit 5 + AssertJ + Mockito、Testcontainers。

**Spec:** `docs/patra/specs/2026-10-29-object-stats-design.md`

## Global Constraints

- 只改 `patra-api/patra-interaction` 与 `docs/`；不改 catalog、identity、门户。
- 新表 `collection.subject_stats(subject_id BIGINT PK, collector_count INTEGER NOT NULL, CONSTRAINT ck_subject_stats_collectors CHECK (collector_count >= 0))`，迁移 `collection/V20261029230000__create_collection_subject_stats.sql`，本 Issue 只用 `V20261029230000 ~ V20261029239999`。
- 统计减法只用 `UPDATE`，不用 upsert；多行 ±1 按 `subject_id` 升序拿行锁。
- 批量接口 `POST /subjects/stats`，请求 `{type, keys}`，1 ~ 100 个 key，响应 `{items:[{key, collectorCount, discussionCount}]}`，`int` 字段；不要会话。
- 获赞 `GET /users/{userId}/helpful-received` → `{helpfulReceived}`（原始 `long`）；不要会话。
- 重算计数名：`collection.collector_count`、`reaction.helpful_count`、`reaction.helpful_received`、`discussion.thread_stats`、`discussion.comment_count`。XXL-Job handler `statsRecountJob`。
- 代码规范：`///` Markdown Javadoc 覆盖所有方法；record ≤ 4 参数用 `of()`；集合字段 `List.copyOf`；测试方法 snake_case；先写失败测试（CLAUDE.md TDD）。
- 运行命令都在仓库根 `/Users/linqibin/Projects/Products/patra/.claude/worktrees/v015-object-stats-e19482`，不 `cd` 出去；用 `./gradlew -p <根> …` 或绝对路径。
- 本地不提交，全部做完停在本地等用户验收（任务里的「提交」一步改为 `git add` 暂存核对）。

## Review Focus

1. **同一用户同一篇反复收藏 / 移出**：`ON CONFLICT DO NOTHING` 与重复删除都不能动计数 → Task 2 加「重复收藏、移出不在库里的不变」断言。
2. **两人并发批量操作重叠的文献**：不死锁、计数等于明细 → Task 2 并发测试（两个事务反序批量收藏 / 移出同一组）。
3. **重算与写入并发**：重算期间有人收藏，结束后计数等于明细 → Task 6 infra IT 用锁等待构造交错。
4. **批量接口重复 key、空白 key、超长数字 key**：去重保序、不合法当 0 → Task 4 查询服务单测。
5. **统计行在、明细没有**（人为删光明细）：重算置 0、不删行 → Task 6、Task 7 断言。

---

## 第一段（现在做）

### Task 1: 收藏人数统计表与回填

**Files:**
- Create: `patra-interaction-infra/src/main/resources/db/migration/collection/V20261029230000__create_collection_subject_stats.sql`
- Test: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/CollectionStatsMigrationIT.java`
- Modify: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/collection/persistence/CollectionPersistenceITSupport.java`（`TRUNCATE` 加 `collection.subject_stats`）

**Interfaces:** Produces: 表 `collection.subject_stats`，没行 = 0。

- [ ] **Step 1: 写失败测试** —— 照 `NotificationDeletedCleanupMigrationIT`：独立库 `collection_stats_migration`，迁到 `BEFORE = "20261029200200"`，造 `library_item`：(U1,S1)(U2,S1)(U1,S2)，再迁到最新。
  - `should_backfill_collector_counts`：`subject_stats` 恰为 `{S1=2, S2=1}`，没有 S3 行。
  - `should_keep_collector_count_non_negative`：插 `-1` 抛 `DataIntegrityViolationException`，消息含 `ck_subject_stats_collectors`。
- [ ] **Step 2: 跑测试确认失败** —— `./gradlew :patra-interaction-infra:integrationTest --tests "*CollectionStatsMigrationIT*"`，预期 FAIL（表不存在）。
- [ ] **Step 3: 写迁移** —— 建表 + 表 / 列 `COMMENT`（英文，照 collection 迁移风格）+ 回填 `INSERT … SELECT subject_id, count(*) … GROUP BY subject_id`。
- [ ] **Step 4: 跑测试确认通过**；同时跑 `InteractionMigrationIT`、`ReactionMigrationIT` 不回归。

### Task 2: 文献库写路径同事务维护收藏人数

**Files:**
- Modify: `patra-interaction-infra/src/main/java/dev/patra/interaction/infra/collection/persistence/LibraryRepositoryAdapter.java`
- Modify: `patra-interaction-domain/.../domain/collection/port/repository/LibraryRepository.java`（只改 Javadoc：四个写方法写明同事务维护收藏人数）
- Test: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/collection/persistence/LibraryRepositoryAdapterIT.java`

**Interfaces:** Consumes Task 1 的表。签名不变。

- [ ] **Step 1: 写失败测试**（每个一个循环：红 → 绿 → 下一个），辅助方法 `collectors(SubjectId)` = `COALESCE(sum(collector_count),0)`：
  - `should_count_collector_once_per_user`：U 收 S1 两次、V 收 S1 → 2；`insertItem` 第二次返回 `false`。
  - `should_count_only_new_items_when_copying`：U 已有 S1；`insertItems(U, [S1,S2,S3])` 返回 2 → S1=1、S2=1、S3=1。
  - `should_decrement_only_removed_items`：U 有 S1、S2；`deleteItems(U, [S1,S3])` → S1=0（行保留）、S2=1；再删一次 S1 不变。
  - `should_decrement_when_deleting_all_of_user`：U 有 S1、S2，V 有 S1；`deleteAllOf(U)` → S1=1、S2=0。
  - `should_not_deadlock_on_overlapping_batches`：两个线程各自事务、先拿各自用户锁，分别 `insertItems(U,[S1,S2,S3])` 与 `insertItems(V,[S3,S2,S1])`，再分别反序 `deleteItems`，重复 20 轮；无异常，最终每个对象计数等于 `library_item` 行数。
- [ ] **Step 2: 跑确认失败**：`--tests "*LibraryRepositoryAdapterIT*"`。
- [ ] **Step 3: 实现**：
  - `insertItem`：影响行数 1 时调私有 `incrementCollectors(List.of(id))`。
  - `insertItems`：`INSERT … RETURNING subject_id` 改用 `.query(Long.class).list()`，返回 `list.size()`，再 `incrementCollectors(ids)`。
  - `deleteItems` / `deleteAllOf`：`DELETE FROM collection.library_item … RETURNING subject_id`，再 `decrementCollectors(ids)`；`deleteAllOf` 的行数取返回列表长度。
  - `private void incrementCollectors(Collection<Long> ids)`：空则返回；`INSERT INTO collection.subject_stats (subject_id, collector_count) SELECT id, 1 FROM unnest(CAST(:ids AS bigint[])) AS t(id) ORDER BY id ON CONFLICT (subject_id) DO UPDATE SET collector_count = collection.subject_stats.collector_count + 1`。
  - `private void decrementCollectors(Collection<Long> ids)`：空则返回；先 `SELECT subject_id FROM collection.subject_stats WHERE subject_id IN (:ids) ORDER BY subject_id FOR UPDATE`，再 `UPDATE … SET collector_count = collector_count - 1 WHERE subject_id IN (:ids)`。
- [ ] **Step 4: 跑确认通过**；`CollectionRepositoryAdapterIT`、`LibraryReadAdapterIT` 不回归。

### Task 3: 底座的对象统计读端

**Files:**
- Create: `patra-interaction-domain/.../domain/subject/model/read/SubjectStats.java` —— `record SubjectStats(int collectorCount, int discussionCount)` + `of()` + `ZERO` 常量
- Create: `patra-interaction-domain/.../domain/subject/port/read/SubjectStatsReadPort.java`
- Create: `patra-interaction-infra/.../infra/subject/persistence/SubjectStatsReadAdapter.java`
- Test: `patra-interaction-infra/src/integrationTest/.../infra/subject/persistence/SubjectStatsReadAdapterIT.java`
- Modify: `SubjectRegistryITSupport`（每例 `TRUNCATE` 加 `collection.subject_stats`；第二段再加 `discussion.subject_stats`）

**Interfaces:** Produces `Map<SubjectId, SubjectStats> SubjectStatsReadPort.findStats(Collection<SubjectId> ids)`，`MAX_IDS = 100`；没有任何统计的对象不在结果里；超限抛 `IllegalArgumentException`。

- [ ] **Step 1: 失败测试**：
  - `should_read_collector_counts_and_omit_unknown`：`subject_stats` 有 S1=3、S2=0；查 [S1,S2,S9] → `{S1=(3,0), S2=(0,0)}`。
  - `should_return_empty_for_empty_ids`；`should_reject_more_than_max_ids`。
- [ ] **Step 2: 跑确认失败**。
- [ ] **Step 3: 实现** —— 第一段只一条语句读 `collection.subject_stats`，`discussionCount` 先填 0；方法内部按「收藏」「讨论」两段合并（第二段只加一条语句）。
- [ ] **Step 4: 跑确认通过**。

### Task 4: 批量对象统计接口

**Files:**
- Create: `patra-interaction-domain/.../domain/subject/exception/InvalidSubjectRequestException.java`（`RULE_VIOLATION`，照 `InvalidCollectionRequestException`）
- Modify: `patra-interaction-domain/src/test/.../domain/subject/exception/SubjectExceptionsTest.java`
- Create: `patra-interaction-app/.../app/subject/usecase/query/SubjectStatsQueryService.java`、`SubjectStatsView.java`（`record (String key, int collectorCount, int discussionCount)` + `of()`）
- Create: `patra-interaction-adapter/.../adapter/subject/rest/SubjectStatsController.java`、`request/SubjectStatsRequest.java`、`response/SubjectStatsResponse.java`、`response/SubjectStatsItemResponse.java`
- Test: `patra-interaction-app/src/test/.../app/subject/usecase/query/SubjectStatsQueryServiceTest.java`
- Test: `patra-interaction-boot/src/integrationTest/.../subject/ObjectStatsFlowIT.java`
- Modify: `patra-interaction-boot/src/integrationTest/.../subject/DeletedUserCleanupFlowIT.java`

**Interfaces:**
- Consumes: `SubjectReadPort.findIdsByKeys(SubjectType, Collection<String>)`、Task 3 的 `findStats`。
- Produces: `List<SubjectStatsView> SubjectStatsQueryService.stats(String type, List<String> keys)`。

- [ ] **Step 1: 查询服务单测（Mockito 两个端口）**，逐个红绿：
  - `should_return_one_row_per_unique_key_in_order`：keys `["2","1","2"]`，`findIdsByKeys` 返回 `{"1"→S1}`，`findStats` 返回 `{S1=(3,4)}` → `[("2",0,0),("1",3,4)]`；`findStats` 只收到 `[S1]`。
  - `should_not_query_stats_when_no_key_registered`：`findStats` 不被调用。
  - `should_reject_unknown_type`（`"book"`）、`should_reject_type_without_collect_or_discuss`（`"journal"`）→ `InvalidSubjectRequestException`。
  - `should_reject_empty_too_many_or_null_keys`：空、101 个 → 抛。
  - `should_accept_post_type`：`"post"` 正常返回。
- [ ] **Step 2: 实现** `stats(type, keys)`：`SubjectType.fromSlug` 不在或 `!supports(COLLECT) && !supports(DISCUSS)` → 抛「不支持的对象类型」；keys 为 null / 空 / 超 `SubjectReadPort.MAX_KEYS` → 抛「一次 1 ~ 100 个 key」；`LinkedHashSet` 去重；查不到补 `SubjectStats.ZERO`。请求体拷贝：null 或含 null → 空列表（照 `StatusRequest`）。控制器 `@Tag(name = "SubjectStats", description = "对象统计")`、`@PostMapping("/subjects/stats")`。
- [ ] **Step 3: 流程 IT `ObjectStatsFlowIT extends CollectionFlowITSupport`**（要访问收藏辅助方法时把它放 `collection` 包；用户 A、B，文献 100、101）：
  - `should_count_collector_once_across_collections`：A 收藏 100，加进两个收藏夹 → 匿名调批量 `collectorCount=1`；A 移出 → 0。
  - `should_count_only_new_items_when_copying_collection`：A 公开收藏夹含 100、101；B 已收藏 100；B 复制 → 100 为 2、101 为 2。
  - `should_return_zero_for_unregistered_and_invalid_keys`：keys `["100","999","abc"]`（100 未收藏过）→ 三行全 0，状态 200。
  - `should_reject_bad_requests`：`type=journal` 422；keys 空 422。
- [ ] **Step 4: 扩展 `DeletedUserCleanupFlowIT`**：`should_decrement_collector_counts_when_cleaning_deleted_users` —— A、B 都收藏 100，A 已注销，跑清理 → 批量接口 100 的 `collectorCount=1`。
- [ ] **Step 5: 跑**：`:patra-interaction-app:test --tests "*SubjectStatsQueryServiceTest*"`、`:patra-interaction-domain:test`、`:patra-interaction-boot:integrationTest --tests "*ObjectStatsFlowIT*" --tests "*DeletedUserCleanupFlowIT*"`，全绿。

### Task 5: 获赞接口

**Files:**
- Create: `patra-interaction-app/.../app/reaction/usecase/query/HelpfulQueryService.java`
- Create: `patra-interaction-adapter/.../adapter/reaction/rest/UserHelpfulController.java`、`response/HelpfulReceivedResponse.java`（`record (long helpfulReceived)` + `of()`）
- Test: `patra-interaction-app/src/test/.../app/reaction/usecase/query/HelpfulQueryServiceTest.java`
- Test: `ObjectStatsFlowIT` 加用例

**Interfaces:** Consumes `HelpfulReadPort.helpfulReceived(long)`；Produces `long HelpfulQueryService.helpfulReceived(long userId)`。

- [ ] **Step 1: 单测** `should_return_helpful_received_from_read_port`（mock 返回 3 → 3）。
- [ ] **Step 2: 实现**；控制器 `@GetMapping("/users/{userId}/helpful-received")`。
- [ ] **Step 3: 流程用例** `should_expose_helpful_received_without_session`：A 发根评论、B 点有帮助 → 匿名 `GET /users/{A}/helpful-received` 得 `helpfulReceived=1`（JSON 数字）；从没被点过的 C → 0。
- [ ] **Step 4: 跑确认通过**。

### Task 6: 重算框架与收藏人数重算

**Files:**
- Create: `patra-interaction-domain/.../domain/subject/port/gateway/StatsRecountGateway.java`
- Create: `patra-interaction-domain/.../domain/subject/model/read/StatsCorrection.java`（`record (long before, long after)` + `of()`）
- Create: `patra-interaction-app/.../app/subject/usecase/recount/RecountStatsCommand.java`、`RecountStatsHandler.java`、`RecountResult.java`
- Create: `patra-interaction-app/.../app/collection/service/CollectorCountRecountGatewayImpl.java`
- Modify: `LibraryRepository` / `LibraryRepositoryAdapter`：加两个方法
- Create: `patra-interaction-adapter/.../adapter/subject/scheduler/StatsRecountScheduleJob.java`
- Test: `RecountStatsHandlerTest`、`CollectorCountRecountGatewayImplTest`、`StatsRecountScheduleJobTest`、`LibraryRepositoryAdapterIT` 加用例

**Interfaces:**
- Produces:
  ```java
  public interface StatsRecountGateway<K> {
    String counter();
    List<K> findDrifted();
    Optional<StatsCorrection> recount(K key);   // 在底座开的事务里调用，一个键一个事务；改了才有值
  }
  ```
  `RecountResult(List<CounterResult> counters)`（紧凑构造器 `List.copyOf`）+ `of()` + `int corrected()` + `String summary()`；`CounterResult(String counter, int corrected)` + `of()`。
  `LibraryRepository.findCollectorCountDrift(): List<SubjectId>`；`LibraryRepository.recountCollectorCount(SubjectId): Optional<StatsCorrection>`。

- [ ] **Step 1: Handler 单测**（两个手写假 gateway，`TransactionOperations` 用 `TransactionOperations.withoutTransaction()` 的包装计数器记录调用次数）：
  - `should_recount_each_drifted_key_in_its_own_transaction`：gateway `a` 有 3 个差异键其中 2 个改了，`b` 有 0 个 → 结果 `{a=2, b=0}`、`corrected()==2`、事务 3 次。
  - `should_order_counters_by_name`：结果列表按计数名排序（Handler 排好再放进有序的 record 列表；测试断言 `counters()` 的计数名顺序与 `summary()`）。
  - `should_propagate_unexpected_exception`。
- [ ] **Step 2: 实现 Handler**：注入 `List<StatsRecountGateway<?>>`、`TransactionOperations`；私有泛型方法 `<K> int recountAll(StatsRecountGateway<K>)`；改了的键打 INFO `统计重算修正: counter={}, key={}, before={}, after={}`；末尾 INFO `统计重算完成: corrected={}, byCounter={}`。
- [ ] **Step 3: 仓储 IT（`LibraryRepositoryAdapterIT`）**：
  - `should_find_collector_count_drift`：明细 (U,S1)(V,S1)(U,S2)；统计 S1=1、S3=4、S2 缺 → 差异 `[S1,S2,S3]`（任意序）；统计改对后为空。
  - `should_recount_collector_count_to_detail`：S1 改成 5 → `recountCollectorCount(S1)` 得 `(5,2)`，再调为空；S3（无明细、统计 4）→ `(4,0)` 且行保留为 0；S2（缺行、明细 1）→ `(0,1)`。
  - `should_not_lose_concurrent_collect_while_recounting`：事务 A `lockOwner(U)` + `insertItem(U,S1)`（不提交）；另一线程跑 `recountCollectorCount(S1)` 事务；A 提交后等重算结束 → 计数等于明细行数。
- [ ] **Step 4: 实现仓储**：`findCollectorCountDrift` 用 `FULL JOIN` 对比 `(SELECT subject_id, count(*) n FROM collection.library_item GROUP BY subject_id)` 与 `subject_stats`，`COALESCE` 两侧后 `<>`；`recountCollectorCount`：`INSERT (id,0) ON CONFLICT DO NOTHING` → `SELECT collector_count … FOR UPDATE` → 新语句 `count(*)` → 不等才 `UPDATE` 并返回修正；方法加 `@Transactional(propagation = MANDATORY)`。
- [ ] **Step 5: `CollectorCountRecountGatewayImpl` 单测 + 实现**：`counter()` = `"collection.collector_count"`，两个方法直接委托仓储。
- [ ] **Step 6: 任务** `StatsRecountScheduleJob`：`@XxlJob("statsRecountJob")`；成功 `XxlJobHelper.handleSuccess("差异 %d 行：%s")`，异常 `handleFail("执行失败：…")`。单测照 `DeletedUserCleanupScheduleJobTest`：`should_report_success_with_counts`、`should_report_failure_on_exception`。
- [ ] **Step 7: 跑** app / adapter 单测与 infra IT，全绿。

### Task 7: 有帮助数与获赞的重算

**Files:**
- Modify: `HelpfulRepository` / `HelpfulRepositoryAdapter`：加四个方法
- Create: `patra-interaction-app/.../app/reaction/service/HelpfulCountRecountGatewayImpl.java`、`HelpfulReceivedRecountGatewayImpl.java`
- Test: `HelpfulRepositoryAdapterIT` 加用例；两个 GatewayImpl 单测

**Interfaces:** Produces
`List<HelpfulTarget> findHelpfulCountDrift()`、`Optional<StatsCorrection> recountHelpfulCount(HelpfulTarget)`（调用方已拿目标锁）、`List<Long> findReceivedDrift()`、`Optional<StatsCorrection> recountReceived(long userId)`。

- [ ] **Step 1: 仓储 IT**：
  - `should_find_and_fix_helpful_count_drift`：明细 C1×2，统计 C1=5、C2=3（无明细）、P1 缺（明细 1）→ 差异三个；逐个 recount → `(5,2)`、`(3,0)` 行保留、`(0,1)`；再找为空。
  - `should_find_and_fix_received_drift`：明细受益人 9 两条、受益人 null 一条；统计 9=7、8=2 → 修正 `(7,2)`、`(2,0)`；null 受益人不进统计。
- [ ] **Step 2: 实现仓储**：四个方法里 `recountHelpfulCount`、`recountReceived` 标 `@Transactional(propagation = MANDATORY)`；差异查询照 Task 6；`recountHelpfulCount` 锁内直接数、读旧值（缺行为 0）、不等则 upsert 成新值；`recountReceived` 用「建出行 → `FOR UPDATE` → 新语句数」。
- [ ] **Step 3: GatewayImpl 单测 + 实现**：`HelpfulCountRecountGatewayImpl.recount(target)` 先 `lockTarget(target)` 再 `recountHelpfulCount`（单测用 `InOrder` 断言顺序）；计数名 `reaction.helpful_count` / `reaction.helpful_received`。
- [ ] **Step 4: 流程 IT `StatsRecountFlowIT`**（boot，`collection` 包，继承 `CollectionFlowITSupport`）：
  - `should_report_zero_when_consistent`：收藏、点有帮助造数据，经 `CommandBus` 跑 `RecountStatsCommand` → `corrected()==0`。
  - `should_fix_corrupted_rows_and_report`：`JdbcClient` 把收藏人数改成 9、删掉一条有帮助数统计行、把获赞改成 0 → 跑 → 三项各 1、值等于明细 → 再跑 0。
- [ ] **Step 5: 跑**全绿。

### Task 8: runbook、README 与第一段收口

**Files:**
- Create: `docs/patra/runbooks/v0.15-helpful-go-live-runbook.md`（照 `v0.14-discussion-go-live-runbook.md` 的结构；内容见 spec 第 7 节；xxl-job 登记写 handler、调度类型「无」、超时 600、不重试）
- Modify: `patra-api/patra-interaction/README.md`（收藏人数统计、批量对象统计、获赞、重算任务各一两句，任务清单加 `statsRecountJob`）

- [ ] **Step 1: 写文档**。
- [ ] **Step 2: 跑** `./gradlew :patra-interaction-domain:check :patra-interaction-app:check :patra-interaction-infra:check :patra-interaction-adapter:check :patra-interaction-boot:check :patra-interaction-infra:integrationTest :patra-interaction-boot:integrationTest`，全绿（先跑 `spotlessApply`）。
- [ ] **Step 3: 在会话里汇报第一段进度**，等「PAP-132 已合回」。

## 第二段（PAP-132 合回后）

### Task 9: 批量接口带讨论数

- [ ] `git merge feat/v0.15-helpful-api`，核对 `discussion.subject_stats` 与 `discussion.thread_stats` 的实际列名与本计划一致。
- [ ] `SubjectStatsReadAdapterIT`：`should_read_discussion_counts`（`discussion.subject_stats` S1=5、S4=2 → S1=(c,5)、S4=(0,2)）→ 红 → `SubjectStatsReadAdapter` 加第二条语句 → 绿。
- [ ] `ObjectStatsFlowIT`：`should_count_discussion_including_replies`：A 发根评论、B 回复 → 100 的 `discussionCount=2`；删回复 → 1。

### Task 10: 讨论两项的重算

- [ ] 讨论的 Repository 加 `findThreadStatsDrift / recountThreadStats(long rootId)`（调用方已拿楼锁；根评论不存在则删行）与 `findCommentCountDrift / recountCommentCount(SubjectId)`（建出行 → `FOR UPDATE` → 新语句数），口径照 PAP-132 的回填 SQL；infra IT 照 Task 7 Step 1。
- [ ] `ThreadStatsRecountGatewayImpl`（先拿楼锁）、`CommentCountRecountGatewayImpl`；单测。
- [ ] `StatsRecountFlowIT` 两个用例里加上楼统计与评论总数。
- [ ] runbook、README 补讨论两项。

### Task 11: 全量验证

- [ ] `./gradlew check`（含两套集成测试）全绿；在会话里汇报，停在本地等用户验收。
