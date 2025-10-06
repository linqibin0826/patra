package com.patra.starter.provenance.pubmed.model.response;

import java.util.List;

/**
 * PubMed data object
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PubmedData(
    List<String> articleIdList,
    String pubStatus,
    String history
) {
}
