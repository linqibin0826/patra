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
 * Produces deterministic JSON snapshots and hashes for expressions so downstream services can
 * cache, deduplicate, or audit requests consistently.
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

  private ExprCanonicalizer() {}

  /**
   * Normalizes the supplied expression and returns a snapshot containing deterministic JSON text
   * and its SHA-256 digest.
   *
   * @param expr expression to normalize
   * @return canonical snapshot
   */
  public static ExprCanonicalSnapshot canonicalize(Expr expr) {
    Objects.requireNonNull(expr, "expr must not be null");
    try {
      JsonNode raw = OBJECT_MAPPER.readTree(Exprs.toJson(expr));
      JsonNode canonical = canonicalizeNode(raw);
      String canonicalJson = CANONICAL_WRITER.writeValueAsString(canonical);
      String hash = HashUtils.sha256Hex(canonicalJson.getBytes(StandardCharsets.UTF_8));
      return new ExprCanonicalSnapshot(expr, canonicalJson, hash);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to canonicalize expression", ex);
    }
  }

  /**
   * Recursively canonicalizes a JSON node.
   *
   * @param node the node to canonicalize
   * @return canonical JSON node
   */
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

  /**
   * Canonicalizes an object node by sorting keys and removing empty values.
   *
   * @param objectNode the object node to canonicalize
   * @return canonical object node
   */
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

  /**
   * Canonicalizes an array node by deduplicating and sorting elements.
   *
   * @param arrayNode the array node to canonicalize
   * @return canonical array node or NullNode if empty
   */
  private static JsonNode canonicalizeArray(ArrayNode arrayNode) {
    List<CanonicalElement> elements = buildCanonicalElements(arrayNode);
    List<CanonicalElement> deduplicated = deduplicateElements(elements);
    List<CanonicalElement> sorted = sortElements(deduplicated);
    return buildCanonicalArrayNode(sorted);
  }

  private static List<CanonicalElement> buildCanonicalElements(ArrayNode arrayNode) {
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
    return elements;
  }

  private static List<CanonicalElement> deduplicateElements(List<CanonicalElement> elements) {
    Map<String, CanonicalElement> deduplicated = new LinkedHashMap<>();
    for (CanonicalElement element : elements) {
      String identity = element.typeTag + "|" + element.serialized;
      deduplicated.putIfAbsent(identity, element);
    }
    return new ArrayList<>(deduplicated.values());
  }

  private static List<CanonicalElement> sortElements(List<CanonicalElement> elements) {
    List<CanonicalElement> sorted = new ArrayList<>(elements);
    sorted.sort(
        Comparator.comparing((CanonicalElement it) -> it.typeTag)
            .thenComparing(it -> it.serialized));
    return sorted;
  }

  private static JsonNode buildCanonicalArrayNode(List<CanonicalElement> elements) {
    ArrayNode canonical = NODE_FACTORY.arrayNode();
    for (CanonicalElement element : elements) {
      canonical.add(element.value);
    }
    return canonical.isEmpty() ? NullNode.getInstance() : canonical;
  }

  /**
   * Canonicalizes text by trimming and collapsing whitespace.
   *
   * @param text the text to canonicalize
   * @return canonical text node or NullNode if empty
   */
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

  /**
   * Canonicalizes numbers by stripping trailing zeros.
   *
   * @param node the number node to canonicalize
   * @return canonical number node
   */
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

  /**
   * Checks if a JSON node is considered empty.
   *
   * @param node the node to check
   * @return true if node is null, missing, or empty
   */
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

  /**
   * Returns a type tag for sorting JSON nodes.
   *
   * @param node the node to tag
   * @return numeric type tag as string
   */
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
      throw new IllegalStateException("Failed to write canonical JSON", ex);
    }
  }

  private record CanonicalElement(JsonNode value, String typeTag, String serialized) {
    private CanonicalElement {
      Objects.requireNonNull(value, "value must not be null");
      Objects.requireNonNull(typeTag, "typeTag must not be null");
      Objects.requireNonNull(serialized, "serialized must not be null");
    }
  }
}
