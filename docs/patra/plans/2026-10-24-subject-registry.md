# 对象登记表与能力矩阵 Implementation Plan（PAP-112）

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（用户 10/21 定：本会话内联 TDD，不用 subagent-driven）。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 patra-interaction 的 `subject` 子域建以 PMID 为锚的对象登记表、`SubjectType` 能力矩阵、首次互动解析与每日对账任务，并给 collection 子域两个公开端口。

**Architecture:** domain 定义值、聚合（含纯函数对账规则）与四个端口；app 的 `SubjectGatewayImpl` 先查本地、未命中才经按类型的来源策略调 catalog，再在事务里按 `natural_key` upsert；对账走 CommandBus，100 行一页按 PMID 回查。infra 全用原生 SQL 读写 `subject.registry`，catalog 来源经 `PublicationEndpoint` 代理。

**Tech Stack:** Java 25、Spring Boot 4、PostgreSQL 17（Testcontainers）、WireMock、XXL-Job、JUnit 5 + AssertJ + Mockito。

**Spec:** `docs/patra/specs/2026-10-24-subject-registry-design.md`（下称「spec」，节号指它）

## Global Constraints

- 包根 `dev.patra.interaction`；所有新类落在 spec 第 4 ~ 8 节写明的包（下面「文件结构」逐个列出），层下只能是 `subject` / `collection` 子域。
- subject 公开包只有 `domain.subject.port.read`、`model.read`、`port.gateway`、`model.vo`、`model.enums`；`@Transactional` 只能在 app（本计划不用注解，用 `TransactionOperations`）。
- 每个方法写 `///` Markdown Javadoc；不写全类名；record ≤ 4 参数用 `of()`、≥ 5 用 `@Builder`；集合字段 `List.copyOf` / `Map.copyOf`。
- 时间一律注入 `java.time.Clock`。
- 迁移脚本 `V20261024200000__create_subject_registry.sql`，表 `subject.registry`，原样照 spec 7.1。
- catalog 分组：`base-url: lb://patra-catalog`、`connect-timeout: 1s`、`read-timeout: 2s`。
- 任务 handler `subjectReconciliationJob`；对账页大小 100；`findIdsByKeys` ≤ 100、`findByIds` ≤ 5000，超出抛 `IllegalArgumentException`。
- 每个 Gradle project 依赖在第一个 import 它的任务里才加（R5）。
- **不 commit、不 push**：每个任务结束只跑测试；全部完成、用户验收同意后统一提交（第 11 任务）。commit 用中文动词开头（commitlint）。
- 命令从仓库根目录跑，一律绝对路径或 `-p`，不 `cd`。下文 `G=./gradlew`，`I=:patra-api:patra-interaction`。

## Review Focus

1. **catalog 返回的 `id` 与请求 key 对不上**（换库后 by-ids 返回的是请求键本身，但 by-pmids 按 PMID 对回）：对账必须按响应的 `pmid` 对回 `NaturalKey`，不能按顺序 —— Task 5 加 `should_match_results_by_pmid_not_by_position`（响应乱序）。
2. **同一页里两行抢同一个新 `source_id`**（A 拿到 B 的旧 ID）：`saveReconciled` 逐行先清占用再写，不能撞部分唯一索引 —— Task 4 加 `should_reassign_source_id_between_rows_in_one_batch`。
3. **`findIdsByKeys` 收到不合法 key**（门户传了 `abc`、`0`）：跳过而不是 422，「我的状态」照常返回其余 —— Task 4 的 `SubjectReadAdapterIT.should_skip_invalid_keys`。
4. **catalog 把 `publishedDate` 给成意外格式**（如 `2019-3`）：快照校验抛错会让整页对账失败；适配器捕获后按「该行无日期」降级并 WARN，不让一行坏数据拖垮一页 —— Task 5 加 `should_drop_malformed_published_date_with_warning`。
5. **对账时 catalog 一直 503**：每页都快速失败、任务标红、不改任何行 —— Task 9 的 FlowIT 用例 6 外加「全部失败时行数据不变」断言。

---

## 文件结构

```
patra-interaction-domain/src/main/java/dev/patra/interaction/domain/subject/
  model/enums/   SubjectType, SubjectCapability, NaturalKeyScheme, SubjectState
  model/vo/      SubjectId, NaturalKey, SubjectSnapshot
  model/source/  SourceRecord, SourceState
  model/aggregate/ SubjectAggregate
  model/read/    SubjectView
  exception/     SubjectSourceUnavailableException, SubjectNotFoundException,
                 InvalidSubjectKeyException, SubjectCapabilityNotSupportedException
  port/repository/ SubjectRepository
  port/source/   SubjectSourcePort
  port/read/     SubjectReadPort
  port/gateway/  SubjectGateway
patra-interaction-app/src/main/java/dev/patra/interaction/app/subject/
  service/       SubjectSources, SubjectGatewayImpl
  usecase/reconcile/ ReconcileSubjectsCommand, ReconcileSubjectsHandler, ReconciliationResult
patra-interaction-infra/src/main/java/dev/patra/interaction/infra/subject/
  persistence/   SubjectRepositoryAdapter, SubjectReadAdapter, SubjectRows（包内共用的行映射与快照编解码）
  source/        PublicationSubjectSourceAdapter
patra-interaction-infra/src/main/resources/db/migration/subject/V20261024200000__create_subject_registry.sql
patra-interaction-adapter/src/main/java/dev/patra/interaction/adapter/subject/scheduler/SubjectReconciliationScheduleJob
patra-interaction-boot/src/main/java/dev/patra/interaction/config/CatalogClientConfiguration
```

---

### Task 0: 对齐分支与开工

- [ ] **Step 1:** `git fetch` 不需要（本地分支）。`git -C <worktree> rebase feat/v0.13-library-api`（本分支此时只有未提交的 spec 与 plan：先 `git stash push -u -m pap112-docs`，记下 SHA，rebase 后 `git stash apply <sha>` 再按标签 drop）。
- [ ] **Step 2:** 确认 PAP-110 计划一已在：`git log --oneline -10` 看到 catalog 的 PAP-110 提交，且 `grep -n publishedDate patra-api/patra-catalog/patra-catalog-api/src/main/java/dev/patra/catalog/api/dto/publication/PublicationResolutionResp.java` 有输出。没有就停下，告诉用户。
- [ ] **Step 3:** Linear PAP-112 推到「BE」。
- [ ] **Step 4:** 基线：`$G $I:patra-interaction-boot:test $I:patra-interaction-infra:integrationTest` 通过。

### Task 1: 能力矩阵与值对象

**Files:**
- Create: `domain/subject/model/enums/{SubjectType,SubjectCapability,NaturalKeyScheme,SubjectState}.java`、`model/vo/{SubjectId,NaturalKey,SubjectSnapshot}.java`、`exception/InvalidSubjectKeyException.java`
- Test: `patra-interaction-domain/src/test/java/dev/patra/interaction/domain/subject/model/{enums/SubjectTypeTest,vo/NaturalKeyTest,vo/SubjectSnapshotTest}.java`

**Interfaces — Produces:**
- `enum SubjectCapability { COLLECT }`；`enum SubjectState { ACTIVE, UNAVAILABLE }`
- `enum NaturalKeyScheme { PMID("pmid", "[1-9][0-9]{0,14}") }`：`String prefix()`、`boolean accepts(String value)`、`static Optional<NaturalKeyScheme> fromPrefix(String)`
- `enum SubjectType { PUBLICATION((short) 1, "publication", NaturalKeyScheme.PMID, EnumSet.of(COLLECT)) }`：`short code()`、`static SubjectType fromCode(short)`（未知抛 `IllegalArgumentException`）、`String slug()`、`static Optional<SubjectType> fromSlug(String)`、`boolean supports(SubjectCapability)`、`NaturalKeyScheme naturalKeyScheme()`、`long parseKey(String key)`（不合法抛 `InvalidSubjectKeyException`）
- `record SubjectId(long value)`：`static SubjectId of(long)`
- `record NaturalKey(NaturalKeyScheme scheme, String value)`：`static NaturalKey of(NaturalKeyScheme, String)`（不合法 `IllegalArgumentException`）、`static NaturalKey parse(String)`、`toString()` → `"pmid:123"`
- `record SubjectSnapshot(String title, String journal, String publishedDate)`：`static SubjectSnapshot of(...)`；紧凑构造器按 spec 4.2 校验 `publishedDate`
- `InvalidSubjectKeyException extends DomainException`，`RULE_VIOLATION`，消息「对象标识格式不正确」，构造参数 `(SubjectType type, String key)`（key 不进消息，进日志用 getter）

- [ ] **Step 1: 写 `SubjectTypeTest`**：`should_keep_stable_code_and_slug`（`PUBLICATION.code() == 1`、`slug()` 是 `"publication"`、`fromCode((short) 1)` 回到 PUBLICATION）；`should_reject_unknown_code`（`fromCode((short) 99)` 抛 `IllegalArgumentException`）；`should_return_empty_for_unknown_slug`；`should_support_collect_for_publication`；`should_parse_canonical_positive_decimal`（`"1"` → 1，`"9223372036854775807"` → `Long.MAX_VALUE`）；`should_reject_non_canonical_keys`（参数化：`"0"`、`"-1"`、`"01"`、`"+1"`、`" 1"`、`"1 "`、`"abc"`、`""`、`null`、`"9223372036854775808"` 都抛 `InvalidSubjectKeyException`）。
- [ ] **Step 2: 写 `NaturalKeyTest`**：`should_round_trip_pmid`（`NaturalKey.parse("pmid:123").toString()` 等于原文，`value()` 是 `"123"`）；`should_reject_malformed`（`"pmid:0"`、`"pmid:01"`、`"pmid:1234567890123456"`（16 位）、`"123"`、`"doi:10.1/x"`、`"pmid:"` 抛 `IllegalArgumentException`）。
- [ ] **Step 3: 写 `SubjectSnapshotTest`**：`should_accept_three_precisions_and_null`（`2019`、`2019-03`、`2019-03-15`、null）；`should_reject_malformed_published_date`（`19`、`2019-3`、`2019-13`、`2019-00`、`2019-03-32`、`2019-03-00`、`2019/03`）；`should_allow_all_fields_null`。
- [ ] **Step 4:** `$G $I:patra-interaction-domain:test` → 编译失败（类不存在）。
- [ ] **Step 5:** 实现上述类型。`parseKey` 用正则 `[1-9][0-9]{0,18}` 再 `Long.parseLong` 捕获溢出。
- [ ] **Step 6:** `$G $I:patra-interaction-domain:test` → PASS。

### Task 2: 聚合、对账规则、端口与其余异常

**Files:**
- Create: `model/source/{SourceRecord,SourceState}.java`、`model/aggregate/SubjectAggregate.java`、`model/read/SubjectView.java`、`exception/{SubjectSourceUnavailableException,SubjectNotFoundException,SubjectCapabilityNotSupportedException}.java`、四个端口接口
- Test: `domain/.../model/aggregate/SubjectAggregateTest.java`、`domain/.../exception/SubjectExceptionsTest.java`

**Interfaces — Produces:**
- `enum SourceState { ACTIVE, DELETED }`
- `record SourceRecord(long sourceId, NaturalKey naturalKey, SourceState state, SubjectSnapshot snapshot)`，`static SourceRecord of(long, NaturalKey, SourceState, SubjectSnapshot)`
- `SubjectAggregate`（不可变，`@Builder`）：`SubjectId subjectId`、`SubjectType type`、`NaturalKey naturalKey`、`Long sourceId`、`SubjectState state`、`SubjectSnapshot snapshot`、`Instant snapshotAt`；构造时校验 `sourceId == null ⇒ state == UNAVAILABLE`（违反抛 `IllegalStateException`）；`SubjectAggregate reconcile(Optional<SourceRecord> found, Instant now)` 按 spec 4.3 表返回新实例；`boolean isActive()`
- `@Builder record SubjectView(SubjectId subjectId, SubjectType type, String key, SubjectState state, SubjectSnapshot snapshot)`（5 参数）；`key` 为 `sourceId` 的十进制或 null
- 异常（全继承 `DomainException`）：`SubjectSourceUnavailableException(Throwable cause)` `DEP_UNAVAILABLE`「文献信息暂时无法获取，请稍后再试」；`SubjectNotFoundException(SubjectType type, String key)` `NOT_FOUND`「文献不存在或已被删除」；`SubjectCapabilityNotSupportedException(SubjectType, SubjectCapability)` `RULE_VIOLATION`「该对象不支持此操作」
- `SubjectRepository`：`Optional<SubjectAggregate> findBySourceId(SubjectType type, long sourceId)`；`SubjectAggregate upsertResolved(SubjectType type, SourceRecord record, Instant now)`；`List<SubjectAggregate> findPage(SubjectType type, long afterSubjectId, int limit)`；`void saveReconciled(List<SubjectAggregate> subjects)`
- `SubjectSourcePort`：`SubjectType type()`；`Optional<SourceRecord> resolveByKey(long sourceId)`；`Map<NaturalKey, SourceRecord> resolveByNaturalKeys(Collection<NaturalKey> keys)`（1 ~ 100）
- `SubjectReadPort`：`Map<String, SubjectId> findIdsByKeys(SubjectType type, Collection<String> keys)`；`Map<SubjectId, SubjectView> findByIds(Collection<SubjectId> ids)`
- `SubjectGateway`：`SubjectId resolveForInteraction(SubjectType type, String key, SubjectCapability capability)`

- [ ] **Step 1: 写 `SubjectAggregateTest`**（基线：ACTIVE、`sourceId=100`、快照 A、`snapshotAt=t0`；`now=t1`）：
  - `should_follow_new_source_id_when_active`：found = (200, ACTIVE, 快照 B) → `sourceId 200`、ACTIVE、快照 B、`snapshotAt t1`、`subjectId` 不变
  - `should_become_unavailable_when_soft_deleted`：found = (100, DELETED, 快照 B) → UNAVAILABLE、`sourceId 100`、快照 B、`t1`
  - `should_clear_source_and_keep_snapshot_when_missing`：found 空 → UNAVAILABLE、`sourceId null`、快照 A、`snapshotAt t0`
  - `should_revive_unavailable_subject`：基线改 UNAVAILABLE / `sourceId null`，found = (300, ACTIVE) → ACTIVE、300
  - `should_reject_active_without_source_id`：builder 造 `state=ACTIVE, sourceId=null` 抛 `IllegalStateException`
- [ ] **Step 2: 写 `SubjectExceptionsTest`**：四个异常的 `getErrorTraits()` 分别只含 `DEP_UNAVAILABLE` / `NOT_FOUND` / `RULE_VIOLATION` / `RULE_VIOLATION`，消息等于上面的中文原文。
- [ ] **Step 3:** `$G $I:patra-interaction-domain:test` → 编译失败。
- [ ] **Step 4:** 实现。端口是纯接口，`///` 写清 spec 6.1 / 6.2 的语义（尤其「只用于新增互动」「未登记或不合法的 key 不在结果里」「上限」）。
- [ ] **Step 5:** `$G $I:patra-interaction-domain:check` → PASS（含 domain 纯净性 R3）。

### Task 3: 迁移脚本

**Files:**
- Create: `patra-interaction-infra/src/main/resources/db/migration/subject/V20261024200000__create_subject_registry.sql`（照 spec 7.1，另加每列英文 `COMMENT ON`）
- Modify: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/InteractionMigrationIT.java`

- [ ] **Step 1: 改测试**：`should_record_both_migrations_with_timestamp_versions` 改名 `should_record_migrations_with_timestamp_versions`，期望 `"20261024100000", "20261024100100", "20261024200000"`；新增：
  - `should_create_registry_with_partial_unique_source_index`：`pg_indexes` 里 `subject.registry` 有 `uk_registry_natural_key`（`indexdef` 不含 `WHERE`）与 `uk_registry_source_id`（`indexdef` 含 `WHERE (source_id IS NOT NULL)`）
  - `should_enforce_registry_checks`：分别插入 `target_type=2`、`state='GONE'`、`state='ACTIVE' AND source_id NULL`、`natural_key='pmid:01'` 各抛 `DataIntegrityViolationException`；`state='UNAVAILABLE', source_id NULL` 成功
  - `should_bump_updated_at_on_update`：插一行（`updated_at` 设为过去）→ `UPDATE … SET state='UNAVAILABLE'` → `updated_at > ` 原值
- [ ] **Step 2:** `$G $I:patra-interaction-infra:integrationTest --tests '*InteractionMigrationIT'` → FAIL。
- [ ] **Step 3:** 写脚本。
- [ ] **Step 4:** 同命令 → PASS；`$G $I:patra-interaction-infra:checkMigrationColumns`（R6）通过。

### Task 4: 持久化适配器

**Files:**
- Modify: `patra-interaction-infra/build.gradle.kts`（加 `api(project("$I:patra-interaction-domain"))`）
- Create: `infra/subject/persistence/{SubjectRows,SubjectRepositoryAdapter,SubjectReadAdapter}.java`
- Test: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/subject/persistence/{SubjectRepositoryAdapterIT,SubjectReadAdapterIT}.java`

**Interfaces:** Consumes Task 2 的 `SubjectRepository`、`SubjectReadPort`。`SubjectRows`（包内）：`static SubjectAggregate toAggregate(Object[] row, JsonMapper)`、`static String writeSnapshot(SubjectSnapshot, JsonMapper)`；`snapshot` 的 JSON 字段名 `title` / `journal` / `publishedDate`。新 `subject_id` 用 `SnowflakeIdGenerator.getId()`。

- [ ] **Step 1: 写 `SubjectRepositoryAdapterIT`**（`@SpringBootTest(classes = InteractionITBootstrap.class)` + PG 初始化器 + `@ActiveProfiles("test")`；每个用例前 `TRUNCATE subject.registry`；写操作用 `TransactionTemplate` 包住，模拟 app 层事务）：
  - `should_insert_new_subject`：upsert (100, pmid:1, ACTIVE, 快照) → 返回的聚合 ACTIVE、`sourceId 100`；`findBySourceId(PUBLICATION, 100)` 得到同一 `subjectId`
  - `should_reuse_subject_for_same_pmid_with_new_source_id`：先 upsert (100, pmid:1)，再 upsert (200, pmid:1) → 同一 `subjectId`、`sourceId 200`、表里 1 行、`findBySourceId(100)` 为空
  - `should_clear_stale_holder_of_source_id`：行 A = (100, pmid:1)；upsert (100, pmid:2) → 不抛异常；A 变 `sourceId null` / UNAVAILABLE；新行 B 持有 100
  - `should_return_same_subject_under_concurrent_upsert`：两个线程用 `CountDownLatch` 同时 upsert (100, pmid:1) → 两个返回值 `subjectId` 相同，`count(*) = 1`
  - `should_page_in_subject_id_order`：插 5 行，`findPage(PUBLICATION, 0, 2)` → 前两个；以第二个的 ID 再取 → 第 3、4 个；最后取到空
  - `should_save_reconciled_rows`：两行改 sourceId / state / snapshot 后 `saveReconciled` → 读回一致
  - `should_reassign_source_id_between_rows_in_one_batch`（Review Focus 2）：A=(100, pmid:1)、B=(200, pmid:2)；同一批 A→200、B→300 → 不抛，读回 A=200、B=300
- [ ] **Step 2: 写 `SubjectReadAdapterIT`**：
  - `should_find_ids_by_registered_keys`：登记 100、200 → `findIdsByKeys(PUBLICATION, ["100","200","300"])` 只含 100、200
  - `should_skip_invalid_keys`（Review Focus 3）：`["abc","0","100"]` → 只含 `"100"`，不抛
  - `should_return_views_with_snapshot_and_state`：UNAVAILABLE 且无 sourceId 的行 → `key == null`、状态与快照正确（三种日期精度各一行）
  - `should_reject_oversized_batches`：101 个 key、5001 个 ID 抛 `IllegalArgumentException`
- [ ] **Step 3:** `$G $I:patra-interaction-infra:integrationTest --tests '*subject.persistence*'` → FAIL。
- [ ] **Step 4:** 实现。SQL 照 spec 7.3；`findIdsByKeys` 先用 `SubjectType.parseKey` 过滤不合法键（捕获 `InvalidSubjectKeyException`），再 `WHERE target_type = :t AND source_id = ANY(CAST(:ids AS bigint[]))`；`findByIds` 用 `subject_id = ANY(...)`；`saveReconciled` 逐行「① 清占用 → UPDATE」，`source_id` 未变的行跳过①。适配器不加 `@Transactional`。
- [ ] **Step 5:** 同命令 → PASS；`$G $I:patra-interaction-infra:check` 通过（R5：domain 被 import）。

### Task 5: catalog 来源策略

**Files:**
- Modify: `patra-interaction-infra/build.gradle.kts`（加 `implementation(project(":patra-api:patra-catalog:patra-catalog-api"))`、`implementation(project(":patra-tool:patra-tool-http-client"))`）
- Create: `infra/subject/source/PublicationSubjectSourceAdapter.java`
- Test: `patra-interaction-infra/src/test/java/.../infra/subject/source/PublicationSubjectSourceAdapterTest.java`、`src/integrationTest/java/.../infra/subject/source/PublicationSubjectSourceAdapterIT.java`

**Interfaces:** Consumes `PublicationEndpoint.resolveByIds(PublicationResolveByIdsReq)`、`resolveByPmids(PublicationResolveByPmidsReq)`（请求 record 的构造写法以 catalog-api 源码为准）、`PublicationResolutionResp`（含 PAP-110 的 `publishedDate`）。Produces `SubjectSourcePort` 的 PUBLICATION 实现（`@Component`）。

- [ ] **Step 1: 写单元测试**（mock `PublicationEndpoint`）：
  - `should_map_active_record`：`exists=true, ACTIVE, id=100, pmid="1", title, journal, publishedDate="2019-03"` → `SourceRecord(100, pmid:1, ACTIVE, 快照)`
  - `should_map_soft_deleted_record`：`DELETED` → `SourceState.DELETED`，快照照样有值
  - `should_treat_missing_as_empty`：`exists=false` → `Optional.empty()`
  - `should_treat_null_pmid_as_empty`：`pmid=null` → 空
  - `should_match_results_by_pmid_not_by_position`（Review Focus 1）：请求 pmid 1、2，响应顺序 2、1、外加 `exists=false` 的 3 → Map 的键值各自对上，3 不在结果里
  - `should_drop_malformed_published_date_with_warning`（Review Focus 4）：`publishedDate="2019-3"` → 快照 `publishedDate == null`，其余字段在
  - `should_translate_failures_to_unavailable`（参数化）：`RemoteCallException`（503 + `DEP_UNAVAILABLE`；422 + `RULE_VIOLATION`）、`ResourceAccessException`、`IllegalStateException` → 都抛 `SubjectSourceUnavailableException`，`getCause()` 是原异常
  - `should_reject_more_than_100_natural_keys`：101 个抛 `IllegalArgumentException`，不调代理
- [ ] **Step 2: 写 `PublicationSubjectSourceAdapterIT`**（`@WireMockTest`，客户端照 identity `CatalogReferenceAdapterIT.adapterFor` 手工建）：
  - `should_post_ids_and_read_string_id_back`：桩 `POST /_internal/publications/resolve/by-ids`，请求体 `equalToJson({"ids":[100]})`，响应 `"id":"100"`（字符串）→ `sourceId == 100L`
  - `should_post_pmids_without_prefix`：请求体 `{"pmids":["1","2"]}`
  - `should_translate_503_problem_detail`：503 + ProblemDetail（`traits:["DEP_UNAVAILABLE"]`）→ 不可用
  - `should_translate_connection_refused`：代理指向一个已关闭的端口（照 identity IT 的 `ServerSocket` 取空闲端口）→ 不可用
- [ ] **Step 3:** 跑 `$G $I:patra-interaction-infra:test --tests '*PublicationSubjectSourceAdapterTest'` 与对应 integrationTest → FAIL。
- [ ] **Step 4:** 实现（spec 8.1；`try` 只包一次代理调用；WARN 只记状态码、错误码、异常类名、键的个数）。
- [ ] **Step 5:** 两条命令 → PASS。

### Task 6: 来源注册与首次互动解析

**Files:**
- Modify: `patra-interaction-app/build.gradle.kts`（加 `api(project("$I:patra-interaction-domain"))`、`api("org.springframework:spring-tx")`）
- Create: `app/subject/service/{SubjectSources,SubjectGatewayImpl}.java`
- Test: `patra-interaction-app/src/test/java/dev/patra/interaction/app/subject/service/{SubjectSourcesTest,SubjectGatewayImplTest}.java`

**Interfaces — Produces:** `SubjectSources(List<SubjectSourcePort> ports)`、`SubjectSourcePort of(SubjectType type)`；`SubjectGatewayImpl(SubjectRepository, SubjectSources, TransactionOperations, Clock)` implements `SubjectGateway`（`@Component`）。

- [ ] **Step 1: 写 `SubjectSourcesTest`**：`should_index_ports_by_type`；`should_fail_when_a_type_has_no_port`（空列表抛 `IllegalStateException`，消息含 `PUBLICATION`）；`should_fail_on_duplicate_ports`。
- [ ] **Step 2: 写 `SubjectGatewayImplTest`**（mock 仓储与来源；`TransactionOperations.withoutTransaction()`；固定 `Clock`）：
  - `should_reject_unsupported_capability`：本版矩阵只有一格（PUBLICATION × COLLECT），用 Mockito 的 inline mock maker（Mockito 5 默认）`mock(SubjectType.class)`，`supports(COLLECT)` 返回 false → 抛 `SubjectCapabilityNotSupportedException`，仓储与来源零交互
  - `should_reject_invalid_key_without_touching_storage`：`"abc"` → `InvalidSubjectKeyException`，仓储与来源零交互
  - `should_return_local_active_subject_without_calling_source`：`findBySourceId` 返回 ACTIVE → 返回其 ID，`verifyNoInteractions(source)`
  - `should_reject_local_unavailable_subject`：返回 UNAVAILABLE → `SubjectNotFoundException`，不调来源
  - `should_propagate_source_unavailable_without_writing`：来源抛 `SubjectSourceUnavailableException` → 原样抛出，`upsertResolved` 未调
  - `should_reject_missing_without_writing`：来源返回空 → `SubjectNotFoundException`，未写
  - `should_reject_soft_deleted_without_writing`：来源返回 DELETED → `SubjectNotFoundException`，未写
  - `should_upsert_resolved_record_inside_transaction`：来源返回 ACTIVE → `upsertResolved(PUBLICATION, record, clock.instant())` 被调，返回其 `subjectId`；用一个记录调用的 `TransactionOperations` 替身断言 upsert 在 `execute` 回调里、来源调用在回调外
- [ ] **Step 3:** `$G $I:patra-interaction-app:test` → FAIL。
- [ ] **Step 4:** 实现 spec 5.1、5.2。
- [ ] **Step 5:** 同命令 → PASS。

### Task 7: 对账命令

**Files:**
- Modify: `patra-interaction-app/build.gradle.kts`（加 `api(project(":patra-tool:patra-tool-cqrs"))`）
- Create: `app/subject/usecase/reconcile/{ReconcileSubjectsCommand,ReconciliationResult,ReconcileSubjectsHandler}.java`
- Test: `patra-interaction-app/src/test/java/.../app/subject/usecase/reconcile/ReconcileSubjectsHandlerTest.java`

**Interfaces — Produces:** `record ReconcileSubjectsCommand() implements Command<ReconciliationResult>`，`of()`；`@Builder record ReconciliationResult(int scanned, int sourceIdChanged, int becameUnavailable, int revived, int failedBatches)`；`ReconcileSubjectsHandler(SubjectRepository, SubjectSources, TransactionOperations, Clock)` implements `CommandHandler<ReconcileSubjectsCommand, ReconciliationResult>`；常量 `PAGE_SIZE = 100`。

- [ ] **Step 1: 写测试**：
  - `should_page_until_empty_and_save_each_page`：仓储依次返回 100 行、3 行、空 → 来源被调两次（100、3 个键），`saveReconciled` 两次；`scanned == 103`
  - `should_count_transitions`：一行换 ID、一行 ACTIVE→UNAVAILABLE、一行 UNAVAILABLE→ACTIVE、一行不变 → 计数 1 / 1 / 1，`scanned 4`
  - `should_skip_failed_page_and_continue`：第一页来源抛 `SubjectSourceUnavailableException`、第二页正常 → `failedBatches 1`，第一页未 `saveReconciled`，第二页已存，第三次 `findPage` 的 `afterSubjectId` 是第二页最后一行（翻页不因失败卡住）
  - `should_use_last_subject_id_as_cursor`：`findPage` 第二次调用的游标是第一页最大 `subjectId`
- [ ] **Step 2:** `$G $I:patra-interaction-app:test --tests '*ReconcileSubjectsHandlerTest'` → FAIL。
- [ ] **Step 3:** 实现 spec 5.3；结束 `log.info("对象对账完成: type={}, scanned={}, sourceIdChanged={}, becameUnavailable={}, revived={}, failedBatches={}", …)`。
- [ ] **Step 4:** 同命令 → PASS。

### Task 8: XXL-Job 处理器

**Files:**
- Modify: `patra-interaction-adapter/build.gradle.kts`（加 `api(project("$I:patra-interaction-app"))`、`api(libs.xxl.job)`、`testImplementation(project(":patra-tool:patra-tool-test"))`）
- Create: `adapter/subject/scheduler/SubjectReconciliationScheduleJob.java`
- Test: `patra-interaction-adapter/src/test/java/.../adapter/subject/scheduler/SubjectReconciliationScheduleJobTest.java`

- [ ] **Step 1: 写测试**，写法照 identity `AccountDeletionScheduleJobTest`（它怎么拦 `XxlJobHelper` 就怎么拦）：`should_report_success_with_counts`（结果无失败 → 成功消息「已核对 103 个：换 ID 1、失效 2、复活 3」）；`should_report_failure_when_batches_failed`（`failedBatches 1` → 失败消息「已核对 103 个，失败 1 批」）；`should_report_failure_on_exception`（CommandBus 抛 `RuntimeException("boom")` → 失败消息「执行失败：boom」）。
- [ ] **Step 2:** `$G $I:patra-interaction-adapter:test` → FAIL。
- [ ] **Step 3:** 实现：`@XxlJob("subjectReconciliationJob")`，其余照 spec 8.4。
- [ ] **Step 4:** 同命令 → PASS。

### Task 9: boot 装配与端到端集成测试（全部 AC）

**Files:**
- Modify: `patra-interaction-boot/build.gradle.kts`（加 `implementation(project(":patra-tool:patra-tool-http-client"))`、`implementation(project(":patra-api:patra-catalog:patra-catalog-api"))`；删掉 `testImplementation` 里的 domain、app 两行，已经经 adapter 传进来）
- Modify: `patra-interaction-boot/src/main/resources/application.yml`（spec 8.3 的 `patra.http-client` 段）
- Create: `boot/src/main/java/dev/patra/interaction/config/CatalogClientConfiguration.java`（照 identity，只建 `catalogRestClient` 与 `PublicationEndpoint` 两个 bean）
- Modify: `boot/src/test/java/dev/patra/interaction/ApplicationConfigTest.java`
- Create: `boot/src/integrationTest/java/dev/patra/interaction/subject/SubjectRegistryFlowIT.java`

**Interfaces:** Consumes `SubjectGateway`、`SubjectReadPort`、`CommandBus` + `ReconcileSubjectsCommand`。

- [ ] **Step 1: `ApplicationConfigTest` 加 `should_route_catalog_group_through_load_balancer`**：`patra.http-client.http-interface.groups.catalog.base-url` 等于 `lb://patra-catalog`，`connect-timeout` `1s`、`read-timeout` `2s`。
- [ ] **Step 2: 写 `SubjectRegistryFlowIT`**：`@SpringBootTest(classes = PatraInteractionApplication.class)` + `InteractionITPostgreSQLContainerInitializer` + `test` profile；`WireMockExtension` 动态端口、`http2PlainDisabled(true)`；`@DynamicPropertySource` 打开 `spring.cloud.discovery.enabled` 并把 `spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri` 指到 WireMock（照 identity `ProfileFlowIT`）；每例前 `resetAll()` + `TRUNCATE subject.registry`。桩辅助方法 `stubById(long id, String pmid, String state)`、`stubMissingId(long id)`、`stubByPmids(List<…>)`、`stubCatalogDown()`（两个路径都 503 ProblemDetail）。用例照 spec 9.2：
  1. `should_follow_new_catalog_id_after_reconciliation`（AC1）
  2. `should_mark_unavailable_on_soft_delete_and_revive`（AC2；中间态首次解析抛 `SubjectNotFoundException`）
  3. `should_reject_first_interaction_when_catalog_down_but_keep_registered`（AC3；断言异常 `getErrorTraits()` 含 `DEP_UNAVAILABLE`，行数不变，已登记 key 返回原 ID 且 `catalog.verify(0, postRequestedFor(anyUrl()))`）；另一例 `should_reject_first_interaction_when_catalog_connection_fails`：桩 `any(anyUrl())` 返回 `aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)`（客户端得到 `ResourceAccessException`）→ 同样断言
  4. `should_reject_missing_and_not_register`（AC4；`exists=false`、`pmid=null`、`DELETED` 三种，`count(*) == 0`）
  5. `should_unlink_vanished_pmid_and_relink_after_reimport`
  6. `should_skip_failed_batch_and_refresh_others`：插 150 行（直接 SQL 造数，PMID 1..150），by-pmids 第一批 503、第二批正常（用 WireMock `equalToJson` 区分两批）→ `failedBatches 1`、第二批 50 行已刷新；再全部 503 跑一次 → `failedBatches 2`，表内容与之前完全一致（Review Focus 5）
- [ ] **Step 3:** `$G $I:patra-interaction-boot:test $I:patra-interaction-boot:integrationTest` → FAIL。
- [ ] **Step 4:** 实现配置与 yml。
- [ ] **Step 5:** 同命令 → PASS（`PatraInteractionApplicationIT` 也要仍然通过：test profile 关着发现，`lb://` 客户端只建不调）。

### Task 10: 架构核对、模块图、文档与全量门禁

**Files:**
- Modify: `patra-infra/cd/module-graph.json`（生成）、必要时 `patra-infra/cd/detect-changes.test.sh`
- Modify: `patra-api/patra-interaction/README.md`、`docs/patra/runbooks/v0.13-library-go-live-runbook.md`（spec 第 10 节）

- [ ] **Step 1: 确认架构测试真的管着新类**：临时在 `app/collection/` 放一个 import `domain.subject.port.repository.SubjectRepository` 的类 → `$G $I:patra-interaction-boot:test --tests '*InteractionArchitectureTest'` 红且点名该类；删掉后绿。
- [ ] **Step 2:** `$G dumpModuleGraph`；`git diff patra-infra/cd/module-graph.json` 只多出 interaction 模块的新依赖与 `patra-catalog-api`（及 http-client 等）`impacts` 里的 `interaction`。
- [ ] **Step 3:** `bash patra-infra/cd/detect-changes.test.sh` 全部通过；有因模块图变化而失败的写死期望就按新图改，改前先确认失败原因确是这个。
- [ ] **Step 4:** 改 README 与 runbook（spec 第 10 节逐条）。
- [ ] **Step 5: 全量门禁**：`$G spotlessApply`，然后 `$G check`，再显式 `$G $I:patra-interaction-infra:integrationTest $I:patra-interaction-boot:integrationTest`。全部 BUILD SUCCESSFUL 才算完。iCloud 负载高导致的随机失败按记忆处理（等负载降下来重跑，不改超时）。

### Task 11: 交接与收尾

- [ ] **Step 1:** 写共享笔记 `PAP-112-subject-contract.md`（spec 第 10 节列的内容：两个端口签名与语义、四个异常与 HTTP 状态、只用于新增互动、异常直接传播不 import、`key` 为空只能用快照、排序键在 `snapshot` 且每日刷新、重导后手动对账）。
- [ ] **Step 2:** 在 spec 末尾加「实施记录」节：与设计的偏离、门禁结果。
- [ ] **Step 3:** 向用户报告改动与验证结果，**等用户同意**后提交（中文动词开头，如 `feat(interaction): 新增对象登记表、首次互动解析与每日对账 (PAP-112)`，核对 `git diff --cached` 只含本 Issue 的路径），再 `git -C <主检出> merge --ff-only claude/nifty-benz-86b9aa` 快进合回 `feat/v0.13-library-api`（主检出若有未提交改动先确认不冲突）。不 push。
- [ ] **Step 4:** Linear PAP-112 勾 To-Do 与 AC；mini 上的操作（runbook 第 6 步建任务、D7 手动触发）待后端 PR 合进 main 后执行，动手前问用户。
