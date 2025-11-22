/// Plan 预验证器包。
///
/// 本包提供 Plan 摄入前的预验证逻辑，在装配前检查窗口合法性、背压状态、容量限制。
///
/// ## 职责
///
/// - 验证规划窗口的合法性（起始时间 < 结束时间、窗口大小合理）
///   - 检查背压状态（待处理任务数是否超过阈值）
///   - 检查容量限制（是否有足够资源处理新 Plan）
///   - 提前拒绝不合法的请求（快速失败）
///
/// ## 核心组件
///
/// - `PlannerValidator` - Plan 验证器接口
///   - `PlannerValidatorImpl` - Plan 验证器实现
///
/// ## 验证规则
///
/// - **窗口合法性**:
///
/// - windowFrom < windowTo
///         - 窗口大小不超过配置的最大值（如 30 天）
///         - 窗口不能在未来（超过当前时间）
///
///   - **背压检查**:
///
/// - 查询该数据源待处理的 Task 数量
///         - 如果超过阈值（如 10000）→ 拒绝新 Plan
///
///   - **容量检查**:
///
/// - 检查数据库连接池可用性
///         - 检查消息队列积压情况
///
/// ## 使用示例
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class PlannerValidatorImpl implements PlannerValidator {
///     private final TaskRepository taskRepository;
///     private final PlannerProperties properties;
///
///     @Override
///     public void validate(PlannerWindow window, ProvenanceCode provenanceCode) {
///         // 1. 验证窗口合法性
///         if (window.getFrom().isAfter(window.getTo())) {
///             throw new PlanValidationException("Invalid window: from > to");
///
///         // 2. 检查窗口大小
///         var windowSize = Duration.between(window.getFrom(), window.getTo());
///         if (windowSize.compareTo(properties.getMaxWindowSize()) > 0) {
///             throw new PlanValidationException("Window size exceeds limit: " + windowSize);
///
///         // 3. 检查背压
///         var pendingTaskCount = taskRepository.countPendingTasks(provenanceCode);
///         if (pendingTaskCount > properties.getBackpressureThreshold()) {
///             throw new PlanValidationException("Backpressure detected: " + pendingTaskCount);
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.plan.validator;
