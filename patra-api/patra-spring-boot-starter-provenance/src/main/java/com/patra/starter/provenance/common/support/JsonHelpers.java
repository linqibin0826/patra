package com.patra.starter.provenance.common.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Utility helpers for defensive {@link JsonNode} parsing.
 *
 * <p>PubMed/EPMC payloads contain mixed node shapes (arrays, objects with "#text" fields,
 * singletons). These helpers normalise them to predictable Java types while tolerating missing or
 * malformed data.
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class JsonHelpers {

  private JsonHelpers() {}

  /**
   * Extract the textual value from a node while tolerating PubMed/EPMC artefacts.
   *
   * @param node source node that may be missing or shaped as {"#text": value}
   * @return textual representation or {@code null} when unavailable
   */
  public static String textValue(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    if (node.isTextual()) {
      return node.asText();
    }
    JsonNode textNode = node.get("#text");
    if (textNode != null && textNode.isTextual()) {
      return textNode.asText();
    }
    return node.asText(null);
  }

  /**
   * Convert a node to an immutable list of strings while skipping blanks.
   *
   * @param node node representing either a scalar or array of text nodes
   * @return immutable list of non-blank strings
   */
  public static List<String> toStringList(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return Collections.emptyList();
    }
    List<String> values = new ArrayList<>();
    if (node.isArray()) {
      for (JsonNode item : node) {
        String text = textValue(item);
        if (text != null && !text.isBlank()) {
          values.add(text);
        }
      }
      return Collections.unmodifiableList(values);
    }
    String single = textValue(node);
    if (single != null && !single.isBlank()) {
      values.add(single);
    }
    return Collections.unmodifiableList(values);
  }

  /**
   * Convert a node to a defensive immutable list of deep-copied nodes.
   *
   * @param node source node which can be a singleton or array
   * @return immutable list of nodes safe for downstream mutation
   */
  public static List<JsonNode> toNodeList(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return Collections.emptyList();
    }
    List<JsonNode> nodes = new ArrayList<>();
    if (node.isArray()) {
      node.forEach(item -> nodes.add(item.deepCopy()));
    } else {
      nodes.add(node.deepCopy());
    }
    return Collections.unmodifiableList(nodes);
  }

  /**
   * Convenience accessor that extracts a child field and converts it via {@link
   * #toNodeList(JsonNode)}.
   *
   * @param parent parent node containing the desired field
   * @param field child field name
   * @return immutable list of deep-copied nodes for the requested field
   */
  public static List<JsonNode> toNodeListFromField(JsonNode parent, String field) {
    return parent != null ? toNodeList(parent.get(field)) : Collections.emptyList();
  }

  /**
   * Collect all child values from an object node as deep copies.
   *
   * @param objectNode object node whose values should be materialised
   * @return immutable list of value nodes
   */
  public static List<JsonNode> nodeValues(JsonNode objectNode) {
    if (objectNode == null || !objectNode.isObject()) {
      return Collections.emptyList();
    }
    List<JsonNode> values = new ArrayList<>();
    Iterator<JsonNode> iterator = objectNode.elements();
    while (iterator.hasNext()) {
      values.add(iterator.next().deepCopy());
    }
    return Collections.unmodifiableList(values);
  }
}
