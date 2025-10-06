package com.patra.starter.provenance.pubmed.model.response;

/**
 * Journal object
 *
 * @author linqibin
 * @since 0.1.0
 */
public record Journal(
    String issn,
    String volume,
    String issue,
    String pubDate,
    String title,
    String isoAbbreviation
) {
}
