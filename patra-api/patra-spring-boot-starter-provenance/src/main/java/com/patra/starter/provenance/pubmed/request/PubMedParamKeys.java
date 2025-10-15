package com.patra.starter.provenance.pubmed.request;

/** Centralized PubMed E-utilities parameter keys. */
public final class PubMedParamKeys {
  private PubMedParamKeys() {}

  // Pagination
  public static final String RETSTART = "retstart";
  public static final String RETMAX = "retmax";

  // Format and type
  public static final String RETMODE = "retmode"; // json/xml
  public static final String RETTYPE = "rettype"; // uilist/count

  // Sorting and filtering
  public static final String SORT = "sort";
  public static final String DATETYPE = "datetype"; // pdat/edat/mdat
  public static final String MINDATE = "mindate";
  public static final String MAXDATE = "maxdate";
  public static final String FIELD = "field";
  public static final String RELDATE = "reldate"; // days

  // History/session
  public static final String USEHISTORY = "usehistory"; // y/n
  public static final String WEBENV = "WebEnv"; // note: PubMed uses camel-case key
  public static final String QUERY_KEY = "query_key";

  // Identification
  public static final String API_KEY = "api_key";
  public static final String TOOL = "tool";
  public static final String EMAIL = "email";
}
