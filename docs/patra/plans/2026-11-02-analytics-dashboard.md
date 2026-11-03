# 运营分析面板实施计划（PAP-172）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（用户定：当前会话内联 TDD 执行，不派子代理）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 mini 的 Grafana 上出一块「运营分析」看板（活跃、留存、漏斗与跳出、检索、文献使用 COUNTER、数据质量），全部读 ClickHouse 的 ADS 小表，三个变量可切换。

**Architecture:** 每张 ADS 表 = 参数化视图 `<表>_calc(from, to)`（口径只写一份）+ 目标表 `ReplacingMergeTree(computed_at)` + 每小时的定时刷新物化视图（传 `today() - 1 .. today()`）。回填脚本按天调同一个视图；集成测试也调它。看板 JSON 走 Grafana provisioning，另有一个集成测试把看板里每条 SQL 在测试库上跑一遍。

**Tech Stack:** ClickHouse 26.8.36.3（参数化视图、`REFRESH EVERY … APPEND`、`windowFunnel`、`retention`）、Grafana 12.3.0 + grafana-clickhouse-datasource 4.22.1、Java 25 / JUnit 5 / AssertJ / Testcontainers（`ClickHouseContainerInitializer`）、Bash。

**Spec:** `docs/patra/specs/2026-11-02-analytics-dashboard-design.md`（下称 spec）。口径以 spec §4 为准，本计划不重复。

**分支：** PR #375（指标走 OTLP）合并后，从最新 `origin/main` 切 `feat/v0.18-analytics-dashboard`。spec 与本计划随这个 PR 提交。PR 描述逐行写 `Closes PAP-172`。

## Global Constraints

- 天一律北京时间：`toDate(x, 'Asia/Shanghai')`；ADS 的天列名统一 `day`（留存表为 `cohort_day`）。
- 两个维度列：`is_synthetic Bool`、`traffic LowCardinality(String)`，取值只有 `'human'`、`'all'`（spec §3.3）。`human` 按会话 = `has_client = 1 AND has_bot = 0`；按人 × 天 = `max(has_client) = 1 AND max(is_bot) = 0`。
- 每张 ADS 目标表：`ENGINE = ReplacingMergeTree(computed_at)`，`computed_at DateTime DEFAULT now()`，`ORDER BY` = 天 + 全部维度键；读时一律 `FINAL`。
- 刷新视图：`REFRESH EVERY 1 HOUR OFFSET <n> MINUTE APPEND TO <表>`，各表 `n` 错开，且避开整点（热读每小时整点重算）：active 7、retention 12、sessions 17、search 22、zero_terms 27、counter 32、quality 37。
- 迁移脚本放 `patra-api/patra-analytics/patra-analytics-infra/src/main/resources/clickhouse/`，从 `V6__` 起编号，只增不改，DDL 一律 `IF NOT EXISTS`（入库设计 §5）。
- ADS 表不存 `user_id` / `person_id` 明文；唯一新增的含用户 ID 的表 `device_user_links` 必须进注销清理（入库设计 §6）。
- 面板查询只读 ADS 表、`dlq_events`、`reconcile_results`、Prometheus；不读 `events`、`daily_person_activity`、`article_session_reads`（spec §7）。
- 不新增常驻容器、不调高任何容器内存。mini 上建表、回填、重跑 `reconcile`、改 Grafana 之前停下来，等编排会话转达用户同意。
- 代码规范：`///` Markdown JavaDoc、无全类名、中文注释与提交；commit subject 用中文动词起头；测试方法 snake_case。

## Review Focus

1. **跨午夜的会话**：会话 23:50 开始、00:10 结束，算进开始那天，第二天不重复算。由 Task 4 的 `session_crossing_midnight_counts_on_start_day` 覆盖。
2. **当天还没过完 / dN 还没到**：留存表对还没到第 N 天的同期群存 `NULL` 而不是 0。由 Task 3 的 `future_day_n_is_null_not_zero` 覆盖。
3. **同一天被重算两次**：刷新视图每小时 APPEND 一版，读 `FINAL` 后只剩最新一版，数字不翻倍。由 Task 2 的 `recomputing_a_day_does_not_double_count` 覆盖（各表共用同一模式，只在这里测一次）。
4. **对账时分区已被 TTL 删掉**：`reconcile` 跳过这一天、不写行，不覆盖旧的正确结果。由 Task 8 的 `skips_days_whose_partition_is_gone` 覆盖。
5. **「含合成数据」切换**：同一面板在 `synthetic = 0` 与 `0,1` 下数字不同。由 Task 10 的 `synthetic_toggle_changes_numbers` 覆盖（这是 AC 原文）。

---

### Task 1：设备与账号的对应表（认回的数据源）

**Files:**
- Create: `…/clickhouse/V6__device_user_links.sql`
- Modify: `…/infra/ingest/store/ClickHouseEventStoreAdapter.java`（`deleteUsers`）
- Test: `…/infra/src/integrationTest/java/dev/patra/analytics/infra/ads/DeviceUserLinksIT.java`
- Modify: `…/infra/ingest/store/ClickHouseEventStoreAdapterIT.java`（`deletes_users_everywhere`）

**Interfaces:**
- Produces：表 `device_user_links(device_id String, user_id UInt64, first_at SimpleAggregateFunction(min, DateTime64(3, 'UTC')))`，`AggregatingMergeTree ORDER BY (device_id, user_id)`；物化视图 `device_user_links_mv`（`events` 中 `user_id != 0` 的行）；参数化视图 `device_user_links_calc(from Date, to Date)`（回填用，输出与目标表同列）。
- Produces：认回规则「一台设备取 `first_at` 最早的那个用户」= `argMin(user_id, first_at) GROUP BY device_id`，Task 3 使用。

- [ ] **Step 1：写失败测试** `DeviceUserLinksIT`
  - `links_device_to_user_seen_on_it`：同一 `device_id` 写一条匿名事件、一条 `user_id = 42` 的事件 → `SELECT argMin(user_id, first_at) FROM device_user_links WHERE device_id = …` 为 42。
  - `first_user_wins_when_device_is_shared`：同一设备 10:00 是用户 42、11:00 是用户 43 → 结果 42。
  - `calc_view_matches_mv_for_a_day`：清空表后 `INSERT INTO device_user_links SELECT * FROM device_user_links_calc(from = '<d>', to = '<d>')`，结果与物化视图写入的一致。
- [ ] **Step 2：** `./gradlew :patra-api:patra-analytics:patra-analytics-infra:integrationTest --tests '*DeviceUserLinksIT'` → FAIL（表不存在）。
- [ ] **Step 3：写 `V6__device_user_links.sql`。**
- [ ] **Step 4：** 同一命令 → PASS。
- [ ] **Step 5：清理测试先红**：`deletes_users_everywhere` 补断言「`device_user_links` 里该用户的行没了」，跑 → FAIL。
- [ ] **Step 6：** `deleteUsers` 在删 `daily_person_activity` **之前**加 `DELETE FROM device_user_links WHERE user_id IN (…)`（日活表是找用户的唯一来源，必须最后删）；跑 → PASS。
- [ ] **Step 7：提交** `feat(analytics): 新增设备与账号对应表，用于新增与留存的认回 (PAP-172)`

### Task 2：活跃 `ads_daily_active`

**Files:**
- Create: `…/clickhouse/V7__ads_daily_active.sql`
- Create: `…/infra/src/integrationTest/java/dev/patra/analytics/infra/ads/AdsITFixture.java`
- Test: `…/infra/src/integrationTest/java/dev/patra/analytics/infra/ads/AdsDailyActiveIT.java`

**Interfaces:**
- Produces：表 `ads_daily_active(day Date, is_synthetic Bool, traffic LowCardinality(String), dau UInt64, wau UInt64, mau UInt64, computed_at DateTime)`，键 `(day, is_synthetic, traffic)`；视图 `ads_daily_active_calc(from Date, to Date)`；刷新视图 `ads_daily_active_rmv`。
- Produces（测试夹具，后续 Task 复用）：`AdsITFixture`
  - `static Client migrated()`：委托 `ClickHouseITFixture.migratedDatabase()`。
  - `static ObjectNode at(String eventName, String source, String sessionId, String deviceId, long userId, String beijingTime)`：基于 `ClickHouseITFixture.event`，设 `derived_at` / `created_at` / `received_at` 为该北京时刻，`properties` 为空对象。
  - `static void backfill(Client client, String table, LocalDate from, LocalDate to)`：执行 `INSERT INTO <table> SELECT * FROM <table>_calc(from = '<from>', to = '<to>')`。
  - `static List<Map<String, Object>> rows(Client client, String sql)`。

- [ ] **Step 1：写失败测试** `AdsDailyActiveIT`（手工造 3 天的人：A 每天来、B 只第 1 天来、爬虫 C、无脚本访客 D，另有一个合成的人 S）
  - `dau_wau_mau_count_humans`：`traffic = 'human'` 第 3 天 `dau = 1, wau = 2, mau = 2`。
  - `all_traffic_includes_bots_and_no_script`：`traffic = 'all'` 第 3 天的数比 human 多出 C、D。
  - `synthetic_people_are_separate_rows`：S 只出现在 `is_synthetic = true` 的行。
  - `recomputing_a_day_does_not_double_count`（Review Focus 3）：对同一天 `backfill` 两次，`FINAL` 读出的 `dau` 不变。
- [ ] **Step 2：** 跑 → FAIL。
- [ ] **Step 3：写 `V7__ads_daily_active.sql`**：计算视图对 `[from, to]` 里每一天，读日活表 `[day − 29, day]`，先 `GROUP BY day, person_id, is_synthetic` 算出每人每天是否真人，再用 `uniqExactState` + 窗口 `uniqExactMerge … OVER (ORDER BY day ROWS BETWEEN 6 / 29 PRECEDING AND CURRENT ROW)` 得 WAU / MAU（mini 实测可行，spec §3.1），`traffic` 用 `arrayJoin(['human', 'all'])` 展开、按条件过滤。刷新视图传 `today() - 1, today()`。
- [ ] **Step 4：** 跑 → PASS。
- [ ] **Step 5：提交** `feat(analytics): 新增活跃 ADS 表 (PAP-172)`

### Task 3：留存与新增 `ads_cohort_retention`

**Files:** Create `V8__ads_cohort_retention.sql`；Test `ads/AdsCohortRetentionIT.java`

**Interfaces:**
- Consumes：`device_user_links`（Task 1 的认回规则）。
- Produces：表 `ads_cohort_retention(cohort_day Date, is_synthetic Bool, traffic LowCardinality(String), cohort_size UInt64, d1 Nullable(UInt64), d7 Nullable(UInt64), d30 Nullable(UInt64), computed_at DateTime)`，键 `(cohort_day, is_synthetic, traffic)`；视图 `ads_cohort_retention_calc(from Date, to Date)`（区间是同期群首日）；刷新视图传 `today() - 31, today()`。

- [ ] **Step 1：写失败测试**
  - `classic_day_n_retention`：首日 D 来了 4 人，D+1 来 2 人、D+7 来 1 人、D+3 来 3 人 → `cohort_size = 4, d1 = 2, d7 = 1`（第 3 天的不算进任何 dN）。
  - `stitched_device_is_not_new_again`：设备 X 匿名在 D 首次出现，D+2 登录成用户 42 → 42 不是 D+2 的新增，X 属于 D 的同期群。
  - `future_day_n_is_null_not_zero`（Review Focus 2）：首日 = 今天 − 3 → `d7`、`d30` 为 `NULL`，`d1` 不为 `NULL`。
- [ ] **Step 2：** 跑 → FAIL。
- [ ] **Step 3：写 `V8`**：先把日活表的 `person_id` 映射成认回后的人（`d:` 开头且在 `device_user_links` 里有对应的，换成 `u:<argMin 用户>`），按人 × 天判断真人，再对全部历史求每人首日；`retention(...)` 算 d1 / d7 / d30；首日 + N > `today()` 时输出 `NULL`。
- [ ] **Step 4：** 跑 → PASS。
- [ ] **Step 5：提交** `feat(analytics): 新增留存与新增 ADS 表，含登录前后认回 (PAP-172)`

### Task 4：漏斗、跳出与会话构成 `ads_daily_sessions`

**Files:** Create `V9__ads_daily_sessions.sql`；Test `ads/AdsDailySessionsIT.java`

**Interfaces:**
- Produces：表 `ads_daily_sessions(day Date, is_synthetic Bool, session_kind LowCardinality(String), funnel_level UInt8, single_page Bool, sessions UInt64, computed_at DateTime)`，键 `(day, is_synthetic, session_kind, funnel_level, single_page)`。`session_kind` ∈ `human` / `bot` / `no_script`（三类互斥；面板上「含爬虫」= 三类都算，「不含」= 只算 `human`）。视图 `ads_daily_sessions_calc(from Date, to Date)`。
- 说明：这张表用 `session_kind` 而不是 `traffic`，因为会话是互斥分类、可以相加，会话构成图也要三类分开。看板 SQL 里写 `('${traffic}' = 'all' OR session_kind = 'human')`（Task 10）；会话构成图不加这个条件。

- [ ] **Step 1：写失败测试**
  - `funnel_counts_furthest_ordered_step`：一个会话依次 page_viewed → search_performed → 点检索结果 → article_viewed，没有全文 → `funnel_level = 4`。
  - `search_before_page_view_still_reaches_step_two`：同一会话 `search_performed` 比 `page_viewed` 早 300 ms → `funnel_level ≥ 2`（spec §4.3 的门槛规则）。
  - `pdf_download_counts_as_fulltext`：最后一步是 `pdf_downloaded` → 5。
  - `no_page_view_is_level_zero`：只有服务端事件的会话 → `funnel_level = 0`、`session_kind = 'no_script'`。
  - `single_page_session_is_bounce`：只有一条 `page_viewed` → `single_page = true`。
  - `bot_session_is_bot_kind`：有一条 `is_bot` 事件 → `session_kind = 'bot'`。
  - `session_crossing_midnight_counts_on_start_day`（Review Focus 1）：23:50 开始、00:10 结束 → 只在开始那天有 1 个会话。
- [ ] **Step 2：** 跑 → FAIL。
- [ ] **Step 3：写 `V9`**：计算视图读 `events` 中 `[from, to + 1 天]` 的分区（为了看到跨午夜的后半段），按 `session_id` 聚合，再只保留会话首日落在 `[from, to]` 的会话。
- [ ] **Step 4：** 跑 → PASS。
- [ ] **Step 5：提交** `feat(analytics): 新增漏斗、跳出与会话构成 ADS 表 (PAP-172)`

### Task 5：检索 `ads_daily_search`、`ads_daily_zero_result_terms`

**Files:** Create `V10__ads_search.sql`；Test `ads/AdsSearchIT.java`

**Interfaces:**
- Produces：`ads_daily_search(day Date, is_synthetic Bool, traffic LowCardinality(String), mode LowCardinality(String), searches UInt64, zero_result_searches UInt64, clicked_searches UInt64, computed_at DateTime)`，键 `(day, is_synthetic, traffic, mode)`。
- Produces：`ads_daily_zero_result_terms(day Date, is_synthetic Bool, traffic LowCardinality(String), term String, searches UInt64, people AggregateFunction(uniqExact, String), computed_at DateTime)`，键 `(day, is_synthetic, traffic, term)`，`TTL day + INTERVAL 90 DAY DELETE`。
- 两个计算视图 `…_calc(from Date, to Date)`；`traffic` 按会话真人判定（读 `session_quality`，与 Reconciler 一样看 `first_day` 在 `[from − 1, to + 1]`）。

- [ ] **Step 1：写失败测试**
  - `zero_result_rate_parts`：3 次检索，1 次 `result_count = 0` → `searches = 3, zero_result_searches = 1`。
  - `clicked_search_needs_click_with_same_list_id`：检索 S1 后有 `article_clicked{list_type: search, list_id: S1}`，S2 后的点击 `list_id` 是别的 → `clicked_searches = 1`。
  - `terms_are_normalized`：`"  Covid  Vaccine "` 和 `"covid vaccine"` 合成一个词 `covid vaccine`，`searches = 2`。
  - `exact_lookup_and_empty_query_are_not_terms`：`mode = exact_pmid` 或 `query = ""` 的无结果检索不进词表。
  - `people_merge_across_days`：同一人两天都搜同一个无结果词 → 两天合并的 `uniqExactMerge(people) = 1`。
- [ ] **Step 2：** 跑 → FAIL。
- [ ] **Step 3：写 `V10`**：规范化用 `replaceRegexpAll(lower(trimBoth(q)), '\\s+', ' ')`。
- [ ] **Step 4：** 跑 → PASS。
- [ ] **Step 5：提交** `feat(analytics): 新增检索与无结果词 ADS 表 (PAP-172)`

### Task 6：文献使用 COUNTER `ads_daily_counter_items`

**Files:** Create `V11__ads_daily_counter_items.sql`；Test `ads/AdsCounterItemsIT.java`

**Interfaces:**
- Produces：`ads_daily_counter_items(day Date, article_id UInt64, is_synthetic Bool, traffic LowCardinality(String), unique_investigations UInt64, unique_requests UInt64, total_investigations UInt64, total_requests UInt64, computed_at DateTime)`，键 `(day, is_synthetic, traffic, article_id)`；视图 `ads_daily_counter_items_calc(from Date, to Date)`。
- 说明：Unique 从 `article_session_reads` 算（`viewed` / `fulltext`）；Total 从 `events` 算双击过滤（spec §4.7，mini 实测一天 0.2 秒）。

- [ ] **Step 1：写失败测试**
  - `double_click_within_30s_counts_once`：同一人同一篇 10:01:00、10:01:29 两次 `article_viewed` → `total_investigations = 1`。
  - `clicks_35s_apart_count_twice`：10:01:00、10:01:35 → 2。
  - `unique_counts_once_per_session`：一个会话里看 3 次 → `unique_investigations = 1`；另一个会话再看 → 2。
  - `requests_are_fulltext_or_pdf`：一次 `fulltext_read`、一次 `pdf_downloaded`（相隔 > 30 秒）、一次 `article_collected` → `total_requests = 2`，`total_investigations = 3`。
- [ ] **Step 2：** 跑 → FAIL。
- [ ] **Step 3：写 `V11`**：Total 的过滤用 `leadInFrame(derived_at) OVER (PARTITION BY person_id, article_id ORDER BY derived_at ROWS BETWEEN CURRENT ROW AND 1 FOLLOWING)`，与下一条相隔 > 30 秒或没有下一条才计数（保留后一条）；Requests 只在 `fulltext_read`、`pdf_downloaded` 两类事件之间做同样的过滤。
- [ ] **Step 4：** 跑 → PASS。
- [ ] **Step 5：提交** `feat(analytics): 新增 COUNTER 文献使用 ADS 表 (PAP-172)`

### Task 7：数据质量 `ads_daily_quality`

**Files:** Create `V12__ads_daily_quality.sql`；Test `ads/AdsDailyQualityIT.java`

**Interfaces:**
- Produces：`ads_daily_quality(day Date, is_synthetic Bool, source LowCardinality(String), lateness_bucket LowCardinality(String), events UInt64, lateness_ms AggregateFunction(quantiles(0.5, 0.95, 0.99), Int64), computed_at DateTime)`，键 `(day, is_synthetic, source, lateness_bucket)`。桶的取值固定为 `'clock_ahead'`、`'lt_1s'`、`'1s_10s'`、`'10s_60s'`、`'1m_10m'`、`'10m_60m'`、`'ge_1h'`。
- Produces：`ads_daily_duplicates(day Date, written_rows UInt64, distinct_events UInt64, computed_at DateTime)`，键 `day`（重复率不分合成，spec §4.5）。`written_rows` 取 `system.part_log` 中 `table = 'events' AND event_type = 'NewPart' AND partition_id = toString(toYYYYMMDD(day))` 的行数之和；`part_log` 里已经没有这一天（超过 7 天）时**不输出这一天**，不让旧快照被 0 覆盖。
- 两个计算视图 `…_calc(from Date, to Date)`。

- [ ] **Step 1：写失败测试**
  - `lateness_buckets`：`received_at − created_at` 分别为 −2 s、0.5 s、5 s、2 h → 依次落进 `clock_ahead`、`lt_1s`、`1s_10s`、`ge_1h`。
  - `duplicates_from_part_log`：同一 `event_id` 用两个不同令牌各写一次，另写 2 条不同事件 → `written_rows = 4, distinct_events = 3`。
  - `day_missing_from_part_log_is_not_overwritten`：先插入一行旧快照，再对 `part_log` 里没有的一天调计算视图 → 不输出行，`FINAL` 仍是旧快照。
- [ ] **Step 2：** 跑 → FAIL（若测试容器的 ClickHouse 没开 `part_log`，先确认镜像默认配置；没开就在 `ClickHouseContainerInitializer` 里打开，并在这一步写明）。
- [ ] **Step 3：写 `V12`。**
- [ ] **Step 4：** 跑 → PASS。
- [ ] **Step 5：提交** `feat(analytics): 新增数据质量 ADS 表：迟到分布与重复率 (PAP-172)`

### Task 8：模拟器把对账结果写进 `reconcile_results`

**Files:**
- Create: `…/clickhouse/V13__reconcile_results.sql`
- Modify: `…/simulator/truth/Reconciler.java`、`…/simulator/command/ReconcileCommand.java`
- Create: `…/simulator/truth/DailyReconcileRow.java`
- Test: `…/simulator/src/integrationTest/java/dev/patra/analytics/simulator/truth/ReconcilerIT.java`（加用例）

**Interfaces:**
- Produces：表 `reconcile_results(run UInt16, day Date, delivered UInt64, distinct_events UInt64, viewers_truth UInt64, viewers_ch UInt64, viewers_abs_diff UInt64, fulltext_truth UInt64, fulltext_ch UInt64, fulltext_abs_diff UInt64, checked_at DateTime)`，`ReplacingMergeTree(checked_at) ORDER BY (run, day)`。
- Produces：`record DailyReconcileRow(int run, LocalDate day, long delivered, long distinctEvents, long viewersTruth, long viewersCh, long viewersAbsDiff, long fulltextTruth, long fulltextCh, long fulltextAbsDiff)`，带 `@Builder`（10 个参数，按 code-style 规范用 Builder）。
- Produces：`Reconciler#reconcile(Path truthFile, RunSummary summary)` 的返回不变；新增 `List<DailyReconcileRow> dailyRows()`（最近一次 `reconcile` 的逐天结果）与 `void writeDailyRows(List<DailyReconcileRow> rows)`（JSONEachRow 写 `reconcile_results`）。`ReconcileCommand` 在打印之后调 `writeDailyRows`。
- `delivered` 取 `summary.eventsByDay().get(day)`。

- [ ] **Step 1：写失败测试**（`ReconcilerIT`）
  - `writes_one_row_per_day`：现有的两天样例跑完对账后，`reconcile_results FINAL` 有两行，`delivered`、`distinct_events` 与 `ReconcileResult` 的合计一致。
  - `skips_days_whose_partition_is_gone`（Review Focus 4）：`summary` 里有一天在 `events` 没有活跃分区（查 `system.parts`）→ 不写这一天，也不计入 `ReconcileResult` 的合计。
- [ ] **Step 2：** `./gradlew :patra-api:patra-analytics:patra-analytics-simulator:integrationTest --tests '*ReconcilerIT'` → FAIL。
- [ ] **Step 3：实现。**
- [ ] **Step 4：** 跑 → PASS；`./gradlew :patra-api:patra-analytics:patra-analytics-simulator:test` 也 PASS（`ReconcilerTest` 不应受影响）。
- [ ] **Step 5：提交** `feat(analytics): 模拟器对账结果按天写进 ClickHouse (PAP-172)`

### Task 9：回填脚本

**Files:**
- Create: `patra-infra/clickhouse/backfill-ads.sh`
- Test: `patra-infra/clickhouse/backfill-ads.test.sh`

**Interfaces:**
- 用法：`backfill-ads.sh --from 2026-08-06 --to 2026-11-02 [--dry-run]`。从 `~/.patra/secrets/clickhouse.env` 读 `CLICKHOUSE_PATRA_PASSWORD`，经 `http://127.0.0.1:8123` 以 patra 账号执行；不打印密码。
- 顺序固定：`device_user_links` → `ads_daily_active` → `ads_cohort_retention` → `ads_daily_sessions` → `ads_daily_search` → `ads_daily_zero_result_terms` → `ads_daily_counter_items` → `ads_daily_quality` → `ads_daily_duplicates`；每张表按天从早到晚、一天一条 `INSERT INTO t SELECT * FROM t_calc(from = 'd', to = 'd')`，串行执行，任一条失败立即退出并打印表名与日期。
- 读 `events` 的表（device_user_links、sessions、search、zero_terms、counter、quality、duplicates）在开始时查 `events` 最早的活跃分区，早于它的天跳过（spec §11 第 0 条）；日活表与 `article_session_reads` 部分不跳过。
- 变量一律写 `${VAR}`（bash 3.2 全角字符陷阱）。

- [ ] **Step 1：写失败测试** `backfill-ads.test.sh`：`--dry-run --from 2026-10-01 --to 2026-10-02 --events-floor 2026-10-02`（测试用的参数，代替查询最早分区）输出的 SQL 列表与期望逐行一致：顺序、表名、日期、跳过规则。
- [ ] **Step 2：** `bash patra-infra/clickhouse/backfill-ads.test.sh` → FAIL。
- [ ] **Step 3：写脚本。**
- [ ] **Step 4：** 跑 → PASS；`shellcheck` 无告警（本机有就跑）。
- [ ] **Step 5：提交** `feat(infra): 新增 ADS 回填脚本 (PAP-172)`

### Task 10：Grafana 看板与看板 SQL 的集成测试

**Files:**
- Create: `patra-infra/docker/grafana/provisioning/dashboards/patra/analytics-ops.json`
- Test: `…/infra/src/integrationTest/java/dev/patra/analytics/infra/ads/AnalyticsOpsDashboardIT.java`

**Interfaces:**
- 看板：uid `patra-analytics-ops`，标题「运营分析」，默认时间范围 `now-30d` 到 `now`，时区 `Asia/Shanghai`。数据源 `{"type": "grafana-clickhouse-datasource", "uid": "clickhouse"}`，查询用 `editorType: "sql"`、`rawSql`；时间序列面板的 `queryType` 为 `timeseries`，并把天转成时间列 `toDateTime(day, 'Asia/Shanghai') AS time`。
- 变量：
  - `synthetic`：custom，选项「含」= `0,1`、「不含」= `0`，默认「含」，SQL 里写 `is_synthetic IN (${synthetic:raw})`。
  - `traffic`：custom，选项「不含爬虫」= `human`、「含爬虫」= `all`，默认 `human`，SQL 写 `traffic = '${traffic}'`。
  - 会话表（Task 4）没有 `traffic` 列，SQL 写 `('${traffic}' = 'all' OR session_kind = 'human')`。
  - `mode`：custom 多选，`keyword`、`exact_pmid`、`exact_doi`，默认 `keyword`，SQL 写 `mode IN (${mode:singlequote})`。
  - `portal`：constant，默认 `http://linqibins-mac-mini:4000`，COUNTER 前 20 表的数据链接 `${portal}/papers/${__data.fields.article_id}`。
- 时间过滤一律写 `day BETWEEN toDate($__fromTime, 'Asia/Shanghai') AND toDate($__toTime, 'Asia/Shanghai')`（留存表用 `cohort_day`）。
- 面板与分组按 spec §5.2；每个面板的 `description` 写一句口径加「数字最多晚 1 小时」（数据质量组的四块另写「不受开关影响」的说明，按 spec §5.2 第 7 组）。概览数字卡里「次日留存」取范围内最后一个 `d1 IS NOT NULL` 的同期群。
- 测试：`AnalyticsOpsDashboardIT`
  - 从 `user.dir` 往上找到含 `patra-infra` 的目录，读看板 JSON；遍历所有 `panels[*].targets[*].rawSql`（含折叠行里的 `panels`）。
  - 替换：`$__fromTime` → `toDateTime('2026-10-01 00:00:00', 'Asia/Shanghai')`，`$__toTime` → `toDateTime('2026-10-03 23:59:59', 'Asia/Shanghai')`，`${synthetic:raw}` → `0,1`，`${traffic}` → `human`，`${mode:singlequote}` → `'keyword'`；替换后 SQL 里还剩 `$` 即失败（防止漏掉宏）。
  - 用例：
    - `every_panel_query_runs`：在用 Task 2～7 的夹具灌好的库上，每条 SQL 都执行成功。
    - `reads_only_allowed_tables`：每条 SQL 引用的表都在白名单里（ADS 各表、`dlq_events`、`reconcile_results`）。
    - `synthetic_toggle_changes_numbers`（Review Focus 5）：「DAU」面板在 `0` 与 `0,1` 下结果不同。
    - `dau_panel_matches_ads_table`：DAU 面板最后一天的值等于 `ads_daily_active` 的对应值。

- [ ] **Step 1：写失败测试**（看板文件还不存在）→ 跑 `--tests '*AnalyticsOpsDashboardIT'` → FAIL。
- [ ] **Step 2：写看板 JSON**（先手写一个能出数的最小版本，再按 spec §5.2 补齐所有面板），每补一组跑一次测试。
- [ ] **Step 3：** 跑 → PASS；`python3 -m json.tool` 校验格式。
- [ ] **Step 4：提交** `feat(infra): 新增运营分析 Grafana 看板 (PAP-172)`

### Task 11：文档同步

**Files:**
- Modify: `docs/patra/specs/2026-11-01-analytics-tracking-plan-design.md` §6.1、§6.5、§6.8（按 spec §9 的三条）
- Modify: `docs/patra/specs/2026-11-01-analytics-ingest-design.md` §6（注销清理多删一张 `device_user_links`）、§7（「业务指标面板归 PAP-172」后面补上看板名）
- Modify: `patra-infra/docker/README.md`（Grafana 一节：看板位置、ADS 层一句话、回填脚本用法、看板以仓库文件为准）
- Modify: `patra-api/patra-analytics/README.md`（数据分层补 ADS 层）

- [ ] **Step 1：改上面四处**（纯文档，不走 TDD）。
- [ ] **Step 2：提交** `docs(analytics): 同步埋点方案、入库设计与运维说明里的面板口径 (PAP-172)`

### Task 12：全量门控与开 PR

- [ ] **Step 1：** `./gradlew spotlessApply`，再 `./gradlew check`，然后单独跑 `./gradlew integrationTest`（`check` 不跑集成测试）。全过；高负载下的网关超时类用例失败时单独重跑确认。
- [ ] **Step 2：** `./gradlew dumpModuleGraph`，`git diff --stat patra-infra/cd/module-graph.json` 应无变化；`bash patra-infra/cd/detect-changes.test.sh` 全过。
- [ ] **Step 3：** 停下来，向编排会话汇报，等转达用户同意后再推送、开 PR（draft → 转 ready），并用 ccd_pr 绑定、开 auto_fix。PR 描述逐行写 `Closes PAP-172`。

### Task 13：mini 上线与验收（每一步都等用户同意）

- [ ] **Step 1：** PR 合并、CD 部署 analytics 后，确认 `schema_migrations` 到 `V13`，`system.view_refreshes` 里 7 个刷新视图状态为 `Scheduled` 且没有 `exception`。
- [ ] **Step 2：** 在 mini 上跑 `backfill-ads.sh --from 2026-08-06 --to <今天>`，记录耗时与 ClickHouse 峰值内存（`system.query_log`）。
- [ ] **Step 3：** 对 run 1、run 4 重跑 `reconcile`（mini 上的 `~/patra-simulator/simulator-out`），确认 `reconcile_results` 有行。
- [ ] **Step 4：** 往采集端点发一小批不合格的事件（spec §8 第 4 步），确认 dlq 面板出图。
- [ ] **Step 5：** 打开看板，逐组截图（Playwright 无头截图，内置浏览器打不开 tailscale IP），切换「含合成数据」后截一次概览，确认数字变了。
- [ ] **Step 6：** 重建 Grafana 容器（`docker compose … up -d --force-recreate grafana`），看板还在。
- [ ] **Step 7：** 回到 Linear 勾 PAP-172 的 To-Do 与 AC，在 PR 评论里贴截图。
