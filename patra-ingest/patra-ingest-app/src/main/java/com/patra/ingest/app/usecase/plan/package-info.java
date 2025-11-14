/**
 * Plan 摄入用例编排包。
 *
 * <p>本包实现采集计划（Plan）的完整摄入流程编排，从配置加载到任务发布。 这是 patra-ingest 服务的核心用例之一，负责将调度触发转化为可执行的任务队列。
 *
 * <h2>核心职责</h2>
 *
 * <ul>
 *   <li>加载 Provenance 配置快照（从 patra-registry）
 *   <li>查询游标水位线（确定采集起点）
 *   <li>解析规划窗口（确定采集范围）
 *   <li>构建 Plan 表达式（未编译的原始表达式）
 *   <li>预验证（窗口合法性、背压检查、容量检查）
 *   <li>装配 Plan/Slice/Task（带幂等性检查）
 *   <li>持久化到数据库（事务内）
 *   <li>发布 TaskQueuedEvent 到 Outbox
 * </ul>
 *
 * <h2>模块结构</h2>
 *
 * <ul>
 *   <li>{@code PlanIngestionOrchestrator} - 主编排器（事务边界）
 *   <li>{@code PlanIngestionUseCase} - 用例接口（供 Adapter 调用）
 *   <li>{@link com.patra.ingest.app.usecase.plan.command} - 输入命令
 *   <li>{@link com.patra.ingest.app.usecase.plan.dto} - 输出结果
 *   <li>{@link com.patra.ingest.app.usecase.plan.validator} - 预验证器
 *   <li>{@link com.patra.ingest.app.usecase.plan.assembler} - Plan 装配器
 *   <li>{@link com.patra.ingest.app.usecase.plan.slicer} - 切片规划器
 *   <li>{@link com.patra.ingest.app.usecase.plan.window} - 窗口解析器
 *   <li>{@link com.patra.ingest.app.usecase.plan.expression} - 表达式构建器
 *   <li>{@link com.patra.ingest.app.usecase.plan.publisher} - 任务发布器
 *   <li>{@code PlanPersistenceCoordinator} - 持久化协调器
 *   <li>{@code PlanIdempotencyCoordinator} - 幂等性协调器
 *   <li>{@code PlanPublishingCoordinator} - 发布协调器
 * </ul>
 *
 * <h2>编排流程</h2>
 *
 * <pre>
 * Phase 1: 准备阶段
 *   ├─ 加载 Provenance 配置快照（PatraRegistryPort）
 *   ├─ 查询游标水位线（CursorRepository）
 *   └─ 解析规划窗口（PlanningWindowResolver）
 *
 * Phase 2: 验证阶段
 *   ├─ 预验证窗口合法性（PlannerValidator）
 *   ├─ 检查背压状态（防止任务积压）
 *   └─ 检查容量限制（防止资源耗尽）
 *
 * Phase 3: 构建阶段
 *   ├─ 构建 Plan 表达式（PlanExpressionBuilder）
 *   ├─ 切片规划（SlicePlanner: TIME/DATE/SINGLE）
 *   └─ 装配 Plan/Slice/Task（PlanAssembler）
 *
 * Phase 4: 幂等性检查
 *   └─ 检查 planKey 是否已存在（PlanIdempotencyCoordinator）
 *
 * Phase 5: 持久化阶段（事务内）
 *   ├─ 持久化 Plan（PlanRepository）
 *   ├─ 持久化 Slice（PlanSliceRepository）
 *   └─ 持久化 Task（TaskRepository）
 *
 * Phase 6: 发布阶段（事务内）
 *   ├─ 收集 TaskQueuedEvent
 *   └─ 发布到 Outbox（TaskOutboxPublisher）
 * </pre>
 *
 * <h2>关键设计</h2>
 *
 * <h3>幂等性保证</h3>
 *
 * <ul>
 *   <li>使用 {@code planKey} 作为业务唯一键（provenanceCode + operationCode + windowHash）
 *   <li>相同 planKey 的重复请求会：
 *       <ul>
 *         <li>如果 Plan 状态为 READY/COMPLETED → 直接返回现有 Plan
 *         <li>如果 Plan 状态为 FAILED → 重试失败的 Task
 *       </ul>
 * </ul>
 *
 * <h3>事务边界</h3>
 *
 * <ul>
 *   <li>{@code PlanIngestionOrchestrator.ingestPlan()} 是一个完整的事务
 *   <li>确保 Plan/Slice/Task 持久化和 Outbox 发布的原子性
 *   <li>如果任何步骤失败，整个事务回滚
 * </ul>
 *
 * <h3>切片策略</h3>
 *
 * <ul>
 *   <li><strong>TIME 策略</strong>: 按时间范围切片（如每小时一个 Slice）
 *   <li><strong>DATE 策略</strong>: 按日期切片（如每天一个 Slice）
 *   <li><strong>SINGLE 策略</strong>: 不切片（整个 Plan 只有一个 Slice）
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>从定时任务触发</h3>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class PlanScheduler {
 *     private final PlanIngestionUseCase planIngestionUseCase;
 *
 *     @Scheduled(cron = "0 0 * * * ?")  // 每小时执行
 *     public void schedulePubMedHarvest() {
 *         var command = PlanIngestionCommand.builder()
 *             .provenanceCode("pubmed")
 *             .operationCode(OperationCode.HARVEST)
 *             .triggerType(TriggerType.SCHEDULED)
 *             .windowFrom(Instant.now().minus(Duration.ofHours(1)))
 *             .windowTo(Instant.now())
 *             .sliceStrategyCode("TIME")
 *             .build();
 *
 *         var result = planIngestionUseCase.ingestPlan(command);
 *
 *         log.info("Plan ingestion completed: planId={}, taskCount={}",
 *             result.getPlanId(), result.getTaskCount());
 *     }
 * }
 * }</pre>
 *
 * <h3>从 REST API 触发</h3>
 *
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/ingest/plans")
 * @RequiredArgsConstructor
 * public class PlanController {
 *     private final PlanIngestionUseCase planIngestionUseCase;
 *
 *     @PostMapping
 *     public ResponseEntity<PlanIngestionResult> ingestPlan(
 *         @RequestBody @Valid PlanIngestionRequest request
 *     ) {
 *         var command = PlanIngestionCommand.builder()
 *             .provenanceCode(request.getProvenanceCode())
 *             .operationCode(request.getOperationCode())
 *             .triggerType(TriggerType.MANUAL)
 *             .windowFrom(request.getWindowFrom())
 *             .windowTo(request.getWindowTo())
 *             .sliceStrategyCode(request.getSliceStrategy())
 *             .build();
 *
 *         var result = planIngestionUseCase.ingestPlan(command);
 *
 *         return ResponseEntity.ok(result);
 *     }
 * }
 * }</pre>
 *
 * <h2>错误处理</h2>
 *
 * <ul>
 *   <li>{@code PlanValidationException}: 窗口不合法、背压超限、容量不足
 *   <li>{@code PlanAssemblyException}: 装配失败（如切片规划错误）
 *   <li>{@code PlanPersistenceException}: 持久化失败（如数据库约束冲突）
 *   <li>{@code OutboxPublishException}: Outbox 发布失败
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 * @see com.patra.ingest.domain.model.aggregate.PlanAggregate Plan 聚合根
 * @see com.patra.ingest.domain.service.PlanDomainService Plan 领域服务
 */
package com.patra.ingest.app.usecase.plan;
