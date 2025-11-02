# patra-spring-boot-starter-provenance

> Spring Boot Starter,用于 PubMed 和 Europe PMC 客户端集成(直连 HTTP 模式)。

## 📌 目的

自动配置用于访问外部医学文献 API 的**数据源客户端**(直连模式):
- **PubMed** 客户端(ESearch、EFetch)
- **Europe PMC** 客户端(Search)
- 可选: Micrometer 指标集成

## 🏗️ 架构

### 客户端栈

```
┌─────────────────────────────────────┐
│   PubMedClient / EPMCClient         │  ← 领域接口
├─────────────────────────────────────┤
│   PubMedClientImpl / EPMCClientImpl │  ← 实现(SimpleHttpClient)
├─────────────────────────────────────┤
│   External APIs (PubMed, EPMC)      │  ← 远程服务
└─────────────────────────────────────┘
```

### 直连模式说明

- 不依赖独立的出站网关服务;出站由 Starter 内置的 `SimpleHttpClient` 完成
- 支持超时、简单重试和本地 QPS 限流(轻量级,非分布式)

## 🔗 依赖

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-provenance</artifactId>
</dependency>
```

**传递依赖**:
- `patra-common`
- Jackson(JSON/XML)
- Micrometer(可选)

## 🔧 自动配置

(无 Feign 依赖,无需配置额外的网关客户端)

### 提供的 Bean

| Bean | 类型 | 作用域 | 描述 |
|------|------|-------|-------------|
| `pubMedClient` | `PubMedClient` | Singleton | PubMed ESearch/EFetch 客户端 |
| `epmcClient` | `EPMCClient` | Singleton | Europe PMC 搜索客户端 |
| `provenanceMetrics` | `ProvenanceMetrics` | Singleton | 指标记录器(如果 `MeterRegistry` 可用) |
| `provenanceXmlMapper` | `XmlMapper` | Singleton | PubMed XML 负载映射器 |

### 必需的 Bean

- `ProvenanceProperties`(由 `application.yml` 绑定)

### 可选的 Bean

- `MeterRegistry` (用于指标,由 `spring-boot-starter-actuator` 提供)

### 配置属性

```yaml
patra:
  provenance:
    enabled: true  # 启用/禁用自动配置(默认: true)
    pubmed:
      base-url: "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"  # 默认
      timeout-seconds: 30  # 默认
    epmc:
      base-url: "https://www.ebi.ac.uk/europepmc/webservices/rest"  # 默认
      timeout-seconds: 30  # 默认
    defaults:
      batching:
        epost-threshold: 200  # 当 PMID 数量超过此值时切换到 EPost
```

**禁用 Starter**:
```yaml
patra:
  provenance:
    enabled: false
```

## 🚀 用法

### 请求组装(避免重复造轮子)

`PubMedESearchRequestAssembler` 用于从"已渲染为 PubMed 参数"的 `JsonNode` 构建
`ESearchRequest`。也就是说,传入的 `params` 已是诸如 `mindate/maxdate/retmax/retstart` 等
PubMed 官方参数名,装配器只做安全解析与绑定,不再进行标准键映射。

参数名统一在常量类维护:`com.patra.starter.provenance.pubmed.request.PubMedParamKeys`,避免
在业务代码中分散硬编码。

用法:

```java
@Component
@RequiredArgsConstructor
public class PubmedSearchPortImpl implements PubmedSearchPort {
  private final PubMedClient pubMedClient;
  private static final PubMedESearchRequestAssembler ASSEMBLER = new PubMedESearchRequestAssembler();

  @Override
  public PlanMetadata preparePlanMetadata(String query, JsonNode pubmedParams) {
    ESearchRequest request = ASSEMBLER.buildList(pubmedParams);
    ESearchResponse response = pubMedClient.esearch(request);
    if (response == null || response.result() == null) {
      return PlanMetadata.empty();
    }
    var result = response.result();
    return new PlanMetadata(result.count(), result.webEnv(), result.queryKey());
  }
}
```

### 示例: PubMed 计数查询

```java
@Component
@RequiredArgsConstructor
public class PubmedSearchPortImpl implements PubmedSearchPort {

    private final PubMedClient pubMedClient;  // 自动注入

    @Override
    public PlanMetadata preparePlanMetadata(String term, JsonNode params) {
        ESearchRequest request = ASSEMBLER.buildList(params);

        ESearchResponse response = pubMedClient.esearch(request);
        if (response == null || response.result() == null) {
            return PlanMetadata.empty();
        }
        var result = response.result();
        return new PlanMetadata(result.count(), result.webEnv(), result.queryKey());
    }
}
```

### 示例: Europe PMC 搜索

```java
@Component
@RequiredArgsConstructor
public class EpmcSearchPortImpl implements EpmcSearchPort {

    private final EPMCClient epmcClient;  // 自动注入
    private static final EpmcSearchRequestAssembler ASSEMBLER = new EpmcSearchRequestAssembler();

    @Override
    public List<Article> search(JsonNode compiledParams) {
        SearchRequest request = ASSEMBLER.build(compiledParams);

        SearchResponse response = epmcClient.search(request);
        return convertToArticles(response);
    }
}
```

### 测试

建议使用 WireMock/MockWebServer stub 外部服务响应,验证请求路径与参数编码是否正确。

## 📊 指标(可选)

如果 classpath 中存在 `spring-boot-starter-actuator`,则自动启用数据源指标:

### 发布的指标

| 指标名称 | 类型 | 标签 | 描述 |
|-------------|------|------|-------------|
| `papertrace.provenance.call.total` | Counter | `client`, `operation`, `status` | API 调用总数 |
| `papertrace.provenance.call.duration` | Timer | `client`, `operation` | 调用延迟 |
| `papertrace.provenance.error.total` | Counter | `client`, `operation`, `error_type` | 错误计数 |

**示例查询**(Prometheus):
```promql
rate(papertrace_provenance_call_total{client="pubmed",status="success"}[5m])
```

## ⚙️ 配置示例

### 自定义超时

```yaml
patra:
  provenance:
    pubmed:
      timeout-seconds: 60  # 为慢查询增加超时时间
    epmc:
      timeout-seconds: 45
```

### 禁用自动配置

```yaml
patra:
  provenance:
    enabled: false
```

然后手动配置 Bean:
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
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .defaultUseWrapper(false)
            .build();
    }
}
```

## 🛡️ 错误处理

所有客户端在失败时抛出 `ProvenanceClientException`:

```java
try {
    ESearchResponse response = pubMedClient.esearch(request);
} catch (ProvenanceClientException ex) {
    log.error("PubMed API call failed: {}", ex.getMessage(), ex);
    // 处理: 重试、降级或传播
}
```

**常见错误场景**:
- 请求超时 → `ProvenanceClientException` 包含超时详情
- 超出速率限制 → `ProvenanceClientException` 包含 429 状态码
- 无效的 API 密钥 → `ProvenanceClientException` 包含认证错误

## 🔗 相关文档

- [主 README](../README.md)
- [patra-ingest](../patra-ingest/README.md) - 消费者示例
- [架构指南](../docs/ARCHITECTURE.md) - 系统设计模式

---

**最后更新**: 2025-10-14
