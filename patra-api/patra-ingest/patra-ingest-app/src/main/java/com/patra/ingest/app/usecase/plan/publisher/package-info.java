/**
 * 任务发布器包。
 *
 * <p>本包提供将 Task 事件发布到 Outbox 的实现。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>将 TaskQueuedEvent 发布到 Outbox 表
 *   <li>构建任务消息的负载（{@code TaskPayload}）和消息头（{@code TaskHeaders}）
 *   <li>定义任务消息的分区策略和幂等键
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code TaskOutboxPublisher} - 任务 Outbox 发布器
 *       <ul>
 *         <li>继承自 {@link com.patra.ingest.app.outbox.core.AbstractOutboxPublisher}
 *         <li>发布到 {@code INGEST_TASK_READY} 通道
 *       </ul>
 *   <li>{@code TaskPayload} - 任务消息负载
 *       <ul>
 *         <li>{@code taskId}: 任务 ID
 *         <li>{@code provenanceCode}: 数据源代码
 *         <li>{@code operationCode}: 操作代码
 *       </ul>
 *   <li>{@code TaskHeaders} - 任务消息头
 *       <ul>
 *         <li>{@code planId}: Plan ID
 *         <li>{@code sliceId}: Slice ID
 *         <li>{@code planKey}: Plan 业务键
 *       </ul>
 * </ul>
 *
 * <h2>分区策略</h2>
 *
 * <ul>
 *   <li>按 {@code taskId} 分区，确保同一任务的消息顺序消费
 * </ul>
 *
 * <h2>幂等键策略</h2>
 *
 * <ul>
 *   <li>使用 {@code planKey + ":" + taskSeq} 作为幂等键
 *   <li>防止同一 Plan 的相同 Task 重复发布
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class TaskOutboxPublisher
 *     extends AbstractOutboxPublisher<TaskQueuedEvent, TaskPayload, TaskHeaders> {
 *
 *     @Override
 *     protected OutboxChannels getChannel() {
 *         return OutboxChannels.INGEST_TASK_READY;
 *     }
 *
 *     @Override
 *     protected TaskPayload buildPayload(TaskQueuedEvent event) {
 *         return TaskPayload.builder()
 *             .taskId(event.getTaskId())
 *             .provenanceCode(event.getProvenanceCode())
 *             .operationCode(event.getOperationCode())
 *             .build();
 *     }
 *
 *     @Override
 *     protected TaskHeaders buildHeaders(TaskQueuedEvent event) {
 *         return TaskHeaders.builder()
 *             .planId(event.getPlanId())
 *             .sliceId(event.getSliceId())
 *             .planKey(event.getPlanKey())
 *             .build();
 *     }
 *
 *     @Override
 *     protected String buildPartitionKey(TaskQueuedEvent event) {
 *         return String.valueOf(event.getTaskId());
 *     }
 *
 *     @Override
 *     protected String buildDedupKey(TaskQueuedEvent event) {
 *         return event.getPlanKey() + ":" + event.getTaskSeq();
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.plan.publisher;
