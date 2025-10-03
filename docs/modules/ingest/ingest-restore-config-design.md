# Ingest 还原 Plan/PlanSlice 与 Source 配置（流程 C · 步骤 2）

作者：linqibin（建议）｜适用：patra-ingest 服务｜版本：v0.1 草案

## 1. 目标与范围
- 目标：在步骤 0/1（租约抢占 + 会话初始化）之后，基于 Plan/PlanSlice 的快照信息，恢复执行期所需的“来源配置与表达式上下文”，并将一致性标记落入 `ing_task_run`，为后续重放与对账提供依据。
- 输入（以快照为准）：
  - Plan：`plan.*`（含 `expr_proto_hash/expr_proto_snapshot_json`、`provenance_config_snapshot_json/provenance_config_hash`、`window_from/to`、`slice_strategy_code/params`）
  - PlanSlice：`plan_slice.*`（含 `slice_signature_hash/slice_spec_json/expr_hash/expr_snapshot_json`）
  - Source 配置：`plan.provenance_config_snapshot`（JSON）
- 输出：
  - 解析后的 `ProvenanceConfigSnapshot`（domain snapshot 对象）与“执行期有效视图（EffectiveSpec）”
  - 一致性标记：`config_hash`、`snapshot_hash`（写入 `ing_task_run.checkpoint` JSON）
  - 校验结果（必要项缺失 → 终止失败）
  - 若解析/校验耗时超出 `ttl/3`，执行一次续租心跳

## 2. 领域对象复用（强制）
- 使用已存在的 Domain Snapshot：`com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot`
  - Jackson 可直接将 JSON 反序列化为该 record（无自定义 POJO）
  - 参考处：
    - 计划装配中已使用 `JsonNormalizer` 与 `HashUtils` 生成 plan 级 `provenance_config_hash`（见 `PlanAssemblerImpl.normalizeConfigSnapshot` 与 `createPlanAggregate`）
- 解析建议：
```java
var om = JsonMapperHolder.getObjectMapper();
ProvenanceConfigSnapshot snapshot = om.readValue(plan.getProvenanceConfigSnapshotJson(), ProvenanceConfigSnapshot.class);
```

## 3. 有效视图（EffectiveSpec）构造与合并规则
- 目的：把“端点覆盖项（EndpointDefinition）”与全局维度（pagination/http/rateLimit/retry/batching/windowOffset/credentials）合并为“本任务/本端点的执行期有效配置”。
- 合并优先级（高→低）：EndpointDefinition 覆盖字段 > 对应全局维度 > 来源默认值
- 关键字段与合并示例：
  - endpoint：`name/usage/method/pathTemplate/defaultQueryParams/defaultBodyPayload/authRequired/credentialHintName`
  - pagination：
    - 模式：`endpoint.cursorParamName/pageParamName/pageSizeParamName` 任一存在即生效覆盖；否则使用 `snapshot.pagination.paginationModeCode`
    - 参数：以 endpoint 覆盖同名字段；否则取 `snapshot.pagination` 对应字段
  - windowOffset（位点）：
    - 模式/字段：`windowModeCode/offsetTypeCode/offsetFieldName/offsetDateFormat/timeUnit/overlap/lookback/watermarkLagSeconds` 直接来自 `snapshot.windowOffset`
  - http：`baseUrlOverride/defaultHeaders/timeout/代理/tls`（endpoint 无覆盖则使用 http 全局；baseUrl = `http.baseUrlOverride` 或 `provenance.baseUrlDefault`）
  - rateLimit/retry/batching：直接引用快照；如有 endpoint 绑定 id/credentialName 则优先选对应记录
  - credentials：
    - 若 endpoint.authRequired=true：优先 endpoint 绑定（credentialHintName 或 endpointId 指向）→ 其次 `defaultPreferred=true` → 否则取首个 ACTIVE
- 输出 DTO（应用层内部 DTO，非领域）：
```java
record EffectiveSpec(
  // endpoint
  String endpointName, String endpointUsage, String httpMethod, String pathTemplate,
  String baseUrl, String defaultQueryParamsJson, String defaultBodyPayloadJson,
  boolean authRequired, String credentialResolved,
  // pagination
  String paginationMode, Integer pageSize, String pageParam, String pageSizeParam,
  String cursorParam, String initialCursor, String nextCursorPath, String hasMorePath,
  // window offset
  String windowMode, Integer windowSizeValue, String windowSizeUnit, String calendarAlignTo,
  String offsetType, String offsetFieldName, String offsetDateFormat,
  Integer lookbackValue, String lookbackUnit, Integer overlapValue, String overlapUnit,
  Integer watermarkLagSeconds,
  // http/rate/retry/batching（摘要）
  String httpTimeouts, String retryPolicy, String rateLimitSummary, String batchingSummary
) {}
```

## 4. 一致性标记（config_hash / snapshot_hash）
- 规范化与哈希：重用 `JsonNormalizer.usingDefault()` 与 `HashUtils.sha256Hex(..)`，保证与 Plan 生成期一致
- `config_hash`：
  - 取 `plan.provenance_config_hash`（计划期计算）作为“基线”
  - 运行期再次规范化 `ProvenanceConfigSnapshot`（仅用于校验一致性）：
    - 若 `runtimeConfigHash != plan.provenanceConfigHash` → 记录两者，置告警字段，供后续分析
- `snapshot_hash`（执行期快照哈希）：
  - 建议包含“有效视图 + 切片表达式哈希”的组合：
    - `material = canonicalJson(EffectiveSpec) + '|' + slice.exprHash`
    - `snapshot_hash = sha256Hex(material)`
- 落库位置：`ing_task_run.checkpoint`（JSON，自定义结构）
  - 建议结构：
```json
{
  "configHashFromPlan": "<plan.provenance_config_hash>",
  "configHashRuntime": "<sha256 of canonical(snapshot)>",
  "snapshotHash": "<sha256(EffectiveSpec|slice.exprHash)>",
  "endpoint": { "name": "search", "usage": "SEARCH", "method": "GET" },
  "pagination": { "mode": "CURSOR", "cursorParam": "cursor", "pageSize": 200 },
  "windowOffset": { "mode": "SLIDING", "offsetType": "DATE", "offsetField": "updated_at" },
  "http": { "baseUrl": "https://api.example.com" },
  "rateLimit": { "summary": "qps=10,burst=100" },
  "retry": { "policy": "EXP_JITTER(3)" },
  "batching": { "detailBatchSize": 50 },
  "credentials": { "resolved": "default-key" },
  "expr": { "planExprProtoHash": "...", "sliceExprHash": "..." }
}
```

## 5. 校验规则与失败语义
- 必要项：
  - endpoint：`endpointName/httpMethod/pathTemplate` 不得为空
  - 分页/游标：`paginationMode` 必须存在，且对应关键参数需完整（PAGE_NUMBER→pageParam/pageSizeParam；CURSOR/TOKEN→cursorParam 与 nextCursor/hasMore 提取路径至少其一）
  - 位点规则：`offsetType` 与对应字段（DATE→offsetFieldName/offsetDateFormat；ID→offsetFieldName）
  - 鉴权：当 `authRequired=true` 时，必须解析出可用凭证（credentialResolved）
  - HTTP：`baseUrl` 必备（endpoint 覆盖或来源默认）
- 不通过 → 标记本次 run 为“不可恢复失败（FAILED_TERMINAL）”：
  - 写入 `ing_task_run.status_code='FAILED'`，`error='CONFIG_INVALID: <reason>'`，`finished_at=now`
  - 打点：`ingest.task.run.failed{reason=config_invalid}`
  - 建议任务级后续不再重试（具体策略由外层统一控制）

## 6. 续租策略（长耗时保护）
- 度量解析+校验耗时 `elapsedMs`
- 若 `elapsedMs > ttl/3 * 1000`：执行一次心跳续租（与步骤 1 复用 `renewLease`）
  - 续租 SQL 见步骤 0/1 文档或附录 B.2
  - 续租失败（更新=0）→ 视为租约丢失：WARN 日志并终止后续执行（ACK）

## 7. 编排位置与接口建议
- 放置在 `TaskExecutionUseCase` 的同一会话上下文中（步骤 1 之后立即执行）
- 伪代码：
```java
Instant t0 = Instant.now();
PlanAggregate plan = planRepo.findById(cmd.planId());
PlanSliceAggregate slice = sliceRepo.findById(cmd.sliceId());
ProvenanceConfigSnapshot snap = om.readValue(plan.getProvenanceConfigSnapshotJson(), ProvenanceConfigSnapshot.class);
EffectiveSpec eff = EffectiveSpecBuilder.from(plan, slice, snap);
String runtimeConfigHash = sha256Hex(JsonNormalizer.usingDefault().normalize(snap).getHashMaterial());
String snapshotHash = sha256Hex(canonical(eff) + "|" + slice.getExprHash());
String checkpointJson = buildCheckpointJson(plan, slice, eff, runtimeConfigHash, snapshotHash);
runRepo.updateCheckpointAndHeartbeat(runId, checkpointJson, Instant.now());
if (Duration.between(t0, Instant.now()).toMillis() > ttl/3*1000) {
    taskRepo.renewLease(taskId, owner, Instant.now(), ttl);
}
```

## 8. 测试与验收
- 配置一致性：`runtimeConfigHash == plan.provenanceConfigHash`（正常）/不等（记录差异但不中断；或按策略中断）
- 必要项缺失：触发 FAILED_TERMINAL 路径，`status_code=FAILED` 且 `error` 含原因
- 续租触发：刻意放大解析成本，验证 `elapsed > ttl/3` 时有一次续租且成功
- 快照重放：通过 `ing_task_run.checkpoint` 还原 EffectiveSpec，重算 `snapshotHash` 一致

---

# 附录 A：规范化与哈希（代码片段）
```java
JsonNormalizer normalizer = JsonNormalizer.usingDefault();
JsonNormalizer.Result cfgCanon = normalizer.normalize(snapshot);
String runtimeConfigHash = HashUtils.sha256Hex(cfgCanon.getHashMaterial());

JsonNormalizer.Result effCanon = normalizer.normalize(effectiveSpec); // DTO 可被正常规范化
String snapshotHash = HashUtils.sha256Hex(effCanon.getCanonicalJson() + '|' + slice.getExprHash());
```

# 附录 B：持久化操作（建议放 XML）
## B.1 写入 checkpoint（覆盖式）
```xml
<mapper namespace="com.patra.ingest.infra.persistence.mapper.TaskRunMapper">
  <update id="updateCheckpointAndHeartbeat">
    UPDATE ing_task_run
       SET checkpoint = CAST(#{checkpointJson} AS JSON),
           last_heartbeat = #{now}
     WHERE id = #{runId}
  </update>
</mapper>
```

## B.2 续租（心跳）
```xml
<mapper namespace="com.patra.ingest.infra.persistence.mapper.TaskMapper">
  <update id="renewLease">
    UPDATE ing_task
       SET leased_until = DATE_ADD(#{now}, INTERVAL #{ttlSec} SECOND),
           last_heartbeat_at = #{now},
           lease_count = lease_count + 1
     WHERE id = #{taskId}
       AND lease_owner = #{owner}
  </update>
</mapper>
```

## B.3 终止失败（配置不完整）
```xml
<mapper namespace="com.patra.ingest.infra.persistence.mapper.TaskRunMapper">
  <update id="markRunFailedTerminal">
    UPDATE ing_task_run
       SET status_code = 'FAILED',
           error = #{errorMsg},
           finished_at = #{now}
     WHERE id = #{runId}
  </update>
</mapper>
```

# 附录 C：与现有代码的对应关系
- Plan 聚合：`patra-ingest-domain/.../PlanAggregate.java`（快照与哈希字段）
- Slice 聚合：`patra-ingest-domain/.../PlanSliceAggregate.java`（exprHash/exprSnapshotJson）
- Snapshot 类型：`patra-ingest-domain/.../snapshot/ProvenanceConfigSnapshot.java`
- 规范化与哈希：`PlanAssemblerImpl` 中已使用（保证一致性）
- Run 映射：`TaskRunConverter`（checkpoint JSON 映射为原样字符串）
