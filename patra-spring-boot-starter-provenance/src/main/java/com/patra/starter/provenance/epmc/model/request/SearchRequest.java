package com.patra.starter.provenance.epmc.model.request;

import com.patra.starter.provenance.common.gateway.ApiRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EPMC search API request parameters.
 * Based on <a href="https://europepmc.org/RestfulWebService">EPMC API documentation</a>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record SearchRequest(
    // Required parameter
    String query,           // Search query

    // Optional parameters
    String format,          // Return format (json/xml, default json)
    Integer pageSize,       // Page size (default 25)
    String cursorMark,      // Cursor mark (for pagination)
    String sort,            // Sort method (RELEVANCE/DATE/...)
    String resultType,      // Result type (core/lite/idlist)
    String synonym,         // Use synonym (true/false)
    String fromSearchPost,  // From search history (true/false)
    String searchType       // Search type (SIMPLE/ADVANCED)
) implements ApiRequest {

    /**
     * Default constructor: use JSON format
     */
    public SearchRequest(String query) {
        this(query, "json", null, null, null, null, null, null, null);
    }

    // Compact constructor: validate required parameters
    public SearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query cannot be null or blank");
        }
        // Default to JSON format
        if (format == null || format.isBlank()) {
            format = "json";
        }
    }

    @Override
    public Map<String, String> toQueryParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", query);

        // Optional parameters
        params.put("format", format != null ? format : "json");
        if (pageSize != null) params.put("pageSize", pageSize.toString());
        if (cursorMark != null) params.put("cursorMark", cursorMark);
        if (sort != null) params.put("sort", sort);
        if (resultType != null) params.put("resultType", resultType);
        if (synonym != null) params.put("synonym", synonym);
        if (fromSearchPost != null) params.put("fromSearchPost", fromSearchPost);
        if (searchType != null) params.put("searchType", searchType);

        return params;
    }
}
