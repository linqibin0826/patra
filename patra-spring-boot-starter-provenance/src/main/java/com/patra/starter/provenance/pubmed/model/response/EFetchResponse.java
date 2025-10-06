package com.patra.starter.provenance.pubmed.model.response;

import java.util.List;

/**
 * PubMed efetch API response
 *
 * @author linqibin
 * @since 0.1.0
 */
public record EFetchResponse(
    List<PubmedArticle> articles  // Article list
) {
}
