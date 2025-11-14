/**
 * 任务执行完成阶段包。
 *
 * <p>本包实现任务执行的完成阶段，包括状态更新、租约释放、事件发布。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>更新任务状态（SUCCEEDED/FAILED）
 *   <li>停止心跳续约
 *   <li>释放任务租约
 *   <li>发布 TaskCompletedEvent（触发 Slice/Plan 状态更新）
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code CompleteTaskExecutionUseCase} - 完成执行用例接口
 *   <li>{@code CompleteTaskExecutionUseCaseImpl} - 完成执行用例实现
 * </ul>
 *
 * <h2>完成流程</h2>
 *
 * <pre>
 * 1. 停止心跳（HeartbeatRenewalService）
 *    └─ 停止后台心跳续约线程
 *
 * 2. 更新任务状态（TaskRepository）
 *    ├─ 设置任务状态为 SUCCEEDED/FAILED
 *    ├─ 记录完成时间和耗时
 *    └─ 使用乐观锁防止并发冲突
 *
 * 3. 释放租约（LeaseManagementService）
 *    └─ 主动释放租约（不等待过期）
 *
 * 4. 发布事件（ApplicationEventPublisher）
 *    └─ 发布 TaskCompletedEvent（触发 Slice/Plan 状态更新）
 * </pre>
 *
 * <h2>成功完成 vs 失败完成</h2>
 *
 * <ul>
 *   <li><strong>成功完成</strong>:
 *       <ul>
 *         <li>状态：{@code SUCCEEDED}
 *         <li>游标：推进到新的水位线
 *         <li>事件：{@code TaskCompletedEvent(status=SUCCEEDED)}
 *       </ul>
 *   <li><strong>失败完成</strong>:
 *       <ul>
 *         <li>状态：{@code FAILED}
 *         <li>游标：不推进（下次重试从原位置继续）
 *         <li>事件：{@code TaskCompletedEvent(status=FAILED)}
 *         <li>错误信息：记录失败原因
 *       </ul>
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class CompleteTaskExecutionUseCaseImpl implements CompleteTaskExecutionUseCase {
 *     private final HeartbeatRenewalService heartbeatService;
 *     private final TaskRepository taskRepository;
 *     private final LeaseManagementService leaseService;
 *     private final ApplicationEventPublisher eventPublisher;
 *
 *     @Override
 *     public void complete(ExecutionSession session, TaskStatus finalStatus, String errorMessage) {
 *         var taskId = session.getContext().getTaskId();
 *
 *         try {
 *             // 1. 停止心跳
 *             heartbeatService.stop(session);
 *
 *             // 2. 更新任务状态
 *             var task = taskRepository.findById(taskId).orElseThrow();
 *             task.updateStatus(finalStatus, errorMessage);
 *             task.recordCompletionTime(Instant.now());
 *             taskRepository.save(task);
 *
 *             // 3. 释放租约
 *             leaseService.releaseLease(session.getLease());
 *
 *             // 4. 发布事件
 *             eventPublisher.publishEvent(
 *                 new TaskCompletedEvent(taskId, finalStatus, session.getSliceId())
 *             );
 *
 *             log.info("Task completed: taskId={}, status={}", taskId, finalStatus);
 *         } catch (Exception e) {
 *             log.error("Failed to complete task: taskId={}", taskId, e);
 *             // 确保租约被释放（防止资源泄漏）
 *             try {
 *                 leaseService.releaseLease(session.getLease());
 *             } catch (Exception ex) {
 *                 log.error("Failed to release lease: taskId={}", taskId, ex);
 *             }
 *             throw new TaskCompletionException("Failed to complete task", e);
 *         }
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.execution.complete;
