package com.patra.starter.provenance.pubmed.model.response;

import java.util.List;

/**
 * Medline citation object
 *
 * @author linqibin
 * @since 0.1.0
 */
public record MedlineCitation(
    String pmid,
    String dateCreated,
    String dateCompleted,
    String dateRevised,
    Article article,
    MedlineJournalInfo medlineJournalInfo,
    List<String> meshHeadingList
) {
}
