package com.patra.ingest.app.outbox.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.outbox.config.OutboxPublisherProperties;
import com.patra.ingest.app.outbox.constants.OutboxAggregateTypes;
import com.patra.ingest.app.outbox.constants.OutboxBusinessTags;
import com.patra.ingest.app.outbox.constants.OutboxChannels;
import com.patra.ingest.app.outbox.core.AbstractOutboxPublisher;
import com.patra.ingest.app.outbox.core.OutboxPublishContext;
import com.patra.ingest.app.outbox.metrics.OutboxMetrics;
import com.patra.ingest.domain.outbox.OutboxHeaders;
import com.patra.ingest.domain.outbox.OutboxPayload;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import com.patra.ingest.domain.port.TechnicalRetryPort;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Publisher for technical retry operations (e.g., failed metadata recording, RPC timeouts).
 *
 * <p>Implements {@link TechnicalRetryPort} to allow infrastructure adapters to delegate retry logic
 * without direct Outbox manipulation. Uses {@link AbstractOutboxPublisher} framework for consistent
 * metrics, logging, and batch handling.
 *
 * <h3>Design Notes</h3>
 *
 * <ul>
 *   <li><b>Framework Integration</b>: Extends AbstractOutboxPublisher for unified Outbox behavior
 *   <li><b>Port Implementation</b>: Implements TechnicalRetryPort to serve infrastructure layer
 *   <li><b>Channel Isolation</b>: Uses STORAGE_METADATA_INTERNAL channel for technical retries
 *   <li><b>Flexible Payload</b>: Accepts pre-serialized JSON payload to support any operation type
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@Slf4j
public class MetadataRecordRetryPublisher
    extends AbstractOutboxPublisher<TechnicalRetryPort.RetryContext, RetryPayload, RetryHeaders>
    implements TechnicalRetryPort {

  private static final HexFormat HEX_FORMAT = HexFormat.of();

  public MetadataRecordRetryPublisher(
      OutboxMessageRepository repository,
      OutboxMetrics metrics,
      OutboxPublisherProperties properties,
      ObjectMapper objectMapper) {
    super(repository, metrics, properties, objectMapper);
  }

  /**
   * Publishes a technical retry request to the Outbox.
   *
   * <p>This method is invoked by infrastructure adapters when a technical operation fails (e.g.,
   * RPC timeout, external service unavailable).
   *
   * @param context retry context containing operation details
   */
  @Override
  public void publishRetry(TechnicalRetryPort.RetryContext context) {
    if (!validateEvent(context)) {
      log.warn(
          "Invalid retry context, skipping publish operationType={} aggregateId={}",
          context.operationType(),
          context.aggregateId());
      return;
    }
    super.publish(List.of(context), OutboxPublishContext.builder().build());
  }

  @Override
  protected OutboxAggregateTypes getAggregateType() {
    return OutboxAggregateTypes.TASK_RUN;
  }

  @Override
  protected OutboxChannels getChannel() {
    return OutboxChannels.STORAGE_METADATA_INTERNAL;
  }

  @Override
  protected RetryPayload buildPayload(
      TechnicalRetryPort.RetryContext event, OutboxPublishContext ctx) {
    return new RetryPayload(event.payload());
  }

  @Override
  protected RetryHeaders buildHeaders(
      TechnicalRetryPort.RetryContext event, OutboxPublishContext ctx) {
    return new RetryHeaders(event.metadata());
  }

  @Override
  protected String buildPartitionKey(
      TechnicalRetryPort.RetryContext event, OutboxPublishContext ctx) {
    Object provenance = event.metadata().get("provenanceCode");
    return provenance != null ? provenance.toString() : "UNKNOWN";
  }

  @Override
  protected String buildDedupKey(TechnicalRetryPort.RetryContext event, OutboxPublishContext ctx) {
    Object storageKey = event.metadata().get("storageKey");
    Object fileSize = event.metadata().get("fileSize");
    String input = storageKey + ":" + fileSize;
    return computeSha256(input);
  }

  @Override
  protected OutboxBusinessTags getOperationType(TechnicalRetryPort.RetryContext event) {
    return OutboxBusinessTags.STORAGE_METADATA_RETRY;
  }

  @Override
  protected Long getAggregateId(TechnicalRetryPort.RetryContext event) {
    return event.aggregateId();
  }

  @Override
  protected boolean validateEvent(TechnicalRetryPort.RetryContext event) {
    return event != null && event.payload() != null && event.aggregateId() != null;
  }

  /**
   * Computes SHA-256 hash for deduplication key.
   *
   * @param input input string
   * @return hex-encoded SHA-256 hash
   */
  private String computeSha256(String input) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      sha256.update(input.getBytes());
      return HEX_FORMAT.formatHex(sha256.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Missing SHA-256 algorithm", ex);
    }
  }
}

/**
 * Retry payload containing the serialized operation request.
 *
 * @param rawPayload pre-serialized JSON payload (e.g., UploadRecordRequest JSON)
 */
record RetryPayload(String rawPayload) implements OutboxPayload {}

/**
 * Retry headers containing metadata for tracing and correlation.
 *
 * @param metadata metadata map (traceId, provenanceCode, batchNo, etc.)
 */
record RetryHeaders(Map<String, Object> metadata) implements OutboxHeaders {}
