/// 任务发布器包。
/// 
/// 本包提供将 Task 事件发布到 Outbox 的实现。
/// 
/// ## 职责
/// 
/// - 将 TaskQueuedEvent 发布到 Outbox 表
///   - 构建任务消息的负载（`TaskPayload`）和消息头（`TaskHeaders`）
///   - 定义任务消息的分区策略和幂等键
/// 
/// ## 核心组件
/// 
/// - `TaskOutboxPublisher` - 任务 Outbox 发布器
///       
/// - 继承自 {@link com.patra.ingest.app.outbox.core.AbstractOutboxPublisher}
///         - 发布到 `INGEST_TASK_READY` 通道
/// 
///   - `TaskPayload` - 任务消息负载
///       
/// - `taskId`: 任务 ID
///         - `provenanceCode`: 数据源代码
///         - `operationCode`: 操作代码
/// 
///   - `TaskHeaders` - 任务消息头
///       
/// - `planId`: Plan ID
///         - `sliceId`: Slice ID
///         - `planKey`: Plan 业务键
/// 
/// ## 分区策略
/// 
/// - 按 `taskId` 分区，确保同一任务的消息顺序消费
/// 
/// ## 幂等键策略
/// 
/// - 使用 `planKey + ":" + taskSeq` 作为幂等键
///   - 防止同一 Plan 的相同 Task 重复发布
/// 
/// ## 使用示例
/// 
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class TaskOutboxPublisher
///     extends AbstractOutboxPublisher<TaskQueuedEvent, TaskPayload, TaskHeaders> {
/// 
///     @Override
///     protected OutboxChannels getChannel() {
///         return OutboxChannels.INGEST_TASK_READY;
/// 
///     @Override
///     protected TaskPayload buildPayload(TaskQueuedEvent event) {
///         return TaskPayload.builder()
///             .taskId(event.getTaskId())
///             .provenanceCode(event.getProvenanceCode())
///             .operationCode(event.getOperationCode())
///             .build();
/// 
///     @Override
///     protected TaskHeaders buildHeaders(TaskQueuedEvent event) {
///         return TaskHeaders.builder()
///             .planId(event.getPlanId())
///             .sliceId(event.getSliceId())
///             .planKey(event.getPlanKey())
///             .build();
/// 
///     @Override
///     protected String buildPartitionKey(TaskQueuedEvent event) {
///         return String.valueOf(event.getTaskId());
/// 
///     @Override
///     protected String buildDedupKey(TaskQueuedEvent event) {
///         return event.getPlanKey() + ":" + event.getTaskSeq();
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.plan.publisher;
