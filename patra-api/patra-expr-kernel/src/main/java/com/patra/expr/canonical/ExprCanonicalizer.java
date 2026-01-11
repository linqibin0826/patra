package com.patra.expr.canonical;

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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

/// 为表达式生成确定性的 JSON 快照和哈希值,使下游服务能够一致地进行缓存、去重或审计。
///
/// 规范化过程包括:
///
/// - 对对象键进行排序
///   - 对数组元素去重并排序
///   - 修剪空白并折叠多余空格
///   - 去除尾随零
///   - 移除空值和空集合
///
/// 输出的规范 JSON 可用于生成 SHA-256 哈希,确保逻辑相同的表达式产生相同的哈希值。
///
/// @author linqibin
/// @since 0.1.0
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

  /// 私有构造函数,防止实例化工具类。
  private ExprCanonicalizer() {}

  /// 规范化给定的表达式,返回包含确定性 JSON 文本及其 SHA-256 摘要的快照。
  ///
  /// @param expr 待规范化的表达式
  /// @return 规范化快照
  /// @throws IllegalStateException 如果规范化过程失败
  public static ExprCanonicalSnapshot canonicalize(Expr expr) {
    Objects.requireNonNull(expr, "expr must not be null");
    try {
      JsonNode raw = OBJECT_MAPPER.readTree(Exprs.toJson(expr));
      JsonNode canonical = canonicalizeNode(raw);
      String canonicalJson = CANONICAL_WRITER.writeValueAsString(canonical);
      String hash = HashUtils.sha256Hex(canonicalJson.getBytes(StandardCharsets.UTF_8));
      return new ExprCanonicalSnapshot(expr, canonicalJson, hash);
    } catch (JacksonException ex) {
      throw new IllegalStateException("Failed to canonicalize expression", ex);
    }
  }

  /// 递归规范化 JSON 节点。
  ///
  /// @param node 待规范化的节点
  /// @return 规范化后的 JSON 节点
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
      return canonicalizeText(node.stringValue());
    }
    if (node.isNumber()) {
      return canonicalizeNumber(node);
    }
    return node;
  }

  /// 通过排序键并移除空值来规范化对象节点。
  ///
  /// @param objectNode 待规范化的对象节点
  /// @return 规范化后的对象节点
  private static JsonNode canonicalizeObject(ObjectNode objectNode) {
    List<String> fieldNames = new ArrayList<>(objectNode.propertyNames());
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

  /// 通过去重和排序元素来规范化数组节点。
  ///
  /// @param arrayNode 待规范化的数组节点
  /// @return 规范化后的数组节点,如果为空则返回 NullNode
  private static JsonNode canonicalizeArray(ArrayNode arrayNode) {
    List<CanonicalElement> elements = buildCanonicalElements(arrayNode);
    List<CanonicalElement> deduplicated = deduplicateElements(elements);
    List<CanonicalElement> sorted = sortElements(deduplicated);
    return buildCanonicalArrayNode(sorted);
  }

  /// 构建数组节点的规范化元素列表。
  ///
  /// @param arrayNode 待处理的数组节点
  /// @return 规范化元素列表
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

  /// 去除重复的数组元素。
  ///
  /// @param elements 待去重的元素列表
  /// @return 去重后的元素列表
  private static List<CanonicalElement> deduplicateElements(List<CanonicalElement> elements) {
    Map<String, CanonicalElement> deduplicated = new LinkedHashMap<>();
    for (CanonicalElement element : elements) {
      String identity = element.typeTag + "|" + element.serialized;
      deduplicated.putIfAbsent(identity, element);
    }
    return new ArrayList<>(deduplicated.values());
  }

  /// 对数组元素进行排序。
  ///
  /// @param elements 待排序的元素列表
  /// @return 排序后的元素列表
  private static List<CanonicalElement> sortElements(List<CanonicalElement> elements) {
    List<CanonicalElement> sorted = new ArrayList<>(elements);
    sorted.sort(
        Comparator.comparing((CanonicalElement it) -> it.typeTag)
            .thenComparing(it -> it.serialized));
    return sorted;
  }

  /// 从规范化元素列表构建数组节点。
  ///
  /// @param elements 规范化元素列表
  /// @return 数组节点,如果为空则返回 NullNode
  private static JsonNode buildCanonicalArrayNode(List<CanonicalElement> elements) {
    ArrayNode canonical = NODE_FACTORY.arrayNode();
    for (CanonicalElement element : elements) {
      canonical.add(element.value);
    }
    return canonical.isEmpty() ? NullNode.getInstance() : canonical;
  }

  /// 通过修剪和折叠空白来规范化文本。
  ///
  /// @param text 待规范化的文本
  /// @return 规范化后的文本节点,如果为空则返回 NullNode
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

  /// 通过去除尾随零来规范化数字。
  ///
  /// @param node 待规范化的数字节点
  /// @return 规范化后的数字节点
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

  /// 检查 JSON 节点是否被视为空。
  ///
  /// @param node 待检查的节点
  /// @return 如果节点为 null、缺失或空,则返回 true
  private static boolean isEmpty(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return true;
    }
    if (node.isTextual()) {
      return node.stringValue().isEmpty();
    }
    if (node.isArray()) {
      return node.isEmpty();
    }
    if (node.isObject()) {
      return node.isEmpty();
    }
    return false;
  }

  /// 返回用于排序 JSON 节点的类型标签。
  ///
  /// @param node 待标记的节点
  /// @return 数字类型标签的字符串表示
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

  /// 将 JSON 节点序列化为字符串。
  ///
  /// @param node 待序列化的节点
  /// @return JSON 字符串
  /// @throws IllegalStateException 如果序列化失败
  private static String writeJson(JsonNode node) {
    try {
      return CANONICAL_WRITER.writeValueAsString(node);
    } catch (JacksonException ex) {
      throw new IllegalStateException("Failed to write canonical JSON", ex);
    }
  }

  /// 规范化元素的内部表示。
  ///
  /// @param value JSON 节点值
  /// @param typeTag 类型标签
  /// @param serialized 序列化字符串
  private record CanonicalElement(JsonNode value, String typeTag, String serialized) {
    /// 规范构造器,强制执行 CanonicalElement 的验证规则。
    ///
    /// 验证规则:
    ///
    /// - value 不能为 null
    /// - typeTag 不能为 null
    /// - serialized 不能为 null
    ///
    /// @throws NullPointerException 如果验证失败
    private CanonicalElement {
      Objects.requireNonNull(value, "value must not be null");
      Objects.requireNonNull(typeTag, "typeTag must not be null");
      Objects.requireNonNull(serialized, "serialized must not be null");
    }
  }
}
