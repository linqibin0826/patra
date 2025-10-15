package com.patra.ingest.domain.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;

/**
 * Domain port for PubMed search metadata.
 *
 * <p>Responsibility: provide a lightweight count estimation for a compiled PubMed search term and
 * parameters, without fetching the full dataset. Implementations must avoid performing any business
 * logic and simply delegate to the upstream client/gateway.
 */
public interface PubmedSearchPort {

  /**
   * Estimate total result count for the given PubMed query and parameters.
   *
   * @param query compiled Boolean query string
   * @param params compiled parameters JSON (e.g., datetype/mindate/maxdate/reldate/sort)
   * @param provenanceConfigSnapshot configuration snapshot for the current execution
   * @return total number of matching records (0 when none or unknown)
   */
  int estimateCount(
      String query, JsonNode params, ProvenanceConfigSnapshot provenanceConfigSnapshot);
}
