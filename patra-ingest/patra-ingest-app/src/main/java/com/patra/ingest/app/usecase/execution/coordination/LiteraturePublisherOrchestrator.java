package com.patra.ingest.app.usecase.execution.coordination;

import com.patra.common.model.StandardLiterature;
import com.patra.ingest.domain.port.LiteratureStoragePort;
import com.patra.ingest.domain.port.StorageMetadataPort;
import com.patra.ingest.domain.port.TechnicalRetryPort;
import feign.FeignException;
import feign.RetryableException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Application orchestrator coordinating literature publishing workflow.
 *
 * <p>This orchestrator makes cross-service integration explicit by coordinating two distinct
 * operations:
 *
 * <ul>
 *   <li>Storage upload via {@link LiteratureStoragePort} (technical infrastructure)
 *   <li>Metadata recording via {@link StorageMetadataPort} (business service integration with
 *       patra-storage)
 * </ul>
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Orchestrate storage and metadata recording sequence
 *   <li>Handle cross-service integration failures
 *   <li>Delegate failed metadata recording to retry mechanism
 *   <li>Provide unified publish result to application callers
 * </ul>
 *
 * <p>This design follows Hexagonal Architecture principles by making orchestration explicit at the
 * Application layer, rather than hiding it within infrastructure adapters.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiteraturePublisherOrchestrator {

  private static final String BUSINESS_TYPE = "literature-batch";

  private final LiteratureStoragePort literatureStoragePort;
  private final StorageMetadataPort storageMetadataPort;
  private final TechnicalRetryPort technicalRetryPort;

  /**
   * Publishes standardized literature by uploading to storage and recording metadata.
   *
   * @param literature domain-normalized literature list
   * @param context publishing context with execution metadata
   * @return publish result with storage location
   */
  public PublishResult publish(List<StandardLiterature> literature, PublishContext context) {
    List<StandardLiterature> safeLiterature =
        literature == null ? Collections.emptyList() : literature;

    // Step 1: Store to object storage
    LiteratureStoragePort.StorageContext storageContext = toStorageContext(context);
    LiteratureStoragePort.StorageResult storageResult =
        literatureStoragePort.store(safeLiterature, storageContext);

    log.info(
        "Literature stored bucket={} key={} size={} bytes count={}",
        storageResult.bucketName(),
        storageResult.objectKey(),
        storageResult.fileSize(),
        storageResult.literatureCount());

    // Step 2: Record metadata to patra-storage service (with error handling)
    try {
      StorageMetadataPort.MetadataRequest metadataRequest =
          buildMetadataRequest(storageResult, context);

      StorageMetadataPort.MetadataResult metadataResult =
          storageMetadataPort.recordUpload(metadataRequest);

      log.info(
          "Metadata recorded successfully storageKey={} metadataId={}",
          storageResult.storageKey(),
          metadataResult.metadataId());

    } catch (FeignException e) {
      handleMetadataRecordFailure(e, storageResult, context);
    } catch (Exception e) {
      if (e instanceof RetryableException) {
        log.warn(
            "Metadata recording timeout, delegating to retry storageKey={}",
            storageResult.storageKey(),
            e);
        delegateToRetry(storageResult, context, e);
      } else {
        log.error(
            "Unexpected error recording metadata, delegating to retry storageKey={}",
            storageResult.storageKey(),
            e);
        delegateToRetry(storageResult, context, e);
      }
    }

    return PublishResult.builder()
        .storageKey(storageResult.storageKey())
        .publishedCount(storageResult.literatureCount())
        .build();
  }

  private LiteratureStoragePort.StorageContext toStorageContext(PublishContext context) {
    return LiteratureStoragePort.StorageContext.builder()
        .runId(context.runId())
        .batchNo(context.batchNo())
        .provenanceCode(context.provenanceCode())
        .build();
  }

  private StorageMetadataPort.MetadataRequest buildMetadataRequest(
      LiteratureStoragePort.StorageResult storageResult, PublishContext context) {
    Map<String, Object> correlation = buildCorrelationData(storageResult, context);

    return StorageMetadataPort.MetadataRequest.builder()
        .storageKey(storageResult.storageKey())
        .bucketName(storageResult.bucketName())
        .objectKey(storageResult.objectKey())
        .fileSize(storageResult.fileSize())
        .contentType("application/json")
        .md5(storageResult.md5())
        .sha256(storageResult.sha256())
        .serviceName(null) // Will be populated by adapter
        .businessType(BUSINESS_TYPE)
        .businessId(buildBusinessId(context))
        .correlation(correlation)
        .providerType(null) // Will be populated by adapter
        .build();
  }

  private Map<String, Object> buildCorrelationData(
      LiteratureStoragePort.StorageResult storageResult, PublishContext context) {
    Map<String, Object> correlation = new LinkedHashMap<>();
    correlation.put("batchNo", context.batchNo());
    correlation.put("provenanceCode", safeProvenance(context.provenanceCode()));
    if (context.runId() != null) {
      correlation.put("runId", context.runId());
    }
    correlation.put("storageKey", storageResult.storageKey());
    return correlation;
  }

  private String buildBusinessId(PublishContext context) {
    String provenance = safeProvenance(context.provenanceCode());
    String runIdSegment = context.runId() != null ? String.valueOf(context.runId()) : "na";
    return provenance + "-" + context.batchNo() + "-" + runIdSegment;
  }

  private void handleMetadataRecordFailure(
      FeignException exception,
      LiteratureStoragePort.StorageResult storageResult,
      PublishContext context) {
    int status = exception.status();
    if (status >= 500 || status == 503 || status == -1) {
      log.warn(
          "patra-storage unavailable (HTTP {}), delegating to retry storageKey={}",
          status,
          storageResult.storageKey());
      delegateToRetry(storageResult, context, exception);
      return;
    }
    if (status >= 400 && status < 500) {
      log.error(
          "Invalid metadata record request (HTTP {}), manual investigation required. StorageKey={} bucket={} key={}",
          status,
          storageResult.storageKey(),
          storageResult.bucketName(),
          storageResult.objectKey(),
          exception);
      return;
    }
    log.error(
        "Unexpected Feign error, delegating to retry storageKey={}",
        storageResult.storageKey(),
        exception);
    delegateToRetry(storageResult, context, exception);
  }

  /**
   * Delegates failed metadata recording to technical retry mechanism.
   *
   * <p>Uses {@link TechnicalRetryPort} to ensure consistent retry handling through the outbox
   * publisher framework.
   *
   * @param storageResult storage result from successful upload
   * @param context publish context for traceability
   * @param error the exception that caused the failure
   */
  private void delegateToRetry(
      LiteratureStoragePort.StorageResult storageResult, PublishContext context, Exception error) {
    try {
      // Reconstruct metadata request for retry
      StorageMetadataPort.MetadataRequest metadataRequest =
          buildMetadataRequest(storageResult, context);

      String payloadJson = metadataRequest.toString();
      Long aggregateId = context.runId() != null ? context.runId() : 0L;

      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("provenanceCode", safeProvenance(context.provenanceCode()));
      metadata.put("batchNo", context.batchNo());
      metadata.put("storageKey", storageResult.storageKey());
      metadata.put("fileSize", storageResult.fileSize());
      String traceId = MDC.get("traceId");
      metadata.put("traceId", traceId != null ? traceId : "");

      TechnicalRetryPort.RetryContext retryContext =
          TechnicalRetryPort.RetryContext.builder()
              .operationType("METADATA_RECORD")
              .aggregateId(aggregateId)
              .payload(payloadJson)
              .metadata(metadata)
              .build();

      technicalRetryPort.publishRetry(retryContext);

      log.info(
          "Metadata record request delegated to retry runId={} storageKey={}",
          context.runId(),
          storageResult.storageKey());

    } catch (Exception e) {
      log.error(
          "CRITICAL: Failed to delegate to retry storageKey={} runId={}",
          storageResult.storageKey(),
          context.runId(),
          e);
    }
  }

  private String safeProvenance(String provenanceCode) {
    return StringUtils.hasText(provenanceCode)
        ? provenanceCode.toLowerCase(Locale.ROOT)
        : "unknown";
  }

  /**
   * Publish result containing storage location information.
   *
   * @param storageKey complete storage identifier
   * @param publishedCount number of literature items published
   */
  @Builder
  public record PublishResult(String storageKey, int publishedCount) {}

  /**
   * Publishing context with execution metadata.
   *
   * @param runId task run identifier
   * @param batchNo execution batch number
   * @param provenanceCode normalized source identifier
   */
  @Builder
  public record PublishContext(Long runId, int batchNo, String provenanceCode) {}
}
