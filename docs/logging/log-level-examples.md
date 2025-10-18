# Log Level Usage Examples by Layer (FR-001)

**Version**: 1.0.0
**Status**: Active
**Related**: [Log Level Guidelines](./log-level-guidelines.md) | [Common Patterns](./common-patterns.md)

---

## Overview

This document provides concrete code examples showing how to use each log level across different hexagonal architecture layers. All examples follow Google Java Style Guide and demonstrate proper trace context inclusion, sanitization, and parameterized logging (FR-012).

---

## Table of Contents

1. [Adapter Layer Examples](#adapter-layer-examples)
2. [Application Layer Examples](#application-layer-examples)
3. [Domain Layer Examples](#domain-layer-examples)
4. [Infrastructure Layer Examples](#infrastructure-layer-examples)
5. [Cross-Cutting Concerns](#cross-cutting-concerns)

---

## Adapter Layer Examples

### REST Controller (HTTP Requests)

```java
package com.papertrace.registry.adapter.rest;

import com.papertrace.registry.app.provenance.query.GetProvenanceConfigsQuery;
import com.papertrace.registry.app.provenance.query.GetProvenanceConfigsQueryHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/provenance-configs")
@RequiredArgsConstructor
public class ProvenanceConfigController {

  private final GetProvenanceConfigsQueryHandler queryHandler;

  /**
   * INFO: Log key business events (request received, completed)
   * DEBUG: Log request/response details for troubleshooting
   */
  @GetMapping
  public ResponseEntity<?> getProvenanceConfigs(
      @RequestParam(required = false) String sourceId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    // INFO: Key business event - request received
    log.info(
        "Received request to query provenance configs: sourceId={}, page={}, size={}",
        sourceId,
        page,
        size);

    // Execute query
    var query = new GetProvenanceConfigsQuery(sourceId, page, size);
    var result = queryHandler.handle(query);

    // INFO: Request completed successfully
    log.info("Query completed: found {} provenance configs", result.getTotalElements());

    return ResponseEntity.ok(result);
  }

  /**
   * WARN: Log failed authentication/authorization
   * ERROR: Log unhandled exceptions (but ExceptionLoggingAspect handles this automatically)
   */
  @PostMapping
  public ResponseEntity<?> createProvenanceConfig(
      @Validated @RequestBody CreateProvenanceConfigRequest request) {

    // DEBUG: Log request details (useful for troubleshooting)
    log.debug("Creating provenance config: {}", request);

    // ... delegate to command handler ...

    // INFO: Key business event - resource created
    log.info("Created provenance config: id={}, sourceId={}", configId, request.getSourceId());

    return ResponseEntity.ok(result);
  }
}
```

---

### Scheduled Job (Batch Processing)

```java
package com.papertrace.ingest.adapter.job;

import com.papertrace.ingest.app.batch.ProcessPubMedBatchCommand;
import com.papertrace.ingest.app.batch.ProcessPubMedBatchCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PubMedIngestionJob {

  private final ProcessPubMedBatchCommandHandler commandHandler;

  /**
   * INFO: Log batch job start/complete with summary counts (FR-010)
   * WARN: Log retry attempts or degraded functionality
   * ERROR: Log job failures requiring immediate attention
   */
  @Scheduled(cron = "0 0 2 * * ?")  // Daily at 2 AM
  public void ingestPubMedArticles() {
    String batchId = UUID.randomUUID().toString();

    // INFO: Batch job started
    log.info("Starting PubMed ingestion batch job: batchId={}", batchId);

    long startTime = System.currentTimeMillis();

    try {
      var command = new ProcessPubMedBatchCommand(batchId, LocalDate.now().minusDays(1));
      var result = commandHandler.handle(command);

      long duration = System.currentTimeMillis() - startTime;

      // INFO: Batch job completed - aggregated summary (NOT per-record logs)
      log.info(
          "Completed PubMed ingestion batch: batchId={}, processed={}, success={}, errors={},"
              + " duration={}ms",
          batchId,
          result.getTotalProcessed(),
          result.getSuccessCount(),
          result.getErrorCount(),
          duration);

      // WARN: Partial success (some records failed)
      if (result.getErrorCount() > 0) {
        log.warn(
            "PubMed batch completed with errors: batchId={}, errorRate={}%",
            batchId,
            (result.getErrorCount() * 100.0 / result.getTotalProcessed()));
      }

    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;

      // ERROR: Batch job failed completely
      log.error(
          "PubMed ingestion batch failed: batchId={}, duration={}ms, error={}",
          batchId,
          duration,
          e.getMessage(),
          e);

      // Re-throw to trigger retry or alerting
      throw new BatchProcessingException("PubMed ingestion failed", e);
    }
  }
}
```

---

### Message Queue Listener (Async Processing)

```java
package com.papertrace.ingest.adapter.mq;

import com.papertrace.ingest.app.article.ProcessArticleCommand;
import com.papertrace.ingest.app.article.ProcessArticleCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = "article-ingestion", consumerGroup = "ingest-service")
@RequiredArgsConstructor
public class ArticleIngestionListener implements RocketMQListener<ArticleIngestionMessage> {

  private final ProcessArticleCommandHandler commandHandler;

  /**
   * INFO: Log message consumption start/complete
   * DEBUG: Log message details (payload, headers)
   * WARN: Log message processing failures (before retry)
   */
  @Override
  public void onMessage(ArticleIngestionMessage message) {
    // INFO: Message consumed
    log.info(
        "Received article ingestion message: articleId={}, sourceId={}",
        message.getArticleId(),
        message.getSourceId());

    // DEBUG: Message details (useful for troubleshooting MQ issues)
    log.debug("Message payload: {}", message);

    try {
      var command =
          new ProcessArticleCommand(message.getArticleId(), message.getSourceId(), message.getRawData());
      commandHandler.handle(command);

      // INFO: Message processed successfully
      log.info("Successfully processed article: articleId={}", message.getArticleId());

    } catch (Exception e) {
      // ERROR: Message processing failed (triggers retry)
      log.error(
          "Failed to process article message: articleId={}, sourceId={}, error={}",
          message.getArticleId(),
          message.getSourceId(),
          e.getMessage(),
          e);

      // Re-throw for RocketMQ retry
      throw new MessageProcessingException("Article processing failed", e);
    }
  }
}
```

---

## Application Layer Examples

### Orchestrator (Use Case Coordination)

```java
package com.papertrace.registry.app.provenance.update;

import com.papertrace.registry.domain.provenance.ProvenanceConfig;
import com.papertrace.registry.domain.provenance.ProvenanceConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProvenanceConfigCommandHandler {

  private final ProvenanceConfigRepository repository;

  /**
   * INFO: Log use case start/complete with outcome
   * DEBUG: Log orchestration flow, decision points
   * TRACE: Log method entry/exit with parameters
   */
  @Transactional
  public UpdateProvenanceConfigResult handle(UpdateProvenanceConfigCommand command) {

    // DEBUG: Orchestrator entry with command details
    log.debug("Handling UpdateProvenanceConfigCommand: configId={}", command.getConfigId());

    // INFO: Key business operation starting
    log.info("Updating provenance config: configId={}", command.getConfigId());

    // Load aggregate
    ProvenanceConfig config =
        repository
            .findById(command.getConfigId())
            .orElseThrow(() -> new ProvenanceConfigNotFoundException(command.getConfigId()));

    // DEBUG: Aggregate loaded
    log.debug(
        "Loaded provenance config: configId={}, currentPriority={}",
        config.getId(),
        config.getPriority());

    // Execute business logic (domain method)
    config.updateMetadata(command.getDescription(), command.getPriority());

    // DEBUG: Business logic executed
    log.debug(
        "Updated provenance config metadata: configId={}, newPriority={}",
        config.getId(),
        config.getPriority());

    // Persist aggregate
    repository.save(config);

    // INFO: Use case completed successfully
    log.info(
        "Successfully updated provenance config: configId={}, priority={}",
        config.getId(),
        config.getPriority());

    // TRACE: Orchestrator exit with result
    log.trace("UpdateProvenanceConfigCommand completed: result={}", result);

    return new UpdateProvenanceConfigResult(config.getId(), config.getVersion());
  }
}
```

---

## Domain Layer Examples

### Entity (Business Logic)

```java
package com.papertrace.registry.domain.provenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Domain layer uses plain SLF4J Logger (NO Lombok @Slf4j).
 *
 * WARN: Business rule violations, validation failures
 * DEBUG: Business logic evaluation, decision points
 * TRACE: Variable states, algorithm steps
 */
public class ProvenanceConfig {

  // Plain SLF4J Logger (no Lombok in domain layer)
  private static final Logger log = LoggerFactory.getLogger(ProvenanceConfig.class);

  private String id;
  private String sourceId;
  private int priority;
  private String description;

  /**
   * WARN: Log business rule violations
   */
  public void updateMetadata(String newDescription, int newPriority) {
    // WARN: Invalid business rule input
    if (newPriority < 1 || newPriority > 100) {
      log.warn(
          "Invalid priority value for provenance config: configId={}, invalidPriority={} (valid"
              + " range: 1-100)",
          this.id,
          newPriority);
      throw new IllegalArgumentException("Priority must be between 1 and 100");
    }

    // DEBUG: Business logic decision point
    if (newPriority != this.priority) {
      log.debug(
          "Priority changed for provenance config: configId={}, oldPriority={}, newPriority={}",
          this.id,
          this.priority,
          newPriority);
    }

    // Update fields
    this.description = newDescription;
    this.priority = newPriority;

    // TRACE: Variable states after update
    log.trace(
        "Provenance config metadata updated: id={}, description={}, priority={}",
        this.id,
        this.description,
        this.priority);
  }
}
```

---

## Infrastructure Layer Examples

### Repository Implementation (Database Access)

```java
package com.papertrace.registry.infra.provenance;

import com.papertrace.registry.domain.provenance.ProvenanceConfig;
import com.papertrace.registry.domain.provenance.ProvenanceConfigRepository;
import com.papertrace.registry.infra.provenance.mapper.ProvenanceConfigMapper;
import com.papertrace.registry.infra.provenance.po.ProvenanceConfigPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ProvenanceConfigRepositoryImpl implements ProvenanceConfigRepository {

  private final ProvenanceConfigMapper mapper;

  /**
   * DEBUG: Log database queries (SQL, execution time per FR-007)
   * ERROR: Log database failures (FR-007: query type, table, exception)
   */
  @Override
  public Optional<ProvenanceConfig> findById(String id) {

    // DEBUG: Database query execution
    log.debug("Querying provenance config by ID: id={}", id);

    long startTime = System.currentTimeMillis();

    try {
      ProvenanceConfigPO po = mapper.selectById(id);

      long duration = System.currentTimeMillis() - startTime;

      // DEBUG: Query execution time (FR-007)
      log.debug(
          "Database query completed: table=provenance_config, operation=SELECT, duration={}ms",
          duration);

      if (po == null) {
        // DEBUG: Not found (not an error)
        log.debug("Provenance config not found: id={}", id);
        return Optional.empty();
      }

      // Map DO to domain entity
      ProvenanceConfig entity = toDomain(po);

      // TRACE: Detailed entity data
      log.trace("Loaded provenance config: {}", entity);

      return Optional.of(entity);

    } catch (Exception e) {
      // ERROR: Database failure (FR-007)
      log.error(
          "Database query failed: table=provenance_config, operation=SELECT, id={}, error={}",
          id,
          e.getMessage(),
          e);

      throw new DatabaseAccessException("Failed to query provenance config", e);
    }
  }

  @Override
  public void save(ProvenanceConfig entity) {

    // INFO: Key database operation (insert/update)
    log.info("Saving provenance config: id={}", entity.getId());

    long startTime = System.currentTimeMillis();

    try {
      ProvenanceConfigPO po = toPO(entity);

      // Determine if insert or update
      boolean isNew = (mapper.selectById(po.getId()) == null);
      int affectedRows = isNew ? mapper.insert(po) : mapper.updateById(po);

      long duration = System.currentTimeMillis() - startTime;

      // INFO: Database write completed
      log.info(
          "Saved provenance config: id={}, operation={}, affectedRows={}, duration={}ms",
          entity.getId(),
          isNew ? "INSERT" : "UPDATE",
          affectedRows,
          duration);

    } catch (Exception e) {
      // ERROR: Database failure (FR-007)
      log.error(
          "Database write failed: table=provenance_config, operation=UPSERT, id={}, error={}",
          entity.getId(),
          e.getMessage(),
          e);

      throw new DatabaseAccessException("Failed to save provenance config", e);
    }
  }
}
```

---

### External API Client (HTTP Calls)

```java
package com.papertrace.ingest.infra.pubmed;

import com.papertrace.common.logging.sanitizer.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class PubMedApiClient {

  private final RestTemplate restTemplate;
  private final LogSanitizer sanitizer;

  /**
   * INFO: Log external API calls (FR-006: URL, status, duration, error)
   * WARN: Log retry attempts
   * ERROR: Log API failures after retries exhausted
   * DEBUG: Log request/response details
   */
  public PubMedArticleResponse fetchArticle(String pmid) {

    String url = String.format("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=pubmed&id=%s&retmode=xml", pmid);

    // INFO: External API call starting (FR-006)
    log.info("Calling PubMed API: pmid={}, url={}", pmid, url);

    long startTime = System.currentTimeMillis();

    try {
      var response = restTemplate.getForEntity(url, String.class);

      long duration = System.currentTimeMillis() - startTime;

      // INFO: External API call completed (FR-006: URL, status, duration)
      log.info(
          "PubMed API call completed: pmid={}, status={}, duration={}ms",
          pmid,
          response.getStatusCode(),
          duration);

      // DEBUG: Response details (useful for troubleshooting API issues)
      log.debug("PubMed API response: pmid={}, bodyLength={}", pmid, response.getBody().length());

      // TRACE: Full response payload (sanitized)
      log.trace("PubMed API response body: {}", sanitizer.sanitize(response.getBody()));

      return parseResponse(response.getBody());

    } catch (HttpServerErrorException e) {
      long duration = System.currentTimeMillis() - startTime;

      // ERROR: External API failure (FR-006: URL, status, duration, error)
      log.error(
          "PubMed API call failed: pmid={}, url={}, status={}, duration={}ms, error={}",
          pmid,
          url,
          e.getStatusCode(),
          duration,
          e.getMessage(),
          e);

      throw new ExternalApiException("PubMed API unavailable", e);

    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;

      // ERROR: Unexpected error
      log.error(
          "Unexpected error calling PubMed API: pmid={}, url={}, duration={}ms, error={}",
          pmid,
          url,
          duration,
          e.getMessage(),
          e);

      throw new ExternalApiException("PubMed API call failed", e);
    }
  }
}
```

---

## Cross-Cutting Concerns

### Sensitive Data Sanitization (FR-008, SC-006)

```java
package com.papertrace.registry.app.user;

import com.papertrace.common.logging.sanitizer.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthenticationService {

  private final LogSanitizer sanitizer;

  /**
   * ALWAYS sanitize sensitive data before logging (FR-008, SC-006)
   */
  public void authenticate(String username, String password) {

    // WARN: Failed authentication (sanitized - NO password logged)
    log.warn("Authentication failed: username={}", username);

    // DEBUG: Request details (sanitized)
    var userRequest = new AuthRequest(username, password);
    log.debug("Authentication request: {}", sanitizer.sanitize(userRequest));

    // Alternatively, redact manually:
    log.debug("Authentication attempt: username={}, password=[REDACTED]", username);
  }
}
```

---

### Exception Logging (Automatic via ExceptionLoggingAspect)

```java
package com.papertrace.registry.adapter.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class ProvenanceConfigController {

  /**
   * Exceptions are automatically logged by ExceptionLoggingAspect (T031)
   * with full stack trace, input context, and trace ID.
   *
   * NO need to manually log exceptions at controller boundaries!
   */
  @GetMapping("/api/v1/provenance-configs/{id}")
  public ResponseEntity<?> getProvenanceConfig(@PathVariable String id) {

    // If exception occurs here, ExceptionLoggingAspect logs it automatically:
    // ERROR: Exception in com.papertrace.registry.adapter.rest.ProvenanceConfigController.getProvenanceConfig:
    //        id=12345, traceId=abc-123, correlationId=xyz-789, error=ProvenanceConfigNotFoundException
    //        [full stack trace]

    var config = queryHandler.handle(new GetProvenanceConfigQuery(id));

    return ResponseEntity.ok(config);
  }
}
```

---

## Performance Optimization Examples

### Avoid Logging in Tight Loops

```java
// ❌ BAD: Per-record INFO logs (10,000 logs for 10k records)
for (Article article : articles) {
  log.info("Processing article: pmid={}", article.getPmid());
  process(article);
}

// ✅ GOOD: Aggregated summary at INFO, per-record at DEBUG
log.info("Processing {} articles", articles.size());
int successCount = 0, errorCount = 0;

for (Article article : articles) {
  // DEBUG logs are filtered out in production (log level = INFO)
  log.debug("Processing article: pmid={}", article.getPmid());

  try {
    process(article);
    successCount++;
  } catch (Exception e) {
    errorCount++;
    log.error("Failed to process article: pmid={}, error={}", article.getPmid(), e.getMessage(), e);
  }
}

// INFO: Aggregated summary (SC-004: reduced log volume)
log.info("Completed processing: total={}, success={}, errors={}", articles.size(), successCount, errorCount);
```

---

## References

- [Log Level Guidelines](./log-level-guidelines.md)
- [Common Logging Patterns](./common-patterns.md)
- [Troubleshooting Log Levels](./troubleshooting-log-levels.md)
