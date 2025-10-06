package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

/**
 * Journal metadata derived from PubMed citation.
 */
public final class Journal {

    private final String issn;
    private final String issnType;
    private final String title;
    private final String isoAbbreviation;

    private Journal(String issn, String issnType, String title, String isoAbbreviation) {
        this.issn = issn;
        this.issnType = issnType;
        this.title = title;
        this.isoAbbreviation = isoAbbreviation;
    }

    public static Journal from(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new Journal(null, null, null, null);
        }
        JsonNode issnNode = node.path("ISSN");
        String issn = JsonHelpers.textValue(issnNode);
        String issnType = JsonHelpers.textValue(issnNode.path("@IssnType"));
        String title = JsonHelpers.textValue(node.path("Title"));
        String isoAbbreviation = JsonHelpers.textValue(node.path("ISOAbbreviation"));
        return new Journal(issn, issnType, title, isoAbbreviation);
    }

    public String issn() {
        return issn;
    }

    public String issnType() {
        return issnType;
    }

    public String title() {
        return title;
    }

    public String isoAbbreviation() {
        return isoAbbreviation;
    }
}
