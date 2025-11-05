/**
 * 领域事件处理器包。
 *
 * <p>本包提供领域事件的订阅和处理逻辑，实现跨聚合的最终一致性协调。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>监听领域事件（使用 {@code @TransactionalEventListener}）
 *   <li>执行跨聚合状态同步（如 Task → Slice → Plan 状态传播）
 *   <li>触发后续领域事件（形成事件链）
 *   <li>处理并发冲突（使用乐观锁和重试机制）
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code TaskCompletedEventHandler} - 任务完成事件处理器
 *       <ul>
 *         <li>监听 {@link com.patra.ingest.domain.event.TaskCompletedEvent}
 *         <li>更新 Slice 状态（1:1 映射 Task 状态）
 *         <li>发布 {@link com.patra.ingest.domain.event.SliceStatusChangedEvent}
 *       </ul>
 *   <li>{@code SliceStatusChangedEventHandler} - 切片状态变更事件处理器
 *       <ul>
 *         <li>监听 {@link com.patra.ingest.domain.event.SliceStatusChangedEvent}
 *         <li>聚合 Plan 下所有 Slice 状态
 *         <li>更新 Plan 状态（如所有 Slice 完成 → Plan 完成）
 *       </ul>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><strong>事务边界</strong>: 使用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 *       确保事件在事务提交后处理
 *   <li><strong>幂等性</strong>: 事件处理器必须支持重复处理（使用版本号或状态检查）
 *   <li><strong>并发控制</strong>: 使用乐观锁（{@code @Version}）防止并发更新冲突
 *   <li><strong>失败处理</strong>: 发生 {@code OptimisticLockingFailureException} 时跳过本次更新
 * </ul>
 *
 * <h2>事件流转链路</h2>
 * <pre>
 * TaskCompletedEvent
 *   ↓ TaskCompletedEventHandler
 * SliceStatusChangedEvent
 *   ↓ SliceStatusChangedEventHandler
 * Plan 状态更新
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class TaskCompletedEventHandler {
 *     private final PlanSliceRepository sliceRepository;
 *     private final ApplicationEventPublisher eventPublisher;
 *
 *     @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *     @Transactional(propagation = Propagation.REQUIRES_NEW)
 *     public void onTaskCompleted(TaskCompletedEvent event) {
 *         try {
 *             // 1. 加载 Slice
 *             var slice = sliceRepository.findById(event.getSliceId())
 *                 .orElseThrow();
 *
 *             // 2. 更新状态（1:1 映射）
 *             var newStatus = mapTaskStatusToSliceStatus(event.getTaskStatus());
 *             slice.updateStatus(newStatus);
 *
 *             // 3. 持久化
 *             sliceRepository.save(slice);
 *
 *             // 4. 发布后续事件
 *             eventPublisher.publishEvent(
 *                 new SliceStatusChangedEvent(slice.getId(), newStatus)
 *             );
 *         } catch (OptimisticLockingFailureException e) {
 *             log.warn("Concurrent update detected, skipping: sliceId={}",
 *                 event.getSliceId());
 *         }
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 * @see com.patra.ingest.domain.event Domain 事件定义
 * @see org.springframework.transaction.event.TransactionalEventListener Spring 事务事件监听
 */
package com.patra.ingest.app.eventhandler;
