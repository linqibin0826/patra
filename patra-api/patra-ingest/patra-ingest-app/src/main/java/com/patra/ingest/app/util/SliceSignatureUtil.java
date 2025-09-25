package com.patra.ingest.app.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 切片签名：仅针对 slice_spec JSON 边界做规范化 (字段有序、去空白) 后计算 SHA-256。
 * 目的：在同一 Plan 下相同边界不重复生成。
 */
public final class SliceSignatureUtil {
    private SliceSignatureUtil() {}

    public static String signatureHash(ObjectMapper mapper, String specJson) {
        if (specJson == null || specJson.isBlank()) {
            return ExprHashUtil.sha256Hex("");
        }
        try {
            JsonNode node = mapper.readTree(specJson);
            JsonNode normalized = sortRecursively(mapper, node);
            String canonical = mapper.writeValueAsString(normalized);
            return ExprHashUtil.sha256Hex(canonical);
        } catch (Exception e) {
            // 解析失败时退化为原串哈希，避免阻断流程
            return ExprHashUtil.sha256Hex(specJson);
        }
    }

    private static JsonNode sortRecursively(ObjectMapper mapper, JsonNode node) {
        if (node == null || node.isMissingNode()) return mapper.nullNode();
        if (node.isObject()) {
            ObjectNode sorted = mapper.createObjectNode();
            java.util.TreeSet<String> names = new java.util.TreeSet<>();
            node.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                sorted.set(name, sortRecursively(mapper, node.get(name)));
            }
            return sorted;
        } else if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode copy = mapper.createArrayNode();
            for (JsonNode child : node) {
                copy.add(sortRecursively(mapper, child));
            }
            return copy;
        }
        return node; // 值节点直接返回
    }
}
