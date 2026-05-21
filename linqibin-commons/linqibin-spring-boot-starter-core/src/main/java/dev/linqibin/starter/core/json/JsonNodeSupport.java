package dev.linqibin.starter.core.json;

import dev.linqibin.commons.json.JsonMapperHolder;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/// 帮助 MapStruct 默认方法复用平台配置的 {@link ObjectMapper} 的接口。
public interface JsonNodeSupport {

  /// 返回共享的 {@link ObjectMapper}。
  ///
  /// @return ObjectMapper 实例
  /// @throws IllegalArgumentException 如果 ObjectMapper 未初始化

  private ObjectMapper jsonMapper() {
    return JsonMapperHolder.getObjectMapper();
  }

  /// 将 JSON 字符串转换为 {@link JsonNode}。
  ///
  /// @param json 源 JSON 字符串
  /// @return 解析后的节点,如果为空则返回 `null`
  /// @throws IllegalArgumentException 如果 JSON 解析失败

  default JsonNode readJsonNode(String json) {
    if (!StringUtils.hasText(json)) {
      return null;
    }
    try {
      return jsonMapper().readTree(json);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("无法解析 JSON", e);
    }
  }

  /// 将 {@link JsonNode} 转换为其 JSON 字符串表示形式。
  ///
  /// @param node 要序列化的节点
  /// @return JSON 字符串,如果节点为 `null` 则返回 `null`
  /// @throws IllegalArgumentException 如果序列化失败

  default String writeJsonString(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    try {
      return jsonMapper().writeValueAsString(node);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("无法序列化 JsonNode", e);
    }
  }
}
