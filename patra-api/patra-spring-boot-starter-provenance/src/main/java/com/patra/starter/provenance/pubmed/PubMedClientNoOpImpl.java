package com.patra.starter.provenance.pubmed;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import lombok.extern.slf4j.Slf4j;


/**
 * No-op implementation of PubMedClient when EgressGatewayClient is not available.
 * Returns empty responses to prevent service failure.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class PubMedClientNoOpImpl implements PubMedClient {

    /** {@inheritDoc} */
    @Override
    public ESearchResponse esearch(ESearchRequest request) {
        return esearch(request, null);
    }

    /** {@inheritDoc} */
    @Override
    public ESearchResponse esearch(ESearchRequest request, ProvenanceConfig config) {
        log.warn("[PROVENANCE][CORE] EgressGatewayClient not available, returning empty esearch response");
        return ESearchResponse.empty();
    }

    /** {@inheritDoc} */
    @Override
    public EFetchResponse efetch(EFetchRequest request) {
        return efetch(request, null);
    }

    /** {@inheritDoc} */
    @Override
    public EFetchResponse efetch(EFetchRequest request, ProvenanceConfig config) {
        log.warn("[PROVENANCE][CORE] EgressGatewayClient not available, returning empty efetch response");
        return EFetchResponse.empty();
    }
}
