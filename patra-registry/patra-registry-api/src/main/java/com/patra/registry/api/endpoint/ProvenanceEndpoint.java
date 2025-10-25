package com.patra.registry.api.endpoint;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.api.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.dto.provenance.ProvenanceResp;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Internal API contract for provenance metadata and configuration queries.
 *
 * <p>Exposes endpoints for querying provenance metadata and effective configuration to internal
 * microservices via Feign client integration.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ProvenanceEndpoint {

  String BASE_PATH = "/_internal/provenances";

  /**
   * Lists all available provenances.
   *
   * @return list of provenance metadata
   */
  @GetMapping(BASE_PATH)
  List<ProvenanceResp> listProvenances();

  /**
   * Retrieves a single provenance by its code.
   *
   * @param code provenance code to look up
   * @return provenance metadata
   */
  @GetMapping(BASE_PATH + "/{code}")
  ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);

  /**
   * Loads the aggregated configuration for a provenance.
   *
   * <p>Retrieves the effective configuration by resolving temporal slices and assembling all
   * dimension configs into a unified view.
   *
   * @param code provenance code to load configuration for
   * @param operationType operation type discriminator (e.g., HARVEST/UPDATE); {@code null} means
   *     ALL
   * @param at instant to query effective configs; {@code null} defaults to current time
   * @return aggregated provenance configuration
   */
  @GetMapping(BASE_PATH + "/{code}/config")
  ProvenanceConfigResp getConfiguration(
      @PathVariable("code") ProvenanceCode code,
      @RequestParam(value = "operationType", required = false) String operationType,
      @RequestParam(value = "at", required = false) Instant at);
}
