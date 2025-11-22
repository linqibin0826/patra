/// Plan 摄入数据传输对象包。
/// 
/// 本包定义 Plan 摄入用例的输出结果和中间传输对象。
/// 
/// ## 职责
/// 
/// - 封装 Plan 摄入用例的输出结果
///   - 传递装配过程中的中间数据
///   - 隔离领域模型和外部表示
/// 
/// ## 核心组件
/// 
/// - `PlanIngestionResult` - Plan 摄入结果
///       
/// - `planId`: 生成的 Plan ID
///         - `taskCount`: 生成的 Task 数量
///         - `sliceCount`: 生成的 Slice 数量
///         - `status`: 摄入状态（SUCCESS、IDEMPOTENT、FAILED）
/// 
///   - `PlanAssemblyResult` - Plan 装配结果（内部使用）
///       
/// - `plan`: 装配的 Plan 聚合根
///         - `slices`: 装配的 Slice 列表
///         - `tasks`: 装配的 Task 列表
/// 
/// ## 使用示例
/// 
/// ```java
/// public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
///     // 装配 Plan
///     var assembly = planAssembler.assemble(request);
/// 
///     // 持久化
///     var persistedPlan = persistenceCoordinator.persistPlan(assembly);
/// 
///     // 返回结果
///     return PlanIngestionResult.success(
///         persistedPlan.getId(),
///         assembly.getTaskCount()
///     );
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.plan.dto;
