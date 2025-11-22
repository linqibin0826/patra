/// Ingest 应用层 - 用例编排与应用服务协调。
///
/// 本包是 **patra-ingest** 服务的应用层（Application Layer），在六边形架构中位于 <strong>Domain
/// 层之上，Adapter 层之下</strong>，负责编排领域对象完成业务用例。
///
/// ## 核心职责
///
/// - **用例编排**: 协调领域聚合根、领域服务、仓储完成完整业务流程
///   - **事务边界管理**: 使用 `@Transactional` 确保业务操作的原子性
///   - **领域事件处理**: 响应领域事件，驱动跨聚合的最终一致性
///   - **Outbox 消息发布**: 通过 Transactional Outbox 模式确保事件可靠发布
///   - **外部服务集成**: 调用外部端口（如 PatraRegistryPort、ExpressionCompilerPort）
///
/// ## 模块结构
///
/// - {@link com.patra.ingest.app.usecase.plan} - Plan 摄入用例编排
///   - {@link com.patra.ingest.app.usecase.execution} - Task 执行用例协调
///   - {@link com.patra.ingest.app.usecase.relay} - Outbox 中继用例编排
///   - {@link com.patra.ingest.app.eventhandler} - 领域事件处理器
///   - {@link com.patra.ingest.app.outbox} - Transactional Outbox 模式组件
///   - {@link com.patra.ingest.app.config} - 应用层配置
///
/// ## 架构约束
///
/// - **依赖方向**: App → Domain（应用层可以依赖领域层，但不能反向依赖）
///   - **端口解耦**: 通过端口接口（Port）与基础设施层解耦，不直接依赖 Infra 层实现
///   - **薄应用层**: 不包含业务逻辑，所有业务规则都在 Domain 层
///   - **无框架侵入**: Domain 层不依赖 Spring 等框架，应用层负责框架集成
///
/// ## 设计模式
///
/// - **编排器模式（Orchestrator）**: 协调多个领域对象完成复杂流程
///   - **命令模式（Command）**: 使用命令对象封装用例输入参数
///   - **策略模式（Strategy）**: 支持多种切片/批次调度策略
///   - **模板方法模式（Template Method）**: AbstractOutboxPublisher 定义通用流程
///   - **事件驱动模式（Event-Driven）**: 通过领域事件实现跨聚合协调
///
/// ## 关键流程
///
/// ### Plan 摄入流程
///
/// ```
///
/// 1. PlanScheduler(Adapter) → PlanIngestionOrchestrator(App)
/// 2. 加载配置快照 + 查询游标水位
/// 3. 解析规划窗口 + 预验证
/// 4. 装配 Plan/Slice/Task（带幂等性检查）
/// 5. 持久化到数据库（事务内）
/// 6. 发布 TaskQueuedEvent 到 Outbox
/// 7. OutboxRelayOrchestrator 轮询并发布到 MQ
///
/// ```
///
/// ### Task 执行流程
///
/// ```
///
/// 1. TaskReadyMessageListener(Adapter) → TaskExecutionUseCase(App)
/// 2. 准备执行上下文（编译表达式 + 获取租约 + 启动心跳）
/// 3. 批次调度 → 批次执行 → 游标推进
/// 4. 发布出版物数据到下游
/// 5. 完成任务（释放租约 + 发布 TaskCompletedEvent）
///
/// ```
///
/// ## 使用示例
///
/// ### 触发 Plan 摄入
///
/// ```java
/// @Service
/// @RequiredArgsConstructor
/// public class PlanSchedulerService {
///     private final PlanIngestionUseCase planIngestionUseCase;
///
///     public void triggerPlanIngestion() {
///         var command = PlanIngestionCommand.builder()
///             .provenanceCode("pubmed")
///             .operationCode(OperationCode.HARVEST)
///             .triggerType(TriggerType.SCHEDULED)
///             .windowFrom(Instant.parse("2025-01-01T00:00:00Z"))
///             .windowTo(Instant.parse("2025-01-10T00:00:00Z"))
///             .sliceStrategyCode("TIME")
///             .build();
///
///         var result = planIngestionUseCase.ingestPlan(command);
///         log.info("Plan created: planId={, taskCount={",
///             result.getPlanId(), result.getTaskCount());
/// ```
///
/// ### 消费任务消息并执行
///
/// ```java
/// @Component
/// @RocketMQMessageListener(topic = "task-ready", consumerGroup = "ingest-worker")
/// public class TaskReadyMessageListener implements RocketMQListener<MessageExt> {
///     private final TaskExecutionUseCase taskExecutionUseCase;
///
///     @Override
///     public void onMessage(MessageExt message) {
///         var payload = parseMessage(message);
///         taskExecutionUseCase.executeTask(payload.getTaskId());
/// ```
///
/// @since 0.1.0
/// @author linqibin
/// @see com.patra.ingest.domain Domain 层 - 业务逻辑核心
/// @see com.patra.ingest.adapter Adapter 层 - 外部接口适配
package com.patra.ingest.app;
