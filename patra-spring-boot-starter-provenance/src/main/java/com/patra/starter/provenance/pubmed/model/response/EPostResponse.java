package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

/**
 * Structured view of the PubMed EPost API response.
 *
 * <p>EPost uploads a list of UIDs to the Entrez History server and returns a WebEnv token and
 * query_key that can be used in subsequent API calls. This avoids URL length limitations when
 * dealing with large ID lists.
 *
 * <p>Response structure:
 *
 * <pre>{@code
 * {
 *   "epostresult": {
 *     "webenv": "MCID_abc123...",
 *     "querykey": "1",
 *     "invalidids": []
 *   }
 * }
 * }</pre>
 *
 * @param webEnv History Server session token (valid for 24 hours)
 * @param queryKey Numeric key identifying the uploaded ID list within the WebEnv session
 * @param count Number of IDs successfully uploaded (optional, may be null)
 * @param raw Original JSON response for debugging/advanced use
 * @author linqibin
 * @since 0.1.0
 */
public record EPostResponse(String webEnv, String queryKey, Integer count, JsonNode raw) {

  /**
   * Parse the PubMed EPost response JSON into a structured representation.
   *
   * <p>Expected JSON structure: {@code {"epostresult": {"webenv": "...", "querykey": "1"}}}
   *
   * @param root root JSON node containing the epostresult object
   * @return structured EPost response
   * @throws IllegalArgumentException if the response structure is invalid
   */
  public static EPostResponse from(JsonNode root) {
    if (root == null || root.isNull()) {
      throw new IllegalArgumentException("EPost response JSON cannot be null");
    }

    JsonNode resultNode = root.path("epostresult");
    if (resultNode.isMissingNode() || resultNode.isNull()) {
      throw new IllegalArgumentException(
          "EPost response missing 'epostresult' node: " + root.toPrettyString());
    }

    // Extract WebEnv (required)
    String webEnv = extractText(resultNode, "webenv");
    if (!StringUtils.hasText(webEnv)) {
      throw new IllegalArgumentException(
          "EPost response missing valid 'webenv': " + resultNode.toPrettyString());
    }

    // Extract QueryKey (required)
    String queryKey = extractText(resultNode, "querykey");
    if (!StringUtils.hasText(queryKey)) {
      throw new IllegalArgumentException(
          "EPost response missing valid 'querykey': " + resultNode.toPrettyString());
    }

    // Extract count (optional)
    Integer count = null;
    JsonNode countNode = resultNode.path("count");
    if (!countNode.isMissingNode() && countNode.isInt()) {
      count = countNode.asInt();
    }

    return new EPostResponse(webEnv, queryKey, count, root);
  }

  /**
   * Validate that the response contains usable WebEnv and QueryKey.
   *
   * @return true if both WebEnv and QueryKey are present and non-empty
   */
  public boolean isValid() {
    return StringUtils.hasText(webEnv) && StringUtils.hasText(queryKey);
  }

  /**
   * Get a truncated WebEnv for logging (first 10 characters + ellipsis).
   *
   * @return truncated WebEnv string suitable for logging
   */
  public String getTruncatedWebEnv() {
    if (!StringUtils.hasText(webEnv)) {
      return "null";
    }
    return webEnv.length() > 10 ? webEnv.substring(0, 10) + "..." : webEnv;
  }

  /**
   * Extract text value from a JSON node, handling both string and numeric values.
   *
   * @param parent parent JSON node
   * @param fieldName field name to extract
   * @return extracted text value, or null if not found
   */
  private static String extractText(JsonNode parent, String fieldName) {
    JsonNode node = parent.path(fieldName);
    if (node.isMissingNode() || node.isNull()) {
      return null;
    }
    if (node.isTextual()) {
      return node.asText();
    }
    if (node.isNumber()) {
      return String.valueOf(node.asInt());
    }
    return null;
  }
}
