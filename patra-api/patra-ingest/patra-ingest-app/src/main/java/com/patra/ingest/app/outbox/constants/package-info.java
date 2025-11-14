/**
 * Outbox 常量定义包。
 *
 * <p>本包定义 Outbox 模式使用的枚举常量，确保通道、聚合类型、业务标签的一致性。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义 Outbox 消息通道（RocketMQ Topic）
 *   <li>定义聚合类型（区分不同领域对象的事件）
 *   <li>定义业务标签（用于消息过滤和分类）
 *   <li>提供常量的集中管理和文档
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code OutboxChannels} - Outbox 消息通道枚举
 *       <ul>
 *         <li>{@code INGEST_TASK_READY}: 任务准备就绪通道
 *         <li>{@code INGEST_LITERATURE_READY}: 文献数据就绪通道
 *         <li>{@code METADATA_RECORD_RETRY}: 元数据重试通道
 *       </ul>
 *   <li>{@code OutboxAggregateTypes} - 聚合类型枚举
 *       <ul>
 *         <li>{@code PLAN}: Plan 聚合
 *         <li>{@code SLICE}: Slice 聚合
 *         <li>{@code TASK}: Task 聚合
 *         <li>{@code METADATA_RECORD}: 元数据记录聚合
 *       </ul>
 *   <li>{@code OutboxBusinessTags} - 业务标签枚举
 *       <ul>
 *         <li>{@code TASK_QUEUED}: 任务入队操作
 *         <li>{@code LITERATURE_PUBLISHED}: 文献发布操作
 *         <li>{@code METADATA_RETRY}: 元数据重试操作
 *       </ul>
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>类型安全</strong>: 使用枚举替代魔法字符串
 *   <li><strong>集中管理</strong>: 所有通道和标签在一处定义
 *   <li><strong>文档化</strong>: 每个枚举值都有详细的 Javadoc 说明
 *   <li><strong>版本兼容</strong>: 枚举值一旦定义不可删除，只能新增
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>在 Outbox 发布器中使用</h3>
 *
 * <pre>{@code
 * @Component
 * public class TaskOutboxPublisher extends AbstractOutboxPublisher<...> {
 *
 *     @Override
 *     protected OutboxChannels getChannel() {
 *         return OutboxChannels.INGEST_TASK_READY;
 *     }
 *
 *     @Override
 *     protected OutboxAggregateTypes getAggregateType() {
 *         return OutboxAggregateTypes.TASK;
 *     }
 *
 *     @Override
 *     protected OutboxBusinessTags getOperationType() {
 *         return OutboxBusinessTags.TASK_QUEUED;
 *     }
 * }
 * }</pre>
 *
 * <h3>在中继日志中使用</h3>
 *
 * <pre>{@code
 * @Service
 * public class OutboxRelayOrchestrator {
 *
 *     public RelayReport relay(OutboxRelayCommand command) {
 *         // 查询待发布消息
 *         var messages = outboxRepository.findPendingMessages(
 *             OutboxChannels.INGEST_TASK_READY.name(),
 *             batchSize
 *         );
 *
 *         // 发布到 MQ
 *         messages.forEach(msg -> {
 *             rocketMQTemplate.send(
 *                 OutboxChannels.INGEST_TASK_READY.getTopic(),
 *                 msg.getPayload()
 *             );
 *         });
 *     }
 * }
 * }</pre>
 *
 * <h2>常量命名规范</h2>
 *
 * <ul>
 *   <li><strong>通道命名</strong>: {SERVICE}_{ENTITY}_{ACTION}
 *       <ul>
 *         <li>示例: {@code INGEST_TASK_READY}, {@code INGEST_LITERATURE_READY}
 *       </ul>
 *   <li><strong>聚合类型</strong>: 使用领域模型名称
 *       <ul>
 *         <li>示例: {@code PLAN}, {@code TASK}, {@code SLICE}
 *       </ul>
 *   <li><strong>业务标签</strong>: {ENTITY}_{OPERATION}
 *       <ul>
 *         <li>示例: {@code TASK_QUEUED}, {@code LITERATURE_PUBLISHED}
 *       </ul>
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.outbox.constants;
