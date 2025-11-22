/// 任务执行准备阶段包。
/// 
/// 本包实现任务执行的准备阶段，包括幂等性检查、租约获取、上下文加载。
/// 
/// ## 职责
/// 
/// - 检查任务状态（如果已成功完成 → 抛出 `TaskAlreadySucceededException`）
///   - 获取任务租约（防止并发执行）
///   - 加载执行上下文（编译表达式、加载配置快照）
///   - 创建执行会话（`ExecutionSession`）
///   - 启动心跳续约
/// 
/// ## 核心组件
/// 
/// - `PrepareTaskExecutionUseCase` - 准备执行用例接口
///   - `PrepareTaskExecutionUseCaseImpl` - 准备执行用例实现
/// 
/// ## 准备流程
/// 
/// ```
/// 
/// 1. 幂等性检查（IdempotencyChecker）
///    ├─ 查询任务状态
///    └─ 如果状态为 SUCCEEDED → 抛出 TaskAlreadySucceededException
/// 
/// 2. 租约获取（LeaseManagementService）
///    ├─ 尝试获取租约（存储在 Redis/DB）
///    └─ 如果租约已被占用 → 抛出 LeaseAcquisitionFailedException
/// 
/// 3. 加载上下文（ExecutionContextLoader）
///    ├─ 加载 Task/Slice/Plan（3 层快照）
///    ├─ 解析配置快照
///    └─ 调用 ExpressionCompilerPort 编译表达式
/// 
/// 4. 创建会话（ExecutionSessionManager）
///    └─ 构建 ExecutionSession（包含上下文、租约信息）
/// 
/// 5. 启动心跳（HeartbeatRenewalService）
///    └─ 启动后台线程定期续约租约
/// 
/// ```
/// 
/// ## 异常处理
/// 
/// - `TaskAlreadySucceededException`: 任务已完成（幂等跳过，不需要重试）
///   - `LeaseAcquisitionFailedException`: 租约获取失败（并发冲突，稍后重试）
///   - `ExpressionCompilationException`: 表达式编译失败（配置错误，需要人工介入）
/// 
/// ## 使用示例
/// 
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class PrepareTaskExecutionUseCaseImpl implements PrepareTaskExecutionUseCase {
///     private final IdempotencyChecker idempotencyChecker;
///     private final LeaseManagementService leaseService;
///     private final ExecutionContextLoader contextLoader;
///     private final ExecutionSessionManager sessionManager;
///     private final HeartbeatRenewalService heartbeatService;
/// 
///     @Override
///     public ExecutionSession prepare(TaskReadyCommand command) {
///         // 1. 幂等性检查
///         if (idempotencyChecker.isAlreadySucceeded(command.getTaskId())) {
///             throw new TaskAlreadySucceededException(command.getTaskId());
/// 
///         // 2. 获取租约
///         var lease = leaseService.acquireLease(command.getTaskId(), Duration.ofMinutes(5));
///         if (!lease.isAcquired()) {
///             throw new LeaseAcquisitionFailedException(command.getTaskId());
/// 
///         // 3. 加载上下文
///         var context = contextLoader.loadContext(command.getTaskId(), lease.getRunId());
/// 
///         // 4. 创建会话
///         var session = sessionManager.createSession(context, lease);
/// 
///         // 5. 启动心跳
///         heartbeatService.start(session);
/// 
///         return session;
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.execution.prepare;
