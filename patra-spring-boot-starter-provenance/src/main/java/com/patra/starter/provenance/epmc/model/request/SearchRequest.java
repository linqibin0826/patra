package com.patra.starter.provenance.epmc.model.request;

import com.patra.starter.provenance.common.gateway.ApiRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EPMC search API request parameters.
 */
public record SearchRequest(
    String query,
    String format,
    Integer pageSize,
    String cursorMark,
    String sort,
    String resultType,
    Boolean synonym,
    Boolean fromSearchPost,
    String searchType
) implements ApiRequest {

    public SearchRequest(String query) {
        this(query, "json", null, null, null, null, null, null, null);
    }

    public SearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query cannot be null or blank");
        }
        if (format == null || format.isBlank()) {
            format = "json";
        }
    }

    @Override
    public Map<String, String> toQueryParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", query);
        params.put("format", format);
        if (pageSize != null) {
            params.put("pageSize", pageSize.toString());
        }
        if (cursorMark != null && !cursorMark.isBlank()) {
            params.put("cursorMark", cursorMark);
        }
        if (sort != null && !sort.isBlank()) {
            params.put("sort", sort);
        }
        if (resultType != null && !resultType.isBlank()) {
            params.put("resultType", resultType);
        }
        if (synonym != null) {
            params.put("synonym", synonym.toString());
        }
        if (fromSearchPost != null) {
            params.put("fromSearchPost", fromSearchPost.toString());
        }
        if (searchType != null && !searchType.isBlank()) {
            params.put("searchType", searchType);
        }
        return params;
    }
}
