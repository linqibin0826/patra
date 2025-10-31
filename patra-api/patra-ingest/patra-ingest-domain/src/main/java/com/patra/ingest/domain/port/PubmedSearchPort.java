package com.patra.ingest.domain.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanMetadata;

/**
 * Domain port for PubMed search metadata.
 *
 * <p>Responsibility: provide planning metadata for a compiled PubMed search term, including total
 * result count and cache handles that can be reused during execution. Implementations must avoid
 * performing any business logic and simply delegate to the upstream client/gateway.
 */
public interface PubmedSearchPort {

  /**
   * Prepare metadata required by the planning stage for the given PubMed query and parameters.
   *
   * @param query compiled Boolean query string
   * @param params compiled parameters JSON (e.g., datetype/mindate/maxdate/reldate/sort)
   * @param provenanceConfigSnapshot configuration snapshot for the current execution
   * @return planning metadata including result count and optional WebEnv cache handles
   */
  PlanMetadata preparePlanMetadata(
      String query, JsonNode params, ProvenanceConfigSnapshot provenanceConfigSnapshot);
}
