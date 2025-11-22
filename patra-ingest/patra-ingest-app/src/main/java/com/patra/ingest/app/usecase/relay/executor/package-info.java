/// Outbox 中继执行器包。
///
/// 本包提供 Outbox 中继的核心执行逻辑，协调租约、发布、日志等子流程。
///
/// ## 职责
///
/// - 执行中继计划（RelayPlan）
///   - 协调租约获取、消息发布、日志记录
///   - 返回中继批次结果（RelayBatchResult）
///
/// ## 核心组件
///
/// - `OutboxRelayExecutor` - Outbox 中继执行器
///
/// - 协调 {@link com.patra.ingest.app.usecase.relay.coordinator} 完成中继流程
///
/// ## 执行流程
///
/// ```
///
/// 1. 获取租约（RelayLeaseCoordinator）
/// 2. 发布消息（RelayPublishCoordinator）
/// 3. 记录日志（RelayLogCoordinator）
/// 4. 返回结果（RelayBatchResult）
///
/// ```
///
/// ## 使用示例
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class OutboxRelayExecutor {
///     private final RelayLeaseCoordinator leaseCoordinator;
///     private final RelayPublishCoordinator publishCoordinator;
///     private final RelayLogCoordinator logCoordinator;
///
///     public RelayBatchResult execute(RelayPlan plan) {
///         // 1. 获取租约
///         var leaseResult = leaseCoordinator.acquireLeases(plan);
///
///         // 2. 发布消息
///         var publishResult = publishCoordinator.publish(leaseResult.getMessages());
///
///         // 3. 记录日志
///         logCoordinator.recordResults(publishResult);
///
///         // 4. 返回结果
///         return RelayBatchResult.builder()
///             .channel(plan.getChannel())
///             .fetched(leaseResult.getFetchedCount())
///             .published(publishResult.getPublishedCount())
///             .failed(publishResult.getFailedCount())
///             .leaseLost(publishResult.getLeaseLostCount())
///             .build();
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.relay.executor;
