package com.patra.starter.provenance.pubmed.model.request;

import com.patra.starter.provenance.common.gateway.ApiRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PubMed EFetch API request parameters following the official E-utilities specification.
 *
 * <p>Field descriptions:
 * @param db database identifier (always "pubmed" for this starter)
 * @param id comma separated list of article identifiers
 * @param retmode response format (xml, json or text)
 * @param rettype response type (abstract, medline, full, uilist)
 * @param retstart offset used when paging through history server results
 * @param retmax maximum records returned in a single call
 * @param webenv WebEnv token acquired from a previous ESearch
 * @param queryKey query key associated with the WebEnv session
 * @param apiKey API key that unlocks higher request quotas
 * @param tool tool identifier registered at NCBI
 * @param email maintainer email for operational contact
 *
 * <p>Use the XML defaults for retrieving detailed article structures and
 * {@link #forUiList(String, String)} for lightweight identifier lists.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record EFetchRequest(
    // Required parameters
    String db,              // Database name (e.g., "pubmed")
    String id,              // Article ID list (comma-separated, max 200)

    // Optional parameters - Basic control
    String retmode,         // Return mode (xml/json/text, default xml)
    String rettype,         // Return type (abstract/medline/full/uilist, default abstract)
    Integer retstart,       // Start position (only for history queries)
    Integer retmax,         // Return count (default 20, max 10000)

    // Optional parameters - History and session
    String webenv,          // Web environment string (WebEnv)
    String queryKey,        // Query key

    // Optional parameters - Authentication and identification (IMPORTANT)
    String apiKey,          // API Key (increase rate limit: 3 req/sec → 10 req/sec)
    String tool,            // Tool name (identify application, e.g., "papertrace")
    String email            // Contact email (NCBI can contact developer)
) implements ApiRequest {

    /**
     * Create a request configured for abstract retrieval via XML.
     *
     * @param db database identifier, typically "pubmed"
     * @param id comma separated list of PubMed identifiers (max 200 per call)
     */
    public EFetchRequest(String db, String id) {
        this(db, id, "xml", "abstract", null, null, null, null, null, null, null);
    }

    /**
     * Create a request tuned for retrieving identifier lists in JSON.
     *
     * @param db database identifier, typically "pubmed"
     * @param id comma separated list of PubMed identifiers (max 200 per call)
     * @return request configured to return the uilist JSON payload
     */
    public static EFetchRequest forUiList(String db, String id) {
        return new EFetchRequest(db, id, "json", "uilist", null, null, null, null, null, null, null);
    }

    // Compact constructor: validate required parameters
    public EFetchRequest {
        if (db == null || db.isBlank()) {
            throw new IllegalArgumentException("db cannot be null or blank");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }
        // Default to XML format (since most rettype only support XML)
        if (retmode == null || retmode.isBlank()) {
            retmode = "xml";
        }
        if (rettype == null || rettype.isBlank()) {
            rettype = "abstract";
        }
    }

    /**
     * Compose the outbound query parameter map understood by the EFetch endpoint.
     *
     * @return parameter map ready for gateway submission
     */
    @Override
    public Map<String, String> toQueryParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("db", db);
        params.put("id", id);

        // Basic control
        params.put("retmode", retmode != null ? retmode : "xml");
        params.put("rettype", rettype != null ? rettype : "abstract");
        if (retstart != null) params.put("retstart", retstart.toString());
        if (retmax != null) params.put("retmax", retmax.toString());

        // History and session
        if (webenv != null) params.put("WebEnv", webenv);
        if (queryKey != null) params.put("query_key", queryKey);

        // Authentication and identification
        if (apiKey != null) params.put("api_key", apiKey);
        if (tool != null) params.put("tool", tool);
        if (email != null) params.put("email", email);

        return params;
    }

    /**
     * Determine whether the caller must perform XML to JSON conversion locally.
     *
     * @return {@code true} when XML is required and no JSON shortcut is available
     */
    public boolean requiresXmlConversion() {
        return "xml".equals(retmode) && !"uilist".equals(rettype);
    }
}
