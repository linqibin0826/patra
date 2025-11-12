# Patra 项目数据源架构探索报告

**探索日期**: 2025-11-12  
**探索深度**: Medium - Thorough  
**目标**: 为 v2.0 多数据类型架构实施做准备

---

## 📊 执行摘要

本次探索系统地分析了Patra项目的现有数据源架构，涵盖Domain、Infrastructure、Application及Framework四个关键层次。发现了清晰的六边形架构实现和DDD模式，现有架构为v2.0多数据类型支持奠定了良好基础。

### 关键发现
- ✅ 完整的六边形架构实现，Domain层与技术框架完全解耦
- ✅ 双层抽象设计（DataSourcePort + DataSourceProvider）支持可扩展性
- ✅ 完善的错误分类机制和配置管理体系
- ⚠️ 当前仅实现了PubMed一个数据源提供者
- ⚠️ 需要补充其他数据源的Provider实现（EPMC, Crossref等）

---

## 🏗️ 架构分层分析

### 1. Domain 层 (patra-ingest-domain)

#### 核心端口接口
```
📍 位置: patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/
```

| 接口 | 职责 | 说明 |
|------|------|------|
| **DataSourcePort** | 数据源获取契约 | 定义fetchData(context, batch)接口 |
| **PubmedSearchPort** | PubMed特有功能 | preparePlanMetadata(query, params, config) |
| **ExpressionCompilerPort** | 表达式编译 | 编译动态查询表达式 |
| **OutboxPublisherPort** | Outbox发布 | 可靠性消息发送 |

#### 核心模型
- **ExecutionContext**: 任务执行上下文（含编译后的查询和参数）
- **Batch**: 分页批次定义（批号、查询、参数、游标）
- **DataFetchResult**: 标准化获取结果（含错误分类）
- **WindowSpec**: Sealed Interface支持TIME/DATE/CURSOR/VOLUME/SINGLE窗口类型

#### 错误分类枚举 (ErrorType)
```java
public enum ErrorType {
    NONE,              // 完全成功
    RETRIABLE,         // 可重试（网络、限流、5xx）
    NON_RETRIABLE,     // 不可重试（认证、参数错）
    PARTIAL_SUCCESS    // 部分成功（数据质量警告）
}
```

### 2. Infrastructure 层 (patra-ingest-infra)

#### 适配器实现
```
📍 位置: patra-ingest-infra/src/main/java/com/patra/ingest/infra/integration/
```

**关键文件: DataSourceAdapter.java**
- 实现DataSourcePort接口
- 桥接Domain与Framework层
- 核心职责:
  1. 解析DataSourceProvider (通过ProviderRegistry)
  2. 转换ProvenanceConfigSnapshot → ProvenanceConfig
  3. 构建BatchExecutionParams (查询+参数合并)
  4. 调用provider.fetchData(ProviderRequest)
  5. 转换ProviderResult → DataFetchResult

**参数合并策略**:
- 查询优先级: Batch.query > ExecutionContext.compiledQuery
- 参数合并: ExecutionContext.compiledParams + Batch.params
- Batch参数覆盖同名字段

#### 配置转换
支持完整的配置转换链:
- HttpConfig (超时)
- PaginationConfig (分页)
- WindowOffsetConfig (窗口偏移)
- BatchingConfig (批处理)
- RetryConfig (重试)
- RateLimitConfig (限流)

#### 其他关键适配器
| 适配器 | 接口 | 功能 |
|------|------|------|
| PubmedSearchAdapter | PubmedSearchPort | ESearch查询 |
| PatraRegistryAdapter | PatraRegistryPort | 加载配置快照 |
| ExpressionCompilerPortImpl | ExpressionCompilerPort | 编译表达式 |
| LiteratureStorageAdapter | LiteratureStoragePort | MinIO存储 |

### 3. Application 层 (patra-ingest-app)

#### 核心编排器
```
📍 位置: patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/
```

| 编排器 | 职责 | 流程 |
|-------|------|------|
| **PlanIngestionOrchestrator** | Plan生命周期管理 | 加载配置→解析窗口→编译表达式→装配计划→持久化→发布事件 |
| **TaskExecutionUseCase** | Task执行协调 | 准备→批次规划→批次执行→进度跟踪→完成 |
| **OutboxRelayOrchestrator** | Outbox可靠发送 | 轮询→租约→发布→标记→记录日志 |

#### 批次执行流程 (GenericBatchExecutor)
```
1. ExecutionContextLoader: 加载上下文（编译表达式）
2. BatchPlanner: 规划批次
3. DataSourcePort.fetchData(): 调用获取数据
4. LiteratureEventPublisher: 发布文献事件
5. CursorAdvancer: 推进游标
```

### 4. Framework 层 (patra-spring-boot-starter-provenance)

#### Provider机制
```
📍 位置: patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/
```

**DataSourceProvider接口**
```java
public interface DataSourceProvider {
    String getProvenanceCode();  // 返回数据源代码
    ProviderResult fetchData(ProviderRequest request);  // 执行数据获取
}
```

**ProviderRegistry (自动注册)**
- Spring自动注入所有DataSourceProvider实现
- 按provenanceCode大小写不敏感查找
- 支持单个数据源多提供者（第一个优先）

#### PubmedDataSourceProvider实现
```
📍 位置: patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/pubmed/
```

四阶段流程:
1. **ESearch**: 搜索获取PMID列表（最多10000）
2. **EPost** (条件触发): 当PMID数>阈值(默认200)时，上传ID获取WebEnv
3. **EFetch**: 获取文献详情（直接ID或WebEnv+QueryKey）
4. **Convert**: XML→CanonicalLiterature转换（容错处理）

特性:
- 智能选择直接EFetch或EPost+EFetch
- 部分转换失败时返回PARTIAL_SUCCESS
- 详细的HTTP状态码分类

---

## 📁 项目结构完整解析

### 模块依赖关系
```
Application Layer (patra-ingest-app)
    ↓ depends on
Domain Layer (patra-ingest-domain)
    ↑ implements (via adapter)
Infrastructure Layer (patra-ingest-infra)
    ↓ uses
Framework Layer (patra-spring-boot-starter-provenance)
    ↓ calls
External APIs (PubMed, EPMC, etc.)
```

### 核心文件清单

#### Domain 层关键文件
```
patra-ingest-domain/src/main/java/com/patra/ingest/domain/
├── port/
│   ├── DataSourcePort.java              ✅ 核心端口接口
│   ├── PubmedSearchPort.java            ✅ PubMed查询接口
│   ├── ExpressionCompilerPort.java      ✅ 表达式编译端口
│   └── OutboxPublisherPort.java         ✅ Outbox发布端口
├── model/aggregate/
│   ├── PlanAggregate.java
│   ├── TaskAggregate.java
│   └── PlanSliceAggregate.java
└── model/vo/
    ├── execution/ExecutionContext.java  ✅ 执行上下文
    ├── batch/Batch.java                 ✅ 批次定义
    └── plan/WindowSpec.java             ✅ 窗口规范
```

#### Infrastructure 层关键文件
```
patra-ingest-infra/src/main/java/com/patra/ingest/infra/
├── integration/datasource/
│   ├── DataSourceAdapter.java           ✅ 核心桥接适配器
│   └── [参数合并、配置转换逻辑]
├── integration/pubmed/
│   └── PubmedSearchAdapter.java
├── persistence/repository/
│   ├── PlanRepositoryMpImpl.java
│   └── TaskRepositoryMpImpl.java
└── integration/registry/
    └── PatraRegistryAdapter.java
```

#### Application 层关键文件
```
patra-ingest-app/src/main/java/com/patra/ingest/app/
├── usecase/plan/
│   ├── PlanIngestionOrchestrator.java   ✅ Plan摄入编排
│   ├── PlanAssembler.java
│   └── slicer/SlicePlanner.java
├── usecase/execution/
│   ├── TaskExecutionUseCaseImpl.java
│   ├── session/ExecutionContextLoader.java ✅ 上下文加载
│   ├── coordination/GenericBatchExecutor.java ✅ 批次执行
│   └── planner/PubmedBatchPlanner.java
└── usecase/relay/
    └── OutboxRelayOrchestrator.java
```

#### Framework 层关键文件
```
patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/
├── common/provider/
│   ├── DataSourceProvider.java          ✅ 提供者接口
│   ├── ProviderRegistry.java            ✅ 注册表
│   ├── ProviderRequest.java
│   └── ProviderResult.java
├── pubmed/
│   ├── PubmedDataSourceProvider.java    ✅ PubMed实现
│   ├── PubMedClient.java
│   └── converter/PubmedArticleConverter.java
└── boot/
    ├── ProvenanceAutoConfiguration.java ✅ 自动配置
    └── ProvenanceProperties.java
```

---

## 🔄 完整调用流程

### 高层序列

```
Application (PlanIngestionOrchestrator)
    ↓ executeTask(taskId)
App Layer (TaskExecutionUseCase)
    ↓ loadExecutionContext(taskId)
App Layer (ExecutionContextLoader)
    ↓ fetchData(context, batch)
Domain Layer (DataSourcePort)
    ↓ [实现]
Infra Layer (DataSourceAdapter)
    ├─ resolveProvider("pubmed")
    │  └─ ProviderRegistry.getProvider()
    ├─ convertConfig(snapshot)
    ├─ buildExecutionParams(context, batch)
    └─ provider.fetchData(request)
        ↓ [实现]
Framework Layer (PubmedDataSourceProvider)
    ├─ buildSearchParams()
    ├─ ESearch API Call
    ├─ [条件] EPost API Call (if pmidCount > 200)
    ├─ EFetch API Call
    ├─ convertArticles() → CanonicalLiterature[]
    └─ return ProviderResult
        ↓ [转换]
Infra Layer (DataSourceAdapter)
    └─ convertResult(providerResult)
        ↓
Domain Layer (DataFetchResult)
    ↓
App Layer (继续处理文献数据)
```

---

## 🔍 现有数据源实现分析

### PubMed 数据源 (当前唯一实现)

**实现文件**: `PubmedDataSourceProvider.java`

**核心参数**:
- `PROVENANCE_CODE = "pubmed"`
- `DEFAULT_EPOST_THRESHOLD = 200`

**处理流程**:
1. 接收ProviderRequest (operationCode, config, executionParams, metadata)
2. 配置合并: 运行时 > 数据源覆盖 > 默认
3. ESearch: 使用PubMedESearchRequestAssembler构建请求
4. PMID提取: response.result().idList()
5. 智能获取策略:
   - pmidCount ≤ 200: 直接EFetch
   - pmidCount > 200: EPost + EFetch (含600ms延迟)
6. 转换阶段容错:
   - 记录转换失败的PMID
   - 返回PARTIAL_SUCCESS + 警告消息
7. 异常分类:
   - ProvenanceClientException: 按HTTP状态码分类
   - SocketTimeoutException: RETRIABLE
   - InterruptedException: 恢复中断标志 + RETRIABLE
   - 其他: 默认NON_RETRIABLE

**可观测性**:
- 开始日志: "PubMed provider start: operation={} batchNo={}"
- 成功日志: "PubMed provider success: fetched={} duration={}ms"
- 错误日志: "PubMed provider client error: status={}"
- 指标: ProvenanceMetrics记录转换成功/失败

---

## ⚙️ 数据转换机制

### 参数合并示例

```
ExecutionContext.compiledParams:
{
  "datetype": "pdat",
  "mindate": "2025-01-01",
  "maxdate": "2025-01-31"
}

Batch.params:
{
  "sort": "date",
  "retmode": "xml"
}

↓ 合并结果
{
  "datetype": "pdat",
  "mindate": "2025-01-01",
  "maxdate": "2025-01-31",
  "sort": "date",
  "retmode": "xml"
}
```

### 配置优先级

```
优先级1 (最高): ExecutionContext.configSnapshot
    └─ 保证任务级别配置一致性

优先级2: ProvenanceProperties.datasources["pubmed"]
    └─ 数据源特定覆盖

优先级3: ProvenanceProperties.defaults
    └─ 全局基线配置（最低）
```

---

## 🚨 错误处理机制

### 错误分类决策树

```
异常 → HTTP状态码分类
├─ 无状态码 → RETRIABLE (网络问题)
├─ 429 (限流) → RETRIABLE
├─ 5xx (服务器错误) → RETRIABLE
├─ 503, 502 → RETRIABLE
├─ 401, 403 (认证) → NON_RETRIABLE
├─ 400, 404 (客户端) → NON_RETRIABLE
├─ 其他4xx → NON_RETRIABLE
└─ 其他 → RETRIABLE (保守策略)

非HTTP异常
├─ SocketTimeoutException → RETRIABLE
├─ HttpTimeoutException → RETRIABLE
├─ InterruptedException → RETRIABLE (+ 恢复中断标志)
└─ 其他 → 检查Timeout链 → NON_RETRIABLE
```

### 部分成功处理

```
转换阶段 (convertArticles)
├─ for each article
│  ├─ 尝试 converter.toCanonicalLiterature(article)
│  └─ catch异常 → 记录failed PMID + 继续处理
└─ 如果有失败
   └─ 返回PARTIAL_SUCCESS + warningMessage
      包含失败PMID列表（样本 + 计数）
```

---

## 📊 数据库支撑

### 核心表设计

| 表名 | 用途 | 关键字段 |
|------|------|---------|
| `ingest_plan` | 计划蓝图 | plan_key(幂等), status, expr_proto_snapshot_json |
| `ingest_task` | 原子任务 | idempotent_key, plan_id, slice_id, params_json, status |
| `ingest_cursor` | 游标水位线 | namespace_key, cursor_value, watermark_at |
| `ingest_outbox_message` | 异步事件 | message_id, aggregate_type, payload, status, seq |
| `ingest_task_run` | 执行记录 | task_id, run_id, started_at, completed_at |

### 索引策略
- `idx_plan_key` (UNIQUE): 幂等性检查
- `idx_task_idempotent_key` (UNIQUE): 任务幂等性
- `idx_task_status_scheduled` (COMPOUND): 查询待执行任务
- `idx_outbox_status_seq` (COMPOUND): Outbox轮询

---

## 🔧 配置体系

### YAML配置结构

```yaml
patra:
  provenance:
    enabled: true
    defaults:
      http:
        timeout-connect-millis: 5000
        timeout-read-millis: 30000
        timeout-total-millis: 60000
      pagination:
        page-size-value: 100
        max-pages-per-execution: 10
      batching:
        detail-fetch-batch-size: 50
        max-ids-per-request: null
      retry:
        max-retry-times: 3
        initial-delay-millis: 1000
      rate-limit:
        max-concurrent-requests: 5
        per-credential-qps-limit: 3
    datasources:
      pubmed:
        http:
          timeout-read-millis: 60000  # 覆盖默认
        batching:
          epost-threshold: 200
```

---

## 📚 Common 模块

### CanonicalLiterature 标准文献模型

```java
@Value
public class CanonicalLiterature {
    String title;
    String abstractText;
    List<AuthorInfo> authors;
    JournalInfo journal;
    Map<String, String> identifiers;  // PMID, DOI, PMC等
    LocalDate publicationDate;
    List<String> keywords;
    
    @Value
    public static class AuthorInfo {
        String lastName;
        String foreName;
        String affiliation;
    }
    
    @Value
    public static class JournalInfo {
        String title;
        String issn;
        String publisher;
    }
}
```

---

## 🎯 关键架构决策

### 1. 双层抽象设计

```
Domain Layer: DataSourcePort
    ↑ implements (Infrastructure)
Infrastructure Layer: DataSourceAdapter
    ↓ uses
Framework Layer: DataSourceProvider
    ↑ implements
PubmedDataSourceProvider
```

**优势**:
- Domain完全不知道框架细节
- 易于添加新数据源（无需改Domain）
- 支持多个Provider实现同一接口

### 2. 自动化的Provider注册

```java
// Spring自动注入所有实现
ProviderRegistry(List<DataSourceProvider> discoveredProviders)
    └─ 自动注册所有providers
```

**优势**:
- 新增Provider无需修改ProviderRegistry
- 自动检测重复注册
- 支持Provider多实现

### 3. 执行上下文的预编译

```
Plan摄入阶段 (Application)
    ├─ 加载表达式原型 (JSON snapshot)
    ├─ 编译 → 生成 compiledQuery + compiledParams
    └─ 保存Plan (快照)

Task执行阶段 (Application)
    ├─ 加载Plan快照
    ├─ 重新编译 (确保一致)
    └─ 构建ExecutionContext
        └─ DataSourceAdapter.fetchData(context, batch)
```

**优势**:
- 表达式编译结果可回放
- 支持动态参数注入
- 避免运行时编译开销

### 4. 分页游标策略

```
基于游标分页:
├─ WebEnv (PubMed特有): 服务器端会话
├─ QueryKey (PubMed特有): 标识查询结果集
└─ 通用cursorToken: 下一页标识

Batch.cursorToken 传递给提供者
提供者返回 nextCursorToken
应用层推进游标 (CursorAdvancer)
```

---

## 🚀 扩展指南

### 添加新数据源的完整步骤

#### 步骤1: 实现DataSourceProvider

```java
@Slf4j
@RequiredArgsConstructor
public class NewSourceDataSourceProvider implements DataSourceProvider {
    private static final String PROVENANCE_CODE = "newsource";
    
    private final NewSourceClient client;
    private final NewSourceConverter converter;
    private final ProvenanceProperties properties;
    
    @Override
    public String getProvenanceCode() {
        return PROVENANCE_CODE;
    }
    
    @Override
    public ProviderResult fetchData(ProviderRequest request) {
        try {
            ProvenanceConfig config = properties.mergeWithRuntime(
                PROVENANCE_CODE, request.config());
            
            // 调用API
            var response = client.fetch(
                request.executionParams().query(),
                request.executionParams().params(),
                config);
            
            // 转换
            List<CanonicalLiterature> literatures = convertResponse(response);
            
            return ProviderResult.success(literatures, response.nextCursor());
        } catch (Exception ex) {
            return handleException(ex);
        }
    }
}
```

#### 步骤2: 注册为Spring Bean

```java
@Configuration
public class NewSourceProviderConfiguration {
    
    @Bean
    public NewSourceDataSourceProvider newSourceProvider(
        NewSourceClient client,
        NewSourceConverter converter,
        ProvenanceProperties properties) {
        return new NewSourceDataSourceProvider(client, converter, properties);
    }
}
```

#### 步骤3: 自动注册 (无需额外代码)
- ProviderRegistry通过Spring自动注入发现
- 自动注册到 providers 映射表

#### 步骤4: 配置 (可选)

```yaml
patra:
  provenance:
    datasources:
      newsource:
        http:
          timeout-read-millis: 45000
        pagination:
          page-size: 150
```

---

## ⚠️ 现存挑战与建议

### Challenge 1: 单一Provider实现
**现状**: 仅实现了PubMed数据源
**影响**: 无法测试Provider多实现机制的真实场景
**建议**: 
- 优先实现 EPMC (Europe PMC) 提供者
- 次优实现 Crossref 提供者
- 验证ProviderRegistry的自动注册机制

### Challenge 2: 批次规划的数据源特异性
**现状**: PubmedBatchPlanner硬编码在特定位置
**影响**: 不同数据源需要不同的批次规划策略
**建议**:
- 创建BatchPlannerRegistry（类似ProviderRegistry）
- 支持按provenanceCode查找对应的BatchPlanner
- 考虑 EpmcBatchPlanner, CrossrefBatchPlanner

### Challenge 3: 错误分类的数据源差异
**现状**: HTTP状态码分类可能因数据源而异
**影响**: 某些错误在EPMC中可重试，在PubMed中不可重试
**建议**:
- Provider.classifyException() 让提供者自己分类
- 保留通用分类作为默认行为
- 在ProviderRequest中增加错误分类上下文

### Challenge 4: 配置可发现性
**现状**: ProvenanceProperties配置项众多
**影响**: 文档维护困难，配置项容易遗漏
**建议**:
- 生成配置元数据文档
- 创建ConfigMetadataProvider接口
- 支持运行时打印所有可用配置项

---

## 📋 修改文件清单

### v2.0 实施需要修改的文件

#### Framework层 (新增)
```
✅ patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/epmc/
├─ EpmcDataSourceProvider.java (新建)
├─ EpmcClient.java (已存在)
└─ EpmcArticleConverter.java (已存在)

✅ patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/crossref/
├─ CrossrefDataSourceProvider.java (新建)
├─ CrossrefClient.java
└─ CrossrefWorksConverter.java
```

#### Application层 (修改)
```
⚠️ patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/strategy/planner/
├─ BatchPlannerRegistry.java (新建 - 类似ProviderRegistry)
├─ EpmcBatchPlanner.java (新建)
└─ CrossrefBatchPlanner.java (新建)

⚠️ patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/slicer/
├─ SlicePlannerRegistry.java (可选优化 - 当前已有)
```

#### Domain层 (无需改)
```
✅ patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/
├─ DataSourcePort.java (保持不变 - 已足够通用)
├─ PubmedSearchPort.java (保持不变 - PubMed特有)
```

#### Infrastructure层 (可能优化)
```
⚠️ patra-ingest-infra/src/main/java/com/patra/ingest/infra/integration/datasource/
├─ DataSourceAdapter.java (可能调整参数合并逻辑)

✅ patra-ingest-infra/src/main/java/com/patra/ingest/infra/integration/epmc/
├─ EpmcSearchAdapter.java (新建 - 类似PubmedSearchAdapter)

✅ patra-ingest-infra/src/main/java/com/patra/ingest/infra/integration/crossref/
├─ CrossrefSearchAdapter.java (新建)
```

---

## 📖 文档建议

### 需要补充的文档

1. **数据源开发者指南**
   - DataSourceProvider实现模板
   - 错误分类最佳实践
   - 配置管理指南

2. **各数据源详细实现文档**
   - EPMC数据源实现
   - Crossref数据源实现
   - 对比分析三个数据源的差异

3. **批次规划算法文档**
   - PubMed批次策略（ESearch + EPost决策）
   - EPMC批次策略（EPMC API限制）
   - Crossref批次策略（Crossref限制）

4. **配置参考手册**
   - 完整的YAML配置示例
   - 各数据源推荐配置
   - 性能调优指南

---

## ✅ 现有测试覆盖

### 单元测试
```
✅ patra-spring-boot-starter-provenance/src/test/java/com/patra/starter/provenance/pubmed/
├─ PubmedArticleConverterTest.java
├─ PubMedESearchRequestAssemblerTest.java

✅ patra-ingest-infra/src/test/java/com/patra/ingest/infra/integration/
├─ DataSourceAdapterTest.java (需确认存在)
```

### 集成测试
```
✅ patra-ingest-boot/src/test/java/com/patra/ingest/
├─ IngestArchitectureTest.java (ArchUnit架构测试)
├─ TaskExecutionE2ETest.java (端到端测试)
```

### v2.0建议补充的测试
```
⚠️ ProviderRegistry 多提供者场景
⚠️ 不同数据源的错误分类对比
⚠️ 配置优先级合并逻辑
⚠️ 部分成功处理的准确性
```

---

## 🎓 关键学习点

### 架构层面
1. **六边形架构的实践**
   - Port定义在Domain，Adapter实现在Infrastructure
   - 依赖倒置原则的应用
   - 清晰的依赖方向

2. **DDD在微服务中的应用**
   - 聚合根 (Plan, Task)
   - 值对象 (WindowSpec, ExecutionContext)
   - 领域事件 (TaskQueuedEvent, TaskCompletedEvent)
   - 端口适配器模式

3. **可扩展设计模式**
   - 策略模式 (Provider/Adapter的多实现)
   - 注册表模式 (ProviderRegistry自动发现)
   - 模板方法模式 (AbstractOutboxPublisher)

### 技术实现
1. **错误处理的细粒度分类**
   - 区分可重试/不可重试
   - HTTP状态码映射
   - 部分成功场景处理

2. **配置管理的分层设计**
   - 运行时快照确保一致性
   - 数据源覆盖支持差异化配置
   - 默认值提供基线

3. **并发安全的Provider注册**
   - ConcurrentHashMap存储
   - 自动去重检查
   - 线程安全的发现机制

---

## 🔗 相关资源位置

### 文档
- `/Users/linqibin/Desktop/Patra-api/patra-ingest/README.md` - Ingest模块总览
- `/Users/linqibin/Desktop/Patra-api/patra-ingest/patra-ingest-domain/README.md` - Domain模块
- `/Users/linqibin/Desktop/Patra-api/patra-ingest/patra-ingest-infra/README.md` - Infrastructure模块
- `/Users/linqibin/Desktop/Patra-api/patra-ingest/patra-ingest-app/README.md` - Application模块
- `/Users/linqibin/Desktop/Patra-api/patra-spring-boot-starter-provenance/README.md` - Framework文档
- `/Users/linqibin/Desktop/Patra-api/dev/docs/数据源端口与提供者架构设计.md` - 设计文档

### 核心代码
- Domain Port: `patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/`
- Adapter: `patra-ingest-infra/src/main/java/com/patra/ingest/infra/integration/datasource/DataSourceAdapter.java`
- Provider: `patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/pubmed/PubmedDataSourceProvider.java`
- Registry: `patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/common/provider/ProviderRegistry.java`

---

## 📈 建议优化方向

### 短期 (立即)
1. 实现EPMC数据源提供者
2. 补充相应的适配器和转换器
3. 创建对比测试验证Provider多实现

### 中期 (1-2周)
1. 实现BatchPlannerRegistry
2. 为EPMC/Crossref实现BatchPlanner
3. 补充批次规划的单元测试

### 长期 (架构优化)
1. 考虑Provider能力声明接口 (getCapabilities)
2. 实现Provider生命周期钩子 (init/destroy)
3. 支持条件化Provider注册 (@ConditionalOnProperty)

---

## 🎯 总结

Patra项目的数据源架构设计优秀，遵循六边形架构和DDD原则，为多数据类型支持奠定了坚实基础。现有的DataSourcePort和ProviderRegistry机制完全支持扩展到多个数据源。v2.0的主要工作是：

1. **实现新的数据源提供者** (EPMC, Crossref等)
2. **创建数据源特异的批次规划策略** (BatchPlannerRegistry)
3. **完善配置管理和错误分类** 
4. **补充相应的测试覆盖**

整个架构无需大的改动，主要是沿着现有设计模式的纵向扩展。

