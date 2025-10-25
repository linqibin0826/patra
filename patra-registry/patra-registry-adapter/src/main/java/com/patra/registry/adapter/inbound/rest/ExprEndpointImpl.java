package com.patra.registry.adapter.inbound.rest;

import com.patra.registry.adapter.inbound.rest.converter.ExprApiConverter;
import com.patra.registry.api.dto.expr.ExprSnapshotResp;
import com.patra.registry.api.endpoint.ExprEndpoint;
import com.patra.registry.app.service.ExprQueryOrchestrator;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of the expression internal API endpoint.
 *
 * <p>Exposes expression snapshot retrieval capabilities to other microservices via internal RPC
 * contract, delegating to orchestrator and converting query DTOs to API response DTOs.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExprEndpointImpl implements ExprEndpoint {

  private final ExprQueryOrchestrator orchestrator;
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
    ExprSnapshotQuery snapshot =
        orchestrator.loadSnapshot(provenanceCode, operationType, endpointName, at);
    return converter.toResp(snapshot);
  }
}
