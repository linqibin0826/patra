/// 任务执行完成阶段包。
///
/// 本包实现任务执行的完成阶段，包括状态更新、租约释放、事件发布。
///
/// ## 职责
///
/// - 更新任务状态（SUCCEEDED/FAILED）
///   - 停止心跳续约
///   - 释放任务租约
///   - 发布 TaskCompletedEvent（触发 Slice/Plan 状态更新）
///
/// ## 核心组件
///
/// - `CompleteTaskExecutionUseCase` - 完成执行用例接口
///   - `CompleteTaskExecutionUseCaseImpl` - 完成执行用例实现
///
/// ## 完成流程
///
/// ```
///
/// 1. 停止心跳（HeartbeatRenewalService）
///    └─ 停止后台心跳续约线程
///
/// 2. 更新任务状态（TaskRepository）
///    ├─ 设置任务状态为 SUCCEEDED/FAILED
///    ├─ 记录完成时间和耗时
///    └─ 使用乐观锁防止并发冲突
///
/// 3. 释放租约（LeaseManagementService）
///    └─ 主动释放租约（不等待过期）
///
/// 4. 发布事件（ApplicationEventPublisher）
///    └─ 发布 TaskCompletedEvent（触发 Slice/Plan 状态更新）
///
/// ```
///
/// ## 成功完成 vs 失败完成
///
/// - **成功完成**:
///
/// - 状态：`SUCCEEDED`
///         - 游标：推进到新的水位线
///         - 事件：`TaskCompletedEvent(status=SUCCEEDED)`
///
///   - **失败完成**:
///
/// - 状态：`FAILED`
///         - 游标：不推进（下次重试从原位置继续）
///         - 事件：`TaskCompletedEvent(status=FAILED)`
///         - 错误信息：记录失败原因
///
/// ## 使用示例
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class CompleteTaskExecutionUseCaseImpl implements CompleteTaskExecutionUseCase {
///     private final HeartbeatRenewalService heartbeatService;
///     private final TaskRepository taskRepository;
///     private final LeaseManagementService leaseService;
///     private final ApplicationEventPublisher eventPublisher;
///
///     @Override
///     public void complete(ExecutionSession session, TaskStatus finalStatus, String errorMessage)
// {
///         var taskId = session.getContext().getTaskId();
///
///         try {
///             // 1. 停止心跳
///             heartbeatService.stop(session);
///
///             // 2. 更新任务状态
///             var task = taskRepository.findById(taskId).orElseThrow();
///             task.updateStatus(finalStatus, errorMessage);
///             task.recordCompletionTime(Instant.now());
///             taskRepository.save(task);
///
///             // 3. 释放租约
///             leaseService.releaseLease(session.getLease());
///
///             // 4. 发布事件
///             eventPublisher.publishEvent(
///                 new TaskCompletedEvent(taskId, finalStatus, session.getSliceId())
///             );
///
///             log.info("Task completed: taskId={, status={", taskId, finalStatus); catch
// (Exception e) {
///             log.error("Failed to complete task: taskId={", taskId, e);
///             // 确保租约被释放（防止资源泄漏）
///             try {
///                 leaseService.releaseLease(session.getLease()); catch (Exception ex) {
///                 log.error("Failed to release lease: taskId={", taskId, ex);
///             throw new TaskCompletionException("Failed to complete task", e);
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.execution.complete;
