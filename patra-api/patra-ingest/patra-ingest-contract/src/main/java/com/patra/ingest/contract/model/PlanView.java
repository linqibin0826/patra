package com.patra.ingest.contract.model;

import java.time.Instant;
import java.util.List;

/**
 * Plan 详情视图（Contract DTO）。
 */
public record PlanView(
        Long id,
        String planKey,
        String provenanceCode,
        String endpointCode,
        String operationCode,
        String statusCode,
        Instant windowFrom,
        Instant windowTo,
        String sliceStrategyCode,
        String sliceParamsJson,
        String exprProtoHash,
        String exprProtoSnapshotJson,
        String configSnapshotJson,
        Instant createdAt,
        Instant updatedAt,
        List<PlanSliceView> slices
) {
    public record PlanSliceView(
            Long id,
            Integer sequence,
            String sliceSignatureHash,
            String sliceSpecJson,
            String exprHash,
            String exprSnapshotJson,
            String statusCode,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
