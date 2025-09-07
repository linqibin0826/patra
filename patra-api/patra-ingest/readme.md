# 1) 有界上下文（Bounded Context）与通用语言（Ubiquitous Language）

**Ingest（采集）上下文**负责：计划编排（Plan/PlanSlice）→ 任务下发（Job）→ 运行（Run/RunBatch）→ 游标推进（Cursor/CursorHistory）→ 源命中与回放（SourceHit）→ 字段谱系（FieldProvenance）→ 指标快照（MetricSnapshot）。

通用术语：

* **Plan**：采集计划（时间范围、表达式、供给方配置）。
* **PlanSlice**：计划切片（按时间/ID分片，让任务并发可控）。
* **Job**：从计划或切片生成的可执行单元（具幂等等、调度时间窗）。
* **Run**：一次 Job 的尝试（带窗口与 attemptNo，不变量：from < to，attempt ≥ 1）。
* **RunBatch**：Run 内的批处理编号（幂等等键保障去重）。
* **Cursor**：来源级别的增量推进点（key/value，面向外部源）。
* **CursorHistory**：游标变更的可追溯历史（窗口+时间）。
* **SourceHit**：对外部源的原始命中（保存原始 JSON、哈希、来源唯一 ID）。
* **FieldProvenance**：某作品（work）字段的来源证明（哪个命中、哪条值、何时抽取）。
* **MetricSnapshot**：某作品/来源的指标（质量、覆盖度等）在某时刻的快照。

---

# 2) 聚合划分（Aggregates）与关系

## A. Plan 聚合

* **聚合根**：`Plan`
* **实体**：`PlanSlice`（也可独立为聚合，取决于写入/并发需求；推荐**独立聚合**以降低 Plan 热点）
* **不变量**：`dateFrom <= dateTo`；Plan 状态机（DRAFT→ACTIVE→PAUSED→FINISHED）
* **关系**：`Plan`（弱引用）→ `PlanSlice`（独立聚合，用 `planId` 关联）

## B. Job 聚合

* **聚合根**：`Job`
* **实体**：无（Job 内部属性即可）
* **不变量**：`jobKey`/`idempotentKey` 唯一；时间窗与状态机（PENDING→SCHEDULED→RUNNING→SUCCEED/FAILED/CANCELLED）
* **关系**：Job 引用 `Plan`/`PlanSlice` 的 id（弱一致，不加 FK 也行）

## C. Run 聚合

* **聚合根**：`Run`
* **实体**：`RunBatch`（通常**同一事务内写入**，建议作为**同聚合**子实体）
* **不变量**：`windowFrom < windowTo`；`attemptNo ≥ 1`；`(jobId, cursorKey, window, attemptNo)` 幂等唯一

## D. Cursor 聚合

* **聚合根**：`Cursor`
* **实体**：`CursorHistory`（可作为独立表/审计，不必在同一聚合内强事务）
* **不变量**：同一 `(provenanceId, cursorKey)` 唯一；推进策略单调不回退（由领域服务保证）

## E. SourceHit 聚合

* **聚合根**：`SourceHit`
* **不变量**：`(provenanceId, sourceSpecificId, rawDataHash)` 唯一；`retrievedAt` 不晚于持久化时间（弱检查）

## F. FieldProvenance 聚合

* **聚合根**：`FieldProvenance`
* **不变量**：`(workId, fieldName, sourceHitId, valueHash)` 唯一；`collectedAt` 合理

## G. MetricSnapshot 聚合

* **聚合根**：`MetricSnapshot`
* **不变量**：`(workId, metricType, source, collectedAt)` 唯一

> 读多写少的 **FieldProvenance** 与 **MetricSnapshot** 可用于读模型/分析，必要时与 OLAP 分层解耦。

---

# 3) 领域对象草模（关键属性 & 不变量）

> 仅列核心字段，实际代码以 Record/类形式表达，保持值对象不可变与输入校验。

* **Plan**

    * id, planKey, name, exprHash, dateFrom, dateTo, status, createdAt, updatedAt
    * 规则：时间范围合法；状态机流转合法

* **PlanSlice**

    * id, planId, sliceNo, sliceFrom, sliceTo, exprHash
    * 规则：范围合法；在 Plan 范围内（应用/领域校验）

* **Job**

    * id, jobKey, idempotentKey, planId?, sliceId?, scheduledAt, status, windowFrom?, windowTo?
    * 规则：幂等键唯一；状态/时间窗一致性

* **Run**

    * id, jobId, cursorKey?, windowFrom, windowTo, attemptNo, status
    * 规则：窗口合法；attempt ≥ 1；与 Job 关联存在

* **RunBatch**

    * id, runId, batchNo, idempotentKey, status
    * 规则：批次号单 run 唯一；幂等键唯一

* **Cursor**

    * id, literatureProvenanceId, cursorKey, cursorValue, version
    * 规则：同源-同 key 唯一；推进策略（单调）

* **SourceHit**

    * id, workId, literatureProvenanceId, sourceSpecificId, retrievedAt, rawDataJson, rawDataHash
    * 规则：去重唯一键；hash/bin 比较

* **FieldProvenance**

    * id, workId, fieldName, sourceHitId, valueJson, valueHash, collectedAt
    * 规则：组合唯一；对应 SourceHit 存在（弱检查）

* **MetricSnapshot**

    * id, workId, metricType, source, collectedAt, valueJson
    * 规则：组合唯一

> **值对象**：`TimeWindow(from, to)`、`IdempotentKey(value)`、`ExprHash(value)`、`CursorPair(key, value)`、`MetricValue(json)`、`ProvenanceValue(json, hash)` 等，均在构造时校验。

---

# 4) 领域事件（Domain Events）

* `PlanActivated`, `PlanPaused`, `PlanFinished`
* `JobScheduled`, `JobStarted`, `JobSucceeded`, `JobFailed`, `JobCancelled`
* `RunStarted`, `RunSucceeded`, `RunFailed`, `RunRetried`
* `CursorAdvanced`（携带 old/new 值，用于写入 CursorHistory）
* `SourceHitRecorded`（触发解析/规范化流程）
* `FieldProvenanceDerived`（某字段已抽取出标准值）
* `MetricSnapshotUpdated`

事件用于解耦：**应用服务**发布领域事件；**出站适配器**（MQ/异步）订阅推进下游流程。

---

# 5) 领域服务与策略（Domain Services / Policies）

* **JobAssembler**：从 Plan/PlanSlice 生成 Job（计算幂等等 key、设定时间窗、去重防抖）。
* **RunOrchestrator**：驱动 Run 生命周期（attempt 递增、窗口继承、失败重试上限策略）。
* **CursorAdvancePolicy**：基于来源类型定义推进规则（时间戳/自增 ID/复合游标），保证**单调**与**幂等**。
* **SourceNormalizationService（ACL 防腐层）**：原始 JSON → 规范值（`valueJson`）与 schema 版本；产出 `valueHash`。
* **FieldDerivationService**：将 SourceHit 解析出的候选值写入 FieldProvenance，处理冲突（最新优先/质量优先/权重合并）。
* **MetricAggregationService**：周期性从 Field/Hit 汇总指标，写入 MetricSnapshot。

---

# 6) 仓储端口（Repository Ports）

> 每个聚合一个仓储接口，**放在 app**，仅表达领域意图；实现由 infra 提供（MyBatis-Plus）。

* `PlanRepository`：`save/ByKey/findSlices(planId)/activate/pause/finish`
* `PlanSliceRepository`：`save/batchSave/findByPlanAndNo`
* `JobRepository`：`save/findByKey/findPending(beforeTime)/markRunning/finish`
* `RunRepository`：`save/findLatestAttempt(jobId,window)/appendBatch/listBatches(runId)/finish`
* `CursorRepository`：`get(provenanceId, key)/saveOrAdvance/appendHistory`
* `SourceHitRepository`：`saveIfAbsent(hit)/listByWork(workId, page)/existsByUnique(provenanceId, sourceSpecificId, hash)`
* `FieldProvenanceRepository`：`saveIfAbsent(fp)/listLatestByWorkAndField(workId, field, limit)`
* `MetricSnapshotRepository`：`upsert(snapshot)/findLatest(workId, metricType, source)`

> **查询型端口（Query Ports）**：为读用例提供面向场景的查询（避免在应用层堆 SQL）。

---

# 7) 应用服务用例（Application Services）

* **PlanAppService**

    * `createPlan(cmd)`：校验范围/表达式，持久化，发 `PlanActivated?`
    * `slicePlan(planId, policy)`：生成切片，落库
    * `activate/pause/finish(planId)`

* **JobAppService**

    * `generateJobs(planId or sliceId)`：调用 `JobAssembler`
    * `scheduleDueJobs(now)`：拉取待调度，投递执行事件/命令
    * `markJobRunning/finish/fail(jobId, reason)`

* **RunAppService**

    * `startRun(jobId, window, attempt)`：校验幂等键，创建 `Run` 与首个 `RunBatch`
    * `appendRunBatch(runId, batchNo, idemKey)`：入队批次
    * `finishRun(runId, summary)`：写指标/派生下一步

* **CursorAppService**

    * `advanceCursor(provenanceId, key, candidateValue)`：根据策略推进 + 记录历史

* **IngestAppService（面向外部源）**

    * `recordSourceHit(cmd)`：`SourceHitRepository.saveIfAbsent` → 发布 `SourceHitRecorded`
    * `deriveFieldProvenance(hitId)`：调用规范化与派生服务 → 写 `FieldProvenance`
    * `updateMetricSnapshot(workId, metrics)`：写 `MetricSnapshot`

> 应用服务只做**编排**与**事务边界**控制；领域规则在实体/值对象/领域服务中。

---

# 8) 六边形架构端口与适配器

* **入站适配器（驱动）**：REST（管理/查询）、Scheduler（定时调度）、MQ/事件订阅（触发下游）
* **出站适配器（被驱动）**：Repository（MyBatis-Plus）、外部 API 客户端、消息发布器（RocketMQ/Kafka）、配置中心（Nacos）

端口定义：

* `…Repository` 接口（app）
* `EventPublisher`（app，发布领域事件）
* `ExternalSourceClient`（ACL，屏蔽源系统差异）

---

# 9) 枚举与类型映射（MyBatis-Plus / TypeHandler）

* `Status`、`MetricType`、`SourceType` 等统一枚举，落库采用 **TINYINT 或 VARCHAR + CodeEnum**；
* `Hash`/`Key` 等值对象在 TypeHandler 中按 **BINARY/utf8mb4\_bin** 对等映射，避免大小写陷阱。
* `TimeWindow` 可拆两列映射，构造时校验。

---

# 10) 事务与一致性

* **单聚合强事务**：`Plan`、`Job`、`Run(+RunBatch)`、`Cursor`、`SourceHit`、`FieldProvenance`、`MetricSnapshot`。
* **跨聚合最终一致**：用领域事件驱动（本地事务 + 事件表/Outbox），出站 MQ 发布，订阅者异步处理。
* **幂等**：所有写接口以 `idempotentKey` 或复合唯一键兜底；应用层重试不造成重复写。

---

# 11) 查询模型与索引友好用例

* **最新指标**：`findLatest(workId, metricType, source)` 走 `(work_id, metric_type, source, collected_at)` 索引 + `ORDER BY collected_at DESC LIMIT 1`
* **按作品回放来源**：`listByWork(workId, page)` 走 `(work_id, retrieved_at)` 索引

---

# 13) 命名与转换器约定

* 转换器分三类：

    * `*RestAssembler`：`Req/Resp ↔ App DTO`（adapter-in/web）
    * `*xxxConverter`：`DO ↔ Domain`（adapter-out/persistence）
    * `*AppConverter`：`ExternalDTO ↔ Domain/VO`（app）
* 枚举与码表：`XxxStatus` 采用 `CodeEnum`。

---

# 15) 与表结构的精确映射要点

* 所有 `id`/外键列统一 `BIGINT UNSIGNED`；实体用 `Long`，并在 MP 配置 `DbColumnType.LONG`。
* 哈希/键类字段用 **BINARY/utf8mb4\_bin**；TypeHandler 做到**大小写透明**。
* `TimeWindow` 映射两列，构造器校验 `from < to`；数据库加 `CHECK` 双保险。
* **幂等等键**在领域中作为**值对象**参与等价性判断；库中建唯一索引兜底。
* 读多写少的查询构造成 **Query Port**，避免在应用层拼 SQL。
