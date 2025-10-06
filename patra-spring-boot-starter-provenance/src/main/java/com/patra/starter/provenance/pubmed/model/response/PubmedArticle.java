package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

import java.util.Objects;

/**
 * Simplified view over a PubMed article with access to the raw citation nodes.
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class PubmedArticle {

    private final String pmid;
    private final Article article;
    private final MedlineJournalInfo journalInfo;
    private final PubmedData pubmedData;
    private final JsonNode rawCitation;
    private final JsonNode rawPubmedData;

    private PubmedArticle(
        String pmid,
        Article article,
        MedlineJournalInfo journalInfo,
        PubmedData pubmedData,
        JsonNode rawCitation,
        JsonNode rawPubmedData
    ) {
        this.pmid = pmid;
        this.article = article;
        this.journalInfo = journalInfo;
        this.pubmedData = pubmedData;
        this.rawCitation = rawCitation;
        this.rawPubmedData = rawPubmedData;
    }

    /**
     * Parse a PubMed article node into a curated representation.
     *
     * @param articleNode raw article node
     * @return structured article view
     */
    public static PubmedArticle from(JsonNode articleNode) {
        Objects.requireNonNull(articleNode, "articleNode cannot be null");
        JsonNode citationNode = articleNode.path("MedlineCitation");
        String pmid = JsonHelpers.textValue(citationNode.path("PMID"));
        Article article = Article.from(citationNode.path("Article"));
        MedlineJournalInfo journalInfo = MedlineJournalInfo.from(citationNode.path("MedlineJournalInfo"));
        PubmedData pubmedData = PubmedData.from(articleNode.path("PubmedData"));
        return new PubmedArticle(
            pmid,
            article,
            journalInfo,
            pubmedData,
            citationNode.deepCopy(),
            articleNode.path("PubmedData").deepCopy()
        );
    }

    /**
     * Get the PubMed identifier.
     *
     * @return PubMed identifier
     */
    /**
     * Get the PubMed identifier.
     *
     * @return PubMed identifier
     */
    public String pmid() {
        return pmid;
    }

    /**
     * Get the curated article metadata.
     *
     * @return article metadata
     */
    /**
     * Get the curated article metadata.
     *
     * @return article metadata
     */
    public Article article() {
        return article;
    }

    /**
     * Get the journal metadata.
     *
     * @return journal information
     */
    /**
     * Get the journal metadata block.
     *
     * @return journal information
     */
    public MedlineJournalInfo journalInfo() {
        return journalInfo;
    }

    /**
     * Get additional PubMed data such as history events.
     *
     * @return supplemental PubMed data
     */
    /**
     * Get the supplemental PubMed data block.
     *
     * @return supplemental PubMed data
     */
    public PubmedData pubmedData() {
        return pubmedData;
    }

    /**
     * Get the raw Medline citation node.
     *
     * @return raw citation node
     */
    /**
     * Get the raw Medline citation node for advanced parsing.
     *
     * @return raw citation node
     */
    public JsonNode rawCitation() {
        return rawCitation;
    }

    /**
     * Get the raw PubMed data node.
     *
     * @return raw PubMed data node
     */
    /**
     * Get the raw PubMed data node for advanced parsing.
     *
     * @return raw PubMed data node
     */
    public JsonNode rawPubmedData() {
        return rawPubmedData;
    }
}
