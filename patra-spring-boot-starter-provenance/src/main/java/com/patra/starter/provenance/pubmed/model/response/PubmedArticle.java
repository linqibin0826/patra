package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

import java.util.Objects;

/**
 * Simplified view over a PubMed article with access to the raw citation nodes.
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

    public String pmid() {
        return pmid;
    }

    public Article article() {
        return article;
    }

    public MedlineJournalInfo journalInfo() {
        return journalInfo;
    }

    public PubmedData pubmedData() {
        return pubmedData;
    }

    public JsonNode rawCitation() {
        return rawCitation;
    }

    public JsonNode rawPubmedData() {
        return rawPubmedData;
    }
}
