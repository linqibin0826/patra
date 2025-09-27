package com.patra.expr.canonical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.util.HashUtils;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.json.ExprJsonCodec;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 提供表达式的规范化 JSON 及散列计算能力，供各模块复用。
 */
public final class ExprCanonicalizer {
    private static final ObjectMapper OBJECT_MAPPER;
    private static final JsonNodeFactory NODE_FACTORY;
    private static final ObjectWriter CANONICAL_WRITER;
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");

    static {
        OBJECT_MAPPER = ExprJsonCodec.mapper();
        NODE_FACTORY = OBJECT_MAPPER.getNodeFactory();
        CANONICAL_WRITER = OBJECT_MAPPER.writer();
    }

    private ExprCanonicalizer() {
    }

    /**
     * 对表达式进行规范化，返回包含确定性 JSON 文本与 SHA-256 散列的快照。
     *
     * @param expr 需要规范化的表达式
     * @return 规范化快照
     */
    public static ExprCanonicalSnapshot canonicalize(Expr expr) {
        Objects.requireNonNull(expr, "expr不能为空");
        try {
            JsonNode raw = OBJECT_MAPPER.readTree(Exprs.toJson(expr));
            JsonNode canonical = canonicalizeNode(raw);
            String canonicalJson = CANONICAL_WRITER.writeValueAsString(canonical);
            String hash = HashUtils.sha256Hex(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return new ExprCanonicalSnapshot(expr, canonicalJson, hash);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("表达式规范化失败", ex);
        }
    }
    private static JsonNode canonicalizeNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return NullNode.getInstance();
        }
        if (node.isObject()) {
            return canonicalizeObject((ObjectNode) node);
        }
        if (node.isArray()) {
            return canonicalizeArray((ArrayNode) node);
        }
        if (node.isTextual()) {
            return canonicalizeText(node.textValue());
        }
        if (node.isNumber()) {
            return canonicalizeNumber(node);
        }
        return node;
    }
    private static JsonNode canonicalizeObject(ObjectNode objectNode) {
        List<String> fieldNames = new ArrayList<>();
        objectNode.fieldNames().forEachRemaining(fieldNames::add);
        fieldNames.sort(Comparator.naturalOrder());

        ObjectNode canonical = NODE_FACTORY.objectNode();
        for (String field : fieldNames) {
            JsonNode child = canonicalizeNode(objectNode.get(field));
            if (!isEmpty(child)) {
                canonical.set(field, child);
            }
        }
        return canonical;
    }
    private static JsonNode canonicalizeArray(ArrayNode arrayNode) {
        List<CanonicalElement> elements = new ArrayList<>();
        for (JsonNode element : arrayNode) {
            JsonNode normalized = canonicalizeNode(element);
            if (isEmpty(normalized)) {
                continue;
            }
            String typeTag = typeTag(normalized);
            String serialized = writeJson(normalized);
            elements.add(new CanonicalElement(normalized, typeTag, serialized));
        }

        Map<String, CanonicalElement> deduplicated = new LinkedHashMap<>();
        for (CanonicalElement element : elements) {
            String identity = element.typeTag + "|" + element.serialized;
            deduplicated.putIfAbsent(identity, element);
        }

        List<CanonicalElement> ordered = new ArrayList<>(deduplicated.values());
        ordered.sort(Comparator
                .comparing((CanonicalElement it) -> it.typeTag)
                .thenComparing(it -> it.serialized));

        ArrayNode canonical = NODE_FACTORY.arrayNode();
        for (CanonicalElement element : ordered) {
            canonical.add(element.value);
        }
        return canonical.isEmpty() ? NullNode.getInstance() : canonical;
    }
    private static JsonNode canonicalizeText(String text) {
        if (text == null) {
            return NullNode.getInstance();
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return NullNode.getInstance();
        }
        String collapsed = SPACE_PATTERN.matcher(trimmed).replaceAll(" ");
        return NODE_FACTORY.textNode(collapsed);
    }
    private static JsonNode canonicalizeNumber(JsonNode node) {
        if (!node.isNumber()) {
            return node;
        }
        BigDecimal decimal = node.decimalValue().stripTrailingZeros();
        if (decimal.scale() < 0) {
            decimal = decimal.setScale(0);
        }
        return NODE_FACTORY.numberNode(decimal);
    }
    private static boolean isEmpty(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return true;
        }
        if (node.isTextual()) {
            return node.textValue().isEmpty();
        }
        if (node.isArray()) {
            return node.isEmpty();
        }
        if (node.isObject()) {
            return node.isEmpty();
        }
        return false;
    }
    private static String typeTag(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "0";
        }
        if (node.isBoolean()) {
            return "1";
        }
        if (node.isNumber()) {
            return "2";
        }
        if (node.isTextual()) {
            return "3";
        }
        if (node.isObject()) {
            return "4";
        }
        if (node.isArray()) {
            return "5";
        }
        return "9";
    }

    private static String writeJson(JsonNode node) {
        try {
            return CANONICAL_WRITER.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("写入 JSON 失败", ex);
        }
    }
    private record CanonicalElement(JsonNode value, String typeTag, String serialized) {
        private CanonicalElement {
            Objects.requireNonNull(value, "value不能为空");
            Objects.requireNonNull(typeTag, "typeTag不能为空");
            Objects.requireNonNull(serialized, "serialized不能为空");
        }
    }
}
