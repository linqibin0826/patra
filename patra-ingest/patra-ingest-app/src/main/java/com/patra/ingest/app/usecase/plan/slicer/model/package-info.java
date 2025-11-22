/// 切片规划模型包。
///
/// 本包定义切片规划器使用的数据模型。
///
/// ## 职责
///
/// - 定义切片规划的输入上下文（`SlicePlanningContext`）
///   - 定义切片规划的输出结果（`SlicePlan`）
///
/// ## 核心组件
///
/// - `SlicePlanningContext` - 切片规划上下文
///
/// - `plannerWindow`: 规划窗口（待切片的范围）
///         - `provenanceCode`: 数据源代码
///         - `operationCode`: 操作代码
///
///   - `SlicePlan` - 切片计划
///
/// - `window`: 切片窗口
///         - `seq`: 切片序列号
///
/// ## 使用示例
///
/// ```java
/// // 1. 构建上下文
/// var context = SlicePlanningContext.builder()
///     .plannerWindow(new PlannerWindow(from, to))
///     .provenanceCode(ProvenanceCode.PUBMED)
///     .operationCode(OperationCode.HARVEST)
///     .build();
///
/// // 2. 调用规划器
/// var slicePlans = slicePlanner.plan(context);
///
/// // 3. 使用规划结果
/// slicePlans.forEach(slicePlan -> {
///     log.info("Slice {: { ~ {",
///         slicePlan.getSeq(),
///         slicePlan.getWindow().getFrom(),
///         slicePlan.getWindow().getTo()
///     ););
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.plan.slicer.model;
