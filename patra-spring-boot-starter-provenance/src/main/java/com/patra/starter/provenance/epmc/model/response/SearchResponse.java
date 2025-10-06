package com.patra.starter.provenance.epmc.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured representation of Europe PMC search response.
 */
public final class SearchResponse {

    private final String version;
    private final long hitCount;
    private final String nextCursorMark;
    private final String nextPageUrl;
    private final Request request;
    private final List<Result> results;
    private final JsonNode raw;

    private SearchResponse(
        String version,
        long hitCount,
        String nextCursorMark,
        String nextPageUrl,
        Request request,
        List<Result> results,
        JsonNode raw
    ) {
        this.version = version;
        this.hitCount = hitCount;
        this.nextCursorMark = nextCursorMark;
        this.nextPageUrl = nextPageUrl;
        this.request = request;
        this.results = results;
        this.raw = raw;
    }

    public static SearchResponse from(JsonNode root) {
        Objects.requireNonNull(root, "root cannot be null");
        String version = JsonHelpers.textValue(root.path("version"));
        long hitCount = root.path("hitCount").asLong(0);
        String nextCursorMark = JsonHelpers.textValue(root.path("nextCursorMark"));
        String nextPageUrl = JsonHelpers.textValue(root.path("nextPageUrl"));
        Request request = Request.from(root.path("request"));

        List<Result> results = new ArrayList<>();
        for (JsonNode resultNode : JsonHelpers.toNodeList(root.path("resultList").path("result"))) {
            results.add(Result.from(resultNode));
        }
        return new SearchResponse(
            version,
            hitCount,
            nextCursorMark,
            nextPageUrl,
            request,
            Collections.unmodifiableList(results),
            root.deepCopy()
        );
    }

    public static SearchResponse empty() {
        return new SearchResponse(null, 0, null, null, Request.empty(), Collections.emptyList(), null);
    }

    public String version() {
        return version;
    }

    public long hitCount() {
        return hitCount;
    }

    public String nextCursorMark() {
        return nextCursorMark;
    }

    public String nextPageUrl() {
        return nextPageUrl;
    }

    public Request request() {
        return request;
    }

    public List<Result> results() {
        return results;
    }

    public JsonNode raw() {
        return raw;
    }

    public record Request(
        String queryString,
        String resultType,
        String cursorMark,
        Integer pageSize,
        String sort,
        Boolean synonym
    ) {
        private static Request empty() {
            return new Request(null, null, null, null, null, null);
        }

        private static Request from(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return empty();
            }
            Integer pageSize = node.hasNonNull("pageSize") ? node.path("pageSize").asInt() : null;
            Boolean synonym = node.hasNonNull("synonym") ? node.path("synonym").asBoolean() : null;
            return new Request(
                JsonHelpers.textValue(node.path("queryString")),
                JsonHelpers.textValue(node.path("resultType")),
                JsonHelpers.textValue(node.path("cursorMark")),
                pageSize,
                JsonHelpers.textValue(node.path("sort")),
                synonym
            );
        }
    }

    public record Result(
        String id,
        String source,
        String pmid,
        String pmcid,
        String doi,
        String title,
        String authorString,
        String journalTitle,
        String pubYear,
        String journalIssn,
        String pageInfo,
        String pubType,
        String abstractText,
        Integer citedByCount,
        JsonNode raw
    ) {
        private static Result from(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return new Result(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            }
            Integer citedBy = node.hasNonNull("citedByCount") ? node.path("citedByCount").asInt() : null;
            return new Result(
                JsonHelpers.textValue(node.path("id")),
                JsonHelpers.textValue(node.path("source")),
                JsonHelpers.textValue(node.path("pmid")),
                JsonHelpers.textValue(node.path("pmcid")),
                JsonHelpers.textValue(node.path("doi")),
                JsonHelpers.textValue(node.path("title")),
                JsonHelpers.textValue(node.path("authorString")),
                JsonHelpers.textValue(node.path("journalTitle")),
                JsonHelpers.textValue(node.path("pubYear")),
                JsonHelpers.textValue(node.path("journalIssn")),
                JsonHelpers.textValue(node.path("pageInfo")),
                JsonHelpers.textValue(node.path("pubType")),
                JsonHelpers.textValue(node.path("abstractText")),
                citedBy,
                node.deepCopy()
            );
        }
    }
}
