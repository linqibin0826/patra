package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import org.springframework.util.StringUtils;

/**
 * Structured view of the PubMed EPost API response (XML only).
 *
 * <p>EPost uploads a list of UIDs to the Entrez History server and returns a WebEnv token and
 * query_key that can be used in subsequent API calls. This avoids URL length limitations when
 * dealing with large ID lists.
 *
 * @author
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "ePostResult")
public final class EPostResponse {

  @JacksonXmlProperty(localName = "WebEnv")
  private String webEnv;

  @JacksonXmlProperty(localName = "QueryKey")
  private String queryKey;

  @JacksonXmlProperty(localName = "Count")
  private Integer count;

  public EPostResponse() {}

  /** History Server session token (valid for ~24 hours). */
  public String webEnv() {
    return webEnv;
  }

  /** Numeric key identifying the uploaded UID list within the WebEnv session. */
  public String queryKey() {
    return queryKey;
  }

  /** Number of identifiers successfully uploaded (may be {@code null}). */
  public Integer count() {
    return count;
  }

  /** Validate that the response contains usable WebEnv and QueryKey. */
  public boolean isValid() {
    return StringUtils.hasText(webEnv) && StringUtils.hasText(queryKey);
  }

  /** Safe WebEnv snippet for logging without leaking full token. */
  public String getTruncatedWebEnv() {
    if (!StringUtils.hasText(webEnv)) {
      return "null";
    }
    return webEnv.length() > 12 ? webEnv.substring(0, 12) + "..." : webEnv;
  }
}
