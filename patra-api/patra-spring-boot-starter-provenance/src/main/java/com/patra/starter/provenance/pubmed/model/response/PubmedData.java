package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Supplemental PubMed data block.
 *
 * @author linqibin
 * @since 0.1.0
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

  /**
   * Parse the PubMed data node into a curated representation.
   *
   * @param node PubMed data node
   * @return structured PubMed data
   */
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

  /**
   * Get the publication status reported by PubMed.
   *
   * @return publication status or {@code null}
   */
  public String publicationStatus() {
    return publicationStatus;
  }

  /**
   * Get the list of PubMed history events.
   *
   * @return immutable list of history events
   */
  public List<HistoryEvent> history() {
    return history;
  }

  /**
   * Get the raw PubMed data node for advanced consumers.
   *
   * @return raw PubMed data node or {@code null}
   */
  public JsonNode raw() {
    return raw;
  }

  /**
   * History event describing a key publication milestone.
   *
   * <p>Field descriptions:
   *
   * @param status status tag assigned by PubMed
   * @param year year component
   * @param month month component
   * @param day day component
   * @author linqibin
   * @since 0.1.0
   */
  public record HistoryEvent(String status, String year, String month, String day) {}
}
