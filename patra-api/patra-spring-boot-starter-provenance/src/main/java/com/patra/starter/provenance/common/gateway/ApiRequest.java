package com.patra.starter.provenance.common.gateway;

import java.util.Map;

/**
 * API request parameter interface.
 * All request objects must implement this interface to convert to query parameters.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ApiRequest {

    /**
     * Convert request to query parameters map.
     * Only non-null values will be included.
     *
     * @return query parameters map
     */
    Map<String, String> toQueryParams();
}
