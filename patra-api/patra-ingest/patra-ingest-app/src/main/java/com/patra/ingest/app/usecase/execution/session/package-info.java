/**
 * 执行会话管理包。
 *
 * <p>本包提供任务执行会话的创建、管理和上下文加载功能。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>加载执行上下文（编译表达式、加载配置快照）
 *   <li>创建执行会话（封装上下文和租约信息）
 *   <li>管理会话生命周期
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code ExecutionContextLoader} - 执行上下文加载器接口
 *   <li>{@code ExecutionContextLoaderImpl} - 执行上下文加载器实现
 *   <li>{@code ExecutionSessionManager} - 执行会话管理器接口
 *   <li>{@code ExecutionSessionManagerImpl} - 执行会话管理器实现
 *   <li>{@code ExecutionSession} - 执行会话（包含上下文、租约、心跳信息）
 * </ul>
 *
 * <h2>执行上下文（ExecutionContext）</h2>
 * <ul>
 *   <li>{@code taskId}: 任务 ID
 *   <li>{@code runId}: 执行批次 ID（同一任务可能多次执行）
 *   <li>{@code provenanceCode}: 数据源代码
 *   <li>{@code operationCode}: 操作代码
 *   <li>{@code configSnapshot}: 配置快照（JSON）
 *   <li>{@code exprHash}: 表达式哈希
 *   <li>{@code compiledQuery}: 编译后的查询（如 "entrez_date:[2025-01-01 TO 2025-01-02]"）
 *   <li>{@code compiledParams}: 编译后的参数（如 retmax=10000）
 *   <li>{@code normalizedExpression}: 规范化表达式（用于日志）
 *   <li>{@code windowSpec}: 窗口规格
 * </ul>
 *
 * <h2>表达式编译</h2>
 * <p>执行上下文加载器的核心职责是将 Plan 中保存的原始表达式编译为可执行的查询和参数。
 *
 * <pre>
 * 输入：Plan 保存的原始表达式（ExprProto JSON）
 * {
 *   "query": "entrez_date:[${from} TO ${to}]",
 *   "params": {
 *     "from": "${window.from}",
 *     "to": "${window.to}",
 *     "retmax": "10000"
 *   }
 * }
 *
 * 处理：ExpressionCompilerPort.compile()
 *   └─ 替换变量：${window.from} → 2025-01-01
 *   └─ 替换变量：${window.to} → 2025-01-02
 *
 * 输出：编译后的查询和参数
 * {
 *   "compiledQuery": "entrez_date:[2025-01-01 TO 2025-01-02]",
 *   "compiledParams": {
 *     "from": "2025-01-01",
 *     "to": "2025-01-02",
 *     "retmax": "10000"
 *   }
 * }
 * </pre>
 *
 * <h2>使用示例</h2>
 * <h3>加载执行上下文</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class ExecutionContextLoaderImpl implements ExecutionContextLoader {
 *     private final TaskRepository taskRepository;
 *     private final PlanSliceRepository sliceRepository;
 *     private final PlanRepository planRepository;
 *     private final ExpressionCompilerPort expressionCompiler;
 *
 *     @Override
 *     public ExecutionContext loadContext(Long taskId, Long runId) {
 *         // 1. 加载快照（Task → Slice → Plan）
 *         var task = taskRepository.findById(taskId).orElseThrow();
 *         var slice = sliceRepository.findById(task.getSliceId()).orElseThrow();
 *         var plan = planRepository.findById(task.getPlanId()).orElseThrow();
 *
 *         // 2. 编译表达式
 *         var compilationRequest = new ExprCompilationRequest(
 *             plan.getExprProtoSnapshotJson(),
 *             plan.getProvenanceConfigSnapshotJson()
 *         );
 *         var compilationResult = expressionCompiler.compile(compilationRequest);
 *
 *         // 3. 构建上下文
 *         return ExecutionContext.builder()
 *             .taskId(taskId)
 *             .runId(runId)
 *             .provenanceCode(task.getProvenanceCode())
 *             .operationCode(task.getOperationCode())
 *             .configSnapshot(plan.getProvenanceConfigSnapshotJson())
 *             .exprHash(plan.getExprHash())
 *             .compiledQuery(compilationResult.query())
 *             .compiledParams(compilationResult.params())
 *             .normalizedExpression(compilationResult.normalizedExpression())
 *             .windowSpec(slice.getWindowSpec())
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * <h3>创建执行会话</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class ExecutionSessionManagerImpl implements ExecutionSessionManager {
 *
 *     @Override
 *     public ExecutionSession createSession(ExecutionContext context, Lease lease) {
 *         return ExecutionSession.builder()
 *             .context(context)
 *             .lease(lease)
 *             .startTime(Instant.now())
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.execution.session;
