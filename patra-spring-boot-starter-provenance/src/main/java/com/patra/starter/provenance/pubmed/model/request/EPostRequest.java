package com.patra.starter.provenance.pubmed.model.request;

import com.patra.starter.provenance.common.gateway.ApiRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PubMed EPost API request parameters for uploading ID lists to the History Server.
 *
 * <p>EPost is used to upload a list of UIDs to the Entrez History server, which returns a WebEnv
 * and query_key that can be used in subsequent EFetch or other E-utility calls. This is the NCBI
 * recommended approach for handling large ID lists (>200 UIDs).
 *
 * <p>Field descriptions:
 *
 * @param db database identifier (always "pubmed" for this starter)
 * @param id comma-separated list of PubMed identifiers (supports large batches)
 * @param apiKey API key granting elevated rate limits (optional)
 * @param tool client identifier registered with NCBI (optional)
 * @param email contact email for NCBI notifications (optional)
 *     <p><b>NCBI Best Practice:</b> Use EPost when fetching more than 200 records to avoid URL
 *     length limitations. The returned WebEnv/query_key can be reused in multiple EFetch calls.
 *     <p>Reference: <a href="https://www.ncbi.nlm.nih.gov/books/NBK25499/">E-utilities In-Depth</a>
 * @author linqibin
 * @since 0.1.0
 */
public record EPostRequest(
    // Required parameters
    String db, // Database name (e.g., "pubmed")
    String id, // Comma-separated PMID list (no hard limit, but recommended < 10000)

    // Optional parameters - Authentication and identification
    String apiKey, // API Key (increase rate limit: 3 req/sec → 10 req/sec)
    String tool, // Tool name (identify application, e.g., "papertrace")
    String email // Contact email (NCBI can contact developer)
    ) implements ApiRequest {

  /**
   * Create a minimal EPost request with only database and ID list.
   *
   * @param db database identifier, typically "pubmed"
   * @param id comma-separated list of PubMed identifiers
   */
  public EPostRequest(String db, String id) {
    this(db, id, null, null, null);
  }

  // Compact constructor: validate required parameters
  public EPostRequest {
    if (db == null || db.isBlank()) {
      throw new IllegalArgumentException("db cannot be null or blank");
    }
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id cannot be null or blank");
    }
  }

  /**
   * Compose the outbound query parameter map understood by the EPost endpoint.
   *
   * @return parameter map ready for gateway submission
   */
  @Override
  public Map<String, String> toQueryParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("db", db);
    params.put("id", id);

    // Authentication and identification
    if (apiKey != null) params.put("api_key", apiKey);
    if (tool != null) params.put("tool", tool);
    if (email != null) params.put("email", email);

    return params;
  }

  /**
   * Get the count of identifiers in the ID list (for logging/debugging).
   *
   * @return number of comma-separated identifiers
   */
  public int getIdCount() {
    if (id == null || id.isBlank()) {
      return 0;
    }
    return id.split(",").length;
  }
}
