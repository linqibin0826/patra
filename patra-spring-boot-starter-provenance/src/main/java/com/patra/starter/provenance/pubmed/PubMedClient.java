package com.patra.starter.provenance.pubmed;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;

/**
 * PubMed client interface.
 * Provides methods to call PubMed E-utilities API.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PubMedClient {

    /**
     * Call PubMed esearch API (search for articles, returns ID list).
     * Uses JSON format by default.
     *
     * @param request esearch request parameters
     * @return esearch response
     * @throws ProvenanceClientException if call fails
     */
    ESearchResponse esearch(ESearchRequest request);

    /**
     * Call PubMed esearch API with config override.
     *
     * @param request esearch request parameters
     * @param config  config override (optional)
     * @return esearch response
     * @throws ProvenanceClientException if call fails
     */
    ESearchResponse esearch(ESearchRequest request, ProvenanceConfig config);

    /**
     * Call PubMed efetch API (fetch article details by ID).
     * Uses XML format by default for detailed article data.
     *
     * @param request efetch request parameters
     * @return efetch response
     * @throws ProvenanceClientException if call fails
     */
    EFetchResponse efetch(EFetchRequest request);

    /**
     * Call PubMed efetch API with config override.
     *
     * @param request efetch request parameters
     * @param config  config override (optional)
     * @return efetch response
     * @throws ProvenanceClientException if call fails
     */
    EFetchResponse efetch(EFetchRequest request, ProvenanceConfig config);
}
