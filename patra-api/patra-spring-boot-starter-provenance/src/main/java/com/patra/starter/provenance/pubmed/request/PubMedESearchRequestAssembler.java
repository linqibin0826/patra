package com.patra.starter.provenance.pubmed.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;

/**
 * Assemble PubMed ESearch requests from provider-named parameters.
 *
 * <p>Note: Upstream rendering has already produced PubMed-compatible keys (mindate/maxdate/retmax
 * etc.). This assembler just reads those keys and binds them to {@link ESearchRequest}.
 */
public class PubMedESearchRequestAssembler {

  /**
   * Build a count-only ESearch request (rettype=count).
   *
   * <p>Note: term is optional when date filters (mindate/maxdate/datetype) are provided.
   */
  public ESearchRequest buildCount(JsonNode params) {
    Values v = extract(params);
    return new ESearchRequest(
        "pubmed",
        v.term, // term is optional, date filters can be used instead
        null, // retstart ignored for count
        null, // retmax ignored for count
        v.retmode != null ? v.retmode : "json",
        "count",
        v.sort,
        v.datetype,
        v.mindate,
        v.maxdate,
        v.field,
        v.reldate,
        v.usehistory,
        v.webenv,
        v.queryKey,
        v.apiKey,
        v.tool,
        v.email);
  }

  /**
   * Build a list request (uilist) honoring pagination.
   *
   * <p>Note: term is optional when date filters (mindate/maxdate/datetype) are provided.
   */
  public ESearchRequest buildList(JsonNode params) {
    Values v = extract(params);
    return new ESearchRequest(
        "pubmed",
        v.term, // term is optional, date filters can be used instead
        v.retstart,
        v.retmax,
        v.retmode != null ? v.retmode : "json",
        v.rettype, // null -> default uilist
        v.sort,
        v.datetype,
        v.mindate,
        v.maxdate,
        v.field,
        v.reldate,
        v.usehistory,
        v.webenv,
        v.queryKey,
        v.apiKey,
        v.tool,
        v.email);
  }

  private Values extract(JsonNode p) {
    Values v = new Values();
    v.term = text(p, PubMedParamKeys.TERM);
    v.retstart = integer(p, PubMedParamKeys.RETSTART);
    v.retmax = integer(p, PubMedParamKeys.RETMAX);
    v.retmode = text(p, PubMedParamKeys.RETMODE);
    v.rettype = text(p, PubMedParamKeys.RETTYPE);
    v.sort = text(p, PubMedParamKeys.SORT);
    v.datetype = text(p, PubMedParamKeys.DATETYPE);
    v.mindate = text(p, PubMedParamKeys.MINDATE);
    v.maxdate = text(p, PubMedParamKeys.MAXDATE);
    v.field = text(p, PubMedParamKeys.FIELD);
    v.reldate = text(p, PubMedParamKeys.RELDATE);
    // Support both WebEnv and webenv for robustness
    v.usehistory = textOr(p, PubMedParamKeys.USEHISTORY, "usehistory");
    v.webenv = textOr(p, PubMedParamKeys.WEBENV, "webenv");
    v.queryKey = textOr(p, PubMedParamKeys.QUERY_KEY, "queryKey");
    v.apiKey = textOr(p, PubMedParamKeys.API_KEY, "apiKey");
    v.tool = text(p, PubMedParamKeys.TOOL);
    v.email = text(p, PubMedParamKeys.EMAIL);
    return v;
  }

  private static String text(JsonNode node, String key) {
    if (node == null || node.isNull() || key == null) return null;
    JsonNode v = node.get(key);
    return v != null && v.isTextual() ? v.asText() : null;
  }

  private static String textOr(JsonNode node, String primary, String alt) {
    String s = text(node, primary);
    return s != null ? s : text(node, alt);
  }

  private static Integer integer(JsonNode node, String key) {
    if (node == null || node.isNull()) return null;
    JsonNode v = node.get(key);
    if (v == null || v.isNull()) return null;
    if (v.isInt()) return v.asInt();
    if (v.isTextual()) {
      try {
        return Integer.parseInt(v.asText());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static final class Values {
    String term;
    Integer retstart;
    Integer retmax;
    String retmode;
    String rettype;
    String sort;
    String datetype;
    String mindate;
    String maxdate;
    String field;
    String reldate;
    String usehistory;
    String webenv;
    String queryKey;
    String apiKey;
    String tool;
    String email;
  }
}
