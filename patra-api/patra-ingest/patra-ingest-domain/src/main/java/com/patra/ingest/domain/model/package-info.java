/// 领域模型包 - 包含聚合根、实体、值对象和枚举
/// 
/// ## 包结构
/// 
/// - {@link com.patra.ingest.domain.model.aggregate} - 聚合根（Plan、Task、PlanSlice、ScheduleInstance）
///   - {@link com.patra.ingest.domain.model.entity} - 领域实体（OutboxMessage、Cursor、TaskRun）
///   - {@link com.patra.ingest.domain.model.vo} - 值对象（WindowSpec、ExecutionContext、CursorWatermark）
///   - {@link com.patra.ingest.domain.model.enums} - 领域枚举（PlanStatus、TaskStatus、OperationCode）
///   - {@link com.patra.ingest.domain.model.snapshot} - 配置快照（ProvenanceConfigSnapshot）
/// 
/// ## 设计原则
/// 
/// - **纯Java**：本包不依赖任何框架（Spring、MyBatis、JPA）
///   - **不可变性**：值对象使用Record或@Value保证不可变
///   - **领域驱动**：遵循DDD原则，聚合根维护一致性边界
///   - **六边形架构**：领域模型位于架构核心，不依赖外层
/// 
/// @since 0.1.0
package com.patra.ingest.domain.model;
