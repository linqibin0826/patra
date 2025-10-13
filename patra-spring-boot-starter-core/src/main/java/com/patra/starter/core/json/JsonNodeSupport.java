package com.patra.starter.core.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.json.JsonMapperHolder;
import org.springframework.util.StringUtils;

/**
 * Helper interface for MapStruct default methods to reuse the platform-configured {@link
 * ObjectMapper}.
 */
public interface JsonNodeSupport {

  /** Returns the shared {@link ObjectMapper}. */
  private ObjectMapper jsonMapper() {
    return JsonMapperHolder.getObjectMapper();
  }

  /**
   * Converts a JSON string to a {@link JsonNode}.
   *
   * @param json source JSON string
   * @return parsed node or {@code null} if blank
   */
  default JsonNode readJsonNode(String json) {
    if (!StringUtils.hasText(json)) {
      return null;
    }
    try {
      return jsonMapper().readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unable to parse JSON", e);
    }
  }

  /**
   * Converts a {@link JsonNode} to its JSON string representation.
   *
   * @param node node to serialize
   * @return JSON string or {@code null} if the node is {@code null}
   */
  default String writeJsonString(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    try {
      return jsonMapper().writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unable to serialize JsonNode", e);
    }
  }
}
