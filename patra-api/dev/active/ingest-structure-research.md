# patra-ingest 微服务现有结构深度调研报告

## 执行摘要

本调研深入分析了 patra-ingest 微服务的现有结构，涵盖 GenericBatchExecutor、DataSourceAdapter 接口、PubMed 实现、以及 patra-registry 集成。发现系统已采用完善的六边形架构+DDD 模式，具有清晰的分层、端口抽象和适配器模式。

---

## 1. GenericBatchExecutor 实现分析

### 位置
- 文件：`patra-ingest-app/src/main/java/.../usecase/execution/coordination/GenericBatchExecutor.java`
- 职责：应用层执行器，负责将批次获取工作委托给 DataSourceAdapter 实现

### 核心方法签名

```java
public BatchResult execute(ExecutionContext context, Batch batch)
```

### 主要职责

1. **适配器解析**：通过 AdapterRegistry 获取正确的数据源适配器
2. **配置转换**：将 ProvenanceConfigSnapshot 转换为运行时 ProvenanceConfig
3. **请求构建**：组装 AdapterRequest（包含查询、参数、元数据、配置）
4. **重试逻辑**：实现指数退避策略（DEFAULT_MAX_RETRY_TIMES=3）
5. **文献发布**：调用 LiteraturePublisherOrchestrator 发布标准化文献
6. **结果转换**：将 AdapterResult 转换为领域层 BatchResult

### 关键特性

| 特性 | 值 |
|------|-----|
| 默认最大重试次数 | 3 |
| 初始退避延迟 | 1000ms |
| 最大退避延迟 | 30000ms |
| 退避策略 | 指数退避 |
| 中断处理 | 主线程设置中断标记，返回失败 |

### 关键调用链路

```
GenericBatchExecutor.execute()
  ├─ adapterRegistry.getAdapter(provenanceCode)          # 获取适配器
  ├─ configConverter.convert(...)                         # 转换配置
  ├─ adapter.fetchData(request)                           # 调用适配器(含重试)
  │   └─ [重试逻辑]
  │       ├─ 检查 errorType == RETRIABLE
  │       ├─ 计算退避延迟
  │       └─ TimeUnit.MILLISECONDS.sleep(...)
  ├─ literaturePublisherOrchestrator.publish(...)         # 发布文献
  └─ return BatchResult
```

---

## 2. DataSourceAdapter 接口定义

### 位置
- 文件：`patra-spring-boot-starter-provenance/src/main/java/.../common/adapter/DataSourceAdapter.java`
- 类型：统一适配器契约接口
- 架构角色：六边形架构的被驱动适配器

### 接口签名

```java
public interface DataSourceAdapter {
  String getProvenanceCode();
  AdapterResult fetchData(AdapterRequest request);
}
```

### 设计理念

- **单一入口**：仅通过 AdapterRegistry 注册和发现
- **不包含业务逻辑**：仅负责数据检索和格式转换
- **操作类型解耦**：HARVEST/UPDATE 等由上层编排处理
- **可扩展性**：新增数据源仅需实现此接口

---

## 3. AdapterRequest 和 AdapterResult 定义

### AdapterRequest 结构

```java
public record AdapterRequest(
    String operationCode,
    ProvenanceConfig config,
    BatchExecutionParams executionParams,
    BatchMetadata metadata)
```

**设计原则**：
- `operationCode`：采集操作类型（HARVEST、UPDATE）
- `config`：应用于本次执行的合并配置（HTTP、重试、限流等）
- `executionParams`：批次执行参数（编译后的查询字符串 + 完整参数）
- `metadata`：批次标识和游标状态（batchNo、cursorToken）

### BatchExecutionParams 结构

```java
public record BatchExecutionParams(
    String query,          // 编译后的布尔查询字符串
    JsonNode params)       // 编译后的参数映射(provider-named)
```

**关键设计**：params 包含完整的分页信息，不仅是查询参数

### AdapterResult 结构

```java
public record AdapterResult(
    boolean success,
    List<StandardLiterature> literatures,
    String nextCursorToken,
    String errorMessage,
    int fetchedCount,
    ErrorType errorType)
```

**错误类型枚举**：
- `NONE`：无错误
- `RETRIABLE`：瞬时错误，建议重试
- `NON_RETRIABLE`：终止性错误，不应自动重试
- `PARTIAL_SUCCESS`：部分成功并带警告

**工厂方法**：
- `success(List<StandardLiterature>, String nextCursorToken)`
- `retriableFailure(String errorMessage)`
- `nonRetriableFailure(String errorMessage)`
- `partialSuccess(List<StandardLiterature>, String nextCursorToken, String warningMessage, int totalAttempted)`

---

## 4. PubMed 适配器实现

### 位置
- 文件：`patra-spring-boot-starter-provenance/src/main/java/.../pubmed/PubmedDataSourceAdapter.java`
- 实现了 DataSourceAdapter 接口

### 核心工作流程

```
1. 准备搜索参数（merge executionParams 中的 query）
2. ESearch API：获取 PMID 列表和 WebEnv/QueryKey
3. 条件选择：
   ├─ PMIDs <= 200：直接 EFetch
   └─ PMIDs > 200：EPost → EFetch（通过 WebEnv）
4. 文章转换：PubmedArticle → StandardLiterature
5. 错误处理：
   ├─ HTTP 429/502/503/5xx → RETRIABLE
   ├─ HTTP 401/403 → NON_RETRIABLE
   ├─ 其他 4xx → NON_RETRIABLE
   └─ 超时异常 → RETRIABLE
```

### 关键实现细节

| 方法 | 说明 |
|------|------|
| `buildSearchParams()` | 将 executionParams.params() 与 query 合并，添加 usehistory=y, retmax=0 |
| `extractPmids()` | 从 ESearchResponse 中提取 PMID 列表 |
| `fetchArticles()` | 根据阈值选择直接 EFetch 或 EPost+EFetch 策略 |
| `convertArticles()` | 遍历 PubmedArticle，调用 converter.toStandardLiterature()，捕获转换错误 |
| `classifyClientException()` | 根据 HTTP 状态码判断是否可重试 |

### 重要常量

```java
private static final String PROVENANCE_CODE = "pubmed";
private static final int DEFAULT_EPOST_THRESHOLD = 200;  // 超过此数量使用 EPost
private static final int WARNING_ID_SAMPLE_LIMIT = 5;    // 错误采样限制
```

### 配置覆盖优先级

```
运行时 ProvenanceConfig > DataSource 全局默认 > 共享默认值
```

---

## 5. PatraRegistry 集成实现

### 位置
- 文件：`patra-ingest-infra/src/main/java/.../integration/registry/PatraRegistryAdapter.java`
- 实现了 PatraRegistryPort 接口

### 核心方法

```java
public ProvenanceConfigSnapshot fetchConfig(
    ProvenanceCode provenanceCode,
    OperationCode operationCode)
```

### 工作流程

```
1. 调用 ProvenanceClient.getConfiguration(...)
2. 错误处理：
   ├─ 404 Not Found → 抛出 IngestConfigurationException
   ├─ 5xx/可重试 → 返回最小快照 + 日志警告
   └─ 其他错误 → 抛出 IngestConfigurationException
3. 转换响应：ProvenanceConfigResp → ProvenanceConfigSnapshot
4. 验证：检查为空则返回最小快照
```

### 优雅降级策略

当 Registry 不可用时，返回最小可用快照：

```java
private ProvenanceConfigSnapshot createMinimalSnapshot(String provenanceCode) {
    ProvenanceInfo minimalProvenance = new ProvenanceConfigSnapshot.ProvenanceInfo(
        null,               // id
        provenanceCode,     // code
        null,               // name
        null,               // baseUrlDefault
        null,               // timezoneDefault
        null,               // docsUrl
        true,               // active
        null                // lifecycleStatusCode
    );
    return new ProvenanceConfigSnapshot(
        minimalProvenance,
        null, null, null, null, null, null  // 所有配置维度为 null
    );
}
```

### 错误处理

使用 `RemoteErrorHelper` 进行错误分类：
- `isNotFound(ex)` → 不可恢复，抛异常
- `isServerError(ex) || isRetryable(ex)` → 返回最小快照
- 其他 → 抛出异常

---

## 6. ProvenanceConfigSnapshot 数据模型

### 位置
- 文件：`patra-ingest-domain/src/main/java/.../model/snapshot/ProvenanceConfigSnapshot.java`

### 结构（Record 类型）

```java
public record ProvenanceConfigSnapshot(
    ProvenanceInfo provenance,        // 数据源元数据
    WindowOffsetConfig windowOffset,  // 时间窗口/增量偏移
    PaginationConfig pagination,      // 分页/游标配置
    HttpConfig http,                  // HTTP 策略配置
    BatchingConfig batching,          // 批处理/请求塑形
    RetryConfig retry,                // 重试和退避
    RateLimitConfig rateLimit)        // 速率限制和并发
```

### ProvenanceInfo 字段

| 字段 | 说明 |
|------|------|
| `id` | 主键 ID |
| `code` | Provenance 代码（全局唯一，如 pubmed/crossref） |
| `name` | 人类可读名称 |
| `baseUrlDefault` | 默认基础 URL |
| `timezoneDefault` | 默认时区（IANA） |
| `docsUrl` | 官方文档 URL |
| `active` | 激活标识 |
| `lifecycleStatusCode` | 生命周期状态（DRAFT/ACTIVE/DEPRECATED/RETIRED） |

### RetryConfig 关键字段

| 字段 | 说明 |
|------|------|
| `maxRetryTimes` | 最大重试次数（NULL=默认；0=禁用） |
| `backoffPolicyTypeCode` | 退避策略（FIXED/EXP/EXP_JITTER/DECOR_JITTER） |
| `initialDelayMillis` | 初始延迟毫秒数（首次重试） |
| `maxDelayMillis` | 每次重试最大延迟毫秒数 |
| `expMultiplierValue` | 指数乘数（EXP 系列） |
| `jitterFactorRatio` | 抖动因子比率（0~1） |
| `retryHttpStatusJson` | 重试 HTTP 状态列表 JSON |
| `giveupHttpStatusJson` | 放弃 HTTP 状态列表 JSON |

---

## 7. 现有 CanonicalData/StandardLiterature 定义

### 位置
- 文件：`patra-common/patra-common-model/src/main/java/.../model/StandardLiterature.java`
- 作用：微服务间共享的规范化文献表示

### 结构

```java
@Value
@Builder
@Jacksonized
public class StandardLiterature {
  String title;
  String abstractText;
  List<StandardAuthor> authors;
  StandardJournal journal;
  Map<String, String> identifiers;  // PMID, DOI, PMC 等
  LocalDate publicationDate;
  List<String> keywords;
}
```

### 嵌套结构

**StandardAuthor**：
```java
String lastName;
String foreName;
String affiliation;
```

**StandardJournal**：
```java
String title;
String issn;
String publisher;
```

### 设计原则

- **不可变**：@Value 注解
- **无框架依赖**：仅依赖 Lombok 和 Jackson
- **可移植性**：作为微服务间的共享内核模型

---

## 8. 现有架构中的适配器层次

### 六边形架构布局

```
┌─────────────────────────────────────┐
│  patra-ingest-adapter               │  驱动适配器
│  ├─ XXL-Job 定时任务                │
│  └─ RocketMQ 消费者                 │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│  patra-ingest-app                   │  应用层
│  ├─ PlanIngestionOrchestrator       │
│  └─ GenericBatchExecutor            │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│  patra-ingest-domain                │  领域层
│  ├─ PubmedSearchPort(接口)          │
│  ├─ PatraRegistryPort(接口)         │
│  └─ DataSourceAdapter(远程)         │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│  patra-ingest-infra                 │  基础设施层
│  ├─ PubmedSearchAdapter             │
│  ├─ PatraRegistryAdapter            │
│  └─ RocketMqOutboxPublisher         │
└─────────────────────────────────────┘
```

### 端口分类

**Domain 层定义的端口**（由 Infra 层实现）：
1. `PatraRegistryPort` - Registry 配置获取
2. `PubmedSearchPort` - PubMed 元数据查询
3. 各类 Repository 端口
4. ExpressionCompilerPort
5. StoragePort

**patra-starter-provenance 中的契约**（由各 DataSourceAdapter 实现）：
1. `DataSourceAdapter` - 数据源适配器基础接口
2. `PubmedDataSourceAdapter` - PubMed 具体实现

---

## 9. 配置快照获取最佳实践

### 调用流程

```java
// 1. 应用层编排器调用
ProvenanceConfigSnapshot snapshot = pattraRegistryPort.fetchConfig(
    ProvenanceCode.PUBMED,
    OperationCode.HARVEST
);

// 2. 基础设施适配器实现
@Component
public class PatraRegistryAdapter implements PatraRegistryPort {
    private final ProvenanceClient provenanceClient;  // Feign 客户端
    private final ProvenanceConfigSnapshotConverter converter;

    @Override
    public ProvenanceConfigSnapshot fetchConfig(
        ProvenanceCode provenanceCode,
        OperationCode operationCode) {
        // 调用 Registry 服务
        ProvenanceConfigResp resp = provenanceClient.getConfiguration(
            provenanceCode,
            operationType,
            Instant.now()
        );
        // 转换为快照
        return converter.convert(resp);
    }
}
```

### 错误处理模式

```java
try {
    ProvenanceConfigResp resp = callRegistry(...);
    return convertAndValidateResponse(resp, code);
} catch (RemoteCallException ex) {
    if (isNotFound(ex)) {
        // 404：配置丢失，不可恢复
        throw new IngestConfigurationException(...);
    }
    if (isServerError(ex)) {
        // 5xx：降级到最小快照
        return createMinimalSnapshot(code);
    }
    // 其他：抛异常
    throw ...;
}
```

### 配置快照缓存策略

- **缓存范围**：Plan 生成时捕获 Provenance 配置快照（无时间衰减）
- **重放语义**：相同 planKey 的重试使用相同快照
- **一致性保证**：避免执行过程中配置变更导致的不一致

---

## 10. 重点发现与建议

### 核心发现

1. **清晰的分层设计**
   - Domain 层定义港口接口，Infra 层提供实现
   - GenericBatchExecutor 作为应用层编排器，不依赖具体适配器

2. **端口分离模式**
   - `PatraRegistryPort`（Domain 定义）vs `PubmedSearchPort`（Domain 定义）
   - 两者都有独立的 Infra 实现

3. **配置优先级明确**
   - 快照首选 > DataSource 覆盖 > 全局默认

4. **重试策略成熟**
   - 指数退避、可配置最大次数、错误分类（可重试/不可重试）
   - GenericBatchExecutor 中已实现 3 次重试机制

5. **StandardLiterature 已就位**
   - patra-common 中已定义规范化文献格式
   - 包含 PMID、DOI、PMC 等多类标识符支持

### 建议

1. **DataSourceAdapter 的扩展**
   - 现有实现已支持 PubMed、EPMC、Crossref
   - 新增数据源仅需继承 DataSourceAdapter 接口

2. **HTTP 重试配置化**
   - RetryConfig 已支持 HTTP 状态码列表配置
   - 建议在 Registry 中预配置各数据源的可重试状态码

3. **游标管理优化**
   - BatchMetadata 已支持 cursorToken
   - 建议在 Batch 执行时动态更新游标位置

---

## 附录：关键文件位置速查表

| 组件 | 文件位置 |
|------|---------|
| GenericBatchExecutor | patra-ingest-app/.../usecase/execution/coordination/GenericBatchExecutor.java |
| DataSourceAdapter | patra-starter-provenance/.../common/adapter/DataSourceAdapter.java |
| AdapterRequest | patra-starter-provenance/.../common/adapter/AdapterRequest.java |
| AdapterResult | patra-starter-provenance/.../common/adapter/AdapterResult.java |
| PubmedDataSourceAdapter | patra-starter-provenance/.../pubmed/PubmedDataSourceAdapter.java |
| PatraRegistryAdapter | patra-ingest-infra/.../integration/registry/PatraRegistryAdapter.java |
| ProvenanceConfigSnapshot | patra-ingest-domain/.../model/snapshot/ProvenanceConfigSnapshot.java |
| StandardLiterature | patra-common/patra-common-model/.../model/StandardLiterature.java |
| PubmedSearchPort | patra-ingest-domain/.../port/PubmedSearchPort.java |
| PatraRegistryPort | patra-ingest-domain/.../port/PatraRegistryPort.java |

---

**调研日期**：2025-11-11  
**范围**：Very Thorough（超深度）  
**涵盖模块**：patra-ingest, patra-starter-provenance, patra-common, patra-registry
