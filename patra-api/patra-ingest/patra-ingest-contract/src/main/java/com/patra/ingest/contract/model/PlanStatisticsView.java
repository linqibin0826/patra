package com.patra.ingest.contract.model;

/**
 * Plan 统计视图（Contract DTO）。
 */
public record PlanStatisticsView(
        String provenanceCode,
        String operationCode,
        long totalPlans,
        long successPlans,
        long failedPlans,
        long runningPlans,
        long waitingPlans
) {}
