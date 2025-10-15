# patra-spring-boot-starter-provenance

> Spring Boot starter for PubMed and Europe PMC client integration.

## 📌 Purpose

Auto-configures **provenance clients** for accessing external medical literature APIs:
- **PubMed** client (ESearch, EFetch operations)
- **Europe PMC** client (search operations)
- Backed by `patra-egress-gateway` for resilient external calls
- Micrometer metrics integration (optional)

## 🏗️ Architecture

### Client Stack

```
┌─────────────────────────────────────┐
│   PubMedClient / EPMCClient         │  ← Domain interface
├─────────────────────────────────────┤
│   PubMedClientImpl / EPMCClientImpl │  ← Implementation
├─────────────────────────────────────┤
│   EgressGatewayClient (Feign)       │  ← Gateway abstraction
├─────────────────────────────────────┤
│   External APIs (PubMed, EPMC)      │  ← Remote services
└─────────────────────────────────────┘
```

### Design Pattern: **Fail-Fast Required Dependencies**

- **Egress Gateway API is required** (not optional)
- If `EgressGatewayClient` bean is missing, Spring fails at startup with clear error
- No silent no-op fallbacks (removed in v0.1.0)

**Why fail-fast?**
- ✅ Clear error messages at compile/startup time
- ✅ No silent failures with empty responses
- ✅ Simpler configuration (no conditional logic)
- ✅ Follows Spring Boot starter conventions

## 🔗 Dependencies

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-provenance</artifactId>
</dependency>
```

**Transitive dependencies** (automatically included):
- `patra-egress-gateway-api` (Feign client contracts)
- `patra-common` (base domain classes)
- Jackson (JSON/XML processing)
- Micrometer (optional, for metrics)

## 🔧 Auto-Configuration

### Feign Client Discovery

The `EgressGatewayClient` is **automatically discovered** via `patra-spring-cloud-starter-feign`, which scans all `@FeignClient` interfaces under the `com.patra` package.

**No manual configuration needed:**
```java
// ✅ Just add the provenance starter dependency
// EgressGatewayClient is automatically discovered and injected
@SpringBootApplication
public class MyApplication {}
```

### Beans Provided

| Bean | Type | Scope | Description |
|------|------|-------|-------------|
| `pubMedClient` | `PubMedClient` | Singleton | PubMed ESearch/EFetch client |
| `epmcClient` | `EPMCClient` | Singleton | Europe PMC search client |
| `provenanceMetrics` | `ProvenanceMetrics` | Singleton | Metrics recorder (if `MeterRegistry` available) |
| `gatewayRequestBuilder` | `GatewayRequestBuilder` | Singleton | Gateway request construction utility |
| `xmlToJsonConverter` | `XmlToJsonConverter` | Singleton | PubMed XML response converter |
| `EgressGatewayClient` | Feign proxy | Singleton | Auto-registered Feign client for egress gateway |

### Required Beans (Must Be Available)

- `EgressGatewayClient` (automatically registered via Feign client scanning)
- `ProvenanceProperties` (auto-configured from `application.yml`)

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
  public int estimateCount(String query, JsonNode pubmedParams) {
    ESearchRequest request = ASSEMBLER.buildCount(query, pubmedParams);
    ESearchResponse response = pubMedClient.esearch(request);
    return response != null && response.result() != null ? response.result().count() : 0;
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
    public int estimateCount(String term, JsonNode params) {
        ESearchRequest request = new ESearchRequest(
            "pubmed",
            term,
            null, null,  // retstart, retmax
            "json",
            "count",     // rettype for count-only
            null, null, null, null, null, null, null, null, null, null, null, null
        );

        ESearchResponse response = pubMedClient.esearch(request);
        return response != null && response.result() != null
            ? response.result().count()
            : 0;
    }
}
```

### Example: Europe PMC Search

```java
@Component
@RequiredArgsConstructor
public class EpmcSearchPortImpl implements EpmcSearchPort {

    private final EPMCClient epmcClient;  // Auto-injected

    @Override
    public List<Article> search(String query, int pageSize) {
        SearchRequest request = new SearchRequest(
            query,
            "json",
            1,  // page
            pageSize,
            "CORE"  // resultType
        );

        SearchResponse response = epmcClient.search(request);
        return convertToArticles(response);
    }
}
```

### Testing with Mock Gateway

```java
@SpringBootTest
@MockBean(EgressGatewayClient.class)  // Mock the gateway
class PubMedClientIntegrationTest {

    @Autowired
    private PubMedClient pubMedClient;  // Starter provides this

    @Autowired
    private EgressGatewayClient mockGateway;

    @Test
    void shouldCallPubMedViaGateway() {
        // Given: Mock gateway response
        when(mockGateway.call(any())).thenReturn(/* mock response */);

        // When: Call PubMed client
        ESearchResponse response = pubMedClient.esearch(request);

        // Then: Verify gateway was called
        verify(mockGateway).call(argThat(req ->
            req.getUrl().contains("eutils.ncbi.nlm.nih.gov")
        ));
    }
}
```

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
    public PubMedClient customPubMedClient(EgressGatewayClient gateway) {
        return new PubMedClientImpl(
            gateway,
            new GatewayRequestBuilder(),
            customConfigProvider(),
            new XmlToJsonConverter(),
            objectMapper(),
            null  // No metrics
        );
    }
}
```

## 🔄 Migration Guide

### From v0.0.x (Optional Dependency) → v0.1.0 (Required Dependency)

**Before (v0.0.x)**:
- `patra-egress-gateway-api` was optional
- No-op implementations returned empty results if gateway missing
- Silent failures at runtime

**After (v0.1.0)**:
- `patra-egress-gateway-api` is **required** (transitive)
- No no-op fallbacks (fail-fast at startup)
- Clear error messages

**Action Required**:
- ✅ No changes needed if `patra-egress-gateway` is already in your dependency tree
- ✅ For tests: Use `@MockBean(EgressGatewayClient.class)` to provide mock implementation
- ❌ If you relied on no-op behavior, you must now provide a real or mock gateway bean

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
- Gateway timeout → `ProvenanceClientException` with timeout details
- Rate limit exceeded → `ProvenanceClientException` with 429 status code
- Invalid API key → `ProvenanceClientException` with authentication error

## 🔗 Related Documentation

- [Main README](../README.md)
- [patra-egress-gateway](../patra-egress-gateway/README.md) - Gateway implementation
- [patra-ingest](../patra-ingest/README.md) - Consumer example
- [Architecture Guide](../docs/ARCHITECTURE.md) - System design patterns

---

**Last Updated**: 2025-10-14
