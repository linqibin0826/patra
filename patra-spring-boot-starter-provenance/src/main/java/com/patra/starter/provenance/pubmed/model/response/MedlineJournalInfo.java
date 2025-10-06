package com.patra.starter.provenance.pubmed.model.response;

/**
 * Medline journal info object
 *
 * @author linqibin
 * @since 0.1.0
 */
public record MedlineJournalInfo(
    String country,
    String medlineTA,
    String nlmUniqueID,
    String issnLinking
) {
}
