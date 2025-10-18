package com.patra.common.logging.sanitizer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link LogSanitizer} with hardcoded regex patterns.
 *
 * <p>Detects and redacts common sensitive data patterns:
 *
 * <ul>
 *   <li>Passwords: password=..., pwd=..., passwd=...
 *   <li>API keys and tokens: apiKey=..., token=..., bearer=..., authorization=...
 *   <li>Email addresses: name@domain.com
 *   <li>Phone numbers: +1-234-567-8900, (123) 456-7890
 *   <li>Credit card numbers: 4111-1111-1111-1111, 4111111111111111
 *   <li>Social Security Numbers: 123-45-6789, 123456789
 * </ul>
 *
 * <p>Thread-safe and suitable for singleton use.
 *
 * @see LogSanitizer
 * @since 0.1.0
 */
public class DefaultLogSanitizer implements LogSanitizer {

  private static final String REDACTED = "***REDACTED***";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final int MAX_DEPTH = 6;

  /** Sensitive field names for JSON and object sanitization (case-insensitive). */
  private static final Set<String> SENSITIVE_KEYS =
      Set.of(
          "password",
          "passwd",
          "pwd",
          "secret",
          "apikey",
          "api_key",
          "token",
          "accesstoken",
          "access_token",
          "refreshtoken",
          "refresh_token",
          "authorization",
          "auth",
          "ssn",
          "creditcard",
          "credit_card",
          "cardnumber",
          "card_number",
          "cvv",
          "pin");

  /** Regex patterns for common sensitive data in plain text. */
  private static final List<Pattern> SENSITIVE_PATTERNS =
      Arrays.asList(
          // Password patterns: password=xxx, pwd=xxx, passwd=xxx
          Pattern.compile(
              "(password|pwd|passwd|secret)\\s*[:=]\\s*['\"]?([^'\"\\s,;\\n]+)",
              Pattern.CASE_INSENSITIVE),
          // API key patterns: apiKey=xxx, api_key=xxx, token=xxx
          Pattern.compile(
              "(apikey|api_key|token|access_token|refresh_token|bearer)\\s*[:=]\\s*['\"]?([^'\"\\s,;\\n]+)",
              Pattern.CASE_INSENSITIVE),
          // Authorization header: Authorization: Bearer xxx
          Pattern.compile(
              "authorization\\s*:\\s*(bearer|basic)\\s+([^\\s,;\\n]+)", Pattern.CASE_INSENSITIVE),
          // Email addresses: name@domain.com
          Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
          // Phone numbers: +1-234-567-8900, (123) 456-7890, 123-456-7890
          Pattern.compile("\\+?\\d{1,3}?[-.\\s]?\\(?\\d{1,4}?\\)?[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,9}"),
          // Credit card numbers: 4111-1111-1111-1111 or 4111111111111111 (13-19 digits)
          Pattern.compile("\\b\\d{4}[-.\\s]?\\d{4}[-.\\s]?\\d{4}[-.\\s]?\\d{4,7}\\b"),
          // Social Security Numbers: 123-45-6789 or 123456789
          Pattern.compile("\\b\\d{3}[-]?\\d{2}[-]?\\d{4}\\b"));

  @Override
  public String sanitize(String message) {
    if (message == null || message.isEmpty()) {
      return message;
    }

    String result = message;
    for (Pattern pattern : SENSITIVE_PATTERNS) {
      Matcher matcher = pattern.matcher(result);
      result =
          matcher.replaceAll(
              matchResult -> {
                if (matchResult.groupCount() >= 2) {
                  // For patterns with groups (e.g., password=xxx), preserve the key
                  return matchResult.group(1) + "=" + REDACTED;
                } else {
                  // For patterns without groups (e.g., email), redact entirely
                  return REDACTED;
                }
              });
    }
    return result;
  }

  @Override
  public String sanitizeJson(String json) {
    if (json == null || json.isEmpty()) {
      return json;
    }

    try {
      JsonNode rootNode = OBJECT_MAPPER.readTree(json);
      JsonNode sanitizedNode = sanitizeJsonNode(rootNode);
      return OBJECT_MAPPER.writeValueAsString(sanitizedNode);
    } catch (JsonProcessingException e) {
      // If JSON parsing fails, fall back to plain text sanitization
      return sanitize(json);
    }
  }

  /**
   * Recursively sanitizes a JSON node by redacting sensitive field values.
   *
   * @param node The JSON node to sanitize
   * @return A sanitized copy of the node
   */
  private JsonNode sanitizeJsonNode(JsonNode node) {
    if (node.isObject()) {
      ObjectNode objectNode = (ObjectNode) node;
      ObjectNode result = OBJECT_MAPPER.createObjectNode();

      objectNode
          .fields()
          .forEachRemaining(
              entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();

                if (isSensitiveKey(key)) {
                  result.put(key, REDACTED);
                } else {
                  result.set(key, sanitizeJsonNode(value));
                }
              });
      return result;
    } else if (node.isArray()) {
      ArrayNode arrayNode = (ArrayNode) node;
      ArrayNode result = OBJECT_MAPPER.createArrayNode();

      for (JsonNode element : arrayNode) {
        result.add(sanitizeJsonNode(element));
      }
      return result;
    } else {
      return node;
    }
  }

  @Override
  public String sanitizeObject(Object obj) {
    if (obj == null) {
      return null;
    }

    // Handle primitives and simple types early
    if (isSimpleValue(obj)) {
      Object sanitizedSimple = formatSimpleValue(obj);
      return sanitizedSimple == null ? null : String.valueOf(sanitizedSimple);
    }

    Object sanitizedStructure = buildSanitizedStructure(obj, 0, newIdentitySet());
    return formatSanitizedOutput(sanitizedStructure);
  }

  /**
   * Checks if an object is a Spring proxy (CGLIB or JDK dynamic proxy).
   *
   * @param obj The object to check
   * @return true if it's a Spring proxy
   */
  private boolean isSpringProxy(Object obj) {
    Class<?> clazz = obj.getClass();
    // Check for CGLIB proxy (class name contains "$$")
    if (clazz.getName().contains("$$")) {
      return true;
    }
    // Check for JDK dynamic proxy
    return java.lang.reflect.Proxy.isProxyClass(clazz);
  }

  /**
   * Checks if an object is a reflection object that should not be serialized.
   *
   * @param obj The object to check
   * @return true if it's a reflection object
   */
  private boolean isReflectionObject(Object obj) {
    return obj instanceof java.lang.reflect.Method
        || obj instanceof java.lang.reflect.Field
        || obj instanceof java.lang.reflect.Constructor
        || obj instanceof Class;
  }

  /**
   * Sanitizes an object graph into a JSON-safe representation with circular reference detection and
   * depth limiting.
   *
   * @param obj The object to sanitize
   * @param depth Current recursion depth
   * @param visited Identity-based visited set
   * @return A sanitized object suitable for JSON serialization
   */
  private Object buildSanitizedStructure(Object obj, int depth, Set<Object> visited) {
    if (obj == null) {
      return null;
    }

    if (depth > MAX_DEPTH) {
      return "...";
    }

    if (isSimpleValue(obj)) {
      return formatSimpleValue(obj);
    }

    if (isSpringProxy(obj) || isReflectionObject(obj)) {
      return obj.getClass().getSimpleName()
          + "@"
          + Integer.toHexString(System.identityHashCode(obj));
    }

    if (!visited.add(obj)) {
      return "(circular-ref)";
    }

    try {
      Class<?> clazz = obj.getClass();

      if (clazz.isArray()) {
        int length = java.lang.reflect.Array.getLength(obj);
        List<Object> sanitizedElements = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
          Object element = java.lang.reflect.Array.get(obj, i);
          sanitizedElements.add(buildSanitizedStructure(element, depth + 1, visited));
        }
        return sanitizedElements;
      }

      if (obj instanceof Map<?, ?> map) {
        Map<String, Object> sanitizedMap = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          String key = stringifyKey(entry.getKey());
          if (isSensitiveKey(key)) {
            sanitizedMap.put(key, REDACTED);
          } else {
            sanitizedMap.put(key, buildSanitizedStructure(entry.getValue(), depth + 1, visited));
          }
        }
        return sanitizedMap;
      }

      if (obj instanceof Iterable<?> iterable) {
        List<Object> sanitizedList = new ArrayList<>();
        for (Object element : iterable) {
          sanitizedList.add(buildSanitizedStructure(element, depth + 1, visited));
        }
        return sanitizedList;
      }

      return sanitizePojo(obj, depth, visited);
    } finally {
      visited.remove(obj);
    }
  }

  @Override
  public boolean containsSensitiveData(String message) {
    if (message == null || message.isEmpty()) {
      return false;
    }

    return SENSITIVE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(message).find());
  }

  /**
   * Checks if a value should be treated as a simple leaf node.
   *
   * @param obj candidate object
   * @return true if the value should be handled as leaf
   */
  private boolean isSimpleValue(Object obj) {
    return obj instanceof CharSequence
        || obj instanceof Number
        || obj instanceof Boolean
        || obj instanceof Character
        || obj instanceof Enum<?>
        || obj instanceof java.util.Date
        || obj instanceof java.time.temporal.TemporalAccessor
        || obj instanceof java.util.UUID;
  }

  /**
   * Produces a sanitized representation for leaf values.
   *
   * @param obj value to sanitize
   * @return sanitized leaf representation
   */
  private Object formatSimpleValue(Object obj) {
    if (obj instanceof CharSequence || obj instanceof Character) {
      return sanitize(obj.toString());
    }
    if (obj instanceof Enum<?> enumValue) {
      return enumValue.name();
    }
    return obj;
  }

  /**
   * Converts the sanitized structure into a string suitable for logging.
   *
   * @param sanitizedStructure structure produced by {@link #buildSanitizedStructure(Object, int,
   *     Set)}
   * @return string form safe for logging
   */
  private String formatSanitizedOutput(Object sanitizedStructure) {
    if (sanitizedStructure == null) {
      return null;
    }

    if (sanitizedStructure instanceof CharSequence
        || sanitizedStructure instanceof Character
        || sanitizedStructure instanceof Number
        || sanitizedStructure instanceof Boolean) {
      return sanitizedStructure.toString();
    }

    try {
      return OBJECT_MAPPER.writeValueAsString(sanitizedStructure);
    } catch (JsonProcessingException e) {
      return sanitizedStructure.toString();
    }
  }

  /**
   * Builds a sanitized map for a POJO by reflecting over declared fields.
   *
   * @param obj POJO instance
   * @param depth current recursion depth
   * @param visited identity-based visited set
   * @return sanitized representation
   */
  private Map<String, Object> sanitizePojo(Object obj, int depth, Set<Object> visited) {
    Map<String, Object> result = new LinkedHashMap<>();
    Class<?> clazz = obj.getClass();

    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
        continue;
      }

      String fieldName = field.getName();
      if (isSensitiveKey(fieldName)) {
        result.put(fieldName, REDACTED);
        continue;
      }

      try {
        field.setAccessible(true);
        Object value = field.get(obj);
        result.put(fieldName, buildSanitizedStructure(value, depth + 1, visited));
      } catch (IllegalAccessException e) {
        result.put(fieldName, "???");
      }
    }
    return result;
  }

  /**
   * Creates a new identity-based visited set.
   *
   * @return visited set that relies on reference equality
   */
  private Set<Object> newIdentitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  /**
   * Provides a stable string key for map entries without invoking potentially recursive toString.
   *
   * @param key map key
   * @return safe string key
   */
  private String stringifyKey(Object key) {
    if (key == null) {
      return "null";
    }
    if (key instanceof CharSequence
        || key instanceof Number
        || key instanceof Boolean
        || key instanceof Character
        || key instanceof Enum<?>) {
      return key.toString();
    }
    return key.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(key));
  }

  /**
   * Checks if a field name is considered sensitive (case-insensitive).
   *
   * @param key The field name to check
   * @return true if the key matches a known sensitive field name
   */
  private boolean isSensitiveKey(String key) {
    return SENSITIVE_KEYS.contains(key.toLowerCase());
  }
}
