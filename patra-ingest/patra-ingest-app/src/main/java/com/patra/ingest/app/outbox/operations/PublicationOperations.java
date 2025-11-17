package com.patra.ingest.app.outbox.operations;

import com.patra.ingest.domain.messaging.OperationType;

/**
 * 出版物相关的操作类型枚举。
 *
 * <p><b>职责</b>：
 *
 * <ul>
 *   <li>定义出版物数据处理流程中的关键操作类型
 *   <li>映射到 {@code ing_outbox_message.op_type} 字段
 *   <li>用于 RocketMQ Tags 过滤和消费者订阅
 * </ul>
 *
 * <p><b>使用场景</b>：
 *
 * <ul>
 *   <li><b>DATA_READY</b>：出版物数据采集完成，已保存到对象存储，通知下游处理
 * </ul>
 *
 * <p><b>示例</b>：
 *
 * <pre>{@code
 * // 在 PublicationOutboxPublisher 中
 * @Override
 * protected OperationType getOperationType(PublicationDataReadyEvent event) {
 *   return PublicationOperations.DATA_READY;
 * }
 *
 * // RocketMQ 消息目标
 * // Topic: INGEST_PUBLICATION
 * // Tags: DATA_READY
 * // Destination: "INGEST_PUBLICATION:DATA_READY"
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 */
public enum PublicationOperations implements OperationType {

  /** 出版物数据就绪 - 采集批次已提交到对象存储。 */
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
