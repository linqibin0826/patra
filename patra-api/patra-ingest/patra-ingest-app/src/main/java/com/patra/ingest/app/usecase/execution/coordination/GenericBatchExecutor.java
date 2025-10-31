package com.patra.ingest.app.usecase.execution.coordination;

import com.patra.common.model.StandardLiterature;
import com.patra.ingest.app.usecase.execution.converter.ProvenanceConfigConverter;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.batch.BatchResult;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.starter.provenance.common.adapter.AdapterRegistry;
import com.patra.starter.provenance.common.adapter.AdapterRequest;
import com.patra.starter.provenance.common.adapter.AdapterResult;
import com.patra.starter.provenance.common.adapter.AdapterResult.ErrorType;
import com.patra.starter.provenance.common.adapter.BatchExecutionParams;
import com.patra.starter.provenance.common.adapter.BatchMetadata;
import com.patra.starter.provenance.common.adapter.DataSourceAdapter;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Generic batch executor that delegates fetching to {@link DataSourceAdapter} implementations.
 *
 * <p>The executor is responsible for:
 *
 * <ul>
 *   <li>Resolving the correct adapter via {@link AdapterRegistry}.
 *   <li>Converting registry snapshots into runtime configuration.
 *   <li>Publishing standardized literature through {@link LiteraturePublisherOrchestrator}.
 *   <li>Translating adapter outcomes into domain {@link BatchResult} instances.
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenericBatchExecutor {

  private static final int DEFAULT_MAX_RETRY_TIMES = 3;
  private static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 1_000L;
  private static final long MAX_BACKOFF_MILLIS = 30_000L;

  private final AdapterRegistry adapterRegistry;
  private final LiteraturePublisherOrchestrator literaturePublisherOrchestrator;
  private final ProvenanceConfigConverter configConverter;

  /**
   * Executes a single batch.
   *
   * @param context execution context
   * @param batch batch definition
   * @return result of the batch execution
   */
  public BatchResult execute(ExecutionContext context, Batch batch) {
    Objects.requireNonNull(context, "execution context must not be null");
    Objects.requireNonNull(batch, "batch must not be null");

    long startAt = System.currentTimeMillis();
    int batchNo = batch.batchNo();
    String provenanceCode = context.provenanceCode();
    String operationCode = context.operationCode();

    log.info(
        "batch execution start provenanceCode={} operationCode={} batchNo={} runId={}",
        provenanceCode,
        operationCode,
        batchNo,
        context.runId());

    try {
      DataSourceAdapter adapter = adapterRegistry.getAdapter(provenanceCode);
      ProvenanceConfig runtimeConfig =
          configConverter.convert(provenanceCode, context.configSnapshot());

      // Build batch execution parameters (query + complete params including pagination)
      BatchExecutionParams executionParams =
          new BatchExecutionParams(batch.query(), batch.params());

      // Build batch metadata (batchNo + cursor)
      BatchMetadata metadata = new BatchMetadata(batch.batchNo(), batch.cursorToken());

      AdapterRequest request =
          AdapterRequest.builder()
              .operationCode(operationCode)
              .config(runtimeConfig)
              .executionParams(executionParams)
              .metadata(metadata)
              .build();

      int maxAttempts = resolveMaxAttempts(runtimeConfig);
      long initialDelayMillis = resolveInitialDelayMillis(runtimeConfig);
      AdapterResult adapterResult = null;
      int attempt = 0;

      while (true) {
        attempt++;
        adapterResult = adapter.fetchData(request);
        if (adapterResult.success()) {
          break;
        }

        boolean retriable = adapterResult.errorType() == ErrorType.RETRIABLE;
        if (!retriable || attempt >= maxAttempts) {
          if (retriable) {
            log.warn(
                "adapter retry exhausted provenanceCode={} batchNo={} attemptsUsed={}/{}",
                provenanceCode,
                batchNo,
                attempt,
                maxAttempts);
          }
          return handleFailure(context, batch, adapterResult, System.currentTimeMillis() - startAt);
        }

        long backoffMillis = calculateBackoffDelay(initialDelayMillis, attempt);
        log.warn(
            "retriable adapter failure provenanceCode={} batchNo={} attempt={} of {} retryDelay={}ms message={}",
            provenanceCode,
            batchNo,
            attempt,
            maxAttempts,
            backoffMillis,
            safeMessage(adapterResult.errorMessage()));
        try {
          TimeUnit.MILLISECONDS.sleep(backoffMillis);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          long duration = System.currentTimeMillis() - startAt;
          log.error(
              "batch execution interrupted during retry provenanceCode={} batchNo={} duration={}ms",
              provenanceCode,
              batchNo,
              duration,
              interrupted);
          return BatchResult.failure(batchNo, "Adapter retry interrupted");
        }
      }

      LiteraturePublisherOrchestrator.PublishResult publishResult =
          publishLiterature(context, batchNo, adapterResult.literatures());
      logAdapterWarnings(adapterResult, provenanceCode, batchNo);

      long duration = System.currentTimeMillis() - startAt;
      log.info(
          "batch execution success provenanceCode={} operationCode={} batchNo={} fetchedCount={} duration={}ms attempts={}",
          provenanceCode,
          operationCode,
          batchNo,
          adapterResult.fetchedCount(),
          duration,
          attempt);

      int publishedCount = publishResult.publishedCount();
      return BatchResult.success(
          batchNo, publishedCount, adapterResult.nextCursorToken(), publishResult.storageKey());

    } catch (Exception ex) {
      long duration = System.currentTimeMillis() - startAt;
      log.error(
          "batch execution unexpected failure provenanceCode={} batchNo={} duration={}ms",
          provenanceCode,
          batchNo,
          duration,
          ex);
      return BatchResult.failure(batchNo, safeMessage(ex.getMessage()));
    }
  }

  private BatchResult handleFailure(
      ExecutionContext context, Batch batch, AdapterResult result, long durationMillis) {
    String provenanceCode = context.provenanceCode();
    int batchNo = batch.batchNo();
    ErrorType errorType = result.errorType();
    String reason = safeMessage(result.errorMessage());
    log.warn(
        "batch execution failed provenanceCode={} batchNo={} errorType={} duration={}ms message={}",
        provenanceCode,
        batchNo,
        errorType,
        durationMillis,
        reason);
    return BatchResult.failure(batchNo, buildFailureMessage(errorType, reason));
  }

  private void logAdapterWarnings(AdapterResult adapterResult, String provenanceCode, int batchNo) {
    if (adapterResult.errorMessage() != null
        && adapterResult.errorType() == ErrorType.PARTIAL_SUCCESS) {
      log.warn(
          "adapter reported partial success provenanceCode={} batchNo={} warning={}",
          provenanceCode,
          batchNo,
          adapterResult.errorMessage());
    }
  }

  private LiteraturePublisherOrchestrator.PublishResult publishLiterature(
      ExecutionContext context, int batchNo, List<StandardLiterature> literatures) {
    List<StandardLiterature> payload = literatures == null ? List.of() : List.copyOf(literatures);
    if (payload.isEmpty()) {
      return LiteraturePublisherOrchestrator.PublishResult.builder()
          .publishedCount(0)
          .storageKey(null)
          .build();
    }
    LiteraturePublisherOrchestrator.PublishContext publishContext =
        LiteraturePublisherOrchestrator.PublishContext.builder()
            .runId(context.runId())
            .batchNo(batchNo)
            .provenanceCode(context.provenanceCode())
            .build();
    return literaturePublisherOrchestrator.publish(payload, publishContext);
  }

  private int resolveMaxAttempts(ProvenanceConfig runtimeConfig) {
    if (runtimeConfig != null && runtimeConfig.retry() != null) {
      Integer configured = runtimeConfig.retry().maxRetryTimes();
      if (configured != null) {
        int sanitized = Math.max(configured, 0);
        if (sanitized >= Integer.MAX_VALUE - 1) {
          return Integer.MAX_VALUE;
        }
        return sanitized + 1;
      }
    }
    return DEFAULT_MAX_RETRY_TIMES + 1;
  }

  private long resolveInitialDelayMillis(ProvenanceConfig runtimeConfig) {
    if (runtimeConfig != null && runtimeConfig.retry() != null) {
      Integer configured = runtimeConfig.retry().initialDelayMillis();
      if (configured != null && configured > 0) {
        return configured.longValue();
      }
    }
    return DEFAULT_INITIAL_BACKOFF_MILLIS;
  }

  private long calculateBackoffDelay(long initialDelayMillis, int attemptIndex) {
    long base = initialDelayMillis > 0 ? initialDelayMillis : DEFAULT_INITIAL_BACKOFF_MILLIS;
    int retryCount = Math.max(attemptIndex, 1);
    long delay = base;
    for (int i = 1; i < retryCount; i++) {
      if (delay >= MAX_BACKOFF_MILLIS) {
        return MAX_BACKOFF_MILLIS;
      }
      long next = delay * 2L;
      if (next <= 0L) {
        return MAX_BACKOFF_MILLIS;
      }
      delay = Math.min(next, MAX_BACKOFF_MILLIS);
    }
    return Math.min(delay, MAX_BACKOFF_MILLIS);
  }

  private String buildFailureMessage(ErrorType type, String reason) {
    if (type == null || type == ErrorType.NONE) {
      return reason;
    }
    return "[%s] %s".formatted(type.name(), reason);
  }

  private String safeMessage(String message) {
    return StringUtils.hasText(message) ? message : "Unknown adapter failure";
  }
}
