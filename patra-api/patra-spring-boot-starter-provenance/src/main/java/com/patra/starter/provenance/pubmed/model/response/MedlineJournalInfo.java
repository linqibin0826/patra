package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

/**
 * Additional journal information supplied by Medline.
 */
public final class MedlineJournalInfo {

    private final String medlineTa;
    private final String country;
    private final String nlmUniqueId;
    private final String issnLinking;

    private MedlineJournalInfo(String medlineTa, String country, String nlmUniqueId, String issnLinking) {
        this.medlineTa = medlineTa;
        this.country = country;
        this.nlmUniqueId = nlmUniqueId;
        this.issnLinking = issnLinking;
    }

    public static MedlineJournalInfo from(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new MedlineJournalInfo(null, null, null, null);
        }
        return new MedlineJournalInfo(
            JsonHelpers.textValue(node.path("MedlineTA")),
            JsonHelpers.textValue(node.path("Country")),
            JsonHelpers.textValue(node.path("NlmUniqueID")),
            JsonHelpers.textValue(node.path("ISSNLinking"))
        );
    }

    public String medlineTa() {
        return medlineTa;
    }

    public String country() {
        return country;
    }

    public String nlmUniqueId() {
        return nlmUniqueId;
    }

    public String issnLinking() {
        return issnLinking;
    }
}
