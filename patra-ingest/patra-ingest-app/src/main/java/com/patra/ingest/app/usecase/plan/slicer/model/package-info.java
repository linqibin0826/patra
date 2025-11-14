/**
 * 切片规划模型包。
 *
 * <p>本包定义切片规划器使用的数据模型。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义切片规划的输入上下文（{@code SlicePlanningContext}）
 *   <li>定义切片规划的输出结果（{@code SlicePlan}）
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code SlicePlanningContext} - 切片规划上下文
 *       <ul>
 *         <li>{@code plannerWindow}: 规划窗口（待切片的范围）
 *         <li>{@code provenanceCode}: 数据源代码
 *         <li>{@code operationCode}: 操作代码
 *       </ul>
 *   <li>{@code SlicePlan} - 切片计划
 *       <ul>
 *         <li>{@code window}: 切片窗口
 *         <li>{@code seq}: 切片序列号
 *       </ul>
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 1. 构建上下文
 * var context = SlicePlanningContext.builder()
 *     .plannerWindow(new PlannerWindow(from, to))
 *     .provenanceCode(ProvenanceCode.PUBMED)
 *     .operationCode(OperationCode.HARVEST)
 *     .build();
 *
 * // 2. 调用规划器
 * var slicePlans = slicePlanner.plan(context);
 *
 * // 3. 使用规划结果
 * slicePlans.forEach(slicePlan -> {
 *     log.info("Slice {}: {} ~ {}",
 *         slicePlan.getSeq(),
 *         slicePlan.getWindow().getFrom(),
 *         slicePlan.getWindow().getTo()
 *     );
 * });
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.plan.slicer.model;
