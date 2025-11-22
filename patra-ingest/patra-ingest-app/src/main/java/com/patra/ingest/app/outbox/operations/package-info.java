/// Outbox 操作类型枚举包。
/// 
/// **职责**：
/// 
/// - 定义各业务领域的操作类型枚举（实现 {@link com.patra.ingest.domain.messaging.OperationType}）
///   - 提供业务语义标签，描述对资源的具体操作（如 READY、FAILED、COMPLETED）
///   - 配合 {@link com.patra.ingest.domain.messaging.IngestPublishingChannels} 实现 Channel + OpType
///       模式
/// 
/// **设计理念**：
/// 
/// - **粗粒度 Channel**：一个资源一个 Topic（如 INGEST_TASK）
///   - **细粒度 OpType**：用 RocketMQ Tags 区分不同操作（如 READY、FAILED）
///   - **面向业务**：操作类型描述业务语义，而非技术 CRUD
/// 
/// **包含的枚举**：
/// 
/// - {@link com.patra.ingest.app.outbox.operations.TaskOperations} -
///       任务操作类型（READY、FAILED、COMPLETED）
///   - {@link com.patra.ingest.app.outbox.operations.PublicationOperations} - 出版物操作类型（DATA_READY）
/// 
/// **使用示例**：
/// 
/// ```java
/// // 在 Publisher 中指定操作类型
/// @Override
/// protected OperationType getOperationType(TaskQueuedEvent event) {
///   return TaskOperations.READY;
/// 
/// // 最终 RocketMQ 消息
/// // Topic: INGEST_TASK (来自 IngestPublishingChannels.TASK)
/// // Tags: READY (来自 TaskOperations.READY)
/// // Destination: "INGEST_TASK:READY"
/// ```
/// 
/// @author linqibin
/// @since 0.2.0
package com.patra.ingest.app.outbox.operations;
