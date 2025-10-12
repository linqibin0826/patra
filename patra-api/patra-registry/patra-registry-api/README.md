# patra-registry-api — Registry API Contracts

> **API module** defining the public contract for the registry service — DTOs, endpoints, Feign clients for provenance and expression metadata.

---

## 📌 Purpose

`patra-registry-api` provides the **external API contract** for querying the registry service from other microservices. It contains:

1. **Provenance DTOs**: Metadata and operational configurations
2. **Expression DTOs**: Expression fields, render rules, API param mappings
3. **Dictionary DTOs**: Dictionary items, types, validations
4. **Endpoint Interfaces**: Spring MVC endpoint definitions
5. **Feign Clients**: Ready-to-use Feign clients for RPC integration
6. **Error Codes**: Standardized error handling

**Why separate API module?**
- **Decoupling**: Consumers depend only on contracts, not implementation
- **Versioning**: API contracts evolve independently
- **Type Safety**: Compile-time verification of RPC calls

---

## 🗂️ Module Structure

```
patra-registry-api/
└─ src/main/java/.../api/
   ├─ rpc/
   │  ├─ dto/
   │  │  ├─ provenance/              # Provenance domain DTOs
   │  │  │  ├─ ProvenanceResp.java       # Provenance metadata
   │  │  │  ├─ ProvenanceConfigResp.java # Aggregated config (7 dimensions)
   │  │  │  ├─ HttpConfigResp.java       # HTTP config
   │  │  │  ├─ RetryConfigResp.java      # Retry config
   │  │  │  ├─ RateLimitConfigResp.java  # Rate limit config
   │  │  │  ├─ PaginationConfigResp.java # Pagination config
   │  │  │  ├─ BatchingConfigResp.java   # Batching config
   │  │  │  └─ WindowOffsetResp.java     # Window offset config
   │  │  │
   │  │  ├─ expr/                    # Expression domain DTOs
   │  │  │  ├─ ExprSnapshotResp.java     # Expression snapshot
   │  │  │  ├─ ExprFieldResp.java        # Field definition
   │  │  │  ├─ ExprRenderRuleResp.java   # Render rule
   │  │  │  ├─ ApiParamMappingResp.java  # API param mapping
   │  │  │  └─ ExprCapabilityResp.java   # Expression capability
   │  │  │
   │  │  └─ dict/                    # Dictionary domain DTOs
   │  │     ├─ DictionaryItemResp.java   # Dictionary item
   │  │     ├─ DictionaryTypeResp.java   # Dictionary type
   │  │     ├─ DictionaryHealthResp.java # Health status
   │  │     ├─ DictionaryValidationResp.java
   │  │     └─ DictionaryReferenceReq.java
   │  │
   │  ├─ endpoint/                   # Endpoint Interfaces
   │  │  ├─ ProvenanceEndpoint.java      # Provenance APIs
   │  │  └─ ExprEndpoint.java            # Expression APIs
   │  │
   │  └─ client/                     # Feign Clients
   │     ├─ ProvenanceClient.java        # Provenance RPC client
   │     └─ ExprClient.java              # Expression RPC client
   │
   └─ error/                         # Error Codes
      └─ RegistryErrorCode.java         # Standardized error codes
```

---

## 🔌 API Contracts

### 1. Provenance APIs

#### ProvenanceEndpoint

```java
public interface ProvenanceEndpoint {
    String BASE_PATH = "/_internal/provenances";

    @GetMapping(BASE_PATH)
    List<ProvenanceResp> listProvenances();

    @GetMapping(BASE_PATH + "/{code}")
    ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);

    @GetMapping(BASE_PATH + "/{code}/config")
    ProvenanceConfigResp getConfiguration(
        @PathVariable("code") ProvenanceCode code,
        @RequestParam(value = "operationType", required = false) String operationType,
        @RequestParam(value = "at", required = false) Instant at
    );
}
```

**3 Operations**:

1. **GET /_internal/provenances** - List all provenances
2. **GET /_internal/provenances/{code}** - Get single provenance by code
3. **GET /_internal/provenances/{code}/config?operationType=&at=** - Get aggregated configuration with **temporal slicing**

---

#### ProvenanceConfigResp (7 Config Dimensions)

```java
public record ProvenanceConfigResp(
    ProvenanceResp provenance,           // Baseline metadata
    WindowOffsetResp windowOffset,       // Window and offset config
    PaginationConfigResp pagination,     // Pagination/cursor config
    HttpConfigResp http,                 // HTTP baseline config
    BatchingConfigResp batching,         // Batching and request shaping
    RetryConfigResp retry,               // Retry and backoff config
    RateLimitConfigResp rateLimit        // Rate limiting and concurrency
) {}
```

**Temporal Slicing**:
- `operationType`: Filter by operation (HARVEST/UPDATE/COMPENSATION)
- `at`: Query configuration effective at this instant

**Example Request**:
```
GET /_internal/provenances/pubmed/config?operationType=HARVEST&at=2025-01-12T10:00:00Z
```

**Example Response**:
```json
{
  "provenance": {
    "code": "pubmed",
    "name": "PubMed",
    "baseUrl": "https://api.pubmed.gov",
    "status": "ACTIVE"
  },
  "windowOffset": {
    "maxWindowDays": 365,
    "offsetSeconds": 0
  },
  "pagination": {
    "type": "OFFSET",
    "defaultPageSize": 1000,
    "maxPageSize": 10000
  },
  "http": {
    "timeoutSeconds": 30,
    "connectTimeoutSeconds": 10,
    "headers": {
      "User-Agent": "Papertrace/1.0",
      "Accept": "application/json"
    }
  },
  "batching": {
    "batchSizeLimit": 1000,
    "batchTimeoutSeconds": 60
  },
  "retry": {
    "maxAttempts": 3,
    "backoffSeconds": 1,
    "maxBackoffSeconds": 10,
    "retryableCodes": [502, 503, 504]
  },
  "rateLimit": {
    "requestsPerSecond": 10,
    "burstSize": 20,
    "concurrentRequests": 5
  }
}
```

---

### 2. Expression APIs

#### ExprEndpoint

```java
public interface ExprEndpoint {
    String BASE_PATH = "/_internal/expr";

    @GetMapping(BASE_PATH + "/snapshot")
    ExprSnapshotResp getSnapshot(
        @RequestParam("provenanceCode") String provenanceCode,
        @RequestParam(value = "operationType", required = false) String operationType,
        @RequestParam(value = "endpointName", required = false) String endpointName,
        @RequestParam(value = "at", required = false) Instant at
    );
}
```

**1 Operation**:

**GET /_internal/expr/snapshot?provenanceCode=&operationType=&endpointName=&at=** - Get expression snapshot with temporal slicing

**Example Request**:
```
GET /_internal/expr/snapshot?provenanceCode=pubmed&operationType=HARVEST&endpointName=search&at=2025-01-12T10:00:00Z
```

**Example Response**:
```json
{
  "provenanceCode": "pubmed",
  "operationType": "HARVEST",
  "endpointName": "search",
  "fields": [
    {
      "fieldCode": "query",
      "fieldType": "STRING",
      "required": true,
      "defaultValue": null,
      "validation": {
        "pattern": ".*",
        "minLength": 1,
        "maxLength": 500
      }
    },
    {
      "fieldCode": "pageSize",
      "fieldType": "INTEGER",
      "required": false,
      "defaultValue": 1000
    }
  ],
  "apiParamMappings": [
    {
      "apiParamName": "q",
      "exprFieldCode": "query",
      "transformRule": "URL_ENCODE"
    },
    {
      "apiParamName": "pagesize",
      "exprFieldCode": "pageSize",
      "transformRule": "TO_STRING"
    }
  ],
  "renderRules": [
    {
      "templateName": "query_url",
      "template": "{baseUrl}/search?q={query}&pagesize={pageSize}",
      "outputFormat": "URL"
    }
  ],
  "capabilities": {
    "supportsCursor": false,
    "supportsOffset": true,
    "supportsBatching": true
  }
}
```

---

### 3. Feign Clients

#### ProvenanceClient

```java
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {
}
```

#### ExprClient

```java
@FeignClient(name = "patra-registry", contextId = "exprClient")
public interface ExprClient extends ExprEndpoint {
}
```

---

## 🚀 Usage Guide

### Step 1: Add Dependency

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-registry-api</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Step 2: Enable Feign Clients

```java
@SpringBootApplication
@EnableFeignClients(clients = {ProvenanceClient.class, ExprClient.class})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### Step 3: Use Provenance Client

```java
@Component
@RequiredArgsConstructor
public class PatraRegistryPortImpl implements PatraRegistryPort {

    private final ProvenanceClient provenanceClient;

    @Override
    public ProvenanceConfigSnapshot fetchConfig(ProvenanceCode code, OperationCode operation) {
        // Query with temporal slicing
        ProvenanceConfigResp resp = provenanceClient.getConfiguration(
            code,
            operation.getCode(),
            Instant.now()  // Get config effective now
        );

        // Convert to domain snapshot
        return convertToSnapshot(resp);
    }

    @Override
    public List<Provenance> listAllProvenances() {
        List<ProvenanceResp> respList = provenanceClient.listProvenances();
        return respList.stream()
            .map(this::convertToDomain)
            .toList();
    }
}
```

### Step 4: Use Expression Client

```java
@Component
@RequiredArgsConstructor
public class PlanExpressionBuilder {

    private final ExprClient exprClient;

    public PlanExpressionDescriptor build(PlanTriggerNorm norm, ProvenanceConfigSnapshot config) {
        // Query expression snapshot
        ExprSnapshotResp exprSnapshot = exprClient.getSnapshot(
            norm.provenanceCode(),
            norm.operationType(),
            "search",
            Instant.now()
        );

        // Build expression from snapshot
        Expr expr = buildExprFromSnapshot(exprSnapshot, norm);

        // Canonicalize
        ExprCanonicalSnapshot canonical = ExprCanonicalizer.canonicalize(expr);

        return new PlanExpressionDescriptor(
            canonical.hash(),
            canonical.canonicalJson()
        );
    }
}
```

---

## 📊 Key Concepts

### Temporal Configuration Slicing

**Problem**: Configuration changes mid-flight break running tasks.

**Solution**: Capture configuration snapshot at plan creation time.

```java
// At plan creation (2025-01-10)
ProvenanceConfigResp snapshot = provenanceClient.getConfiguration(
    ProvenanceCode.PUBMED,
    "HARVEST",
    Instant.parse("2025-01-10T00:00:00Z")
);

// Configuration effective on 2025-01-10 is captured
// Even if config changes on 2025-01-11, plan uses 2025-01-10 snapshot
```

**Benefits**:
- **Immutability**: Plans don't break when config changes
- **Audit Trail**: Know which config was used
- **Gradual Rollout**: A/B testing with time slices

### Config Dimension Precedence

**Scope precedence** (highest to lowest):
1. **TASK-level**: Most specific (operation + endpoint + task-specific)
2. **OPERATION-level**: Operation-specific (HARVEST vs UPDATE)
3. **SOURCE-level**: Default for all operations

**Example**:
- SOURCE-level: `rateLimit = 10`
- HARVEST-level: `rateLimit = 5`
- UPDATE-level: `rateLimit = 20`

```
HARVEST operations → use 5 req/s
UPDATE operations → use 20 req/s
Other operations → use 10 req/s (SOURCE default)
```

---

## ⚠️ Error Codes

### RegistryErrorCode

**Standardized error codes** following pattern: `REG-{segment}{number}`

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `REG-0400` | 400 | Bad Request |
| `REG-0404` | 404 | Provenance/Config Not Found |
| `REG-0422` | 422 | Unprocessable Entity (validation failed) |
| `REG-0500` | 500 | Internal Server Error |
| `REG-1001` | 404 | Provenance not found |
| `REG-1002` | 404 | Configuration not found |
| `REG-1003` | 422 | Configuration validation failed |
| `REG-1004` | 409 | Configuration conflict (duplicate) |

---

## 📋 Best Practices

### 1. Always Provide Temporal Context

```java
// ✅ Good: Specify temporal context
ProvenanceConfigResp config = provenanceClient.getConfiguration(
    code,
    operationType,
    Instant.now()  // Or plan.getCreatedAt()
);
```

```java
// ❌ Bad: Relying on default (current time)
ProvenanceConfigResp config = provenanceClient.getConfiguration(
    code,
    operationType,
    null  // Defaults to "now", but unclear
);
```

### 2. Cache Config Snapshots

```java
// ✅ Good: Capture snapshot once at plan creation
ProvenanceConfigSnapshot snapshot = fetchConfig(code, operation);
plan.setConfigSnapshotJson(toJson(snapshot));

// Later, tasks use captured snapshot
ProvenanceConfigSnapshot config = fromJson(plan.getConfigSnapshotJson());
```

```java
// ❌ Bad: Fetching config for every task
for (Task task : tasks) {
    ProvenanceConfigResp config = provenanceClient.getConfiguration(...);  // Network call!
}
```

### 3. Handle Not Found Gracefully

```java
// ✅ Good: Handle 404
try {
    ProvenanceConfigResp config = provenanceClient.getConfiguration(...);
} catch (FeignException.NotFound ex) {
    log.warn("Configuration not found for {} at {}", code, at);
    // Fall back to defaults or fail gracefully
}
```

### 4. Use Operation Type for Filtering

```java
// ✅ Good: Filter by operation type
ProvenanceConfigResp harvestConfig = provenanceClient.getConfiguration(
    ProvenanceCode.PUBMED,
    "HARVEST",  // Get HARVEST-specific config
    Instant.now()
);

ProvenanceConfigResp updateConfig = provenanceClient.getConfiguration(
    ProvenanceCode.PUBMED,
    "UPDATE",  // Get UPDATE-specific config
    Instant.now()
);
```

---

## 🔗 Related Documentation

- [patra-registry Service README](../README.md)
- [Main README](../../README.md)
- [Architecture Guide](../../docs/ARCHITECTURE.md)
- [Temporal Configuration Pattern](../../docs/ARCHITECTURE.md#43-temporal-configuration)

---

**Last Updated**: 2025-01-12
