package com.patra.ingest.app.outbox.operations;

import com.patra.ingest.domain.messaging.OperationType;

/// 任务相关的操作类型枚举。
/// 
/// **职责**：
/// 
/// - 定义任务生命周期中的关键操作类型
///   - 映射到 `ing_outbox_message.op_type` 字段
///   - 用于 RocketMQ Tags 过滤和消费者订阅
/// 
/// **使用场景**：
/// 
/// - **READY**：任务调度器创建任务后，通知执行器开始采集
///   - **FAILED**：任务执行失败，触发告警或重试逻辑
///   - **COMPLETED**：任务成功完成，触发下游流程（如数据转换）
/// 
/// **示例**：
/// 
/// ```java
/// // 在 TaskOutboxPublisher 中
/// @Override
/// protected OperationType getOperationType(TaskQueuedEvent event) {
///   return TaskOperations.READY;
/// 
/// // RocketMQ 消息目标
/// // Topic: INGEST_TASK
/// // Tags: READY
/// // Destination: "INGEST_TASK:READY"
/// ```
/// 
/// @author linqibin
/// @since 0.2.0
public enum TaskOperations implements OperationType {

  /// 任务就绪 - 调度器已创建任务并排队等待执行。
  READY("READY", "任务就绪 - 调度器已创建任务并排队等待执行"),
  ;

  private final String code;
  private final String description;

  TaskOperations(String code, String description) {
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
