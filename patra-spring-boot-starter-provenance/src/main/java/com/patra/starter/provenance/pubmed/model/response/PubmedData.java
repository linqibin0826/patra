package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Supplemental PubMed data block.
 */
public final class PubmedData {

    private final String publicationStatus;
    private final List<HistoryEvent> history;
    private final JsonNode raw;

    private PubmedData(String publicationStatus, List<HistoryEvent> history, JsonNode raw) {
        this.publicationStatus = publicationStatus;
        this.history = history;
        this.raw = raw;
    }

    public static PubmedData from(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new PubmedData(null, Collections.emptyList(), null);
        }
        String status = JsonHelpers.textValue(node.path("PublicationStatus"));
        List<HistoryEvent> history = parseHistory(node.path("History"));
        return new PubmedData(status, history, node.deepCopy());
    }

    private static List<HistoryEvent> parseHistory(JsonNode historyNode) {
        if (historyNode == null || historyNode.isMissingNode() || historyNode.isNull()) {
            return Collections.emptyList();
        }
        List<HistoryEvent> events = new ArrayList<>();
        for (JsonNode dateNode : JsonHelpers.toNodeList(historyNode.path("PubMedPubDate"))) {
            String status = JsonHelpers.textValue(dateNode.path("@PubStatus"));
            String year = JsonHelpers.textValue(dateNode.path("Year"));
            String month = JsonHelpers.textValue(dateNode.path("Month"));
            String day = JsonHelpers.textValue(dateNode.path("Day"));
            events.add(new HistoryEvent(status, year, month, day));
        }
        return Collections.unmodifiableList(events);
    }

    public String publicationStatus() {
        return publicationStatus;
    }

    public List<HistoryEvent> history() {
        return history;
    }

    public JsonNode raw() {
        return raw;
    }

    public record HistoryEvent(String status, String year, String month, String day) {
    }
}
