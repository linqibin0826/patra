# patra-spring-boot-starter-provenance

## 概述

用于 PubMed 和 Europe PMC 数据源的 HTTP 客户端集成 Starter,提供直连模式的数据采集能力,无需独立的出站网关服务。

本 Starter 自动配置医学文献 API 客户端,支持 ESearch、EFetch、EPost 等常用操作,内置请求组装、超时控制、简单重试和本地限流机制。

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
| `pubMedClient` | `PubMedClient` | PubMed E-utilities 客户端 |
| `epmcClient` | `EPMCClient` | Europe PMC 搜索客户端 |
| `provenanceXmlMapper` | `XmlMapper` | PubMed XML 响应映射器 |
| `pubmedArticleConverter` | `PubmedArticleConverter` | PubMed 文章转换器 |
| `defaultConfigProvider` | `DefaultConfigProvider` | 配置提供器 |
| `providerRegistry` | `ProviderRegistry` | 数据源提供者注册表 |
| `pubmedDataSourceProvider` | `PubmedDataSourceProvider` | PubMed 数据源提供者实现 |
| `provenanceMetrics` | `ProvenanceMetrics` | 指标记录器(需要 MeterRegistry) |

### 启用条件

- 配置属性 `patra.provenance.enabled=true` (默认启用)
- 指标 Bean 需要 `MeterRegistry` 存在

## 主要组件

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

参数常量统一维护在 `PubMedParamKeys` 和 `EpmcParamKeys` 类中。

### ProviderRegistry

统一的数据源提供者注册表,支持:
- 自动发现所有 `DataSourceProvider` 实现
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

#### PubMed 搜索

```java
@Component
@RequiredArgsConstructor
public class PubmedSearchPortImpl implements PubmedSearchPort {

    private final PubMedClient pubMedClient;
    private static final PubMedESearchRequestAssembler ASSEMBLER = new PubMedESearchRequestAssembler();

    @Override
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
  - DataSourcePort (业务端口接口)
    ↑ implements
Infrastructure Layer (patra-ingest-infra)
  - DataSourceAdapter (桥接适配器)
    ↓ uses
Framework Layer (patra-starter-provenance) ← 本 Starter
  - DataSourceProvider (技术提供者接口)
  - ProviderRegistry (提供者注册表)
    ↑ implements
Infrastructure Layer (patra-ingest-infra)
  - PubmedDataSourceProvider (具体实现)
  - EpmcDataSourceProvider (具体实现)
```

### 命名语义说明

- **DataSourceProvider**（本 Starter）：框架层的技术提供者接口，定义"如何提供数据获取能力"
- **DataSourceAdapter**（Infrastructure 层）：桥接适配器，连接领域端口和框架提供者
- **DataSourcePort**（Domain 层）：业务端口接口，定义"需要什么数据获取能力"

### 使用场景

Infrastructure 层的 `DataSourceAdapter` 使用本 Starter：

```java
@Component
@RequiredArgsConstructor
public class DataSourceAdapter implements DataSourcePort {
    private final ProviderRegistry providerRegistry; // 来自本 Starter

    @Override
    public DataFetchResult fetchData(ExecutionContext context, Batch batch) {
        // 1. 从注册表获取提供者
        DataSourceProvider provider = providerRegistry.getProvider(context.provenanceCode());

        // 2. 构建提供者请求
        ProviderRequest request = buildProviderRequest(context, batch);

        // 3. 调用提供者
        ProviderResult result = provider.fetchData(request);

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
    public PubMedClient customPubMedClient(ObjectMapper objectMapper) {
        return new PubMedClientImpl(
            new SimpleHttpClient(),
            customConfigProvider(),
            objectMapper,
            xmlMapper(),
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
public class CustomDataSourceProvider implements DataSourceProvider<CanonicalLiterature> {

    @Override
    public String getProvenanceCode() {
        return "custom-source";
    }

    @Override
    public ProviderResult<CanonicalLiterature> fetchData(ProviderRequest request) {
        // 实现数据获取逻辑
        return ProviderResult.success(data, nextCursor);
    }

    @Override
    public ProviderCapability getCapabilities() {
        return ProviderCapability.builder()
            .dataSource("Custom Source")
            .supportedDataTypes(Set.of(DataType.LITERATURE))
            .build();
    }

    @Override
    public Class<CanonicalLiterature> getDataTypeClass() {
        return CanonicalLiterature.class;
    }
}
```

提供者会自动注册到 `ProviderRegistry`。

## 技术栈

- Spring Boot 3.5.7
- Jackson (JSON/XML)
- Micrometer (可选)
- Hutool
- patra-common-core
- patra-common-model
