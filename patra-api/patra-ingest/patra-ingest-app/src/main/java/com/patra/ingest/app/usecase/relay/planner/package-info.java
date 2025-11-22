/// 中继计划构建器包。
///
/// 本包提供中继计划的构建逻辑，查询待发布消息并生成中继计划。
///
/// ## 职责
///
/// - 查询待发布的 Outbox 消息（状态=PENDING，notBefore<=now）
///   - 限制批次大小（如 100 条）
///   - 生成租约信息（leaseOwner、leaseExpireAt）
///   - 构建中继计划（RelayPlan）
///
/// ## 核心组件
///
/// - `RelayPlanBuilder` - 中继计划构建器
///
/// ## 查询条件
///
/// ```
///
/// SELECT * FROM outbox_message
/// WHERE status = 'PENDING'
///   AND not_before <= NOW()
///   AND (channel = ? OR ? IS NULL)  -- 可选：按通道过滤
/// ORDER BY created_at ASC
/// LIMIT ?  -- 批次大小
///
/// ```
///
/// ## 使用示例
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class RelayPlanBuilder {
///     private final OutboxMessageRepository outboxRepository;
///     private final NodeIdProvider nodeIdProvider;
///     private final OutboxRelayProperties properties;
///
///     public RelayPlan build(OutboxRelayCommand command) {
///         // 1. 查询待发布消息
///         var messages = outboxRepository.findPendingMessages(
///             command.getChannel(),
///             command.getBatchSize(),
///             Instant.now()
///         );
///
///         // 2. 生成租约信息
///         var leaseOwner = nodeIdProvider.getNodeId();
///         var leaseExpireAt = Instant.now().plus(properties.getLeaseDuration());
///
///         // 3. 构建计划
///         return RelayPlan.builder()
///             .channel(command.getChannel())
///             .messages(messages)
///             .batchSize(command.getBatchSize())
///             .leaseOwner(leaseOwner)
///             .leaseExpireAt(leaseExpireAt)
///             .build();
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.relay.planner;
