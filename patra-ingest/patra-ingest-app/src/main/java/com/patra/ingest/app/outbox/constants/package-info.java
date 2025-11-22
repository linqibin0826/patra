/// Outbox 常量定义包。
/// 
/// 本包定义 Outbox 模式使用的枚举常量，确保聚合类型的一致性。
/// 
/// **注意**：自 v0.2.0 起，Channel 和 OpType 已迁移至新架构：
/// 
/// - **Channel**：使用 {@link
///       com.patra.ingest.domain.messaging.IngestPublishingChannels}（粗粒度资源级别）
///   - **OpType**：使用 {@link com.patra.ingest.app.outbox.operations} 包下的枚举（如
///       TaskOperations、PublicationOperations）
/// 
/// ## 职责
/// 
/// - 定义聚合类型（区分不同领域对象的事件）
///   - 提供常量的集中管理和文档
/// 
/// ## 核心组件
/// 
/// - `OutboxAggregateTypes` - 聚合类型枚举
///       
/// - `PLAN`: Plan 聚合
///         - `SLICE`: Slice 聚合
///         - `TASK`: Task 聚合
///         - `METADATA_RECORD`: 元数据记录聚合
/// 
/// ## 设计原则
/// 
/// - **类型安全**: 使用枚举替代魔法字符串
///   - **集中管理**: 所有通道和标签在一处定义
///   - **文档化**: 每个枚举值都有详细的 Javadoc 说明
///   - **版本兼容**: 枚举值一旦定义不可删除，只能新增
/// 
/// ## 使用示例
/// 
/// ### 在 Outbox 发布器中使用
/// 
/// ```java
/// @Component
/// public class TaskOutboxPublisher extends AbstractOutboxPublisher<...> {
/// 
///     @Override
///     protected IngestPublishingChannels getChannel() {
///         return IngestPublishingChannels.TASK;  // 粗粒度 Channel
/// 
///     @Override
///     protected OutboxAggregateTypes getAggregateType() {
///         return OutboxAggregateTypes.TASK;
/// 
///     @Override
///     protected OperationType getOperationType(TaskQueuedEvent event) {
///         return TaskOperations.READY;  // 细粒度 OpType
/// ```
/// 
/// ## 常量命名规范
/// 
/// - **聚合类型**: 使用领域模型名称
///       
/// - 示例: `PLAN`, `TASK`, `SLICE`
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.outbox.constants;
