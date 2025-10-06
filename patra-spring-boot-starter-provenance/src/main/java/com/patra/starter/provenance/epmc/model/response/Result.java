package com.patra.starter.provenance.epmc.model.response;

import java.util.List;

/**
 * EPMC search result object
 *
 * @author linqibin
 * @since 0.1.0
 */
public record Result(
    String id,              // Article ID
    String source,          // Source (MED/PMC/...)
    String pmid,            // PubMed ID
    String pmcid,           // PMC ID
    String doi,             // DOI
    String title,           // Title
    String authorString,    // Author string
    String journalTitle,    // Journal title
    String pubYear,         // Publication year
    String pubType,         // Publication type
    String abstractText,    // Abstract
    String affiliation,     // Affiliation
    String language,        // Language
    List<Author> authorList,// Author list
    List<String> meshHeadingList,  // MeSH headings
    String fullTextUrlList  // Full text URL list
) {
}
