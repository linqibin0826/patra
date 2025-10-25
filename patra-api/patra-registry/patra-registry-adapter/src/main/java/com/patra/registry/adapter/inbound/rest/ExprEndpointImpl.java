package com.patra.registry.adapter.inbound.rest;

import com.patra.registry.adapter.inbound.rest.converter.ExprApiConverter;
import com.patra.registry.api.dto.expr.ExprSnapshotResp;
import com.patra.registry.api.endpoint.ExprEndpoint;
import com.patra.registry.app.service.ExprQueryAppService;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of the expression internal API endpoint.
 *
 * <p>Exposes expression snapshot retrieval capabilities to other microservices via internal RPC
 * contract, delegating to application service and converting query DTOs to API response DTOs.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExprEndpointImpl implements ExprEndpoint {

  private final ExprQueryAppService exprQueryAppService;
  private final ExprApiConverter converter;

  /**
   * Loads an expression snapshot and converts it to API response DTO.
   *
   * @param provenanceCode the provenance code identifying the source system
   * @param operationType the operation type discriminator; {@code null} means all operations
   * @param endpointName the endpoint name filter; {@code null} means all endpoints
   * @param at the instant used for temporal slicing; {@code null} defaults to current time
   * @return the expression snapshot response DTO exposed by RPC contract
   */
  @Override
  public ExprSnapshotResp getSnapshot(
      String provenanceCode, String operationType, String endpointName, Instant at) {
    log.debug(
        "Retrieving expression snapshot for provenance [{}] with operationType [{}], endpoint [{}] at timestamp [{}]",
        provenanceCode,
        operationType,
        endpointName,
        at);
    ExprSnapshotQuery snapshot =
        exprQueryAppService.loadSnapshot(provenanceCode, operationType, endpointName, at);
    log.debug(
        "Successfully loaded expression snapshot for provenance [{}] with {} fields, {} capabilities, {} render rules, {} API params",
        provenanceCode,
        snapshot.fields().size(),
        snapshot.capabilities().size(),
        snapshot.renderRules().size(),
        snapshot.apiParamMappings().size());
    return converter.toResp(snapshot);
  }
}
