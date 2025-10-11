package com.patra.ingest.domain.model.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;

/**
 * Execution context capturing configuration snapshots and compiled expressions for a task run.
 *
 * @param taskId               task identifier
 * @param runId                run identifier
 * @param provenanceCode       provenance code
 * @param operationCode        operation code
 * @param configSnapshot       configuration snapshot
 * @param exprHash             expression hash
 * @param compiledQuery        compiled query
 * @param compiledParams       compiled query parameters
 * @param normalizedExpression normalized expression string
 * @param windowSpec           window specification
 * @author linqibin
 * @since 0.1.0
 */
public record ExecutionContext(
        Long taskId,
        Long runId,
        String provenanceCode,
        String operationCode,
        ProvenanceConfigSnapshot configSnapshot,
        String exprHash,
        String compiledQuery,
        JsonNode compiledParams,
        String normalizedExpression,
        WindowSpec windowSpec
) {
}
