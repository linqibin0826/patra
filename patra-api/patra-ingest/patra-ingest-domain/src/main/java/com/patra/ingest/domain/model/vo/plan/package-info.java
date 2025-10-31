/**
 * Plan and scheduling value objects.
 *
 * <p>Contains value objects for ingestion plan specification and scheduling:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.model.vo.plan.PlanMetadata} - Plan metadata
 *   <li>{@link com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm} - Plan trigger normalization
 *   <li>{@link com.patra.ingest.domain.model.vo.plan.PlannerWindow} - Planner window specification
 *   <li>{@link com.patra.ingest.domain.model.vo.plan.TaskSchedulerContext} - Task scheduler context
 *   <li>{@link com.patra.ingest.domain.model.vo.plan.WindowSpec} - Window specification (sealed
 *       interface with 5 strategies: TIME, ID_RANGE, CURSOR_LANDMARK, VOLUME_BUDGET, SINGLE)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.domain.model.vo.plan;
