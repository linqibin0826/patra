package com.patra.starter.logging.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.logging.context.DistributedTraceContext;
import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import com.patra.starter.logging.autoconfigure.LoggingAutoConfiguration;
import com.patra.starter.logging.autoconfigure.TraceContextAutoConfiguration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test for trace context propagation across layers.
 *
 * <p>Tests User Story 3 requirements (T058, T059):
 *
 * <ul>
 *   <li>Verify trace ID propagation across synchronous calls
 *   <li>Verify MDC enrichment with trace context
 *   <li>Verify context cleanup after operations
 * </ul>
 *
 * <p>This test validates that:
 *
 * <ol>
 *   <li>TraceContextHolder correctly stores and retrieves trace context
 *   <li>LogContextEnricher populates MDC with trace data
 *   <li>Trace context is maintained across service boundaries (simulated)
 *   <li>Context cleanup prevents leakage between requests
 * </ol>
 *
 * @see TraceContextHolder
 * @see LogContextEnricher
 * @see DistributedTraceContext
 * @since 0.1.0 (Phase 5 - User Story 3)
 */
@SpringBootTest(
    classes = {
      LoggingAutoConfiguration.class,
      TraceContextAutoConfiguration.class,
    })
@ContextConfiguration
@DisplayName("Trace Context Propagation Integration Test (T058, T059)")
class TraceContextPropagationIntegrationTest {

  @Autowired private TraceContextHolder traceContextHolder;

  @Autowired private LogContextEnricher logContextEnricher;

  @BeforeEach
  void setUp() {
    // Ensure clean state before each test
    traceContextHolder.clearContext();
    logContextEnricher.clear();
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    // Cleanup after each test
    traceContextHolder.clearContext();
    logContextEnricher.clear();
    MDC.clear();
  }

  @Test
  @DisplayName(
      "T059: Should propagate trace ID across synchronous calls (Adapter → App → Infra layers)")
  void shouldPropagateTraceIdAcrossSynchronousLayers() {
    // Given: Incoming request with trace context (simulating adapter layer entry point)
    String expectedTraceId = UUID.randomUUID().toString();
    String expectedSpanId = UUID.randomUUID().toString();
    String expectedCorrelationId = "batch-" + UUID.randomUUID().toString();

    DistributedTraceContext incomingContext =
        new DistributedTraceContext(
            expectedTraceId, expectedSpanId, Optional.empty(), Optional.of(expectedCorrelationId));

    // When: Adapter layer sets trace context
    traceContextHolder.setContext(incomingContext);
    logContextEnricher.enrich(incomingContext);

    // Then: Verify context is available in current layer (Adapter)
    Optional<DistributedTraceContext> adapterContext = traceContextHolder.getContext();
    assertThat(adapterContext).isPresent();
    assertThat(adapterContext.get().traceId()).isEqualTo(expectedTraceId);
    assertThat(adapterContext.get().spanId()).isEqualTo(expectedSpanId);
    assertThat(adapterContext.get().correlationId()).hasValue(expectedCorrelationId);

    // Verify MDC is populated (for logging)
    assertThat(MDC.get("traceId")).isEqualTo(expectedTraceId);
    assertThat(MDC.get("spanId")).isEqualTo(expectedSpanId);
    assertThat(MDC.get("correlationId")).isEqualTo(expectedCorrelationId);

    // Simulate: App layer orchestrator accessing trace context
    Optional<DistributedTraceContext> appContext = traceContextHolder.getContext();
    assertThat(appContext).isPresent();
    assertThat(appContext.get().traceId())
        .as("App layer should see same trace ID")
        .isEqualTo(expectedTraceId);

    // Simulate: Infra layer repository accessing trace context
    Optional<DistributedTraceContext> infraContext = traceContextHolder.getContext();
    assertThat(infraContext).isPresent();
    assertThat(infraContext.get().traceId())
        .as("Infra layer should see same trace ID")
        .isEqualTo(expectedTraceId);

    // Verify MDC is still populated across layers
    assertThat(MDC.get("traceId"))
        .as("MDC trace ID should persist across layers")
        .isEqualTo(expectedTraceId);
  }

  @Test
  @DisplayName("T059: Should maintain correlation ID for batch operations")
  void shouldMaintainCorrelationIdForBatchOperations() {
    // Given: Batch processing with correlation ID
    String traceId = UUID.randomUUID().toString();
    String correlationId = "batch-ingest-2025-01-15-001";

    DistributedTraceContext batchContext =
        new DistributedTraceContext(
            traceId, UUID.randomUUID().toString(), Optional.empty(), Optional.of(correlationId));

    // When: Set context for batch operation
    traceContextHolder.setContext(batchContext);
    logContextEnricher.enrich(batchContext);

    // Then: Verify correlation ID is preserved
    Optional<DistributedTraceContext> context = traceContextHolder.getContext();
    assertThat(context).isPresent();
    assertThat(context.get().correlationId())
        .as("Correlation ID must be preserved for batch tracking")
        .hasValue(correlationId);

    assertThat(MDC.get("correlationId"))
        .as("Correlation ID should be available in MDC for logging")
        .isEqualTo(correlationId);
  }

  @Test
  @DisplayName("T058: Should clean up trace context after request completes")
  void shouldCleanupTraceContextAfterRequest() {
    // Given: Request with trace context
    String traceId = UUID.randomUUID().toString();
    DistributedTraceContext requestContext =
        new DistributedTraceContext(
            traceId, UUID.randomUUID().toString(), Optional.empty(), Optional.empty());

    traceContextHolder.setContext(requestContext);
    logContextEnricher.enrich(requestContext);

    // Verify context is set
    assertThat(traceContextHolder.getContext()).isPresent();
    assertThat(MDC.get("traceId")).isNotNull();

    // When: Request completes (cleanup)
    traceContextHolder.clearContext();
    logContextEnricher.clear();

    // Then: Verify context is cleared (prevents leakage to next request)
    assertThat(traceContextHolder.getContext())
        .as("TraceContextHolder should be cleared after request")
        .isEmpty();
    assertThat(MDC.get("traceId")).as("MDC should be cleared to prevent leakage").isNull();
    assertThat(MDC.get("spanId")).isNull();
    assertThat(MDC.get("correlationId")).isNull();
  }

  @Test
  @DisplayName("T058: Should handle missing trace context gracefully")
  void shouldHandleMissingTraceContextGracefully() {
    // Given: No trace context set (simulating missing upstream trace propagation)

    // When: Attempting to retrieve context
    Optional<DistributedTraceContext> context = traceContextHolder.getContext();

    // Then: Should return empty Optional (not throw exception)
    assertThat(context)
        .as("Missing trace context should return empty Optional, not throw exception")
        .isEmpty();

    // Verify MDC is not polluted
    assertThat(MDC.get("traceId")).isNull();
  }

  @Test
  @DisplayName("T059: Should support trace context updates (span ID changes)")
  void shouldSupportTraceContextUpdates() {
    // Given: Initial trace context
    String traceId = UUID.randomUUID().toString();
    String initialSpanId = UUID.randomUUID().toString();

    DistributedTraceContext initialContext =
        new DistributedTraceContext(traceId, initialSpanId, Optional.empty(), Optional.empty());

    traceContextHolder.setContext(initialContext);
    logContextEnricher.enrich(initialContext);

    // When: Span ID changes (simulating nested operation)
    String newSpanId = UUID.randomUUID().toString();
    DistributedTraceContext updatedContext =
        new DistributedTraceContext(
            traceId, newSpanId, Optional.of(initialSpanId), Optional.empty());

    traceContextHolder.setContext(updatedContext);
    logContextEnricher.enrich(updatedContext);

    // Then: Verify trace ID remains same but span ID updated
    Optional<DistributedTraceContext> context = traceContextHolder.getContext();
    assertThat(context).isPresent();
    assertThat(context.get().traceId()).as("Trace ID must remain constant").isEqualTo(traceId);
    assertThat(context.get().spanId())
        .as("Span ID should be updated for nested operation")
        .isEqualTo(newSpanId);
    assertThat(context.get().parentSpanId())
        .as("Parent span ID should reference previous span")
        .hasValue(initialSpanId);

    // Verify MDC reflects updated span
    assertThat(MDC.get("spanId")).isEqualTo(newSpanId);
  }

  @Test
  @DisplayName("T058: Should handle optional correlation ID correctly")
  void shouldHandleOptionalCorrelationIdCorrectly() {
    // Given: Trace context without correlation ID (normal request, not batch)
    String traceId = UUID.randomUUID().toString();

    DistributedTraceContext contextWithoutCorrelation =
        new DistributedTraceContext(
            traceId, UUID.randomUUID().toString(), Optional.empty(), Optional.empty());

    // When: Set context without correlation ID
    traceContextHolder.setContext(contextWithoutCorrelation);
    logContextEnricher.enrich(contextWithoutCorrelation);

    // Then: Verify correlation ID is absent (not "null" string)
    Optional<DistributedTraceContext> context = traceContextHolder.getContext();
    assertThat(context).isPresent();
    assertThat(context.get().correlationId())
        .as("Correlation ID should be empty Optional for non-batch requests")
        .isEmpty();

    // MDC should not have "null" or empty string for correlationId
    String mdcCorrelationId = MDC.get("correlationId");
    assertThat(mdcCorrelationId)
        .as("MDC correlation ID should be null (not logged) when absent")
        .isNull();
  }
}
