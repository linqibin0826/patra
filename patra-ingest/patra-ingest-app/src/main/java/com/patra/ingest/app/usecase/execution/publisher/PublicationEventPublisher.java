package com.patra.ingest.app.usecase.execution.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.outbox.config.OutboxPublisherProperties;
import com.patra.ingest.app.outbox.constants.OutboxAggregateTypes;
import com.patra.ingest.app.outbox.core.AbstractOutboxPublisher;
import com.patra.ingest.app.outbox.core.OutboxPublishContext;
import com.patra.ingest.app.outbox.metrics.OutboxMetrics;
import com.patra.ingest.app.outbox.operations.PublicationOperations;
import com.patra.ingest.domain.event.PublicationDataReadyEvent;
import com.patra.ingest.domain.messaging.IngestPublishingChannels;
import com.patra.ingest.domain.messaging.OperationType;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/// 出版物事件发布器
/// 
/// 在六边形架构+DDD中的角色:应用层事件发布器,负责在任务执行完成后发布出版物就绪事件。
/// 
/// 主要职责:将领域事件持久化到Outbox表,由中继组件投递到MQ。
@Component
public class PublicationEventPublisher
    extends AbstractOutboxPublisher<
        PublicationDataReadyEvent, PublicationReadyPayload, PublicationReadyHeaders> {

  public PublicationEventPublisher(
      OutboxMessageRepository repository,
      OutboxMetrics metrics,
      OutboxPublisherProperties properties,
      ObjectMapper objectMapper) {
    super(repository, metrics, properties, objectMapper);
  }

  /// 发布出版物就绪事件
/// 
/// 将事件持久化到outbox表,由relay组件投递到MQ。
/// 
/// @param event 出版物就绪领域事件
  public void publish(PublicationDataReadyEvent event) {
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
  protected IngestPublishingChannels getChannel() {
    return IngestPublishingChannels.PUBLICATION;
  }

  @Override
  protected PublicationReadyPayload buildPayload(
      PublicationDataReadyEvent event, OutboxPublishContext ctx) {
    ProvenanceCode pc = event.provenanceCode();
    String provenanceCode = pc != null ? pc.getCode() : null;
    return new PublicationReadyPayload(
        event.taskId(),
        event.runId(),
        provenanceCode,
        event.storageKeys(),
        event.totalPublicationCount(),
        event.successBatchCount(),
        event.failedBatchCount(),
        event.timestamp());
  }

  @Override
  protected PublicationReadyHeaders buildHeaders(
      PublicationDataReadyEvent event, OutboxPublishContext ctx) {
    int storageKeyCount = event.storageKeys() != null ? event.storageKeys().size() : 0;
    ProvenanceCode pc = event.provenanceCode();
    String provenanceCode = pc != null ? pc.getCode() : null;
    return new PublicationReadyHeaders(
        provenanceCode, event.taskId(), event.runId(), storageKeyCount, event.timestamp());
  }

  @Override
  protected String buildPartitionKey(PublicationDataReadyEvent event, OutboxPublishContext ctx) {
    ProvenanceCode pc = event.provenanceCode();
    String provenanceCode = pc != null ? pc.getCode() : null;
    if (provenanceCode != null) {
      return provenanceCode;
    }
    return "PUBLICATION";
  }

  @Override
  protected String buildDedupKey(PublicationDataReadyEvent event, OutboxPublishContext ctx) {
    return String.format("task:%d:run:%d:publication", event.taskId(), event.runId());
  }

  @Override
  protected OperationType getOperationType(PublicationDataReadyEvent event) {
    return PublicationOperations.DATA_READY;
  }

  @Override
  protected Long getAggregateId(PublicationDataReadyEvent event) {
    return event.taskId();
  }

  @Override
  protected boolean validateEvent(PublicationDataReadyEvent event) {
    return event != null
        && event.taskId() != null
        && event.runId() != null
        && event.storageKeys() != null
        && !event.storageKeys().isEmpty();
  }
}
