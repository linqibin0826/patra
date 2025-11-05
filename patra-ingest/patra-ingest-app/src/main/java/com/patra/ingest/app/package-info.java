/**
 * Ingest 应用层 - 用例编排与应用服务协调。
 *
 * <p>本包是 <strong>patra-ingest</strong> 服务的应用层（Application Layer），在六边形架构中位于
 * <strong>Domain 层之上，Adapter 层之下</strong>，负责编排领域对象完成业务用例。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li><strong>用例编排</strong>: 协调领域聚合根、领域服务、仓储完成完整业务流程
 *   <li><strong>事务边界管理</strong>: 使用 {@code @Transactional} 确保业务操作的原子性
 *   <li><strong>领域事件处理</strong>: 响应领域事件，驱动跨聚合的最终一致性
 *   <li><strong>Outbox 消息发布</strong>: 通过 Transactional Outbox 模式确保事件可靠发布
 *   <li><strong>外部服务集成</strong>: 调用外部端口（如 PatraRegistryPort、ExpressionCompilerPort）
 * </ul>
 *
 * <h2>模块结构</h2>
 * <ul>
 *   <li>{@link com.patra.ingest.app.usecase.plan} - Plan 摄入用例编排
 *   <li>{@link com.patra.ingest.app.usecase.execution} - Task 执行用例协调
 *   <li>{@link com.patra.ingest.app.usecase.relay} - Outbox 中继用例编排
 *   <li>{@link com.patra.ingest.app.eventhandler} - 领域事件处理器
 *   <li>{@link com.patra.ingest.app.outbox} - Transactional Outbox 模式组件
 *   <li>{@link com.patra.ingest.app.config} - 应用层配置
 * </ul>
 *
 * <h2>架构约束</h2>
 * <ul>
 *   <li><strong>依赖方向</strong>: App → Domain（应用层可以依赖领域层，但不能反向依赖）
 *   <li><strong>端口解耦</strong>: 通过端口接口（Port）与基础设施层解耦，不直接依赖 Infra 层实现
 *   <li><strong>薄应用层</strong>: 不包含业务逻辑，所有业务规则都在 Domain 层
 *   <li><strong>无框架侵入</strong>: Domain 层不依赖 Spring 等框架，应用层负责框架集成
 * </ul>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li><strong>编排器模式（Orchestrator）</strong>: 协调多个领域对象完成复杂流程
 *   <li><strong>命令模式（Command）</strong>: 使用命令对象封装用例输入参数
 *   <li><strong>策略模式（Strategy）</strong>: 支持多种切片/批次规划策略
 *   <li><strong>模板方法模式（Template Method）</strong>: AbstractOutboxPublisher 定义通用流程
 *   <li><strong>事件驱动模式（Event-Driven）</strong>: 通过领域事件实现跨聚合协调
 * </ul>
 *
 * <h2>关键流程</h2>
 * <h3>Plan 摄入流程</h3>
 * <pre>
 * 1. PlanScheduler(Adapter) → PlanIngestionOrchestrator(App)
 * 2. 加载配置快照 + 查询游标水位
 * 3. 解析规划窗口 + 预验证
 * 4. 装配 Plan/Slice/Task（带幂等性检查）
 * 5. 持久化到数据库（事务内）
 * 6. 发布 TaskQueuedEvent 到 Outbox
 * 7. OutboxRelayOrchestrator 轮询并发布到 MQ
 * </pre>
 *
 * <h3>Task 执行流程</h3>
 * <pre>
 * 1. TaskReadyMessageListener(Adapter) → TaskExecutionUseCase(App)
 * 2. 准备执行上下文（编译表达式 + 获取租约 + 启动心跳）
 * 3. 批次规划 → 批次执行 → 游标推进
 * 4. 发布文献数据到下游
 * 5. 完成任务（释放租约 + 发布 TaskCompletedEvent）
 * </pre>
 *
 * <h2>使用示例</h2>
 * <h3>触发 Plan 摄入</h3>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class PlanSchedulerService {
 *     private final PlanIngestionUseCase planIngestionUseCase;
 *
 *     public void triggerPlanIngestion() {
 *         var command = PlanIngestionCommand.builder()
 *             .provenanceCode("pubmed")
 *             .operationCode(OperationCode.HARVEST)
 *             .triggerType(TriggerType.SCHEDULED)
 *             .windowFrom(Instant.parse("2025-01-01T00:00:00Z"))
 *             .windowTo(Instant.parse("2025-01-10T00:00:00Z"))
 *             .sliceStrategyCode("TIME")
 *             .build();
 *
 *         var result = planIngestionUseCase.ingestPlan(command);
 *         log.info("Plan created: planId={}, taskCount={}",
 *             result.getPlanId(), result.getTaskCount());
 *     }
 * }
 * }</pre>
 *
 * <h3>消费任务消息并执行</h3>
 * <pre>{@code
 * @Component
 * @RocketMQMessageListener(topic = "task-ready", consumerGroup = "ingest-worker")
 * public class TaskReadyMessageListener implements RocketMQListener<MessageExt> {
 *     private final TaskExecutionUseCase taskExecutionUseCase;
 *
 *     @Override
 *     public void onMessage(MessageExt message) {
 *         var payload = parseMessage(message);
 *         taskExecutionUseCase.executeTask(payload.getTaskId());
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 * @see com.patra.ingest.domain Domain 层 - 业务逻辑核心
 * @see com.patra.ingest.adapter Adapter 层 - 外部接口适配
 */
package com.patra.ingest.app;
