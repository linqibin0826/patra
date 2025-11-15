/**
 * Outbox 常量定义包。
 *
 * <p>本包定义 Outbox 模式使用的枚举常量，确保聚合类型的一致性。
 *
 * <p><b>注意</b>：自 v0.2.0 起，Channel 和 OpType 已迁移至新架构：
 * <ul>
 *   <li><b>Channel</b>：使用 {@link com.patra.ingest.domain.messaging.IngestPublishingChannels}（粗粒度资源级别）
 *   <li><b>OpType</b>：使用 {@link com.patra.ingest.app.outbox.operations} 包下的枚举（如 TaskOperations、LiteratureOperations）
 * </ul>
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义聚合类型（区分不同领域对象的事件）
 *   <li>提供常量的集中管理和文档
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code OutboxAggregateTypes} - 聚合类型枚举
 *       <ul>
 *         <li>{@code PLAN}: Plan 聚合
 *         <li>{@code SLICE}: Slice 聚合
 *         <li>{@code TASK}: Task 聚合
 *         <li>{@code METADATA_RECORD}: 元数据记录聚合
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
 *     protected IngestPublishingChannels getChannel() {
 *         return IngestPublishingChannels.TASK;  // 粗粒度 Channel
 *     }
 *
 *     @Override
 *     protected OutboxAggregateTypes getAggregateType() {
 *         return OutboxAggregateTypes.TASK;
 *     }
 *
 *     @Override
 *     protected OperationType getOperationType(TaskQueuedEvent event) {
 *         return TaskOperations.READY;  // 细粒度 OpType
 *     }
 * }
 * }</pre>
 *
 * <h2>常量命名规范</h2>
 *
 * <ul>
 *   <li><strong>聚合类型</strong>: 使用领域模型名称
 *       <ul>
 *         <li>示例: {@code PLAN}, {@code TASK}, {@code SLICE}
 *       </ul>
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.outbox.constants;
