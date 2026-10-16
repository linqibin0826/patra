package dev.patra.tool.jpa.json;

import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// JSON 列与领域值之间的转换器，供 JPA 映射器使用。
///
/// 持有容器里的 `JsonMapper`，所以写进 JSON 列的内容和应用其他地方的 JSON 形态一致
/// （`Long` 是数字）。映射器用 `@Autowired protected JsonColumnCodec codec` 注入后，在
/// 手写方法和 `@Mapping(expression = ...)` 里都能调用。
///
/// 空值语义：
///
/// - [#read]：空白文本得到 `null`
/// - [#write]：`null` 节点得到 `null`
/// - [#fromMap]：`null` 或空 Map 得到 `null`，列保持 NULL
/// - [#toMap]：`null`、JSON `null`、缺失节点都得到空 Map
public final class JsonColumnCodec {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JsonMapper jsonMapper;

  /// 用容器里的 `JsonMapper` 创建转换器。
  ///
  /// @param jsonMapper 容器里的 `JsonMapper`
  public JsonColumnCodec(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /// 把 JSON 文本解析成节点。
  ///
  /// @param json JSON 文本，可为 `null`
  /// @return 解析后的节点；文本为空白时返回 `null`
  /// @throws IllegalArgumentException JSON 格式无效
  public JsonNode read(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return jsonMapper.readTree(json);
    } catch (JacksonException ex) {
      throw new IllegalArgumentException("JSON 解析失败", ex);
    }
  }

  /// 把节点写成 JSON 文本。
  ///
  /// @param node JSON 节点，可为 `null`
  /// @return JSON 文本；节点为 `null` 时返回 `null`
  /// @throws IllegalArgumentException 序列化失败
  public String write(JsonNode node) {
    if (node == null) {
      return null;
    }
    try {
      return jsonMapper.writeValueAsString(node);
    } catch (JacksonException ex) {
      throw new IllegalArgumentException("JSON 序列化失败: " + node.getClass().getSimpleName(), ex);
    }
  }

  /// 把 Map 转成 JSON 节点。
  ///
  /// @param map 源 Map，可为 `null`
  /// @return 对应的节点；Map 为 `null` 或空时返回 `null`
  public JsonNode fromMap(Map<String, ?> map) {
    if (map == null || map.isEmpty()) {
      return null;
    }
    return jsonMapper.valueToTree(map);
  }

  /// 把 JSON 节点转成 Map。
  ///
  /// @param node JSON 节点，可为 `null`
  /// @return Map 形式；节点为 `null`、JSON `null` 或缺失时返回空 Map
  public Map<String, Object> toMap(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return Map.of();
    }
    return jsonMapper.convertValue(node, MAP_TYPE);
  }
}
