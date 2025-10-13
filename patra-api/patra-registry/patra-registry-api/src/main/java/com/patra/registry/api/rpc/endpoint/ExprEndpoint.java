package com.patra.registry.api.rpc.endpoint;

import com.patra.registry.api.rpc.dto.expr.ExprSnapshotResp;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Internal API contract for expression snapshot retrieval.
 *
 * <p>Exposes endpoints for querying expression configuration snapshots to internal microservices
 * via Feign client integration.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExprEndpoint {

  String BASE_PATH = "/_internal/expr";

  /**
   * Loads the aggregated expression snapshot for a provenance.
   *
   * @param provenanceCode the provenance code identifying the source system
   * @param operationType the operation type discriminator; {@code null} means all operations
   * @param endpointName the endpoint name filter; {@code null} means all endpoints
   * @param at the instant used for temporal slicing; {@code null} defaults to current time
   * @return the expression snapshot response DTO
   */
  @GetMapping(BASE_PATH + "/snapshot")
  ExprSnapshotResp getSnapshot(
      @RequestParam("provenanceCode") String provenanceCode,
      @RequestParam(value = "operationType", required = false) String operationType,
      @RequestParam(value = "endpointName", required = false) String endpointName,
      @RequestParam(value = "at", required = false) Instant at);
}
