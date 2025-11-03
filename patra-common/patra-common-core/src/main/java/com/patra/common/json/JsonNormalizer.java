package com.patra.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * JSON 规范化工具,将任意输入(POJO、{@link JsonNode}、字符串等)转换为确定性结构和规范 JSON 文本。
 *
 * <p>主要特性:
 *
 * <ul>
 *   <li><b>键排序</b>:使用可选的 ASCII/Unicode 比较器进行稳定的对象键排序。
 *   <li><b>数组处理</b>:按类型标签和序列化值对数组去重和排序,同时保留已配置序列字段的顺序。
 *   <li><b>空值策略</b>:删除空对象/数组/字符串,支持白名单。
 *   <li><b>类型强制</b>:规范化布尔值、数字和时间戳;从 {@link BigDecimal} 值中去除尾随零。
 *   <li><b>字符串清理</b>:可选的修剪、空格折叠以及字段/路径级别的小写转换。
 *   <li><b>时间规范化</b>:解析多种格式(包括秒/毫秒纪元),并以毫秒精度发出 UTC 时间戳({@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'})。
 *   <li><b>安全防护</b>:强制执行 UTF-8 字节限制、最大深度,并拒绝非有限数字。
 *   <li><b>确定性输出</b>:使用配置了 {@link
 *       com.fasterxml.jackson.core.JsonGenerator.Feature#WRITE_BIGDECIMAL_AS_PLAIN} 的 {@link
 *       ObjectWriter} 编写规范 JSON。
 * </ul>
 *
 * <h3>Spring 集成</h3>
 *
 * <p>不依赖 Spring。使用 {@link JsonMapperHolder} 访问 {@link ObjectMapper}。在 Spring 应用中,启动器注册容器映射器;在
 * Spring 之外,创建默认映射器。在业务代码中优先使用依赖注入;仅在不可用时使用静态工厂。
 *
 * <h3>使用场景</h3>
 *
 * <ul>
 *   <li>用于签名、去重、缓存键的规范 JSON
 *   <li>规范化来自多个源的异构有效负载
 *   <li>持久化规范形式及其哈希值
 * </ul>
 *
 * <h3>线程安全</h3>
 *
 * <p>不可变。{@link JsonMapperHolder} 确保安全的 {@link ObjectMapper} 发布。
 *
 * <h3>示例</h3>
 *
 * <pre>{@code
 * // 使用全局 ObjectMapper 和默认配置快速规范化
 * JsonNormalizerResult r = JsonNormalizer.normalizeDefault(input);
 * String canonical = r.getCanonicalJson();
 * byte[] material = r.getHashMaterial();
 *
 * // 自定义配置
 * JsonNormalizer normalizer = JsonNormalizer.withConfig(
 *     JsonNormalizerConfig.builder()
 *         .coerceNumber(true)
 *         .coerceTime(true)
 *         .removeEmpty(true)
 *         .build()
 * );
 * JsonNormalizerResult r2 = normalizer.normalize(input);
 * }</pre>
 */
public final class JsonNormalizer {

  private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");

  private final ObjectMapper objectMapper;
  private final JsonNormalizerConfig config;
  private final ObjectWriter canonicalWriter;

  private JsonNormalizer(ObjectMapper objectMapper, JsonNormalizerConfig config) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.config = Objects.requireNonNull(config, "config");
    this.canonicalWriter =
        objectMapper
            .writer()
            .without(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
            .withFeatures(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
  }

  /**
   * 使用由 {@link JsonMapperHolder} 提供的全局 {@link ObjectMapper}(从 Spring 桥接,如果可用)和默认的 {@link
   * JsonNormalizerConfig} 规范化给定输入。
   */
  public static JsonNormalizerResult normalizeDefault(Object input) {
    return usingDefault().normalize(input);
  }

  /** 创建由全局 {@link ObjectMapper} 和默认 {@link JsonNormalizerConfig} 支持的可重用规范化器。当多个输入共享相同设置时非常理想。 */
  public static JsonNormalizer usingDefault() {
    return new JsonNormalizer(
        JsonMapperHolder.getObjectMapper(), JsonNormalizerConfig.builder().build());
  }

  /**
   * 创建由全局 {@link ObjectMapper} 和提供的 {@link JsonNormalizerConfig} 支持的规范化器。使用 {@link
   * #withMapper(ObjectMapper, JsonNormalizerConfig)} 提供自定义映射器。
   */
  public static JsonNormalizer withConfig(JsonNormalizerConfig config) {
    return new JsonNormalizer(JsonMapperHolder.getObjectMapper(), config);
  }

  /**
   * 使用提供的 {@link ObjectMapper} 和配置构建规范化器。
   *
   * <p>优先将映射器注入到服务层并在那里组合此工具,而不是将其视为服务定位器。
   */
  public static JsonNormalizer withMapper(ObjectMapper objectMapper, JsonNormalizerConfig config) {
    return new JsonNormalizer(objectMapper, config);
  }

  /**
   * 通过将输入转换为 {@link JsonNode}、根据配置的策略递归清理它、渲染规范 JSON 并返回结构化的 {@link JsonNormalizerResult} 来执行规范化。
   *
   * <p>对于过度深度、非法数字或超大字符串等违规情况,抛出 {@link JsonNormalizationException}。
   */
  public JsonNormalizerResult normalize(Object input) {
    JsonNode root = toJsonNode(input);
    NormalizationPath path = new NormalizationPath();
    NormalizedValue normalized = normalizeNode(root, path, 1);
    Object canonical = normalized.present() ? normalized.value() : null;
    String canonicalJson = writeCanonicalJson(canonical);
    byte[] hashMaterial = canonicalJson.getBytes(StandardCharsets.UTF_8);
    return new JsonNormalizerResult(canonical, canonicalJson, hashMaterial);
  }

  private JsonNode toJsonNode(Object input) {
    if (input == null) {
      return NullNode.getInstance();
    }
    try {
      if (input instanceof JsonNode jsonNode) {
        return jsonNode;
      }
      if (input instanceof CharSequence seq) {
        String text = seq.toString();
        return text.isEmpty() ? NullNode.getInstance() : objectMapper.readTree(text);
      }
      return objectMapper.valueToTree(input);
    } catch (JsonProcessingException ex) {
      throw new JsonNormalizationException("Unable to parse JSON input", ex);
    }
  }

  private NormalizedValue normalizeNode(JsonNode node, NormalizationPath path, int depth) {
    if (depth > config.maxDepth) {
      throw new JsonNormalizationException("Max depth exceeded at path " + path.asString(true));
    }
    if (node == null || node.isMissingNode()) {
      return NormalizedValue.absent();
    }
    if (node.isNull()) {
      return applyEmptyPolicy(null, path);
    }
    return switch (node.getNodeType()) {
      case OBJECT -> normalizeObject((ObjectNode) node, path, depth);
      case ARRAY -> normalizeArray((ArrayNode) node, path, depth);
      case STRING -> normalizeText(node.textValue(), path);
      case NUMBER -> normalizeNumber(node, path);
      case BOOLEAN -> NormalizedValue.of(Boolean.valueOf(node.booleanValue()));
      case BINARY -> normalizeBinary(node, path);
      case POJO -> normalizePojo((POJONode) node, path, depth);
      default ->
          throw new JsonNormalizationException("Unsupported JSON node: " + node.getNodeType());
    };
  }

  private NormalizedValue normalizePojo(POJONode node, NormalizationPath path, int depth) {
    JsonNode converted = objectMapper.valueToTree(node.getPojo());
    return normalizeNode(converted, path, depth + 1);
  }

  private NormalizedValue normalizeBinary(JsonNode node, NormalizationPath path) {
    try {
      byte[] data = node.binaryValue();
      if (data == null || data.length == 0) {
        return applyEmptyPolicy(null, path);
      }
      String encoded = Base64.getEncoder().encodeToString(data);
      ensureStringWithinLimit(encoded, path);
      return applyEmptyPolicy(encoded, path);
    } catch (IOException ex) {
      throw new JsonNormalizationException("Failed to read binary data", ex);
    }
  }

  private NormalizedValue normalizeObject(
      ObjectNode objectNode, NormalizationPath path, int depth) {
    List<String> keys = new ArrayList<>();
    objectNode.fieldNames().forEachRemaining(keys::add);
    keys.sort(config.keyComparator);

    Map<String, Object> result = new LinkedHashMap<>();
    for (String key : keys) {
      if (config.forbidKeys.contains(key)) {
        throw new JsonNormalizationException("Forbidden key encountered: " + key);
      }
      JsonNode child = objectNode.get(key);
      path.pushField(key);
      NormalizedValue normalized = normalizeNode(child, path, depth + 1);
      path.pop();
      if (normalized.present()) {
        result.put(key, normalized.value());
      }
    }
    Map<String, Object> immutable = Collections.unmodifiableMap(result);
    return applyEmptyPolicy(immutable, path);
  }

  private NormalizedValue normalizeArray(ArrayNode arrayNode, NormalizationPath path, int depth) {
    path.markArray();
    NormalizedValue outcome;
    try {
      boolean isSequence = matchesFieldOrPath(config.sequenceFieldWhitelist, path);
      List<ArrayElement> elements = new ArrayList<>();
      for (JsonNode elementNode : arrayNode) {
        NormalizedValue normalized = normalizeNode(elementNode, path, depth + 1);
        if (!normalized.present()) {
          continue;
        }
        Object value = normalized.value();
        String typeTag = typeTag(value);
        String serialized = writeCanonicalJson(value);
        elements.add(new ArrayElement(value, typeTag, serialized));
      }
      List<Object> values;
      if (isSequence) {
        values = elements.stream().map(ArrayElement::value).toList();
      } else {
        List<ArrayElement> working = new ArrayList<>();
        if (config.arrayDeduplicate) {
          Map<String, ArrayElement> dedup = new LinkedHashMap<>();
          for (ArrayElement element : elements) {
            String identity = element.typeTag + '|' + element.serialized;
            dedup.putIfAbsent(identity, element);
          }
          working.addAll(dedup.values());
        } else {
          working.addAll(elements);
        }
        working.sort(
            Comparator.comparing(ArrayElement::typeTag).thenComparing(ArrayElement::serialized));
        values = working.stream().map(ArrayElement::value).toList();
      }
      List<Object> immutable = List.copyOf(values);
      outcome = applyEmptyPolicy(immutable, path);
    } finally {
      path.unmarkArray();
    }
    return outcome;
  }

  private NormalizedValue normalizeText(String text, NormalizationPath path) {
    if (text == null) {
      return applyEmptyPolicy(null, path);
    }
    String normalized = text;
    if (config.trimStrings) {
      normalized = normalized.trim();
    }
    if (config.collapseSpaces) {
      normalized = SPACE_PATTERN.matcher(normalized).replaceAll(" ");
    }
    if (matchesFieldOrPath(config.lowercaseFields, path)) {
      normalized = normalized.toLowerCase(Locale.ROOT);
    }
    ensureStringWithinLimit(normalized, path);
    if (config.coerceBoolean != JsonNormalizerConfig.CoerceBoolean.NONE) {
      Optional<Boolean> bool = coerceBoolean(normalized, config.coerceBoolean);
      if (bool.isPresent()) {
        return NormalizedValue.of(bool.get());
      }
    }
    if (config.coerceTime) {
      Optional<String> temporal = TemporalCoercion.coerceString(normalized, config.defaultZoneId);
      if (temporal.isPresent()) {
        return applyEmptyPolicy(temporal.get(), path);
      }
    }
    if (config.coerceNumber) {
      Optional<BigDecimal> numeric = coerceNumeric(normalized);
      if (numeric.isPresent()) {
        return NormalizedValue.of(sanitizeBigDecimal(numeric.get()));
      }
    }
    return applyEmptyPolicy(normalized, path);
  }

  private NormalizedValue normalizeNumber(JsonNode node, NormalizationPath path) {
    if (node.isFloatingPointNumber()) {
      double value = node.doubleValue();
      if (!Double.isFinite(value)) {
        throw new JsonNormalizationException(
            "Non-finite number encountered at " + path.asString(true));
      }
    }
    BigDecimal decimal = node.decimalValue();
    if (config.coerceBoolean == JsonNormalizerConfig.CoerceBoolean.LOOSE && isZeroOrOne(decimal)) {
      return NormalizedValue.of(decimal.compareTo(BigDecimal.ONE) == 0);
    }
    if (config.coerceTime) {
      Optional<String> temporal = TemporalCoercion.coerceBigDecimal(decimal);
      if (temporal.isPresent()) {
        return NormalizedValue.of(temporal.get());
      }
    }
    BigDecimal sanitized = config.coerceNumber ? sanitizeBigDecimal(decimal) : decimal;
    return NormalizedValue.of(sanitized);
  }

  private Optional<Boolean> coerceBoolean(String text, JsonNormalizerConfig.CoerceBoolean mode) {
    String lower = text.toLowerCase(Locale.ROOT);
    if (mode == JsonNormalizerConfig.CoerceBoolean.STRICT) {
      return switch (lower) {
        case "true" -> Optional.of(Boolean.TRUE);
        case "false" -> Optional.of(Boolean.FALSE);
        default -> Optional.empty();
      };
    }
    return switch (lower) {
      case "true", "1", "yes" -> Optional.of(Boolean.TRUE);
      case "false", "0", "no" -> Optional.of(Boolean.FALSE);
      default -> Optional.empty();
    };
  }

  private Optional<BigDecimal> coerceNumeric(String value) {
    if (value.isEmpty()) {
      return Optional.empty();
    }
    if (value.equalsIgnoreCase("nan")
        || value.equalsIgnoreCase("infinity")
        || value.equalsIgnoreCase("-infinity")) {
      throw new JsonNormalizationException("Illegal numeric literal: " + value);
    }
    try {
      return Optional.of(new BigDecimal(value, MathContext.UNLIMITED));
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }

  private boolean isZeroOrOne(BigDecimal decimal) {
    return decimal.compareTo(BigDecimal.ZERO) == 0 || decimal.compareTo(BigDecimal.ONE) == 0;
  }

  private BigDecimal sanitizeBigDecimal(BigDecimal decimal) {
    BigDecimal stripped = decimal.stripTrailingZeros();
    if (stripped.scale() < 0) {
      stripped = stripped.setScale(0);
    }
    return stripped;
  }

  private void ensureStringWithinLimit(String value, NormalizationPath path) {
    if (config.maxStringBytes <= 0) {
      return;
    }
    int length = value.getBytes(StandardCharsets.UTF_8).length;
    if (length > config.maxStringBytes) {
      throw new JsonNormalizationException(
          "String length exceeds limit at "
              + path.asString(true)
              + ": "
              + length
              + " > "
              + config.maxStringBytes);
    }
  }

  private NormalizedValue applyEmptyPolicy(Object value, NormalizationPath path) {
    boolean empty = isEmptyValue(value);
    if (empty && config.removeEmpty && !matchesFieldOrPath(config.keepEmptyWhitelist, path)) {
      return NormalizedValue.absent();
    }
    return NormalizedValue.of(value);
  }

  private boolean isEmptyValue(Object value) {
    if (value == null) {
      return true;
    }
    if (value instanceof CharSequence sequence) {
      return sequence.length() == 0;
    }
    if (value instanceof Collection<?> collection) {
      return collection.isEmpty();
    }
    if (value instanceof Map<?, ?> map) {
      return map.isEmpty();
    }
    return false;
  }

  private boolean matchesFieldOrPath(Set<String> configured, NormalizationPath path) {
    if (configured.isEmpty()) {
      return false;
    }
    String noRoot = path.asString(false);
    if (!noRoot.isEmpty() && configured.contains(noRoot)) {
      return true;
    }
    String withRoot = path.asString(true);
    if (configured.contains(withRoot)) {
      return true;
    }
    String leaf = path.leaf();
    return !leaf.isEmpty() && configured.contains(leaf);
  }

  private String writeCanonicalJson(Object value) {
    try {
      return canonicalWriter.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new JsonNormalizationException("Failed to serialize canonical JSON", ex);
    }
  }

  private String typeTag(Object value) {
    if (value == null) {
      return "0";
    }
    if (value instanceof Boolean) {
      return "1";
    }
    if (value instanceof BigDecimal) {
      return "2";
    }
    if (value instanceof CharSequence) {
      return "3";
    }
    if (value instanceof Map) {
      return "4";
    }
    if (value instanceof List) {
      return "5";
    }
    return "9";
  }

  private record ArrayElement(Object value, String typeTag, String serialized) {}

  private record NormalizedValue(boolean present, Object value) {
    static NormalizedValue absent() {
      return new NormalizedValue(false, null);
    }

    static NormalizedValue of(Object value) {
      return new NormalizedValue(true, value);
    }
  }
}
