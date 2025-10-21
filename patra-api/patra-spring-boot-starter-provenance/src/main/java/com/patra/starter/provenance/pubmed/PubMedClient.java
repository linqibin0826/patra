package com.patra.starter.provenance.pubmed;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import com.patra.starter.provenance.pubmed.model.request.EPostRequest;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import com.patra.starter.provenance.pubmed.model.response.EPostResponse;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;

/**
 * PubMed client interface. Provides methods to call PubMed E-utilities API.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PubMedClient {

  /**
   * Call the PubMed ESearch API to retrieve article identifiers. Uses JSON format by default.
   *
   * @param request esearch request parameters
   * @return esearch response
   * @throws ProvenanceClientException if the gateway reports an error or parsing fails
   */
  ESearchResponse esearch(ESearchRequest request);

  /**
   * Call the PubMed ESearch API with caller-supplied configuration overrides.
   *
   * @param request esearch request parameters
   * @param config config override (optional)
   * @return esearch response
   * @throws ProvenanceClientException if the gateway reports an error or parsing fails
   */
  ESearchResponse esearch(ESearchRequest request, ProvenanceConfig config);

  /**
   * Call the PubMed EFetch API to retrieve article details by identifier. Uses XML format by
   * default for detailed article data.
   *
   * @param request efetch request parameters
   * @return efetch response
   * @throws ProvenanceClientException if the gateway reports an error or parsing fails
   */
  EFetchResponse efetch(EFetchRequest request);

  /**
   * Call the PubMed EFetch API with caller-supplied configuration overrides.
   *
   * @param request efetch request parameters
   * @param config config override (optional)
   * @return efetch response
   * @throws ProvenanceClientException if the gateway reports an error or parsing fails
   */
  EFetchResponse efetch(EFetchRequest request, ProvenanceConfig config);

  /**
   * Call the PubMed EPost API to upload ID list to History Server.
   *
   * <p>EPost is the recommended approach for handling large ID lists (>200 UIDs). It uploads the
   * UIDs to NCBI's History Server and returns a WebEnv token and query_key that can be used in
   * subsequent EFetch or other E-utility calls.
   *
   * <p><b>NCBI Best Practice:</b> Use EPost when you need to fetch more than 200 records to avoid
   * URL length limitations.
   *
   * @param request epost request with ID list
   * @return epost response containing WebEnv and QueryKey
   * @throws ProvenanceClientException if the gateway reports an error or parsing fails
   */
  EPostResponse epost(EPostRequest request);

  /**
   * Call the PubMed EPost API with caller-supplied configuration overrides.
   *
   * @param request epost request with ID list
   * @param config config override (optional)
   * @return epost response containing WebEnv and QueryKey
   * @throws ProvenanceClientException if the gateway reports an error or parsing fails
   */
  EPostResponse epost(EPostRequest request, ProvenanceConfig config);
}
