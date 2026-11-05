# 阅读历史实施计划（PAP-169）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（本仓库约定：plan 在当前会话内联执行）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `patra-analytics` 加 `history` 子域：采集时同步写个人阅读历史（PG），提供翻页、删一条、清空、开关接口，12 个月过期清理与注销清理。

**Architecture:** 采集用例在 Kafka 写成后发进程内事件 `ArticlesReadEvent`（`domain.collect.event`），`history` 子域同步 `@EventListener` 合并、过滤（开关、清空时刻、删除记录）后一条 upsert 写 PG，失败与超时只记指标。接口走 `CurrentUserPort`（`patra-tool-security` 验网关断言）。持久化用 `JdbcClient` 原生 SQL（照 interaction `HelpfulRepositoryAdapter`）。

**Tech Stack:** Java 25、Spring Boot 4.1.2、PostgreSQL 17 + Flyway、`JdbcClient`、`patra-tool-security`、XXL-Job、Testcontainers。

**Spec:** [`docs/patra/specs/2026-11-02-analytics-reading-history-design.md`](../specs/2026-11-02-analytics-reading-history-design.md)

## Global Constraints

- schema `history`；表 `reading_history`（PK `user_id, article_id`）、`recording_setting`（PK `user_id`，`cleared_at` 可空）、`deleted_entry`（PK `user_id, article_id`）；索引照 spec §5；迁移在 `infra/src/main/resources/db/migration/history/V20261102100000__create_history_schema.sql`。
- 只记：`user_id` 非空、`is_synthetic = false`、`article_id` 非空，事件是 `ARTICLE_VIEWED`（打开）、`FULLTEXT_READ` / `PDF_DOWNLOADED`（打开 + 全文）；`readAt` = 校正后的 `derived_at`。爬虫标记不影响。
- 丢掉：开关关着；`readAt <= cleared_at`；`readAt <= deleted_at`（等于也丢）。
- upsert：`last_read_at = GREATEST(旧, 新)`、`fulltext_read = 旧 OR 新`；写入前在 Java 里按（用户，文献）合并并排序；写入事务里 `SET LOCAL statement_timeout = '500ms'`、`SET LOCAL lock_timeout = '500ms'`。
- Hikari `connection-timeout: 500ms`（boot `application.yml`，原 30000）。
- 接口前缀 `/me/reading-history`：`GET`（`?cursor=`，一页 30，返回 `recordingEnabled`、`items[{articleId, lastReadAt, fulltextRead}]`、`nextCursor`）、`DELETE /{articleId}`、`DELETE`、`GET|PUT /recording`（`{enabled}`）；204 / 401 / 422（校验失败一律 422）。列表只返回 12 个月内的。
- 游标：Base64URL（无填充）编码的 `"{毫秒}:{articleId}"`，排序 `last_read_at DESC, article_id DESC`。
- 保留：历史 12 个日历月（UTC，`OffsetDateTime.minusMonths(12)`），删除记录 2 天；清理任务 `readingHistoryRetentionJob`、`0 40 6 * * ?`、每批 10,000 行。
- 注销清理：`HistoryUserDataCleanupGatewayImpl`，子域名 `history`，找用户与删除覆盖三张表，一个事务。
- 指标：`analytics.history.writes{outcome=recorded|disabled|erased|failed}`、`analytics.history.purged{table=reading_history|deleted_entry}`。
- 子域 `history` 加进 `AnalyticsArchitectureTest`，`.allowingSubscription("history", "collect")`。
- 分页查询走 `ReadingHistoryReadPort`（`port.read`），不放在 spec §3 写的 `ReadingHistoryRepository` 里：port-service 规则里 CQRS 读端用 ReadPort。
- 不按任务提交；全部完成、终审通过并经用户同意后再提交。

## Review Focus

1. **同一批里同一个人对同一篇有多条**（打开后马上读全文）——不能触发 PG 的 `cannot affect row a second time`；合并成一行、全文为真。由 Task 3 的 `duplicate_reads_in_batch_are_merged` 钉住。
2. **晚到的旧事件**（比库里的时间早）——时间不往回改、全文标记不被抹掉。由 Task 1 的 `older_read_does_not_move_time_back`、`plain_view_keeps_fulltext_flag` 钉住。
3. **PG 被锁住或很慢**——写历史 1 秒内以失败结束，采集照常 202。由 Task 1 的 `write_times_out_when_row_is_locked` 与 Task 3 的 `write_failure_is_swallowed` 钉住。
4. **很多行同一毫秒**（模拟器批量造事件）——翻页不重不漏、顺序稳定。由 Task 1 的 `same_millisecond_rows_page_stably` 钉住。
5. **超长的用户 ID 或文献 ID**（schema 允许 19 位，超过 `long`）——不让整批采集失败，只是这条不进历史。由 Task 2 的 `oversized_ids_are_not_recorded` 钉住。

---

### Task 1：表结构与持久化

**Files:**
- Create：`domain/.../domain/history/model/vo/{ReadingRecord,ReadingKey,ReadingEntry,RecordingSetting,HistoryCursor}.java`、`domain/.../domain/history/exception/InvalidHistoryCursorException.java`、`domain/.../domain/history/policy/HistoryRetention.java`
- Create：`domain/.../domain/history/port/repository/{ReadingHistoryRepository,RecordingSettingRepository}.java`、`domain/.../domain/history/port/read/ReadingHistoryReadPort.java`
- Create：`infra/.../infra/history/persistence/{ReadingHistoryRepositoryAdapter,RecordingSettingRepositoryAdapter,ReadingHistoryReadAdapter}.java`、迁移脚本（见 Global Constraints）
- Create：各层 `history` 的 `package-info.java`
- Test：`domain/src/test/.../history/model/vo/HistoryCursorTest.java`、`domain/src/test/.../history/policy/HistoryRetentionTest.java`、`infra/src/integrationTest/.../history/persistence/{HistoryPersistenceITBootstrap,HistoryPersistenceITSupport,ReadingHistoryRepositoryAdapterIT,RecordingSettingRepositoryAdapterIT,ReadingHistoryReadAdapterIT}.java`

**Interfaces:**
- Produces（domain）：
  - `record ReadingRecord(long userId, long articleId, Instant readAt, boolean fulltext)`（`of`）；`record ReadingKey(long userId, long articleId)`（`of`）；`record ReadingEntry(long articleId, Instant lastReadAt, boolean fulltextRead)`（`of`）
  - `record RecordingSetting(long userId, boolean enabled, Instant clearedAt)`（`clearedAt` 可空；`of`；`static RecordingSetting defaults(long userId)` = 开、无清空；`boolean accepts(Instant readAt)` = `enabled && (clearedAt == null || readAt.isAfter(clearedAt))`）
  - `record HistoryCursor(Instant lastReadAt, long articleId)`：`String encode()`、`static HistoryCursor decode(String)`（解析失败抛 `InvalidHistoryCursorException`，`RULE_VIOLATION`）
  - `final class HistoryRetention`：`static Instant historyCutoff(Instant now)`（UTC 减 12 个月）、`static Instant deletionCutoff(Instant now)`（减 2 天）、`int PAGE_SIZE = 30`、`int PURGE_BATCH = 10_000`
  - `ReadingHistoryRepository`：`void upsertAll(List<ReadingRecord> merged)`（要求已合并、已排序；`MANDATORY` 事务；内含两条 `SET LOCAL`）、`Map<ReadingKey, Instant> findDeletedAt(Collection<ReadingKey> keys)`、`void deleteOne(long userId, long articleId, Instant deletedAt)`、`void deleteAllOf(long userId)`（历史 + 删除记录）、`int purgeReadBefore(Instant cutoff, int limit)`、`int purgeDeletionsBefore(Instant cutoff, int limit)`、`List<Long> findUsersAfter(long afterUserId, int limit)`（三表合并）、`void deleteUsers(List<Long> userIds)`（三表）
  - `RecordingSettingRepository`：`Optional<RecordingSetting> find(long userId)`、`Map<Long, RecordingSetting> findAll(Collection<Long> userIds)`、`void saveEnabled(long userId, boolean enabled, Instant now)`、`void markCleared(long userId, Instant clearedAt)`（没有行插入 `enabled = true`）
  - `ReadingHistoryReadPort`：`List<ReadingEntry> findPage(long userId, Instant since, HistoryCursor after, int limit)`（`after` 可空）

- [ ] **Step 1：写失败测试**
  - `HistoryCursorTest`：`encode_decode_round_trip`（`2026-11-02T07:42:10.177Z` + `40000001`）；`garbage_cursor_is_rejected`（`"!!"`、`"bWlzc2luZw"`、空串 → `InvalidHistoryCursorException`）。
  - `HistoryRetentionTest`：`history_cutoff_is_twelve_calendar_months`（`2026-03-31T00:00Z` → `2025-03-31T00:00Z`；`2028-02-29T12:00Z` → `2027-02-28T12:00Z`）；`deletion_cutoff_is_two_days`。
  - `RecordingSetting`：`accepts_only_after_cleared`（`readAt == clearedAt` → false；晚 1 毫秒 → true；关着 → false）放进 `RecordingSettingTest`。
  - `ReadingHistoryRepositoryAdapterIT`（每例 `TRUNCATE` 三表；写在 `TransactionTemplate` 里）：`reopen_updates_time_once`、`older_read_does_not_move_time_back`、`plain_view_keeps_fulltext_flag`、`write_times_out_when_row_is_locked`（另一个线程在事务里 `SELECT ... FOR UPDATE` 锁住目标行并持有 3 秒；`upsertAll` 在 2 秒内抛出、库里时间未变）、`delete_one_writes_deletion_and_is_idempotent`、`delete_all_removes_rows_and_deletions_of_that_user_only`、`purge_deletes_in_batches_until_done`（25 行过期 + 3 行未过期，`limit = 10` 循环调用 → 返回 10、10、5、0）、`purge_deletions_before_cutoff`、`finds_users_across_three_tables`（用户 3 只在历史、5 只在设置、9 只在删除记录 → `findUsersAfter(0, 10) = [3, 5, 9]`、`findUsersAfter(3, 1) = [5]`）、`delete_users_clears_three_tables`。
  - `RecordingSettingRepositoryAdapterIT`：`missing_setting_is_empty`、`save_enabled_upserts`、`mark_cleared_inserts_enabled_row`、`mark_cleared_keeps_disabled_flag`、`find_all_returns_only_existing`。
  - `ReadingHistoryReadAdapterIT`：`pages_newest_first_without_gaps`（65 行，30/30/5）、`same_millisecond_rows_page_stably`（40 行同一时刻，按 `article_id` 倒序，两页合起来 40 个不重复）、`rows_older_than_since_are_hidden`、`other_users_rows_are_invisible`。
- [ ] **Step 2：运行确认失败**：`./gradlew :patra-api:patra-analytics:patra-analytics-domain:test :patra-api:patra-analytics:patra-analytics-infra:integrationTest --tests "*history*"`。
- [ ] **Step 3：实现**（DDL 照 spec §5；`upsertAll` 用一条多行 `INSERT ... VALUES ... ON CONFLICT (user_id, article_id) DO UPDATE`；`purge*` 用 `DELETE ... WHERE ctid IN (SELECT ctid ... LIMIT :limit)`；`findUsersAfter` 三表 `UNION` 后 `ORDER BY ... LIMIT`）。
- [ ] **Step 4：运行确认通过**。

### Task 2：采集侧发事件

**Files:**
- Modify：`domain/.../collect/model/vo/CollectedEnvelope.java`（加 `Long articleId`，可空）、`infra/.../collect/schema/NetworkntEventSchemaAdapter.java`（`toEnvelope` 解析属性里的 `article_id`）、`app/.../collect/usecase/collect/CollectEventsHandler.java`（注入 `ApplicationEventPublisher`）
- Create：`domain/.../domain/collect/event/{ArticleRead,ArticlesReadEvent}.java`
- Test：`domain/src/test/.../collect/event/ArticleReadTest.java`；Modify：`NetworkntEventSchemaAdapterTest`、`CollectEventsHandlerTest`

**Interfaces:**
- Produces：`record ArticleRead(long userId, long articleId, Instant readAt, boolean fulltext)`，`static Optional<ArticleRead> from(CollectedEnvelope event, Instant readAt)`（不满足 Global Constraints 的「只记」或 `user_id` 解析不成 `long` 时为空）；`record ArticlesReadEvent(List<ArticleRead> reads)`（`of`，`copyOf`）。`CollectEventsHandler` 在 `publisher.publish(...)` 成功返回后、`reads` 非空时 `events.publishEvent(ArticlesReadEvent.of(reads))`。

- [ ] **Step 1：写失败测试**
  - `ArticleReadTest`：`article_viewed_by_user_is_a_plain_read`；`fulltext_and_pdf_are_fulltext_reads`；`anonymous_synthetic_or_other_events_are_not_reads`（`user_id = null`、`synthetic = true`、`ARTICLE_COLLECTED`、`articleId = null` 各一）；`oversized_ids_are_not_recorded`（`user_id = "9999999999999999999"` → 空）。
  - `NetworkntEventSchemaAdapterTest`：`article_id_is_parsed_into_envelope`（`39871245`）；`envelope_without_article_id_has_null`（`page_viewed`）；`oversized_article_id_is_null`（属性里 `"9999999999999999999"` → `null`，事件仍合格）。
  - `CollectEventsHandlerTest`：`publishes_reads_after_kafka_success`（登录用户的 `article_viewed` + 匿名 `page_viewed` → 一个事件，1 条 `ArticleRead`，`readAt` = 校正后的 `derived_at`）；`no_event_when_kafka_fails`；`no_event_without_reads`；`rejected_events_are_not_reads`。
- [ ] **Step 2：运行确认失败**。
- [ ] **Step 3：实现**。
- [ ] **Step 4：运行确认通过**：domain、app、infra 的 test。

### Task 3：history 应用层

**Files:**
- Create：`app/.../app/history/listener/ReadingHistoryListener.java`
- Create：`app/.../app/history/usecase/clear/{ClearReadingHistoryCommand,ClearReadingHistoryHandler}.java`、`.../usecase/delete/{DeleteReadingEntryCommand,DeleteReadingEntryHandler}.java`、`.../usecase/recording/{ChangeRecordingCommand,ChangeRecordingHandler,RecordingResult}.java`、`.../usecase/purge/{PurgeExpiredHistoryCommand,PurgeExpiredHistoryHandler,PurgeResult}.java`、`.../usecase/query/{ReadingHistoryQueryService,ReadingHistoryPage}.java`
- Create：`app/.../app/history/service/HistoryUserDataCleanupGatewayImpl.java`
- Test：上面每个类一个 `*Test`（假仓储 + `SimpleMeterRegistry` + 固定 `Clock` + `TransactionOperations.withoutTransaction()`）

**Interfaces:**
- Consumes：Task 1 的仓储、读端口、`HistoryRetention`、`RecordingSetting.accepts`；Task 2 的 `ArticlesReadEvent`；`CurrentUserPort`（`patra-tool-core`）。
- Produces：
  - `ReadingHistoryListener.on(ArticlesReadEvent)`：合并 → 读设置与删除记录 → 过滤 → `transactions.executeWithoutResult(s -> upsertAll(...))`；任何 `RuntimeException` 捕获后 WARN + `failed`，不抛出。
  - Command 都以 `of(...)` 创建，用户取 `currentUser.require().userId()`（在 Handler 里取，Command 不带用户）：`ClearReadingHistoryCommand()`、`DeleteReadingEntryCommand(long articleId)`、`ChangeRecordingCommand(boolean enabled)` → `RecordingResult(boolean enabled)`；`PurgeExpiredHistoryCommand()` → `PurgeResult(int historyRows, int deletionRows)`。
  - `ReadingHistoryQueryService`：`ReadingHistoryPage page(String cursor)`（`record ReadingHistoryPage(boolean recordingEnabled, List<ReadingEntry> items, String nextCursor)`；取 `PAGE_SIZE + 1` 行判断是否还有下一页）、`boolean recordingEnabled()`。
  - `HistoryUserDataCleanupGatewayImpl`：`subdomain() = "history"`、`findUsersAfter` 委托仓储、`deleteAllOf(List<Long>)` 在一个事务里 `deleteUsers` 并返回人数，空列表返回 0 不调用。

- [ ] **Step 1：写失败测试**
  - `ReadingHistoryListenerTest`：`records_reads_for_enabled_users`；`duplicate_reads_in_batch_are_merged`（同一人同一篇：普通 10:00 + 全文 09:59 → 一行，时间 10:00、全文真）；`rows_are_sorted_by_user_then_article`；`disabled_users_are_skipped`（记 `disabled`）；`reads_not_after_cleared_at_are_erased`（`readAt == clearedAt` 也丢，记 `erased`）；`reads_not_after_deletion_are_erased`；`write_failure_is_swallowed`（仓储抛异常 → 不抛、记 `failed`，计数 = 合并后的行数）。
  - `ClearReadingHistoryHandlerTest`：`clears_rows_and_marks_cleared_in_one_transaction`（`RecordingTransactions` 断言顺序：BEGIN、deleteAllOf、markCleared、END；`clearedAt` = 固定时钟）；`anonymous_is_rejected`（`CurrentUserPort` 无用户 → `AuthenticationRequiredException`）。
  - `DeleteReadingEntryHandlerTest`：`deletes_with_now_as_deleted_at`；`rejects_non_positive_article_id`（`DeleteReadingEntryCommand.of(0)` 抛 `IllegalArgumentException`；接口层另有 `@Positive`，HTTP 上是 422）。
  - `ChangeRecordingHandlerTest`：`saves_and_returns_new_value`。
  - `ReadingHistoryQueryServiceTest`：`first_page_has_next_cursor_when_more`（假读端口返回 31 行 → 30 条 + `nextCursor` 指向第 30 条）；`last_page_has_null_cursor`；`since_is_twelve_months_ago`；`recording_enabled_defaults_true`；`bad_cursor_propagates`。
  - `PurgeExpiredHistoryHandlerTest`：`purges_until_batch_is_short`（假仓储依次返回 10000、10000、42 → 合计 20042，删除记录同理）；`cutoffs_come_from_clock`。
  - `HistoryUserDataCleanupGatewayImplTest`：`finds_users_from_repository`、`deletes_batch_in_one_call`、`empty_batch_deletes_nothing`。
- [ ] **Step 2：运行确认失败**。
- [ ] **Step 3：实现**（`analytics.history.purged{table}` 在 `PurgeExpiredHistoryHandler` 记）。
- [ ] **Step 4：运行确认通过**：app 的 test。

### Task 4：接口、任务与装配

**Files:**
- Create：`adapter/.../adapter/history/rest/MyReadingHistoryController.java`、`.../rest/request/RecordingRequest.java`、`.../rest/response/{ReadingHistoryResponse,ReadingEntryResponse,RecordingResponse}.java`
- Create：`adapter/.../adapter/history/scheduler/ReadingHistoryRetentionScheduleJob.java`
- Modify：`boot/build.gradle.kts`（`implementation(project(":patra-tool:patra-tool-security"))`、`testImplementation(testFixtures(project(":patra-tool:patra-tool-security")))`）、`boot/.../resources/application.yml`（Hikari `connection-timeout: 500ms`）、`AnalyticsArchitectureTest`（加 `history` 与订阅边）
- Test：`adapter/src/test/.../history/scheduler/ReadingHistoryRetentionScheduleJobTest.java`、`boot/src/integrationTest/.../history/HistoryFlowIT.java`

**Interfaces:**
- Consumes：Task 3 的 Command、`ReadingHistoryQueryService`；Task 2 的采集链路。
- Produces：spec §6 的四个接口；`@XxlJob("readingHistoryRetentionJob")`（成功回写「已删除 N 条历史、M 条删除记录」，异常回写失败、不抛）。

- [ ] **Step 1：写失败测试**
  - `ReadingHistoryRetentionScheduleJobTest`：成功 / 抛异常两种回写（照 `DeletedUserCleanupScheduleJobTest`）。
  - `HistoryFlowIT`（PG + Kafka + ClickHouse + Redis 初始化器，`TestIdentity.headers(userId)` 带断言；每例 `TRUNCATE` `history` 三表；用 `collect` 包的 `CollectExamples`（改为 public）造信封并改 `user_id`；`InvalidHistoryCursorException` 带 `RULE_VIOLATION`，由 tool-web 自动映射 422，不改 `AnalyticsErrorMappingContributor`）：
    - `anonymous_requests_are_401`（四个接口各一次）
    - `collected_view_appears_in_history`（采集一条 `article_viewed`，`GET` 里 `articleId` 是字符串 `"39871245"`、`fulltextRead = false`、`recordingEnabled = true`）
    - `disabled_recording_skips_history_but_not_kafka`（`PUT /recording {enabled:false}` 后采集一条 → 历史为空，Kafka `events.raw` 能读到这条 `event_id`）
    - `late_event_after_clear_is_dropped`（采集 A → `DELETE` 清空 → 采集一条 `created_at`/`sent_at` 早于清空时刻 1 分钟的 B（同一篇）→ 历史为空；再采集一条现在的 C → 出现）
    - `delete_one_is_idempotent`（两次 `DELETE /{id}` 都 204）
    - `bad_cursor_and_bad_id_are_422`（`?cursor=!!`、`DELETE /0`、`PUT /recording {}`）
- [ ] **Step 2：运行确认失败**。
- [ ] **Step 3：实现**。
- [ ] **Step 4：运行确认通过**：adapter、boot 的 test 与 integrationTest。

### Task 5：文档、门控与终审

- [ ] **Step 1**：runbook 新增 8.3（登记 `readingHistoryRetentionJob`、验收步骤照 spec §10）；`patra-api/patra-analytics/README.md` 加 history；`./gradlew dumpModuleGraph`（boot 新依赖 tool-security）；`.claude/rules/project-info.md` 的 analytics 一行。
- [ ] **Step 2**：`./gradlew spotlessApply` 后 `./gradlew check`，再单独跑 analytics infra 与 boot 的 `integrationTest`（`check` 不执行集成测试）。Expected：全部通过。
- [ ] **Step 3**：新上下文 reviewer 终审，修 Critical / Important，等用户同意后提交；提醒用户在 IDEA 里「加载 Gradle 更改」。
