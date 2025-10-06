package com.patra.registry.adapter.inbound.rest.feign;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.adapter.inbound.rest.feign.converter.ProvenanceApiConverter;
import com.patra.registry.api.rpc.client.ProvenanceClient;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceResp;
import com.patra.registry.app.service.ProvenanceConfigAppService;
import com.patra.registry.domain.exception.provenance.ProvenanceNotFoundException;
import com.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of the provenance internal API (Feign client endpoint).
 *
 * <p>Exposes provenance metadata and configuration retrieval capabilities to other
 * microservices via internal RPC contract, delegating to application service and
 * converting query DTOs to API response DTOs.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ProvenanceClientImpl implements ProvenanceClient {

    private final ProvenanceConfigAppService appService;
    private final ProvenanceApiConverter converter;

    /**
     * Lists all available provenances and converts them to response DTOs.
     *
     * @return the list of provenance response DTOs
     */
    @Override
    public List<ProvenanceResp> listProvenances() {
        log.debug("[REGISTRY][ADAPTER] list provenances");
        return converter.toResp(appService.listProvenances());
    }

    /**
     * Retrieves a single provenance by code.
     *
     * @param code the provenance code to look up
     * @return the provenance response DTO
     * @throws ProvenanceNotFoundException when provenance is absent
     */
    @Override
    public ProvenanceResp getProvenance(ProvenanceCode code) {
        log.debug("[REGISTRY][ADAPTER] get provenance code={}", code);
        Optional<ProvenanceQuery> result = appService.findProvenance(code);
        return result.map(converter::toResp)
                .orElseThrow(() -> new ProvenanceNotFoundException("Provenance not found: code=" + code));
    }

    /**
     * Loads aggregated configuration for a provenance and operation type.
     *
     * @param code the provenance code to load configuration for
     * @param operationType the operation type discriminator; {@code null} means all operations
     * @param at the instant used for temporal slicing; {@code null} defaults to current time
     * @return the provenance configuration response DTO
     * @throws ProvenanceNotFoundException when configuration is absent for the inputs
     */
    @Override
    public ProvenanceConfigResp getConfiguration(ProvenanceCode code,
                                                 String operationType,
                                                 Instant at) {
        log.debug("[REGISTRY][ADAPTER] get provenance config code={} operationType={} at={}", code, operationType, at);
        Optional<ProvenanceConfigQuery> result = appService.loadConfiguration(code, operationType, at);
        return result.map(converter::toResp)
                .orElseThrow(() -> new ProvenanceNotFoundException(
                        "Provenance configuration not found: code=" + code + ", operationType=" + operationType));
    }
}
