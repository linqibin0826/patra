package com.patra.starter.provenance.pubmed.model.request;

import com.patra.starter.provenance.common.gateway.ApiRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PubMed ESearch API request parameters aligned with the official E-utilities documentation.
 *
 * <p>Field descriptions:
 *
 * @param db target database identifier (e.g. "pubmed")
 * @param term Boolean query string sent to the ESearch API (optional when date filters are used)
 * @param retstart zero-based offset for pagination
 * @param retmax maximum records returned per invocation (max 10000)
 * @param retmode response format (json or xml)
 * @param rettype response type (uilist, count, etc.)
 * @param sort ordering strategy applied by PubMed
 * @param datetype publication date field evaluated for filtering
 * @param mindate lower bound date constraint
 * @param maxdate upper bound date constraint
 * @param field field-specific search restriction
 * @param reldate relative date filter expressed in days
 * @param usehistory toggle for PubMed history server usage
 * @param webenv history session WebEnv token
 * @param queryKey numeric key pointing to a stored query
 * @param apiKey API key granting elevated rate limits
 * @param tool client identifier registered with NCBI
 * @param email contact email for NCBI notifications
 *     <p>Prefer the convenience constructor when defaults are acceptable. Advanced callers may
 *     override optional parameters individually.
 * @author linqibin
 * @since 0.1.0
 */
public record ESearchRequest(
    // Required parameters
    String db, // Database name (e.g., "pubmed")

    // Search term (optional when date filters mindate/maxdate/datetype are provided)
    String term,

    // Optional parameters - Basic control
    Integer retstart, // Start position (default 0)
    Integer retmax, // Return count (default 20, max 10000)
    String retmode, // Return mode (json/xml, default json)
    String rettype, // Return type (uilist/count)

    // Optional parameters - Sorting and filtering
    String sort, // Sort method (relevance/pub_date/Author/JournalName)
    String datetype, // Date type (pdat/edat/mdat)
    String mindate, // Minimum date (YYYY/MM/DD or YYYY)
    String maxdate, // Maximum date (YYYY/MM/DD or YYYY)
    String field, // Search field (limit search scope)
    String reldate, // Relative date (days)

    // Optional parameters - History and session
    String usehistory, // Use history (y/n)
    String webenv, // Web environment string
    String queryKey, // Query key

    // Optional parameters - Authentication and identification (IMPORTANT)
    String apiKey, // API Key (increase rate limit: 3 req/sec → 10 req/sec)
    String tool, // Tool name (identify application, e.g., "papertrace")
    String email // Contact email (NCBI can contact developer)
    ) implements ApiRequest {

  /**
   * Create a request that targets the PubMed database using JSON output.
   *
   * @param db database identifier, typically "pubmed"
   * @param term Boolean query string that drives the search
   */
  public ESearchRequest(String db, String term) {
    this(
        db, term, null, null, "json", null, null, null, null, null, null, null, null, null, null,
        null, null, null);
  }

  // Compact constructor: validate required parameters
  public ESearchRequest {
    if (db == null || db.isBlank()) {
      throw new IllegalArgumentException("db cannot be null or blank");
    }
    // Note: term is optional when date filters (mindate/maxdate/datetype) are provided
    // Default to JSON format
    if (retmode == null || retmode.isBlank()) {
      retmode = "json";
    }
  }

  /**
   * Compose the outbound query parameter map understood by the ESearch endpoint.
   *
   * @return parameter map ready for gateway submission
   */
  @Override
  public Map<String, String> toQueryParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("db", db);
    // term is optional - only add when present (date filters can be used instead)
    if (term != null && !term.isBlank()) {
      params.put("term", term);
    }

    // Basic control
    if (retstart != null) params.put("retstart", retstart.toString());
    if (retmax != null) params.put("retmax", retmax.toString());
    params.put("retmode", retmode != null ? retmode : "json");
    if (rettype != null) params.put("rettype", rettype);

    // Sorting and filtering
    if (sort != null) params.put("sort", sort);
    if (datetype != null) params.put("datetype", datetype);
    if (mindate != null) params.put("mindate", mindate);
    if (maxdate != null) params.put("maxdate", maxdate);
    if (field != null) params.put("field", field);
    if (reldate != null) params.put("reldate", reldate);

    // History and session
    if (usehistory != null) params.put("usehistory", usehistory);
    if (webenv != null) params.put("WebEnv", webenv);
    if (queryKey != null) params.put("query_key", queryKey);

    // Authentication and identification
    if (apiKey != null) params.put("api_key", apiKey);
    if (tool != null) params.put("tool", tool);
    if (email != null) params.put("email", email);

    return params;
  }
}
