package dev.linqibin.patra.ingest.infra.outbox;

import dev.linqibin.patra.ingest.domain.model.entity.OutboxMessage;
import dev.linqibin.patra.ingest.domain.port.OutboxMessageRepository;
import dev.linqibin.patra.ingest.domain.port.TechnicalRetryPort;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/// 技术重试适配器（Infrastructure 层实现）
///
/// 职责：
///
/// - 实现 {@link TechnicalRetryPort} 端口
///   - 将 {@link RetryContext} 转换为 {@link OutboxMessage}
///   - 计算 SHA-256 去重键
///   - 序列化 metadata 为 JSON
///   - 持久化到 Outbox 表
///
/// ### 架构说明
///
/// 此实现位于 Infrastructure 层，符合六边形架构原则：
///
/// - 不依赖 Application 层的 `AbstractOutboxPublisher`（避免反向依赖）
///   - 直接实现 Domain 层的 `TechnicalRetryPort` 接口
///   - 使用 Domain 层的 `OutboxMessageRepository` 进行持久化
///
/// @author linqibin
/// @since 0.1.0
@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class TechnicalRetryAdapter implements TechnicalRetryPort {

  private final OutboxMessageRepository repository;
  private final ObjectMapper objectMapper;

  private static final HexFormat HEX_FORMAT = HexFormat.of();

  @Override
  public void publishRetry(RetryContext context) {
    // 1. 验证输入
    if (!validateContext(context)) {
      log.warn(
          "无效的重试上下文，跳过发布，operationType={}，aggregateId={}",
          context != null ? context.operationType() : "null",
          context != null ? context.aggregateId() : "null");
      return;
    }

    // 2. 构建 Outbox 消息
    OutboxMessage message = buildOutboxMessage(context);

    // 3. 持久化到 Outbox
    repository.saveOrUpdate(message);

    log.info(
        "技术重试已发布到 Outbox，operationType={}，aggregateId={}",
        context.operationType(),
        context.aggregateId());
  }

  /// 验证重试上下文是否有效
  ///
  /// @param context 重试上下文
  /// @return true 如果有效，false 否则
  private boolean validateContext(RetryContext context) {
    return context != null && context.payload() != null && context.aggregateId() != null;
  }

  /// 从 RetryContext 构建 OutboxMessage
  ///
  /// @param context 重试上下文
  /// @return Outbox 消息实体
  private OutboxMessage buildOutboxMessage(RetryContext context) {
    String dedupKey = computeDedupKey(context);
    String partitionKey = extractPartitionKey(context);
    String headersJson = serializeHeaders(context);

    return OutboxMessage.builder()
        .aggregateType("TASK_RUN")
        .aggregateId(context.aggregateId())
        .channel("STORAGE_METADATA_INTERNAL")
        .opType("STORAGE_METADATA_RETRY")
        .payloadJson(context.payload())
        .headersJson(headersJson)
        .partitionKey(partitionKey)
        .dedupKey(dedupKey)
        .statusCode("PENDING")
        .retryCount(0)
        .build();
  }

  /// 计算去重键（使用 SHA-256 哈希）
  ///
  /// 去重键格式：SHA-256(storageKey + ":" + fileSize)
  ///
  /// @param context 重试上下文
  /// @return 十六进制编码的 SHA-256 哈希值
  private String computeDedupKey(RetryContext context) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

      Object storageKey = context.metadata().get("storageKey");
      Object fileSize = context.metadata().get("fileSize");

      String input = storageKey + ":" + fileSize;
      sha256.update(input.getBytes());

      return HEX_FORMAT.formatHex(sha256.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("缺少 SHA-256 算法", e);
    }
  }

  /// 提取分区键（用于消息顺序投递）
  ///
  /// 分区键策略：使用 provenanceCode，缺失时使用 "UNKNOWN"
  ///
  /// @param context 重试上下文
  /// @return 分区键字符串
  private String extractPartitionKey(RetryContext context) {
    Object provenance = context.metadata().get("provenanceCode");
    return provenance != null ? provenance.toString() : "UNKNOWN";
  }

  /// 序列化 metadata 为 JSON 字符串
  ///
  /// @param context 重试上下文
  /// @return JSON 字符串
  private String serializeHeaders(RetryContext context) {
    try {
      return objectMapper.writeValueAsString(context.metadata());
    } catch (JacksonException e) {
      log.error("序列化 metadata 失败，aggregateId={}", context.aggregateId(), e);
      return "{}";
    }
  }
}
