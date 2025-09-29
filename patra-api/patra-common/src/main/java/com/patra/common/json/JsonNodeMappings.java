package com.patra.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;

/**
 * JSON 与 {@link JsonNode} 映射辅助，统一复用平台配置的 {@link ObjectMapper}。
 */
public final class JsonNodeMappings {

    private static final ObjectMapper MAPPER = JsonMapperHolder.getObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private JsonNodeMappings() {
    }

    /**
     * 字符串转 {@link JsonNode}。
     *
     * @param json 原始 JSON 字符串
     * @return 解析后的节点，空白字符串返回 null
     */
    public static JsonNode jsonStringToNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON 解析失败", ex);
        }
    }

    /**
     * {@link JsonNode} 转字符串。
     *
     * @param node JsonNode 对象
     * @return 字符串表现形式，null 节点返回 null
     */
    public static String jsonNodeToString(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON 序列化失败", ex);
        }
    }

    /**
     * Map 转 {@link JsonNode}。
     *
     * @param map 原始 Map
     * @return 对应节点，空 Map 返回 null
     */
    public static JsonNode mapToJsonNode(Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return MAPPER.valueToTree(map);
    }

    /**
     * {@link JsonNode} 转 Map。
     *
     * @param node JsonNode 对象
     * @return Map 表现形式，null 节点返回空 Map
     */
    public static Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Collections.emptyMap();
        }
        return MAPPER.convertValue(node, MAP_TYPE);
    }
}
