package com.patra.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;

/**
 * Helper utilities for converting between JSON structures and {@link JsonNode} while reusing the
 * platform-configured {@link ObjectMapper}.
 */
public final class JsonNodeMappings {

  private static final ObjectMapper MAPPER = JsonMapperHolder.getObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private JsonNodeMappings() {}

  /**
   * Converts a JSON string into a {@link JsonNode}.
   *
   * @param json source JSON string
   * @return parsed node, or {@code null} if the input is blank
   */
  public static JsonNode jsonStringToNode(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return MAPPER.readTree(json);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Unable to parse JSON", ex);
    }
  }

  /**
   * Converts a {@link JsonNode} back to its string representation.
   *
   * @param node JSON node to serialize
   * @return JSON string, or {@code null} if the node itself is {@code null}
   */
  public static String jsonNodeToString(JsonNode node) {
    if (node == null) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(node);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException(
          "Unable to serialize JsonNode: " + node.getClass().getSimpleName(), ex);
    }
  }

  /**
   * Converts a {@link Map} into a {@link JsonNode}.
   *
   * @param map source map
   * @return corresponding node, or {@code null} for {@code null}/empty input
   */
  public static JsonNode mapToJsonNode(Map<String, ?> map) {
    if (map == null || map.isEmpty()) {
      return null;
    }
    return MAPPER.valueToTree(map);
  }

  /**
   * Converts a {@link JsonNode} into a {@link Map} view.
   *
   * @param node JSON node to convert
   * @return map representation, or an empty map if the node is {@code null} or JSON {@code null}
   */
  public static Map<String, Object> jsonNodeToMap(JsonNode node) {
    if (node == null || node.isNull()) {
      return Collections.emptyMap();
    }
    return MAPPER.convertValue(node, MAP_TYPE);
  }
}
