/**
 * 幂等性检查器包。
 *
 * <p>本包提供任务执行的幂等性检查，防止重复消费任务消息。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>检查任务是否已成功完成
 *   <li>防止重复消费任务消息
 *   <li>支持快速幂等跳过（不执行任何逻辑）
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code IdempotencyChecker} - 幂等性检查器接口
 *   <li>{@code IdempotencyCheckerImpl} - 幂等性检查器实现
 * </ul>
 *
 * <h2>检查策略</h2>
 *
 * <pre>
 * 1. 查询任务状态
 *    └─ TaskRepository.findById(taskId)
 *
 * 2. 判断任务状态
 *    ├─ SUCCEEDED → 返回 true（已完成，幂等跳过）
 *    ├─ FAILED → 返回 false（需要重试）
 *    └─ PENDING/RUNNING → 返回 false（首次执行或执行中）
 * </pre>
 *
 * <h2>幂等键设计</h2>
 *
 * <ul>
 *   <li><strong>任务级别</strong>: {@code taskId}（防止同一任务重复执行）
 *   <li><strong>消息级别</strong>: {@code idempotentKey}（如 planKey + taskSeq）
 *       <ul>
 *         <li>用于消息队列的幂等去重
 *         <li>防止同一消息被多次消费
 *       </ul>
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <h3>场景 1: 消息重复消费</h3>
 *
 * <pre>
 * 原因：MQ 至少一次语义（At-Least-Once）
 * 表现：同一任务消息被多次投递
 * 处理：幂等性检查 → 任务状态已为 SUCCEEDED → 快速跳过
 * </pre>
 *
 * <h3>场景 2: 任务重试</h3>
 *
 * <pre>
 * 原因：任务执行失败（如网络超时）
 * 表现：任务状态为 FAILED
 * 处理：幂等性检查 → 任务状态为 FAILED → 允许重新执行
 * </pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class IdempotencyCheckerImpl implements IdempotencyChecker {
 *     private final TaskRepository taskRepository;
 *
 *     @Override
 *     public boolean isAlreadySucceeded(Long taskId) {
 *         // 1. 查询任务状态
 *         var task = taskRepository.findById(taskId)
 *             .orElseThrow(() -> new TaskNotFoundException(taskId));
 *
 *         // 2. 判断状态
 *         var status = task.getStatus();
 *
 *         if (status == TaskStatus.SUCCEEDED) {
 *             log.info("Task already succeeded (idempotent skip): taskId={}", taskId);
 *             return true;
 *         }
 *
 *         if (status == TaskStatus.FAILED) {
 *             log.info("Task failed before, will retry: taskId={}", taskId);
 *             return false;
 *         }
 *
 *         if (status == TaskStatus.RUNNING) {
 *             log.warn("Task is running (possible concurrent execution): taskId={}", taskId);
 *             return false;  // 租约机制会处理并发问题
 *         }
 *
 *         // PENDING
 *         return false;
 *     }
 *
 *     @Override
 *     public boolean isIdempotentKeyProcessed(String idempotentKey) {
 *         // 可选：额外的消息级别幂等检查
 *         // 存储在 Redis: SET idempotent:key:{key} 1 EX 86400
 *         return idempotentKeyCache.exists(idempotentKey);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.execution.idempotency;
