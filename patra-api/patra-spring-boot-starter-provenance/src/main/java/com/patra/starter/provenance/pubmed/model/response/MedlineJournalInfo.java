package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

/**
 * Additional journal information supplied by Medline.
 *
 * @author linqibin
 * @since 0.1.0
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

    /**
     * Parse the Medline journal info node into a curated representation.
     *
     * @param node Medline journal info node
     * @return structured journal info
     */
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

    /**
     * Get the Medline TA abbreviation.
     *
     * @return Medline TA value or {@code null}
     */
    public String medlineTa() {
        return medlineTa;
    }

    /**
     * Get the country associated with the journal.
     *
     * @return country or {@code null}
     */
    public String country() {
        return country;
    }

    /**
     * Get the NLM unique identifier.
     *
     * @return NLM unique identifier or {@code null}
     */
    public String nlmUniqueId() {
        return nlmUniqueId;
    }

    /**
     * Get the linking ISSN value.
     *
     * @return linking ISSN or {@code null}
     */
    public String issnLinking() {
        return issnLinking;
    }
}
