package com.patra.starter.provenance.epmc;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.epmc.model.request.SearchRequest;
import com.patra.starter.provenance.epmc.model.response.SearchResponse;

/**
 * EPMC client interface.
 * Provides methods to call EPMC API.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface EPMCClient {

    /**
     * Call the Europe PMC search API for literature discovery.
     *
     * @param request search request parameters
     * @return search response
     * @throws ProvenanceClientException if the gateway reports an error or parsing fails
     */
    SearchResponse search(SearchRequest request);

    /**
     * Call the Europe PMC search API using caller-supplied configuration overrides.
     *
     * @param request search request parameters
     * @param config config override (optional)
     * @return search response
     * @throws ProvenanceClientException if the gateway reports an error or parsing fails
     */
    SearchResponse search(SearchRequest request, ProvenanceConfig config);
}

