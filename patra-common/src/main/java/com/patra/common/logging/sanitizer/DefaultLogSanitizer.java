package com.patra.common.logging.sanitizer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
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
    if (isPrimitiveOrWrapper(obj)) {
      return obj.toString();
    }

    // Skip Spring proxies and reflection objects to prevent circular references
    if (isSpringProxy(obj) || isReflectionObject(obj)) {
      return obj.getClass().getSimpleName()
          + "@"
          + Integer.toHexString(System.identityHashCode(obj));
    }

    try {
      // Configure ObjectMapper to handle circular references
      ObjectMapper mapper = new ObjectMapper();
      mapper.configure(
          com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_SELF_REFERENCES, false);

      // Convert object to JSON for sanitization
      String json = mapper.writeValueAsString(obj);
      return sanitizeJson(json);
    } catch (JsonProcessingException e) {
      // Fallback: use reflection to create safe string representation
      return sanitizeViaReflection(obj);
    }
  }

  /**
   * Checks if an object is a primitive type or wrapper.
   *
   * @param obj The object to check
   * @return true if primitive or wrapper
   */
  private boolean isPrimitiveOrWrapper(Object obj) {
    return obj instanceof String
        || obj instanceof Number
        || obj instanceof Boolean
        || obj instanceof Character;
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
   * Sanitizes an object using reflection (fallback when JSON serialization fails).
   *
   * @param obj The object to sanitize
   * @return A safe string representation
   */
  private String sanitizeViaReflection(Object obj) {
    return sanitizeViaReflection(obj, 0, new java.util.HashSet<>());
  }

  /**
   * Sanitizes an object using reflection with circular reference detection and depth limit.
   *
   * @param obj The object to sanitize
   * @param depth Current recursion depth
   * @param visited Set of already visited objects (by identity)
   * @return A safe string representation
   */
  private String sanitizeViaReflection(Object obj, int depth, java.util.Set<Object> visited) {
    // Depth limit to prevent stack overflow
    if (depth > 3) {
      return "...";
    }

    if (obj == null) {
      return "null";
    }

    // Check for circular references using identity hash
    if (visited.contains(obj)) {
      return "(circular-ref)";
    }

    // Handle primitives and wrappers
    if (isPrimitiveOrWrapper(obj)) {
      return obj.toString();
    }

    // Skip Spring proxies and reflection objects
    if (isSpringProxy(obj) || isReflectionObject(obj)) {
      return obj.getClass().getSimpleName()
          + "@"
          + Integer.toHexString(System.identityHashCode(obj));
    }

    // Mark as visited
    visited.add(obj);

    try {
      Class<?> clazz = obj.getClass();
      StringBuilder result = new StringBuilder(clazz.getSimpleName()).append("{");

      Field[] fields = clazz.getDeclaredFields();
      boolean first = true;

      for (Field field : fields) {
        // Skip static and synthetic fields
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
          continue;
        }

        if (!first) {
          result.append(", ");
        }
        first = false;

        String fieldName = field.getName();
        result.append(fieldName).append("=");

        if (isSensitiveKey(fieldName)) {
          result.append(REDACTED);
        } else {
          try {
            field.setAccessible(true);
            Object value = field.get(obj);

            if (value == null) {
              result.append("null");
            } else if (isPrimitiveOrWrapper(value)) {
              result.append(value.toString());
            } else {
              // Recurse with depth+1
              result.append(sanitizeViaReflection(value, depth + 1, visited));
            }
          } catch (IllegalAccessException e) {
            result.append("???");
          }
        }
      }

      result.append("}");
      return result.toString();
    } finally {
      // Remove from visited set for this branch
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
   * Checks if a field name is considered sensitive (case-insensitive).
   *
   * @param key The field name to check
   * @return true if the key matches a known sensitive field name
   */
  private boolean isSensitiveKey(String key) {
    return SENSITIVE_KEYS.contains(key.toLowerCase());
  }
}
