/**
 * 中继计划构建器包。
 *
 * <p>本包提供中继计划的构建逻辑，查询待发布消息并生成中继计划。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>查询待发布的 Outbox 消息（状态=PENDING，notBefore<=now）
 *   <li>限制批次大小（如 100 条）
 *   <li>生成租约信息（leaseOwner、leaseExpireAt）
 *   <li>构建中继计划（RelayPlan）
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code RelayPlanBuilder} - 中继计划构建器
 * </ul>
 *
 * <h2>查询条件</h2>
 * <pre>
 * SELECT * FROM outbox_message
 * WHERE status = 'PENDING'
 *   AND not_before <= NOW()
 *   AND (channel = ? OR ? IS NULL)  -- 可选：按通道过滤
 * ORDER BY created_at ASC
 * LIMIT ?  -- 批次大小
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class RelayPlanBuilder {
 *     private final OutboxMessageRepository outboxRepository;
 *     private final NodeIdProvider nodeIdProvider;
 *     private final OutboxRelayProperties properties;
 *
 *     public RelayPlan build(OutboxRelayCommand command) {
 *         // 1. 查询待发布消息
 *         var messages = outboxRepository.findPendingMessages(
 *             command.getChannel(),
 *             command.getBatchSize(),
 *             Instant.now()
 *         );
 *
 *         // 2. 生成租约信息
 *         var leaseOwner = nodeIdProvider.getNodeId();
 *         var leaseExpireAt = Instant.now().plus(properties.getLeaseDuration());
 *
 *         // 3. 构建计划
 *         return RelayPlan.builder()
 *             .channel(command.getChannel())
 *             .messages(messages)
 *             .batchSize(command.getBatchSize())
 *             .leaseOwner(leaseOwner)
 *             .leaseExpireAt(leaseExpireAt)
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.relay.planner;
