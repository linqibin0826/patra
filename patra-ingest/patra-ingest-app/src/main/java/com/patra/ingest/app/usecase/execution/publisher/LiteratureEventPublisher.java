package com.patra.ingest.app.usecase.execution.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.outbox.config.OutboxPublisherProperties;
import com.patra.ingest.app.outbox.constants.OutboxAggregateTypes;
import com.patra.ingest.app.outbox.constants.OutboxBusinessTags;
import com.patra.ingest.app.outbox.constants.OutboxChannels;
import com.patra.ingest.app.outbox.core.AbstractOutboxPublisher;
import com.patra.ingest.app.outbox.core.OutboxPublishContext;
import com.patra.ingest.app.outbox.metrics.OutboxMetrics;
import com.patra.ingest.domain.event.LiteratureDataReadyEvent;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Outbox publisher for literature ready events emitted after task execution completes. */
@Component
public class LiteratureEventPublisher
    extends AbstractOutboxPublisher<
        LiteratureDataReadyEvent, LiteratureReadyPayload, LiteratureReadyHeaders> {

  public LiteratureEventPublisher(
      OutboxMessageRepository repository,
      OutboxMetrics metrics,
      OutboxPublisherProperties properties,
      ObjectMapper objectMapper) {
    super(repository, metrics, properties, objectMapper);
  }

  /**
   * Persist the given event into the outbox so the relay can deliver it to MQ.
   *
   * @param event literature ready domain event
   */
  public void publish(LiteratureDataReadyEvent event) {
    if (!validateEvent(event)) {
      return;
    }
    super.publish(List.of(event), OutboxPublishContext.builder().build());
  }

  @Override
  protected OutboxAggregateTypes getAggregateType() {
    return OutboxAggregateTypes.TASK;
  }

  @Override
  protected OutboxChannels getChannel() {
    return OutboxChannels.LITERATURE_DATA_READY;
  }

  @Override
  protected LiteratureReadyPayload buildPayload(
      LiteratureDataReadyEvent event, OutboxPublishContext ctx) {
    return new LiteratureReadyPayload(
        event.taskId(),
        event.runId(),
        event.provenanceCode(),
        event.storageKeys(),
        event.totalLiteratureCount(),
        event.successBatchCount(),
        event.failedBatchCount(),
        event.timestamp());
  }

  @Override
  protected LiteratureReadyHeaders buildHeaders(
      LiteratureDataReadyEvent event, OutboxPublishContext ctx) {
    int storageKeyCount = event.storageKeys() != null ? event.storageKeys().size() : 0;
    return new LiteratureReadyHeaders(
        event.provenanceCode(), event.taskId(), event.runId(), storageKeyCount, event.timestamp());
  }

  @Override
  protected String buildPartitionKey(LiteratureDataReadyEvent event, OutboxPublishContext ctx) {
    if (event.provenanceCode() != null && !event.provenanceCode().isBlank()) {
      return event.provenanceCode();
    }
    return "LITERATURE";
  }

  @Override
  protected String buildDedupKey(LiteratureDataReadyEvent event, OutboxPublishContext ctx) {
    return String.format("task:%d:run:%d:literature", event.taskId(), event.runId());
  }

  @Override
  protected OutboxBusinessTags getOperationType(LiteratureDataReadyEvent event) {
    return OutboxBusinessTags.LITERATURE_DATA_READY;
  }

  @Override
  protected Long getAggregateId(LiteratureDataReadyEvent event) {
    return event.taskId();
  }

  @Override
  protected boolean validateEvent(LiteratureDataReadyEvent event) {
    return event != null
        && event.taskId() != null
        && event.runId() != null
        && event.storageKeys() != null
        && !event.storageKeys().isEmpty();
  }
}
