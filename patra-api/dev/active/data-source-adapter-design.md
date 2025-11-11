# 数据源适配器架构设计方案

**文档版本**: 1.0.0
**创建日期**: 2025-11-11
**作者**: Patra 架构团队
**状态**: 实施中

---

## 1. 架构概览

### 1.1 调用链路

```
GenericBatchExecutor (Application Layer)
         ↓
    AdapterRegistry
         ↓
    DataSourceAdapter<T>
         ↓
    DataTransformStrategy<S, T>
         ↓
    CanonicalData
```

### 1.2 架构图

```
┌────────────────────────────────────────────────────────────────┐
│                       Application Layer                         │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ GenericBatchExecutor                                    │  │
│  │   - 执行批次数据获取                                     │  │
│  │   - 通过 AdapterRegistry 获取适配器                      │  │
│  │   - 处理重试和错误                                       │  │
│  │   - 发布规范化数据                                       │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────┬────────────────────────────────────┘
                           ↓
┌────────────────────────────────────────────────────────────────┐
│                    Infrastructure Layer                         │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ AdapterRegistry                                         │  │
│  │   - 管理所有数据源适配器                                 │  │
│  │   - 按数据源和数据类型查找适配器                          │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ DataSourceAdapter<T> 实现                               │  │
│  │   - PubMedAdapter                                       │  │
│  │   - EPMCAdapter                                         │  │
│  │   - ArXivAdapter                                        │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ DataTransformStrategy<S, T> 实现                        │  │
│  │   - PubMedToLiteratureStrategy                          │  │
│  │   - EPMCToAuthorStrategy                                │  │
│  │   - ArXivToFullTextStrategy                             │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ External Clients (防腐层)                               │  │
│  │   - PubMedClient → PubMedArticle                        │  │
│  │   - EPMCClient → EPMCPublication                        │  │
│  │   - ArXivClient → ArXivPaper                            │  │
│  └─────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

---

## 2. 规范数据模型设计

### 2.1 顶层接口

```java
// patra-common/src/main/java/com/patra/common/model/CanonicalData.java
package com.patra.common.model;

import java.time.Instant;

/**
 * 所有规范数据模型的顶层接口
 */
public interface CanonicalData {

    /**
     * 获取数据的唯一标识符
     */
    String getId();

    /**
     * 获取数据类型
     */
    DataType getDataType();

    /**
     * 获取数据来源
     */
    String getProvenance();

    /**
     * 获取数据创建时间
     */
    Instant getCreatedAt();

    /**
     * 数据验证
     */
    default ValidationResult validate() {
        return ValidationResult.success();
    }
}

/**
 * 数据类型枚举
 */
public enum DataType {
    LITERATURE("literature", "文献"),
    JOURNAL("journal", "期刊"),
    AUTHOR("author", "作者"),
    CITATION("citation", "引用"),
    FULLTEXT("fulltext", "原文"),
    AFFILIATION("affiliation", "机构"),
    GRANT("grant", "基金");

    private final String code;
    private final String description;

    // 构造器和方法省略
}
```

### 2.2 具体数据模型

```java
// patra-common/src/main/java/com/patra/common/model/CanonicalLiterature.java
package com.patra.common.model;

/**
 * 规范文献数据模型
 */
@Data
@Builder
public class CanonicalLiterature implements CanonicalData {

    private String id;
    private String title;
    private String abstractText;
    private List<CanonicalAuthor> authors;
    private CanonicalJournal journal;
    private LocalDate publicationDate;
    private List<String> keywords;
    private Map<String, String> identifiers; // doi, pmid, arxivId等
    private String provenance;
    private Instant createdAt;

    @Override
    public DataType getDataType() {
        return DataType.LITERATURE;
    }
}

/**
 * 规范作者数据模型
 */
@Data
@Builder
public class CanonicalAuthor implements CanonicalData {

    private String id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String orcid;
    private String email;
    private List<String> affiliations;
    private String provenance;
    private Instant createdAt;

    @Override
    public DataType getDataType() {
        return DataType.AUTHOR;
    }
}

/**
 * 规范期刊数据模型
 */
@Data
@Builder
public class CanonicalJournal implements CanonicalData {

    private String id;
    private String issn;
    private String eissn;
    private String title;
    private String abbreviation;
    private String publisher;
    private Double impactFactor;
    private String country;
    private String provenance;
    private Instant createdAt;

    @Override
    public DataType getDataType() {
        return DataType.JOURNAL;
    }
}

/**
 * 规范引用数据模型
 */
@Data
@Builder
public class CanonicalCitation implements CanonicalData {

    private String id;
    private String citingId;      // 引用文献ID
    private String citedId;       // 被引文献ID
    private String context;       // 引用上下文
    private CitationType type;    // 引用类型
    private String provenance;
    private Instant createdAt;

    @Override
    public DataType getDataType() {
        return DataType.CITATION;
    }

    public enum CitationType {
        DIRECT, INDIRECT, SELF
    }
}

/**
 * 规范全文数据模型
 */
@Data
@Builder
public class CanonicalFullText implements CanonicalData {

    private String id;
    private String literatureId;
    private String format;        // PDF, HTML, XML
    private String content;       // 文本内容
    private String downloadUrl;   // 下载链接
    private Long fileSize;
    private String provenance;
    private Instant createdAt;

    @Override
    public DataType getDataType() {
        return DataType.FULLTEXT;
    }
}
```

---

## 3. 核心接口设计

### 3.1 数据源适配器接口

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/common/adapter/DataSourceAdapter.java
package com.patra.starter.provenance.common.adapter;

import com.patra.common.model.CanonicalData;

/**
 * 数据源适配器泛型接口
 *
 * <p>由 GenericBatchExecutor 调用，执行数据获取和转换</p>
 *
 * @param <T> 返回的规范数据类型
 */
public interface DataSourceAdapter<T extends CanonicalData> {

    /**
     * 返回数据源代码
     *
     * @return 数据源标识（如 "pubmed", "epmc"）
     */
    String getProvenanceCode();

    /**
     * 执行数据检索和转换
     *
     * @param request 包含查询条件、配置和元数据的请求对象
     * @return 包含规范化数据的结果
     */
    AdapterResult<T> fetchData(AdapterRequest request);

    /**
     * 获取适配器支持的能力
     *
     * @return 能力描述对象
     */
    AdapterCapability getCapabilities();

    /**
     * 获取支持的数据类型类
     *
     * @return 数据类型的 Class 对象
     */
    Class<T> getDataTypeClass();
}
```

### 3.2 增强的 AdapterResult

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/common/adapter/AdapterResult.java
package com.patra.starter.provenance.common.adapter;

import com.patra.common.model.CanonicalData;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 泛型化的适配器执行结果
 *
 * @param <T> 数据载荷类型
 */
public record AdapterResult<T extends CanonicalData>(
    boolean success,
    List<T> data,
    String nextCursorToken,
    String errorMessage,
    int fetchedCount,
    ErrorType errorType,
    Map<String, Object> metadata
) {

    public AdapterResult {
        data = data == null ? List.of() : List.copyOf(data);
        fetchedCount = Math.max(fetchedCount, data.size());
        errorType = Objects.requireNonNullElse(errorType, ErrorType.NONE);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * 创建成功结果
     */
    public static <T extends CanonicalData> AdapterResult<T> success(
            List<T> data,
            String nextCursorToken) {
        return new AdapterResult<>(
            true,
            data,
            nextCursorToken,
            null,
            data == null ? 0 : data.size(),
            ErrorType.NONE,
            Map.of()
        );
    }

    /**
     * 创建可重试的失败结果
     */
    public static <T extends CanonicalData> AdapterResult<T> retriableFailure(
            String errorMessage) {
        return new AdapterResult<>(
            false,
            List.of(),
            null,
            errorMessage,
            0,
            ErrorType.RETRIABLE,
            Map.of()
        );
    }

    /**
     * 创建不可重试的失败结果
     */
    public static <T extends CanonicalData> AdapterResult<T> nonRetriableFailure(
            String errorMessage) {
        return new AdapterResult<>(
            false,
            List.of(),
            null,
            errorMessage,
            0,
            ErrorType.NON_RETRIABLE,
            Map.of()
        );
    }

    /**
     * 创建部分成功结果
     */
    public static <T extends CanonicalData> AdapterResult<T> partialSuccess(
            List<T> data,
            String nextCursorToken,
            String warningMessage,
            int totalAttempted) {
        return new AdapterResult<>(
            true,
            data,
            nextCursorToken,
            warningMessage,
            totalAttempted,
            ErrorType.PARTIAL_SUCCESS,
            Map.of()
        );
    }

    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        NONE,
        RETRIABLE,
        NON_RETRIABLE,
        PARTIAL_SUCCESS
    }
}
```

### 3.3 增强的 AdapterRequest

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/common/adapter/AdapterRequest.java
package com.patra.starter.provenance.common.adapter;

/**
 * 适配器请求对象
 */
public record AdapterRequest(
    String operationCode,
    ProvenanceConfig config,
    BatchExecutionParams executionParams,
    BatchMetadata metadata,
    DataType requestedDataType
) {

    public AdapterRequest {
        if (operationCode == null || operationCode.isBlank()) {
            throw new IllegalArgumentException("operationCode 不能为空");
        }
        if (executionParams == null) {
            throw new IllegalArgumentException("executionParams 不能为空");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata 不能为空");
        }
        // requestedDataType 可以为 null，适配器会使用默认类型
    }
}
```

### 3.4 能力声明

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/common/adapter/AdapterCapability.java
package com.patra.starter.provenance.common.adapter;

import com.patra.common.model.DataType;
import java.util.Set;

/**
 * 数据源能力声明
 */
@Data
@Builder
public class AdapterCapability {

    private String dataSource;
    private Set<DataType> supportedDataTypes;
}
```

---

## 4. 数据转换策略设计

### 4.1 策略接口

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/common/strategy/DataTransformStrategy.java
package com.patra.starter.provenance.common.strategy;

import com.patra.common.model.CanonicalData;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据转换策略接口
 *
 * @param <S> 源数据类型（外部模型）
 * @param <T> 目标数据类型（规范模型）
 */
public interface DataTransformStrategy<S, T extends CanonicalData> {

    /**
     * 执行单个数据转换
     *
     * @param source 源数据对象
     * @return 转换后的规范数据对象
     */
    T transform(S source);

    /**
     * 批量转换
     *
     * @param sources 源数据列表
     * @return 转换结果
     */
    default TransformResult<T> batchTransform(List<S> sources) {
        List<T> successItems = new ArrayList<>();
        List<TransformError> errors = new ArrayList<>();

        for (int i = 0; i < sources.size(); i++) {
            try {
                T result = transform(sources.get(i));
                if (result != null) {
                    successItems.add(result);
                }
            } catch (Exception e) {
                errors.add(new TransformError(i, sources.get(i), e.getMessage()));
            }
        }

        return new TransformResult<>(successItems, errors);
    }

    /**
     * 获取源类型
     */
    Class<S> getSourceType();

    /**
     * 获取目标类型
     */
    Class<T> getTargetType();
}

/**
 * 转换结果
 */
@Data
public class TransformResult<T extends CanonicalData> {
    private final List<T> successItems;
    private final List<TransformError> errors;

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public double getSuccessRate() {
        int total = successItems.size() + errors.size();
        return total == 0 ? 0 : (double) successItems.size() / total;
    }
}

/**
 * 转换错误
 */
@Data
public class TransformError {
    private final int index;
    private final Object sourceData;
    private final String errorMessage;
}
```

### 4.2 策略注册中心

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/common/strategy/StrategyRegistry.java
package com.patra.starter.provenance.common.strategy;

import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略注册中心
 */
@Component
@Slf4j
public class StrategyRegistry {

    private final Map<StrategyKey, DataTransformStrategy<?, ?>> strategies = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void registerStrategies() {
        Map<String, DataTransformStrategy> strategyBeans =
            applicationContext.getBeansOfType(DataTransformStrategy.class);

        for (Map.Entry<String, DataTransformStrategy> entry : strategyBeans.entrySet()) {
            DataTransformStrategy<?, ?> strategy = entry.getValue();
            register(strategy);
            log.info("注册转换策略: {} -> {}",
                strategy.getSourceType().getSimpleName(),
                strategy.getTargetType().getSimpleName());
        }
    }

    public <S, T extends CanonicalData> void register(DataTransformStrategy<S, T> strategy) {
        StrategyKey key = new StrategyKey(
            strategy.getSourceType(),
            strategy.getTargetType()
        );
        strategies.put(key, strategy);
    }

    @SuppressWarnings("unchecked")
    public <S, T extends CanonicalData> DataTransformStrategy<S, T> getStrategy(
            Class<S> sourceType,
            Class<T> targetType) {

        StrategyKey key = new StrategyKey(sourceType, targetType);
        DataTransformStrategy<?, ?> strategy = strategies.get(key);

        if (strategy == null) {
            throw new IllegalArgumentException(
                String.format("未找到转换策略: %s -> %s",
                    sourceType.getSimpleName(),
                    targetType.getSimpleName())
            );
        }

        return (DataTransformStrategy<S, T>) strategy;
    }

    @Data
    private static class StrategyKey {
        private final Class<?> sourceType;
        private final Class<?> targetType;
    }
}
```

---

## 5. 适配器注册中心

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/common/adapter/AdapterRegistry.java
package com.patra.starter.provenance.common.adapter;

import com.patra.common.model.DataType;
import com.patra.common.model.CanonicalData;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 适配器注册中心
 */
@Component
@Slf4j
public class AdapterRegistry {

    private final Map<String, DataSourceAdapter<?>> adaptersByProvenance = new ConcurrentHashMap<>();
    private final Map<RegistryKey, DataSourceAdapter<?>> adaptersByType = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void registerAdapters() {
        Map<String, DataSourceAdapter> adapterBeans =
            applicationContext.getBeansOfType(DataSourceAdapter.class);

        for (Map.Entry<String, DataSourceAdapter> entry : adapterBeans.entrySet()) {
            DataSourceAdapter<?> adapter = entry.getValue();
            String provenanceCode = adapter.getProvenanceCode();

            // 注册到主表
            adaptersByProvenance.put(provenanceCode, adapter);

            // 注册到类型表
            AdapterCapability capability = adapter.getCapabilities();
            if (capability != null && capability.getSupportedDataTypes() != null) {
                for (DataType dataType : capability.getSupportedDataTypes()) {
                    RegistryKey key = new RegistryKey(provenanceCode, dataType);
                    adaptersByType.put(key, adapter);
                }
            }

            log.info("注册数据源适配器: {} ({})", entry.getKey(), provenanceCode);
        }
    }

    /**
     * 根据数据源代码获取适配器
     */
    public DataSourceAdapter<?> getAdapter(String provenanceCode) {
        DataSourceAdapter<?> adapter = adaptersByProvenance.get(provenanceCode);
        if (adapter == null) {
            throw new IllegalArgumentException("未找到数据源适配器: " + provenanceCode);
        }
        return adapter;
    }

    /**
     * 根据数据源和数据类型获取适配器
     */
    @SuppressWarnings("unchecked")
    public <T extends CanonicalData> DataSourceAdapter<T> getAdapter(
            String provenanceCode,
            Class<T> dataTypeClass) {

        DataType dataType = extractDataType(dataTypeClass);
        RegistryKey key = new RegistryKey(provenanceCode, dataType);

        DataSourceAdapter<?> adapter = adaptersByType.get(key);
        if (adapter == null) {
            throw new IllegalArgumentException(
                String.format("未找到适配器: %s -> %s", provenanceCode, dataType)
            );
        }

        return (DataSourceAdapter<T>) adapter;
    }

    private DataType extractDataType(Class<?> dataTypeClass) {
        // 伪代码：从 Class 提取对应的 DataType
        // 实际实现可以通过反射或映射表
        return DataType.LITERATURE; // 示例
    }

    @Data
    private static class RegistryKey {
        private final String provenanceCode;
        private final DataType dataType;
    }
}
```

---

## 6. 具体适配器实现（伪代码）

### 6.1 PubMed 多类型适配器

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/pubmed/PubMedAdapter.java
package com.patra.starter.provenance.pubmed;

/**
 * PubMed 数据源适配器
 *
 * <p>支持文献、作者、引用多种数据类型</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PubMedAdapter implements DataSourceAdapter<CanonicalData> {

    private final PubMedClient pubMedClient;
    private final StrategyRegistry strategyRegistry;

    @Override
    public String getProvenanceCode() {
        return "pubmed";
    }

    @Override
    public AdapterResult<CanonicalData> fetchData(AdapterRequest request) {
        // 确定请求的数据类型
        DataType requestedType = request.requestedDataType();
        if (requestedType == null) {
            requestedType = DataType.LITERATURE;
        }

        // 根据类型分发到不同的处理方法
        return switch (requestedType) {
            case LITERATURE -> fetchLiteratures(request);
            case AUTHOR -> fetchAuthors(request);
            case CITATION -> fetchCitations(request);
            default -> AdapterResult.nonRetriableFailure(
                "不支持的数据类型: " + requestedType);
        };
    }

    /**
     * 获取文献数据
     */
    private AdapterResult<CanonicalData> fetchLiteratures(AdapterRequest request) {
        try {
            // 1. 构建 PubMed 查询
            PubMedSearchQuery query = buildSearchQuery(request);

            // 2. 调用 PubMed API
            PubMedSearchResponse response = pubMedClient.search(query);

            // 3. 获取转换策略
            DataTransformStrategy<PubMedArticle, CanonicalLiterature> strategy =
                strategyRegistry.getStrategy(
                    PubMedArticle.class,
                    CanonicalLiterature.class
                );

            // 4. 批量转换
            TransformResult<CanonicalLiterature> transformResult =
                strategy.batchTransform(response.getArticles());

            // 5. 处理转换错误
            if (transformResult.hasErrors()) {
                log.warn("部分文献转换失败: {}/{} 成功",
                    transformResult.getSuccessItems().size(),
                    transformResult.getSuccessItems().size() + transformResult.getErrors().size());
            }

            // 6. 构建返回结果
            List<CanonicalData> data = new ArrayList<>(transformResult.getSuccessItems());
            return AdapterResult.success(data, response.getNextCursor());

        } catch (PubMedApiException e) {
            log.error("PubMed API 调用失败", e);
            return AdapterResult.retriableFailure(e.getMessage());
        } catch (Exception e) {
            log.error("文献获取失败", e);
            return AdapterResult.nonRetriableFailure(e.getMessage());
        }
    }

    /**
     * 获取作者数据
     */
    private AdapterResult<CanonicalData> fetchAuthors(AdapterRequest request) {
        try {
            // 伪代码：调用 PubMed 作者 API
            List<PubMedAuthor> authors = pubMedClient.searchAuthors(/*...*/);

            // 转换为规范作者模型
            DataTransformStrategy<PubMedAuthor, CanonicalAuthor> strategy =
                strategyRegistry.getStrategy(PubMedAuthor.class, CanonicalAuthor.class);

            TransformResult<CanonicalAuthor> result = strategy.batchTransform(authors);

            List<CanonicalData> data = new ArrayList<>(result.getSuccessItems());
            return AdapterResult.success(data, null);

        } catch (Exception e) {
            return AdapterResult.retriableFailure(e.getMessage());
        }
    }

    /**
     * 获取引用数据
     */
    private AdapterResult<CanonicalData> fetchCitations(AdapterRequest request) {
        try {
            // 伪代码：调用 PubMed 引用 API
            List<PubMedCitation> citations = pubMedClient.fetchCitations(/*...*/);

            // 转换为规范引用模型
            DataTransformStrategy<PubMedCitation, CanonicalCitation> strategy =
                strategyRegistry.getStrategy(PubMedCitation.class, CanonicalCitation.class);

            TransformResult<CanonicalCitation> result = strategy.batchTransform(citations);

            List<CanonicalData> data = new ArrayList<>(result.getSuccessItems());
            return AdapterResult.success(data, null);

        } catch (Exception e) {
            return AdapterResult.retriableFailure(e.getMessage());
        }
    }

    @Override
    public AdapterCapability getCapabilities() {
        return AdapterCapability.builder()
            .dataSource("PubMed")
            .supportedDataTypes(Set.of(
                DataType.LITERATURE,
                DataType.AUTHOR,
                DataType.CITATION
            ))
            .build();
    }

    @Override
    public Class<CanonicalData> getDataTypeClass() {
        return CanonicalData.class;
    }

    /**
     * 构建 PubMed 查询对象
     */
    private PubMedSearchQuery buildSearchQuery(AdapterRequest request) {
        // 伪代码：从 request 提取参数构建查询
        BatchExecutionParams params = request.executionParams();
        return PubMedSearchQuery.builder()
            .term(params.query())
            .pageSize(params.params().get("pageSize"))
            .cursor(request.metadata().cursorToken())
            .build();
    }
}
```

### 6.2 转换策略实现示例

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/pubmed/strategy/PubMedToLiteratureStrategy.java
package com.patra.starter.provenance.pubmed.strategy;

/**
 * PubMed 文献转换策略
 */
@Component
@Slf4j
public class PubMedToLiteratureStrategy
        implements DataTransformStrategy<PubMedArticle, CanonicalLiterature> {

    @Override
    public CanonicalLiterature transform(PubMedArticle source) {
        return CanonicalLiterature.builder()
            .id(source.getPmid())
            .title(extractTitle(source))
            .abstractText(extractAbstract(source))
            .authors(transformAuthors(source.getAuthorList()))
            .journal(transformJournal(source.getJournal()))
            .publicationDate(parsePublicationDate(source.getPubDate()))
            .keywords(source.getKeywordList())
            .identifiers(buildIdentifiers(source))
            .provenance("pubmed")
            .createdAt(Instant.now())
            .build();
    }

    private String extractTitle(PubMedArticle article) {
        // 伪代码：提取标题
        return article.getArticleTitle();
    }

    private String extractAbstract(PubMedArticle article) {
        // 伪代码：提取摘要，可能需要组合多个部分
        if (article.getAbstract() == null) {
            return null;
        }
        return article.getAbstract().getText();
    }

    private List<CanonicalAuthor> transformAuthors(List<PubMedAuthor> pubMedAuthors) {
        if (pubMedAuthors == null) {
            return List.of();
        }

        return pubMedAuthors.stream()
            .map(author -> CanonicalAuthor.builder()
                .firstName(author.getForeName())
                .lastName(author.getLastName())
                .fullName(author.getFullName())
                .affiliations(List.of(author.getAffiliation()))
                .build())
            .collect(Collectors.toList());
    }

    private CanonicalJournal transformJournal(PubMedJournal pubMedJournal) {
        if (pubMedJournal == null) {
            return null;
        }

        return CanonicalJournal.builder()
            .title(pubMedJournal.getTitle())
            .abbreviation(pubMedJournal.getIsoAbbreviation())
            .issn(pubMedJournal.getIssn())
            .build();
    }

    private LocalDate parsePublicationDate(String pubDate) {
        // 伪代码：解析日期
        try {
            return LocalDate.parse(pubDate);
        } catch (Exception e) {
            log.warn("日期解析失败: {}", pubDate);
            return null;
        }
    }

    private Map<String, String> buildIdentifiers(PubMedArticle article) {
        Map<String, String> identifiers = new HashMap<>();
        identifiers.put("pmid", article.getPmid());
        if (article.getDoi() != null) {
            identifiers.put("doi", article.getDoi());
        }
        if (article.getPmcId() != null) {
            identifiers.put("pmc", article.getPmcId());
        }
        return identifiers;
    }

    @Override
    public Class<PubMedArticle> getSourceType() {
        return PubMedArticle.class;
    }

    @Override
    public Class<CanonicalLiterature> getTargetType() {
        return CanonicalLiterature.class;
    }
}
```

### 6.3 外部客户端（防腐层）

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/pubmed/client/PubMedClient.java
package com.patra.starter.provenance.pubmed.client;

/**
 * PubMed HTTP 客户端
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PubMedClient {

    private final RestTemplate restTemplate;
    private final PubMedProperties properties;

    /**
     * 搜索文献
     */
    public PubMedSearchResponse search(PubMedSearchQuery query) {
        // 伪代码：构建 URL
        String url = buildSearchUrl(query);

        // 调用 API
        ResponseEntity<String> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            buildHeaders(),
            String.class
        );

        // 解析响应
        return parseSearchResponse(response.getBody());
    }

    /**
     * 批量获取文章详情
     */
    public List<PubMedArticle> fetchArticles(List<String> pmids) {
        // 伪代码：批量获取
        String url = buildFetchUrl(pmids);

        ResponseEntity<String> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            buildHeaders(),
            String.class
        );

        return parseFetchResponse(response.getBody());
    }

    private String buildSearchUrl(PubMedSearchQuery query) {
        // 伪代码：构建 URL
        return String.format("%s/esearch.fcgi?term=%s&retmax=%d",
            properties.getBaseUrl(),
            query.getTerm(),
            query.getPageSize());
    }

    private HttpEntity<?> buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("API-Key", properties.getApiKey());
        return new HttpEntity<>(headers);
    }

    private PubMedSearchResponse parseSearchResponse(String xml) {
        // 伪代码：解析 XML 响应
        return new PubMedSearchResponse(/* 解析结果 */);
    }

    private List<PubMedArticle> parseFetchResponse(String xml) {
        // 伪代码：解析 XML 响应
        return List.of(/* 解析结果 */);
    }
}

/**
 * PubMed 文章模型（外部模型）
 */
@Data
public class PubMedArticle {
    private String pmid;
    private String doi;
    private String pmcId;
    private String articleTitle;
    private PubMedAbstract abstract;
    private List<PubMedAuthor> authorList;
    private PubMedJournal journal;
    private String pubDate;
    private List<String> keywordList;
}
```

---

## 7. GenericBatchExecutor 使用示例

```java
// patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/coordination/GenericBatchExecutor.java

public BatchResult execute(ExecutionContext context, Batch batch) {
    // 1. 获取适配器（默认获取文献适配器）
    DataSourceAdapter<?> adapter = adapterRegistry.getAdapter(context.provenanceCode());

    // 2. 构建请求
    AdapterRequest request = AdapterRequest.builder()
        .operationCode(context.operationCode())
        .config(runtimeConfig)
        .executionParams(executionParams)
        .metadata(metadata)
        .requestedDataType(DataType.LITERATURE)  // 明确指定数据类型
        .build();

    // 3. 调用适配器获取数据
    AdapterResult<?> adapterResult = adapter.fetchData(request);

    // 4. 处理结果
    if (adapterResult.success()) {
        // 发布数据
        publishData(context, batch, adapterResult.data());
        return BatchResult.success(/*...*/);
    } else {
        // 处理失败
        return handleFailure(/*...*/);
    }
}
```

---

## 8. 配置管理

### 8.1 应用配置

```yaml
# application.yml
patra:
  provenance:
    datasources:
      # PubMed 配置
      pubmed:
        enabled: true
        base-url: https://eutils.ncbi.nlm.nih.gov/entrez/eutils
        api-key: ${PUBMED_API_KEY}
        timeout: 30s
        retry:
          max-attempts: 3
          initial-delay: 1s
          multiplier: 2
        rate-limit:
          requests-per-second: 10
          daily-quota: 100000

      # EPMC 配置
      epmc:
        enabled: true
        base-url: https://www.ebi.ac.uk/europepmc/webservices/rest
        timeout: 20s
        rate-limit:
          requests-per-second: 15

      # ArXiv 配置
      arxiv:
        enabled: true
        base-url: http://export.arxiv.org/api
        timeout: 30s
```

### 8.2 Spring 配置类

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/config/DataSourceConfiguration.java
package com.patra.starter.provenance.config;

/**
 * 数据源配置类
 */
@Configuration
@EnableConfigurationProperties(ProvenanceProperties.class)
public class DataSourceConfiguration {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "patra.provenance.datasources.pubmed", name = "enabled", havingValue = "true")
    public PubMedClient pubMedClient(RestTemplate restTemplate, ProvenanceProperties properties) {
        PubMedProperties pubMedProps = properties.getDatasources().get("pubmed");
        return new PubMedClient(restTemplate, pubMedProps);
    }

    @Bean
    @ConditionalOnProperty(prefix = "patra.provenance.datasources.pubmed", name = "enabled", havingValue = "true")
    public PubMedAdapter pubMedAdapter(PubMedClient client, StrategyRegistry registry) {
        return new PubMedAdapter(client, registry);
    }
}
```

---

## 9. 测试策略

### 9.1 单元测试

```java
// patra-starter-provenance/src/test/java/com/patra/starter/provenance/pubmed/PubMedAdapterTest.java

@ExtendWith(MockitoExtension.class)
class PubMedAdapterTest {

    @Mock
    private PubMedClient pubMedClient;

    @Mock
    private StrategyRegistry strategyRegistry;

    @InjectMocks
    private PubMedAdapter adapter;

    @Test
    void shouldFetchLiteratures() {
        // Given
        AdapterRequest request = createTestRequest();
        PubMedSearchResponse mockResponse = createMockResponse();
        DataTransformStrategy<PubMedArticle, CanonicalLiterature> mockStrategy = createMockStrategy();

        when(pubMedClient.search(any())).thenReturn(mockResponse);
        when(strategyRegistry.getStrategy(any(), any())).thenReturn(mockStrategy);

        // When
        AdapterResult<CanonicalData> result = adapter.fetchData(request);

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.data()).hasSize(10);
        verify(pubMedClient).search(any());
    }

    @Test
    void shouldHandleApiFailure() {
        // Given
        AdapterRequest request = createTestRequest();
        when(pubMedClient.search(any())).thenThrow(new PubMedApiException("API Error"));

        // When
        AdapterResult<CanonicalData> result = adapter.fetchData(request);

        // Then
        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo(ErrorType.RETRIABLE);
    }
}
```

### 9.2 集成测试

```java
// patra-starter-provenance/src/test/java/com/patra/starter/provenance/pubmed/PubMedAdapterIntegrationTest.java

@SpringBootTest
@ActiveProfiles("test")
class PubMedAdapterIntegrationTest {

    @Autowired
    private PubMedAdapter adapter;

    @Autowired
    private AdapterRegistry registry;

    @Test
    void shouldRegisterAdapter() {
        // When
        DataSourceAdapter<?> registered = registry.getAdapter("pubmed");

        // Then
        assertThat(registered).isNotNull();
        assertThat(registered.getProvenanceCode()).isEqualTo("pubmed");
    }

    @Test
    void shouldFetchRealData() {
        // Given
        AdapterRequest request = AdapterRequest.builder()
            .operationCode("HARVEST")
            .config(createTestConfig())
            .executionParams(createTestParams())
            .metadata(new BatchMetadata(1, null))
            .requestedDataType(DataType.LITERATURE)
            .build();

        // When
        AdapterResult<CanonicalData> result = adapter.fetchData(request);

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotEmpty();
    }
}
```

---

## 10. 监控和健康检查

### 10.1 健康检查

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/health/DataSourceHealthIndicator.java
package com.patra.starter.provenance.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 数据源健康检查
 */
@Component
@RequiredArgsConstructor
public class DataSourceHealthIndicator implements HealthIndicator {

    private final AdapterRegistry registry;

    @Override
    public Health health() {
        Map<String, String> details = new HashMap<>();
        boolean allHealthy = true;

        for (DataSourceAdapter<?> adapter : registry.getAllAdapters()) {
            String provenance = adapter.getProvenanceCode();
            try {
                // 简单的连通性测试
                testAdapter(adapter);
                details.put(provenance, "UP");
            } catch (Exception e) {
                details.put(provenance, "DOWN: " + e.getMessage());
                allHealthy = false;
            }
        }

        return allHealthy
            ? Health.up().withDetails(details).build()
            : Health.down().withDetails(details).build();
    }

    private void testAdapter(DataSourceAdapter<?> adapter) {
        // 伪代码：测试适配器连通性
        // 可以调用一个轻量级的测试请求
    }
}
```

### 10.2 性能指标

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/metrics/AdapterMetrics.java
package com.patra.starter.provenance.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 适配器性能指标
 */
@Component
@RequiredArgsConstructor
public class AdapterMetrics {

    private final MeterRegistry meterRegistry;

    public void recordFetchTime(String provenance, String dataType, long durationMs) {
        Timer.builder("adapter.fetch.time")
            .tag("provenance", provenance)
            .tag("dataType", dataType)
            .register(meterRegistry)
            .record(Duration.ofMillis(durationMs));
    }

    public void recordFetchCount(String provenance, String dataType, boolean success) {
        meterRegistry.counter("adapter.fetch.count",
            "provenance", provenance,
            "dataType", dataType,
            "status", success ? "success" : "failure"
        ).increment();
    }

    public void recordTransformError(String provenance, String dataType) {
        meterRegistry.counter("adapter.transform.error",
            "provenance", provenance,
            "dataType", dataType
        ).increment();
    }
}
```

---

## 11. 架构优势总结

### 11.1 核心优势

1. **类型安全**：泛型化设计提供编译时类型检查
2. **清晰分层**：严格遵循六边形架构原则
3. **易于扩展**：新数据源和数据类型可快速接入
4. **防腐隔离**：外部模型变化不影响领域模型
5. **策略可插拔**：数据转换策略独立可配置

### 11.2 扩展点

1. **新数据源接入**：实现 `DataSourceAdapter<T>` 接口
2. **新数据类型**：定义 `CanonicalData` 实现类
3. **转换策略**：实现 `DataTransformStrategy<S, T>` 接口
4. **能力扩展**：通过 `AdapterCapability` 声明新能力

### 11.3 设计原则体现

- **单一职责**：每个组件职责明确
- **开闭原则**：对扩展开放，对修改关闭
- **依赖倒置**：依赖抽象而非具体实现
- **接口隔离**：细粒度的接口定义
- **里氏替换**：所有适配器可互相替换