package com.patra.registry.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.model.vo.expr.ExprSnapshot;

import java.time.Instant;

/**
 * Repository port for expression-related domain objects providing read-only access.
 *
 * <p>This port abstracts persistence concerns for expression fields, capabilities,
 * render rules, and API parameter mappings.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExprRepository {

    /**
     * Loads an aggregated expression snapshot for the specified provenance and operation scope.
     *
     * <p>The snapshot includes field definitions, capabilities, render rules, and parameter mappings
     * that are effective at the given instant.
     *
     * @param provenanceCode the provenance code identifying the data source
     * @param operationType the operation type (HARVEST/UPDATE/BACKFILL); nullable for cross-operation queries
     * @param endpointName the endpoint name for scoped configuration; nullable for endpoint-agnostic queries
     * @param at the effective instant for temporal filtering
     * @return aggregated expression snapshot containing all expression-related configuration
     */
    ExprSnapshot loadSnapshot(ProvenanceCode provenanceCode,
                              String operationType,
                              String endpointName,
                              Instant at);
}
