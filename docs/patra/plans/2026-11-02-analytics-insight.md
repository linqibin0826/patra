# Analytics Insight（计数、热读、看过还看了）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `patra-analytics` 新增 `insight` 子域：批量计数（ClickHouse 汇总 + Redis 60 秒缓存）、每小时热读榜与每天共现推荐（ClickHouse 明细算、PG 快照存），三个公开接口；catalog 补齐撤稿标记、最新收录、同主题邻居三个内部能力。

**Architecture:** 计数走 `ReaderStatsPort`（ClickHouse 适配器 + Redis cache-aside 装饰器）；两个 XXL-Job 用例从 `ReadingSignalPort`（ClickHouse `events`）取候选、经 `PublicationCatalogPort` 向 catalog 核对与补位，再一个事务整表替换 PG 快照；三个查询服务只读 PG（看过还看了不足 6 篇时请求时向 catalog 要邻居，缓存 6 小时）。

**Tech Stack:** Java 25、Spring Boot 4、ClickHouse client-v2、Spring Data Redis（`StringRedisTemplate`）、`JdbcClient`、Flyway、HTTP Interface（`patra-tool-http-client`）、XXL-Job、JUnit 5 + AssertJ + Mockito、Testcontainers（ClickHouse、PG、Redis、Kafka）。

**Spec:** [`docs/patra/specs/2026-11-02-analytics-insight-design.md`](../specs/2026-11-02-analytics-insight-design.md)（定稿）

## Global Constraints

- 包、命名照 `.claude/rules/tech/package-layout.md`、`port-service.md`；所有方法写 `///` JavaDoc；不写全类名；record 参数 ≤ 4 用 `of()`、≥ 5 用 `@Builder`；集合字段紧凑构造器 `List.copyOf`。
- 「天」一律北京时间 `Asia/Shanghai`；「今天」由注入的 `Clock` 算出，作为查询参数传入。
- 查看类事件：`article_viewed`、`fulltext_read`、`pdf_downloaded`、`article_collected`；全文：`fulltext_read`、`pdf_downloaded`。
- 真人会话：`session_quality` 里 `max(has_client) = 1 AND max(has_bot) = 0`。
- `patra.analytics.include-synthetic` 默认 `false`；`false` 时所有 ClickHouse 查询加 `is_synthetic = 0`；Redis 键段 `real` / `all`。
- 热读：窗口 `[T-13, T]`，门槛 `[T-6, T]` 不同人数 ≥ 3，全文权重 3，半衰期 3 天，候选取 150，榜单 100，页面 14，补位向 catalog 取 28。
- 共现：窗口 `[T-13, T]`，每组前 20 篇（按当天首条事件时间），`co ≥ 3`，相似度 `co / √(n(X)·n(Y))`，候选每篇 25，留 20，分片 `patra.analytics.insight.co-reading-shards` 默认 8。
- 看过还看了：最多 6 篇，向 catalog 固定取 11 个邻居，邻居缓存 `analytics:insight:neighbors:{articleId}` 6 小时；计数缓存 `analytics:insight:stats:{real|all}:{articleId}` 值 `"{viewers}:{fulltext}"` TTL 60 秒。
- 对外 JSON：ID 是字符串；校验失败 422；ClickHouse / PG 不可用 503（`InsightUnavailableException` → `http.UNAVAILABLE()`）。
- 测试命名 snake_case；单测 `src/test`，集成测试 `src/integrationTest`，`*IT`。
- 每个 Task 末尾的 Commit 步骤：**不执行**，改为 `git add` 本 Task 的路径并在 Task 列表里记下待提交范围；提交要等编排会话转达用户同意（任务约束）。
- commit subject 用中文动词开头（commitlint），body 每行 ≤ 100 字符。
- 改了 `*.gradle.kts` 后跑 `./gradlew dumpModuleGraph`，结束时提醒用户在 IDEA 点「加载 Gradle 更改」。

## Review Focus

1. **同一个人在一个会话里有服务端浏览、却没有任何浏览器事件**（门户脚本被拦）：三类数字都不计入。→ Task 6、Task 7 的 IT 各有 `server_only_session_is_not_counted`。
2. **请求里有重复 ID、超大 ID（> 2^53）**：去重后按请求顺序一行一个，ID 原样以字符串回显。→ Task 10 `stats_dedupes_and_keeps_big_ids_as_strings`。
3. **快照表正在被替换时有页面请求**：读到的是完整的旧快照或完整的新快照。→ Task 5 `reader_sees_old_snapshot_until_replace_commits`。
4. **catalog 在热读任务中途不可用**：任务失败，旧快照原样保留。→ Task 9 `hot_job_keeps_previous_snapshot_when_catalog_fails`。
5. **事件跨午夜**（北京时间 23:59 与 00:01 两条）：落在两天，热读里算两行、共现里是两组。→ Task 7 `days_are_cut_at_beijing_midnight`。

---

### Task 1: catalog `resolve/by-ids` 加 `retracted`

**Files:**
- Modify: `patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/publication/persistence/dao/PublicationResolveDao.java`（`SELECT` 加列）
- Modify: `.../infra/publication/persistence/PublicationResolveRow.java`（`Boolean getRetracted()`）、`PublicationResolveReadAdapter.java`
- Modify: `patra-catalog-domain/.../domain/publication/model/read/PublicationResolveReadModel.java`（加 `boolean retracted`）
- Modify: `patra-catalog-api/.../api/publication/dto/PublicationResolutionResp.java`（加 `boolean retracted`，`missing()` 为 `false`）
- Modify: `patra-catalog-adapter/.../adapter/publication/internal/PublicationEndpointImpl.java`
- Test: `patra-catalog-infra/src/integrationTest/.../publication/persistence/PublicationResolveReadAdapterIT.java`、`patra-catalog-adapter/src/integrationTest/.../publication/internal/PublicationEndpointIT.java`

**Interfaces:**
- Produces: `PublicationResolutionResp.retracted()`（JSON 字段 `retracted`）。

- [ ] **Step 1: 写失败的测试** — `PublicationResolveReadAdapterIT.should_flag_retracted_by_publication_type_or_fulltext_reason`：三篇文献，一篇带 `cat_publication_type.type_id = 'D016441'`，一篇 `cat_publication_fulltext.unavailable_reason = 'RETRACTED'`，一篇正常；断言 `retracted()` 依次 `true, true, false`；另加一篇带 `D016440`（撤稿声明）断言 `false`。`PublicationEndpointIT` 现有用例里断言 `first.get("retracted").asBoolean()` 为模型值、`missing.get("retracted").asBoolean()` 为 `false`。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*PublicationResolveReadAdapterIT*"`，预期编译失败（`retracted()` 不存在）。
- [ ] **Step 3: 实现** — DAO `SELECT` 加 `(EXISTS (SELECT 1 FROM cat_publication_type rt WHERE rt.publication_id = p.id AND rt.type_id = 'D016441') OR EXISTS (SELECT 1 FROM cat_publication_fulltext f WHERE f.publication_id = p.id AND f.unavailable_reason = 'RETRACTED')) AS "retracted"`；一路透传到响应。
- [ ] **Step 4: 跑测试确认通过** — 上面命令 + `./gradlew :patra-api:patra-catalog:patra-catalog-adapter:integrationTest --tests "*PublicationEndpointIT*"`，PASS。
- [ ] **Step 5: 暂存** — `git add` 上述文件。

### Task 2: catalog 最新收录与同主题邻居的读端口

**Files:**
- Create: `patra-catalog-domain/.../domain/publication/port/read/PublicationNeighborReadPort.java`
- Create: `patra-catalog-domain/.../domain/publication/model/read/SubjectNeighborReadModel.java`
- Create: `patra-catalog-domain/.../domain/publication/model/enums/SubjectNeighborReason.java`（`SAME_MESH`、`SAME_VENUE`）
- Create: `patra-catalog-infra/.../infra/publication/persistence/PublicationNeighborReadAdapter.java`（`JdbcClient`）
- Create: `patra-catalog-infra/src/main/resources/db/migration/V14__add_subject_neighbor_indexes.sql`
- Test: `patra-catalog-infra/src/integrationTest/.../publication/persistence/PublicationNeighborReadAdapterIT.java`

**Interfaces:**
- Produces:
  - `List<Long> PublicationNeighborReadPort.findLatestIds(int limit)` — 主键倒序，去掉软删与撤稿。
  - `List<SubjectNeighborReadModel> PublicationNeighborReadPort.findSubjectNeighbors(long publicationId, int limit)`。
  - `record SubjectNeighborReadModel(long id, SubjectNeighborReason reason, String meshDescriptorUi, String meshTerm, String venueName)`（5 个参数，`@Builder`）。

- [ ] **Step 1: 写失败的测试**（`@DataJpaTest` 照 `PublicationResolveReadAdapterIT` 的写法，`@Import(PublicationNeighborReadAdapter.class)`），用例：
  - `latest_ids_are_newest_first_without_deleted_or_retracted`：4 篇（主键 10、20、30、40；30 软删、40 撤稿）→ `findLatestIds(10)` 等于 `[20, 10]`。
  - `neighbors_round_robin_across_major_descriptors`：X 的主要主题词 A、B（`heading_order` 1、2）；A 下有 101、102、103，B 下有 201、202 → `findSubjectNeighbors(X, 4)` 的 id 依次 `[103, 202, 102, 201]`，`reason` 全 `SAME_MESH`，`meshTerm` 是 `cat_mesh_descriptor.name`。
  - `major_on_qualifier_counts_on_both_sides`：X 的主题词 C 本身 N、其副主题词 Y；另一篇 301 的 C 本身 N、副主题词 Y → 301 出现；302 的 C 本身 N、副主题词 N → 不出现。
  - `venue_fills_after_mesh_and_skips_duplicates`：X 期刊 V（`cat_venue.title = "Chest"`）；V 下有 401、402，其中 402 也在 A 下 → `findSubjectNeighbors(X, 10)` 里 402 只出现一次且为 `SAME_MESH`，401 为 `SAME_VENUE`、`venueName = "Chest"`、`meshTerm` 为 null。
  - `excludes_self_deleted_and_retracted`；`missing_or_deleted_publication_has_no_neighbors`（不存在的 ID、软删的 X → 空列表）。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*PublicationNeighborReadAdapterIT*"`，编译失败。
- [ ] **Step 3: 实现** — V14：

```sql
-- 同主题邻居（analytics PAP-170）：按主题词 / 期刊取最新收录的几篇，靠索引倒序直接取前几行
CREATE INDEX idx_mesh_heading_descriptor_pub ON cat_publication_mesh_heading (descriptor_ui, publication_id DESC);
CREATE INDEX idx_pub_venue_id_live ON cat_publication (venue_id, id DESC) WHERE deleted_at IS NULL;
```

  适配器 SQL 骨架（「可用」= `deleted_at IS NULL` 且不撤稿，撤稿判定同 Task 1；major = 主题词 major 或任一副主题词 major）：

```sql
WITH x AS (SELECT id, venue_id FROM cat_publication WHERE id = :id AND deleted_at IS NULL),
majors AS (  -- X 的主要主题词，d_rank 为轮流的次序
  SELECT h.descriptor_ui, row_number() OVER (ORDER BY h.heading_order NULLS LAST, h.descriptor_ui) AS d_rank
  FROM cat_publication_mesh_heading h JOIN x ON h.publication_id = x.id
  WHERE <major(h)>),
mesh AS (    -- 每个词各取 :limit 篇，rn 为词内名次
  SELECT c.publication_id AS id, m.descriptor_ui, m.d_rank,
         row_number() OVER (PARTITION BY m.descriptor_ui ORDER BY c.publication_id DESC) AS rn
  FROM majors m CROSS JOIN LATERAL (
    SELECT h2.publication_id FROM cat_publication_mesh_heading h2
    WHERE h2.descriptor_ui = m.descriptor_ui AND h2.publication_id <> (SELECT id FROM x)
      AND <major(h2)> AND <可用(h2.publication_id)>
    ORDER BY h2.publication_id DESC LIMIT :limit) c),
mesh_first AS (SELECT DISTINCT ON (id) id, descriptor_ui, d_rank, rn FROM mesh ORDER BY id, rn, d_rank),
venue AS (
  SELECT p.id, row_number() OVER (ORDER BY p.id DESC) AS rn FROM x CROSS JOIN LATERAL (
    SELECT p.id FROM cat_publication p
    WHERE p.venue_id = x.venue_id AND p.deleted_at IS NULL AND p.id <> x.id
      AND p.id NOT IN (SELECT id FROM mesh_first) AND <不撤稿(p.id)>
    ORDER BY p.id DESC LIMIT :limit) p)
SELECT * FROM (
  SELECT id, 'SAME_MESH' AS reason, descriptor_ui, d.name AS mesh_term, NULL AS venue_name, 0 AS grp, rn, d_rank
  FROM mesh_first JOIN cat_mesh_descriptor d ON d.ui = mesh_first.descriptor_ui
  UNION ALL
  SELECT venue.id, 'SAME_VENUE', NULL, NULL, v.title, 1, venue.rn, 0
  FROM venue JOIN x ON true JOIN cat_venue v ON v.id = x.venue_id) u
ORDER BY grp, rn, d_rank LIMIT :limit
```

- [ ] **Step 4: 跑测试确认通过** — 同 Step 2 命令，PASS。
- [ ] **Step 5: 暂存。**

### Task 3: catalog 两个新内部接口

**Files:**
- Modify: `patra-catalog-api/.../api/publication/endpoint/PublicationEndpoint.java`
- Create: `patra-catalog-api/.../api/publication/dto/PublicationLatestResp.java`、`SubjectNeighborResp.java`、`SubjectNeighborsResp.java`、`SubjectNeighborReasonResp.java`
- Create: `patra-catalog-app/.../app/publication/usecase/query/PublicationNeighborQueryService.java`
- Modify: `patra-catalog-adapter/.../adapter/publication/internal/PublicationEndpointImpl.java`（类上加 `@Validated`）
- Test: `PublicationEndpointIT.java`（同上）

**Interfaces:**
- Consumes: Task 2 的 `PublicationNeighborReadPort`。
- Produces（analytics 的 HTTP Interface 代理直接用）：
  - `@GetExchange("/latest") PublicationLatestResp latest(@RequestParam("limit") @Min(1) @Max(100) int limit)`；`record PublicationLatestResp(List<Long> ids)`。
  - `@GetExchange("/{id}/subject-neighbors") SubjectNeighborsResp subjectNeighbors(@PathVariable("id") @Positive long id, @RequestParam("limit") @Min(1) @Max(20) int limit)`；`record SubjectNeighborsResp(List<SubjectNeighborResp> items)`；`@Builder record SubjectNeighborResp(Long id, SubjectNeighborReasonResp reason, String meshDescriptorUi, String meshTerm, String venueName)`。
  - `PublicationNeighborQueryService.latest(int limit)`、`subjectNeighbors(long id, int limit)`（`@Transactional(readOnly = true)`，直接委托端口）。

- [ ] **Step 1: 写失败的测试** — `PublicationEndpointIT`：`latest_returns_ids_as_strings`（服务返回 `[9007199254740993, 1]` → `ids[0].isString()` 且原样）；`latest_rejects_limit_out_of_range`（`limit=0`、`101` → 422）；`subject_neighbors_maps_reason_and_labels`（一行 `SAME_MESH` 带 `meshTerm`、一行 `SAME_VENUE` 带 `venueName`，null 字段存在且为 null）；`subject_neighbors_rejects_bad_id_or_limit`（`id=0`、`limit=21` → 422）。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-catalog:patra-catalog-adapter:integrationTest --tests "*PublicationEndpointIT*"`。
- [ ] **Step 3: 实现**（端点契约的 JavaDoc 同步更新顶部的端点列表）。
- [ ] **Step 4: 跑测试确认通过；再跑 `./gradlew :patra-api:patra-catalog:check`。**
- [ ] **Step 5: 暂存。**

### Task 4: insight 领域层（规则、值对象、端口）+ 子域登记

**Files:**
- Create（`patra-analytics-domain/src/main/java/dev/patra/analytics/domain/insight/`）：`package-info.java`；`model/vo/ReaderStats.java`、`HotReadingCandidate.java`、`HotReadingEntry.java`、`HotReadingSummary.java`、`CoReadingPair.java`、`CoReadingEntry.java`、`AlsoReadItem.java`；`model/enums/AlsoReadSource.java`；`policy/HotReadingRule.java`、`CoReadingRule.java`、`AlsoReadRule.java`；`exception/InsightUnavailableException.java`；`port/stats/ReaderStatsPort.java`、`port/signal/ReadingSignalPort.java`、`port/catalog/PublicationCatalogPort.java`、`port/repository/HotReadingSnapshotRepository.java`、`CoReadingSnapshotRepository.java`、`port/read/HotReadingReadPort.java`、`CoReadingReadPort.java`
- Create: app / infra / adapter 三层的 `insight/package-info.java`
- Modify: `patra-analytics-boot/src/test/java/dev/patra/analytics/architecture/AnalyticsArchitectureTest.java`（`SUBDOMAINS` 加 `"insight"`，注释写 PAP-170）
- Test: `patra-analytics-domain/src/test/java/dev/patra/analytics/domain/insight/policy/HotReadingRuleTest.java`、`CoReadingRuleTest.java`、`AlsoReadRuleTest.java`

**Interfaces:**
- Produces（后面所有 Task 用这些名字）：
  - `record ReaderStats(long articleId, long viewers, long fulltextReaders)`：`of(...)`、`none(long articleId)`。
  - `record HotReadingCandidate(long articleId, double score, int readers7d)`：`of`。
  - `@Builder record HotReadingEntry(int position, long articleId, boolean ranked, Double score, Integer readers7d)`。
  - `record HotReadingSummary(Instant computedAt, int rankedTotal, List<HotReadingEntry> items)`：`of`、`empty()`（`null, 0, []`）。
  - `record CoReadingPair(long articleId, long relatedArticleId, double similarity, int coReaders)`：`of`。
  - `@Builder record CoReadingEntry(long articleId, int position, long relatedArticleId, double similarity, int coReaders)`。
  - `enum AlsoReadSource { CO_READ, SAME_MESH, SAME_VENUE }`；`record AlsoReadItem(long articleId, AlsoReadSource source, String meshTerm, String venueName)`：`coRead(long)`、`of(...)`。
  - `HotReadingRule`：常量 `WINDOW_DAYS = 14`、`ELIGIBLE_DAYS = 7`、`MIN_READERS = 3`、`FULLTEXT_WEIGHT = 3`、`HALF_LIFE_DAYS = 3`、`CANDIDATE_LIMIT = 150`、`BOARD_SIZE = 100`、`PAGE_SIZE = 14`、`LATEST_FETCH = 28`；`static List<HotReadingEntry> board(List<HotReadingCandidate> eligibleInOrder, List<Long> latestIds)`。
  - `CoReadingRule`：`WINDOW_DAYS = 14`、`GROUP_LIMIT = 20`、`MIN_CO_READERS = 3`、`CANDIDATE_LIMIT = 25`、`KEEP = 20`；`static List<CoReadingEntry> keep(List<CoReadingPair> candidates, Set<Long> eligible)`（输入按 `articleId` 分组、组内已按名次排好）。
  - `AlsoReadRule`：`DISPLAY = 6`、`NEIGHBOR_FETCH = 11`；`static List<AlsoReadItem> assemble(List<Long> coRead, List<AlsoReadItem> neighbors)`。
  - `InsightUnavailableException(String message, Throwable cause)`，trait `DEP_UNAVAILABLE`。
  - `ReaderStatsPort.statsOf(Collection<Long> articleIds): Map<Long, ReaderStats>`（只含有数据的；ClickHouse 不可用抛 `InsightUnavailableException`）。
  - `ReadingSignalPort.hotCandidates(LocalDate today, int limit): List<HotReadingCandidate>`；`coReadingPairs(LocalDate today, int perArticle): List<CoReadingPair>`。
  - `PublicationCatalogPort.eligibleAmong(Collection<Long> ids): Set<Long>`（适配器内部按 100 分批）；`latestIds(int limit): List<Long>`；`subjectNeighbors(long articleId, int limit): List<AlsoReadItem>`；失败一律抛 `InsightUnavailableException`。
  - `HotReadingSnapshotRepository.replace(List<HotReadingEntry> entries, Instant computedAt)`；`CoReadingSnapshotRepository.replace(List<CoReadingEntry> entries, Instant computedAt)`（都要求在事务里）。
  - `HotReadingReadPort.summary(int limit): HotReadingSummary`；`rankedAmong(Collection<Long> ids): Set<Long>`。
  - `CoReadingReadPort.relatedOf(long articleId, int limit): List<Long>`（按 `position`）。

- [ ] **Step 1: 写失败的测试**
  - `HotReadingRuleTest.board_without_backfill_when_ranked_fills_page`：20 个候选、`latestIds` 任意 → 结果 20 行全 `ranked`、`position` 1..20（板子保留全部 ≤ 100 名，页面截 14 由读端做）。
  - `board_caps_at_100_ranked`：150 个候选 → 100 行。
  - `board_backfills_to_page_size_skipping_ranked`：候选 `[5, 6, 7, 8]`，`latestIds = [9, 6, 10, 11, …]`（至少 12 个）→ 共 14 行，第 5 行起 `ranked = false`、`score`/`readers7d` 为 null，且不含 6。
  - `board_backfill_takes_what_is_available`：候选 2 个、latest 3 个（不重复）→ 5 行。
  - `CoReadingRuleTest.keep_drops_ineligible_and_renumbers`：X 的候选 `[A, B, C]`，`B` 不合格 → `[A(1), C(2)]`；`X` 自身不合格 → X 的行全部去掉。`keep_caps_at_20`：25 个 → 20。
  - `AlsoReadRuleTest.full_co_read_has_no_backfill`（7 个 → 前 6 个 `CO_READ`）；`backfills_after_co_read_without_duplicates`（co `[1, 2, 3]`、邻居 `[2(MESH), 4(MESH), 5(VENUE), 6(VENUE), 7]` → `[1, 2, 3, 4, 5, 6]`，2 保持 `CO_READ`）；`no_co_read_uses_neighbors_only`；`empty_when_nothing`。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-analytics:patra-analytics-domain:test --tests "*insight*"`。
- [ ] **Step 3: 实现**（端口只有接口；规则里只放上面三个静态方法与常量）。
- [ ] **Step 4: 跑测试确认通过；`./gradlew :patra-api:patra-analytics:patra-analytics-domain:check :patra-api:patra-analytics:patra-analytics-boot:test --tests "*AnalyticsArchitectureTest*"` 通过。**
- [ ] **Step 5: 暂存。**

### Task 5: PG 快照表与读写适配器

**Files:**
- Create: `patra-analytics-infra/src/main/resources/db/migration/insight/V20261102120000__create_insight_schema.sql`（spec §7 原文）
- Create: `patra-analytics-infra/src/main/java/dev/patra/analytics/infra/insight/persistence/HotReadingSnapshotRepositoryAdapter.java`、`CoReadingSnapshotRepositoryAdapter.java`、`HotReadingReadAdapter.java`、`CoReadingReadAdapter.java`
- Test: `patra-analytics-infra/src/integrationTest/java/dev/patra/analytics/infra/insight/persistence/InsightPersistenceITBootstrap.java`、`InsightPersistenceITSupport.java`（照 history 的两个类，`TRUNCATE insight.hot_reading, insight.co_reading, insight.snapshot_run`）、`HotReadingSnapshotIT.java`、`CoReadingSnapshotIT.java`

**Interfaces:**
- Consumes: Task 4 的两个 Repository 与两个 ReadPort。
- Produces: 四个 `@Component` 适配器；`replace` 标 `@Transactional(propagation = MANDATORY)`；每 5,000 行一条 `INSERT … SELECT * FROM unnest(:a::bigint[], :b::smallint[], …)`；`snapshot_run` 用 `INSERT … ON CONFLICT (kind) DO UPDATE`，`kind` 取 `HOT_READING` / `CO_READING`；替换时更新 gauge `analytics.insight.snapshot.rows{kind}`（注入 `MeterRegistry`）。

- [ ] **Step 1: 写失败的测试**
  - `HotReadingSnapshotIT.replace_swaps_whole_table`：先写 3 行，再 `replace` 2 行 → `summary(14).items()` 只有新 2 行、按 `position`；`computedAt` 为第二次的时间；`rankedTotal` = `ranked = true` 的行数。
  - `summary_is_empty_before_first_run`：→ `HotReadingSummary.empty()`。
  - `summary_limits_items_but_counts_all_ranked`：100 个上榜 → `summary(14)` 14 行、`rankedTotal = 100`。
  - `ranked_among_ignores_backfill`：上榜 `[1, 2]`、补位 `[3]` → `rankedAmong([1, 3, 4]) = {1}`。
  - `reader_sees_old_snapshot_until_replace_commits`：开一个事务 `replace` 但不提交（`TransactionTemplate` 里用 `CountDownLatch` 卡住），另一个线程 `summary(14)` 读到旧行；提交后读到新行。
  - `CoReadingSnapshotIT.replace_and_read_in_position_order`（含 12,000 行，覆盖分块插入）；`related_of_limits`（20 行 → `relatedOf(x, 6)` 6 个）。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-analytics:patra-analytics-infra:integrationTest --tests "*insight.persistence*"`。
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 暂存。**

### Task 6: ClickHouse 计数适配器

**Files:**
- Create: `patra-analytics-infra/src/main/java/dev/patra/analytics/infra/insight/config/InsightProperties.java`（`@ConfigurationProperties("patra.analytics")`：`boolean includeSynthetic`，嵌套 `Insight insight` 内 `int coReadingShards` 默认 8）、`InsightInfraConfiguration.java`（`@EnableConfigurationProperties`；装配 Task 6、8 的适配器与装饰器，装饰器 `@Primary`）
- Create: `.../infra/insight/stats/ClickHouseReaderStatsAdapter.java`
- Test: `patra-analytics-infra/src/integrationTest/java/dev/patra/analytics/infra/insight/stats/ClickHouseReaderStatsAdapterIT.java`（用 `ClickHouseITFixture.migratedDatabase()`、`event(...)`、`pageViewIn(...)`、`insert(...)`）

**Interfaces:**
- Produces: `new ClickHouseReaderStatsAdapter(Client client, InsightProperties properties)` 实现 `ReaderStatsPort`；SQL 为 spec §4.2，查询设置 `max_execution_time = 3`；客户端异常包成 `InsightUnavailableException`。

- [ ] **Step 1: 写失败的测试**（每个用例给事件配一条同会话的 `pageViewIn`，除非用例本身测它的缺失）：
  - `same_person_counts_once`：同一人同一篇 3 次 `article_viewed`（两个会话）→ `viewers = 1`、`fulltextReaders = 0`。
  - `fulltext_counts_in_both`：一人 `article_viewed` + `fulltext_read`、另一人只 `pdf_downloaded` → `viewers = 2`、`fulltextReaders = 2`。
  - `server_only_session_is_not_counted`（无 `pageViewIn`）→ 结果里没有这篇。
  - `bot_session_is_not_counted`：会话里另有一条 `is_bot = true` 的事件 → 不计。
  - `synthetic_excluded_unless_enabled`：`is_synthetic = true` 的读者 → 默认不计；`includeSynthetic = true` 的适配器 → 计入。
  - `unknown_articles_are_absent`；`clickhouse_down_throws_unavailable`（客户端指向关掉的端口 → `InsightUnavailableException`）。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-analytics:patra-analytics-infra:integrationTest --tests "*ClickHouseReaderStatsAdapterIT*"`。
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 暂存。**

### Task 7: ClickHouse 热读候选与共现配对

**Files:**
- Create: `.../infra/insight/signal/ClickHouseReadingSignalAdapter.java`
- Test: `patra-analytics-infra/src/integrationTest/java/dev/patra/analytics/infra/insight/signal/ClickHouseReadingSignalAdapterIT.java`

**Interfaces:**
- Consumes: Task 6 的 `InsightProperties`。
- Produces: `new ClickHouseReadingSignalAdapter(Client client, InsightProperties properties)` 实现 `ReadingSignalPort`。`coReadingPairs` 按 `0..shards-1` 逐片查询后拼接，结果按 `articleId`、名次排好。

热读 SQL（`{synthetic}` 关开关时为 `AND is_synthetic = 0`；`derived_at` 另加 UTC 区间以便分区裁剪）：

```sql
WITH real_sessions AS (
    SELECT session_id FROM session_quality WHERE first_day >= {from:Date} - 1
    GROUP BY session_id HAVING max(has_client) = 1 AND max(has_bot) = 0),
reads AS (
    SELECT article_id, person_id, toDate(derived_at, 'Asia/Shanghai') AS day,
           max(event_name IN ('fulltext_read', 'pdf_downloaded')) AS fulltext
    FROM events
    WHERE event_name IN ('article_viewed', 'fulltext_read', 'pdf_downloaded', 'article_collected')
      AND derived_at >= {fromInstant:DateTime64(3)} AND derived_at < {toInstant:DateTime64(3)}
      AND article_id != 0 {synthetic} AND session_id IN real_sessions
    GROUP BY article_id, person_id, day)
SELECT article_id,
       sum(if(fulltext, 3, 1) * pow(0.5, dateDiff('day', day, {today:Date}) / 3)) AS score,
       uniqExactIf(person_id, day >= {today:Date} - 6) AS readers_7d
FROM reads GROUP BY article_id HAVING readers_7d >= 3
ORDER BY score DESC, readers_7d DESC, article_id ASC LIMIT {limit:UInt32}
```

共现 SQL（每片一条，`reads` 同上但取 `min(derived_at) AS first_at` 并按 `person_id, day, article_id` 分组）：

```sql
baskets AS (
    SELECT person_id,
           arraySlice(arrayMap(t -> t.2, arraySort(groupArray((first_at, article_id)))), 1, 20) AS items
    FROM reads GROUP BY person_id, day),
readers AS (SELECT a AS article_id, uniqExact(person_id) AS n FROM baskets ARRAY JOIN items AS a GROUP BY a),
pairs AS (
    SELECT p.1 AS x, p.2 AS y, uniqExact(person_id) AS co FROM baskets
    ARRAY JOIN arrayFlatten(arrayMap(a -> arrayMap(b -> (a, b), arrayFilter(b -> b != a, items)),
               arrayFilter(a -> cityHash64(a) % {shards:UInt16} = {shard:UInt16}, items))) AS p
    GROUP BY x, y HAVING co >= 3)
SELECT x, y, co, co / sqrt(rx.n * ry.n) AS similarity
FROM pairs JOIN readers AS rx ON rx.article_id = x JOIN readers AS ry ON ry.article_id = y
ORDER BY x, similarity DESC, co DESC, y ASC
LIMIT {perArticle:UInt32} BY x
```

- [ ] **Step 1: 写失败的测试**（固定 `today = 2026-11-02`；事件时间用北京时间构造）：
  - `three_readers_put_article_on_board`、`two_readers_do_not`（AC：热读）。
  - `fulltext_weight_and_half_life`：A 今天 3 人看摘要、B 6 天前 4 人读全文（B 另需近 7 天 ≥ 3 人：让这 4 人在 T-6）→ 两篇 `score` 都等于 3.0（误差 1e-9）。
  - `same_person_same_day_counts_once`：一人一天读 3 次 → 该篇分数只算 1 行。
  - `reads_outside_14_days_are_ignored`：T-14 的阅读不计；`eligibility_uses_last_7_days`：3 人都在 T-7 读 → 不上榜。
  - `days_are_cut_at_beijing_midnight`：同一人北京时间 23:59 与次日 00:01 各读一次 → 两行（分数 = 两个衰减系数之和）。
  - `three_people_same_day_make_a_pair`、`two_people_do_not`、`same_people_different_days_do_not`（AC：共现）；结果双向（X→Y、Y→X 都有）。
  - `basket_keeps_first_20_reads_of_the_day`：一人一天读 25 篇，第 21～25 篇（按时间）不出现在任何配对里。
  - `cosine_demotes_popular_article`：照 spec §2.4 例子缩小规模构造（X 4 读者；Y 4 读者其中 3 人同日读 X；Z 30 读者其中 4 人同日读 X）→ X 的结果里 Y 在 Z 前。
  - `server_only_and_bot_sessions_are_ignored`、`synthetic_excluded_unless_enabled`（两种信号各测一次）。
  - `pairs_are_complete_across_shards`：`coReadingShards = 3` 与 `= 1` 的适配器结果相同。
  - **一致性（决策 G）**：`hot_readers_match_reader_stats`：同一批事件，`hotCandidates` 的 `readers7d` 等于 Task 6 适配器的 `viewers`（所有阅读都在近 7 天）。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-analytics:patra-analytics-infra:integrationTest --tests "*ClickHouseReadingSignalAdapterIT*"`。
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 暂存。**

### Task 8: Redis 计数缓存、catalog 适配器与邻居缓存

**Files:**
- Create: `.../infra/insight/stats/CachingReaderStatsDecorator.java`
- Create: `.../infra/insight/catalog/PublicationCatalogAdapter.java`、`CachingPublicationNeighborsDecorator.java`
- Modify: `patra-analytics-infra/build.gradle.kts`（`implementation(project(":patra-api:patra-catalog:patra-catalog-api"))`）
- Create: `patra-analytics-boot/src/main/java/dev/patra/analytics/config/CatalogClientConfiguration.java`（照 `IdentityClientConfiguration`，分组 `catalog`，`lb://patra-catalog`，产出 `PublicationEndpoint` 代理）
- Modify: `patra-analytics-boot/src/main/resources/application.yml`（`groups.catalog`：`base-url: lb://patra-catalog`、`connect-timeout: 1s`、`read-timeout: 2s`）
- Test: `patra-analytics-infra/src/integrationTest/java/dev/patra/analytics/infra/insight/stats/CachingReaderStatsDecoratorIT.java`（`RedisContainerInitializer`）、`.../insight/catalog/CachingPublicationNeighborsDecoratorIT.java`；`patra-analytics-infra/src/test/java/dev/patra/analytics/infra/insight/catalog/PublicationCatalogAdapterTest.java`

**Interfaces:**
- Consumes: Task 4 端口、Task 3 的 `PublicationEndpoint`、Task 1 的 `retracted`。
- Produces:
  - `new CachingReaderStatsDecorator(ReaderStatsPort delegate, StringRedisTemplate redis, InsightProperties properties, MeterRegistry meters)`：`MGET` → 没命中的交给 `delegate` 一次 → 管道 `SET … EX 60`（没数据的写 `"0:0"`）；Redis 抛 `RuntimeException` 时整次直接走 `delegate`，计 `analytics.insight.stats.cache{result=error}`；`hit` / `miss` 按篇计数。
  - `new PublicationCatalogAdapter(PublicationEndpoint endpoint)`：`eligibleAmong` 按 100 分批调 `resolveByIds`，合格 = `exists && state == ACTIVE && !retracted`；`latestIds` → `latest(limit).ids()`；`subjectNeighbors` → 映射 `SAME_MESH`/`SAME_VENUE`；`RemoteCallException` 及其他 `RuntimeException` 包成 `InsightUnavailableException`（`.claude/rules/tech/error-handling.md`）。
  - `new CachingPublicationNeighborsDecorator(PublicationCatalogPort delegate, StringRedisTemplate redis, JsonMapper json, MeterRegistry meters)`：只缓存 `subjectNeighbors`（键 `analytics:insight:neighbors:{articleId}`，TTL 6 小时，值为 JSON 数组），其余两个方法直接委托；delegate 抛异常时不写缓存、原样抛出；Redis 出错时直接委托；计 `analytics.insight.neighbors{outcome=hit|fetched|unavailable}`。

- [ ] **Step 1: 写失败的测试**
  - `CachingReaderStatsDecoratorIT`（delegate 用 Mockito 替身）：`all_hits_skip_delegate`；`only_misses_go_to_delegate`（缓存了 1、请求 `[1, 2]` → delegate 只收到 `[2]`）；`zero_is_cached`（delegate 返回空 → 再查不调 delegate，`redis.getExpire(key)` 在 `(0, 60]`）；`keys_are_split_by_synthetic_flag`（`includeSynthetic` 不同的两个装饰器互不命中）；`redis_down_falls_back_to_delegate`（`StringRedisTemplate` 指向关掉的端口 → 结果来自 delegate、不抛）。
  - `PublicationCatalogAdapterTest`：`eligible_requires_active_existing_and_not_retracted`；`eligible_batches_by_100`（250 个 → 3 次调用）；`remote_failure_becomes_unavailable`。
  - `CachingPublicationNeighborsDecoratorIT`：`second_call_hits_cache`（TTL 在 `(5h, 6h]`）；`failure_is_not_cached`（第一次 delegate 抛 → 第二次仍调 delegate）。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-analytics:patra-analytics-infra:test :patra-api:patra-analytics:patra-analytics-infra:integrationTest --tests "*insight*"`。
- [ ] **Step 3: 实现；`InsightInfraConfiguration` 里两个装饰器 `@Primary`；跑 `./gradlew dumpModuleGraph`。**
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 暂存（含 `module-graph` 产物）。**

### Task 9: app 层：两个刷新用例与三个查询服务

**Files:**
- Create（`patra-analytics-app/src/main/java/dev/patra/analytics/app/insight/usecase/`）：`hot/RefreshHotReadingCommand.java`、`RefreshHotReadingHandler.java`、`RefreshHotReadingResult.java`；`coread/RefreshCoReadingCommand.java`、`RefreshCoReadingHandler.java`、`RefreshCoReadingResult.java`；`query/ArticleStatsQueryService.java`、`ArticleStatsView.java`、`HotReadingQueryService.java`、`AlsoReadQueryService.java`
- Test: 对应的 `*Test.java`（`patra-analytics-app/src/test/java/...`）

**Interfaces:**
- Consumes: Task 4 全部端口与规则。
- Produces:
  - `RefreshHotReadingCommand.of()`（`Command<RefreshHotReadingResult>`）；`record RefreshHotReadingResult(int ranked, int backfill)`。流程：`today = LocalDate.now(clock.withZone(Asia/Shanghai))` → `signals.hotCandidates(today, CANDIDATE_LIMIT)` → `catalog.eligibleAmong(ids)` 过滤保持顺序 → 上榜 < 14 时 `catalog.latestIds(LATEST_FETCH)` → `HotReadingRule.board(...)` → `transactions.executeWithoutResult(s -> hotSnapshots.replace(board, clock.instant()))`；计时 `analytics.insight.job{job=hot_reading, outcome}`。
  - `RefreshCoReadingCommand.of()`；`record RefreshCoReadingResult(int articles, int rows)`。流程：`signals.coReadingPairs(today, CANDIDATE_LIMIT)` → 全部 ID 去重 `catalog.eligibleAmong` → `CoReadingRule.keep` → 事务里 `replace`；计时 `job=co_reading`。
  - `record ArticleStatsView(long articleId, long viewers, long fulltextReaders, boolean hot)`；`ArticleStatsQueryService.stats(List<Long> articleIds): List<ArticleStatsView>`（去重保序；`statsOf` 没有的为 0；`hot` 来自 `HotReadingReadPort.rankedAmong`）。
  - `HotReadingQueryService.page(): HotReadingSummary`（`summary(HotReadingRule.PAGE_SIZE)`）。
  - `AlsoReadQueryService.alsoRead(long articleId): List<AlsoReadItem>`：`coReads.relatedOf(id, DISPLAY)`；不足 6 时 `catalog.subjectNeighbors(id, NEIGHBOR_FETCH)`，抛 `InsightUnavailableException` 时只用共现部分并 `log.warn`；`AlsoReadRule.assemble(...)`。

- [ ] **Step 1: 写失败的测试**（端口都用 Mockito，`Clock.fixed(2026-11-02T08:05Z)`，`TransactionOperations` 用直接执行回调的替身）：
  - `RefreshHotReadingHandlerTest`：`passes_beijing_today`（UTC 2026-11-01T17:00Z → `today = 2026-11-02`）；`filters_ineligible_and_keeps_order`；`skips_latest_when_ranked_fills_page`（14 个合格 → 不调 `latestIds`）；`hot_job_keeps_previous_snapshot_when_catalog_fails`（`eligibleAmong` 抛 → 异常抛出、`replace` 从未调用）；`clickhouse_failure_does_not_touch_snapshot`。
  - `RefreshCoReadingHandlerTest`：`checks_all_ids_once_and_replaces`；`failure_does_not_touch_snapshot`。
  - `ArticleStatsQueryServiceTest`：`dedupes_and_keeps_request_order`；`missing_stats_are_zero`；`hot_comes_from_ranked_snapshot`；`clickhouse_unavailable_propagates`。
  - `AlsoReadQueryServiceTest`：`six_co_reads_do_not_call_catalog`；`backfills_from_neighbors`；`catalog_unavailable_returns_co_read_only`。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-analytics:patra-analytics-app:test --tests "*insight*"`。
- [ ] **Step 3: 实现**（`RefreshHotReadingHandler`、`RefreshCoReadingHandler` 不标 `@Transactional`，用 `TransactionOperations`）。
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 暂存。**

### Task 10: 接口、任务入口与错误映射

**Files:**
- Create（`patra-analytics-adapter/src/main/java/dev/patra/analytics/adapter/insight/`）：`rest/ArticleInsightController.java`（`POST /articles/stats`、`GET /articles/{articleId}/also-read`）、`rest/HotReadingController.java`（`GET /hot-reads`）、`rest/request/ArticleStatsRequest.java`、`rest/response/ArticleStatsResponse.java`、`ArticleStatsItemResponse.java`、`HotReadingResponse.java`、`HotReadingItemResponse.java`、`AlsoReadResponse.java`、`AlsoReadItemResponse.java`；`scheduler/HotReadingScheduleJob.java`（`@XxlJob("hotReadingJob")`）、`CoReadingScheduleJob.java`（`@XxlJob("coReadingJob")`）
- Modify: `patra-analytics-boot/src/main/java/dev/patra/analytics/config/AnalyticsErrorMappingContributor.java`（`InsightUnavailableException → http.UNAVAILABLE()`，类注释补一行）
- Test: `patra-analytics-adapter/src/test/java/dev/patra/analytics/adapter/insight/scheduler/HotReadingScheduleJobTest.java`、`CoReadingScheduleJobTest.java`（照 `ReadingHistoryRetentionScheduleJobTest`）；接口形状在 Task 11 的 boot IT 里测

**Interfaces:**
- Consumes: Task 9。
- Produces（JSON 形状 = spec §9 原样）：
  - `record ArticleStatsRequest(@NotNull @Size(min = 1, max = 100) List<@NotNull @Positive Long> articleIds)`（紧凑构造器 `copyOf`，含 null 时保留原值交给校验）。
  - `ArticleStatsItemResponse(String articleId, long viewerCount, long fulltextReaderCount, boolean hot)`。
  - `HotReadingResponse(Instant computedAt, int rankedTotal, List<HotReadingItemResponse> items)`；`HotReadingItemResponse(String articleId, String source, Integer rank)`，`source` 为 `RANKED` / `LATEST`，`rank` = `position`（上榜）或 null。
  - `AlsoReadItemResponse(String articleId, String source, String meshTerm, String venueName)`。
  - 两个任务成功时 `XxlJobHelper.handleSuccess("上榜 %d 篇、补位 %d 篇")` / `("%d 篇文献、%d 条推荐")`，失败 `handleFail`；类注释写 Cron（`0 5 * * * ?`、`0 30 4 * * ?`）。

- [ ] **Step 1: 写失败的测试** — 两个 `*ScheduleJobTest`：成功回写文案、命令总线抛异常时 `handleFail`。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-analytics:patra-analytics-adapter:test --tests "*insight*"`。
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 暂存。**

### Task 11: boot 端到端测试

**Files:**
- Create: `patra-analytics-boot/src/integrationTest/java/dev/patra/analytics/insight/InsightFlowIT.java`
- Modify: `patra-analytics-boot/src/test/java/dev/patra/analytics/ApplicationConfigTest.java`（`catalog_client_times_out_fast`：`groups.catalog.read-timeout = 2s`）

**Interfaces:**
- Consumes: 全部。`@SpringBootTest(RANDOM_PORT)` + 四个容器初始化器（同 `HistoryFlowIT`，topic 用 `it-insight.raw` / `it-insight.dlq`）；`@MockitoBean PublicationEndpoint`（catalog 替身：`resolveByIds` 全部合格、`latest` 返回固定 ID、`subjectNeighbors` 返回两行）。事件时间取「现在」，任务用真实时钟（不替换 `Clock`）。

- [ ] **Step 1: 写失败的测试**
  - `stats_reflect_ingested_event_within_a_minute`（AC 1）：经 Kafka `it-insight.raw` 写同一会话的 `page_viewed`（client）+ `article_viewed` + `fulltext_read` → 轮询 `POST /articles/stats` ≤ 10 秒内 `viewerCount = 1`、`fulltextReaderCount = 1`；再断言 Redis 键 TTL ∈ `(0, 60]`。
  - `stats_dedupes_and_keeps_big_ids_as_strings`：`["9007199254740993", "9007199254740993", "1"]` → 两行，`articleId` 为字符串原样。
  - `stats_rejects_bad_requests`：`{}`、`[]`、101 个、`["0"]`、`["abc"]` → 422。
  - `hot_reads_after_job`：三个不同设备的真人会话读同一篇 → 等入库 → 执行 `commandBus.handle(RefreshHotReadingCommand.of())` → `GET /hot-reads`：第一项 `source = RANKED`、`rank = 1`，其余 13 项 `LATEST`、`rankedTotal = 1`；`POST /articles/stats` 该篇 `hot = true`、补位的 `hot = false`。
  - `hot_reads_empty_before_first_run`：`TRUNCATE insight.*` 后 → `computedAt = null`、`items = []`。
  - `also_read_after_job`：三人同一天读 X 与 Y → 跑共现任务 → `GET /articles/{X}/also-read` 第一项 `{Y, CO_READ}`，后面是替身给的 `SAME_MESH`、`SAME_VENUE` 两行；`GET /articles/0/also-read` → 422。
  - `clickhouse_down_is_503`：单独的 IT 类 `InsightClickHouseDownIT`（照 `IngestClickHouseDownIT`，`patra.clickhouse.url` 指向关掉的端口）→ `POST /articles/stats` 503。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-analytics:patra-analytics-boot:integrationTest --tests "*Insight*"`。
- [ ] **Step 3: 补齐缺的装配直到通过**（不改前面 Task 定下的接口）。
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 暂存。**

### Task 12: 文档与全量验证

**Files:**
- Modify: `patra-api/patra-analytics/README.md`（insight 子域一节：分层、两个任务、缓存与快照）
- Modify: `.claude/rules/project-info.md`（analytics 一行：「计数、热读、看过还看了」不再写「随后续 Issue」）
- Modify: `docs/patra/runbooks/v0.18-analytics-go-live-runbook.md`（新增 8.4 节，内容见 spec §13：catalog 先部署、V14 索引耗时、XXL-Job 两个任务的执行器 / handler / Cron / 手动执行、`insight.snapshot_run` 两行、`GET /patra-analytics/hot-reads` 14 篇、B / C / E 三人读同一篇后手动跑热读任务上榜）
- Modify: `patra-api/patra-catalog/README.md`（内部接口一行：`latest`、`subject-neighbors`）

- [ ] **Step 1: 改文档。**
- [ ] **Step 2: 全量验证** — `./gradlew check`（PASS）；`./gradlew :patra-api:patra-analytics:patra-analytics-infra:integrationTest`（PASS）；`./gradlew :patra-api:patra-analytics:patra-analytics-boot:integrationTest`（PASS）；`./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest :patra-api:patra-catalog:patra-catalog-adapter:integrationTest`（PASS）。
- [ ] **Step 3: 暂存；整理待提交的路径清单与建议的提交信息，交编排会话问用户。**
