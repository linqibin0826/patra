/**
 * 计划和调度值对象。
 *
 * <p>包含用于采集计划规范和调度的值对象:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.model.vo.plan.BatchPlan} - 批次计划（领域模型，屏蔽外部数据源实现细节）
 *   <li>{@link com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm} - 计划触发器归一化
 *   <li>{@link com.patra.ingest.domain.model.vo.plan.PlannerWindow} - 计划器窗口规范
 *   <li>{@link com.patra.ingest.domain.model.vo.plan.TaskSchedulerContext} - 任务调度器上下文
 *   <li>{@link com.patra.ingest.domain.model.vo.plan.WindowSpec} - 窗口规范(密封接口,包含 5 种策略: TIME,
 *       ID_RANGE, CURSOR_LANDMARK, VOLUME_BUDGET, SINGLE)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.domain.model.vo.plan;
