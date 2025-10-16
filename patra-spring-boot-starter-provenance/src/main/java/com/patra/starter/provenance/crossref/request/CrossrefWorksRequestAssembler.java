package com.patra.starter.provenance.crossref.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.patra.starter.provenance.crossref.model.request.CrossrefWorksRequest;

/**
 * Assemble {@link CrossrefWorksRequest} objects from provider-named parameters emitted by the
 * expression compiler.
 */
public class CrossrefWorksRequestAssembler {

  /**
   * Build a {@link CrossrefWorksRequest} using provider parameters (query, filter, rows, etc.).
   *
   * @param params provider parameter map as JSON
   * @return assembled request
   */
  public CrossrefWorksRequest build(JsonNode params) {
    String query = text(params, CrossrefParamKeys.QUERY);
    String filter = text(params, CrossrefParamKeys.FILTER);
    Integer rows = integer(params, CrossrefParamKeys.ROWS);
    Integer offset = integer(params, CrossrefParamKeys.OFFSET);
    String sort = text(params, CrossrefParamKeys.SORT);
    String order = text(params, CrossrefParamKeys.ORDER);
    String cursor = text(params, CrossrefParamKeys.CURSOR);
    String select = text(params, CrossrefParamKeys.SELECT);
    String mailto = text(params, CrossrefParamKeys.MAILTO);

    return new CrossrefWorksRequest(
        query, filter, rows, offset, sort, order, cursor, select, mailto);
  }

  private static String text(JsonNode node, String key) {
    if (node == null || node.isNull() || key == null) {
      return null;
    }
    JsonNode value = node.get(key);
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.getNodeType() == JsonNodeType.STRING) {
      String text = value.asText();
      return text != null && !text.isBlank() ? text : null;
    }
    return value.isValueNode() ? value.asText() : null;
  }

  private static Integer integer(JsonNode node, String key) {
    if (node == null || node.isNull() || key == null) {
      return null;
    }
    JsonNode value = node.get(key);
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isInt()) {
      return value.intValue();
    }
    if (value.isLong()) {
      long v = value.longValue();
      if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
        throw new IllegalArgumentException(
            "Crossref parameter '" + key + "' exceeds integer range");
      }
      return (int) v;
    }
    if (value.isTextual()) {
      String text = value.asText();
      if (text == null || text.isBlank()) {
        return null;
      }
      try {
        return Integer.parseInt(text);
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
            "Crossref parameter '" + key + "' is not a valid integer: " + text, ex);
      }
    }
    return null;
  }
}
