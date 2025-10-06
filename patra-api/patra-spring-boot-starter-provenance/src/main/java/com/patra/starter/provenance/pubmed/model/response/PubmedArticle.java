package com.patra.starter.provenance.pubmed.model.response;

/**
 * PubMed article object
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PubmedArticle(
    String pmid,                    // PubMed ID
    MedlineCitation medlineCitation,// Medline citation
    PubmedData pubmedData           // PubMed data
) {
}
