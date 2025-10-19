# PubMed WebEnv 缓存优化设计

## 文档信息

- **作者**: linqibin
- **创建日期**: 2025-01-19
- **状态**: 设计阶段
- **影响范围**: patra-ingest (PubMed 数据源)

---

## 1. 背景与问题

### 1.1 当前问题

在 PubMed 数据采集流程中，Plan 阶段和 Execute 阶段存在重复的 API 调用：

1. **Plan 阶段**：调用 `PubmedSearchPort.estimateCount()` 获取搜索结果总数
   - 使用 ESearch API with `rettype=count`
   - 只返回 `int` 类型的 count 值

2. **Execute 阶段**：每个 Batch 都会重新调用 ESearch API
   - 使用相同的 query 和 date filters
   - 每次都需要 PubMed 重新执行搜索查询
   - 对于复杂查询（多条件 + 日期范围），开销很大

**问题示例**：
```
假设搜索返回 100,000 篇文献，pageSize=5000，需要 20 个 batch
- Plan 阶段：1 次 ESearch (rettype=count)
- Execute 阶段：20 次 ESearch (重复执行相同搜索) + 20 次 EFetch
- 总计 21 次 ESearch，其中 20 次是重复的
```

### 1.2 性能影响

- **API 调用开销**：复杂查询重复执行 20+ 次
- **PubMed 服务器负载**：每次都需要重新计算搜索结果
- **数据一致性风险**：Plan 和 Execute 之间 PubMed 数据可能变化，导致 count 不匹配

### 1.3 ⚠️ 性能优化的边界

**重要说明**：本优化主要针对 **ESearch 阶段**，对整体性能的提升取决于 ESearch 在总时间中的占比。

实际数据采集流程包括两个主要阶段：
1. **ESearch**：根据查询获取 PMID 列表
2. **EFetch**：根据 PMID 获取完整文献数据

**性能收益评估**：
- 如果 EFetch 占总时间的 50%，优化 ESearch 可带来约 30-40% 的整体提升
- 如果 EFetch 占总时间的 80%，优化 ESearch 仅能带来约 10-15% 的整体提升
- **建议**：在实施前测量 ESearch vs EFetch 的实际时间占比，以准确评估收益

---

## 2. 解决方案

### 2.1 PubMed WebEnv 机制

PubMed E-utilities 提供 **History Server** 功能：

- **usehistory=y**：告诉 PubMed 保存搜索结果到服务器端
- **WebEnv**：会话标识符（有效期约 8 小时）
- **QueryKey**：查询键，标识特定的搜索结果集

后续请求可以使用 `WebEnv + QueryKey + retstart/retmax` 读取缓存的结果，无需重新执行搜索。

**参考文档**: [PubMed E-utilities History Server](https://www.ncbi.nlm.nih.gov/books/NBK25497/#chapter2.History_Server)

### 2.2 优化方案

**核心思路**：Plan 阶段获取 WebEnv 缓存句柄，Execute 阶段使用缓存读取数据

```
┌─────────────────────────────────────────────────────────────┐
│ Plan 阶段                                                     │
│  1. ESearch with usehistory=y, retmax=0                     │
│  2. 返回: count + WebEnv + QueryKey (不获取任何 ID)          │
│  3. 将 WebEnv/QueryKey 添加到每个 Batch 的 params           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Execute 阶段 (Batch 1...N)                                   │
│  1. ESearch with WebEnv + QueryKey + retstart/retmax        │
│  2. PubMed 从缓存读取该批次的 PMID (不重新搜索)              │
│  3. EFetch 获取详细数据                                      │
└─────────────────────────────────────────────────────────────┘
```

**关键收益**：
- ✅ Execute 阶段的 ESearch 是基于服务器端缓存的快速分页读取
- ✅ 避免重复执行复杂的搜索查询（布尔逻辑 + 日期过滤）
- ✅ 数据一致性：WebEnv 锁定结果集，Plan 和 Execute 看到相同数据

**⚠️ 数据一致性语义变化**：

使用 WebEnv 后，结果集会被"锁定"：
- **优点**：Plan 和 Execute 阶段看到完全相同的数据，避免 count 不匹配
- **语义变化**：Plan 阶段执行后，PubMed 新增的符合条件的文献**不会被采集**

**示例**：
```
10:00 - Plan 阶段执行，搜索到 10000 篇文献，WebEnv 创建
10:02 - PubMed 新增 100 篇符合条件的文献
10:05 - Execute 阶段执行，仍然只采集最初的 10000 篇（WebEnv 锁定）
结果：新增的 100 篇不会被本次任务采集
```

**适用场景判断**：
- ✅ 历史数据采集：期望 Plan 和 Execute 一致，适合使用 WebEnv
- ⚠️ 实时数据采集：如果需要获取"最新"数据，可能需要配置开关控制是否使用 WebEnv

---

## 3. 技术设计

### 3.1 架构原则

**职责划分**：
- **Plan 阶段**：快速规划（获取 count + 缓存句柄）
- **Execute 阶段**：可靠执行（使用缓存获取数据）

**依赖方向**：
- Domain 层定义 Port 和 VO（PlanMetadata）
- Infra 层实现 Port（调用 PubMed API）
- App 层编排流程（Planner 使用 Port）

### 3.2 数据模型

#### 3.2.1 PlanMetadata (新增)

**位置**: `patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/PlanMetadata.java`

```java
package com.patra.ingest.domain.model.vo;

/**
 * Plan 阶段从数据源获取的元数据。
 *
 * <p>封装了规划执行批次所需的信息：
 * <ul>
 *   <li>总数：用于计算批次数量
 *   <li>缓存句柄：用于后续批次从服务器端缓存读取数据（如 PubMed WebEnv）
 * </ul>
 *
 * @param totalCount 搜索结果总数
 * @param webEnv     WebEnv 缓存句柄 (PubMed History Server token, nullable)
 * @param queryKey   查询键 (PubMed query key for reuse, nullable)
 * @author linqibin
 * @since 0.1.0
 */
public record PlanMetadata(int totalCount, String webEnv, String queryKey) {

  public PlanMetadata {
    if (totalCount < 0) {
      throw new IllegalArgumentException("totalCount must be >= 0");
    }

    // ⚠️ 重要：WebEnv 和 QueryKey 必须成对出现或同时缺失
    boolean hasWebEnv = webEnv != null && !webEnv.isBlank();
    boolean hasQueryKey = queryKey != null && !queryKey.isBlank();

    if (hasWebEnv != hasQueryKey) {
      throw new IllegalArgumentException(
          "webEnv and queryKey must be both present or both absent. " +
          "webEnv=" + (hasWebEnv ? "present" : "absent") + ", " +
          "queryKey=" + (hasQueryKey ? "present" : "absent"));
    }
  }

  /**
   * Create empty metadata indicating no results.
   *
   * @return empty plan metadata
   */
  public static PlanMetadata empty() {
    return new PlanMetadata(0, null, null);
  }

  /**
   * Check if WebEnv cache handle is available.
   *
   * @return true if both webEnv and queryKey are present
   */
  public boolean hasWebEnv() {
    return webEnv != null && !webEnv.isBlank();
  }
}
```

#### 3.2.2 Batch (无需修改)

现有的 `Batch` record 通过 `params` 字段传递 WebEnv/QueryKey，无需修改结构。

```java
public record Batch(
    int batchNo,
    String query,
    JsonNode params,        // ← WebEnv/QueryKey 通过这里传递
    String cursorToken,
    Integer expectedCount
)
```

### 3.3 接口设计

#### 3.3.1 PubmedSearchPort (修改)

**位置**: `patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/PubmedSearchPort.java`

**改动**：
1. 方法重命名：`estimateCount` → `preparePlanMetadata`
2. 返回类型：`int` → `PlanMetadata`

```java
package com.patra.ingest.domain.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.PlanMetadata;

/**
 * Domain port for PubMed search metadata.
 *
 * <p>Responsibility: provide planning metadata for PubMed searches, including result count
 * and cache handles (WebEnv) for efficient batch execution.
 */
public interface PubmedSearchPort {

  /**
   * Prepare planning metadata for the given PubMed query.
   *
   * <p>This method calls PubMed ESearch API with usehistory=y to obtain:
   * <ul>
   *   <li>Result count for batch planning
   *   <li>WebEnv and QueryKey for Execute stage to reuse cached results
   * </ul>
   *
   * @param query compiled Boolean query string
   * @param params compiled parameters JSON (e.g., datetype/mindate/maxdate/reldate/sort)
   * @param provenanceConfigSnapshot configuration snapshot for the current execution
   * @return plan metadata containing count and cache handles
   */
  PlanMetadata preparePlanMetadata(
      String query, JsonNode params, ProvenanceConfigSnapshot provenanceConfigSnapshot);
}
```

---

## 4. 实施步骤

### 4.1 Domain 层

#### 步骤 1：创建 PlanMetadata

**文件**: `patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/PlanMetadata.java`

**操作**: 创建新文件，参考 3.2.1 节的代码

#### 步骤 2：修改 PubmedSearchPort

**文件**: `patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/PubmedSearchPort.java`

**操作**:
1. 导入 `PlanMetadata`
2. 重命名方法 `estimateCount` → `preparePlanMetadata`
3. 修改返回类型 `int` → `PlanMetadata`
4. 更新 Javadoc

---

### 4.2 Infra 层

#### 步骤 3：修改 PubmedSearchPortImpl

**文件**: `patra-ingest-infra/src/main/java/com/patra/ingest/infra/rpc/pubmed/PubmedSearchPortImpl.java`

**操作**:

1. **重命名方法并修改返回类型**

```java
@Override
public PlanMetadata preparePlanMetadata(
    String query, JsonNode params, ProvenanceConfigSnapshot provenanceConfigSnapshot) {
  try {
    // 添加 usehistory=y 和 retmax=0
    JsonNode enrichedParams = addUseHistory(params);

    // 使用 buildList 而不是 buildCount
    ESearchRequest request = ASSEMBLER.buildList(enrichedParams);

    String termHash = safeHash(request.term());
    ProvenanceConfig config = toProvenanceConfig(provenanceConfigSnapshot);

    ESearchResponse response =
        config != null
            ? pubMedClient.esearch(request, config)
            : pubMedClient.esearch(request);

    if (response == null || response.result() == null) {
      log.warn("[INGEST][INFRA] pubmed esearch returned null termHash={}", termHash);
      return PlanMetadata.empty();
    }

    ESearchResponse.Result result = response.result();
    int count = result.count();
    String webEnv = result.webEnv();
    String queryKey = result.queryKey();

    log.info(
        "[INGEST][INFRA] pubmed esearch metadata termHash={} count={} webEnv={} queryKey={}",
        termHash,
        count,
        webEnv != null ? "present" : "absent",
        queryKey != null ? "present" : "absent");

    // ⚠️ 增强日志：检测 WebEnv 和 QueryKey 的一致性
    if ((webEnv != null && !webEnv.isBlank()) && (queryKey == null || queryKey.isBlank())) {
      log.warn(
          "[INGEST][INFRA] pubmed esearch returned WebEnv but no QueryKey termHash={} count={}",
          termHash,
          count);
    }

    return new PlanMetadata(Math.max(count, 0), webEnv, queryKey);

  } catch (ProvenanceClientException ex) {
    String msg = String.format("PubMed metadata lookup failed: %s", ex.getMessage());
    log.error("[INGEST][INFRA] {} termHash={}", msg, safeHash(query), ex);
    throw new BatchPlanningException(msg, ex);
  } catch (Exception ex) {
    String msg = String.format("PubMed metadata lookup unexpected error: %s", ex.getMessage());
    log.error("[INGEST][INFRA] {} termHash={}", msg, safeHash(query), ex);
    throw new BatchPlanningException(msg, ex);
  }
}
```

2. **添加 addUseHistory 辅助方法**

```java
/**
 * Enrich params with usehistory=y and retmax=0.
 *
 * <p>usehistory=y tells PubMed to save search results to History Server.
 * retmax=0 means we only need metadata (count + WebEnv), not the ID list.
 *
 * @param params original params
 * @return enriched params
 */
private JsonNode addUseHistory(JsonNode params) {
  ObjectNode node;
  if (params == null || params.isNull()) {
    node = objectMapper.createObjectNode();
  } else if (params.isObject()) {
    node = ((ObjectNode) params).deepCopy();
  } else {
    throw new IllegalArgumentException("params must be an object node");
  }

  // 只在不存在时添加（允许用户覆盖）
  if (!node.has("usehistory")) {
    node.put("usehistory", "y");
  }

  // retmax=0: 只获取元数据，不获取 ID 列表
  node.put("retmax", "0");

  return node;
}
```

**关键说明**：
- `retmax=0`：只获取元数据（count + WebEnv），不获取任何 PMID
- `usehistory=y`：PubMed 保存搜索结果到 History Server
- 使用 `buildList()` 而不是 `buildCount()`，因为 rettype=count 不返回 WebEnv

---

### 4.3 App 层

#### 步骤 4：修改 PubmedBatchPlanner

**文件**: `patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/execute/PubmedBatchPlanner.java`

**操作**:

1. **修改 plan() 方法**

```java
@Override
public BatchPlan plan(ExecutionContext ctx) {
  String compiledQuery = ctx.compiledQuery();
  JsonNode compiledParams = ctx.compiledParams();

  // 验证 query/params (保持原有逻辑)
  boolean hasQuery = compiledQuery != null && !compiledQuery.isBlank();
  boolean hasParams = compiledParams != null && !compiledParams.isEmpty();

  if (!hasQuery && !hasParams) {
    throw new BatchPlanningException(
        "Both compiledQuery and compiledParams are empty for PubMed batch planning");
  }

  ObjectNode baseParams = toObjectNode(compiledParams);

  // *** 改动：调用 preparePlanMetadata 替代 estimateCount ***
  PlanMetadata metadata =
      searchPort.preparePlanMetadata(compiledQuery, ctx.compiledParams(), ctx.configSnapshot());

  int total = metadata.totalCount();
  if (total <= 0) {
    log.info("[INGEST][APP] pubmed planner: no results termHash={}", safeHash(compiledQuery));
    return BatchPlan.empty();
  }

  int pageSize = resolvePageSize(baseParams, ctx.configSnapshot());
  int maxPages = resolveMaxPages(ctx.configSnapshot());

  int pagesNeeded = (int) Math.ceil(total / (double) pageSize);
  if (pagesNeeded > maxPages) {
    log.warn(
        "[INGEST][APP] pubmed planner fail-fast: pagesNeeded={} > maxPages={} termHash={} pageSize={} total={}",
        pagesNeeded,
        maxPages,
        safeHash(compiledQuery),
        pageSize,
        total);
    return new BatchPlan(List.of(), pagesNeeded, true);
  }

  int pages = Math.min(pagesNeeded, maxPages);
  List<Batch> batches = new ArrayList<>(pages);

  for (int i = 0; i < pages; i++) {
    int retstart = i * pageSize;
    ObjectNode batchParams = baseParams.deepCopy();

    // 设置分页参数
    batchParams.put("retstart", retstart);
    batchParams.put("retmax", pageSize);
    batchParams.put("retmode", "json");

    // *** 关键改动：添加 WebEnv 和 QueryKey ***
    if (metadata.hasWebEnv()) {
      batchParams.put("WebEnv", metadata.webEnv());        // 注意大写 W
      batchParams.put("query_key", metadata.queryKey());   // 注意小写和下划线
    }

    // 移除 rettype=count (保持原有逻辑)
    if (batchParams.has("rettype")) {
      batchParams.remove("rettype");
    }

    batches.add(new Batch(i + 1, compiledQuery, batchParams, null, null));
  }

  log.info(
      "[INGEST][APP] pubmed planner: planned {} batches termHash={} pageSize={} total={} webEnv={}",
      pages,
      safeHash(compiledQuery),
      pageSize,
      total,
      metadata.hasWebEnv() ? "enabled" : "disabled");

  return new BatchPlan(batches, pages, false);
}
```

**关键说明**：
- `WebEnv`（大写 W）：PubMed API 使用的参数名，见 `PubMedParamKeys.WEBENV`
- `query_key`（小写 + 下划线）：PubMed API 使用的参数名，见 `PubMedParamKeys.QUERY_KEY`
- 通过 `metadata.hasWebEnv()` 判断是否启用缓存（向后兼容）

---

### 4.4 Execute 阶段（无需改动）

**说明**：`PubMedESearchRequestAssembler` 已经支持提取 `webenv` 和 `query_key` 参数（见 `buildList()` 方法第 62-64 行）。

Execute 阶段的 BatchExecutor 调用 ESearch 时：
1. 读取 `batch.params()`
2. `ASSEMBLER.buildList(params)` 自动提取 WebEnv/QueryKey
3. PubMed API 使用 WebEnv 从缓存读取该批次的 PMID
4. 如果 WebEnv 过期，PubMed 会自动 fallback 到重新搜索（只是慢一些）

**无需任何改动，自动生效！**

---

## 5. 测试验证

### 5.1 单元测试

#### 5.1.1 PlanMetadata 测试

**文件**: `patra-ingest-domain/src/test/java/com/patra/ingest/domain/model/vo/PlanMetadataTest.java`

```java
class PlanMetadataTest {

  @Test
  void shouldCreateValidMetadata() {
    PlanMetadata metadata = new PlanMetadata(100, "env123", "key456");

    assertThat(metadata.totalCount()).isEqualTo(100);
    assertThat(metadata.webEnv()).isEqualTo("env123");
    assertThat(metadata.queryKey()).isEqualTo("key456");
    assertThat(metadata.hasWebEnv()).isTrue();
  }

  @Test
  void shouldCreateEmptyMetadata() {
    PlanMetadata metadata = PlanMetadata.empty();

    assertThat(metadata.totalCount()).isEqualTo(0);
    assertThat(metadata.webEnv()).isNull();
    assertThat(metadata.queryKey()).isNull();
    assertThat(metadata.hasWebEnv()).isFalse();
  }

  @Test
  void shouldRejectNegativeCount() {
    assertThatThrownBy(() -> new PlanMetadata(-1, "env", "key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("totalCount must be >= 0");
  }

  @Test
  void shouldRejectWebEnvWithoutQueryKey() {
    // WebEnv 存在但 QueryKey 为 null
    assertThatThrownBy(() -> new PlanMetadata(100, "env123", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("webEnv and queryKey must be both present or both absent");
  }

  @Test
  void shouldRejectQueryKeyWithoutWebEnv() {
    // QueryKey 存在但 WebEnv 为 null
    assertThatThrownBy(() -> new PlanMetadata(100, null, "key456"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("webEnv and queryKey must be both present or both absent");
  }

  @Test
  void shouldAcceptBothNullOrBothPresent() {
    // 两者都为 null（合法）
    PlanMetadata both Null = new PlanMetadata(100, null, null);
    assertThat(bothNull.hasWebEnv()).isFalse();

    // 两者都存在（合法）
    PlanMetadata bothPresent = new PlanMetadata(100, "env", "key");
    assertThat(bothPresent.hasWebEnv()).isTrue();
  }
}
```

#### 5.1.2 PubmedBatchPlanner 测试

**文件**: `patra-ingest-app/src/test/java/com/patra/ingest/app/usecase/execution/execute/PubmedBatchPlannerTest.java`

```java
class PubmedBatchPlannerTest {

  @Mock
  private PubmedSearchPort searchPort;

  private PubmedBatchPlanner planner;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    planner = new PubmedBatchPlanner(searchPort, new ObjectMapper(), 10000);
  }

  @Test
  void shouldAddWebEnvToAllBatches() {
    // Given
    PlanMetadata metadata = new PlanMetadata(15000, "webenv123", "querykey456");
    when(searchPort.preparePlanMetadata(any(), any(), any())).thenReturn(metadata);

    ExecutionContext ctx = createTestContext();

    // When
    BatchPlan plan = planner.plan(ctx);

    // Then
    assertThat(plan.batches()).hasSize(3);  // 15000 / 5000 = 3

    for (Batch batch : plan.batches()) {
      JsonNode params = batch.params();
      assertThat(params.has("WebEnv")).isTrue();
      assertThat(params.get("WebEnv").asText()).isEqualTo("webenv123");
      assertThat(params.has("query_key")).isTrue();
      assertThat(params.get("query_key").asText()).isEqualTo("querykey456");
    }
  }

  @Test
  void shouldHandleMissingWebEnv() {
    // Given: WebEnv not returned by PubMed
    PlanMetadata metadata = new PlanMetadata(5000, null, null);
    when(searchPort.preparePlanMetadata(any(), any(), any())).thenReturn(metadata);

    ExecutionContext ctx = createTestContext();

    // When
    BatchPlan plan = planner.plan(ctx);

    // Then: should still work, just without WebEnv optimization
    assertThat(plan.batches()).hasSize(1);
    assertThat(plan.batches().get(0).params().has("WebEnv")).isFalse();
  }
}
```

### 5.2 集成测试

#### 5.2.1 PubmedSearchPortImpl 集成测试

**文件**: `patra-ingest-boot/src/test/java/com/patra/ingest/infra/PubmedSearchPortImplIntegrationTest.java`

```java
@SpringBootTest
class PubmedSearchPortImplIntegrationTest {

  @Autowired
  private PubmedSearchPort searchPort;

  @Test
  void shouldReturnWebEnvForValidQuery() {
    // Given
    String query = "covid-19[Title]";
    ObjectNode params = JsonMapperHolder.getObjectMapper().createObjectNode();
    params.put("mindate", "2020/01/01");
    params.put("maxdate", "2020/12/31");
    params.put("datetype", "pdat");

    // When
    PlanMetadata metadata = searchPort.preparePlanMetadata(query, params, null);

    // Then
    assertThat(metadata.totalCount()).isGreaterThan(0);
    assertThat(metadata.hasWebEnv()).isTrue();  // WebEnv should be returned
    assertThat(metadata.webEnv()).isNotBlank();
    assertThat(metadata.queryKey()).isNotBlank();
  }

  @Test
  void shouldReturnWebEnvWithRetmaxZero() {
    // ✅ 核心验证：retmax=0 + usehistory=y 是否返回 WebEnv
    // Given
    String query = "asthma[Title]";
    ObjectNode params = JsonMapperHolder.getObjectMapper().createObjectNode();
    // params 不包含 retmax，由 preparePlanMetadata 自动添加 retmax=0

    // When
    PlanMetadata metadata = searchPort.preparePlanMetadata(query, params, null);

    // Then
    assertThat(metadata.totalCount()).isGreaterThan(0);
    assertThat(metadata.hasWebEnv()).isTrue();  // ✅ retmax=0 仍然返回 WebEnv
    assertThat(metadata.webEnv()).isNotBlank();
    assertThat(metadata.queryKey()).isNotBlank();
  }

  @Test
  void shouldHandleEmptyResults() {
    // Given: 一个不存在的查询
    String query = "xyzabc123nonexistent[Title]";
    ObjectNode params = JsonMapperHolder.getObjectMapper().createObjectNode();

    // When
    PlanMetadata metadata = searchPort.preparePlanMetadata(query, params, null);

    // Then
    assertThat(metadata.totalCount()).isEqualTo(0);
    // PubMed 在 count=0 时可能不返回 WebEnv，这是正常的
  }
}
```

### 5.3 端到端测试

#### 5.3.1 完整流程测试

**验证点**：
1. Plan 阶段返回 WebEnv
2. Batch params 包含 WebEnv/QueryKey
3. Execute 阶段成功使用 WebEnv 获取数据
4. 对比优化前后的执行时间

**测试步骤**：
```bash
# 1. 创建测试任务（大批量查询）
POST /api/tasks
{
  "provenanceCode": "PUBMED",
  "query": "diabetes[Title] AND (2020[PDAT] : 2023[PDAT])"
}

# 2. 观察日志
# Plan 阶段应该输出：
[INGEST][INFRA] pubmed esearch metadata termHash=xxx count=50000 webEnv=present queryKey=present
[INGEST][APP] pubmed planner: planned 10 batches ... webEnv=enabled

# Execute 阶段应该输出（每个 batch）：
[INGEST][APP] execute batch start taskId=xxx runId=xxx batchNo=1/10

# 3. 性能对比
# 记录优化前后的批量导入时间
```

---

## 6. 关键注意事项

### 6.1 字段名称大小写

⚠️ **重要**：PubMed API 对参数名称的大小写敏感

- `WebEnv`（大写 W）：必须使用大写，见 `PubMedParamKeys.WEBENV = "WebEnv"`
- `query_key`（小写 + 下划线）：必须使用小写，见 `PubMedParamKeys.QUERY_KEY = "query_key"`

### 6.2 WebEnv 有效期

- **有效期**：约 8 小时（PubMed 官方文档）
- **正常情况**：Plan → Execute 在分钟级，风险极低
- **降级策略**：WebEnv 过期时，PubMed API 会自动重新执行搜索（只是慢一些）

### 6.3 retmax=0 的含义

- `retmax=0` 告诉 PubMed **只返回元数据**（count + WebEnv），不返回任何 PMID
- 这样 Plan 阶段的响应最快，符合"快速规划"的原则

**✅ 验证结果**（2025-01-19）：
```xml
<eSearchResult>
  <Count>236539</Count>
  <RetMax>0</RetMax>
  <QueryKey>1</QueryKey>
  <WebEnv>MCID_68f4fb33846ce9a2530c04a9</WebEnv>
  <IdList/>
</eSearchResult>
```
- 确认：`retmax=0 + usehistory=y` **确实返回 WebEnv 和 QueryKey**
- IdList 为空（符合预期）
- 核心假设验证通过 ✅

### 6.4 向后兼容性

- 如果 PubMed 不返回 WebEnv（某些边界情况），代码会优雅降级
- `metadata.hasWebEnv()` 返回 false 时，不添加 WebEnv 到 params
- Execute 阶段会像以前一样重新搜索（无优化，但不会报错）

### 6.5 对其他数据源的影响

- ✅ **零影响**：改动只涉及 PubMed 相关的类
- ✅ **隔离性好**：每个数据源有独立的 Port 和 Planner
- ✅ **可复用模式**：其他数据源（如 EPMC）可以参考相同的优化模式

### 6.6 配置值获取

从 `ProvenanceConfigSnapshot` 读取 pageSize：

```java
int pageSize = resolvePageSize(baseParams, ctx.configSnapshot());

private int resolvePageSize(ObjectNode baseParams, ProvenanceConfigSnapshot config) {
  // 优先使用 params 中的配置
  Integer paramsRetmax = intOrNull(baseParams, "retmax");
  if (paramsRetmax != null && paramsRetmax > 0) {
    return Math.min(paramsRetmax, pubmedRetmaxLimit);
  }

  // 其次使用配置快照
  if (config != null && config.pagination() != null) {
    Integer configPageSize = config.pagination().pageSizeValue();
    if (configPageSize != null && configPageSize > 0) {
      return Math.min(configPageSize, pubmedRetmaxLimit);
    }
  }

  // 最后使用默认值
  return Math.min(5000, pubmedRetmaxLimit);
}
```

---

## 7. 预期收益

### 7.1 性能提升

**⚠️ 重要前提**：以下预期基于 **ESearch 阶段的优化**。实际整体性能提升取决于 ESearch 在总时间中的占比。

#### 7.1.1 ESearch 阶段性能提升

| 场景 | 当前 ESearch | 优化后 ESearch | ESearch 提升 |
|------|-------------|---------------|-------------|
| 简单查询（单 term） | 100ms/batch | 80-90ms/batch | 10-20% |
| 复杂查询（多条件 + 日期） | 500ms/batch | 100-150ms/batch | 70-80% |

#### 7.1.2 整体性能提升（取决于 EFetch 占比）

**场景 1：EFetch 占 50%**
```
当前：ESearch(500ms) + EFetch(500ms) = 1000ms/batch
优化后：ESearch(100ms) + EFetch(500ms) = 600ms/batch
整体提升：40%
```

**场景 2：EFetch 占 80%**（更常见）
```
当前：ESearch(200ms) + EFetch(800ms) = 1000ms/batch
优化后：ESearch(40ms) + EFetch(800ms) = 840ms/batch
整体提升：16%
```

**修正后的预期**：
- 简单查询：整体提升 **5-10%**
- 复杂查询：整体提升 **30-50%**（取决于 EFetch 占比）
- 100 万篇文献（200 batches）：整体提升 **20-40%**

#### 7.1.3 验证方法

在实施后，建议通过日志分析实际测量：
```java
long esearchStart = System.currentTimeMillis();
// ESearch logic
long esearchDuration = System.currentTimeMillis() - esearchStart;

long efetchStart = System.currentTimeMillis();
// EFetch logic
long efetchDuration = System.currentTimeMillis() - efetchStart;

log.info("[PERF] batch={} esearch={}ms efetch={}ms total={}ms",
         batchNo, esearchDuration, efetchDuration, esearchDuration + efetchDuration);
```

### 7.2 其他收益

- ✅ **减少 PubMed 服务器负载**：避免重复计算相同查询
- ✅ **数据一致性提升**：WebEnv 锁定结果集，Plan 和 Execute 看到相同数据
- ✅ **API 配额节省**：减少 API 调用次数（对有 API key 的用户）
- ✅ **架构清晰度提升**：Plan 和 Execute 职责更明确
- ✅ **降低 Rate Limit 风险**：减少 API 调用频率，降低被限流的风险

---

## 8. 未来扩展

### 8.1 其他数据源优化

类似的缓存机制可以应用到：
- **EPMC**：支持 cursor-based pagination
- **OpenAlex**：支持 cursor pagination
- **Crossref**：支持 cursor pagination

### 8.2 监控和告警

#### 8.2.1 关键监控指标

**1. WebEnv 生成成功率**
```java
// 在 PubmedSearchPortImpl 中
@Counted(value = "pubmed.webenv.generated", extraTags = {"success"})
public PlanMetadata preparePlanMetadata(...) {
  // ...
  if (metadata.hasWebEnv()) {
    // success = true
  } else {
    // success = false
  }
}
```

**2. WebEnv 降级率**
```java
// 在 PubmedBatchPlanner 中
if (!metadata.hasWebEnv()) {
  log.warn("[INGEST][APP] pubmed webenv not available, using fallback termHash={}",
           safeHash(compiledQuery));
  // 记录降级事件
}
```

**3. Plan/Execute 阶段耗时**
```java
@Timed("pubmed.plan.duration")
public PlanMetadata preparePlanMetadata(...) { ... }

@Timed("pubmed.execute.batch.duration")
public void executeBatch(...) { ... }
```

**4. ESearch vs EFetch 耗时分布**（建议添加）
```java
log.info("[PERF] batch={} esearch={}ms efetch={}ms total={}ms",
         batchNo, esearchDuration, efetchDuration, total);
```

#### 8.2.2 告警规则

| 指标 | 阈值 | 说明 |
|------|------|------|
| WebEnv 生成成功率 | < 95% | PubMed API 行为变化，需要检查 |
| WebEnv 降级率 | > 10% | 频繁降级，可能影响性能 |
| Plan 阶段平均耗时 | > 5s | Plan 阶段耗时异常 |
| Execute 批次失败率 | > 5% | 需要检查 WebEnv 过期或 API 错误 |

#### 8.2.3 日志分析

关键日志关键词（便于 ELK/Loki 查询）：
- `pubmed esearch metadata` - Plan 阶段执行
- `webEnv=enabled/disabled` - WebEnv 状态
- `pubmed planner: planned` - Batch 规划完成
- `[PERF] batch=` - 性能数据

### 8.3 配置化开关

可以添加配置开关控制是否启用 WebEnv：

```yaml
patra:
  ingest:
    pubmed:
      use-webenv-cache: true  # 默认启用，可关闭用于 A/B 测试
```

---

## 9. 参考资料

- [PubMed E-utilities History Server](https://www.ncbi.nlm.nih.gov/books/NBK25497/#chapter2.History_Server)
- [ESearch API Parameters](https://www.ncbi.nlm.nih.gov/books/NBK25499/#chapter4.ESearch)
- [项目架构文档](../ARCHITECTURE.md)
- [patra-ingest 数据流](patra-ingest-flow.md)

---

## 10. 实施前检查清单

### 10.1 前置验证

- [x] **retmax=0 验证**：确认 `retmax=0 + usehistory=y` 返回 WebEnv（已验证 ✅）
- [ ] **影响范围评估**：搜索所有 `estimateCount()` 的调用，确认只有 PubmedBatchPlanner 使用
  ```bash
  # 使用 grep 搜索
  grep -r "estimateCount" patra-ingest/
  ```
- [ ] **依赖检查**：确认 `patra-spring-boot-starter-provenance` 已包含 `PubMedParamKeys`
- [ ] **配置检查**：确认 ObjectMapper 在 PubmedSearchPortImpl 中可用

### 10.2 代码实施检查

#### Domain 层
- [ ] 创建 `PlanMetadata.java`（包含增强验证）
- [ ] 修改 `PubmedSearchPort.java`（重命名方法 + 返回类型）
- [ ] 单元测试：`PlanMetadataTest.java`（至少 6 个测试用例）

#### Infra 层
- [ ] 修改 `PubmedSearchPortImpl.java`
  - [ ] 实现 `preparePlanMetadata()` 方法
  - [ ] 添加 `addUseHistory()` 辅助方法
  - [ ] 添加 WebEnv/QueryKey 一致性检测日志
- [ ] 确认 `ObjectMapper` 注入正确

#### App 层
- [ ] 修改 `PubmedBatchPlanner.java`
  - [ ] 调用 `preparePlanMetadata()` 替代 `estimateCount()`
  - [ ] 添加 WebEnv/QueryKey 到 batch params
  - [ ] 更新日志记录（webEnv=enabled/disabled）
- [ ] 单元测试：`PubmedBatchPlannerTest.java`（至少 2 个测试用例）

### 10.3 测试检查

- [ ] **单元测试**：所有新增测试通过
- [ ] **集成测试**：`PubmedSearchPortImplIntegrationTest.java`
  - [ ] `shouldReturnWebEnvWithRetmaxZero()` 通过
  - [ ] `shouldHandleEmptyResults()` 通过
- [ ] **编译检查**：`mvn clean compile` 无错误
- [ ] **完整测试**：`mvn test` 无失败

### 10.4 部署前检查

- [ ] **代码审查**：至少一位同事审查代码
- [ ] **文档更新**：API 文档、CHANGELOG 更新
- [ ] **监控准备**：
  - [ ] 确认 Micrometer 依赖可用
  - [ ] 准备 Grafana 面板（可选）
- [ ] **回滚计划**：准备回滚脚本（恢复 estimateCount）

### 10.5 上线检查

- [ ] **灰度发布**：先在测试环境验证
- [ ] **日志监控**：检查关键日志输出
  - `pubmed esearch metadata ... webEnv=present queryKey=present`
  - `pubmed planner: planned X batches ... webEnv=enabled`
- [ ] **性能监控**：对比优化前后的耗时
  - Plan 阶段平均耗时
  - Execute 批次平均耗时
  - ESearch vs EFetch 时间占比
- [ ] **错误监控**：检查是否有新的异常

### 10.6 验证标准

✅ **成功标准**：
- WebEnv 生成成功率 > 95%
- 复杂查询性能提升 > 20%
- 无功能回归（现有测试全部通过）
- 无新增错误或异常

⚠️ **降级触发条件**：
- WebEnv 生成成功率 < 80%（连续 1 小时）
- 出现大量 PubMed API 错误（错误率 > 10%）
- 性能反而下降（平均耗时增加 > 20%）

---

## 11. 快速参考

### 11.1 关键文件清单

```
patra-ingest-domain/
└── src/main/java/com/patra/ingest/domain/
    ├── model/vo/PlanMetadata.java                      [新增]
    └── port/PubmedSearchPort.java                      [修改]

patra-ingest-infra/
└── src/main/java/com/patra/ingest/infra/
    └── rpc/pubmed/PubmedSearchPortImpl.java           [修改]

patra-ingest-app/
└── src/main/java/com/patra/ingest/app/
    └── usecase/execution/execute/PubmedBatchPlanner.java  [修改]

patra-ingest-domain/src/test/
└── java/com/patra/ingest/domain/model/vo/
    └── PlanMetadataTest.java                          [新增]

patra-ingest-app/src/test/
└── java/com/patra/ingest/app/usecase/execution/execute/
    └── PubmedBatchPlannerTest.java                    [修改]

patra-ingest-boot/src/test/
└── java/com/patra/ingest/infra/
    └── PubmedSearchPortImplIntegrationTest.java       [新增]
```

### 11.2 关键命令

```bash
# 搜索影响范围
grep -r "estimateCount" patra-ingest/

# 编译检查
mvn -q clean compile

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=PlanMetadataTest

# 运行集成测试
cd patra-ingest/patra-ingest-boot
mvn test -Dtest=PubmedSearchPortImplIntegrationTest
```

### 11.3 预计工作量

| 阶段 | 工作量 | 说明 |
|------|--------|------|
| 代码实施 | 2-3 人日 | Domain + Infra + App 层修改 |
| 单元测试 | 0.5-1 人日 | 新增测试用例编写 |
| 集成测试 | 0.5-1 人日 | 真实 API 测试验证 |
| 代码审查 | 0.5 人日 | 同事审查 + 修改 |
| 部署验证 | 1 人日 | 灰度发布 + 监控 |
| **总计** | **4-6 人日** | **约 1 周** |

---

## 12. 变更记录

| 日期 | 作者 | 变更内容 |
|------|------|---------|
| 2025-01-19 | linqibin | 初始版本：设计文档创建 |
| 2025-01-19 | linqibin | 补充实施检查清单、调整性能预期、增强测试用例 |
| 2025-01-19 | linqibin | 验证 retmax=0 假设（通过 ✅）、简化监控方案 |
