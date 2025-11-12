/**
 * 领域模型包 - 包含聚合根、实体、值对象和枚举
 *
 * <h2>包结构</h2>
 * <ul>
 *   <li>{@link com.patra.ingest.domain.model.aggregate} - 聚合根（Plan、Task、PlanSlice、ScheduleInstance）</li>
 *   <li>{@link com.patra.ingest.domain.model.entity} - 领域实体（OutboxMessage、Cursor、TaskRun）</li>
 *   <li>{@link com.patra.ingest.domain.model.vo} - 值对象（WindowSpec、ExecutionContext、CursorWatermark）</li>
 *   <li>{@link com.patra.ingest.domain.model.enums} - 领域枚举（PlanStatus、TaskStatus、OperationCode）</li>
 *   <li>{@link com.patra.ingest.domain.model.snapshot} - 配置快照（ProvenanceConfigSnapshot）</li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><strong>纯Java</strong>：本包不依赖任何框架（Spring、MyBatis、JPA）</li>
 *   <li><strong>不可变性</strong>：值对象使用Record或@Value保证不可变</li>
 *   <li><strong>领域驱动</strong>：遵循DDD原则，聚合根维护一致性边界</li>
 *   <li><strong>六边形架构</strong>：领域模型位于架构核心，不依赖外层</li>
 * </ul>
 *
 * @since 0.1.0
 */
package com.patra.ingest.domain.model;
