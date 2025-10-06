package com.patra.starter.provenance.pubmed.model.response;

import java.util.List;

/**
 * PubMed esearch API response
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ESearchResponse(
    Integer count,              // Total result count
    Integer retmax,             // Return count
    Integer retstart,           // Start position
    List<String> idList,        // Article ID list
    String webenv,              // Web environment string
    String queryKey,            // Query key
    List<String> translationSet,// Translation set
    String queryTranslation,    // Query translation
    List<String> errorList      // Error list
) {
}
