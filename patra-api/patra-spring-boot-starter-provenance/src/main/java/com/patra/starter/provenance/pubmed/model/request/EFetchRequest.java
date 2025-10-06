package com.patra.starter.provenance.pubmed.model.request;

import com.patra.starter.provenance.common.gateway.ApiRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PubMed efetch API request parameters.
 * Based on <a href="https://www.ncbi.nlm.nih.gov/books/NBK25499/#chapter4.EFetch">PubMed E-utilities API documentation</a>
 *
 * Format support:
 * - rettype=abstract/medline/full: XML only, requires XmlToJsonConverter
 * - rettype=uilist: JSON supported, can use directly
 *
 * Recommended strategy:
 * - Get article details → use XML format (retmode=xml, rettype=abstract)
 * - Get ID list → use JSON format (retmode=json, rettype=uilist)
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
     * Default constructor: use XML format to get abstract
     */
    public EFetchRequest(String db, String id) {
        this(db, id, "xml", "abstract", null, null, null, null, null, null, null);
    }

    /**
     * JSON format constructor: get ID list
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
     * Check if this request requires XML to JSON conversion
     *
     * @return true if XML conversion is needed
     */
    public boolean requiresXmlConversion() {
        return "xml".equals(retmode) && !"uilist".equals(rettype);
    }
}
