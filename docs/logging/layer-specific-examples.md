# Layer-Specific Logging Examples

**Feature**: Enhanced Logging System | **Phase**: 6 - User Story 4 | **Date**: 2025-10-17

## Overview

This guide provides concrete logging examples organized by Hexagonal Architecture layer. Each example demonstrates proper log levels, trace context usage, sanitization, and architectural boundaries.

---

## Table of Contents

1. [Adapter Layer](#adapter-layer)
2. [Application Layer](#application-layer)
3. [Domain Layer](#domain-layer)
4. [Infrastructure Layer](#infrastructure-layer)
5. [Cross-Cutting Concerns](#cross-cutting-concerns)

---

## Adapter Layer

**Responsibilities**: External interface (HTTP, MQ, Jobs), request validation, trace context enrichment, MDC management

**Logger Pattern**: `@Slf4j` (Lombok)

### Example 1: REST Controller (Web Entry Point)

```java
package com.patra.registry.adapter.web;

import com.patra.common.logging.sanitizer.LogSanitizer;
import com.patra.common.logging.context.LogContextEnricher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/provenance")
@Slf4j
public class ProvenanceController {

    private final LogSanitizer sanitizer;
    private final LogContextEnricher enricher;
    private final ProvenanceQueryOrchestrator orchestrator;

    public ProvenanceController(
            LogSanitizer sanitizer,
            LogContextEnricher enricher,
            ProvenanceQueryOrchestrator orchestrator) {
        this.sanitizer = sanitizer;
        this.enricher = enricher;
        this.orchestrator = orchestrator;
    }

    /**
     * Fetch provenance configuration by source.
     *
     * Logging strategy:
     * - INFO: Request received, response sent (key business events)
     * - DEBUG: Detailed request parameters (may be high-frequency)
     * - WARN: Validation failures, not found scenarios
     * - ERROR: Unexpected exceptions
     */
    @GetMapping("/{source}")
    public ResponseEntity<ProvenanceDTO> getBySource(
            @PathVariable String source,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        // Enrich MDC with business context (cleaned up in finally)
        enricher.enrich("operation", "QUERY_PROVENANCE");
        enricher.enrich("userId", sanitizer.sanitize(userId));

        try {
            // INFO: Key business event - always logged
            log.info("Fetching provenance: source={}", sanitizer.sanitize(source));

            // DEBUG: Detailed context (may be sampled under high load)
            log.debug("Request details: source={}, userId={}, traceId={}",
                    sanitizer.sanitize(source), sanitizer.sanitize(userId),
                    MDC.get("traceId"));

            // Delegate to application layer (orchestrator)
            ProvenanceDTO result = orchestrator.queryBySource(source);

            // INFO: Success outcome
            log.info("Provenance fetched successfully: source={}, id={}",
                    source, result.getId());

            return ResponseEntity.ok(result);

        } catch (ProvenanceNotFoundException e) {
            // WARN: Expected business exception (client error)
            log.warn("Provenance not found: source={}", source);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Provenance not found", e);

        } catch (Exception e) {
            // ERROR: Unexpected system error
            log.error("Failed to fetch provenance: source={}", source, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal error", e);

        } finally {
            // CRITICAL: Clean up MDC to prevent memory leak
            enricher.clearEnriched();
        }
    }

    /**
     * Create new provenance configuration.
     *
     * Logging strategy:
     * - INFO: Creation request and success
     * - DEBUG: Full request body (sanitized)
     * - WARN: Validation failures
     * - ERROR: Creation failures
     */
    @PostMapping
    public ResponseEntity<ProvenanceDTO> create(
            @Valid @RequestBody CreateProvenanceRequest request) {

        enricher.enrich("operation", "CREATE_PROVENANCE");

        try {
            log.info("Creating provenance: source={}, name={}",
                    sanitizer.sanitize(request.getSource()),
                    sanitizer.sanitize(request.getName()));

            // DEBUG: Full request details (may contain sensitive data)
            log.debug("Creation request: {}", sanitizer.sanitizeObject(request));

            ProvenanceDTO result = orchestrator.create(request);

            log.info("Provenance created successfully: id={}, source={}",
                    result.getId(), result.getSource());

            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (ValidationException e) {
            log.warn("Provenance validation failed: source={}, errors={}",
                    request.getSource(), e.getErrors());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Validation failed", e);

        } catch (Exception e) {
            log.error("Failed to create provenance: source={}",
                    request.getSource(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal error", e);

        } finally {
            enricher.clearEnriched();
        }
    }
}
```

**Key Patterns:**
- ✅ Use `@Slf4j` for Lombok logger injection
- ✅ INFO for request/response key events
- ✅ Sanitize all user input before logging
- ✅ Enrich MDC with operation context
- ✅ Clean up MDC in `finally` block
- ✅ Delegate business logic to orchestrator

---

### Example 2: Scheduled Job (Batch Processing)

```java
package com.patra.ingest.adapter.job;

import com.patra.common.logging.context.DistributedTraceContext;
import com.patra.common.logging.context.TraceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@Slf4j
public class DailyIngestJob {

    private final TraceContextHolder traceContextHolder;
    private final IngestBatchOrchestrator orchestrator;

    public DailyIngestJob(
            TraceContextHolder traceContextHolder,
            IngestBatchOrchestrator orchestrator) {
        this.traceContextHolder = traceContextHolder;
        this.orchestrator = orchestrator;
    }

    /**
     * Daily batch job to ingest new articles from all configured sources.
     *
     * Logging strategy:
     * - INFO: Job start/end, summary metrics (total, success, errors)
     * - DEBUG: Per-source progress (may be high-frequency)
     * - WARN: Individual source failures (job continues)
     * - ERROR: Job-level failures (entire job aborted)
     */
    @Scheduled(cron = "${papertrace.ingest.daily-job.cron:0 0 2 * * ?}")
    public void executeDailyIngest() {
        // Generate batch-level trace context (no upstream request)
        String batchId = generateBatchId();
        DistributedTraceContext context = traceContextHolder.withCorrelationId(batchId);
        traceContextHolder.populateMDC(context);

        // Add batch metadata to MDC
        MDC.put("batchId", batchId);
        MDC.put("jobType", "DAILY_INGEST");

        try {
            // INFO: Job start (always logged)
            log.info("Daily ingest job started: batchId={}", batchId);

            // Delegate to orchestrator
            BatchResult result = orchestrator.executeDaily(batchId);

            // INFO: Job completion with metrics
            log.info("Daily ingest job completed: batchId={}, duration={}ms, " +
                    "sources={}, records={}, errors={}",
                    batchId, result.getDuration(), result.getSourceCount(),
                    result.getRecordCount(), result.getErrorCount());

            // WARN: Alert if error rate exceeds threshold
            if (result.getErrorRate() > 0.1) {  // >10% error rate
                log.warn("High error rate detected: batchId={}, errorRate={:.2f}%",
                        batchId, result.getErrorRate() * 100);
            }

        } catch (Exception e) {
            // ERROR: Job-level failure
            log.error("Daily ingest job failed: batchId={}", batchId, e);
            // Optionally notify operations team
            throw e;

        } finally {
            // CRITICAL: Clean up MDC
            MDC.remove("batchId");
            MDC.remove("jobType");
            traceContextHolder.clearMDC();
        }
    }

    private String generateBatchId() {
        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return "daily-ingest-" + timestamp + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
```

**Key Patterns:**
- ✅ Generate trace context for batch (no upstream request)
- ✅ Use correlation ID as batch ID for grouping
- ✅ Log summary metrics at INFO level
- ✅ Clean up MDC in `finally` block
- ✅ WARN for high error rates

---

### Example 3: Message Listener (Event-Driven)

```java
package com.patra.ingest.adapter.mq;

import com.patra.common.logging.context.DistributedTraceContext;
import com.patra.common.logging.context.TraceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RocketMQMessageListener(
    topic = "provenance-updated-events",
    consumerGroup = "ingest-provenance-consumer"
)
public class ProvenanceUpdatedListener implements RocketMQListener<ProvenanceUpdatedEvent> {

    private final TraceContextHolder traceContextHolder;
    private final ProvenanceUpdateOrchestrator orchestrator;

    public ProvenanceUpdatedListener(
            TraceContextHolder traceContextHolder,
            ProvenanceUpdateOrchestrator orchestrator) {
        this.traceContextHolder = traceContextHolder;
        this.orchestrator = orchestrator;
    }

    /**
     * Handle provenance updated events from registry service.
     *
     * Logging strategy:
     * - INFO: Event received, processing completed
     * - DEBUG: Event details (may be high-frequency)
     * - WARN: Processing retries
     * - ERROR: Processing failures (DLQ)
     */
    @Override
    public void onMessage(ProvenanceUpdatedEvent event) {
        // Extract trace context from message headers
        String traceId = event.getTraceId();
        String correlationId = event.getCorrelationId();
        DistributedTraceContext context = DistributedTraceContext.withCorrelation(
                traceId != null ? traceId : UUID.randomUUID().toString(),
                correlationId
        );
        traceContextHolder.populateMDC(context);

        // Enrich MDC with event metadata
        MDC.put("eventType", "PROVENANCE_UPDATED");
        MDC.put("source", event.getSource());
        MDC.put("version", String.valueOf(event.getVersion()));

        try {
            // INFO: Event received
            log.info("Provenance updated event received: source={}, version={}",
                    event.getSource(), event.getVersion());

            // DEBUG: Full event details
            log.debug("Event payload: eventId={}, timestamp={}, changeType={}",
                    event.getEventId(), event.getTimestamp(), event.getChangeType());

            // Delegate to orchestrator
            orchestrator.handleUpdate(event);

            // INFO: Processing completed
            log.info("Provenance updated event processed: source={}, version={}",
                    event.getSource(), event.getVersion());

        } catch (RetryableException e) {
            // WARN: Transient error, will retry
            log.warn("Provenance update processing failed (retryable): source={}, " +
                    "version={}, error={}", event.getSource(), event.getVersion(),
                    e.getMessage());
            throw e;  // Trigger RocketMQ retry

        } catch (Exception e) {
            // ERROR: Non-retryable error, send to DLQ
            log.error("Provenance update processing failed: source={}, version={}",
                    event.getSource(), event.getVersion(), e);
            // Don't re-throw, let RocketMQ send to DLQ

        } finally {
            // CRITICAL: Clean up MDC
            MDC.remove("eventType");
            MDC.remove("source");
            MDC.remove("version");
            traceContextHolder.clearMDC();
        }
    }
}
```

**Key Patterns:**
- ✅ Extract trace context from message headers
- ✅ Generate fallback trace ID if missing
- ✅ Enrich MDC with event metadata
- ✅ WARN for retryable errors, ERROR for DLQ
- ✅ Clean up MDC in `finally` block

---

## Application Layer

**Responsibilities**: Use case orchestration, transaction boundaries, cross-aggregate coordination

**Logger Pattern**: `@Slf4j` (Lombok)

### Example 1: Command Orchestrator

```java
package com.patra.registry.app.provenance.create;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class CreateProvenanceOrchestrator {

    private final ProvenanceRepository provenanceRepository;
    private final DictionaryRepository dictionaryRepository;
    private final ProvenanceCreatedEventPublisher eventPublisher;

    public CreateProvenanceOrchestrator(
            ProvenanceRepository provenanceRepository,
            DictionaryRepository dictionaryRepository,
            ProvenanceCreatedEventPublisher eventPublisher) {
        this.provenanceRepository = provenanceRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Create new provenance configuration.
     *
     * Logging strategy:
     * - INFO: Orchestration start/end, key decisions
     * - DEBUG: Detailed orchestration steps
     * - WARN: Business rule violations (handled gracefully)
     * - ERROR: Orchestration failures (transaction rollback)
     */
    public CreateProvenanceResult execute(CreateProvenanceCommand command) {
        // Trace context already in MDC from adapter layer

        // INFO: Orchestration start
        log.info("Creating provenance: source={}, apiVersion={}",
                command.getSource(), command.getApiVersion());

        // DEBUG: Command details
        log.debug("Command details: dictionaries={}, endpoints={}",
                command.getDictionaries().size(), command.getEndpoints().size());

        try {
            // Step 1: Validate uniqueness
            if (provenanceRepository.existsBySource(command.getSource())) {
                log.warn("Provenance already exists: source={}", command.getSource());
                throw new ProvenanceDuplicateException(command.getSource());
            }

            // Step 2: Load referenced dictionaries
            log.debug("Loading dictionaries: count={}", command.getDictionaries().size());
            List<Dictionary> dictionaries = loadDictionaries(command.getDictionaries());

            // Step 3: Create domain aggregate
            log.debug("Creating provenance aggregate");
            Provenance provenance = Provenance.create(
                    command.getSource(),
                    command.getApiVersion(),
                    command.getEndpoints(),
                    dictionaries
            );

            // Step 4: Persist aggregate
            log.debug("Persisting provenance: source={}", provenance.getSource());
            provenanceRepository.save(provenance);

            // Step 5: Publish domain event
            log.debug("Publishing provenance created event");
            ProvenanceCreatedEvent event = ProvenanceCreatedEvent.from(provenance);
            eventPublisher.publish(event);

            // INFO: Success outcome
            log.info("Provenance created successfully: id={}, source={}, version={}",
                    provenance.getId(), provenance.getSource(), provenance.getVersion());

            return CreateProvenanceResult.from(provenance);

        } catch (DictionaryNotFoundException e) {
            // WARN: Business validation failure
            log.warn("Dictionary not found: dictionaryId={}", e.getDictionaryId());
            throw new ValidationException("Referenced dictionary not found", e);

        } catch (Exception e) {
            // ERROR: Unexpected orchestration failure (transaction will rollback)
            log.error("Failed to create provenance: source={}", command.getSource(), e);
            throw new OrchestrationException("Provenance creation failed", e);
        }
    }

    private List<Dictionary> loadDictionaries(List<Long> dictionaryIds) {
        // DEBUG: Fine-grained step logging
        log.debug("Loading {} dictionaries", dictionaryIds.size());

        List<Dictionary> dictionaries = dictionaryRepository.findAllById(dictionaryIds);

        if (dictionaries.size() != dictionaryIds.size()) {
            log.warn("Some dictionaries not found: expected={}, found={}",
                    dictionaryIds.size(), dictionaries.size());
            throw new DictionaryNotFoundException(findMissingIds(dictionaryIds, dictionaries));
        }

        log.debug("Dictionaries loaded successfully");
        return dictionaries;
    }
}
```

**Key Patterns:**
- ✅ Use `@Slf4j` for Lombok logger
- ✅ INFO for orchestration lifecycle
- ✅ DEBUG for detailed steps (may be sampled)
- ✅ WARN for business rule violations
- ✅ ERROR for orchestration failures
- ✅ NO MDC manipulation (adapter layer responsibility)

---

### Example 2: Query Orchestrator

```java
package com.patra.registry.app.provenance.query;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional(readOnly = true)
public class QueryProvenanceOrchestrator {

    private final ProvenanceRepository provenanceRepository;
    private final DictionaryRepository dictionaryRepository;

    public QueryProvenanceOrchestrator(
            ProvenanceRepository provenanceRepository,
            DictionaryRepository dictionaryRepository) {
        this.provenanceRepository = provenanceRepository;
        this.dictionaryRepository = dictionaryRepository;
    }

    /**
     * Query provenance by source name.
     *
     * Logging strategy:
     * - INFO: Query start (minimal, may be high-frequency)
     * - DEBUG: Detailed query execution steps
     * - WARN: Not found scenarios
     */
    @Cacheable(value = "provenance", key = "#source")
    public ProvenanceDTO queryBySource(String source) {
        // INFO: Query start (concise to avoid log flooding)
        log.info("Querying provenance: source={}", source);

        // DEBUG: Query details
        log.debug("Checking cache for provenance: source={}", source);

        Provenance provenance = provenanceRepository.findBySource(source)
                .orElseThrow(() -> {
                    log.warn("Provenance not found: source={}", source);
                    return new ProvenanceNotFoundException(source);
                });

        // DEBUG: Query result details
        log.debug("Provenance found: id={}, version={}, dictionaries={}",
                provenance.getId(), provenance.getVersion(),
                provenance.getDictionaries().size());

        // Load associated dictionaries if needed
        if (!provenance.getDictionaries().isEmpty()) {
            log.debug("Loading associated dictionaries: count={}",
                    provenance.getDictionaries().size());
            // ... load dictionaries
        }

        ProvenanceDTO result = ProvenanceDTO.from(provenance);

        // INFO: Query completed (concise)
        log.info("Provenance query completed: source={}", source);

        return result;
    }
}
```

**Key Patterns:**
- ✅ Minimal INFO logging for high-frequency queries
- ✅ DEBUG for detailed execution steps
- ✅ WARN for not found scenarios
- ✅ Avoid excessive logging in hot paths

---

## Domain Layer

**Responsibilities**: Pure business logic, aggregates, entities, value objects, domain events

**Logger Pattern**: Plain `Logger` (NO Lombok, NO Spring)

### Example 1: Domain Aggregate

```java
package com.patra.registry.domain.provenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provenance aggregate root.
 *
 * Logging policy:
 * - Use plain Logger (NO @Slf4j annotation)
 * - Minimal logging (validation failures, state transitions)
 * - NO MDC manipulation (pure Java requirement)
 * - NO trace context awareness
 */
public class Provenance {

    private static final Logger log = LoggerFactory.getLogger(Provenance.class);

    private Long id;
    private String source;
    private ApiVersion apiVersion;
    private List<Endpoint> endpoints;
    private List<Dictionary> dictionaries;
    private ProvenanceStatus status;
    private Integer version;

    /**
     * Factory method to create new provenance.
     *
     * Logging strategy:
     * - DEBUG: Creation initiated
     * - WARN: Validation failures
     */
    public static Provenance create(
            String source,
            ApiVersion apiVersion,
            List<Endpoint> endpoints,
            List<Dictionary> dictionaries) {

        // DEBUG: Domain object creation (rarely logged)
        log.debug("Creating provenance aggregate: source={}", source);

        // Validation with WARN logging
        if (source == null || source.isBlank()) {
            log.warn("Provenance creation failed: source is null or empty");
            throw new ValidationException("Source is required");
        }

        if (endpoints == null || endpoints.isEmpty()) {
            log.warn("Provenance creation failed: no endpoints provided");
            throw new ValidationException("At least one endpoint is required");
        }

        // Create instance
        Provenance provenance = new Provenance();
        provenance.source = source;
        provenance.apiVersion = apiVersion;
        provenance.endpoints = new ArrayList<>(endpoints);
        provenance.dictionaries = new ArrayList<>(dictionaries);
        provenance.status = ProvenanceStatus.ACTIVE;
        provenance.version = 1;

        log.debug("Provenance aggregate created: source={}", source);

        return provenance;
    }

    /**
     * Domain behavior: Update API version.
     *
     * Logging strategy:
     * - INFO: State transition (important domain event)
     * - WARN: Invalid state transition attempts
     */
    public void updateApiVersion(ApiVersion newVersion) {
        // WARN: Invalid state
        if (this.status != ProvenanceStatus.ACTIVE) {
            log.warn("Cannot update API version: provenance is not active, " +
                    "source={}, status={}", this.source, this.status);
            throw new InvalidStateException("Provenance must be active to update");
        }

        // INFO: State transition (important domain event)
        log.info("Updating API version: source={}, oldVersion={}, newVersion={}",
                this.source, this.apiVersion, newVersion);

        this.apiVersion = newVersion;
        this.version++;
    }

    /**
     * Domain behavior: Deactivate provenance.
     *
     * Logging strategy:
     * - INFO: Status change (important domain event)
     * - WARN: Already deactivated
     */
    public void deactivate() {
        if (this.status == ProvenanceStatus.INACTIVE) {
            log.warn("Provenance already deactivated: source={}", this.source);
            return;
        }

        log.info("Deactivating provenance: source={}, previousStatus={}",
                this.source, this.status);

        this.status = ProvenanceStatus.INACTIVE;
        this.version++;
    }

    // Getters (no logging)
    public Long getId() { return id; }
    public String getSource() { return source; }
    // ... other getters

    // Package-private setters for repository use (no logging)
    void setId(Long id) { this.id = id; }
    void setVersion(Integer version) { this.version = version; }
}
```

**Key Patterns:**
- ✅ Use plain `Logger` (NO `@Slf4j`)
- ✅ Minimal logging (validation, state transitions)
- ✅ NO MDC manipulation
- ✅ NO trace context awareness
- ✅ INFO for important state changes
- ✅ WARN for validation failures
- ✅ DEBUG for creation (rare)

---

### Example 2: Value Object

```java
package com.patra.registry.domain.provenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * API version value object.
 *
 * Logging policy:
 * - Minimal logging (validation only)
 * - Use plain Logger
 */
public record ApiVersion(String value) {

    private static final Logger log = LoggerFactory.getLogger(ApiVersion.class);

    /**
     * Factory method with validation.
     *
     * Logging strategy:
     * - WARN: Invalid format
     */
    public static ApiVersion of(String value) {
        if (value == null || !value.matches("^v\\d+(\\.\\d+)*$")) {
            // WARN: Validation failure (business rule violated)
            log.warn("Invalid API version format: value={}", value);
            throw new ValidationException("API version must match pattern: v<major>.<minor>");
        }

        return new ApiVersion(value);
    }

    /**
     * Compare versions (no logging for pure calculations).
     */
    public boolean isNewerThan(ApiVersion other) {
        // NO logging for pure computations
        String[] thisParts = this.value.substring(1).split("\\.");
        String[] otherParts = other.value.substring(1).split("\\.");

        for (int i = 0; i < Math.min(thisParts.length, otherParts.length); i++) {
            int thisNum = Integer.parseInt(thisParts[i]);
            int otherNum = Integer.parseInt(otherParts[i]);
            if (thisNum != otherNum) {
                return thisNum > otherNum;
            }
        }

        return thisParts.length > otherParts.length;
    }
}
```

**Key Patterns:**
- ✅ Use plain `Logger` (NO `@Slf4j`)
- ✅ Log only validation failures
- ✅ NO logging for pure calculations
- ✅ Keep logs minimal

---

## Infrastructure Layer

**Responsibilities**: Data persistence, external API calls, technical infrastructure

**Logger Pattern**: `@Slf4j` (Lombok)

### Example 1: Repository Implementation

```java
package com.patra.registry.infra.provenance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class ProvenanceRepositoryMpImpl implements ProvenanceRepository {

    private final ProvenanceMapper provenanceMapper;
    private final ProvenanceDOMapper provenanceDOMapper;

    public ProvenanceRepositoryMpImpl(
            ProvenanceMapper provenanceMapper,
            ProvenanceDOMapper provenanceDOMapper) {
        this.provenanceMapper = provenanceMapper;
        this.provenanceDOMapper = provenanceDOMapper;
    }

    /**
     * Find provenance by source name.
     *
     * Logging strategy:
     * - DEBUG: Query execution details
     * - WARN: Database query issues
     * - ERROR: Database failures
     */
    @Override
    public Optional<Provenance> findBySource(String source) {
        // DEBUG: Query start
        log.debug("Finding provenance by source: source={}", source);

        try {
            ProvenanceDO provenanceDO = provenanceMapper.selectBySource(source);

            if (provenanceDO == null) {
                // DEBUG: Not found (expected scenario)
                log.debug("Provenance not found: source={}", source);
                return Optional.empty();
            }

            // Map DO to domain aggregate
            Provenance provenance = provenanceDOMapper.toDomain(provenanceDO);

            // DEBUG: Query success
            log.debug("Provenance found: source={}, id={}, version={}",
                    source, provenance.getId(), provenance.getVersion());

            return Optional.of(provenance);

        } catch (Exception e) {
            // ERROR: Database failure
            log.error("Failed to find provenance: source={}", source, e);
            throw new RepositoryException("Database query failed", e);
        }
    }

    /**
     * Save provenance aggregate.
     *
     * Logging strategy:
     * - DEBUG: Save operation details
     * - ERROR: Database failures (constraint violations, connection errors)
     */
    @Override
    public void save(Provenance provenance) {
        // DEBUG: Save start
        log.debug("Saving provenance: source={}, id={}",
                provenance.getSource(), provenance.getId());

        try {
            ProvenanceDO provenanceDO = provenanceDOMapper.toDO(provenance);

            if (provenance.getId() == null) {
                // INSERT
                log.debug("Inserting new provenance: source={}", provenance.getSource());
                provenanceMapper.insert(provenanceDO);
                provenance.setId(provenanceDO.getId());  // Update with generated ID

            } else {
                // UPDATE
                log.debug("Updating provenance: id={}, version={}",
                        provenance.getId(), provenance.getVersion());
                int rowsUpdated = provenanceMapper.updateById(provenanceDO);

                if (rowsUpdated == 0) {
                    // WARN: Optimistic locking conflict
                    log.warn("Optimistic lock failure: id={}, version={}",
                            provenance.getId(), provenance.getVersion());
                    throw new OptimisticLockException("Provenance was modified by another transaction");
                }
            }

            // DEBUG: Save success
            log.debug("Provenance saved successfully: id={}", provenance.getId());

        } catch (DuplicateKeyException e) {
            // ERROR: Unique constraint violation
            log.error("Duplicate provenance: source={}", provenance.getSource(), e);
            throw new ProvenanceDuplicateException(provenance.getSource(), e);

        } catch (Exception e) {
            // ERROR: Other database failures
            log.error("Failed to save provenance: source={}", provenance.getSource(), e);
            throw new RepositoryException("Database save failed", e);
        }
    }
}
```

**Key Patterns:**
- ✅ Use `@Slf4j` for Lombok logger
- ✅ DEBUG for query details (may be sampled)
- ✅ WARN for optimistic lock conflicts
- ✅ ERROR for database failures
- ✅ Include key identifiers in logs

---

### Example 2: External API Client

```java
package com.patra.ingest.infra.pubmed;

import com.patra.common.logging.sanitizer.LogSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class PubMedApiClient {

    private final RestTemplate restTemplate;
    private final LogSanitizer sanitizer;
    private final String baseUrl;

    public PubMedApiClient(
            RestTemplate restTemplate,
            LogSanitizer sanitizer,
            @Value("${pubmed.api.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.sanitizer = sanitizer;
        this.baseUrl = baseUrl;
    }

    /**
     * Search PubMed articles by query.
     *
     * Logging strategy (FR-006):
     * - INFO: API call start, success (URL, status, duration)
     * - DEBUG: Request/response bodies (sanitized)
     * - WARN: Client errors (4xx) with retry info
     * - ERROR: Server errors (5xx), network failures
     */
    public PubMedSearchResponse search(PubMedSearchRequest request) {
        String url = baseUrl + "/esearch.fcgi";
        long startTime = System.currentTimeMillis();

        // INFO: API call initiated
        log.info("Calling PubMed API: url={}, query={}",
                url, sanitizer.sanitize(request.getQuery()));

        // DEBUG: Full request details
        log.debug("Request details: {}", sanitizer.sanitizeObject(request));

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, request, String.class);

            long duration = System.currentTimeMillis() - startTime;
            HttpStatus status = response.getStatusCode();

            // INFO: API call success (FR-006: URL, status, duration)
            log.info("PubMed API call succeeded: url={}, status={}, duration={}ms",
                    url, status.value(), duration);

            // DEBUG: Response body (sanitized)
            log.debug("Response: status={}, body={}",
                    status.value(), sanitizer.sanitize(response.getBody()));

            return parsePubMedResponse(response.getBody());

        } catch (HttpClientErrorException e) {
            // WARN: Client error (4xx) - may be retryable
            long duration = System.currentTimeMillis() - startTime;
            HttpStatus status = e.getStatusCode();

            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("PubMed API rate limit exceeded: url={}, duration={}ms, " +
                        "retryAfter={}", url, duration, e.getResponseHeaders().getFirst("Retry-After"));
                throw new RateLimitException("PubMed rate limit exceeded", e);

            } else if (status == HttpStatus.BAD_REQUEST) {
                log.warn("PubMed API bad request: url={}, duration={}ms, query={}",
                        url, duration, sanitizer.sanitize(request.getQuery()));
                throw new InvalidQueryException("Invalid PubMed query", e);

            } else {
                log.warn("PubMed API client error: url={}, status={}, duration={}ms",
                        url, status.value(), duration);
                throw new PubMedApiException("PubMed client error", e);
            }

        } catch (HttpServerErrorException e) {
            // ERROR: Server error (5xx)
            long duration = System.currentTimeMillis() - startTime;
            log.error("PubMed API server error: url={}, status={}, duration={}ms",
                    url, e.getStatusCode().value(), duration, e);
            throw new PubMedApiException("PubMed server error", e);

        } catch (ResourceAccessException e) {
            // ERROR: Network/timeout error
            long duration = System.currentTimeMillis() - startTime;
            log.error("PubMed API connection failed: url={}, duration={}ms, error={}",
                    url, duration, e.getMessage(), e);
            throw new PubMedApiException("PubMed connection error", e);

        } catch (Exception e) {
            // ERROR: Unexpected error
            long duration = System.currentTimeMillis() - startTime;
            log.error("PubMed API call failed unexpectedly: url={}, duration={}ms",
                    url, duration, e);
            throw new PubMedApiException("Unexpected PubMed API error", e);
        }
    }
}
```

**Key Patterns:**
- ✅ Use `@Slf4j` for Lombok logger
- ✅ INFO for API call lifecycle (FR-006: URL, status, duration)
- ✅ DEBUG for request/response bodies (sanitized)
- ✅ WARN for client errors (4xx)
- ✅ ERROR for server errors (5xx), network failures
- ✅ Measure and log duration

---

## Cross-Cutting Concerns

### Aspect: Exception Logging

```java
package com.patra.starter.logging.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ExceptionLoggingAspect {

    /**
     * Automatically log exceptions thrown from orchestrators with full context.
     *
     * Logging strategy (FR-005):
     * - ERROR: Exception with stack trace, method signature, input parameters
     */
    @AfterThrowing(
        pointcut = "execution(* com.patra..app..*Orchestrator.*(..))",
        throwing = "exception"
    )
    public void logOrchestratorException(JoinPoint joinPoint, Throwable exception) {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        // ERROR: Exception with full context (FR-005)
        log.error("Orchestrator exception: class={}, method={}, args={}, traceId={}, " +
                "correlationId={}, exception={}",
                className, methodName, sanitizeArgs(args),
                MDC.get("traceId"), MDC.get("correlationId"),
                exception.getClass().getSimpleName(), exception);
    }

    private String sanitizeArgs(Object[] args) {
        // Sanitize arguments to avoid logging sensitive data
        if (args == null || args.length == 0) {
            return "[]";
        }
        return Arrays.stream(args)
                .map(arg -> arg == null ? "null" : arg.getClass().getSimpleName())
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
```

---

## Summary

### Logger Declaration by Layer

| Layer | Logger Declaration | Reason |
|-------|-------------------|--------|
| Adapter | `@Slf4j` | Lombok integration, Spring context |
| Application | `@Slf4j` | Lombok integration, Spring context |
| Domain | `private static final Logger log = LoggerFactory.getLogger(...)` | Pure Java, no framework dependencies |
| Infrastructure | `@Slf4j` | Lombok integration, Spring context |

### Log Level Guidelines by Layer

| Layer | INFO | DEBUG | WARN | ERROR |
|-------|------|-------|------|-------|
| Adapter | Request/response events | Request details, headers | Validation failures, not found | Unexpected exceptions |
| Application | Orchestration lifecycle | Detailed steps, decisions | Business rule violations | Orchestration failures |
| Domain | State transitions | Creation, computations | Validation failures, invalid states | (Rare - throw exceptions) |
| Infrastructure | API calls (URL, status, duration) | Query details, response bodies | Client errors (4xx), retries | Server errors (5xx), DB failures |

---

**Related Documentation:**
- [Quickstart Guide](../../specs/001-logging-starter/quickstart.md) - Migration checklist
- [Log Level Guidelines](log-level-guidelines.md) - Semantic log level usage
- [Common Patterns Guide](common-patterns.md) - Design patterns and best practices

**Next Steps:**
- Review examples for your target layer
- Adapt patterns to your service's domain
- Test locally and verify trace context propagation
