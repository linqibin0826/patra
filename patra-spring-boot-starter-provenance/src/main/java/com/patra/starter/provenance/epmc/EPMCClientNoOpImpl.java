package com.patra.starter.provenance.epmc;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.epmc.model.request.SearchRequest;
import com.patra.starter.provenance.epmc.model.response.SearchResponse;
import lombok.extern.slf4j.Slf4j;


/**
 * No-op implementation of EPMCClient when EgressGatewayClient is not available.
 * Returns empty responses to prevent service failure.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class EPMCClientNoOpImpl implements EPMCClient {

    @Override
    public SearchResponse search(SearchRequest request) {
        return search(request, null);
    }

    @Override
    public SearchResponse search(SearchRequest request, ProvenanceConfig config) {
        log.warn("[PROVENANCE][CORE] EgressGatewayClient not available, returning empty search response");
        return SearchResponse.empty();
    }
}
