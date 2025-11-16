# patra-spring-boot-starter-provenance

## 概述

用于 PubMed 和 Europe PMC 数据源的 HTTP 客户端集成 Starter,提供直连模式的数据采集能力,无需独立的出站网关服务。

本 Starter 自动配置医学文献 API 客户端,支持 ESearch、EFetch、EPost 等常用操作,内置请求组装、超时控制、简单重试和本地限流机制。

**HTTP 客户端**: 基于 **Spring RestClient**（使用底层 JDK 21 HttpClient），提供类型安全的 HTTP 调用和自动配置支持。

## 核心功能

- **PubMed 客户端**: 支持 ESearch、EFetch、EPost 操作
- **Europe PMC 客户端**: 支持文献搜索
- **请求组装器**: 标准化参数构建,避免硬编码
- **配置合并**: 支持全局默认值和数据源级覆盖
- **指标集成**: 可选的 Micrometer 指标采集
- **提供者注册**: 统一的数据源提供者发现机制

## 自动配置内容

### 自动配置类

`ProvenanceAutoConfiguration` 自动配置以下 Bean:

| Bean 名称 | 类型 | 描述 |
|-----------|------|------|
| `pubMedRestClient` | `RestClient` | PubMed 专用 RestClient（含超时和默认 Headers） |
| `epmcRestClient` | `RestClient` | EPMC 专用 RestClient（含超时和默认 Headers） |
| `pubMedClient` | `PubMedClient` | PubMed E-utilities 客户端 |
| `epmcClient` | `EPMCClient` | Europe PMC 搜索客户端 |
| `provenanceXmlMapper` | `XmlMapper` | PubMed XML 响应映射器 |
| `pubmedArticleConverter` | `PubmedLiteratureConverter` | PubMed 文章转换器 |
| `defaultConfigProvider` | `DefaultConfigProvider` | 配置提供器 |
| `providerRegistry` | `ProviderRegistry` | 数据源提供者注册表 |
| `pubmedDataProvider` | `PubmedDataProvider` | PubMed 数据提供者实现 |
| `provenanceMetrics` | `ProvenanceMetrics` | 指标记录器(需要 MeterRegistry) |

### 启用条件

- 配置属性 `patra.provenance.enabled=true` (默认启用)
- 指标 Bean 需要 `MeterRegistry` 存在

## 主要组件

### RestClient（Spring 管理）

提供类型安全的 HTTP 调用能力:

- **PubMed RestClient**: 自动配置 baseUrl、超时、默认 Headers
- **EPMC RestClient**: 自动配置 baseUrl、超时、默认 Headers
- **底层实现**: JDK 21 HttpClient（通过 JdkClientHttpRequestFactory）
- **配置来源**: 从 `ProvenanceConfig.http()` 提取

### PubMedClient

提供 PubMed E-utilities API 访问能力:

- `esearch()`: 搜索文献 ID
- `efetch()`: 获取文献详情
- `epost()`: 上传 ID 列表到 History Server(推荐用于 >200 个 ID)

### EPMCClient

提供 Europe PMC API 访问能力:

- `search()`: 文献搜索

### 请求组装器

- `PubMedESearchRequestAssembler`: 组装 ESearch 请求
- `EpmcSearchRequestAssembler`: 组装 EPMC 搜索请求

**API 常量来源**: 所有参数键、端点路径和参数值枚举统一维护在 **`patra-common-provenance-api`** 模块：
- 参数键常量: `PubMedParamKeys`、`EpmcParamKeys`、`CrossrefParamKeys`
- 端点路径常量: `PubMedEndpoints`（已废弃，推荐使用 `PubMedOperation`）、`EpmcEndpoints`、`CrossrefEndpoints`
- 参数值枚举: `RetMode`、`RetType`、`UseHistory`、`DateType`、`Format`、`ResultType` 等
- 操作枚举: `PubMedOperation`、`EpmcOperation`（推荐使用，封装操作名称+端点+描述）

参考 [patra-common-provenance-api/README.md](../patra-common/patra-common-provenance-api/README.md) 了解完整的 API 常量使用指南。

### ProviderRegistry

统一的数据源提供者注册表,支持:
- 自动发现所有 `ProvenanceDataProvider` 实现
- 按数据源代码查找提供者
- 为 Ingest 服务提供统一的数据源访问接口

## 配置属性

配置前缀: `patra.provenance`

### 全局配置

```yaml
patra:
  provenance:
    enabled: true  # 启用自动配置(默认 true)
    defaults:
      http:
        timeout-connect-millis: 10000  # 连接超时(默认 10s)
        timeout-read-millis: 30000     # 读取超时(默认 30s)
      pagination:
        page-size-value: 100           # 分页大小(默认 100)
      batching:
        epost-threshold: 200           # EPost 阈值(默认 200)
      retry:
        max-retry-times: 3             # 最大重试次数(默认 3)
        initial-delay-millis: 1000     # 初始重试延迟(默认 1s)
      rate-limit:
        max-concurrent-requests: 10    # 最大并发请求(默认 10)
        per-credential-qps-limit: 5    # 每凭证 QPS 限制(默认 5)
```

### 数据源级覆盖

```yaml
patra:
  provenance:
    sources:
      pubmed:
        base-url: "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"
        http:
          timeout-read-millis: 60000  # 覆盖 PubMed 读取超时
      epmc:
        base-url: "https://www.ebi.ac.uk/europepmc/webservices/rest"
```

## 使用方式

### Maven 依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-provenance</artifactId>
</dependency>
```

注意：
- `spring-web` 已作为核心依赖自动引入，提供 RestClient 支持
- `patra-common-provenance-api` 已自动引入，提供 API 常量和枚举

### 配置示例

```yaml
patra:
  provenance:
    enabled: true
    defaults:
      http:
        timeout-read-millis: 45000
      batching:
        epost-threshold: 300
```

### 代码示例

#### 使用 ProvenanceDataProvider（推荐）

```java
@Component
@RequiredArgsConstructor
public class ProvenanceDataAdapterImpl implements ProvenanceDataPort {

    private final ProviderRegistry providerRegistry;

    @Override
    public QuerySession prepareQuerySession(ExecutionContext context, DataType dataType) {
        // 1. 从注册表获取提供者
        ProvenanceDataProvider provider = providerRegistry.getProvider(
            context.provenanceCode(),
            dataType
        );

        // 2. 构建提供者请求
        ProviderRequest request = buildProviderRequest(context);

        // 3. 调用提供者准备查询会话
        return provider.prepareQuerySession(request);
    }

    @Override
    public <T> DataFetchResult<T> fetchData(
        ExecutionContext context,
        DataType dataType,
        TypeReference<T> typeRef,
        Batch batch
    ) {
        ProvenanceDataProvider provider = providerRegistry.getProvider(
            context.provenanceCode(),
            dataType
        );

        ProviderRequest request = buildProviderRequest(context, batch);
        return provider.fetchData(request, dataType, typeRef);
    }
}
```

#### 直接使用 PubMedClient（低级 API）

```java
@Component
@RequiredArgsConstructor
public class PubmedSearchService {

    private final PubMedClient pubMedClient;
    private static final PubMedESearchRequestAssembler ASSEMBLER = new PubMedESearchRequestAssembler();

    public PlanMetadata preparePlanMetadata(String query, JsonNode params) {
        // 从已渲染的参数构建请求
        ESearchRequest request = ASSEMBLER.buildList(params);

        // 调用 PubMed API
        ESearchResponse response = pubMedClient.esearch(request);

        if (response == null || response.result() == null) {
            return PlanMetadata.empty();
        }

        var result = response.result();
        return new PlanMetadata(result.count(), result.webEnv(), result.queryKey());
    }
}
```

#### 使用 API 常量和枚举（推荐）

```java
import com.patra.common.provenance.api.params.PubMedParamKeys;
import com.patra.common.provenance.api.values.pubmed.*;
import com.patra.common.provenance.api.constants.PubMedOperation;

// 构建请求参数（类型安全）
Map<String, String> params = new HashMap<>();
params.put(PubMedParamKeys.TERM, "cancer");
params.put(PubMedParamKeys.RETMODE, RetMode.JSON.value());  // 使用枚举
params.put(PubMedParamKeys.RETTYPE, RetType.UILIST.value());
params.put(PubMedParamKeys.DATETYPE, DateType.PUBLICATION_DATE.value());
params.put(PubMedParamKeys.USEHISTORY, UseHistory.YES.value());

// 使用操作枚举（推荐，包含操作名称+端点+描述）
PubMedOperation op = PubMedOperation.ESEARCH;
String endpoint = op.getEndpoint();  // "/esearch.fcgi"
String operationName = op.getOperationName();  // "esearch"

// 类型安全的枚举比较
if (request.retmode() == RetMode.XML) {
    // 处理 XML 格式
}
```

#### 错误处理

所有客户端在失败时抛出 `ProvenanceClientException`:

```java
try {
    ESearchResponse response = pubMedClient.esearch(request);
} catch (ProvenanceClientException ex) {
    log.error("PubMed API 调用失败: {}", ex.getMessage(), ex);
    // 处理错误: 重试、降级或传播
}
```

## 架构集成

### 六边形架构中的位置

本 Starter 位于**框架层（Framework Layer）**，为基础设施层提供技术支撑：

```
Domain Layer (patra-ingest-domain)
  - ProvenanceDataPort (业务端口接口)
    ↑ implements
Infrastructure Layer (patra-ingest-infra)
  - ProvenanceDataAdapter (桥接适配器)
    ↓ uses
Framework Layer (patra-starter-provenance) ← 本 Starter
  - ProvenanceDataProvider (技术提供者接口)
  - ProviderRegistry (提供者注册表)
  - RestClient (Spring 管理的 HTTP 客户端)
    ↑ implements
Provider Implementations (各数据源实现层)
  - PubmedProvenanceDataProvider (具体实现)
  - EpmcProvenanceDataProvider (具体实现)
```

### 命名语义说明

- **ProvenanceDataProvider**（本 Starter）：框架层的技术提供者接口，定义"如何提供数据获取能力"
- **ProvenanceDataAdapter**（Infrastructure 层）：桥接适配器，连接领域端口和框架提供者
- **ProvenanceDataPort**（Domain 层）：业务端口接口，定义"需要什么数据获取能力"

### 使用场景

Infrastructure 层的 `ProvenanceDataAdapter` 使用本 Starter：

```java
@Component
@RequiredArgsConstructor
public class ProvenanceDataAdapter implements ProvenanceDataPort {
    private final ProviderRegistry providerRegistry; // 来自本 Starter

    @Override
    public <T> DataFetchResult<T> fetchData(
        ExecutionContext context,
        DataType dataType,
        TypeReference<T> typeRef,
        Batch batch
    ) {
        // 1. 从注册表获取提供者
        ProvenanceDataProvider provider = providerRegistry.getProvider(
            context.provenanceCode(),
            dataType
        );

        // 2. 构建提供者请求
        ProviderRequest request = buildProviderRequest(context, batch);

        // 3. 调用提供者（类型安全）
        ProviderResult<T> result = provider.fetchData(request, dataType, typeRef);

        // 4. 转换为领域结果
        return convertToDataFetchResult(result);
    }
}
```

## 扩展点

### 自定义配置提供器

如需完全自定义配置逻辑,可禁用自动配置并手动创建 Bean:

```yaml
patra:
  provenance:
    enabled: false
```

```java
@Configuration
public class CustomProvenanceConfig {

    @Bean
    public RestClient customPubMedRestClient() {
        return RestClient.builder()
            .baseUrl("https://custom-url")
            .defaultHeader("Custom-Header", "value")
            .build();
    }

    @Bean
    public PubMedClient customPubMedClient(
        RestClient customPubMedRestClient,
        ObjectMapper objectMapper,
        XmlMapper xmlMapper
    ) {
        return new PubMedClientImpl(
            customPubMedRestClient,
            customConfigProvider(),
            objectMapper,
            xmlMapper,
            null
        );
    }

    private XmlMapper xmlMapper() {
        return XmlMapper.builder()
            .findAndAddModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
    }
}
```

### 实现自定义数据源提供者

```java
@Component
public class CustomProvenanceDataProvider implements ProvenanceDataProvider {

    @Override
    public ProvenanceCode getProvenanceCode() {
        return ProvenanceCode.of("custom-source");
    }

    @Override
    public Set<DataType> getSupportedDataTypes() {
        return Set.of(DataType.LITERATURE);
    }

    @Override
    public <T> ProviderResult<T> fetchData(
        ProviderRequest request,
        DataType dataType,
        TypeReference<T> typeRef
    ) {
        // 实现数据获取逻辑
        // ...
        return ProviderResult.success(data, dataType, nextCursor);
    }

    @Override
    public QuerySession prepareQuerySession(ProviderRequest request) {
        // 实现查询会话准备逻辑
        // ...
        return new QuerySession(totalCount, sessionToken);
    }
}
```

提供者会自动注册到 `ProviderRegistry`。

## 技术栈

- Spring Boot 3.5.7
- **Spring Web** (RestClient)
- Jackson (JSON/XML)
- Micrometer (可选)
- Hutool
- patra-common-core
- patra-common-model
- **patra-common-provenance-api** (API 常量和枚举)

## 迁移说明

### v0.1.0: 从 SimpleHttpClient 到 RestClient

**背景**: v0.1.0 版本将 HTTP 调用从自定义的 `SimpleHttpClient` 迁移到 Spring 管理的 `RestClient`。

**主要变更**:

1. **依赖变更**:
   - 新增: `spring-web` 依赖（提供 RestClient）
   - 移除: 自定义 `SimpleHttpClient`、`HttpResilienceConfig`

2. **自动配置变更**:
   - 新增: `pubMedRestClient` 和 `epmcRestClient` Bean
   - 修改: `PubMedClientImpl` 和 `EPMCClientImpl` 构造函数接受 `RestClient`

3. **配置保持不变**:
   - `patra.provenance.defaults.http.*` 配置继续有效
   - 超时和默认 Headers 从配置中自动提取

**影响范围**:
- **对用户透明**: 如果使用 `PubMedClient` 或 `EPMCClient` 接口，无需修改代码
- **自定义配置**: 如果手动创建了 `PubMedClientImpl`，需要传入 `RestClient` 而非 `SimpleHttpClient`

**示例**:

```java
// 旧版本（已废弃）
PubMedClient client = new PubMedClientImpl(
    new SimpleHttpClient(),  // ❌ 已移除
    configProvider,
    objectMapper,
    xmlMapper,
    null
);

// 新版本（推荐）
// 1. 使用自动配置（推荐）
@Autowired
private PubMedClient pubMedClient;  // ✅ 直接注入

// 2. 手动配置（高级用法）
RestClient restClient = RestClient.builder()
    .baseUrl("https://eutils.ncbi.nlm.nih.gov/entrez/eutils")
    .build();

PubMedClient client = new PubMedClientImpl(
    restClient,  // ✅ 使用 RestClient
    configProvider,
    objectMapper,
    xmlMapper,
    null
);
```

**优势**:
- ✅ 与 Spring 生态深度集成
- ✅ 统一的超时、重试、拦截器配置
- ✅ 更好的测试支持（MockRestServiceServer）
- ✅ 减少自定义代码维护成本

### v0.1.0: API 常量迁移到 patra-common-provenance-api

**背景**: v0.1.0 版本将 API 常量从 `patra-spring-boot-starter-provenance` 迁移到独立的 `patra-common-provenance-api` 模块。

**主要变更**:

1. **包路径变更**:
   - 旧位置: `com.patra.starter.provenance.pubmed.request.PubMedParamKeys`
   - 新位置: `com.patra.common.provenance.api.params.PubMedParamKeys`

   - 旧位置: `com.patra.starter.provenance.epmc.request.EpmcParamKeys`
   - 新位置: `com.patra.common.provenance.api.params.EpmcParamKeys`

2. **新增功能**:
   - 端点路径常量: `PubMedEndpoints`、`EpmcEndpoints`、`CrossrefEndpoints`（已废弃）
   - 操作枚举: `PubMedOperation`、`EpmcOperation`（推荐使用，封装操作名称+端点+描述）
   - 参数值枚举: `RetMode`、`RetType`、`UseHistory`、`DateType`、`Format`、`ResultType`

3. **兼容性**:
   - ✅ `patra-spring-boot-starter-provenance` 自动依赖 `patra-common-provenance-api`
   - ✅ 旧代码无需修改，但建议更新导入语句

**迁移指南**:

```java
// 旧导入（已废弃，但仍兼容）
import com.patra.starter.provenance.pubmed.request.PubMedParamKeys;

// 新导入（推荐）
import com.patra.common.provenance.api.params.PubMedParamKeys;
import com.patra.common.provenance.api.values.pubmed.RetMode;
import com.patra.common.provenance.api.constants.PubMedOperation;

// 使用类型安全的枚举（推荐）
params.put(PubMedParamKeys.RETMODE, RetMode.JSON.value());
```

**优势**:
- ✅ 单一事实来源（SSOT），避免重复定义
- ✅ 类型安全的枚举，避免魔法字符串
- ✅ 跨模块共享，支持测试、监控、CLI 等场景
- ✅ IDE 友好，自动补全和重构安全

参考 [patra-common-provenance-api/README.md](../patra-common/patra-common-provenance-api/README.md) 了解完整的 API 常量使用指南。
