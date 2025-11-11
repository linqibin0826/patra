# DataSourceAdapter 泛型适配器接口契约

**版本**: 1.0.0
**模块**: `patra-starter-provenance`
**包路径**: `com.patra.starter.provenance.common.adapter`
**最后更新**: 2025-11-11

---

## 接口定义

### Java 接口签名

```java
package com.patra.starter.provenance.common.adapter;

import com.patra.ingest.domain.model.canonical.CanonicalData;

/**
 * 数据源适配器泛型接口。
 *
 * <p>封装与外部数据源的交互,负责数据获取和转换。</p>
 *
 * <h2>设计原则：</h2>
 * <ul>
 *   <li>泛型化：支持返回不同类型的规范数据(CanonicalLiterature、CanonicalAuthor等)</li>
 *   <li>职责单一：仅负责数据检索和格式转换,不包含业务逻辑</li>
 *   <li>能力声明：通过 getCapabilities() 明确声明支持的数据类型</li>
 *   <li>错误分类：标识错误类型(RETRIABLE/NON_RETRIABLE),指导上层重试决策</li>
 * </ul>
 *
 * @param <T> 规范数据类型(必须实现 CanonicalData 接口)
 * @since 1.0.0
 * @see AdapterRequest
 * @see AdapterResult
 * @see AdapterCapabilities
 */
public interface DataSourceAdapter<T extends CanonicalData> {

    /**
     * 获取数据源代码。
     *
     * <p>全局唯一标识此适配器对应的数据源(如 "pubmed", "epmc", "arxiv")。</p>
     *
     * @return 数据源代码,不能为空
     */
    String getProvenanceCode();

    /**
     * 获取适配器能力声明。
     *
     * <p>声明此适配器支持的数据类型列表,用于注册表查找和能力检查。</p>
     *
     * @return 能力声明对象
     */
    AdapterCapabilities getCapabilities();

    /**
     * 从外部数据源获取数据。
     *
     * <p>此方法负责:</p>
     * <ol>
     *   <li>调用外部 API 获取原始数据</li>
     *   <li>调用转换策略将原始数据转换为规范数据</li>
     *   <li>识别错误类型(RETRIABLE/NON_RETRIABLE)</li>
     *   <li>构建并返回 AdapterResult</li>
     * </ol>
     *
     * <h3>错误处理：</h3>
     * <ul>
     *   <li>不抛出异常：所有错误都封装在 AdapterResult 中</li>
     *   <li>错误分类：根据异常类型和HTTP状态码标识错误类型</li>
     *   <li>部分成功：批量获取时,支持返回部分成功的数据</li>
     * </ul>
     *
     * @param request 适配器请求对象(包含查询条件、分页参数等)
     * @return 适配器结果对象(包含成功数据或错误信息)
     */
    AdapterResult<T> fetchData(AdapterRequest request);
}
```

---

## AdapterRequest (请求对象)

### 字段定义

```java
package com.patra.starter.provenance.common.adapter;

import lombok.Value;
import lombok.Builder;
import java.util.Map;

/**
 * 适配器请求对象。
 *
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
public class AdapterRequest {

    /**
     * 操作代码(如 "INGEST_LITERATURE", "SEARCH_AUTHOR")。
     */
    String operationCode;

    /**
     * 请求的数据类型(如 LITERATURE, AUTHOR, CITATION)。
     */
    DataType requestedDataType;

    /**
     * 数据源配置快照(从 patra-registry 获取)。
     */
    ProvenanceConfigSnapshot config;

    /**
     * 执行参数(包含查询条件、分页参数等)。
     */
    Map<String, Object> executionParams;

    /**
     * 元数据(可选,用于传递额外信息)。
     */
    Map<String, Object> metadata;

    /**
     * 获取查询参数。
     *
     * @param key 参数键
     * @return 参数值,如果不存在返回 null
     */
    public Object getParam(String key) {
        return executionParams != null ? executionParams.get(key) : null;
    }

    /**
     * 获取字符串类型的查询参数。
     *
     * @param key 参数键
     * @return 参数值,如果不存在或类型不匹配返回 null
     */
    public String getStringParam(String key) {
        Object value = getParam(key);
        return value instanceof String ? (String) value : null;
    }

    /**
     * 获取整数类型的查询参数。
     *
     * @param key 参数键
     * @return 参数值,如果不存在或类型不匹配返回 null
     */
    public Integer getIntParam(String key) {
        Object value = getParam(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
```

### 请求示例

```java
// 文献检索请求
AdapterRequest request = AdapterRequest.builder()
    .operationCode("INGEST_LITERATURE")
    .requestedDataType(DataType.LITERATURE)
    .config(provenanceConfigSnapshot)  // 从 patra-registry 获取
    .executionParams(Map.of(
        "query", "COVID-19",
        "pageSize", 100,
        "cursorToken", "abc123"
    ))
    .build();

// 作者检索请求
AdapterRequest authorRequest = AdapterRequest.builder()
    .operationCode("SEARCH_AUTHOR")
    .requestedDataType(DataType.AUTHOR)
    .config(provenanceConfigSnapshot)
    .executionParams(Map.of(
        "authorName", "John Smith",
        "orcid", "0000-0001-2345-6789"
    ))
    .build();
```

---

## AdapterResult (结果对象)

### 字段定义

```java
package com.patra.starter.provenance.common.adapter;

import com.patra.ingest.domain.model.canonical.CanonicalData;
import com.patra.ingest.domain.model.valueobject.ErrorType;
import lombok.Value;
import lombok.Builder;
import java.util.List;
import java.util.Map;

/**
 * 适配器结果对象。
 *
 * @param <T> 规范数据类型
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
public class AdapterResult<T extends CanonicalData> {

    /**
     * 是否成功。
     */
    boolean success;

    /**
     * 获取到的规范数据列表。
     */
    List<T> data;

    /**
     * 下一页游标令牌(用于分页,可选)。
     */
    String nextCursorToken;

    /**
     * 错误消息(失败时填充)。
     */
    String errorMessage;

    /**
     * 错误类型(失败时填充)。
     */
    ErrorType errorType;

    /**
     * 获取到的数据数量。
     */
    int fetchedCount;

    /**
     * 元数据(可选,用于传递额外信息如retryAfterSeconds)。
     */
    Map<String, Object> metadata;

    /**
     * 创建成功结果。
     *
     * @param data 规范数据列表
     * @param <T> 数据类型
     * @return AdapterResult 实例
     */
    public static <T extends CanonicalData> AdapterResult<T> success(List<T> data) {
        return AdapterResult.<T>builder()
            .success(true)
            .data(data)
            .fetchedCount(data.size())
            .errorType(ErrorType.NONE)
            .build();
    }

    /**
     * 创建成功结果(带游标令牌)。
     *
     * @param data 规范数据列表
     * @param nextCursor 下一页游标令牌
     * @param <T> 数据类型
     * @return AdapterResult 实例
     */
    public static <T extends CanonicalData> AdapterResult<T> success(
            List<T> data, String nextCursor) {
        return AdapterResult.<T>builder()
            .success(true)
            .data(data)
            .nextCursorToken(nextCursor)
            .fetchedCount(data.size())
            .errorType(ErrorType.NONE)
            .build();
    }

    /**
     * 创建可重试错误结果。
     *
     * @param errorMessage 错误消息
     * @param <T> 数据类型
     * @return AdapterResult 实例
     */
    public static <T extends CanonicalData> AdapterResult<T> retriableFailure(
            String errorMessage) {
        return AdapterResult.<T>builder()
            .success(false)
            .errorMessage(errorMessage)
            .errorType(ErrorType.RETRIABLE)
            .fetchedCount(0)
            .build();
    }

    /**
     * 创建不可重试错误结果。
     *
     * @param errorMessage 错误消息
     * @param <T> 数据类型
     * @return AdapterResult 实例
     */
    public static <T extends CanonicalData> AdapterResult<T> nonRetriableFailure(
            String errorMessage) {
        return AdapterResult.<T>builder()
            .success(false)
            .errorMessage(errorMessage)
            .errorType(ErrorType.NON_RETRIABLE)
            .fetchedCount(0)
            .build();
    }

    /**
     * 创建部分成功结果。
     *
     * @param data 成功的数据列表
     * @param errorMessage 部分失败的错误消息
     * @param <T> 数据类型
     * @return AdapterResult 实例
     */
    public static <T extends CanonicalData> AdapterResult<T> partialSuccess(
            List<T> data, String errorMessage) {
        return AdapterResult.<T>builder()
            .success(true)
            .data(data)
            .fetchedCount(data.size())
            .errorMessage(errorMessage)
            .errorType(ErrorType.PARTIAL_SUCCESS)
            .build();
    }

    /**
     * 添加元数据。
     *
     * @param key 键
     * @param value 值
     * @return 新的 AdapterResult 实例
     */
    public AdapterResult<T> withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(metadata != null ? metadata : Map.of());
        newMetadata.put(key, value);
        return toBuilder().metadata(newMetadata).build();
    }
}
```

### 结果示例

```java
// 成功结果
AdapterResult<CanonicalLiterature> successResult = AdapterResult.success(
    List.of(literature1, literature2, literature3),
    "nextCursor123"
);

// 可重试错误(网络超时)
AdapterResult<CanonicalLiterature> retriableError = AdapterResult.retriableFailure(
    "网络超时: Read timed out"
);

// 不可重试错误(认证失败)
AdapterResult<CanonicalLiterature> nonRetriableError = AdapterResult.nonRetriableFailure(
    "API认证失败: 401 Unauthorized"
);

// HTTP 429 限流(可重试,带 retryAfterSeconds)
AdapterResult<CanonicalLiterature> rateLimitError = AdapterResult
    .retriableFailure("API限流,请稍后重试")
    .withMetadata("retryAfterSeconds", 60);

// 部分成功
AdapterResult<CanonicalLiterature> partialResult = AdapterResult.partialSuccess(
    List.of(literature1, literature2),  // 2条成功
    "部分数据转换失败: 3条失败,详见日志"  // 3条失败
);
```

---

## AdapterCapabilities (能力声明对象)

### 字段定义

```java
package com.patra.starter.provenance.common.adapter;

import com.patra.ingest.domain.model.valueobject.DataType;
import lombok.Value;
import lombok.Builder;
import java.util.Set;

/**
 * 适配器能力声明对象。
 *
 * @since 1.0.0
 */
@Value
@Builder
public class AdapterCapabilities {

    /**
     * 数据源名称。
     */
    String provenanceCode;

    /**
     * 数据源显示名称(可选)。
     */
    String provenanceName;

    /**
     * 支持的数据类型集合。
     */
    Set<DataType> supportedDataTypes;

    /**
     * 是否支持分页。
     */
    boolean supportsPagination;

    /**
     * 是否支持游标分页。
     */
    boolean supportsCursorPagination;

    /**
     * 最大批量大小(单次请求最多返回多少条数据)。
     */
    int maxBatchSize;

    /**
     * 检查是否支持指定的数据类型。
     *
     * @param dataType 数据类型
     * @return true 如果支持
     */
    public boolean supports(DataType dataType) {
        return supportedDataTypes != null && supportedDataTypes.contains(dataType);
    }
}
```

### 能力声明示例

```java
// PubMed 适配器能力
AdapterCapabilities pubmedCapabilities = AdapterCapabilities.builder()
    .provenanceCode("pubmed")
    .provenanceName("PubMed")
    .supportedDataTypes(Set.of(
        DataType.LITERATURE,
        DataType.AUTHOR,
        DataType.CITATION
    ))
    .supportsPagination(true)
    .supportsCursorPagination(false)  // PubMed 使用 retstart/retmax 分页
    .maxBatchSize(200)
    .build();

// ArXiv 适配器能力
AdapterCapabilities arxivCapabilities = AdapterCapabilities.builder()
    .provenanceCode("arxiv")
    .provenanceName("ArXiv")
    .supportedDataTypes(Set.of(
        DataType.LITERATURE,
        DataType.FULLTEXT
    ))
    .supportsPagination(true)
    .supportsCursorPagination(true)  // ArXiv 使用 resumptionToken
    .maxBatchSize(1000)
    .build();
```

---

## AdapterRegistry (注册表接口)

### 接口定义

```java
package com.patra.starter.provenance.common.registry;

import com.patra.starter.provenance.common.adapter.DataSourceAdapter;
import com.patra.ingest.domain.model.canonical.CanonicalData;
import com.patra.ingest.domain.model.valueobject.DataType;

/**
 * 适配器注册表接口。
 *
 * <p>负责管理和查找数据源适配器。</p>
 *
 * @since 1.0.0
 */
public interface AdapterRegistry {

    /**
     * 根据数据源代码和数据类型查找适配器。
     *
     * <p>时间复杂度: O(1)</p>
     *
     * @param provenanceCode 数据源代码(如 "pubmed")
     * @param dataType 数据类型(如 DataType.LITERATURE)
     * @param <T> 规范数据类型
     * @return 适配器实例
     * @throws AdapterNotFoundException 如果未找到匹配的适配器
     */
    <T extends CanonicalData> DataSourceAdapter<T> getAdapter(
        String provenanceCode,
        Class<T> dataType
    );

    /**
     * 根据数据源代码和数据类型枚举查找适配器。
     *
     * @param provenanceCode 数据源代码
     * @param dataType 数据类型枚举
     * @param <T> 规范数据类型
     * @return 适配器实例
     * @throws AdapterNotFoundException 如果未找到匹配的适配器
     */
    <T extends CanonicalData> DataSourceAdapter<T> getAdapter(
        String provenanceCode,
        DataType dataType
    );

    /**
     * 注册适配器。
     *
     * @param adapter 适配器实例
     */
    void register(DataSourceAdapter<?> adapter);

    /**
     * 检查是否存在指定的适配器。
     *
     * @param provenanceCode 数据源代码
     * @param dataType 数据类型
     * @return true 如果存在
     */
    boolean hasAdapter(String provenanceCode, DataType dataType);
}
```

### 使用示例

```java
// 查找适配器
DataSourceAdapter<CanonicalLiterature> literatureAdapter =
    adapterRegistry.getAdapter("pubmed", CanonicalLiterature.class);

DataSourceAdapter<CanonicalAuthor> authorAdapter =
    adapterRegistry.getAdapter("pubmed", DataType.AUTHOR);

// 检查能力
if (adapterRegistry.hasAdapter("pubmed", DataType.FULLTEXT)) {
    // PubMed 支持全文
} else {
    // PubMed 不支持全文
}

// 调用适配器
AdapterRequest request = AdapterRequest.builder()
    .requestedDataType(DataType.LITERATURE)
    .executionParams(Map.of("query", "COVID-19"))
    .build();

AdapterResult<CanonicalLiterature> result = literatureAdapter.fetchData(request);

if (result.isSuccess()) {
    List<CanonicalLiterature> literatures = result.getData();
    // 处理成功数据
} else if (result.getErrorType() == ErrorType.RETRIABLE) {
    // 可重试错误,等待后重试
    log.warn("适配器调用失败(可重试): {}", result.getErrorMessage());
} else {
    // 不可重试错误,记录并告警
    log.error("适配器调用失败(不可重试): {}", result.getErrorMessage());
}
```

---

## 实现指南

### 1. 适配器实现示例

```java
@Component
public class PubMedLiteratureAdapter implements DataSourceAdapter<CanonicalLiterature> {

    private final PubMedClient pubMedClient;
    private final DataTransformStrategy<PubMedArticle, CanonicalLiterature> transformStrategy;

    @Override
    public String getProvenanceCode() {
        return "pubmed";
    }

    @Override
    public AdapterCapabilities getCapabilities() {
        return AdapterCapabilities.builder()
            .provenanceCode("pubmed")
            .provenanceName("PubMed")
            .supportedDataTypes(Set.of(DataType.LITERATURE, DataType.AUTHOR, DataType.CITATION))
            .supportsPagination(true)
            .maxBatchSize(200)
            .build();
    }

    @Override
    public AdapterResult<CanonicalLiterature> fetchData(AdapterRequest request) {
        try {
            // 1. 调用 Client 获取原始数据
            List<PubMedArticle> articles = pubMedClient.searchArticles(
                buildSearchRequest(request)
            );

            // 2. 调用 Strategy 转换数据
            TransformResult<CanonicalLiterature> transformResult =
                transformStrategy.batchTransform(articles);

            // 3. 检查成功率
            double threshold = request.getConfig().getBatchingConfig().getSuccessRateThreshold();
            if (transformResult.getSuccessRate() < threshold) {
                return AdapterResult.partialSuccess(
                    transformResult.getSuccessItems(),
                    String.format("转换成功率 %.2f%% 低于阈值 %.2f%%",
                        transformResult.getSuccessRate() * 100,
                        threshold * 100)
                );
            }

            // 4. 构建成功结果
            return AdapterResult.success(
                transformResult.getSuccessItems(),
                extractCursorToken(articles)
            );

        } catch (HttpClientErrorException.TooManyRequests e) {
            // HTTP 429 限流
            long retryAfterSeconds = parseRetryAfter(e.getResponseHeaders());
            return AdapterResult.<CanonicalLiterature>retriableFailure(
                "API限流,建议" + retryAfterSeconds + "秒后重试"
            ).withMetadata("retryAfterSeconds", retryAfterSeconds);

        } catch (SocketTimeoutException | TimeoutException e) {
            // 网络超时
            return AdapterResult.retriableFailure("网络超时: " + e.getMessage());

        } catch (HttpServerErrorException e) {
            // 服务器错误
            return AdapterResult.retriableFailure("服务器错误: " + e.getMessage());

        } catch (HttpClientErrorException e) {
            // 客户端错误(不可重试)
            return AdapterResult.nonRetriableFailure(
                "客户端错误(" + e.getStatusCode() + "): " + e.getMessage()
            );

        } catch (IllegalArgumentException e) {
            // 参数错误(不可重试)
            return AdapterResult.nonRetriableFailure("参数错误: " + e.getMessage());
        }
    }

    private PubMedSearchRequest buildSearchRequest(AdapterRequest request) {
        // 构建 PubMed 搜索请求
    }

    private String extractCursorToken(List<PubMedArticle> articles) {
        // 提取游标令牌
    }

    private long parseRetryAfter(HttpHeaders headers) {
        // 解析 Retry-After 响应头
    }
}
```

### 2. 注册表实现示例

```java
@Component
public class DefaultAdapterRegistry implements AdapterRegistry {

    // Key: "{provenanceCode}:{dataType}", Value: Adapter
    private final Map<String, DataSourceAdapter<?>> adapters = new ConcurrentHashMap<>();

    // Spring 自动装配所有适配器
    public DefaultAdapterRegistry(List<DataSourceAdapter<?>> adapterList) {
        adapterList.forEach(this::register);
    }

    @Override
    public void register(DataSourceAdapter<?> adapter) {
        String provenanceCode = adapter.getProvenanceCode();
        adapter.getCapabilities().getSupportedDataTypes().forEach(dataType -> {
            String key = buildKey(provenanceCode, dataType);
            adapters.put(key, adapter);
            log.info("注册适配器: {} -> {}", key, adapter.getClass().getSimpleName());
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends CanonicalData> DataSourceAdapter<T> getAdapter(
            String provenanceCode, Class<T> dataTypeClass) {
        DataType dataType = inferDataType(dataTypeClass);
        return getAdapter(provenanceCode, dataType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends CanonicalData> DataSourceAdapter<T> getAdapter(
            String provenanceCode, DataType dataType) {
        String key = buildKey(provenanceCode, dataType);
        DataSourceAdapter<?> adapter = adapters.get(key);

        if (adapter == null) {
            throw new AdapterNotFoundException(
                "未找到适配器: " + provenanceCode + " -> " + dataType.name()
            );
        }

        return (DataSourceAdapter<T>) adapter;
    }

    @Override
    public boolean hasAdapter(String provenanceCode, DataType dataType) {
        String key = buildKey(provenanceCode, dataType);
        return adapters.containsKey(key);
    }

    private String buildKey(String provenanceCode, DataType dataType) {
        return provenanceCode + ":" + dataType.name();
    }

    private DataType inferDataType(Class<? extends CanonicalData> dataTypeClass) {
        if (dataTypeClass == CanonicalLiterature.class) {
            return DataType.LITERATURE;
        } else if (dataTypeClass == CanonicalAuthor.class) {
            return DataType.AUTHOR;
        } else if (dataTypeClass == CanonicalJournal.class) {
            return DataType.JOURNAL;
        } else if (dataTypeClass == CanonicalCitation.class) {
            return DataType.CITATION;
        } else if (dataTypeClass == CanonicalFullText.class) {
            return DataType.FULLTEXT;
        } else {
            throw new IllegalArgumentException("未知的数据类型: " + dataTypeClass.getName());
        }
    }
}
```

---

## 契约验证

### 单元测试

```java
@Test
void should_return_success_when_fetch_data_successfully() {
    // Given
    AdapterRequest request = AdapterRequest.builder()
        .requestedDataType(DataType.LITERATURE)
        .executionParams(Map.of("query", "COVID-19"))
        .build();

    // When
    AdapterResult<CanonicalLiterature> result = adapter.fetchData(request);

    // Then
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isNotEmpty();
    assertThat(result.getErrorType()).isEqualTo(ErrorType.NONE);
}

@Test
void should_return_retriable_error_when_network_timeout() {
    // Given
    when(pubMedClient.searchArticles(any()))
        .thenThrow(new SocketTimeoutException("Read timed out"));

    // When
    AdapterResult<CanonicalLiterature> result = adapter.fetchData(request);

    // Then
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ErrorType.RETRIABLE);
    assertThat(result.getErrorMessage()).contains("网络超时");
}

@Test
void should_return_non_retriable_error_when_authentication_failed() {
    // Given
    when(pubMedClient.searchArticles(any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

    // When
    AdapterResult<CanonicalLiterature> result = adapter.fetchData(request);

    // Then
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ErrorType.NON_RETRIABLE);
    assertThat(result.getErrorMessage()).contains("401");
}
```

---

## 总结

本契约定义了泛型化数据源适配器的标准接口,确保:

1. ✅ **类型安全**: 使用 Java 泛型,编译期类型检查
2. ✅ **职责单一**: 适配器仅负责数据获取和错误分类
3. ✅ **能力声明**: 明确声明支持的数据类型
4. ✅ **错误分类**: 区分 RETRIABLE 和 NON_RETRIABLE 错误
5. ✅ **部分成功**: 支持批量处理的部分成功机制
6. ✅ **O(1) 查找**: 注册表使用 HashMap 实现高效查找

**下一步**: 定义数据转换策略接口(参见 `DataTransformStrategy.md`)。
