package com.patra.registry.api.rpc.endpoint;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceResp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;

/**
 * Internal API contract for provenance configuration.
 *
 * <p>Exposes endpoints for querying provenance metadata and effective configuration
 * to internal microservices via Feign client integration.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ProvenanceEndpoint {

    String BASE_PATH = "/_internal/provenances";

    /**
     * Lists all available provenances.
     *
     * @return list of provenance response DTOs
     */
    @GetMapping(BASE_PATH)
    List<ProvenanceResp> listProvenances();

    /**
     * Retrieves a single provenance by its code.
     *
     * @param code the provenance code to look up
     * @return provenance response DTO
     */
    @GetMapping(BASE_PATH + "/{code}")
    ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);

    /**
     * Loads the aggregated configuration for a provenance.
     *
     * <p>Retrieves the effective configuration by resolving temporal slices
     * and assembling all dimension configs into a unified view.</p>
     *
     * @param code the provenance code to load configuration for
     * @param operationType the operation type discriminator (e.g., HARVEST/UPDATE); {@code null} means ALL
     * @param at the instant to query effective configs; {@code null} defaults to current time
     * @return provenance configuration response DTO
     */
    @GetMapping(BASE_PATH + "/{code}/config")
    ProvenanceConfigResp getConfiguration(@PathVariable("code") ProvenanceCode code,
                                          @RequestParam(value = "operationType", required = false) String operationType,
                                          @RequestParam(value = "at", required = false) Instant at);
}
