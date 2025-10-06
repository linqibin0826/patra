package com.patra.starter.provenance.common.gateway;

import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.egress.api.dto.ResilienceConfigDTO;
import com.patra.starter.provenance.common.config.ProvenanceConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gateway request builder.
 * Builds ExternalCallRequestDTO from API request and config.
 *
 * @author linqibin
 * @since 0.1.0
 */
public class GatewayRequestBuilder {

    /**
     * Build gateway request from API request and config.
     *
     * @param baseUrl  data source base URL
     * @param path     API path
     * @param request  request parameters (must implement ApiRequest interface)
     * @param config   provenance config
     * @return gateway request DTO
     */
    public ExternalCallRequestDTO build(
        String baseUrl,
        String path,
        ApiRequest request,
        ProvenanceConfig config
    ) {
        // 1. Build complete URL (baseUrl + path + query parameters)
        Map<String, String> queryParams = request.toQueryParams();
        String queryString = buildQueryString(queryParams);
        String fullUrl = baseUrl + path + "?" + queryString;

        // 2. Build HTTP Headers (User-Agent, API-Key, etc. from config)
        Map<String, String> headers = new HashMap<>();
        if (config.http() != null && config.http().defaultHeaders() != null) {
            headers.putAll(config.http().defaultHeaders());
        }

        // 3. Build resilience config (convert from ProvenanceConfig)
        ResilienceConfigDTO resilienceConfig = convertToResilienceConfig(config);

        // 4. Return ExternalCallRequestDTO
        return new ExternalCallRequestDTO(fullUrl, "GET", headers, null, resilienceConfig);
    }

    /**
     * Build query string from parameters map
     *
     * @param params query parameters
     * @return encoded query string
     */
    private String buildQueryString(Map<String, String> params) {
        return params.entrySet().stream()
            .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    }

    /**
     * Convert ProvenanceConfig to ResilienceConfigDTO
     *
     * @param config provenance config
     * @return resilience config DTO
     */
    private ResilienceConfigDTO convertToResilienceConfig(ProvenanceConfig config) {
        // Extract timeout in seconds (convert from milliseconds)
        Long timeoutSeconds = null;
        if (config.http() != null && config.http().timeoutTotalMillis() != null) {
            timeoutSeconds = config.http().timeoutTotalMillis() / 1000L;
        }

        // Extract retry config
        Integer maxRetries = null;
        Long retryBackoffSeconds = null;
        if (config.retry() != null) {
            maxRetries = config.retry().maxRetryTimes();
            if (config.retry().initialDelayMillis() != null) {
                retryBackoffSeconds = config.retry().initialDelayMillis() / 1000L;
            }
        }

        // Extract rate limit
        Integer rateLimit = null;
        if (config.rateLimit() != null && config.rateLimit().perCredentialQpsLimit() != null) {
            rateLimit = config.rateLimit().perCredentialQpsLimit();
        }

        // Use ResilienceConfigDTO record constructor
        return new ResilienceConfigDTO(
            timeoutSeconds,
            maxRetries,
            retryBackoffSeconds,
            rateLimit,
            null,  // circuitBreakerThreshold - not configured locally
            null,  // circuitBreakerWindowSeconds - not configured locally
            null   // responseHeaderWhitelist - not configured locally
        );
    }
}
