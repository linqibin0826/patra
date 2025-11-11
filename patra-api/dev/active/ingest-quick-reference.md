# patra-ingest 现有结构快速参考卡片

## 核心调用路径映射

### Plan 摄入中的适配器使用

```
PlanIngestionOrchestrator
  └─ PatraRegistryPort.fetchConfig()          # 获取 Provenance 配置快照
      └─ PatraRegistryAdapter (Infra)
          └─ ProvenanceClient.getConfiguration()  # Feign RPC 调用

PubmedSearchPort.preparePlanMetadata()         # 获取计划元数据
  └─ PubmedSearchAdapter (Infra)
      └─ PubMedClient.esearch()                # PubMed API 调用
```

### Task 执行中的适配器使用

```
TaskExecutionUseCase
  └─ GenericBatchExecutor.execute(context, batch)
      ├─ AdapterRegistry.getAdapter(provenanceCode)
      │   └─ DataSourceAdapter 实例（如 PubmedDataSourceAdapter）
      │
      ├─ adapter.fetchData(request)           # 调用数据源适配器
      │   ├─ 构建：ESearch 查询
      │   ├─ 执行：ESearch + EFetch API
      │   ├─ 转换：PubmedArticle → StandardLiterature
      │   └─ 返回：AdapterResult
      │
      ├─ [重试逻辑]（仅当 errorType == RETRIABLE）
      │   └─ 指数退避（1s → 2s → 4s → ... → 30s max）
      │
      └─ LiteraturePublisherOrchestrator.publish()
          └─ 发布 StandardLiterature 到存储/MQ
```

---

## 关键对象流转矩阵

```
╔════════════════════╦════════════════════╦════════════════════╗
║ 生成点               ║ 对象类型           ║ 消费点               ║
╠════════════════════╬════════════════════╬════════════════════╣
║ Orchestrator       ║ ExecutionContext   ║ GenericBatchExecutor║
║ GenericBatchExec   ║ AdapterRequest     ║ DataSourceAdapter   ║
║ DataSourceAdapter  ║ AdapterResult      ║ GenericBatchExec    ║
║ DataSourceAdapter  ║ StandardLiterature ║ Publisher/Ingest    ║
║ Registry           ║ ProvConfig Snapshot║ Orchestrator/Executor║
╚════════════════════╩════════════════════╩════════════════════╝
```

---

## 错误处理决策树

```
AdapterResult.fetchData() 抛异常或返回失败
  │
  ├─ errorType == RETRIABLE?
  │   ├─ YES:
  │   │   ├─ attempt < maxAttempts?
  │   │   │   ├─ YES: sleep(backoff) → 重试
  │   │   │   └─ NO: 返回失败
  │   │   └─ 中断异常? → 设置中断标记，返回失败
  │   │
  │   └─ NO (NON_RETRIABLE 或 NONE)
  │       └─ 直接返回失败，不重试
  │
  └─ 预期错误状态码（从 ProvenanceConfigSnapshot 配置）
      ├─ 429, 502, 503, 5xx: RETRIABLE
      ├─ 401, 403: NON_RETRIABLE
      └─ 其他 4xx: NON_RETRIABLE
```

---

## 配置快照获取路径

```
┌─ 时刻 T ──────────────────────────────────────┐
│                                                │
│ Registry.getConfiguration(provenanceCode,     │
│                          operationCode,       │
│                          Instant.now())       │
│                                                │
│ 响应包含：                                      │
│ - ProvenanceInfo (id, code, baseUrl, ...)    │
│ - WindowOffsetConfig (窗口切片规则)            │
│ - PaginationConfig (分页模式)                 │
│ - HttpConfig (超时、Headers、代理)             │
│ - BatchingConfig (批大小、EPost 阈值)         │
│ - RetryConfig (重试次数、退避策略)             │
│ - RateLimitConfig (QPS、并发数)               │
│                                                │
│ 转换为：ProvenanceConfigSnapshot              │
│ 存储在：Plan 实体的 configSnapshotJson 字段   │
│                                                │
│ 用途：                                          │
│ 1. Task 执行时使用（通过 GenericBatchExec）   │
│ 2. 配置覆盖（运行时配置 > 快照配置 > 默认）   │
│ 3. 重放执行（相同快照确保一致性）              │
│                                                │
└────────────────────────────────────────────────┘
```

---

## 批次执行参数构成

```
ExecutionContext (来自 Task 和快照)
  ├─ compiledQuery: String          # 编译后的布尔查询
  │                                  # 例："pubdate > 20250101 AND cancer"
  │
  └─ compiledParams: Map<String, ?> # 编译后的参数
                                     # 例：{
                                     #   "datetype": "pdat",
                                     #   "mindate": "2025/01/01",
                                     #   "maxdate": "2025/01/10",
                                     #   "retmax": 100,
                                     #   "retstart": 0
                                     # }

                    ↓
         (传递给 GenericBatchExecutor)
                    ↓

BatchExecutionParams
  ├─ query: compiledQuery
  └─ params: compiledParams

                    ↓
         (构建 AdapterRequest)
                    ↓

AdapterRequest
  ├─ operationCode: "HARVEST"
  ├─ config: ProvenanceConfig (从快照转换)
  ├─ executionParams: BatchExecutionParams
  └─ metadata: BatchMetadata(batchNo=1, cursorToken=null)

                    ↓
         (传递给 DataSourceAdapter)
                    ↓

PubmedDataSourceAdapter.fetchData(request)
  1. buildSearchParams() → merge query + params
  2. pubMedClient.esearch(mergedParams)
  3. pubMedClient.efetch(...) or epost+efetch
  4. converter.toStandardLiterature(articles)
  5. 返回 AdapterResult
```

---

## 重试配置优先级

```
运行时 ProvenanceConfig (最高优先)
  ├─ ProvenanceConfig.retry().maxRetryTimes()
  ├─ ProvenanceConfig.retry().initialDelayMillis()
  └─ ...
    ↓
数据源全局覆盖 (次高)
  └─ properties.mergeWithRuntime(provenanceCode, config)
    ↓
共享默认值 (最低)
  ├─ GenericBatchExecutor.DEFAULT_MAX_RETRY_TIMES = 3
  ├─ GenericBatchExecutor.DEFAULT_INITIAL_BACKOFF_MILLIS = 1000ms
  └─ GenericBatchExecutor.MAX_BACKOFF_MILLIS = 30000ms
```

---

## StandardLiterature 字段映射

```
StandardLiterature (Patra 规范模型)
├─ title                          ← PubmedArticle.title
├─ abstractText                   ← PubmedArticle.abstractText
├─ authors: List<StandardAuthor>  ← PubmedArticle.authorList[]
│   ├─ lastName                   ← Author.lastName
│   ├─ foreName                   ← Author.foreName
│   └─ affiliation                ← Author.affiliation
├─ journal: StandardJournal       ← PubmedArticle.journal
│   ├─ title                      ← Journal.title
│   ├─ issn                       ← Journal.issn
│   └─ publisher                  ← Journal.publisher
├─ identifiers: Map<String, String>
│   ├─ "pmid": article.pmid()
│   ├─ "doi": article.doi()
│   └─ "pmc": article.pmc()
├─ publicationDate                ← PubmedArticle.publicationDate
└─ keywords: List<String>         ← PubmedArticle.meshHeadings[]
```

---

## 关键接口注解与职责

| 接口 | 位置 | 实现位置 | 职责 |
|------|------|---------|------|
| `DataSourceAdapter` | patra-starter-provenance | pubmed/PubmedDataSourceAdapter | 数据源特定逻辑 |
| `PatraRegistryPort` | patra-ingest-domain | patra-ingest-infra | Registry 配置获取 |
| `PubmedSearchPort` | patra-ingest-domain | patra-ingest-infra | PubMed 元数据 |
| `ExpressionCompilerPort` | patra-ingest-domain | patra-ingest-infra | 表达式编译 |
| `PlanRepository` | patra-ingest-domain | patra-ingest-infra | Plan 持久化 |
| `TaskRepository` | patra-ingest-domain | patra-ingest-infra | Task 持久化 |

---

## 常见问题排查路径

### 问题：为什么重试 3 次仍失败?

```
检查点：
1. GenericBatchExecutor.resolveMaxAttempts()
   ├─ 检查 ProvenanceConfig.retry().maxRetryTimes()
   ├─ 是否为 null?→ 使用默认值 3
   └─ 是否为 0?→ 禁用重试
   
2. GenericBatchExecutor.checkErrorType()
   ├─ errorType == RETRIABLE? 
   ├─ 否 → 第一次失败立即返回
   └─ 是 → 继续重试
   
3. 增加日志级别观察完整的重试轨迹
   └─ DEBUG: "适配器可重试失败 ... attempt={} of {}"
```

### 问题：如何调整重试退避延迟?

```
方式 1：通过 Registry 配置（推荐）
  └─ reg_prov_retry_cfg.initial_delay_millis
  
方式 2：通过代码常量（紧急应对）
  └─ GenericBatchExecutor.DEFAULT_INITIAL_BACKOFF_MILLIS
  └─ GenericBatchExecutor.MAX_BACKOFF_MILLIS
  
计算公式：
  delay[i] = min(initialDelay * 2^(i-1), MAX_BACKOFF)
  例：1s, 2s, 4s, 8s, ..., 30s
```

### 问题：为什么 PubMed API 返回超时仍然重试?

```
检查点：
1. PubmedDataSourceAdapter.isTimeout(Throwable)
   ├─ instanceof HttpTimeoutException? → 是 RETRIABLE
   ├─ instanceof SocketTimeoutException? → 是 RETRIABLE
   └─ 包含此异常链? → 是 RETRIABLE
   
2. 日志搜索：
   └─ "PubMed adapter timeout detected"
   
3. 确认配置
   └─ ProvenanceConfig.retry().retryOnNetworkError = true
```

---

## 部署检查清单

- [ ] ProvenanceRegistry 已部署并包含 pubmed provenance 配置
- [ ] patra-starter-provenance 已发布，包含 PubmedDataSourceAdapter
- [ ] patra-ingest-infra 已包含 PatraRegistryAdapter 和 PubmedSearchAdapter
- [ ] ExpressionCompiler 已配置并能编译查询表达式
- [ ] RocketMQ 已启动并配置 task-ready 主题
- [ ] MinIO/S3 已配置存储桶用于文献存储
- [ ] MySQL 已初始化 ingest_plan, ingest_task 等表

---

**快速参考卡片版本**：1.0  
**最后更新**：2025-11-11
