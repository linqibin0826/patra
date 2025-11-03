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
 * 技术重试操作的发布器(例如失败的元数据记录、RPC 超时)。
 *
 * <p>实现 {@link TechnicalRetryPort} 以允许基础设施适配器委托重试逻辑,而无需直接操作 Outbox。 使用 {@link
 * AbstractOutboxPublisher} 框架以实现一致的指标、日志记录和批处理。
 *
 * <h3>设计说明</h3>
 *
 * <ul>
 *   <li><b>框架集成</b>: 扩展 AbstractOutboxPublisher 以获得统一的 Outbox 行为
 *   <li><b>端口实现</b>: 实现 TechnicalRetryPort 以服务基础设施层
 *   <li><b>通道隔离</b>: 使用 STORAGE_METADATA_INTERNAL 通道进行技术重试
 *   <li><b>灵活负载</b>: 接受预序列化的 JSON 负载以支持任何操作类型
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
   * 将技术重试请求发布到 Outbox。
   *
   * <p>当技术操作失败时(例如 RPC 超时、外部服务不可用),基础设施适配器会调用此方法。
   *
   * @param context 包含操作详情的重试上下文
   */
  @Override
  public void publishRetry(TechnicalRetryPort.RetryContext context) {
    if (!validateEvent(context)) {
      log.warn(
          "无效的重试上下文,跳过发布,operationType={},aggregateId={}",
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
   * 计算用于去重键的 SHA-256 哈希。
   *
   * @param input 输入字符串
   * @return 十六进制编码的 SHA-256 哈希
   */
  private String computeSha256(String input) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      sha256.update(input.getBytes());
      return HEX_FORMAT.formatHex(sha256.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("缺少 SHA-256 算法", ex);
    }
  }
}

/**
 * 包含序列化操作请求的重试负载。
 *
 * @param rawPayload 预序列化的 JSON 负载(例如 UploadRecordRequest JSON)
 */
record RetryPayload(String rawPayload) implements OutboxPayload {}

/**
 * 包含用于跟踪和关联的元数据的重试头部。
 *
 * @param metadata 元数据映射(traceId、provenanceCode、batchNo 等)
 */
record RetryHeaders(Map<String, Object> metadata) implements OutboxHeaders {}
