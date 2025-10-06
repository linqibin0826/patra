package com.patra.starter.provenance.pubmed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.egress.api.client.EgressGatewayClient;
import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.egress.api.dto.ExternalCallResponseDTO;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.converter.XmlToJsonConverter;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.gateway.GatewayRequestBuilder;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * PubMed client implementation.
 * Calls PubMed E-utilities API through egress gateway.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class PubMedClientImpl implements PubMedClient {

    private final EgressGatewayClient gatewayClient;
    private final GatewayRequestBuilder requestBuilder;
    private final DefaultConfigProvider configProvider;
    private final XmlToJsonConverter xmlConverter;
    private final ProvenanceMetrics metrics;  // May be null
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public PubMedClientImpl(
        EgressGatewayClient gatewayClient,
        GatewayRequestBuilder requestBuilder,
        DefaultConfigProvider configProvider,
        XmlToJsonConverter xmlConverter,
        ProvenanceMetrics metrics  // @Autowired(required = false)
    ) {
        this.gatewayClient = gatewayClient;
        this.requestBuilder = requestBuilder;
        this.configProvider = configProvider;
        this.xmlConverter = xmlConverter;
        this.metrics = metrics;
    }

    @Override
    public ESearchResponse esearch(ESearchRequest request) {
        return esearch(request, null);
    }

    @Override
    public ESearchResponse esearch(ESearchRequest request, ProvenanceConfig config) {
        // Use metrics if available, otherwise execute directly
        if (metrics != null) {
            return metrics.recordApiCall("PUBMED", "esearch", () -> executeESearch(request, config));
        } else {
            return executeESearch(request, config);
        }
    }

    @Override
    public EFetchResponse efetch(EFetchRequest request) {
        return efetch(request, null);
    }

    @Override
    public EFetchResponse efetch(EFetchRequest request, ProvenanceConfig config) {
        // Use metrics if available, otherwise execute directly
        if (metrics != null) {
            return metrics.recordApiCall("PUBMED", "efetch", () -> executeEFetch(request, config));
        } else {
            return executeEFetch(request, config);
        }
    }

    private ESearchResponse executeESearch(ESearchRequest request, ProvenanceConfig config) {
        // 1. Load config
        ProvenanceConfig finalConfig = config != null ? config : configProvider.getPubMedDefaultConfig();

        // 2. Build gateway request
        ExternalCallRequestDTO gatewayRequest = requestBuilder.build(
            finalConfig.baseUrl(),
            "/esearch.fcgi",
            request,
            finalConfig
        );

        // 3. Call gateway
        ExternalCallResponseDTO response = gatewayClient.call(gatewayRequest);

        // 4. Parse response (ESearch uses JSON by default, no XML conversion needed)
        try {
            return jsonMapper.readValue(response.envelope().body(), ESearchResponse.class);
        } catch (Exception e) {
            log.error("[PROVENANCE][CORE] Failed to parse ESearch response", e);
            throw new ProvenanceClientException("PUBMED", "esearch", "Failed to parse JSON response", e);
        }
    }

    private EFetchResponse executeEFetch(EFetchRequest request, ProvenanceConfig config) {
        // 1. Load config
        ProvenanceConfig finalConfig = config != null ? config : configProvider.getPubMedDefaultConfig();

        // 2. Build gateway request
        ExternalCallRequestDTO gatewayRequest = requestBuilder.build(
            finalConfig.baseUrl(),
            "/efetch.fcgi",
            request,
            finalConfig
        );

        // 3. Call gateway
        ExternalCallResponseDTO response = gatewayClient.call(gatewayRequest);

        // 4. Parse response (use XML converter only when necessary)
        try {
            if (request.requiresXmlConversion()) {
                log.debug("[PROVENANCE][CORE] Using XML to JSON conversion for efetch");
                return xmlConverter.convert(response.envelope().body(), EFetchResponse.class);
            } else {
                log.debug("[PROVENANCE][CORE] Using direct JSON parsing for efetch");
                return jsonMapper.readValue(response.envelope().body(), EFetchResponse.class);
            }
        } catch (Exception e) {
            log.error("[PROVENANCE][CORE] Failed to parse EFetch response", e);
            throw new ProvenanceClientException("PUBMED", "efetch", "Failed to parse response", e);
        }
    }
}
