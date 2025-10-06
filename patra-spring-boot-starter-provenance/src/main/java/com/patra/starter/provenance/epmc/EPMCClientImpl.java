package com.patra.starter.provenance.epmc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.egress.api.client.EgressGatewayClient;
import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.egress.api.dto.ExternalCallResponseDTO;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.gateway.GatewayRequestBuilder;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.epmc.model.request.SearchRequest;
import com.patra.starter.provenance.epmc.model.response.SearchResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * EPMC client implementation.
 * Calls EPMC API through egress gateway.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class EPMCClientImpl implements EPMCClient {

    private final EgressGatewayClient gatewayClient;
    private final GatewayRequestBuilder requestBuilder;
    private final DefaultConfigProvider configProvider;
    private final ProvenanceMetrics metrics;  // May be null
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public EPMCClientImpl(
        EgressGatewayClient gatewayClient,
        GatewayRequestBuilder requestBuilder,
        DefaultConfigProvider configProvider,
        ProvenanceMetrics metrics  // @Autowired(required = false)
    ) {
        this.gatewayClient = gatewayClient;
        this.requestBuilder = requestBuilder;
        this.configProvider = configProvider;
        this.metrics = metrics;
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        return search(request, null);
    }

    @Override
    public SearchResponse search(SearchRequest request, ProvenanceConfig config) {
        // Use metrics if available, otherwise execute directly
        if (metrics != null) {
            return metrics.recordApiCall("EPMC", "search", () -> executeSearch(request, config));
        } else {
            return executeSearch(request, config);
        }
    }

    private SearchResponse executeSearch(SearchRequest request, ProvenanceConfig config) {
        // 1. Load config
        ProvenanceConfig finalConfig = config != null ? config : configProvider.getEPMCDefaultConfig();

        // 2. Build gateway request
        ExternalCallRequestDTO gatewayRequest = requestBuilder.build(
            finalConfig.baseUrl(),
            "/search",
            request,
            finalConfig
        );

        // 3. Call gateway
        ExternalCallResponseDTO response = gatewayClient.call(gatewayRequest);

        // 4. Parse response (EPMC uses JSON natively, no XML conversion needed)
        try {
            return jsonMapper.readValue(response.envelope().body(), SearchResponse.class);
        } catch (Exception e) {
            log.error("[PROVENANCE][CORE] Failed to parse EPMC search response", e);
            throw new ProvenanceClientException("EPMC", "search", "Failed to parse JSON response", e);
        }
    }
}
