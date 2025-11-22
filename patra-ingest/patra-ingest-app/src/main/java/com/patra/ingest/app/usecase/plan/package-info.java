/// Plan 摄入用例编排包。
/// 
/// 本包实现采集计划（Plan）的完整摄入流程编排，从配置加载到任务发布。 这是 patra-ingest 服务的核心用例之一，负责将调度触发转化为可执行的任务队列。
/// 
/// ## 核心职责
/// 
/// - 加载 Provenance 配置快照（从 patra-registry）
///   - 查询游标水位线（确定采集起点）
///   - 解析规划窗口（确定采集范围）
///   - 构建 Plan 表达式（未编译的原始表达式）
///   - 预验证（窗口合法性、背压检查、容量检查）
///   - 装配 Plan/Slice/Task（带幂等性检查）
///   - 持久化到数据库（事务内）
///   - 发布 TaskQueuedEvent 到 Outbox
/// 
/// ## 模块结构
/// 
/// - `PlanIngestionOrchestrator` - 主编排器（事务边界）
///   - `PlanIngestionUseCase` - 用例接口（供 Adapter 调用）
///   - {@link com.patra.ingest.app.usecase.plan.command} - 输入命令
///   - {@link com.patra.ingest.app.usecase.plan.dto} - 输出结果
///   - {@link com.patra.ingest.app.usecase.plan.validator} - 预验证器
///   - {@link com.patra.ingest.app.usecase.plan.assembler} - Plan 装配器
///   - {@link com.patra.ingest.app.usecase.plan.slicer} - 切片规划器
///   - {@link com.patra.ingest.app.usecase.plan.window} - 窗口解析器
///   - {@link com.patra.ingest.app.usecase.plan.expression} - 表达式构建器
///   - {@link com.patra.ingest.app.usecase.plan.publisher} - 任务发布器
///   - `PlanPersistenceCoordinator` - 持久化协调器
///   - `PlanIdempotencyCoordinator` - 幂等性协调器
///   - `PlanPublishingCoordinator` - 发布协调器
/// 
/// ## 编排流程
/// 
/// ```
/// 
/// Phase 1: 准备阶段
///   ├─ 加载 Provenance 配置快照（PatraRegistryPort）
///   ├─ 查询游标水位线（CursorRepository）
///   └─ 解析规划窗口（PlanningWindowResolver）
/// 
/// Phase 2: 验证阶段
///   ├─ 预验证窗口合法性（PlannerValidator）
///   ├─ 检查背压状态（防止任务积压）
///   └─ 检查容量限制（防止资源耗尽）
/// 
/// Phase 3: 构建阶段
///   ├─ 构建 Plan 表达式（PlanExpressionBuilder）
///   ├─ 切片规划（SlicePlanner: TIME/DATE/SINGLE）
///   └─ 装配 Plan/Slice/Task（PlanAssembler）
/// 
/// Phase 4: 幂等性检查
///   └─ 检查 planKey 是否已存在（PlanIdempotencyCoordinator）
/// 
/// Phase 5: 持久化阶段（事务内）
///   ├─ 持久化 Plan（PlanRepository）
///   ├─ 持久化 Slice（PlanSliceRepository）
///   └─ 持久化 Task（TaskRepository）
/// 
/// Phase 6: 发布阶段（事务内）
///   ├─ 收集 TaskQueuedEvent
///   └─ 发布到 Outbox（TaskOutboxPublisher）
/// 
/// ```
/// 
/// ## 关键设计
/// 
/// ### 幂等性保证
/// 
/// - 使用 `planKey` 作为业务唯一键（provenanceCode + operationCode + windowHash）
///   - 相同 planKey 的重复请求会：
///       
/// - 如果 Plan 状态为 READY/COMPLETED → 直接返回现有 Plan
///         - 如果 Plan 状态为 FAILED → 重试失败的 Task
/// 
/// ### 事务边界
/// 
/// - `PlanIngestionOrchestrator.ingestPlan()` 是一个完整的事务
///   - 确保 Plan/Slice/Task 持久化和 Outbox 发布的原子性
///   - 如果任何步骤失败，整个事务回滚
/// 
/// ### 切片策略
/// 
/// - **TIME 策略**: 按时间范围切片（如每小时一个 Slice）
///   - **DATE 策略**: 按日期切片（如每天一个 Slice）
///   - **SINGLE 策略**: 不切片（整个 Plan 只有一个 Slice）
/// 
/// ## 使用示例
/// 
/// ### 从定时任务触发
/// 
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class PlanScheduler {
///     private final PlanIngestionUseCase planIngestionUseCase;
/// 
///     @Scheduled(cron = "0 0 * * * ?")  // 每小时执行
///     public void schedulePubMedHarvest() {
///         var command = PlanIngestionCommand.builder()
///             .provenanceCode("pubmed")
///             .operationCode(OperationCode.HARVEST)
///             .triggerType(TriggerType.SCHEDULED)
///             .windowFrom(Instant.now().minus(Duration.ofHours(1)))
///             .windowTo(Instant.now())
///             .sliceStrategyCode("TIME")
///             .build();
/// 
///         var result = planIngestionUseCase.ingestPlan(command);
/// 
///         log.info("Plan ingestion completed: planId={, taskCount={",
///             result.getPlanId(), result.getTaskCount());
/// ```
/// 
/// ### 从 REST API 触发
/// 
/// ```java
/// @RestController
/// @RequestMapping("/api/ingest/plans")
/// @RequiredArgsConstructor
/// public class PlanController {
///     private final PlanIngestionUseCase planIngestionUseCase;
/// 
///     @PostMapping
///     public ResponseEntity<PlanIngestionResult> ingestPlan(
///         @RequestBody @Valid PlanIngestionRequest request
///     ) {
///         var command = PlanIngestionCommand.builder()
///             .provenanceCode(request.getProvenanceCode())
///             .operationCode(request.getOperationCode())
///             .triggerType(TriggerType.MANUAL)
///             .windowFrom(request.getWindowFrom())
///             .windowTo(request.getWindowTo())
///             .sliceStrategyCode(request.getSliceStrategy())
///             .build();
/// 
///         var result = planIngestionUseCase.ingestPlan(command);
/// 
///         return ResponseEntity.ok(result);
/// ```
/// 
/// ## 错误处理
/// 
/// - `PlanValidationException`: 窗口不合法、背压超限、容量不足
///   - `PlanAssemblyException`: 装配失败（如切片规划错误）
///   - `PlanPersistenceException`: 持久化失败（如数据库约束冲突）
///   - `OutboxPublishException`: Outbox 发布失败
/// 
/// @since 0.1.0
/// @author linqibin
/// @see com.patra.ingest.domain.model.aggregate.PlanAggregate Plan 聚合根
/// @see com.patra.ingest.domain.service.PlanDomainService Plan 领域服务
package com.patra.ingest.app.usecase.plan;
