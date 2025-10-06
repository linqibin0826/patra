package com.patra.starter.provenance.epmc.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured representation of the Europe PMC search response payload.
 *
 * <p>Exposes curated fields while providing access to the raw JSON for
 * downstream processing.</p>
 *
 * @author linqibin
 * @since 0.1.0
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

    /**
     * Parse a Europe PMC response tree into the strongly typed representation.
     *
     * @param root response root node
     * @return structured response view
     */
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

    /**
     * Create an empty response placeholder for no-op scenarios.
     *
     * @return empty response instance
     */
    public static SearchResponse empty() {
        return new SearchResponse(null, 0, null, null, Request.empty(), Collections.emptyList(), null);
    }

    /**
     * Get the API version reported by Europe PMC.
     *
     * @return response version string
     */
    /**
     * Get the API version reported by Europe PMC.
     *
     * @return response version string
     */
    public String version() {
        return version;
    }

    /**
     * Get the total hit count for the query.
     *
     * @return hit count
     */
    /**
     * Get the total hit count for the query.
     *
     * @return hit count
     */
    public long hitCount() {
        return hitCount;
    }

    /**
     * Get the cursor mark for continued paging.
     *
     * @return cursor mark token or {@code null}
     */
    /**
     * Get the cursor mark for continued paging.
     *
     * @return cursor mark token or {@code null}
     */
    public String nextCursorMark() {
        return nextCursorMark;
    }

    /**
     * Get the URL that represents the next page link, if provided.
     *
     * @return next page URL or {@code null}
     */
    /**
     * Get the URL that represents the next page link, if provided.
     *
     * @return next page URL or {@code null}
     */
    public String nextPageUrl() {
        return nextPageUrl;
    }

    /**
     * Get the request echo block returned by Europe PMC.
     *
     * @return structured request metadata
     */
    /**
     * Get the structured request echo returned by Europe PMC.
     *
     * @return request metadata
     */
    public Request request() {
        return request;
    }

    /**
     * Get the list of search results.
     *
     * @return immutable list of results
     */
    /**
     * Get the immutable list of search results.
     *
     * @return search results
     */
    public List<Result> results() {
        return results;
    }

    /**
     * Get the raw JSON payload for advanced consumers.
     *
     * @return raw response node or {@code null}
     */
    /**
     * Get the raw JSON payload for advanced consumers.
     *
     * @return raw response node or {@code null}
     */
    public JsonNode raw() {
        return raw;
    }

    /**
     * Echo parameters returned by Europe PMC.
     *
     * <p>Field descriptions:
     * @param queryString resolved query string
     * @param resultType result projection requested
     * @param cursorMark cursor token for deep paging
     * @param pageSize page size echoed by the API
     * @param sort sorting applied to the results
     * @param synonym whether synonym expansion was enabled
     *
     * @author linqibin
     * @since 0.1.0
     */
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

    /**
     * Individual search result summary.
     *
     * <p>Field descriptions:
     * @param id Europe PMC identifier
     * @param source source repository (e.g. MED)
     * @param pmid PubMed identifier when available
     * @param pmcid PubMed Central identifier when available
     * @param doi digital object identifier
     * @param title article title
     * @param authorString concatenated author list
     * @param journalTitle journal name
     * @param pubYear publication year
     * @param journalIssn ISSN code
     * @param pageInfo page information string
     * @param pubType publication type
     * @param abstractText abstract snippet
     * @param citedByCount citation count
     * @param raw raw JSON node for the record
     *
     * @author linqibin
     * @since 0.1.0
     */
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
