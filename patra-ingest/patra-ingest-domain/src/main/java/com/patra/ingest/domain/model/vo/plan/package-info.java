/// 采集任务调度计划值对象。
///
/// 包含用于采集任务调度计划规范的值对象（注意：批次调度相关模型已迁移至 batch 和 fetch 包）:
///
/// - {@link com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm} - 计划触发器归一化
///   - {@link com.patra.ingest.domain.model.vo.plan.PlannerWindow} - 计划器窗口规范
///   - {@link com.patra.ingest.domain.model.vo.plan.TaskSchedulerContext} - 任务调度器上下文
///   - {@link com.patra.ingest.domain.model.vo.plan.WindowSpec} - 窗口规范(密封接口,包含 5 种策略: TIME,
///       ID_RANGE, CURSOR_LANDMARK, VOLUME_BUDGET, SINGLE)
///
/// @author linqibin
/// @since 0.1.0
package com.patra.ingest.domain.model.vo.plan;
