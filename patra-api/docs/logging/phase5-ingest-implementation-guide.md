# Phase 5 Implementation Guide: Ingest Service Trace Context Integration

**Version**: 1.0.0
**Status**: Implementation Guide
**Related Tasks**: T055 (Batch Jobs), T056 (Orchestrators), T057 (API Clients)

---

## Overview

This guide provides step-by-step instructions for integrating trace context propagation into the patra-ingest service. The logging starter infrastructure is already in place (T054 completed), and auto-configuration handles most trace propagation automatically. This guide focuses on **explicit correlation ID management** for batch operations and **trace-aware logging** patterns.

---

## Prerequisites

✅ **Already Complete:**
- `patra-spring-boot-starter-logging` dependency added to `patra-ingest-boot/pom.xml`
- Auto-configurations registered: `TraceContextFilter`, `TraceContextInterceptor`, `MdcTaskDecorator`
- Trace context automatically propagates for:
  - HTTP requests (via `TraceContextFilter`)
  - Feign calls (via `TraceContextInterceptor`)
  - RestTemplate calls (if configured with interceptor)
  - Async operations (via `MdcTaskDecorator`)

**What's Missing:**
- Explicit correlation ID generation for batch/job operations
- Trace context logging in orchestrators
- Verification that API clients use trace-propagating interceptors

---

## T055: Update XXL-Job Handlers with Correlation ID

### Objective

Generate and propagate **correlation IDs** for batch operations to group related logs across the entire batch lifecycle.

### Key Concept: Trace ID vs Correlation ID

- **Trace ID**: Identifies a single request flow (gateway → registry → ingest)
- **Correlation ID**: Identifies a business operation (e.g., entire PubMed harvest batch with 10,000 articles)

For scheduled jobs, **generate a new correlation ID** at job start and propagate it through all orchestrators and API calls.

---

### Implementation Pattern

#### 1. Generate Correlation ID in Job Handler

**File**: `patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/inbound/scheduler/job/PubmedHarvestJob.java`

**Current Code**:
```java
@Slf4j
@Component
public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {

  @XxlJob("pubmedHarvest")
  public void run() {
    String paramStr = XxlJobHelper.getJobParam();
    executeScheduleJob(paramStr);
  }
}
```

**Enhanced Code** (add correlation ID generation):
```java
@Slf4j
@Component
public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {

  private final LogContextEnricher logContextEnricher;

  public PubmedHarvestJob(
      PlanIngestionOrchestrator orchestrator,
      LogContextEnricher logContextEnricher) {
    super(orchestrator);
    this.logContextEnricher = logContextEnricher;
  }

  @XxlJob("pubmedHarvest")
  public void run() {
    // Generate correlation ID for this batch operation
    String correlationId = UUID.randomUUID().toString();

    // Add to MDC for all subsequent logs
    logContextEnricher.put("correlationId", correlationId);
    logContextEnricher.put("jobName", "pubmedHarvest");

    try {
      log.info("[INGEST][ADAPTER] Starting PubMed harvest job: correlationId={}", correlationId);

      String paramStr = XxlJobHelper.getJobParam();
      executeScheduleJob(paramStr);

      log.info("[INGEST][ADAPTER] Completed PubMed harvest job: correlationId={}", correlationId);
    } finally {
      // Clean up MDC
      logContextEnricher.remove("correlationId");
      logContextEnricher.remove("jobName");
    }
  }
}
```

**Key Points:**
- Generate `correlationId` at job entry
- Add to MDC via `logContextEnricher.put()`
- **All subsequent logs** automatically include `correlationId` in MDC pattern
- Clean up in `finally` block to prevent leakage

---

#### 2. Propagate Correlation ID to Orchestrators

**File**: `patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/PlanIngestionOrchestrator.java`

**Current Code**:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {

  @Override
  @Transactional
  public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
    log.info("[INGEST][APP] plan-ingest start, provenance={}, op={}, triggeredAt={}",
        command.getProvenanceCode(), command.getOperationCode(), command.getTriggeredAt());

    // ... orchestration logic ...
  }
}
```

**Enhanced Code** (correlation ID is already in MDC):
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {

  private final TraceContextHolder traceContextHolder;

  @Override
  @Transactional
  public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
    // Correlation ID is already in MDC from job handler
    // Just include it explicitly in key logs for clarity

    String correlationId = traceContextHolder.getCorrelationId().orElse("none");
    String traceId = traceContextHolder.getTraceId().orElse("none");

    log.info("[INGEST][APP] plan-ingest start, provenance={}, op={}, triggeredAt={}, traceId={}, correlationId={}",
        command.getProvenanceCode(),
        command.getOperationCode(),
        command.getTriggeredAt(),
        traceId,
        correlationId);

    // ... orchestration logic (correlation ID automatically in all logs via MDC) ...

    log.info("[INGEST][APP] plan-ingest success, planId={}, sliceCount={}, taskCount={}, correlationId={}",
        result.getPlanId(),
        result.getSliceCount(),
        result.getTaskCount(),
        correlationId);

    return result;
  }
}
```

**Key Points:**
- **No explicit propagation needed** - MDC is thread-local and inherited
- Optionally log `correlationId` explicitly for important milestones
- Access via `TraceContextHolder.getCorrelationId()` if needed

---

#### 3. Alternative: Pass Correlation ID via Command

If you prefer explicit parameter passing (more traceable in code):

**Command Object**:
```java
public class PlanIngestionCommand {
  private String correlationId;  // Add this field
  private ProvenanceCode provenanceCode;
  private OperationCode operationCode;
  // ... other fields
}
```

**Job Handler**:
```java
@XxlJob("pubmedHarvest")
public void run() {
  String correlationId = UUID.randomUUID().toString();
  logContextEnricher.put("correlationId", correlationId);

  PlanIngestionCommand command = PlanIngestionCommand.builder()
      .correlationId(correlationId)  // Pass explicitly
      .provenanceCode(ProvenanceCode.PUBMED)
      .operationCode(OperationCode.HARVEST)
      .build();

  orchestrator.ingestPlan(command);
}
```

**Orchestrator**:
```java
public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
  // Use command.getCorrelationId() instead of MDC
  log.info("[INGEST][APP] plan-ingest start, correlationId={}", command.getCorrelationId());
}
```

**Trade-off**: More explicit but requires command object changes.

---

### Files to Update (T055)

1. **AbstractProvenanceScheduleJob.java** - Add correlation ID generation in base class
2. **PubmedHarvestJob.java** - Override to set correlation ID in MDC
3. **OutboxRelayJob.java** - Add correlation ID for relay operations
4. All orchestrators called from jobs - Optionally log correlation ID explicitly

---

## T056: Update Orchestrators with Trace Context

### Objective

Ensure orchestrators **log trace context** explicitly at key decision points for troubleshooting.

---

### Implementation Pattern

#### Before (Current)
```java
@Slf4j
@Service
public class PlanIngestionOrchestrator {

  public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
    log.info("[INGEST][APP] plan-ingest start, provenance={}", command.getProvenanceCode());

    // Phase 1: Load provenance config
    ProvenanceConfigSnapshot config = registryPort.fetchConfig(
        command.getProvenanceCode(),
        command.getOperationCode()
    );

    // Phase 2: Resolve planning window
    PlanningWindow window = windowResolver.resolve(config);

    // ... more phases ...
  }
}
```

#### After (Trace-Aware)
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator {

  private final TraceContextHolder traceContextHolder;

  public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
    // Explicitly log trace context at orchestrator entry
    logTraceContext("plan-ingest start", command.getProvenanceCode());

    // Phase 1: Load provenance config
    log.debug("[INGEST][APP] Phase 1: Loading provenance config for {}", command.getProvenanceCode());
    ProvenanceConfigSnapshot config = registryPort.fetchConfig(
        command.getProvenanceCode(),
        command.getOperationCode()
    );

    // Phase 2: Resolve planning window
    log.debug("[INGEST][APP] Phase 2: Resolving planning window");
    PlanningWindow window = windowResolver.resolve(config);
    log.debug("[INGEST][APP] Window resolved: start={}, end={}", window.getStart(), window.getEnd());

    // ... more phases with DEBUG logging ...

    logTraceContext("plan-ingest success", result.getPlanId());
    return result;
  }

  /**
   * Helper method to log trace context explicitly.
   */
  private void logTraceContext(String operation, Object... params) {
    String traceId = traceContextHolder.getTraceId().orElse("none");
    String correlationId = traceContextHolder.getCorrelationId().orElse("none");

    log.info("[INGEST][APP] {} | traceId={}, correlationId={}, params={}",
        operation, traceId, correlationId, Arrays.toString(params));
  }
}
```

**Key Points:**
- Add `TraceContextHolder` dependency
- Create `logTraceContext()` helper for consistent logging
- Log trace context at orchestrator entry/exit
- Add DEBUG logs for each phase (enables dynamic troubleshooting via Nacos)

---

### Files to Update (T056)

1. **PlanIngestionOrchestrator.java** - Add trace context logging
2. **OutboxRelayOrchestrator.java** - Add trace context logging
3. **TaskExecutionUseCaseImpl.java** - Add trace context logging
4. All command handlers - Add `TraceContextHolder` dependency

---

## T057: Update External API Clients with Trace Propagation

### Objective

Verify that external API clients (PubMed, Registry) propagate trace headers in HTTP requests.

---

### Auto-Configuration Status

**Good News**: Trace propagation is **mostly automatic** if clients use Feign or RestTemplate with interceptors.

#### Feign Clients (Auto-Configured)

**File**: `patra-ingest-infra/src/main/java/com/patra/ingest/infra/rpc/ProvenancePortRpcAdapter.java`

```java
@RequiredArgsConstructor
public class ProvenancePortRpcAdapter implements PatraRegistryPort {

  private final ProvenanceClient provenanceClient;  // Feign client

  @Override
  public ProvenanceConfigSnapshot fetchConfig(ProvenanceCode code, OperationCode operation) {
    // Trace headers automatically added by TraceContextInterceptor (registered via auto-config)
    ProvenanceConfigResp resp = provenanceClient.getConfig(code.name(), operation.name());
    return converter.toSnapshot(resp);
  }
}
```

**Status**: ✅ **No changes needed** - `TraceContextInterceptor` is auto-registered for all Feign clients.

---

#### RestTemplate Clients (Manual Configuration Required)

**File**: `patra-common/src/main/java/com/patra/common/client/PubMedClient.java` (hypothetical)

**Current Code**:
```java
@Component
public class PubMedClient {

  private final RestTemplate restTemplate;

  public PubMedClient(RestTemplateBuilder builder) {
    this.restTemplate = builder.build();
  }

  public String searchArticles(String query) {
    return restTemplate.getForObject("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?term=" + query, String.class);
  }
}
```

**Enhanced Code** (add trace interceptor):
```java
@Component
public class PubMedClient {

  private final RestTemplate restTemplate;

  public PubMedClient(
      RestTemplateBuilder builder,
      RestTemplateInterceptor traceInterceptor) {  // Inject from logging starter

    this.restTemplate = builder
        .interceptors(traceInterceptor)  // Add trace propagation
        .build();
  }

  public String searchArticles(String query) {
    // Trace headers (X-Trace-Id, X-Correlation-Id) automatically added
    return restTemplate.getForObject("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?term=" + query, String.class);
  }
}
```

**Key Points:**
- Inject `RestTemplateInterceptor` from logging starter
- Add to `RestTemplateBuilder.interceptors()`
- All HTTP requests now include trace headers

---

### Verification

Add DEBUG logging to confirm trace headers are sent:

```java
log.debug("[INGEST][INFRA] Calling PubMed API: query={}, traceId={}",
    query,
    traceContextHolder.getTraceId().orElse("none"));
```

---

### Files to Update (T057)

1. **Identify RestTemplate usages** in infra layer:
   ```bash
   grep -r "RestTemplate" patra-ingest/patra-ingest-infra/src/main/java/
   ```

2. **Add `RestTemplateInterceptor`** to each RestTemplate configuration

3. **Feign clients**: ✅ Already configured (no changes)

4. **RocketMQ publisher**: ✅ Already propagates trace context via `RocketMQMessageListenerDecorator`

---

## Testing Trace Propagation

### Unit Test: Verify Trace Context in Logs

```java
@SpringBootTest
class PlanIngestionOrchestratorTest {

  @Autowired
  private PlanIngestionOrchestrator orchestrator;

  @Autowired
  private TraceContextHolder traceContextHolder;

  @Test
  void shouldIncludeTraceContextInLogs() {
    // Given: Set trace context
    DistributedTraceContext context = DistributedTraceContext.withCorrelation(
        "test-trace-123",
        "test-span-456",
        "test-correlation-789"
    );
    traceContextHolder.setContext(context);

    // When: Execute orchestrator
    PlanIngestionCommand command = PlanIngestionCommand.builder()
        .provenanceCode(ProvenanceCode.PUBMED)
        .operationCode(OperationCode.HARVEST)
        .build();

    orchestrator.ingestPlan(command);

    // Then: Verify trace context propagated (check logs manually or use log captor)
    assertThat(traceContextHolder.getTraceId()).contains("test-trace-123");
  }
}
```

---

### Integration Test: End-to-End Trace

```java
@SpringBootTest
@Testcontainers
class TraceContextIntegrationTest {

  @Test
  void shouldPropagateTraceAcrossServices() {
    // Given: Gateway sends request with trace headers
    String traceId = UUID.randomUUID().toString();

    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Trace-Id", traceId);
    headers.set("X-Correlation-Id", "batch-001");

    // When: Call ingest API
    ResponseEntity<String> response = restTemplate.exchange(
        "http://localhost:8082/ingest/plans",
        HttpMethod.POST,
        new HttpEntity<>(request, headers),
        String.class
    );

    // Then: Verify all logs contain same trace ID
    // (Search log aggregation tool or local logs)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
```

---

## Summary Checklist

### T055: XXL-Job Handlers ✅
- [ ] Update `AbstractProvenanceScheduleJob` to generate correlation ID
- [ ] Update `PubmedHarvestJob` to set correlation ID in MDC
- [ ] Update `OutboxRelayJob` to set correlation ID
- [ ] Inject `LogContextEnricher` dependency
- [ ] Clean up MDC in `finally` blocks

### T056: Orchestrators ✅
- [ ] Inject `TraceContextHolder` in all orchestrators
- [ ] Add `logTraceContext()` helper method
- [ ] Log trace context at orchestrator entry/exit
- [ ] Add DEBUG logs for each orchestration phase
- [ ] Test with dynamic log level changes (Nacos)

### T057: API Clients ✅
- [ ] Verify Feign clients use `TraceContextInterceptor` (auto-configured)
- [ ] Update RestTemplate beans to include `RestTemplateInterceptor`
- [ ] Add DEBUG logging for external API calls
- [ ] Test trace header propagation with Wireshark/proxy

---

## Expected Log Output (After Implementation)

### Gateway → Ingest → Registry Flow

```
# Gateway (entry point)
INFO  [patra-gateway] [service=patra-gateway][layer=adapter] [traceId=abc-123][correlationId=batch-001] Routing request to patra-ingest

# Ingest Adapter (job handler)
INFO  [patra-ingest] [service=patra-ingest][layer=adapter] [traceId=abc-123][correlationId=batch-001][jobName=pubmedHarvest] Starting PubMed harvest job

# Ingest App (orchestrator)
INFO  [patra-ingest] [service=patra-ingest][layer=app] [traceId=abc-123][correlationId=batch-001] plan-ingest start | traceId=abc-123, correlationId=batch-001, params=[PUBMED]
DEBUG [patra-ingest] [service=patra-ingest][layer=app] [traceId=abc-123][correlationId=batch-001] Phase 1: Loading provenance config for PUBMED

# Ingest Infra (external API call to registry)
INFO  [patra-ingest] [service=patra-ingest][layer=infra] [traceId=abc-123][correlationId=batch-001] Calling registry: provenanceCode=PUBMED

# Registry Adapter (receives request with trace headers)
INFO  [patra-registry] [service=patra-registry][layer=adapter] [traceId=abc-123][correlationId=batch-001] Received request: GET /provenance/config?code=PUBMED
```

**Key Points:**
- All logs have **same traceId** (abc-123)
- All logs have **same correlationId** (batch-001)
- Service and layer identifiers clearly show request flow
- Chronological ordering enables complete request reconstruction

---

## References

- [Log Level Guidelines](./log-level-guidelines.md)
- [Log Level Examples](./log-level-examples.md)
- [Troubleshooting Log Levels](./troubleshooting-log-levels.md)
- FR-003: Automatic trace context propagation
- FR-004: Trace context across async boundaries
- SC-002: 100% trace ID coverage
