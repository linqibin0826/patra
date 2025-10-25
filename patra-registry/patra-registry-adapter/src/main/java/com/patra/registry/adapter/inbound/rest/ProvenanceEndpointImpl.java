package com.patra.registry.adapter.inbound.rest;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.adapter.inbound.rest.converter.ProvenanceApiConverter;
import com.patra.registry.api.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.dto.provenance.ProvenanceResp;
import com.patra.registry.api.endpoint.ProvenanceEndpoint;
import com.patra.registry.app.service.ProvenanceConfigOrchestrator;
import com.patra.registry.domain.exception.provenance.ProvenanceNotFoundException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of the provenance internal API endpoint.
 *
 * <p>Exposes provenance metadata and configuration retrieval capabilities to other microservices
 * via internal RPC contract, delegating to orchestrator and converting query DTOs to API response
 * DTOs.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ProvenanceEndpointImpl implements ProvenanceEndpoint {

  private final ProvenanceConfigOrchestrator orchestrator;
  private final ProvenanceApiConverter converter;

  /**
   * Lists all available provenances and converts them to response DTOs.
   *
   * @return the list of provenance response DTOs
   */
  @Override
  public List<ProvenanceResp> listProvenances() {
    log.debug("Received request to list all provenances");

    List<ProvenanceResp> response = converter.toResp(orchestrator.listProvenances());

    log.debug("Returning {} provenance entries", response.size());

    return response;
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
    log.debug("Received request to get provenance for code [{}]", code.getCode());

    ProvenanceResp response =
        orchestrator
            .findProvenance(code)
            .map(converter::toResp)
            .orElseThrow(
                () ->
                    new ProvenanceNotFoundException(
                        String.format("Provenance not found for code [%s]", code.getCode())));

    log.debug(
        "Successfully retrieved provenance [{}] with name [{}]", code.getCode(), response.name());

    return response;
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
  public ProvenanceConfigResp getConfiguration(
      ProvenanceCode code, String operationType, Instant at) {
    log.debug(
        "Received provenance configuration request for code [{}], operationType [{}], at [{}]",
        code.getCode(),
        operationType,
        at);

    ProvenanceConfigResp response =
        orchestrator
            .loadConfiguration(code, operationType, at)
            .map(converter::toResp)
            .orElseThrow(
                () ->
                    new ProvenanceNotFoundException(
                        String.format(
                            "Provenance configuration not found for code [%s] and operationType [%s]",
                            code.getCode(), operationType)));

    log.debug("Successfully loaded configuration for provenance [{}]", code.getCode());

    return response;
  }
}
