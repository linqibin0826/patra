package com.patra.starter.provenance.epmc.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.patra.starter.provenance.epmc.model.request.SearchRequest;

/**
 * Assemble Europe PMC {@link SearchRequest} instances from provider-named parameters emitted by the
 * expression compiler.
 */
public class EpmcSearchRequestAssembler {

  /**
   * Build a Europe PMC search request using the provider parameter map produced by the compiler.
   *
   * @param params provider-named parameters (e.g., query/pageSize/cursorMark)
   * @return assembled {@link SearchRequest}
   */
  public SearchRequest build(JsonNode params) {
    String query = text(params, EpmcParamKeys.QUERY);
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException(
          "EPMC params must include provider key 'query' after compiler mapping");
    }

    String format = text(params, EpmcParamKeys.FORMAT);
    Integer pageSize = integer(params, EpmcParamKeys.PAGE_SIZE);
    String cursorMark = text(params, EpmcParamKeys.CURSOR_MARK);
    String sort = text(params, EpmcParamKeys.SORT);
    String resultType = text(params, EpmcParamKeys.RESULT_TYPE);
    Boolean synonym = bool(params, EpmcParamKeys.SYNONYM);
    Boolean fromSearchPost = bool(params, EpmcParamKeys.FROM_SEARCH_POST);
    String searchType = text(params, EpmcParamKeys.SEARCH_TYPE);

    return new SearchRequest(
        query, format, pageSize, cursorMark, sort, resultType, synonym, fromSearchPost, searchType);
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
        throw new IllegalArgumentException("EPMC pageSize is out of int range: " + v);
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
            "EPMC parameter '" + key + "' is not a valid integer: " + text, ex);
      }
    }
    return null;
  }

  private static Boolean bool(JsonNode node, String key) {
    if (node == null || node.isNull() || key == null) {
      return null;
    }
    JsonNode value = node.get(key);
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isBoolean()) {
      return value.booleanValue();
    }
    if (value.isTextual()) {
      String text = value.asText();
      if (text == null || text.isBlank()) {
        return null;
      }
      return Boolean.parseBoolean(text);
    }
    return null;
  }
}
