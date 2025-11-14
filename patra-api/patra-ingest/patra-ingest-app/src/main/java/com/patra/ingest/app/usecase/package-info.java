/**
 * 用例编排层根包。
 *
 * <p>本包包含 patra-ingest 服务的所有业务用例编排器（Orchestrator）和协调器（Coordinator）。
 *
 * <h2>子模块</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.app.usecase.plan} - Plan 摄入用例编排
 *       <ul>
 *         <li>配置加载、窗口解析、表达式构建、预验证、计划装配、任务发布
 *       </ul>
 *   <li>{@link com.patra.ingest.app.usecase.execution} - Task 执行用例协调
 *       <ul>
 *         <li>执行准备、租约管理、批次规划、批次执行、游标推进、任务完成
 *       </ul>
 *   <li>{@link com.patra.ingest.app.usecase.relay} - Outbox 中继用例编排
 *       <ul>
 *         <li>消息轮询、租约管理、批量发布、日志记录、错误分类
 *       </ul>
 * </ul>
 *
 * <h2>核心概念</h2>
 *
 * <h3>编排器（Orchestrator）</h3>
 *
 * <ul>
 *   <li>协调多个领域对象、仓储、外部服务完成完整业务流程
 *   <li>管理事务边界（使用 {@code @Transactional}）
 *   <li>不包含业务逻辑（业务规则在 Domain 层）
 *   <li>示例：{@code PlanIngestionOrchestrator}、{@code OutboxRelayOrchestrator}
 * </ul>
 *
 * <h3>协调器（Coordinator）</h3>
 *
 * <ul>
 *   <li>负责特定子流程的协调（如持久化、发布、幂等性检查）
 *   <li>被编排器调用，实现关注点分离
 *   <li>示例：{@code PlanPersistenceCoordinator}、{@code PlanIdempotencyCoordinator}
 * </ul>
 *
 * <h3>用例接口（UseCase）</h3>
 *
 * <ul>
 *   <li>定义用例的公共接口（门面模式）
 *   <li>供 Adapter 层调用，隐藏实现细节
 *   <li>示例：{@code PlanIngestionUseCase}、{@code TaskExecutionUseCase}
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>单一职责</strong>: 每个编排器负责一个完整业务用例
 *   <li><strong>协调而非实现</strong>: 编排器不包含业务逻辑，只协调领域对象
 *   <li><strong>事务边界清晰</strong>: 编排器方法是事务的边界
 *   <li><strong>依赖倒置</strong>: 通过端口接口与基础设施解耦
 * </ul>
 *
 * <h2>典型编排流程</h2>
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * @Transactional
 * public class PlanIngestionOrchestrator implements PlanIngestionUseCase {
 *
 *     // 1. 依赖注入（通过构造器）
 *     private final PlanRepository planRepository;
 *     private final PatraRegistryPort registryPort;
 *     private final PlanPersistenceCoordinator persistenceCoordinator;
 *     private final PlanPublishingCoordinator publishingCoordinator;
 *
 *     // 2. 编排主流程
 *     public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
 *         // Step 1: 加载配置快照
 *         var configSnapshot = registryPort.loadConfig(command.getProvenanceCode());
 *
 *         // Step 2: 装配 Plan/Slice/Task
 *         var assembly = assemblePlan(command, configSnapshot);
 *
 *         // Step 3: 持久化（委派给协调器）
 *         var persistedPlan = persistenceCoordinator.persistPlan(assembly);
 *
 *         // Step 4: 发布事件（委派给协调器）
 *         publishingCoordinator.publishTaskEvents(persistedPlan, assembly.getTasks());
 *
 *         // Step 5: 返回结果
 *         return PlanIngestionResult.success(persistedPlan.getId(), assembly.getTaskCount());
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase;
