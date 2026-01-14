package com.patra.common.json;

import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/// JSON 结构与 {@link JsonNode} 互转的辅助工具类，复用平台配置的 {@link ObjectMapper}。
///
/// 提供 JSON 字符串、JsonNode、Map 之间的双向转换，确保使用统一的 ObjectMapper 配置。
///
/// @author linqibin
/// @since 0.1.0
public final class JsonNodeMappings {

  private static final ObjectMapper MAPPER = JsonMapperHolder.getObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  /// 私有构造函数,防止实例化工具类。
  private JsonNodeMappings() {}

  /// 将 JSON 字符串转换为 {@link JsonNode}。
  ///
  /// @param json 源 JSON 字符串
  /// @return 解析后的节点，如果输入为空白则返回 `null`
  /// @throws IllegalArgumentException 如果 JSON 格式无效
  public static JsonNode jsonStringToNode(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return MAPPER.readTree(json);
    } catch (JacksonException ex) {
      throw new IllegalArgumentException("Unable to parse JSON", ex);
    }
  }

  /// 将 {@link JsonNode} 转换回其字符串表示形式。
  ///
  /// @param node 要序列化的 JSON 节点
  /// @return JSON 字符串，如果节点本身为 `null` 则返回 `null`
  /// @throws IllegalArgumentException 如果序列化失败
  public static String jsonNodeToString(JsonNode node) {
    if (node == null) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(node);
    } catch (JacksonException ex) {
      throw new IllegalArgumentException(
          "Unable to serialize JsonNode: " + node.getClass().getSimpleName(), ex);
    }
  }

  /// 将 {@link Map} 转换为 {@link JsonNode}。
  ///
  /// @param map 源 Map 对象
  /// @return 对应的 JSON 节点，如果输入为 `null` 或空则返回 `null`
  public static JsonNode mapToJsonNode(Map<String, ?> map) {
    if (map == null || map.isEmpty()) {
      return null;
    }
    return MAPPER.valueToTree(map);
  }

  /// 将 {@link JsonNode} 转换为 {@link Map} 视图。
  ///
  /// @param node 要转换的 JSON 节点
  /// @return Map 表示形式，如果节点为 `null` 或 JSON `null` 则返回空 Map
  public static Map<String, Object> jsonNodeToMap(JsonNode node) {
    if (node == null || node.isNull()) {
      return Map.of();
    }
    return MAPPER.convertValue(node, MAP_TYPE);
  }
}
