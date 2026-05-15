package dev.linqibin.patra.ingest.domain.messaging;

/// 消息操作类型的统一契约。
///
/// 职责：
///
/// - 定义业务操作的语义标签（存储在 `ing_outbox_message.op_type` 字段）
///   - 提供统一的接口供不同领域实现各自的操作类型枚举
///   - 用于 RocketMQ Tags 过滤、监控统计、审计查询
///
/// **设计理念**：
///
/// - **面向业务**：操作类型描述特定的业务操作，而不是通用的 CRUD
///   - **领域特定**：每个领域定义自己的操作类型枚举（如 `TaskOperations`）
///   - **分离关注点**：Channel 负责路由，OperationType 负责语义
///
/// **使用示例**：
///
/// ```java
/// // 定义任务领域的操作类型
/// public enum TaskOperations implements OperationType {
///   READY("READY", "任务就绪 - 调度器已创建任务并排队等待执行"),
///   FAILED("FAILED", "任务失败 - 执行过程中发生错误"),
///   COMPLETED("COMPLETED", "任务完成 - 成功执行完毕");
///
///   private final String code;
///   private final String description;
///
///   TaskOperations(String code, String description) {
///     this.code = code;
///     this.description = description;
///
///   @Override
///   public String getCode() { return code;
///
///   @Override
///   public String getDescription() { return description;
///
/// // 在 Publisher 中使用
/// @Override
/// protected TaskOperations getOperationType(TaskQueuedEvent event) {
///   return TaskOperations.READY;
/// ```
///
/// **与 Channel 的关系**：
///
/// - Channel（如 `INGEST_TASK`）：资源级别，决定消息路由到哪个 Topic
///   - OperationType（如 `READY`）：操作级别，描述对资源的具体操作
///   - 组合示例：`channel=INGEST_TASK, opType=READY` → RocketMQ: `INGEST_TASK:READY`
///
/// @author linqibin
/// @since 0.1.0
/// @see dev.linqibin.commons.messaging.ChannelKey
public interface OperationType {

  /// 返回操作类型的代码。
  ///
  /// 此值：
  ///
  /// - 存储在 `ing_outbox_message.op_type` 字段
  ///   - 映射到 RocketMQ Tags（用于消费端过滤）
  ///   - 用于监控统计和审计查询
  ///
  /// 命名约定：使用大写蛇形命名风格（例如 `READY`、`FAILED`、`COMPLETED`）
  ///
  /// @return 操作类型代码（例如 "READY", "FAILED", "COMPLETED"）
  String getCode();

  /// 返回操作类型的人类可读描述。
  ///
  /// 用于：
  ///
  /// - 文档和日志说明
  ///   - 监控面板展示
  ///   - 业务人员理解
  ///
  /// @return 操作类型的中文描述
  String getDescription();
}
