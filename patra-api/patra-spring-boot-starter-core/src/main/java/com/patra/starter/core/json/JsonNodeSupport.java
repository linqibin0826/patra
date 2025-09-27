package com.patra.starter.core.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.json.JsonMapperHolder;
import org.springframework.util.StringUtils;

/**
 * <p><b>JSON 节点转换支撑</b></p>
 * <p>为 MapStruct 默认方法复用的 JSON 解析/序列化工具，统一复用平台级 {@link ObjectMapper}。</p>
 */
public interface JsonNodeSupport {

    /** 获取平台统一配置的 {@link ObjectMapper}。 */
    private ObjectMapper jsonMapper() {
        return JsonMapperHolder.getObjectMapper();
    }

    /**
     * 字符串转 {@link JsonNode}。
     *
     * @param json 原始 JSON 字符串
     * @return JsonNode 或 null
     */
    default JsonNode readJsonNode(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return jsonMapper().readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 解析失败", e);
        }
    }

    /**
     * {@link JsonNode} 转字符串。
     *
     * @param node JsonNode 对象
     * @return JSON 字符串或 null
     */
    default String writeJsonString(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return jsonMapper().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 序列化失败", e);
        }
    }
}
