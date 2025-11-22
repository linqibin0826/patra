/// Outbox 中继用例编排包。
/// 
/// 本包实现 Outbox 消息的中继流程，将 Outbox 表中的待发布消息发布到 RocketMQ。 这是 Transactional Outbox Pattern
/// 的核心实现，确保领域事件的最终一致性和可靠投递。
/// 
/// ## 核心职责
/// 
/// - 轮询 Outbox 表中的待发布消息（状态为 PENDING）
///   - 获取消息租约（防止多实例并发发布同一消息）
///   - 批量发布消息到 RocketMQ
///   - 标记消息为已发布状态（PUBLISHED）
///   - 记录中继日志和指标
///   - 处理发布失败和重试逻辑
/// 
/// ## 模块结构
/// 
/// - `OutboxRelayOrchestrator` - 中继编排器（事务边界）
///   - `OutboxRelayUseCase` - 中继用例接口（供 Adapter 调用）
///   - {@link com.patra.ingest.app.usecase.relay.command} - 输入命令
///   - {@link com.patra.ingest.app.usecase.relay.dto} - 输出结果
///   - {@link com.patra.ingest.app.usecase.relay.coordinator} - 协调器（租约、日志、发布）
///   - {@link com.patra.ingest.app.usecase.relay.executor} - 中继执行器
///   - {@link com.patra.ingest.app.usecase.relay.planner} - 中继计划构建器
///   - {@link com.patra.ingest.app.usecase.relay.publisher} - 中继事件发布器
///   - {@link com.patra.ingest.app.usecase.relay.classifier} - 错误分类器
///   - {@link com.patra.ingest.app.usecase.relay.metrics} - 中继指标
///   - {@link com.patra.ingest.app.usecase.relay.config} - 中继配置
/// 
/// ## 中继流程
/// 
/// ```
/// 
/// Phase 1: 构建中继计划（RelayPlanBuilder）
///   ├─ 查询待发布消息（状态=PENDING, notBefore<=now, 按创建时间排序）
///   ├─ 限制批次大小（如 100 条）
///   └─ 生成租约信息（leaseOwner、leaseExpireAt）
/// 
/// Phase 2: 获取租约（RelayLeaseCoordinator）
///   ├─ 批量更新消息状态（PENDING → PUBLISHING）
///   ├─ 设置租约持有者和过期时间
///   └─ 使用乐观锁防止并发冲突
/// 
/// Phase 3: 发布消息（RelayPublishCoordinator）
///   ├─ 遍历消息列表
///   ├─ 构建 RocketMQ 消息（Topic、Tag、Body、Headers）
///   ├─ 发送到 RocketMQ
///   ├─ 成功 → 标记为 PUBLISHED
///   └─ 失败 → 根据错误类型决定重试或标记为 FAILED
/// 
/// Phase 4: 记录日志（RelayLogCoordinator）
///   ├─ 记录成功数、失败数、租约丢失数
///   ├─ 记录错误信息
///   └─ 更新指标（OutboxRelayMetrics）
/// 
/// Phase 5: 发布中继事件（RelayEventPublisher）
///   └─ 发布 RelayCompletedEvent（用于监控和审计）
/// 
/// ```
/// 
/// ## 关键设计
/// 
/// ### 租约机制
/// 
/// - 每条消息在中继前必须获取租约（防止并发中继）
///   - 租约有过期时间（如 5 分钟），防止中继节点宕机导致消息永久锁定
///   - 中继成功后自动释放租约（标记为 PUBLISHED）
///   - 中继失败后根据错误类型决定是否立即释放租约
/// 
/// ### 错误分类
/// 
/// - **可重试错误**:
///       
/// - 网络超时（MQClientException、RemotingException）
///         - Broker 繁忙（MQBrokerException）
///         - 处理：标记为 PENDING，等待下次中继
/// 
///   - **不可重试错误**:
///       
/// - 消息格式错误（InvalidMessageException）
///         - Topic 不存在（TopicNotExistException）
///         - 处理：标记为 FAILED，不再重试
/// 
/// ### 批量处理
/// 
/// - 每次中继处理固定数量的消息（如 100 条）
///   - 批量查询、批量更新状态（提高性能）
///   - 单条消息发送（确保每条消息的投递状态独立）
/// 
/// ### 延迟发布
/// 
/// - 支持 `notBefore` 字段（延迟发布时间）
///   - 中继时只处理 `notBefore <= now` 的消息
///   - 用于实现延迟任务、定时通知等场景
/// 
/// ## 调度策略
/// 
/// ### 定时调度
/// 
/// ```
/// 
/// @Scheduled(fixedDelay = 10000)  // 每 10 秒执行一次
/// public void scheduleRelay() {
///     outboxRelayUseCase.relay(OutboxRelayCommand.builder()
///         .batchSize(100)
///         .build());
/// }
/// 
/// ```
/// 
/// ### 按通道调度
/// 
/// ```
/// 
/// // 为不同通道配置不同的调度频率
/// @Scheduled(fixedDelay = 5000)  // 高优先级通道（如任务消息）
/// public void relayTaskChannel() {
///     outboxRelayUseCase.relay(OutboxRelayCommand.builder()
///         .channel(OutboxChannels.INGEST_TASK_READY)
///         .batchSize(200)
///         .build());
/// }
/// 
/// @Scheduled(fixedDelay = 30000)  // 低优先级通道（如日志消息）
/// public void relayLogChannel() {
///     outboxRelayUseCase.relay(OutboxRelayCommand.builder()
///         .channel(OutboxChannels.AUDIT_LOG)
///         .batchSize(50)
///         .build());
/// }
/// 
/// ```
/// 
/// ## 使用示例
/// 
/// ### 从定时任务触发中继
/// 
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class OutboxRelayScheduler {
///     private final OutboxRelayUseCase outboxRelayUseCase;
/// 
///     @Scheduled(fixedDelay = 10000)
///     public void scheduleRelay() {
///         var command = OutboxRelayCommand.builder()
///             .batchSize(100)
///             .build();
/// 
///         var report = outboxRelayUseCase.relay(command);
/// 
///         log.info("Outbox relay completed: published={, failed={",
///             report.getPublishedCount(), report.getFailedCount());
/// ```
/// 
/// ### 从 REST API 手动触发中继
/// 
/// ```java
/// @RestController
/// @RequestMapping("/api/ingest/outbox")
/// @RequiredArgsConstructor
/// public class OutboxController {
///     private final OutboxRelayUseCase outboxRelayUseCase;
/// 
///     @PostMapping("/relay")
///     public ResponseEntity<RelayReport> manualRelay(
///         @RequestParam(required = false) String channel,
///         @RequestParam(defaultValue = "100") int batchSize
///     ) {
///         var command = OutboxRelayCommand.builder()
///             .channel(channel != null ? OutboxChannels.valueOf(channel) : null)
///             .batchSize(batchSize)
///             .build();
/// 
///         var report = outboxRelayUseCase.relay(command);
/// 
///         return ResponseEntity.ok(report);
/// ```
/// 
/// ## 监控和指标
/// 
/// - **outbox.relay.published**: 成功发布的消息数
///   - **outbox.relay.failed**: 发布失败的消息数
///   - **outbox.relay.lease_lost**: 租约丢失的消息数
///   - **outbox.relay.latency**: 消息从创建到发布的延迟
///   - **outbox.relay.duration**: 中继批次的执行时长
/// 
/// ## 错误处理
/// 
/// - `LeaseLostException`: 租约丢失（被其他节点抢占）
///   - `RelayPublishException`: 发布失败（网络超时、Broker 错误）
///   - `InvalidMessageException`: 消息格式错误（不再重试）
/// 
/// @since 0.1.0
/// @author linqibin
/// @see com.patra.ingest.app.outbox Transactional Outbox 模式组件
/// @see <a href="https://microservices.io/patterns/data/transactional-outbox.html">Transactional
///     Outbox Pattern</a>
package com.patra.ingest.app.usecase.relay;
