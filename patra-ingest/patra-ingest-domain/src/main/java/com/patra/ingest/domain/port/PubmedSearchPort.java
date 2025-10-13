package com.patra.ingest.domain.port;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Domain port for PubMed search metadata.
 *
 * <p>Responsibility: provide a lightweight count estimation for a compiled PubMed search term and
 * parameters, without fetching the full dataset. Implementations must avoid performing any business
 * logic and simply delegate to the upstream client/gateway.
 */
public interface PubmedSearchPort {

  /**
   * Estimate total result count for the given PubMed term and parameters.
   *
   * @param term compiled Boolean query string
   * @param params compiled parameters JSON (e.g., datetype/mindate/maxdate/reldate/sort)
   * @return total number of matching records (0 when none or unknown)
   */
  int estimateCount(String term, JsonNode params);
}
