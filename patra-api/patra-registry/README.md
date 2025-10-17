# patra-registry — SSOT Registry Service

> **Single Source of Truth** for provenance metadata, operational configurations, expressions, and dictionaries.

---

## 📌 Purpose

`patra-registry` serves as the **configuration hub** for the Papertrace platform, providing:

1. **Provenance Management**: Catalog of external data sources (PubMed, EPMC, Crossref, etc.)
2. **Operational Configurations**: HTTP policies, retry strategies, rate limits, pagination rules
3. **Expression Metadata**: API parameter mappings, field definitions, render rules
4. **Dictionary Management**: System dictionaries for codes and enums
5. **Temporal Slicing**: Time-effective configurations (config snapshots valid at specific instants)

All other services (especially `patra-ingest`) query this service via **Feign RPC** to retrieve configuration snapshots.

---

## 🏗️ Module Structure

```
patra-registry/
├─ patra-registry-api/              # External contracts (Feign clients, DTOs)
│  └─ src/main/java/.../api/
│     ├─ rpc/client/                # Feign client interfaces
│     │  ├─ ProvenanceClient.java   # Provenance API client
│     │  └─ ExprClient.java         # Expression API client
│     ├─ rpc/dto/                   # Response DTOs
│     │  ├─ provenance/             # Provenance-related DTOs
│     │  └─ expr/                   # Expression-related DTOs
│     └─ rpc/endpoint/              # Endpoint contracts
│
├─ patra-registry-domain/           # Pure Java domain model (NO frameworks)
│  └─ src/main/java/.../domain/
│     ├─ model/
│     │  ├─ aggregate/              # ProvenanceConfiguration (aggregate root)
│     │  ├─ vo/                     # Value objects (Provenance, *Config)
│     │  │  ├─ provenance/          # Provenance VOs
│     │  │  └─ expr/                # Expression VOs
│     │  └─ read/                   # Read models (CQRS queries)
│     │     ├─ provenance/          # Provenance queries
│     │     └─ expr/                # Expression queries
│     ├─ port/                      # Repository interfaces
│     │  ├─ ProvenanceConfigRepository.java
│     │  └─ ExprRepository.java
│     ├─ exception/                 # Domain exceptions
│     └─ support/                   # Domain support classes
│
├─ patra-registry-app/              # Application layer (orchestration)
│  └─ src/main/java/.../app/
│     ├─ service/                   # Application services
│     │  ├─ ProvenanceConfigAppService.java
│     │  └─ ExprQueryAppService.java
│     └─ converter/                 # Query assemblers (Domain → DTO)
│        ├─ ProvenanceQueryAssembler.java
│        └─ ExprQueryAssembler.java
│
├─ patra-registry-infra/            # Infrastructure layer (persistence)
│  └─ src/main/java/.../infra/
│     └─ persistence/
│        ├─ entity/                 # MyBatis-Plus entities (DOs)
│        │  ├─ provenance/          # RegProvenanceDO, RegProv*CfgDO
│        │  ├─ expr/                # RegProvExpr*DO
│        │  └─ dictionary/          # RegSysDict*DO
│        ├─ mapper/                 # MyBatis mappers
│        ├─ converter/              # DO ↔ Domain converters
│        └─ repository/             # Repository implementations
│           ├─ ProvenanceConfigRepositoryMpImpl.java
│           └─ ExprRepositoryMpImpl.java
│
├─ patra-registry-adapter/          # Adapter layer (inbound/outbound)
│  └─ src/main/java/.../adapter/
│     ├─ inbound/rest/feign/        # Feign endpoint implementations
│     │  ├─ ProvenanceClientImpl.java  # Implements ProvenanceClient
│     │  └─ ExprClientImpl.java        # Implements ExprClient
│     └─ config/                    # Error mapping contributors
│
└─ patra-registry-boot/             # Executable module
   └─ src/main/java/.../
      └─ PatraRegistryApplication.java  # Main class
```

---

## 🔑 Key Domain Concepts

### 1. Provenance

**Definition**: An external data source (e.g., PubMed, EPMC, Crossref).

**Attributes**:
- `code` (String): Unique stable identifier (e.g., `"pubmed"`, `"crossref"`)
- `name` (String): Display name (e.g., `"PubMed"`)
- `baseUrlDefault` (String): Default API base URL
- `timezoneDefault` (String): Default timezone (IANA format, e.g., `"UTC"`)
- `active` (boolean): Whether the source is active
- `lifecycleStatusCode` (String): Dictionary code for lifecycle state

**File**: [`patra-registry-domain/src/main/java/.../model/vo/provenance/Provenance.java`](patra-registry-domain/src/main/java/com/patra/registry/domain/model/vo/provenance/Provenance.java)

### 2. ProvenanceConfiguration (Aggregate Root)

**Definition**: Read-only aggregate combining `Provenance` with all operational configs.

**Structure**:
```java
public record ProvenanceConfiguration(
    Provenance provenance,              // Core metadata (NEVER null)
    WindowOffsetConfig windowOffset,    // Time window segmentation (nullable)
    PaginationConfig pagination,        // Pagination strategy (nullable)
    HttpConfig http,                    // HTTP client settings (nullable)
    BatchingConfig batching,            // Batching rules (nullable)
    RetryConfig retry,                  // Retry policy (nullable)
    RateLimitConfig rateLimit           // Rate limit config (nullable)
) { }
```

**Scope Precedence**: TASK-specific configs override SOURCE-level defaults.

**File**: [`patra-registry-domain/src/main/java/.../model/aggregate/ProvenanceConfiguration.java`](patra-registry-domain/src/main/java/com/patra/registry/domain/model/aggregate/ProvenanceConfiguration.java)

### 3. Temporal Configuration

All configs (except `Provenance` itself) have **effective time ranges**:

- `effective_from` (Instant): Config becomes valid
- `effective_until` (Instant): Config expires

**Query Pattern**:
```java
// Find config effective at specific time
Optional<HttpConfig> findActiveHttpConfig(
    Long provenanceId,
    String operationType,
    Instant at  // Query time
);
```

**Benefits**:
- Safe config updates without impacting running tasks
- A/B testing support
- Audit trail

### 4. Operational Configurations

| Config Type | Purpose | Key Fields |
|-------------|---------|------------|
| **WindowOffsetConfig** | Time window segmentation | `startOffsetDays`, `lookbackWindowDays` |
| **PaginationConfig** | Pagination strategy | `pageSize`, `maxPages`, `cursorField` |
| **HttpConfig** | HTTP client settings | `baseUrl`, `connectTimeout`, `readTimeout`, `headers` |
| **BatchingConfig** | Batching rules for detail fetching | `batchSize`, `maxConcurrentBatches` |
| **RetryConfig** | Retry policy | `maxRetries`, `backoffMillis`, `retryableStatusCodes` |
| **RateLimitConfig** | Rate limiting | `requestsPerSecond`, `burstCapacity` |

### 5. Expression Metadata

**Definition**: Metadata for dynamic API parameter mapping (used by `patra-expr-kernel`).

**Components**:
- **ExprCapability**: Defines operation capabilities (e.g., `HARVEST`, `UPDATE`)
- **ApiParamMapping**: Maps logical params to API query params
- **ExprField**: Field definitions (data types, constraints)
- **ExprRenderRule**: Rules for rendering expressions into API queries

**Example Use Case**: Map `dateFrom`/`dateTo` logical params to PubMed's `mindate`/`maxdate` query params.

---

## 🔌 API Contracts

### Internal RPC API (Feign)

#### ProvenanceClient

**Base Path**: `/internal/provenance`

**Endpoints**:
```java
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient {

    // List all provenances
    @GetMapping("/list")
    List<ProvenanceResp> listProvenances();

    // Get single provenance by code
    @GetMapping("/{code}")
    ProvenanceResp getProvenance(@PathVariable ProvenanceCode code);

    // Load full configuration (with temporal slicing)
    @GetMapping("/{code}/config")
    ProvenanceConfigResp getConfiguration(
        @PathVariable ProvenanceCode code,
        @RequestParam(required = false) String operationType,
        @RequestParam(required = false) Instant at
    );
}
```

**Response DTOs**:
- `ProvenanceResp`: Basic provenance metadata
- `ProvenanceConfigResp`: Full configuration aggregate (with all nested configs)

#### ExprClient

**Base Path**: `/internal/expr`

**Endpoints**:
```java
@FeignClient(name = "patra-registry", contextId = "exprClient")
public interface ExprClient {

    // Get expression capabilities for provenance
    @GetMapping("/{provenanceCode}/capabilities")
    List<ExprCapabilityResp> getCapabilities(@PathVariable String provenanceCode);

    // Get API param mappings
    @GetMapping("/{provenanceCode}/param-mappings")
    List<ApiParamMappingResp> getParamMappings(@PathVariable String provenanceCode);
}
```

---

## 🛠️ How to Extend

### Adding a New Configuration Type

**Example**: Add `CacheConfig` for caching policies.

#### Step 1: Define Domain VO

```java
// patra-registry-domain/model/vo/provenance/CacheConfig.java
package com.patra.registry.domain.model.vo.provenance;

public record CacheConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Integer ttlSeconds,
    Integer maxEntries,
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

#### Step 2: Define Read Model

```java
// patra-registry-domain/model/read/provenance/CacheConfigQuery.java
package com.patra.registry.domain.model.read.provenance;

public record CacheConfigQuery(
    Integer ttlSeconds,
    Integer maxEntries
) { }
```

#### Step 3: Add to Aggregate

```java
// ProvenanceConfiguration.java
public record ProvenanceConfiguration(
    Provenance provenance,
    WindowOffsetConfig windowOffset,
    PaginationConfig pagination,
    HttpConfig http,
    BatchingConfig batching,
    RetryConfig retry,
    RateLimitConfig rateLimit,
    CacheConfig cache  // NEW
) { }
```

#### Step 4: Add Repository Port Method

```java
// ProvenanceConfigRepository.java
Optional<CacheConfig> findActiveCache(Long provenanceId, String operationType, Instant at);
```

#### Step 5: Create DO and Mapper

```java
// RegProvCacheCfgDO.java
@TableName("reg_prov_cache_cfg")
public class RegProvCacheCfgDO { ... }

// RegProvCacheCfgMapper.java
@Mapper
public interface RegProvCacheCfgMapper extends BaseMapper<RegProvCacheCfgDO> { }
```

#### Step 6: Implement Repository Method

```java
// ProvenanceConfigRepositoryMpImpl.java
@Override
public Optional<CacheConfig> findActiveCache(Long provenanceId, String operationType, Instant at) {
    // Query with temporal filtering (effective_from <= at < effective_until)
    // ...
}
```

#### Step 7: Update DTO and Converter

```java
// ProvenanceConfigResp.java (in api module)
public record ProvenanceConfigResp(
    ProvenanceResp provenance,
    WindowOffsetResp windowOffset,
    // ...
    CacheConfigResp cache  // NEW
) { }
```

---

## 🔍 Example: Configuration Query Flow

### Scenario

`patra-ingest` needs to fetch PubMed configuration for a HARVEST operation at `2025-01-12T10:00:00Z`.

### Sequence

```
1. patra-ingest calls:
   provenanceClient.getConfiguration(PUBMED, "HARVEST", Instant.parse("2025-01-12T10:00:00Z"))

   ↓

2. ProvenanceClientImpl.getConfiguration()
   - Calls ProvenanceConfigAppService.loadConfiguration()

   ↓

3. ProvenanceConfigAppService
   - repository.findProvenanceByCode(PUBMED)
   - repository.loadConfiguration(provenanceId, "HARVEST", at)

   ↓

4. ProvenanceConfigRepositoryMpImpl.loadConfiguration()
   - Query provenance (reg_provenance)
   - Query windowOffset (reg_prov_window_offset_cfg WHERE effective_from <= at < effective_until)
   - Query pagination (reg_prov_pagination_cfg ...)
   - Query http (reg_prov_http_cfg ...)
   - Query batching (reg_prov_batching_cfg ...)
   - Query retry (reg_prov_retry_cfg ...)
   - Query rateLimit (reg_prov_rate_limit_cfg ...)
   - Assemble ProvenanceConfiguration aggregate

   ↓

5. ProvenanceQueryAssembler.toQuery()
   - Convert domain aggregate → ProvenanceConfigQuery

   ↓

6. ProvenanceApiConverter.toResp()
   - Convert query DTO → ProvenanceConfigResp (API contract)

   ↓

7. Return to patra-ingest as ProvenanceConfigResp JSON
```

---

## 🗄️ Database Schema Overview

### Tables

| Table | Purpose |
|-------|---------|
| `reg_provenance` | Core provenance catalog |
| `reg_prov_window_offset_cfg` | Window offset configs (temporal) |
| `reg_prov_pagination_cfg` | Pagination configs (temporal) |
| `reg_prov_http_cfg` | HTTP client configs (temporal) |
| `reg_prov_batching_cfg` | Batching configs (temporal) |
| `reg_prov_retry_cfg` | Retry policies (temporal) |
| `reg_prov_rate_limit_cfg` | Rate limit configs (temporal) |
| `reg_prov_expr_capability` | Expression capabilities |
| `reg_prov_api_param_map` | API parameter mappings |
| `reg_expr_field_dict` | Expression field definitions |
| `reg_prov_expr_render_rule` | Expression render rules |
| `reg_sys_dict_type` | Dictionary types |
| `reg_sys_dict_item` | Dictionary items |
| `reg_sys_dict_item_alias` | Dictionary item aliases |

> `reg_prov_window_offset_cfg.offset_field_key` and `reg_prov_window_offset_cfg.window_date_field_key` store unified
> semantic keys (`std_key` matching `reg_expr_field_dict.field_key`) rather than provider-specific parameter names.

**Key Relationships**:
- All `reg_prov_*_cfg` tables have FK → `reg_provenance.id`
- All temporal configs have `effective_from` and `effective_until` columns
- Scope is determined by `operation_type` (NULL = SOURCE-level default)

---

## 🧪 Testing

### Unit Tests (Domain)

```bash
mvn test -pl patra-registry-domain
```

**Focus**: Value object validation, aggregate invariants.

### Integration Tests (Repository)

```bash
mvn verify -pl patra-registry-infra
```

**Focus**: Temporal queries, MyBatis mappers, DO ↔ Domain conversion.

### API Tests (Adapter)

```bash
mvn verify -pl patra-registry-adapter
```

**Focus**: Feign endpoint contracts, error mapping.

---

## 📊 Observability

### Logs

- **INFO**: High-level operations (e.g., "Loading provenance config for PUBMED")
- **DEBUG**: Query details (e.g., "Found 3 active configs for provenanceId=1")
- **ERROR**: Failures (e.g., "Provenance not found: code=INVALID")

### 🪵 Logging (Starter v1.0)

`patra-registry` integrates `patra-spring-boot-starter-logging` to provide:
- Trace context propagation (traceId/correlationId in MDC) via `TraceContextFilter`
- Sensitive data sanitization via `LogSanitizer`
- Dynamic log levels via Nacos (≤60s)

Minimal setup (already applied):
```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-spring-boot-starter-logging</artifactId>
</dependency>
```

application.yml:
```yaml
spring:
  application.name: patra-registry

papertrace.logging.trace.enabled: true
```

Adapter example (sanitize + business MDC):
```java
@RestController
@Slf4j
public class ProvenanceController {
  @Autowired LogSanitizer sanitizer;
  @Autowired LogContextEnricher enricher;

  @GetMapping("/provenance/{code}")
  public ResponseEntity<?> get(@PathVariable String code) {
    enricher.enrich("operation", "GET_PROVENANCE");
    try {
      log.info("Load provenance: code={}", sanitizer.sanitize(code));
      // delegate to app layer
      return ResponseEntity.ok().build();
    } finally {
      enricher.clearEnriched();
    }
  }
}
```

Dynamic levels (Nacos `logging-patra-registry.yml`):
```yaml
logging.level:
  root: INFO
  com.patra.registry.app: DEBUG
  com.patra.registry.infra: DEBUG
```

References: docs/logging/operations-guide.md, specs/001-logging-starter/quickstart.md

### Metrics (Planned)

- `provenance.config.query.duration` (histogram)
- `provenance.config.cache.hit_rate` (gauge)
- `provenance.config.temporal.slices_active` (gauge)

---

## 🚀 Running Locally

```bash
# Start MySQL
docker-compose up -d mysql

# Run migrations (if applicable)
# ...

# Start service
cd patra-registry/patra-registry-boot
mvn spring-boot:run
```

**Default Port**: 8081

---

## 🔗 Related Documentation

- [Main README](../README.md)
- [Architecture Guide](../docs/ARCHITECTURE.md)
- [Development Guide](../docs/DEV-GUIDE.md)
- [patra-ingest README](../patra-ingest/README.md)

---

**Last Updated**: 2025-01-12
