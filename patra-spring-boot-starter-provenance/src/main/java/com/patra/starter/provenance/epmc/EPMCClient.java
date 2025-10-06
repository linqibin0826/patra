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
     * Call EPMC search API (search for literature)
     *
     * @param request search request parameters
     * @return search response
     * @throws ProvenanceClientException if call fails
     */
    SearchResponse search(SearchRequest request);

    /**
     * Call EPMC search API with config override
     *
     * @param request search request parameters
     * @param config  config override (optional)
     * @return search response
     * @throws ProvenanceClientException if call fails
     */
    SearchResponse search(SearchRequest request, ProvenanceConfig config);
}
