/**
 * Outbox 中继执行器包。
 *
 * <p>本包提供 Outbox 中继的核心执行逻辑，协调租约、发布、日志等子流程。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>执行中继计划（RelayPlan）
 *   <li>协调租约获取、消息发布、日志记录
 *   <li>返回中继批次结果（RelayBatchResult）
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code OutboxRelayExecutor} - Outbox 中继执行器
 *       <ul>
 *         <li>协调 {@link com.patra.ingest.app.usecase.relay.coordinator} 完成中继流程
 *       </ul>
 * </ul>
 *
 * <h2>执行流程</h2>
 *
 * <pre>
 * 1. 获取租约（RelayLeaseCoordinator）
 * 2. 发布消息（RelayPublishCoordinator）
 * 3. 记录日志（RelayLogCoordinator）
 * 4. 返回结果（RelayBatchResult）
 * </pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class OutboxRelayExecutor {
 *     private final RelayLeaseCoordinator leaseCoordinator;
 *     private final RelayPublishCoordinator publishCoordinator;
 *     private final RelayLogCoordinator logCoordinator;
 *
 *     public RelayBatchResult execute(RelayPlan plan) {
 *         // 1. 获取租约
 *         var leaseResult = leaseCoordinator.acquireLeases(plan);
 *
 *         // 2. 发布消息
 *         var publishResult = publishCoordinator.publish(leaseResult.getMessages());
 *
 *         // 3. 记录日志
 *         logCoordinator.recordResults(publishResult);
 *
 *         // 4. 返回结果
 *         return RelayBatchResult.builder()
 *             .channel(plan.getChannel())
 *             .fetched(leaseResult.getFetchedCount())
 *             .published(publishResult.getPublishedCount())
 *             .failed(publishResult.getFailedCount())
 *             .leaseLost(publishResult.getLeaseLostCount())
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.relay.executor;
