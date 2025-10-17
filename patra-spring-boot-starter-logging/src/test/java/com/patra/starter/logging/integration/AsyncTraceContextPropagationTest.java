package com.patra.starter.logging.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.patra.common.logging.context.DistributedTraceContext;
import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import com.patra.starter.logging.async.MdcTaskDecorator;
import com.patra.starter.logging.autoconfigure.AsyncAutoConfiguration;
import com.patra.starter.logging.autoconfigure.LoggingAutoConfiguration;
import com.patra.starter.logging.autoconfigure.TraceContextAutoConfiguration;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.ReflectionUtils;

/**
 * Integration test for async trace context propagation.
 *
 * <p>Tests User Story 3 requirements (T060):
 *
 * <ul>
 *   <li>Verify trace context propagation across async boundaries (ThreadPoolTaskExecutor)
 *   <li>Verify MDC propagation to async threads via MdcTaskDecorator
 *   <li>Verify trace context maintained in CompletableFuture chains
 * </ul>
 *
 * <p>This test validates that:
 *
 * <ol>
 *   <li>MDC is correctly copied to async threads
 *   <li>Trace IDs remain consistent across async operations
 *   <li>Correlation IDs are preserved for async batch processing
 *   <li>Context cleanup works correctly in async scenarios
 * </ol>
 *
 * @see MdcTaskDecorator
 * @see AsyncAutoConfiguration
 * @since 0.1.0 (Phase 5 - User Story 3)
 */
@SpringBootTest(
    classes = {
      LoggingAutoConfiguration.class,
      TraceContextAutoConfiguration.class,
      AsyncAutoConfiguration.class,
      AsyncTraceContextPropagationTest.AsyncTestService.class,
      AsyncTraceContextPropagationTest.AsyncExecutorConfiguration.class
    })
@ContextConfiguration
@EnableAsync
@DisplayName("Async Trace Context Propagation Test (T060)")
class AsyncTraceContextPropagationTest {

  private static final Logger log = LoggerFactory.getLogger(AsyncTraceContextPropagationTest.class);

  @Autowired private TraceContextHolder traceContextHolder;

  @Autowired private LogContextEnricher logContextEnricher;

  @Autowired private AsyncTestService asyncTestService;

  @Autowired private ThreadPoolTaskExecutor taskExecutor;

  @BeforeEach
  void setUp() {
    traceContextHolder.clearContext();
    logContextEnricher.clear();
    MDC.clear();
  }

  @Test
  @DisplayName("Async executor should automatically receive MdcTaskDecorator")
  void shouldDecorateThreadPoolTaskExecutor() {
    assertThat(resolveTaskDecorator(taskExecutor))
        .as("ThreadPoolTaskExecutor must use MdcTaskDecorator for MDC propagation")
        .isInstanceOf(MdcTaskDecorator.class);
  }

  @AfterEach
  void tearDown() {
    traceContextHolder.clearContext();
    logContextEnricher.clear();
    MDC.clear();
  }

  @Test
  @DisplayName("T060: Should propagate MDC to @Async methods via MdcTaskDecorator")
  void shouldPropagateMdcToAsyncMethods() {
    // Given: Trace context set in parent thread
    String expectedTraceId = UUID.randomUUID().toString();
    String expectedSpanId = UUID.randomUUID().toString();
    String expectedCorrelationId = "async-batch-001";

    DistributedTraceContext parentContext =
        new DistributedTraceContext(
            expectedTraceId, expectedSpanId, Optional.empty(), Optional.of(expectedCorrelationId));

    traceContextHolder.setContext(parentContext);
    logContextEnricher.enrich(parentContext);

    log.info("Parent thread MDC before async call: traceId={}", MDC.get("traceId"));

    // When: Execute async operation
    AtomicReference<String> asyncThreadTraceId = new AtomicReference<>();
    AtomicReference<String> asyncThreadCorrelationId = new AtomicReference<>();

    CompletableFuture<Void> asyncOperation =
        asyncTestService.performAsyncOperation(asyncThreadTraceId, asyncThreadCorrelationId);

    // Then: Wait for async operation to complete and verify
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(asyncOperation).isCompleted();
              assertThat(asyncThreadTraceId.get())
                  .as("Async thread should have same trace ID via MDC propagation")
                  .isEqualTo(expectedTraceId);
              assertThat(asyncThreadCorrelationId.get())
                  .as("Async thread should have same correlation ID via MDC propagation")
                  .isEqualTo(expectedCorrelationId);
            });
  }

  @Test
  @DisplayName("T060: Should maintain correlation ID across async batch processing")
  void shouldMaintainCorrelationIdAcrossAsyncBatchProcessing() {
    // Given: Batch processing with correlation ID
    String traceId = UUID.randomUUID().toString();
    String batchCorrelationId = "batch-ingest-2025-01-15-001";

    DistributedTraceContext batchContext =
        new DistributedTraceContext(
            traceId,
            UUID.randomUUID().toString(),
            Optional.empty(),
            Optional.of(batchCorrelationId));

    traceContextHolder.setContext(batchContext);
    logContextEnricher.enrich(batchContext);

    // When: Execute async batch processing
    AtomicReference<String> asyncCorrelationId = new AtomicReference<>();
    AtomicReference<String> asyncTraceId = new AtomicReference<>();

    CompletableFuture<Void> batchOperation =
        asyncTestService.performAsyncOperation(asyncTraceId, asyncCorrelationId);

    // Then: Verify correlation ID is maintained
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(batchOperation).isCompleted();
              assertThat(asyncCorrelationId.get())
                  .as(
                      "Correlation ID must be maintained for batch tracking across async boundaries")
                  .isEqualTo(batchCorrelationId);
              assertThat(asyncTraceId.get())
                  .as("Trace ID must be maintained across async boundaries")
                  .isEqualTo(traceId);
            });
  }

  @Test
  @DisplayName("T060: Should handle missing MDC in async thread gracefully")
  void shouldHandleMissingMdcInAsyncThreadGracefully() {
    // Given: NO trace context set in parent thread (simulating missing context)
    MDC.clear();

    // When: Execute async operation without parent context
    AtomicReference<String> asyncThreadTraceId = new AtomicReference<>();
    AtomicReference<String> asyncThreadCorrelationId = new AtomicReference<>();

    CompletableFuture<Void> asyncOperation =
        asyncTestService.performAsyncOperation(asyncThreadTraceId, asyncThreadCorrelationId);

    // Then: Async thread should not have MDC pollution (null or empty)
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(asyncOperation).isCompleted();
              assertThat(asyncThreadTraceId.get())
                  .as("Async thread should not have polluted MDC when parent has none")
                  .isNull();
              assertThat(asyncThreadCorrelationId.get())
                  .as("Async thread should not have polluted MDC when parent has none")
                  .isNull();
            });
  }

  @Test
  @DisplayName("T060: Should support nested async operations with trace context")
  void shouldSupportNestedAsyncOperations() {
    // Given: Parent trace context
    String parentTraceId = UUID.randomUUID().toString();
    String parentSpanId = UUID.randomUUID().toString();

    DistributedTraceContext parentContext =
        new DistributedTraceContext(
            parentTraceId, parentSpanId, Optional.empty(), Optional.empty());

    traceContextHolder.setContext(parentContext);
    logContextEnricher.enrich(parentContext);

    // When: Execute nested async operations
    AtomicReference<String> level1TraceId = new AtomicReference<>();
    AtomicReference<String> level2TraceId = new AtomicReference<>();

    CompletableFuture<Void> nestedOperation =
        asyncTestService
            .performAsyncOperation(level1TraceId, new AtomicReference<>())
            .thenCompose(
                unused -> {
                  // Nested async operation should still have trace context
                  return asyncTestService.performAsyncOperation(
                      level2TraceId, new AtomicReference<>());
                });

    // Then: Verify trace ID propagated through nested async operations
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(nestedOperation).isCompleted();
              assertThat(level1TraceId.get())
                  .as("First level async should have parent trace ID")
                  .isEqualTo(parentTraceId);
              assertThat(level2TraceId.get())
                  .as("Second level async should have parent trace ID")
                  .isEqualTo(parentTraceId);
            });
  }

  /**
   * Test service with @Async methods for testing async trace propagation.
   *
   * <p>This service simulates async operations in the application layer (orchestrators) or
   * infrastructure layer (async repository operations, external API calls).
   */
  @Service
  static class AsyncTestService {

    private static final Logger log = LoggerFactory.getLogger(AsyncTestService.class);

    /**
     * Simulates an async operation that accesses MDC.
     *
     * <p>This method is annotated with @Async, so Spring will execute it in a separate thread from
     * the TaskExecutor. The MdcTaskDecorator should copy MDC from parent thread to this async
     * thread.
     *
     * @param traceIdCapture AtomicReference to capture trace ID from async thread
     * @param correlationIdCapture AtomicReference to capture correlation ID from async thread
     * @return CompletableFuture completing when operation finishes
     */
    @Async
    public CompletableFuture<Void> performAsyncOperation(
        AtomicReference<String> traceIdCapture, AtomicReference<String> correlationIdCapture) {

      // Capture MDC values in async thread (should be propagated via MdcTaskDecorator)
      String asyncTraceId = MDC.get("traceId");
      String asyncCorrelationId = MDC.get("correlationId");

      log.info(
          "Async operation executing: traceId={}, correlationId={}, thread={}",
          asyncTraceId,
          asyncCorrelationId,
          Thread.currentThread().getName());

      traceIdCapture.set(asyncTraceId);
      correlationIdCapture.set(asyncCorrelationId);

      // Simulate some work
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      return CompletableFuture.completedFuture(null);
    }
  }

  /**
   * Provides a ThreadPoolTaskExecutor bean to emulate host application async configuration. The
   * auto-configuration should decorate this executor with {@link MdcTaskDecorator} without
   * modifying its sizing parameters.
   */
  @TestConfiguration
  static class AsyncExecutorConfiguration {

    @Bean
    ThreadPoolTaskExecutor taskExecutor() {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(4);
      executor.setMaxPoolSize(8);
      executor.setQueueCapacity(64);
      executor.setThreadNamePrefix("async-mdc-test-");
      return executor;
    }
  }

  private static Object resolveTaskDecorator(ThreadPoolTaskExecutor executor) {
    var field = ReflectionUtils.findField(ThreadPoolTaskExecutor.class, "taskDecorator");
    if (field == null) {
      return null;
    }
    ReflectionUtils.makeAccessible(field);
    return ReflectionUtils.getField(field, executor);
  }
}
