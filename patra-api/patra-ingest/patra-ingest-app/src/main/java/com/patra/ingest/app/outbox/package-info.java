/**
 * Transactional Outbox 模式组件包。
 *
 * <p>本包实现 <strong>Transactional Outbox Pattern</strong>，确保领域事件的可靠发布。
 * 通过将事件写入数据库表（与业务操作在同一事务），然后异步轮询并发布到消息队列， 解决分布式系统中的双写问题（数据库 + 消息队列）。
 *
 * <h2>核心职责</h2>
 *
 * <ul>
 *   <li>将领域事件写入 Outbox 表（事务内）
 *   <li>异步轮询 Outbox 表中的待发布消息
 *   <li>批量发布消息到 RocketMQ
 *   <li>标记消息为已发布状态
 *   <li>记录发布日志和指标
 * </ul>
 *
 * <h2>模块结构</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.app.outbox.core} - Outbox 核心抽象和发布逻辑
 *   <li>{@link com.patra.ingest.app.outbox.publisher} - 具体的 Outbox 发布器实现
 *   <li>{@link com.patra.ingest.app.outbox.config} - Outbox 配置类和属性
 *   <li>{@link com.patra.ingest.app.outbox.constants} - Outbox 常量（通道、聚合类型、业务标签）
 *   <li>{@link com.patra.ingest.app.outbox.metrics} - Outbox 指标收集
 * </ul>
 *
 * <h2>Outbox 模式流程</h2>
 *
 * <pre>
 * [写入阶段] 业务事务内
 * 1. 执行业务操作（如创建 Task）
 * 2. 收集领域事件（如 TaskQueuedEvent）
 * 3. TaskOutboxPublisher.publish(events)
 * 4. 写入 outbox_message 表
 * 5. 提交事务（业务数据 + Outbox 消息同时持久化）
 *
 * [中继阶段] 异步定时任务
 * 1. OutboxRelayOrchestrator 轮询 outbox_message
 * 2. 查询状态为 PENDING 的消息（带租约机制）
 * 3. 批量发布到 RocketMQ
 * 4. 标记消息为 PUBLISHED
 * 5. 记录中继日志
 * </pre>
 *
 * <h2>关键特性</h2>
 *
 * <ul>
 *   <li><strong>At-Least-Once 保证</strong>: 确保消息至少发布一次（消费端需实现幂等性）
 *   <li><strong>租约机制</strong>: 防止多实例并发发布同一消息
 *   <li><strong>批量发布</strong>: 支持批量发布以提高吞吐量
 *   <li><strong>错误分类</strong>: 区分可重试和不可重试错误
 *   <li><strong>延迟发布</strong>: 支持 {@code notBefore} 字段实现延迟发布
 *   <li><strong>分区策略</strong>: 支持自定义分区键（如按 taskId 分区）
 *   <li><strong>幂等键</strong>: 支持自定义幂等键（如 planKey + taskSeq）
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code AbstractOutboxPublisher} - Outbox 发布器抽象基类
 *       <ul>
 *         <li>提供模板方法：{@code publish(List<E> events)}
 *         <li>子类实现：{@code buildPayload()}、{@code buildHeaders()}、{@code buildPartitionKey()}
 *       </ul>
 *   <li>{@code TaskOutboxPublisher} - 任务事件发布器
 *       <ul>
 *         <li>处理 {@code TaskQueuedEvent}
 *         <li>发布到 {@code INGEST_TASK_READY} 通道
 *       </ul>
 *   <li>{@code LiteratureEventPublisher} - 文献事件发布器
 *       <ul>
 *         <li>处理 {@code LiteratureDataReadyEvent}
 *         <li>发布到 {@code LITERATURE_DATA_READY} 通道
 *       </ul>
 * </ul>
 *
 * <h2>配置示例</h2>
 *
 * <pre>
 * patra:
 *   ingest:
 *     outbox:
 *       publisher:
 *         enabled: true
 *         batch-size: 50
 *         timeout: 5s
 *       relay:
 *         batch-size: 100
 *         lease-duration: 5m
 *         polling-interval: 10s
 *         retry-max-attempts: 3
 * </pre>
 *
 * <h2>使用示例</h2>
 *
 * <h3>定义 Outbox 发布器</h3>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class TaskOutboxPublisher
 *     extends AbstractOutboxPublisher<TaskQueuedEvent, TaskPayload, TaskHeaders> {
 *
 *     @Override
 *     protected OutboxAggregateTypes getAggregateType() {
 *         return OutboxAggregateTypes.TASK;
 *     }
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
 *             .build();
 *     }
 *
 *     @Override
 *     protected TaskHeaders buildHeaders(TaskQueuedEvent event) {
 *         return TaskHeaders.builder()
 *             .planId(event.getPlanId())
 *             .sliceId(event.getSliceId())
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
 * <h3>在编排器中使用</h3>
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * @Transactional
 * public class PlanIngestionOrchestrator {
 *     private final TaskOutboxPublisher taskOutboxPublisher;
 *
 *     public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
 *         // 1. 装配 Plan/Slice/Task
 *         var assembly = assemblePlan(command);
 *
 *         // 2. 持久化到数据库
 *         persistenceCoordinator.persistPlan(assembly);
 *
 *         // 3. 收集领域事件
 *         var events = assembly.getTasks().stream()
 *             .map(task -> new TaskQueuedEvent(task.getId(), task.getPlanId(), ...))
 *             .toList();
 *
 *         // 4. 发布到 Outbox（同一事务内）
 *         var publishResult = taskOutboxPublisher.publish(events);
 *
 *         return PlanIngestionResult.success(assembly.getPlanId(), events.size());
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 * @see <a href="https://microservices.io/patterns/data/transactional-outbox.html">Transactional
 *     Outbox Pattern</a>
 */
package com.patra.ingest.app.outbox;
