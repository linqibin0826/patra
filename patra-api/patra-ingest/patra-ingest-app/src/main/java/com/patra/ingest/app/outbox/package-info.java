/// Transactional Outbox 模式组件包。
///
/// 本包实现 **Transactional Outbox Pattern**，确保领域事件的可靠发布。
/// 通过将事件写入数据库表（与业务操作在同一事务），然后异步轮询并发布到消息队列， 解决分布式系统中的双写问题（数据库 + 消息队列）。
///
/// ## 核心职责
///
/// - 将领域事件写入 Outbox 表（事务内）
///   - 异步轮询 Outbox 表中的待发布消息
///   - 批量发布消息到 RocketMQ
///   - 标记消息为已发布状态
///   - 记录发布日志和指标
///
/// ## 模块结构
///
/// - {@link com.patra.ingest.app.outbox.core} - Outbox 核心抽象和发布逻辑
///   - {@link com.patra.ingest.app.outbox.publisher} - 具体的 Outbox 发布器实现
///   - {@link com.patra.ingest.app.outbox.config} - Outbox 配置类和属性
///   - {@link com.patra.ingest.app.outbox.constants} - Outbox 常量（通道、聚合类型、业务标签）
///   - {@link com.patra.ingest.app.outbox.metrics} - Outbox 指标收集
///
/// ## Outbox 模式流程
///
/// ```
///
/// [写入阶段] 业务事务内
/// 1. 执行业务操作（如创建 Task）
/// 2. 收集领域事件（如 TaskQueuedEvent）
/// 3. TaskOutboxPublisher.publish(events)
/// 4. 写入 outbox_message 表
/// 5. 提交事务（业务数据 + Outbox 消息同时持久化）
///
/// [中继阶段] 异步定时任务
/// 1. OutboxRelayOrchestrator 轮询 outbox_message
/// 2. 查询状态为 PENDING 的消息（带租约机制）
/// 3. 批量发布到 RocketMQ
/// 4. 标记消息为 PUBLISHED
/// 5. 记录中继日志
///
/// ```
///
/// ## 关键特性
///
/// - **At-Least-Once 保证**: 确保消息至少发布一次（消费端需实现幂等性）
///   - **租约机制**: 防止多实例并发发布同一消息
///   - **批量发布**: 支持批量发布以提高吞吐量
///   - **错误分类**: 区分可重试和不可重试错误
///   - **延迟发布**: 支持 `notBefore` 字段实现延迟发布
///   - **分区策略**: 支持自定义分区键（如按 taskId 分区）
///   - **幂等键**: 支持自定义幂等键（如 planKey + taskSeq）
///
/// ## 核心组件
///
/// - `AbstractOutboxPublisher` - Outbox 发布器抽象基类
///
/// - 提供模板方法：`publish(List<E> events)`
///         - 子类实现：`buildPayload()`、`buildHeaders()`、`buildPartitionKey()`
///
///   - `TaskOutboxPublisher` - 任务事件发布器
///
/// - 处理 `TaskQueuedEvent`
///         - 发布到 `INGEST_TASK_READY` 通道
///
///   - `PublicationEventPublisher` - 出版物事件发布器
///
/// - 处理 `PublicationDataReadyEvent`
///         - 发布到 `PUBLICATION_DATA_READY` 通道
///
/// ## 配置示例
///
/// ```
///
/// patra:
///   ingest:
///     outbox:
///       publisher:
///         enabled: true
///         batch-size: 50
///         timeout: 5s
///       relay:
///         batch-size: 100
///         lease-duration: 5m
///         polling-interval: 10s
///         retry-max-attempts: 3
///
/// ```
///
/// ## 使用示例
///
/// ### 定义 Outbox 发布器
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class TaskOutboxPublisher
///     extends AbstractOutboxPublisher<TaskQueuedEvent, TaskPayload, TaskHeaders> {
///
///     @Override
///     protected OutboxAggregateTypes getAggregateType() {
///         return OutboxAggregateTypes.TASK;
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
///             .build();
///
///     @Override
///     protected TaskHeaders buildHeaders(TaskQueuedEvent event) {
///         return TaskHeaders.builder()
///             .planId(event.getPlanId())
///             .sliceId(event.getSliceId())
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
/// ### 在编排器中使用
///
/// ```java
/// @Service
/// @RequiredArgsConstructor
/// @Transactional
/// public class PlanIngestionOrchestrator {
///     private final TaskOutboxPublisher taskOutboxPublisher;
///
///     public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
///         // 1. 装配 Plan/Slice/Task
///         var assembly = assemblePlan(command);
///
///         // 2. 持久化到数据库
///         persistenceCoordinator.persistPlan(assembly);
///
///         // 3. 收集领域事件
///         var events = assembly.getTasks().stream()
///             .map(task -> new TaskQueuedEvent(task.getId(), task.getPlanId(), ...))
///             .toList();
///
///         // 4. 发布到 Outbox（同一事务内）
///         var publishResult = taskOutboxPublisher.publish(events);
///
///         return PlanIngestionResult.success(assembly.getPlanId(), events.size());
/// ```
///
/// @since 0.1.0
/// @author linqibin
/// @see <a href="https://microservices.io/patterns/data/transactional-outbox.html">Transactional
///     Outbox Pattern</a>
package com.patra.ingest.app.outbox;
