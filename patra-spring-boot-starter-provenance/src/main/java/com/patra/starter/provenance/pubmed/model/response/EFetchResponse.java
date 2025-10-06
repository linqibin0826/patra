package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Parsed PubMed EFetch response with curated article view plus raw payload access.
 */
public final class EFetchResponse {

    private final List<PubmedArticle> articles;
    private final JsonNode raw;

    private EFetchResponse(List<PubmedArticle> articles, JsonNode raw) {
        this.articles = articles;
        this.raw = raw;
    }

    public static EFetchResponse from(JsonNode root) {
        Objects.requireNonNull(root, "root cannot be null");
        List<JsonNode> articleNodes = JsonHelpers.toNodeList(root.path("PubmedArticle"));
        List<PubmedArticle> articles = new ArrayList<>(articleNodes.size());
        for (JsonNode articleNode : articleNodes) {
            articles.add(PubmedArticle.from(articleNode));
        }
        return new EFetchResponse(Collections.unmodifiableList(articles), root.deepCopy());
    }

    public static EFetchResponse empty() {
        return new EFetchResponse(Collections.emptyList(), null);
    }

    public List<PubmedArticle> articles() {
        return articles;
    }

    public JsonNode raw() {
        return raw;
    }
}
