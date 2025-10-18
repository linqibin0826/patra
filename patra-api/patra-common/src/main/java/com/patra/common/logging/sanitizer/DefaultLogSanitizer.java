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

    try {
      // Convert object to JSON for sanitization
      String json = OBJECT_MAPPER.writeValueAsString(obj);
      return sanitizeJson(json);
    } catch (JsonProcessingException e) {
      // Fallback: use reflection to create safe string representation
      return sanitizeViaReflection(obj);
    }
  }

  /**
   * Sanitizes an object using reflection (fallback when JSON serialization fails).
   *
   * @param obj The object to sanitize
   * @return A safe string representation
   */
  private String sanitizeViaReflection(Object obj) {
    Class<?> clazz = obj.getClass();
    StringBuilder result = new StringBuilder(clazz.getSimpleName()).append("{");

    Field[] fields = clazz.getDeclaredFields();
    boolean first = true;

    for (Field field : fields) {
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
          result.append(value != null ? value.toString() : "null");
        } catch (IllegalAccessException e) {
          result.append("???");
        }
      }
    }

    result.append("}");
    return result.toString();
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
