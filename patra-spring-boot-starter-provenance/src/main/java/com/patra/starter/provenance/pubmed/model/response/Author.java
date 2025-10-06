package com.patra.starter.provenance.pubmed.model.response;

/**
 * Author object
 *
 * @author linqibin
 * @since 0.1.0
 */
public record Author(
    String lastName,
    String foreName,
    String initials,
    String affiliation
) {
}
