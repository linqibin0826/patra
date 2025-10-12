# patra-spring-boot-starter-provenance

> Provenance configuration integration — auto-configures registry client and configuration snapshot loading.

## 📌 Purpose

Simplifies **provenance configuration** integration:
- Auto-configured `PatraRegistryPort` (Feign client)
- Configuration snapshot loading
- Cache support for provenance configs
- Retry policies for registry calls

## 🔧 Auto-Configurations

### Registry Client
- `ProvenanceClient` Feign bean auto-configured
- Retry policy: 3 attempts, exponential backoff
- Circuit breaker integration

### Configuration Loading
- Snapshot loading at plan creation time
- Temporal slicing support (`effectiveAt` parameter)
- Config hash generation for change detection

## 🔗 Dependencies

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-provenance</artifactId>
</dependency>
```

Includes: `patra-registry-api` (Feign client), `patra-spring-boot-starter-web`

## 🚀 Usage

### In patra-ingest
```java
@Component
@RequiredArgsConstructor
public class PatraRegistryPortImpl implements PatraRegistryPort {

    private final ProvenanceClient client;  // Auto-configured

    @Override
    public ProvenanceConfigSnapshot fetchConfig(ProvenanceCode code, OperationCode op) {
        ProvenanceConfigResp resp = client.getConfiguration(code, op.getCode(), Instant.now());
        return convertToSnapshot(resp);
    }
}
```

---

**Last Updated**: 2025-01-12
