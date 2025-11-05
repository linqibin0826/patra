/**
 * Plan 摄入数据传输对象包。
 *
 * <p>本包定义 Plan 摄入用例的输出结果和中间传输对象。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>封装 Plan 摄入用例的输出结果
 *   <li>传递装配过程中的中间数据
 *   <li>隔离领域模型和外部表示
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code PlanIngestionResult} - Plan 摄入结果
 *       <ul>
 *         <li>{@code planId}: 生成的 Plan ID
 *         <li>{@code taskCount}: 生成的 Task 数量
 *         <li>{@code sliceCount}: 生成的 Slice 数量
 *         <li>{@code status}: 摄入状态（SUCCESS、IDEMPOTENT、FAILED）
 *       </ul>
 *   <li>{@code PlanAssemblyResult} - Plan 装配结果（内部使用）
 *       <ul>
 *         <li>{@code plan}: 装配的 Plan 聚合根
 *         <li>{@code slices}: 装配的 Slice 列表
 *         <li>{@code tasks}: 装配的 Task 列表
 *       </ul>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
 *     // 装配 Plan
 *     var assembly = planAssembler.assemble(request);
 *
 *     // 持久化
 *     var persistedPlan = persistenceCoordinator.persistPlan(assembly);
 *
 *     // 返回结果
 *     return PlanIngestionResult.success(
 *         persistedPlan.getId(),
 *         assembly.getTaskCount()
 *     );
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.plan.dto;
