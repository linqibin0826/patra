# 数据源适配器架构设计方案

**文档版本**: 2.0.0
**创建日期**: 2025-11-11
**更新日期**: 2025-11-11
**作者**: Patra 架构团队
**状态**: 实施中

---

## 1. 架构概览

### 1.1 调用链路

```
GenericBatchExecutor (Application Layer)
         ↓
    ProviderRegistry
         ↓
    DataSourceProvider<T>
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
│  │   - 通过 ProviderRegistry 获取提供者                     │  │
│  │   - 处理错误和部分成功                                   │  │
│  │   - 发布规范化数据                                       │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────┬────────────────────────────────────┘
                           ↓
┌────────────────────────────────────────────────────────────────┐
│                    Infrastructure Layer                         │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ ProviderRegistry                                        │  │
│  │   - 管理所有数据源提供者                                 │  │
│  │   - 按数据源和数据类型查找提供者                          │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ DataSourceProvider<T> 实现                              │  │
│  │   - PubMedProvider                                      │  │
│  │   - EPMCProvider                                        │  │
│  │   - ArXivProvider                                       │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ DataTransformStrategy<S, T> 实现                        │  │
│  │   - PubMedToLiteratureStrategy                          │  │
│  │   - EPMCToJournalStrategy                               │  │
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
    private List<AuthorInfo> authors;  // 内嵌的作者基本信息（非独立数据类型）
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
 * 作者基本信息（内嵌在文献中，非独立数据类型）
 */
@Data
@Builder
public class AuthorInfo {
    private String firstName;
    private String lastName;
    private String fullName;
    private String orcid;
    private String email;
    private List<String> affiliations;
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

### 3.1 数据源提供者接口（Framework 层）

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/common/provider/DataSourceProvider.java
package com.patra.starter.provenance.common.provider;

import com.patra.common.model.CanonicalData;

/**
 * 数据源提供者泛型接口（Framework 层）
 *
 * <p>由 Infrastructure 层桥接到 Domain 层端口</p>
 *
 * @param <T> 返回的规范数据类型
 */
public interface DataSourceProvider<T extends CanonicalData> {

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
    ProviderResult<T> fetchData(ProviderRequest request);

    /**
     * 获取提供者支持的能力
     *
     * @return 能力描述对象
     */
    ProviderCapability getCapabilities();

    /**
     * 获取支持的数据类型类
     *
     * @return 数据类型的 Class 对象
     */
    Class<T> getDataTypeClass();
}
```

### 3.2 增强的 ProviderResult

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/common/provider/ProviderResult.java
package com.patra.starter.provenance.common.provider;

import com.patra.common.model.CanonicalData;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 泛型化的提供者执行结果
 *
 * @param <T> 数据载荷类型
 */
public record ProviderResult<T extends CanonicalData>(
    boolean success,
    List<T> data,
    String nextCursorToken,
    String errorMessage,
    int fetchedCount,
    ErrorType errorType,
    Map<String, Object> metadata
) {

    public ProviderResult {
        data = data == null ? List.of() : List.copyOf(data);
        fetchedCount = Math.max(fetchedCount, data.size());
        errorType = Objects.requireNonNullElse(errorType, ErrorType.NONE);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * 创建成功结果
     */
    public static <T extends CanonicalData> ProviderResult<T> success(
            List<T> data,
            String nextCursorToken) {
        return new ProviderResult<>(
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
     * 创建标识为可重试错误的失败结果
     * (注: 是否实际重试由上层调度器决定)
     */
    public static <T extends CanonicalData> ProviderResult<T> retriableFailure(
            String errorMessage) {
        return new ProviderResult<>(
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
     * 创建标识为不可重试错误的失败结果
     * (注: 表示错误是永久性的，重试无法解决)
     */
    public static <T extends CanonicalData> ProviderResult<T> nonRetriableFailure(
            String errorMessage) {
        return new ProviderResult<>(
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
    public static <T extends CanonicalData> ProviderResult<T> partialSuccess(
            List<T> data,
            String nextCursorToken,
            String warningMessage,
            int totalAttempted) {
        return new ProviderResult<>(
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

### 3.3 增强的 ProviderRequest

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/common/provider/ProviderRequest.java
package com.patra.starter.provenance.common.provider;

/**
 * 提供者请求对象
 */
public record ProviderRequest(
    String operationCode,
    ProvenanceConfig config,
    BatchExecutionParams executionParams,
    BatchMetadata metadata,
    DataType requestedDataType
) {

    public ProviderRequest {
        if (operationCode == null || operationCode.isBlank()) {
            throw new IllegalArgumentException("operationCode 不能为空");
        }
        if (executionParams == null) {
            throw new IllegalArgumentException("executionParams 不能为空");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata 不能为空");
        }
        // requestedDataType 可以为 null，提供者实现会使用默认类型
    }
}
```

### 3.4 能力声明

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/common/provider/ProviderCapability.java
package com.patra.starter.provenance.common.provider;

import com.patra.common.model.DataType;
import java.util.Set;

/**
 * 数据源能力声明
 */
@Data
@Builder
public class ProviderCapability {

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

## 5. 提供者注册中心

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/common/provider/ProviderRegistry.java
package com.patra.starter.provenance.common.provider;

import com.patra.common.model.DataType;
import com.patra.common.model.CanonicalData;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源提供者注册中心（Framework 层）
 */
@Component
@Slf4j
public class ProviderRegistry {

    private final Map<String, DataSourceProvider<?>> providersByProvenance = new ConcurrentHashMap<>();
    private final Map<RegistryKey, DataSourceProvider<?>> providersByType = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void registerProviders() {
        Map<String, DataSourceProvider> providerBeans =
            applicationContext.getBeansOfType(DataSourceProvider.class);

        for (Map.Entry<String, DataSourceProvider> entry : providerBeans.entrySet()) {
            DataSourceProvider<?> provider = entry.getValue();
            String provenanceCode = provider.getProvenanceCode();

            // 注册到主表
            providersByProvenance.put(provenanceCode, provider);

            // 注册到类型表
            ProviderCapability capability = provider.getCapabilities();
            if (capability != null && capability.getSupportedDataTypes() != null) {
                for (DataType dataType : capability.getSupportedDataTypes()) {
                    RegistryKey key = new RegistryKey(provenanceCode, dataType);
                    providersByType.put(key, provider);
                }
            }

            log.info("注册数据源提供者: {} ({})", entry.getKey(), provenanceCode);
        }
    }

    /**
     * 根据数据源代码获取提供者
     */
    public DataSourceProvider<?> getProvider(String provenanceCode) {
        DataSourceProvider<?> provider = providersByProvenance.get(provenanceCode);
        if (provider == null) {
            throw new IllegalArgumentException("未找到数据源提供者: " + provenanceCode);
        }
        return provider;
    }

    /**
     * 根据数据源和数据类型获取提供者
     */
    @SuppressWarnings("unchecked")
    public <T extends CanonicalData> DataSourceProvider<T> getProvider(
            String provenanceCode,
            Class<T> dataTypeClass) {

        DataType dataType = extractDataType(dataTypeClass);
        RegistryKey key = new RegistryKey(provenanceCode, dataType);

        DataSourceProvider<?> provider = providersByType.get(key);
        if (provider == null) {
            throw new IllegalArgumentException(
                String.format("未找到提供者: %s -> %s", provenanceCode, dataType)
            );
        }

        return (DataSourceProvider<T>) provider;
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

## 6. 具体提供者实现（伪代码）

### 6.1 PubMed 多类型提供者

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/pubmed/PubMedProvider.java
package com.patra.starter.provenance.pubmed;

/**
 * PubMed 数据源提供者（Framework 层实现）
 *
 * <p>支持文献、作者、引用多种数据类型</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PubMedProvider implements DataSourceProvider<CanonicalData> {

    private final PubMedClient pubMedClient;
    private final StrategyRegistry strategyRegistry;

    @Override
    public String getProvenanceCode() {
        return "pubmed";
    }

    @Override
    public ProviderResult<CanonicalData> fetchData(ProviderRequest request) {
        // 确定请求的数据类型
        DataType requestedType = request.requestedDataType();
        if (requestedType == null) {
            requestedType = DataType.LITERATURE;
        }

        // 根据类型分发到不同的处理方法
        return switch (requestedType) {
            case LITERATURE -> fetchLiteratures(request);
            case CITATION -> fetchCitations(request);
            default -> ProviderResult.nonRetriableFailure(
                "不支持的数据类型: " + requestedType);
        };
    }

    /**
     * 获取文献数据
     */
    private ProviderResult<CanonicalData> fetchLiteratures(ProviderRequest request) {
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
            return ProviderResult.success(data, response.getNextCursor());

        } catch (PubMedApiException e) {
            log.error("PubMed API 调用失败", e);
            return ProviderResult.retriableFailure(e.getMessage());
        } catch (Exception e) {
            log.error("文献获取失败", e);
            return ProviderResult.nonRetriableFailure(e.getMessage());
        }
    }

    /**
     * 获取引用数据
     */
    private ProviderResult<CanonicalData> fetchCitations(ProviderRequest request) {
        try {
            // 伪代码：调用 PubMed 引用 API
            List<PubMedCitation> citations = pubMedClient.fetchCitations(/*...*/);

            // 转换为规范引用模型
            DataTransformStrategy<PubMedCitation, CanonicalCitation> strategy =
                strategyRegistry.getStrategy(PubMedCitation.class, CanonicalCitation.class);

            TransformResult<CanonicalCitation> result = strategy.batchTransform(citations);

            List<CanonicalData> data = new ArrayList<>(result.getSuccessItems());
            return ProviderResult.success(data, null);

        } catch (Exception e) {
            return ProviderResult.retriableFailure(e.getMessage());
        }
    }

    @Override
    public ProviderCapability getCapabilities() {
        return ProviderCapability.builder()
            .dataSource("PubMed")
            .supportedDataTypes(Set.of(
                DataType.LITERATURE,
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
    private PubMedSearchQuery buildSearchQuery(ProviderRequest request) {
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

    private List<AuthorInfo> transformAuthors(List<PubMedAuthor> pubMedAuthors) {
        if (pubMedAuthors == null) {
            return List.of();
        }

        return pubMedAuthors.stream()
            .map(author -> AuthorInfo.builder()
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
    // 1. 获取提供者（默认获取文献提供者）
    DataSourceProvider<?> provider = providerRegistry.getProvider(context.provenanceCode());

    // 2. 构建请求
    ProviderRequest request = ProviderRequest.builder()
        .operationCode(context.operationCode())
        .config(runtimeConfig)
        .executionParams(executionParams)
        .metadata(metadata)
        .requestedDataType(DataType.LITERATURE)  // 明确指定数据类型
        .build();

    // 3. 调用提供者获取数据
    ProviderResult<?> providerResult = provider.fetchData(request);

    // 4. 处理结果
    if (providerResult.success()) {
        // 发布数据
        publishData(context, batch, providerResult.data());
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
    public PubMedProvider pubMedProvider(PubMedClient client, StrategyRegistry registry) {
        return new PubMedProvider(client, registry);
    }
}
```

---

## 9. 测试策略

### 9.1 单元测试

```java
// patra-starter-provenance/src/test/java/com/patra/starter/provenance/pubmed/PubMedProviderTest.java

@ExtendWith(MockitoExtension.class)
class PubMedProviderTest {

    @Mock
    private PubMedClient pubMedClient;

    @Mock
    private StrategyRegistry strategyRegistry;

    @InjectMocks
    private PubMedProvider provider;

    @Test
    void shouldFetchLiteratures() {
        // Given
        ProviderRequest request = createTestRequest();
        PubMedSearchResponse mockResponse = createMockResponse();
        DataTransformStrategy<PubMedArticle, CanonicalLiterature> mockStrategy = createMockStrategy();

        when(pubMedClient.search(any())).thenReturn(mockResponse);
        when(strategyRegistry.getStrategy(any(), any())).thenReturn(mockStrategy);

        // When
        ProviderResult<CanonicalData> result = provider.fetchData(request);

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.data()).hasSize(10);
        verify(pubMedClient).search(any());
    }

    @Test
    void shouldHandleApiFailure() {
        // Given
        ProviderRequest request = createTestRequest();
        when(pubMedClient.search(any())).thenThrow(new PubMedApiException("API Error"));

        // When
        ProviderResult<CanonicalData> result = provider.fetchData(request);

        // Then
        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo(ErrorType.RETRIABLE);
    }
}
```

### 9.2 集成测试

```java
// patra-starter-provenance/src/test/java/com/patra/starter/provenance/pubmed/PubMedProviderIntegrationTest.java

@SpringBootTest
@ActiveProfiles("test")
class PubMedProviderIntegrationTest {

    @Autowired
    private PubMedProvider provider;

    @Autowired
    private ProviderRegistry registry;

    @Test
    void shouldRegisterProvider() {
        // When
        DataSourceProvider<?> registered = registry.getProvider("pubmed");

        // Then
        assertThat(registered).isNotNull();
        assertThat(registered.getProvenanceCode()).isEqualTo("pubmed");
    }

    @Test
    void shouldFetchRealData() {
        // Given
        ProviderRequest request = ProviderRequest.builder()
            .operationCode("HARVEST")
            .config(createTestConfig())
            .executionParams(createTestParams())
            .metadata(new BatchMetadata(1, null))
            .requestedDataType(DataType.LITERATURE)
            .build();

        // When
        ProviderResult<CanonicalData> result = provider.fetchData(request);

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

    private final ProviderRegistry registry;

    @Override
    public Health health() {
        Map<String, String> details = new HashMap<>();
        boolean allHealthy = true;

        for (DataSourceProvider<?> provider : registry.getAllProviders()) {
            String provenance = provider.getProvenanceCode();
            try {
                // 简单的连通性测试
                testProvider(provider);
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

    private void testProvider(DataSourceProvider<?> provider) {
        // 伪代码：测试提供者连通性
        // 可以调用一个轻量级的测试请求
    }
}
```

### 10.2 性能指标

```java
// patra-starter-provenance/src/main/java/com/patra/starter/provenance/metrics/ProviderMetrics.java
package com.patra.starter.provenance.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 提供者性能指标
 */
@Component
@RequiredArgsConstructor
public class ProviderMetrics {

    private final MeterRegistry meterRegistry;

    public void recordFetchTime(String provenance, String dataType, long durationMs) {
        Timer.builder("provider.fetch.time")
            .tag("provenance", provenance)
            .tag("dataType", dataType)
            .register(meterRegistry)
            .record(Duration.ofMillis(durationMs));
    }

    public void recordFetchCount(String provenance, String dataType, boolean success) {
        meterRegistry.counter("provider.fetch.count",
            "provenance", provenance,
            "dataType", dataType,
            "status", success ? "success" : "failure"
        ).increment();
    }

    public void recordTransformError(String provenance, String dataType) {
        meterRegistry.counter("provider.transform.error",
            "provenance", provenance,
            "dataType", dataType
        ).increment();
    }
}
```

---

## 11. 六边形架构实现

### 11.1 架构层次

本设计严格遵循六边形架构（Ports & Adapters）原则，确保依赖方向正确：

#### 层次关系
```
┌────────────────────────────────────────────────────┐
│ Domain Layer (patra-ingest-domain)                │
│ - DataSourcePort (输出端口接口)                    │
│ - DataFetchResult (领域值对象)                     │
│ 职责: 定义业务契约，不依赖任何技术实现            │
└────────────────────────────────────────────────────┘
                      ▲
                      │ depends on
                      │
┌────────────────────────────────────────────────────┐
│ Application Layer (patra-ingest-app)              │
│ - GenericBatchExecutor (用例编排器)               │
│ 职责: 业务流程编排，不关注技术细节                │
└────────────────────────────────────────────────────┘
                      ▲
                      │ implements
                      │
┌────────────────────────────────────────────────────┐
│ Infrastructure Layer (patra-ingest-infra)         │
│ - DataSourceAdapter (端口适配器)                  │
│ 职责: 桥接领域层和框架层，处理技术转换            │
│   - 配置快照转换                                   │
│   - 请求/响应对象转换                              │
│   - 错误类型映射                                   │
└────────────────────────────────────────────────────┘
                      │ uses
                      ↓
┌────────────────────────────────────────────────────┐
│ Framework Layer (patra-starter-provenance)        │
│ - DataSourceProvider (框架接口)                   │
│ - ProviderRegistry (提供者注册表)                  │
│ 职责: 提供技术支撑和统一规范                       │
└────────────────────────────────────────────────────┘
                      ▲
                      │ implements
                      │
┌────────────────────────────────────────────────────┐
│ Infrastructure Layer (patra-ingest-infra)         │
│ - PubmedDataSourceProvider                        │
│ - EpmcDataSourceProvider                          │
│ 职责: 具体数据源的实现                             │
└────────────────────────────────────────────────────┘
```

### 11.2 关键设计决策

#### 命名语义说明

本架构采用三层接口设计，每层都有清晰的命名语义：

| 层级 | 接口名称 | 命名语义 | 职责 |
|------|---------|---------|------|
| **Domain 层** | `DataSourcePort` | 业务端口 | 定义业务契约，表示"获取数据的能力" |
| **Infrastructure 层** | `DataSourceAdapter` | 适配器 | 适配 Domain Port 到 Framework Provider |
| **Framework 层** | `DataSourceProvider` | 技术提供者 | 提供技术实现，表示"数据获取的技术规范" |

**核心理念**：
- **Port**（Domain 层）：业务语言，定义"需要什么能力"
- **Adapter**（Infrastructure 层）：桥接层，处理"业务契约 → 技术规范"的转换
- **Provider**（Framework 层）：技术语言，定义"如何提供能力"

#### 为什么需要三层接口？

**DataSourcePort (领域层)** vs **DataSourceAdapter (基础设施层)** vs **DataSourceProvider (框架层)**

| 对比项 | DataSourcePort (领域层) | DataSourceAdapter (基础设施层) | DataSourceProvider (框架层) |
|--------|------------------------|-------------------------------|---------------------------|
| 定义位置 | patra-ingest-domain | patra-ingest-infra | patra-starter-provenance |
| 职责 | 定义业务契约 | 桥接业务和技术 | 定义技术规范 |
| 依赖者 | Application 层 | — | DataSourceAdapter |
| 方法签名 | `fetchData(ExecutionContext, Batch)` | 同 Port | `fetchData(ProviderRequest)` |
| 返回类型 | DataFetchResult (领域对象) | 同 Port | ProviderResult (框架对象) |
| 变更影响 | 影响业务流程 | 影响转换逻辑 | 影响技术实现 |

**架构优势**:
1. **依赖倒置**: Application 层不依赖框架或技术实现
2. **关注点分离**: 业务逻辑、转换逻辑、技术细节完全解耦
3. **可测试性**: Application 层可以轻松 Mock 端口
4. **灵活性**: 可以替换底层技术实现而不影响业务层

#### DataSourceAdapter 的职责（Infrastructure 层）

DataSourceAdapter 作为 Infrastructure 层的桥接适配器，负责：

1. **路由**: 使用 ProviderRegistry 根据 provenanceCode 获取具体提供者
2. **类型转换**:
   - ExecutionContext + Batch → ProviderRequest
   - ProviderResult → DataFetchResult
3. **配置转换**: ProvenanceConfigSnapshot → ProvenanceConfig
4. **错误映射**: Framework ErrorType → Domain ErrorType

### 11.3 实现文件

#### Domain 层
```java
// com.patra.ingest.domain.port.DataSourcePort
public interface DataSourcePort {
    DataFetchResult fetchData(ExecutionContext context, Batch batch);

    record DataFetchResult(
        boolean success,
        List<CanonicalLiterature> literatures,
        String nextCursorToken,
        String errorMessage,
        int fetchedCount,
        ErrorType errorType
    ) { ... }
}
```

#### Infrastructure 层
```java
// com.patra.ingest.infra.integration.datasource.DataSourceAdapter
@Component
@RequiredArgsConstructor
public class DataSourceAdapter implements DataSourcePort {
    private final ProviderRegistry providerRegistry;

    @Override
    public DataFetchResult fetchData(ExecutionContext context, Batch batch) {
        String provenanceCode = context.provenanceCode();
        DataSourceProvider provider = providerRegistry.getProvider(provenanceCode);
        ProviderRequest request = buildProviderRequest(context, batch);
        ProviderResult result = provider.fetchData(request);
        return convertToDataFetchResult(result);
    }
}
```

#### Application 层
```java
// com.patra.ingest.app.usecase.execution.coordination.GenericBatchExecutor
@Component
@RequiredArgsConstructor
public class GenericBatchExecutor {
    private final DataSourcePort dataSourcePort;  // ✅ 依赖端口，不依赖实现

    public BatchResult execute(ExecutionContext context, Batch batch) {
        DataFetchResult fetchResult = dataSourcePort.fetchData(context, batch);
        // 编排业务流程...
    }
}
```

### 11.4 重构成果

#### 代码简化
- GenericBatchExecutor: 302 行 → 203 行 (-32.8%)
- GenericBatchExecutorTest: 573 行 → 345 行 (-39.8%)
- 删除 ProvenanceConfigConverter: 201 行
- **总计节省: 528 行代码**

#### 职责清晰化
**重构前**: GenericBatchExecutor 负责
- ❌ 提供者注册表查找
- ❌ 配置转换
- ❌ 构建框架请求对象
- ❌ 重试逻辑和指数退避
- ✅ 业务流程编排

**重构后**: GenericBatchExecutor 只负责
- ✅ 调用领域端口获取数据
- ✅ 发布文献
- ✅ 构建批次执行结果
- ✅ 记录日志

#### 测试改进
- 移除了重试逻辑测试（已在 starter 和 infra 层测试）
- 移除了提供者参数传递测试（已在 infra 层测试）
- Application 层测试更聚焦于业务流程
- 测试代码减少 39.8%，但覆盖度不降低

### 11.5 架构验证

#### Maven 依赖验证
```bash
# Application 层 - 不依赖 starter
$ grep "patra-starter-provenance" patra-ingest-app/pom.xml
# (无结果 - 正确)

# Infrastructure 层 - 依赖 starter
$ grep "patra-starter-provenance" patra-ingest-infra/pom.xml
<artifactId>patra-spring-boot-starter-provenance</artifactId>
# (有结果 - 正确)
```

#### 测试验证
- ✅ DataSourceAdapterTest: 12/12 通过
- ✅ GenericBatchExecutorTest: 7/7 通过
- ✅ 编译通过，无依赖冲突

### 11.6 扩展性

#### 添加新数据源
1. **Framework 层**: 实现 DataSourceProvider（如 ArxivDataSourceProvider）
2. **Infrastructure 层**: 注册到 ProviderRegistry
3. **无需修改**: Domain 层、Application 层、DataSourceAdapter

#### 更换框架实现
1. **保持不变**: Domain 层的 DataSourcePort 接口
2. **保持不变**: Application 层的 GenericBatchExecutor
3. **修改**: Infrastructure 层的 DataSourceAdapter 实现
4. **替换**: Framework 层的实现（如使用其他 HTTP 客户端）

---

## 12. 架构优势总结

### 12.1 核心优势

1. **类型安全**：泛型化设计提供编译时类型检查
2. **清晰分层**：严格遵循六边形架构原则
3. **易于扩展**：新数据源和数据类型可快速接入
4. **防腐隔离**：外部模型变化不影响领域模型
5. **策略可插拔**：数据转换策略独立可配置

### 12.2 扩展点

1. **新数据源接入**：实现 `DataSourceProvider<T>` 接口
2. **新数据类型**：定义 `CanonicalData` 实现类
3. **转换策略**：实现 `DataTransformStrategy<S, T>` 接口
4. **能力扩展**：通过 `ProviderCapability` 声明新能力

### 12.3 设计原则体现

- **单一职责**：每个组件职责明确
- **开闭原则**：对扩展开放，对修改关闭
- **依赖倒置**：依赖抽象而非具体实现
- **接口隔离**：细粒度的接口定义
- **里氏替换**：所有提供者实现可互相替换

---

## 13. 总结

### 设计亮点

1. **严格的六边形架构**: 依赖方向正确，Domain ← Application ← Infrastructure
2. **三层接口设计**: DataSourcePort (业务契约) + DataSourceAdapter (桥接层) + DataSourceProvider (技术规范)
3. **桥接适配器**: DataSourceAdapter 完美隔离业务和技术
4. **代码简化**: 移除冗余代码 528 行，职责更清晰
5. **清晰的命名语义**: Port/Adapter/Provider 各司其职

### 架构优势

1. **可测试性**: Application 层无需启动框架即可测试
2. **可维护性**: 业务逻辑与技术实现完全解耦
3. **可扩展性**: 添加新数据源无需修改 Application 层
4. **可替换性**: 可以轻松替换底层技术实现

### 遵循的原则

- ✅ **依赖倒置原则**: 高层不依赖低层，都依赖抽象
- ✅ **单一职责原则**: 每层只负责自己的职责
- ✅ **开闭原则**: 对扩展开放，对修改关闭
- ✅ **接口隔离原则**: 领域接口和框架接口分离
