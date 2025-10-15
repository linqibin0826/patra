package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;

/**
 * Strongly typed view of the PubMed ESearch response.
 *
 * @author linqibin
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ESearchResponse {

  private final Header header;
  private final Result result;

  @JsonCreator
  public ESearchResponse(
      @JsonProperty("header") Header header, @JsonProperty("esearchresult") Result result) {
    this.header = header;
    this.result = result;
  }

  /**
   * Get the response header block returned by PubMed.
   *
   * @return response header
   */
  public Header header() {
    return header;
  }

  /**
   * Get the main search result payload.
   *
   * @return structured result view
   */
  public Result result() {
    return result;
  }

  /**
   * Metadata header returned by the ESearch endpoint.
   *
   * <p>Field descriptions:
   *
   * @param type response type indicator
   * @param version ESearch API version
   * @author linqibin
   * @since 0.1.0
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Header(
      @JsonProperty("type") String type, @JsonProperty("version") String version) {}

  /**
   * Search result payload summarising identifiers, translations and warnings.
   *
   * <p>Field descriptions:
   *
   * @param count total records matching the query
   * @param retMax maximum records returned
   * @param retStart offset for the current page
   * @param idList identifiers returned by the call
   * @param translationSet translation pairs applied by PubMed
   * @param translationStack raw translation stack nodes
   * @param webEnv history server WebEnv token
   * @param queryKey query key for history server reuse
   * @param queryTranslation translated query string
   * @param errorList error list wrapper containing phrases
   * @param warnings warnings wrapper containing messages
   * @author linqibin
   * @since 0.1.0
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Result(
      @JsonProperty("count") int count,
      @JsonProperty("retmax") int retMax,
      @JsonProperty("retstart") int retStart,
      @JsonProperty("idlist") List<String> idList,
      @JsonProperty("translationset") List<Translation> translationSet,
      @JsonProperty("translationstack") List<JsonNode> translationStack,
      @JsonProperty("webenv") String webEnv,
      @JsonProperty("querykey") String queryKey,
      @JsonProperty("querytranslation") String queryTranslation,
      @JsonProperty("errorlist") ErrorList errorList,
      @JsonProperty("warnings") Warnings warnings) {

    public Result {
      idList = idList != null ? List.copyOf(idList) : Collections.emptyList();
      translationSet =
          translationSet != null ? List.copyOf(translationSet) : Collections.emptyList();
      translationStack =
          translationStack != null ? List.copyOf(translationStack) : Collections.emptyList();
    }

    /**
     * Query translation pair applied by PubMed.
     *
     * <p>Field descriptions:
     *
     * @param from original token
     * @param to translated token applied to the query
     * @author linqibin
     * @since 0.1.0
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Translation(@JsonProperty("from") String from, @JsonProperty("to") String to) {}

    /**
     * Error list wrapper from PubMed response.
     *
     * @param phrase list of error phrases
     * @author linqibin
     * @since 0.1.0
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorList(@JsonProperty("phrase") List<String> phrase) {}

    /**
     * Warnings wrapper from PubMed response.
     *
     * @param outputMessage output messages
     * @param warning warning messages
     * @author linqibin
     * @since 0.1.0
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Warnings(
        @JsonProperty("outputmessage") List<String> outputMessage,
        @JsonProperty("warning") List<String> warning) {}
  }
}
