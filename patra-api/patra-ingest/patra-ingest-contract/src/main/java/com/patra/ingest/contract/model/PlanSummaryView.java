package com.patra.ingest.contract.model;

import java.time.Instant;

/**
 * Plan 摘要视图（Contract DTO）。
 */
public record PlanSummaryView(
        Long id,
        String planKey,
        String provenanceCode,
        String operationCode,
        String endpointCode,
        String statusCode,
        Instant windowFrom,
        Instant windowTo,
        Integer sliceCount,
        Integer taskCount,
        Instant createdAt
) {}
