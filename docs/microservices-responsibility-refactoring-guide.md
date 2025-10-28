# Papertrace 微服务架构职责重构实施文档

## 📋 目录

1. [背景与目标](#1-背景与目标)
2. [架构设计原则](#2-架构设计原则)
3. [详细技术方案](#3-详细技术方案)
4. [核心代码实现](#4-核心代码实现)
5. [文件迁移清单](#5-文件迁移清单)
6. [实施计划](#6-实施计划)
7. [测试策略](#7-测试策略)
8. [风险控制](#8-风险控制)
9. [验收标准](#9-验收标准)

---

## 1. 背景与目标

### 1.1 当前架构问题

**职责边界模糊**：
- `BatchExecutor` 混合了通用批处理逻辑和数据源特定逻辑
- `PubmedArticleConverter` 位于 app 层，但包含业务规则
- Registry 模块职责不够纯净，与数据采集逻辑耦合

**扩展性差**：
- 新增数据源需要修改 ingest 模块代码
- 数据源特定逻辑分散在多个模块
- 违反开闭原则

**维护难度高**：
- 数据质量问题定位困难
- 数据源配置与转换逻辑分离
- 测试覆盖度不足

### 1.2 重构目标

**清晰的职责分离**：
- Registry：纯元数据管理（SSOT）
- Starter：数据源协议适配内聚
- Ingest：通用摄取引擎

**优秀的扩展性**：
- 新增数据源零修改现有代码
- 遵循开闭原则
- 支持数据源独立演进

**更高的可维护性**：
- 问题快速定位到对应适配器
- 统一的接口便于测试和监控
- 配置与逻辑内聚管理

---

## 2. 架构设计原则

### 2.1 核心理念

```
🏛️ Registry (SSOT)          📦 Starter (数据源适配)         🚀 Ingest (通用引擎)
     ↓                            ↓                            ↓
 配置和元数据管理              API客户端 + 转换器           批处理编排和存储
```

### 2.2 模块职责重新定义

#### **Registry 模块：纯元数据管理**
```yaml
职责范围:
  ✅ ProvenanceConfiguration 管理
  ✅ 配置时间切片和优先级
  ✅ 字典和元数据存储
  ✅ 向其他模块提供配置查询接口

明确不包含:
  ❌ 任何 API 调用逻辑
  ❌ 数据转换逻辑
  ❌ 业务流程执行
```

#### **Starter 模块：数据源协议适配**
```yaml
职责范围:
  ✅ 各数据源的协议适配器
  ✅ 数据格式转换器（原始 → 标准）
  ✅ API调用细节封装
  ✅ 错误码映射和重试策略
  ✅ 适配器注册和发现

技术实现:
  ✅ 充分复用现有 PubMedClient、SimpleHttpClient
  ✅ 统一的 DataSourceAdapter 接口
  ✅ 兜底配置 + 数据源特定覆盖
```

#### **Ingest 模块：通用摄取引擎**
```yaml
职责范围:
  ✅ 通用批处理编排
  ✅ 任务调度和状态管理
  ✅ 通过适配器获取数据
  ✅ 数据存储和发布
  ✅ 通用基础设施

简化内容:
  ❌ 删除所有 BatchExecutor 相关代码
  ❌ 删除数据源特定的转换逻辑
  ❌ 删除对具体数据源包的依赖
```

### 2.3 数据流设计

**重构后的完整数据流**：
```
1. Registry 提供 ProvenanceConfigSnapshot
    ↓
2. Ingest.GenericBatchExecutor 通过 AdapterRegistry 选择适配器
    ↓
3. Starter.DataSourceAdapter 执行：
   - API调用（复用现有 PubMedClient）
   - 数据转换（迁移的 PubmedArticleConverter）
   - 返回 StandardLiterature 列表
    ↓
4. Ingest 接收结果 → 发布存储（保持现有逻辑）
```

---

## 3. 详细技术方案

### 3.1 配置管理设计

#### **配置层级结构**
```yaml
ProvenanceProperties (应用级):
  default: SourceProperties      # 兜底配置，所有数据源共享
  pubmed: SourceProperties       # PubMed 特定覆盖
  epmc: SourceProperties         # EPMC 特定覆盖
  crossref: SourceProperties     # Crossref 特定覆盖
```

#### **配置优先级**
```
运行时配置 (Registry快照) > 数据源特定配置 > 兜底配置 > 代码默认值
```

#### **配置示例**
```yaml
# application.yml
patra:
  provenance:
    enabled: true

    # 兜底配置 - 所有数据源共享
    default:
      http:
        timeout-connect-millis: 10000
        timeout-read-millis: 30000
      pagination:
        page-size-value: 100
      retry:
        max-retry-times: 3
        initial-delay-millis: 1000
      rate-limit:
        max-concurrent-requests: 10
        per-credential-qps-limit: 5

    # PubMed 特定配置（覆盖兜底配置）
    pubmed:
      base-url: "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"
      pagination:
        page-size-value: 200  # PubMed 支持更大的分页
      batching:
        detail-fetch-batch-size: 100
        max-ids-per-request: 500

    # EPMC 特定配置
    epmc:
      base-url: "https://www.ebi.ac.uk/europepmc/webservices/rest"
      rate-limit:
        max-concurrent-requests: 5  # EPMC 限制更严格
```

### 3.2 适配器接口设计

#### **核心接口定义**
```java
// com.patra.starter.provenance.common.adapter.DataSourceAdapter
public interface DataSourceAdapter {
    /**
     * 数据源标识码
     */
    String getProvenanceCode();

    /**
     * 支持的操作类型
     */
    boolean supports(String operationCode);

    /**
     * 执行数据获取和转换
     */
    AdapterResult fetchData(AdapterRequest request);
}
```

#### **请求响应模型**
```java
// com.patra.starter.provenance.common.adapter.AdapterRequest
public record AdapterRequest(
    String operationCode,
    ProvenanceConfig config,          // 合并后的运行时配置
    String compiledQuery,             // 编译后的查询表达式
    JsonNode compiledParams,          // 编译后的参数
    BatchInfo batchInfo               // 批次信息
) {}

// com.patra.starter.provenance.common.adapter.AdapterResult
public record AdapterResult(
    boolean success,
    List<StandardLiterature> literatures,
    String nextCursorToken,
    String errorMessage,
    int fetchedCount
) {
    public static AdapterResult success(List<StandardLiterature> literatures, String nextCursor) {
        return new AdapterResult(true, literatures, nextCursor, null, literatures.size());
    }

    public static AdapterResult failure(String errorMessage) {
        return new AdapterResult(false, List.of(), null, errorMessage, 0);
    }
}

// com.patra.starter.provenance.common.adapter.BatchInfo
public record BatchInfo(
    int batchNo,
    String query,
    JsonNode params,
    String cursorToken,
    Integer expectedCount
) {
    public static BatchInfo from(Batch batch) {
        return new BatchInfo(
            batch.batchNo(),
            batch.query(),
            batch.params(),
            batch.cursorToken(),
            batch.expectedCount()
        );
    }
}
```

### 3.3 目录结构设计

#### **patra-spring-boot-starter-provenance 增强后结构**
```
patra-spring-boot-starter-provenance/
├── src/main/java/com/patra/starter/provenance/
│   ├── common/
│   │   ├── adapter/
│   │   │   ├── DataSourceAdapter.java           # 统一适配器接口
│   │   │   ├── AdapterRequest.java              # 请求模型
│   │   │   ├── AdapterResult.java               # 响应模型
│   │   │   ├── BatchInfo.java                   # 批次信息
│   │   │   └── AdapterRegistry.java             # 适配器注册表
│   │   ├── model/
│   │   │   └── StandardLiterature.java          # 迁移自 ingest
│   │   ├── converter/
│   │   │   └── BaseArticleConverter.java        # 转换器基类
│   │   └── （现有的 http、config、exception 等保持不变）
│   ├── pubmed/
│   │   ├── PubmedDataSourceAdapter.java         # PubMed 适配器实现
│   │   ├── converter/
│   │   │   └── PubmedArticleConverter.java      # 迁移自 ingest
│   │   └── （现有的 PubMedClient、model、request 等保持不变）
│   ├── epmc/
│   │   ├── EpmcDataSourceAdapter.java           # EPMC 适配器实现
│   │   ├── converter/
│   │   │   └── EpmcArticleConverter.java        # 新增
│   │   └── （现有的 EPMCClient 等保持不变）
│   └── crossref/
│       ├── CrossrefDataSourceAdapter.java       # Crossref 适配器实现
│       ├── converter/
│       │   └── CrossrefArticleConverter.java    # 新增
│       └── （现有的 model、request 等基础上完善）
```

---

## 4. 核心代码实现

### 4.1 标准模型定义

```java
// com.patra.starter.provenance.common.model.StandardLiterature
package com.patra.starter.provenance.common.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 标准化文献模型 - 数据源无关的统一表示
 *
 * <p>从 patra-ingest 迁移，作为所有数据源适配器的输出模型
 */
@Data
@Builder
@Jacksonized
public class StandardLiterature {

    private String title;
    private String abstractText;
    private List<StandardAuthor> authors;
    private StandardJournal journal;
    private Map<String, String> identifiers;  // pmid, doi, pmc 等
    private LocalDate publicationDate;
    private List<String> keywords;

    @Data
    @Builder
    @Jacksonized
    public static class StandardAuthor {
        private String lastName;
        private String foreName;
        private String affiliation;
    }

    @Data
    @Builder
    @Jacksonized
    public static class StandardJournal {
        private String title;
        private String issn;
        private String publisher;
    }
}
```

### 4.2 适配器注册表实现

```java
// com.patra.starter.provenance.common.adapter.AdapterRegistry
package com.patra.starter.provenance.common.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源适配器注册表
 *
 * <p>自动发现和注册所有 DataSourceAdapter 实现，提供线程安全的查找服务
 */
@Component
@Slf4j
public class AdapterRegistry {

    private final Map<String, DataSourceAdapter> adapters = new ConcurrentHashMap<>();

    public AdapterRegistry(List<DataSourceAdapter> adapterList) {
        for (DataSourceAdapter adapter : adapterList) {
            String provenanceCode = adapter.getProvenanceCode();
            adapters.put(provenanceCode.toLowerCase(), adapter);
            log.info("Registered data source adapter: provenanceCode={}, class={}",
                    provenanceCode, adapter.getClass().getSimpleName());
        }
        log.info("Total {} data source adapters registered", adapters.size());
    }

    /**
     * 获取指定数据源的适配器
     */
    public DataSourceAdapter getAdapter(String provenanceCode, String operationCode) {
        String key = provenanceCode.toLowerCase();
        DataSourceAdapter adapter = adapters.get(key);

        if (adapter == null) {
            throw new IllegalArgumentException(
                String.format("Data source adapter not found: provenanceCode=%s", provenanceCode));
        }

        if (!adapter.supports(operationCode)) {
            throw new IllegalArgumentException(
                String.format("Operation not supported: provenanceCode=%s, operationCode=%s",
                        provenanceCode, operationCode));
        }

        return adapter;
    }

    /**
     * 检查是否支持指定的数据源和操作
     */
    public boolean supports(String provenanceCode, String operationCode) {
        String key = provenanceCode.toLowerCase();
        DataSourceAdapter adapter = adapters.get(key);
        return adapter != null && adapter.supports(operationCode);
    }

    /**
     * 获取所有注册的数据源代码
     */
    public List<String> getRegisteredProvenanceCodes() {
        return List.copyOf(adapters.keySet());
    }
}
```

### 4.3 PubMed 适配器实现

```java
// com.patra.starter.provenance.pubmed.PubmedDataSourceAdapter
package com.patra.starter.provenance.pubmed;

import com.patra.starter.provenance.common.adapter.AdapterRequest;
import com.patra.starter.provenance.common.adapter.AdapterResult;
import com.patra.starter.provenance.common.adapter.DataSourceAdapter;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.model.StandardLiterature;
import com.patra.starter.provenance.pubmed.converter.PubmedArticleConverter;
import com.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import com.patra.starter.provenance.pubmed.model.request.EPostRequest;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import com.patra.starter.provenance.pubmed.model.response.EPostResponse;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import com.patra.starter.provenance.pubmed.request.PubMedESearchRequestAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.patra.starter.provenance.pubmed.request.PubMedESearchRequestAssembler.ASSEMBLER;

/**
 * PubMed 数据源适配器
 *
 * <p>整合现有的 PubMedClient 和迁移的转换器，提供统一的数据获取接口
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PubmedDataSourceAdapter implements DataSourceAdapter {

    private static final int EPOST_THRESHOLD = 200;

    private final PubMedClient pubMedClient;
    private final PubmedArticleConverter converter;

    @Override
    public String getProvenanceCode() {
        return "pubmed";
    }

    @Override
    public boolean supports(String operationCode) {
        return "HARVEST".equalsIgnoreCase(operationCode) ||
               "UPDATE".equalsIgnoreCase(operationCode);
    }

    @Override
    public AdapterResult fetchData(AdapterRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("PubMed adapter starting: operationCode={}, batchNo={}",
                    request.operationCode(), request.batchInfo().batchNo());

            // 1. 构建 ESearch 请求
            ESearchRequest searchRequest = ASSEMBLER.buildList(request.compiledParams());
            log.debug("Executing PubMed ESearch: query={}", searchRequest.getTerm());

            // 2. 执行 ESearch
            ESearchResponse searchResponse = pubMedClient.esearch(searchRequest, request.config());
            List<String> pmids = searchResponse.pmids();

            if (pmids.isEmpty()) {
                log.info("PubMed ESearch returned no results");
                return AdapterResult.success(List.of(), null);
            }

            log.debug("PubMed ESearch found {} PMIDs", pmids.size());

            // 3. 根据 PMID 数量选择获取策略
            List<StandardLiterature> literatures;
            if (pmids.size() <= EPOST_THRESHOLD) {
                // 直接 EFetch
                literatures = fetchDirectly(pmids, request.config());
            } else {
                // EPost + EFetch
                literatures = fetchViaEPost(pmids, request.config());
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("PubMed adapter completed: fetchedCount={}, duration={}ms",
                    literatures.size(), duration);

            return AdapterResult.success(literatures, searchResponse.nextCursor());

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("PubMed adapter failed after {}ms: operationCode={}, batchNo={}",
                    duration, request.operationCode(), request.batchInfo().batchNo(), ex);
            return AdapterResult.failure(ex.getMessage());
        }
    }

    /**
     * 直接通过 EFetch 获取文章详情
     */
    private List<StandardLiterature> fetchDirectly(List<String> pmids, ProvenanceConfig config) {
        EFetchRequest fetchRequest = new EFetchRequest(pmids);
        EFetchResponse fetchResponse = pubMedClient.efetch(fetchRequest, config);

        return convertArticles(fetchResponse);
    }

    /**
     * 通过 EPost + EFetch 获取文章详情（处理大量 PMID）
     */
    private List<StandardLiterature> fetchViaEPost(List<String> pmids, ProvenanceConfig config) {
        // 1. EPost 上传 PMIDs 到历史服务器
        EPostRequest postRequest = new EPostRequest(pmids);
        EPostResponse postResponse = pubMedClient.epost(postRequest, config);

        if (postResponse.webEnv() == null) {
            throw new RuntimeException("EPost failed: no WebEnv returned");
        }

        // 2. 使用 WebEnv 进行 EFetch
        EFetchRequest fetchRequest = EFetchRequest.builder()
                .webEnv(postResponse.webEnv())
                .queryKey(postResponse.queryKey())
                .build();
        EFetchResponse fetchResponse = pubMedClient.efetch(fetchRequest, config);

        return convertArticles(fetchResponse);
    }

    /**
     * 转换文章数据为标准模型
     */
    private List<StandardLiterature> convertArticles(EFetchResponse response) {
        return response.articles().stream()
                .map(article -> {
                    try {
                        return converter.toStandardLiterature(article);
                    } catch (Exception ex) {
                        log.warn("Failed to convert PubMed article: pmid={}",
                                article.pmid(), ex);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
```

### 4.4 配置增强实现

```java
// com.patra.starter.provenance.boot.ProvenanceProperties（增强现有类）
package com.patra.starter.provenance.boot;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 增强的 Provenance 配置属性
 *
 * <p>支持兜底配置 + 数据源特定覆盖的配置模式
 */
@Data
@ConfigurationProperties(prefix = "patra.provenance")
public class ProvenanceProperties {

    private boolean enabled = true;

    // 兜底配置 - 所有数据源共享
    private SourceProperties defaults = new SourceProperties();

    // 数据源特定配置
    private SourceProperties pubmed = new SourceProperties();
    private SourceProperties epmc = new SourceProperties();
    private SourceProperties crossref = new SourceProperties();

    /**
     * 获取指定数据源的合并配置
     */
    public SourceProperties getConfigForSource(String provenanceCode) {
        SourceProperties specific = switch (provenanceCode.toLowerCase()) {
            case "pubmed" -> pubmed;
            case "epmc" -> epmc;
            case "crossref" -> crossref;
            default -> new SourceProperties();
        };

        return mergeWithDefaults(specific, defaults);
    }

    /**
     * 将数据源特定配置与兜底配置合并
     */
    private SourceProperties mergeWithDefaults(SourceProperties specific, SourceProperties defaults) {
        SourceProperties merged = new SourceProperties();

        // 基础配置
        merged.setBaseUrl(firstNonNull(specific.getBaseUrl(), defaults.getBaseUrl()));

        // HTTP 配置
        merged.setHttp(mergeHttpConfig(specific.getHttp(), defaults.getHttp()));

        // 分页配置
        merged.setPagination(mergePaginationConfig(specific.getPagination(), defaults.getPagination()));

        // 其他配置字段...
        merged.setWindowOffset(mergeWindowOffsetConfig(specific.getWindowOffset(), defaults.getWindowOffset()));
        merged.setBatching(mergeBatchingConfig(specific.getBatching(), defaults.getBatching()));
        merged.setRetry(mergeRetryConfig(specific.getRetry(), defaults.getRetry()));
        merged.setRateLimit(mergeRateLimitConfig(specific.getRateLimit(), defaults.getRateLimit()));

        return merged;
    }

    /**
     * 三层配置合并：运行时 > 特定 > 兜底
     */
    public ProvenanceConfig mergeWithRuntime(String provenanceCode, ProvenanceConfig runtime) {
        SourceProperties sourceConfig = getConfigForSource(provenanceCode);

        if (runtime == null) {
            return sourceConfig.toProvenanceConfig();
        }

        return ProvenanceConfig.builder()
                .baseUrl(firstNonNull(runtime.baseUrl(), sourceConfig.getBaseUrl()))
                .http(mergeHttpConfig(runtime.http(), sourceConfig.getHttp()))
                .pagination(mergePaginationConfig(runtime.pagination(), sourceConfig.getPagination()))
                // ... 其他字段
                .build();
    }

    // 辅助方法
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    // 各配置对象的合并方法
    private HttpConfig mergeHttpConfig(HttpConfig specific, HttpConfig defaults) {
        if (specific == null && defaults == null) return null;
        if (specific == null) return defaults;
        if (defaults == null) return specific;

        return HttpConfig.builder()
                .timeoutConnectMillis(firstNonNull(specific.timeoutConnectMillis(), defaults.timeoutConnectMillis()))
                .timeoutReadMillis(firstNonNull(specific.timeoutReadMillis(), defaults.timeoutReadMillis()))
                .timeoutTotalMillis(firstNonNull(specific.timeoutTotalMillis(), defaults.timeoutTotalMillis()))
                .defaultHeaders(mergeHeaders(specific.defaultHeaders(), defaults.defaultHeaders()))
                .build();
    }

    // 其他 merge 方法类似...
}
```

### 4.5 Ingest 通用执行器实现

```java
// com.patra.ingest.app.usecase.execution.GenericBatchExecutor
package com.patra.ingest.app.usecase.execution;

import com.patra.ingest.domain.model.vo.Batch;
import com.patra.ingest.domain.model.vo.BatchResult;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.port.LiteraturePublisherPort;
import com.patra.starter.provenance.common.adapter.AdapterRegistry;
import com.patra.starter.provenance.common.adapter.AdapterRequest;
import com.patra.starter.provenance.common.adapter.AdapterResult;
import com.patra.starter.provenance.common.adapter.BatchInfo;
import com.patra.starter.provenance.common.adapter.DataSourceAdapter;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.model.StandardLiterature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用批处理执行器
 *
 * <p>替换所有 BatchExecutor 实现，通过适配器模式支持多数据源
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenericBatchExecutor {

    private final AdapterRegistry adapterRegistry;
    private final LiteraturePublisherPort publisherPort;
    private final ProvenanceConfigConverter configConverter;

    /**
     * 执行单个批次的数据获取
     */
    public BatchResult execute(ExecutionContext context, Batch batch) {
        long startTime = System.currentTimeMillis();
        String provenanceCode = context.provenanceCode();
        String operationCode = context.operationCode();
        int batchNo = batch.batchNo();

        try {
            log.info("Generic batch execution starting: provenanceCode={}, operationCode={}, batchNo={}",
                    provenanceCode, operationCode, batchNo);

            // 1. 选择数据源适配器
            DataSourceAdapter adapter = adapterRegistry.getAdapter(provenanceCode, operationCode);

            // 2. 转换配置
            ProvenanceConfig runtimeConfig = configConverter.convert(context.configSnapshot());

            // 3. 构建适配器请求
            AdapterRequest request = AdapterRequest.builder()
                    .operationCode(operationCode)
                    .config(runtimeConfig)
                    .compiledQuery(context.compiledQuery())
                    .compiledParams(context.compiledParams())
                    .batchInfo(BatchInfo.from(batch))
                    .build();

            // 4. 执行数据获取
            log.debug("Delegating to adapter: class={}", adapter.getClass().getSimpleName());
            AdapterResult result = adapter.fetchData(request);

            if (!result.success()) {
                log.error("Adapter execution failed: provenanceCode={}, batchNo={}, error={}",
                        provenanceCode, batchNo, result.errorMessage());
                return BatchResult.failure(batchNo, result.errorMessage());
            }

            // 5. 发布数据（保持现有逻辑）
            String storageKey = publishLiteratures(result.literatures(), context, batch);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Generic batch execution completed: provenanceCode={}, batchNo={}, fetchedCount={}, duration={}ms",
                    provenanceCode, batchNo, result.fetchedCount(), duration);

            return BatchResult.success(batchNo, result.fetchedCount(), result.nextCursorToken(), storageKey);

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Generic batch execution failed after {}ms: provenanceCode={}, batchNo={}",
                    duration, provenanceCode, batchNo, ex);
            return BatchResult.failure(batchNo, ex.getMessage());
        }
    }

    /**
     * 发布文献数据（保持现有逻辑）
     */
    private String publishLiteratures(List<StandardLiterature> literatures,
                                    ExecutionContext context, Batch batch) {
        if (literatures.isEmpty()) {
            log.debug("No literature to publish for batch {}", batch.batchNo());
            return null;
        }

        try {
            LiteraturePublisherPort.PublishContext publishContext =
                    LiteraturePublisherPort.PublishContext.builder()
                            .runId(context.runId())
                            .batchNo(batch.batchNo())
                            .provenanceCode(context.provenanceCode())
                            .build();

            LiteraturePublisherPort.PublishResult publishResult =
                    publisherPort.publish(literatures, publishContext);

            log.debug("Published {} literatures to storage: storageKey={}",
                    literatures.size(), publishResult.storageKey());

            return publishResult.storageKey();

        } catch (Exception ex) {
            log.error("Failed to publish literatures for batch {}", batch.batchNo(), ex);
            throw new RuntimeException("Literature publication failed", ex);
        }
    }
}
```

---

## 5. 文件迁移清单

### 5.1 新增文件

#### **patra-spring-boot-starter-provenance 新增**

| 文件路径 | 说明 | 状态 |
|---------|------|------|
| `common/adapter/DataSourceAdapter.java` | 统一适配器接口 | 新增 |
| `common/adapter/AdapterRequest.java` | 请求模型 | 新增 |
| `common/adapter/AdapterResult.java` | 响应模型 | 新增 |
| `common/adapter/BatchInfo.java` | 批次信息 | 新增 |
| `common/adapter/AdapterRegistry.java` | 适配器注册表 | 新增 |
| `common/model/StandardLiterature.java` | 标准模型 | 迁移自 ingest |
| `common/converter/BaseArticleConverter.java` | 转换器基类 | 新增 |
| `pubmed/PubmedDataSourceAdapter.java` | PubMed适配器 | 新增 |
| `pubmed/converter/PubmedArticleConverter.java` | PubMed转换器 | 迁移自 ingest |
| `epmc/EpmcDataSourceAdapter.java` | EPMC适配器 | 新增 |
| `epmc/converter/EpmcArticleConverter.java` | EPMC转换器 | 新增 |
| `crossref/CrossrefDataSourceAdapter.java` | Crossref适配器 | 新增 |
| `crossref/converter/CrossrefArticleConverter.java` | Crossref转换器 | 新增 |

#### **patra-ingest 新增**

| 文件路径 | 说明 | 状态 |
|---------|------|------|
| `app/usecase/execution/GenericBatchExecutor.java` | 通用批处理执行器 | 新增 |
| `infra/config/ProvenanceConfigConverter.java` | 配置转换器 | 新增 |

### 5.2 删除文件

#### **patra-ingest 删除**

| 文件路径 | 说明 | 删除原因 |
|---------|------|---------|
| `app/usecase/execution/batch/executor/BatchExecutor.java` | 批处理执行器接口 | 被 DataSourceAdapter 替代 |
| `app/usecase/execution/batch/executor/PubmedBatchExecutor.java` | PubMed执行器 | 迁移到 starter 中 |
| `app/usecase/execution/batch/executor/BatchExecutorRegistry.java` | 执行器注册表 | 被 AdapterRegistry 替代 |
| `app/usecase/execution/batch/converter/PubmedArticleConverter.java` | PubMed转换器 | 迁移到 starter 中 |
| `domain/model/vo/StandardLiterature.java` | 标准模型 | 迁移到 starter 中 |

### 5.3 修改文件

#### **patra-ingest 修改**

| 文件路径 | 修改内容 | 影响范围 |
|---------|---------|----------|
| `app/usecase/execution/ExecuteTaskBatchesUseCaseImpl.java` | 使用 GenericBatchExecutor | 核心执行逻辑 |
| `pom.xml` | 删除对具体数据源 starter 的依赖，添加通用 starter 依赖 | 依赖管理 |

#### **patra-spring-boot-starter-provenance 修改**

| 文件路径 | 修改内容 | 影响范围 |
|---------|---------|----------|
| `boot/ProvenanceAutoConfiguration.java` | 添加适配器相关 Bean 配置 | Spring配置 |
| `boot/ProvenanceProperties.java` | 增强配置合并逻辑 | 配置管理 |

### 5.4 Maven 依赖调整

#### **patra-ingest/pom.xml**

```xml
<!-- 删除 -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-starter-provenance-pubmed</artifactId>
</dependency>
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-starter-provenance-epmc</artifactId>
</dependency>

<!-- 添加 -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-provenance</artifactId>
    <version>${project.version}</version>
</dependency>
```

#### **patra-spring-boot-starter-provenance/pom.xml**

```xml
<!-- 保持现有依赖，新增 -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
```

---

## 6. 实施计划

### 6.1 Phase 1: 基础设施建设（第1-2周）

#### **目标**：建立适配器框架，保持系统正常运行

#### **详细步骤**：

**Day 1-2: 接口设计**
```bash
# 1. 创建适配器相关目录
mkdir -p patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/common/adapter
mkdir -p patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/common/model
mkdir -p patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/common/converter

# 2. 创建核心接口
# - DataSourceAdapter.java
# - AdapterRequest.java
# - AdapterResult.java
# - BatchInfo.java
```

**Day 3-4: 模型迁移**
```bash
# 1. 迁移 StandardLiterature 到 starter
cp patra-ingest/patra-ingest-domain/src/main/java/.../StandardLiterature.java \
   patra-spring-boot-starter-provenance/src/main/java/.../common/model/

# 2. 更新包路径和依赖
# 3. 在 ingest 中添加对新模型的依赖
```

**Day 5-7: 注册表实现**
```bash
# 1. 实现 AdapterRegistry
# 2. 更新 ProvenanceAutoConfiguration
# 3. 添加配置合并逻辑
# 4. 编写单元测试
```

**验收标准**：
- ✅ 所有新接口编译通过
- ✅ StandardLiterature 迁移完成，ingest 正常引用
- ✅ AdapterRegistry 单元测试通过
- ✅ 现有功能不受影响

### 6.2 Phase 2: 适配器实现（第3-4周）

#### **目标**：实现 PubMed 适配器，准备切换

#### **详细步骤**：

**Day 8-10: PubMed 转换器迁移**
```bash
# 1. 迁移 PubmedArticleConverter
cp patra-ingest/patra-ingest-app/src/main/java/.../PubmedArticleConverter.java \
   patra-spring-boot-starter-provenance/src/main/java/.../pubmed/converter/

# 2. 更新导入路径，引用新的 StandardLiterature
# 3. 编写转换器单元测试
```

**Day 11-14: PubMed 适配器实现**
```bash
# 1. 实现 PubmedDataSourceAdapter
# 2. 整合现有 PubMedClient
# 3. 实现配置合并逻辑
# 4. 编写集成测试

# 测试脚本
./test-pubmed-adapter.sh
```

**验收标准**：
- ✅ PubmedDataSourceAdapter 实现完成
- ✅ 配置合并逻辑正确工作
- ✅ 与现有 PubmedBatchExecutor 输出结果一致
- ✅ 集成测试通过

### 6.3 Phase 3: Ingest 重构（第5周）

#### **目标**：实现通用执行器，切换到新架构

#### **详细步骤**：

**Day 15-16: 通用执行器实现**
```bash
# 1. 实现 GenericBatchExecutor
# 2. 实现 ProvenanceConfigConverter
# 3. 添加配置开关支持新旧实现切换
```

**Day 17-18: 切换逻辑**
```bash
# 1. 修改 ExecuteTaskBatchesUseCaseImpl
# 2. 添加功能开关配置
# 3. 实现并行验证逻辑
```

**配置开关示例**：
```yaml
# application.yml
batch.execution:
  use-new-adapter: false  # 默认使用旧实现
  adapter-comparison-mode: true  # 开启对比模式
```

**Day 19: 小流量验证**
```bash
# 1. 开启对比模式
# 2. 验证新旧实现结果一致性
# 3. 监控性能指标
```

**验收标准**：
- ✅ GenericBatchExecutor 正常工作
- ✅ 新旧实现输出结果一致（对比模式验证）
- ✅ 性能指标正常
- ✅ 可以通过配置开关切换

### 6.4 Phase 4: 清理和优化（第6周）

#### **目标**：清理冗余代码，完成重构

#### **详细步骤**：

**Day 20-21: 全量切换**
```bash
# 1. 配置切换到新实现
batch.execution.use-new-adapter: true

# 2. 监控系统稳定性
# 3. 验证各项功能正常
```

**Day 22-23: 代码清理**
```bash
# 1. 删除旧的 BatchExecutor 相关代码
# 2. 清理不需要的依赖
# 3. 更新文档和注释
```

**Day 24: 性能优化**
```bash
# 1. 分析性能瓶颈
# 2. 优化适配器调用
# 3. 调整配置参数
```

**验收标准**：
- ✅ 旧代码完全清理
- ✅ 系统性能符合预期
- ✅ 所有测试通过
- ✅ 文档更新完成

---

## 7. 测试策略

### 7.1 单元测试

#### **适配器层测试**
```java
// PubmedDataSourceAdapterTest.java
@ExtendWith(MockitoExtension.class)
class PubmedDataSourceAdapterTest {

    @Mock
    private PubMedClient pubMedClient;

    @Mock
    private PubmedArticleConverter converter;

    @InjectMocks
    private PubmedDataSourceAdapter adapter;

    @Test
    void testFetchDataSuccess() {
        // Given
        AdapterRequest request = createTestRequest();
        ESearchResponse searchResponse = createMockSearchResponse();
        EFetchResponse fetchResponse = createMockFetchResponse();
        StandardLiterature literature = createMockLiterature();

        when(pubMedClient.esearch(any(), any())).thenReturn(searchResponse);
        when(pubMedClient.efetch(any(), any())).thenReturn(fetchResponse);
        when(converter.toStandardLiterature(any())).thenReturn(literature);

        // When
        AdapterResult result = adapter.fetchData(request);

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.literatures()).hasSize(1);
        assertThat(result.fetchedCount()).isEqualTo(1);
    }

    @Test
    void testFetchDataWithEPost() {
        // 测试大量 PMID 的 EPost 流程
    }

    @Test
    void testFetchDataFailure() {
        // 测试异常处理
    }
}
```

#### **注册表测试**
```java
// AdapterRegistryTest.java
@ExtendWith(MockitoExtension.class)
class AdapterRegistryTest {

    @Test
    void testAutoRegistration() {
        // Given
        DataSourceAdapter adapter1 = mock(DataSourceAdapter.class);
        when(adapter1.getProvenanceCode()).thenReturn("pubmed");
        when(adapter1.supports("HARVEST")).thenReturn(true);

        List<DataSourceAdapter> adapters = List.of(adapter1);

        // When
        AdapterRegistry registry = new AdapterRegistry(adapters);

        // Then
        assertThat(registry.supports("pubmed", "HARVEST")).isTrue();
        assertThat(registry.getAdapter("pubmed", "HARVEST")).isSameAs(adapter1);
    }

    @Test
    void testAdapterNotFound() {
        // 测试适配器未找到的异常处理
    }
}
```

### 7.2 集成测试

#### **端到端测试**
```java
// GenericBatchExecutorIntegrationTest.java
@SpringBootTest
@TestPropertySource(properties = {
    "batch.execution.use-new-adapter=true"
})
class GenericBatchExecutorIntegrationTest {

    @Autowired
    private GenericBatchExecutor executor;

    @Test
    void testPubmedBatchExecution() {
        // Given
        ExecutionContext context = createTestContext("pubmed");
        Batch batch = createTestBatch();

        // When
        BatchResult result = executor.execute(context, batch);

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.fetchedCount()).isGreaterThan(0);
    }

    @Test
    @Disabled("需要真实 API 调用")
    void testRealPubmedAPI() {
        // 真实 API 集成测试
    }
}
```

### 7.3 性能测试

#### **对比测试脚本**
```bash
#!/bin/bash
# performance-comparison.sh

echo "开始性能对比测试..."

# 配置旧实现
kubectl patch configmap app-config --patch '{"data":{"batch.execution.use-new-adapter":"false"}}'
kubectl rollout restart deployment/ingest-service

# 等待部署完成
kubectl rollout status deployment/ingest-service

# 执行测试任务
echo "测试旧实现性能..."
./run-batch-test.sh --implementation=old --batch-size=100 --iterations=10

# 配置新实现
kubectl patch configmap app-config --patch '{"data":{"batch.execution.use-new-adapter":"true"}}'
kubectl rollout restart deployment/ingest-service
kubectl rollout status deployment/ingest-service

# 执行测试任务
echo "测试新实现性能..."
./run-batch-test.sh --implementation=new --batch-size=100 --iterations=10

# 分析结果
echo "生成性能对比报告..."
python performance-analyzer.py --old-results=old_results.json --new-results=new_results.json
```

### 7.4 数据一致性测试

#### **对比验证脚本**
```java
// DataConsistencyTest.java
@Component
public class DataConsistencyValidator {

    public boolean validateResults(BatchResult oldResult, BatchResult newResult) {
        // 1. 验证基本指标
        if (oldResult.fetchedCount() != newResult.fetchedCount()) {
            log.warn("Fetched count mismatch: old={}, new={}",
                    oldResult.fetchedCount(), newResult.fetchedCount());
            return false;
        }

        // 2. 验证数据内容（需要反序列化存储的数据）
        List<StandardLiterature> oldLiteratures = loadFromStorage(oldResult.storageKey());
        List<StandardLiterature> newLiteratures = loadFromStorage(newResult.storageKey());

        return compareContentDeep(oldLiteratures, newLiteratures);
    }

    private boolean compareContentDeep(List<StandardLiterature> list1, List<StandardLiterature> list2) {
        // 深度对比文献内容
        // 忽略不重要的字段差异
        // 重点关注标题、作者、DOI等核心字段
    }
}
```

---

## 8. 风险控制

### 8.1 技术风险

#### **风险1: 性能回退**

**风险描述**：新架构引入适配器调用可能增加延迟

**影响程度**：中等

**缓解措施**：
1. **基准测试**：重构前测量现有性能基线
2. **性能监控**：实时监控关键性能指标
3. **优化策略**：
   ```java
   // 适配器调用优化
   @Cacheable(value = "adapters", key = "#provenanceCode")
   public DataSourceAdapter getAdapter(String provenanceCode, String operationCode) {
       // 缓存适配器实例
   }

   // 配置合并优化
   @Cacheable(value = "configs", key = "#provenanceCode + '_' + #configHash")
   public ProvenanceConfig mergeConfig(String provenanceCode, String configHash) {
       // 缓存合并后的配置
   }
   ```

**回滚方案**：保留配置开关，可快速切回旧实现

#### **风险2: 数据一致性问题**

**风险描述**：重构过程中可能出现数据格式差异

**影响程度**：高

**缓解措施**：
1. **并行验证**：新旧实现同时运行，对比结果
2. **逐步切换**：先切换小流量验证，再全量切换
3. **数据校验**：
   ```java
   // 自动数据一致性检查
   @EventListener
   public void onBatchCompleted(BatchCompletedEvent event) {
       if (comparisonMode && event.hasOldAndNewResults()) {
           boolean consistent = validator.validateResults(
               event.getOldResult(), event.getNewResult());
           if (!consistent) {
               alertService.sendDataInconsistencyAlert(event);
           }
       }
   }
   ```

### 8.2 运维风险

#### **风险3: 部署复杂性**

**风险描述**：多模块协调发布可能出现版本不一致

**影响程度**：中等

**缓解措施**：
1. **向后兼容**：确保新接口向后兼容
2. **独立发布**：模块可独立发布，不强依赖
3. **版本管理**：
   ```yaml
   # 部署脚本
   # deploy.sh
   #!/bin/bash

   # 1. 先发布 starter（向后兼容）
   ./deploy-module.sh patra-spring-boot-starter-provenance

   # 2. 等待服务稳定
   ./wait-for-health.sh

   # 3. 再发布 ingest（可选使用新功能）
   ./deploy-module.sh patra-ingest
   ```

#### **风险4: 配置复杂性增加**

**风险描述**：新的配置结构可能导致配置错误

**影响程度**：低

**缓解措施**：
1. **配置验证**：启动时验证配置完整性
2. **默认值**：提供合理的默认配置
3. **文档**：详细的配置说明文档

### 8.3 业务风险

#### **风险5: 数据采集中断**

**风险描述**：重构过程中可能影响数据采集任务

**影响程度**：高

**缓解措施**：
1. **零停机部署**：使用蓝绿部署或滚动升级
2. **快速回滚**：准备快速回滚方案
3. **监控告警**：实时监控采集任务状态

**应急方案**：
```bash
# 紧急回滚脚本
#!/bin/bash
# emergency-rollback.sh

echo "开始紧急回滚..."

# 1. 立即切换到旧实现
kubectl patch configmap app-config --patch '{"data":{"batch.execution.use-new-adapter":"false"}}'

# 2. 重启服务
kubectl rollout restart deployment/ingest-service

# 3. 等待服务恢复
kubectl rollout status deployment/ingest-service

# 4. 验证服务状态
./health-check.sh

echo "回滚完成，服务已恢复"
```

### 8.4 风险监控指标

```yaml
# 关键监控指标
performance_metrics:
  - batch_execution_duration_p99
  - batch_success_rate
  - adapter_call_duration
  - config_merge_duration

business_metrics:
  - daily_literature_count
  - data_quality_score
  - error_rate_by_source

system_metrics:
  - memory_usage
  - cpu_usage
  - gc_frequency
```

---

## 9. 验收标准

### 9.1 功能验收

#### **基本功能**
- [ ] 所有现有数据源（PubMed、EPMC）正常工作
- [ ] 数据转换结果与重构前一致
- [ ] 配置系统正常工作，支持兜底配置和特定覆盖
- [ ] 错误处理和重试机制正常

#### **扩展性验证**
- [ ] 可以通过配置新增数据源，无需修改代码
- [ ] 新增 Crossref 适配器作为扩展性验证
- [ ] 适配器注册表自动发现新适配器

#### **性能验证**
- [ ] 批处理执行时间不超过重构前的110%
- [ ] 内存使用量无显著增加
- [ ] 并发处理能力保持不变

### 9.2 代码质量

#### **架构质量**
- [ ] 模块职责清晰，无循环依赖
- [ ] 接口设计符合开闭原则
- [ ] 代码符合 Google Java Style Guide

#### **测试覆盖**
- [ ] 单元测试覆盖率 > 85%
- [ ] 集成测试覆盖核心流程
- [ ] 性能测试和数据一致性测试通过

### 9.3 运维质量

#### **可观测性**
- [ ] 关键操作有完整的日志记录
- [ ] 监控指标正确暴露
- [ ] 分布式链路追踪正常

#### **稳定性**
- [ ] 7天稳定运行无故障
- [ ] 异常情况下系统能正常恢复
- [ ] 回滚方案经过验证

### 9.4 文档完整性

#### **技术文档**
- [ ] 架构设计文档更新
- [ ] API 接口文档完整
- [ ] 配置参数说明清晰

#### **运维文档**
- [ ] 部署手册更新
- [ ] 故障排查指南
- [ ] 性能调优建议

---

## 🎯 总结

这个重构方案通过**充分利用现有基础设施**，实现了清晰的职责分离：

- **Registry**：保持纯净的配置管理职责
- **Starter**：内聚所有数据源相关逻辑
- **Ingest**：专注通用批处理引擎

重构后的架构具有**优秀的扩展性**、**更高的可维护性**和**清晰的模块边界**，为系统的长期演进奠定了坚实基础。

通过分阶段实施、风险控制和全面测试，确保重构过程平稳进行，最终实现架构质量的显著提升。

---

## 📚 附录

### A.1 参考文档

- [patra-spring-boot-starter-provenance README.md](../patra-spring-boot-starter-provenance/README.md)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Spring Boot 自动配置最佳实践](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-developing-auto-configuration)

### A.2 关键文件路径索引

```
# 核心接口定义
/patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/common/adapter/

# PubMed 适配器实现
/patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/pubmed/

# 通用批处理执行器
/patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/GenericBatchExecutor.java

# 配置管理
/patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/boot/ProvenanceProperties.java
```

### A.3 联系方式

如有实施过程中的问题，请联系：
- 架构设计：Jobs（Claude Assistant）
- 技术实施：开发团队
- 运维支持：DevOps 团队

---

**文档版本**：v1.0
**创建日期**：2025-10-28
**最后更新**：2025-10-28
**状态**：待实施