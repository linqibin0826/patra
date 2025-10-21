# patra-spring-boot-starter-provenance

> Spring Boot starter for PubMed and Europe PMC client integration (direct HTTP).

## 📌 Purpose

Auto-configures **provenance clients** for accessing external medical literature APIs（直连模式）:
- **PubMed** client（ESearch、EFetch）
- **Europe PMC** client（Search）
- 可选：Micrometer 指标集成

## 🏗️ Architecture

### Client Stack

```
┌─────────────────────────────────────┐
│   PubMedClient / EPMCClient         │  ← Domain interface
├─────────────────────────────────────┤
│   PubMedClientImpl / EPMCClientImpl │  ← Implementation (SimpleHttpClient)
├─────────────────────────────────────┤
│   External APIs (PubMed, EPMC)      │  ← Remote services
└─────────────────────────────────────┘
```

### 直连模式说明

- 不依赖独立的出站网关服务；出站由启动器内置 `SimpleHttpClient` 完成
- 支持超时、简单重试和本地 QPS 节流（轻量级，非分布式）

## 🔗 Dependencies

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-provenance</artifactId>
</dependency>
```

**Transitive dependencies**：
- `patra-common`
- Jackson（JSON/XML）
- Micrometer（可选）

## 🔧 Auto-Configuration

（无 Feign 依赖，无需配置额外的网关客户端）

### Beans Provided

| Bean | Type | Scope | Description |
|------|------|-------|-------------|
| `pubMedClient` | `PubMedClient` | Singleton | PubMed ESearch/EFetch client |
| `epmcClient` | `EPMCClient` | Singleton | Europe PMC search client |
| `provenanceMetrics` | `ProvenanceMetrics` | Singleton | Metrics recorder (if `MeterRegistry` available) |
| `xmlToJsonConverter` | `XmlToJsonConverter` | Singleton | PubMed XML response converter |

### Required Beans

- `ProvenanceProperties`（由 `application.yml` 绑定）

### Optional Beans

- `MeterRegistry` (for metrics, provided by `spring-boot-starter-actuator`)

### Configuration Properties

```yaml
patra:
  provenance:
    enabled: true  # Enable/disable auto-configuration (default: true)
    pubmed:
      base-url: "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"  # Default
      timeout-seconds: 30  # Default
    epmc:
      base-url: "https://www.ebi.ac.uk/europepmc/webservices/rest"  # Default
      timeout-seconds: 30  # Default
```

**Disabling the starter:**
```yaml
patra:
  provenance:
    enabled: false
```

## 🚀 Usage

### Request Assembly（避免重复造轮子）

`PubMedESearchRequestAssembler` 用于从“已渲染为 PubMed 参数”的 `JsonNode` 构建
`ESearchRequest`。也就是说，传入的 `params` 已是诸如 `mindate/maxdate/retmax/retstart` 等
PubMed 官方参数名，装配器只做安全解析与绑定，不再进行标准键映射。

参数名统一在常量类维护：`com.patra.starter.provenance.pubmed.request.PubMedParamKeys`，避免
在业务代码中分散硬编码。

用法：

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

### Example: PubMed Count Lookup

```java
@Component
@RequiredArgsConstructor
public class PubmedSearchPortImpl implements PubmedSearchPort {

    private final PubMedClient pubMedClient;  // Auto-injected

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

### Example: Europe PMC Search

```java
@Component
@RequiredArgsConstructor
public class EpmcSearchPortImpl implements EpmcSearchPort {

    private final EPMCClient epmcClient;  // Auto-injected
    private static final EpmcSearchRequestAssembler ASSEMBLER = new EpmcSearchRequestAssembler();

    @Override
    public List<Article> search(JsonNode compiledParams) {
        SearchRequest request = ASSEMBLER.build(compiledParams);

        SearchResponse response = epmcClient.search(request);
        return convertToArticles(response);
    }
}
```

### Testing

建议使用 WireMock/MockWebServer stub 外部服务响应，验证请求路径与参数编码是否正确。

## 📊 Metrics (Optional)

If `spring-boot-starter-actuator` is on classpath, provenance metrics are auto-enabled:

### Metrics Published

| Metric Name | Type | Tags | Description |
|-------------|------|------|-------------|
| `papertrace.provenance.call.total` | Counter | `client`, `operation`, `status` | Total API calls |
| `papertrace.provenance.call.duration` | Timer | `client`, `operation` | Call latency |
| `papertrace.provenance.error.total` | Counter | `client`, `operation`, `error_type` | Error counts |

**Example query** (Prometheus):
```promql
rate(papertrace_provenance_call_total{client="pubmed",status="success"}[5m])
```

## ⚙️ Configuration Examples

### Custom Timeouts

```yaml
patra:
  provenance:
    pubmed:
      timeout-seconds: 60  # Increase for slow queries
    epmc:
      timeout-seconds: 45
```

### Disable Auto-Configuration

```yaml
patra:
  provenance:
    enabled: false
```

Then manually configure beans:
```java
@Configuration
public class CustomProvenanceConfig {

    @Bean
    public PubMedClient customPubMedClient() {
        return new PubMedClientImpl(
            new SimpleHttpClient(),
            customConfigProvider(),
            new XmlToJsonConverter(),
            objectMapper(),
            null
        );
    }
}
```

## 🛡️ Error Handling

All clients throw `ProvenanceClientException` for failures:

```java
try {
    ESearchResponse response = pubMedClient.esearch(request);
} catch (ProvenanceClientException ex) {
    log.error("PubMed API call failed: {}", ex.getMessage(), ex);
    // Handle: retry, fallback, or propagate
}
```

**Common error scenarios:**
- Request timeout → `ProvenanceClientException` with timeout details
- Rate limit exceeded → `ProvenanceClientException` with 429 status code
- Invalid API key → `ProvenanceClientException` with authentication error

## 🔗 Related Documentation

- [Main README](../README.md)
- [patra-ingest](../patra-ingest/README.md) - Consumer example
- [Architecture Guide](../docs/ARCHITECTURE.md) - System design patterns

---

**Last Updated**: 2025-10-14
