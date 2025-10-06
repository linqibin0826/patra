package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Strongly typed view of the PubMed ESearch response while preserving the raw payload.
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class ESearchResponse {

    private final Header header;
    private final Result result;
    private final JsonNode raw;

    private ESearchResponse(Header header, Result result, JsonNode raw) {
        this.header = header;
        this.result = result;
        this.raw = raw;
    }

    /**
     * Parse the PubMed ESearch response tree into a structured representation.
     *
     * @param root response root node
     * @return structured response view
     */
    public static ESearchResponse from(JsonNode root) {
        Objects.requireNonNull(root, "root cannot be null");

        Header header = new Header(
            safeText(root.path("header"), "type"),
            safeText(root.path("header"), "version")
        );

        JsonNode resultNode = root.path("esearchresult");
        Result result = new Result(
            parseInt(resultNode, "count"),
            parseInt(resultNode, "retmax"),
            parseInt(resultNode, "retstart"),
            JsonHelpers.toStringList(resultNode.path("idlist")),
            parseTranslationSet(resultNode.path("translationset")),
            JsonHelpers.toNodeList(resultNode.path("translationstack")),
            safeText(resultNode, "webenv"),
            safeText(resultNode, "querykey"),
            safeText(resultNode, "querytranslation"),
            JsonHelpers.toStringList(resultNode.path("errorlist").path("phrase")),
            parseWarningMessages(resultNode.path("warnings"))
        );

        return new ESearchResponse(header, result, root.deepCopy());
    }

    /**
     * Create an empty response placeholder for no-op scenarios.
     *
     * @return empty response instance
     */
    public static ESearchResponse empty() {
        return new ESearchResponse(
            new Header(null, null),
            new Result(0, 0, 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, null, null, Collections.emptyList(), Collections.emptyList()),
            null
        );
    }

    /**
     * Get the response header block returned by PubMed.
     *
     * @return response header
     */
    public Header header() {
        return header;
    }

    /**
     * Get the main search result payload.
     *
     * @return structured result view
     */
    public Result result() {
        return result;
    }

    /**
     * Get the raw JSON payload for advanced consumers.
     *
     * @return raw response node or {@code null}
     */
    public JsonNode raw() {
        return raw;
    }

    private static List<Translation> parseTranslationSet(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Collections.emptyList();
        }
        List<Translation> translations = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String from = safeText(item, "from");
                String to = safeText(item, "to");
                if (from != null || to != null) {
                    translations.add(new Translation(from, to));
                }
            }
        } else {
            String from = safeText(node, "from");
            String to = safeText(node, "to");
            if (from != null || to != null) {
                translations.add(new Translation(from, to));
            }
        }
        return Collections.unmodifiableList(translations);
    }

    private static List<String> parseWarningMessages(JsonNode warningsNode) {
        if (warningsNode == null || warningsNode.isMissingNode() || warningsNode.isNull()) {
            return Collections.emptyList();
        }
        // Common shapes: {"outputmessage": ["..."]} or {"warning": ["..."]}
        List<String> messages = new ArrayList<>();
        messages.addAll(JsonHelpers.toStringList(warningsNode.path("outputmessage")));
        messages.addAll(JsonHelpers.toStringList(warningsNode.path("warning")));
        if (messages.isEmpty()) {
            messages.addAll(JsonHelpers.toStringList(warningsNode));
        }
        return Collections.unmodifiableList(messages);
    }

    private static int parseInt(JsonNode node, String field) {
        JsonNode target = node.get(field);
        if (target == null || target.isNull()) {
            return 0;
        }
        if (target.isInt()) {
            return target.asInt();
        }
        if (target.isTextual()) {
            try {
                return Integer.parseInt(target.asText());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return target.asInt(0);
    }

    private static String safeText(JsonNode node, String field) {
        return JsonHelpers.textValue(node.get(field));
    }

    /**
     * Metadata header returned by the ESearch endpoint.
     *
     * <p>Field descriptions:
     * @param type response type indicator
     * @param version ESearch API version
     *
     * @author linqibin
     * @since 0.1.0
     */
    public record Header(String type, String version) {
    }

    /**
     * Search result payload summarising identifiers, translations and warnings.
     *
     * <p>Field descriptions:
     * @param count total records matching the query
     * @param retMax maximum records returned
     * @param retStart offset for the current page
     * @param idList identifiers returned by the call
     * @param translationSet translation pairs applied by PubMed
     * @param translationStack raw translation stack nodes
     * @param webEnv history server WebEnv token
     * @param queryKey query key for history server reuse
     * @param queryTranslation translated query string
     * @param errorPhrases reported error phrases
     * @param warningMessages warning messages emitted by PubMed
     *
     * @author linqibin
     * @since 0.1.0
     */
    public record Result(
        int count,
        int retMax,
        int retStart,
        List<String> idList,
        List<Translation> translationSet,
        List<JsonNode> translationStack,
        String webEnv,
        String queryKey,
        String queryTranslation,
        List<String> errorPhrases,
        List<String> warningMessages
    ) {
    }

    /**
     * Query translation pair applied by PubMed.
     *
     * <p>Field descriptions:
     * @param from original token
     * @param to translated token applied to the query
     *
     * @author linqibin
     * @since 0.1.0
     */
    public record Translation(String from, String to) {
    }
}
