package dev.linqibin.patra.ingest.app.outbox.operations;

import dev.linqibin.patra.ingest.domain.messaging.OperationType;

/// 出版物相关的操作类型枚举。
///
/// **职责**：
///
/// - 定义出版物数据处理流程中的关键操作类型
///   - 映射到 `ing_outbox_message.op_type` 字段
///   - 用于 RocketMQ Tags 过滤和消费者订阅
///
/// **使用场景**：
///
/// - **DATA_READY**：出版物数据采集完成，已保存到对象存储，通知下游处理
///
/// **示例**：
///
/// ```java
/// // 在 PublicationOutboxPublisher 中
/// @Override
/// protected OperationType getOperationType(PublicationDataReadyEvent event) {
///   return PublicationOperations.DATA_READY;
///
/// // RocketMQ 消息目标
/// // Topic: INGEST_PUBLICATION
/// // Tags: DATA_READY
/// // Destination: "INGEST_PUBLICATION:DATA_READY"
/// ```
///
/// @author linqibin
/// @since 0.1.0
public enum PublicationOperations implements OperationType {

  /// 出版物数据就绪 - 采集批次已提交到对象存储。
  DATA_READY("DATA_READY", "出版物数据就绪 - 采集批次已提交到对象存储");

  private final String code;
  private final String description;

  PublicationOperations(String code, String description) {
    this.code = code;
    this.description = description;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getDescription() {
    return description;
  }
}
