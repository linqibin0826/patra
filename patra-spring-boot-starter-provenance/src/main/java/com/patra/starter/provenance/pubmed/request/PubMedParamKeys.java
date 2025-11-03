package com.patra.starter.provenance.pubmed.request;

/**
 * PubMed E-utilities 参数键常量
 *
 * <p>集中定义所有PubMed API参数名称，供请求组装器和参数映射使用。
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class PubMedParamKeys {
  private PubMedParamKeys() {}

  // 分页参数
  public static final String RETSTART = "retstart";
  public static final String RETMAX = "retmax";

  // Format and type
  public static final String RETMODE = "retmode"; // json/xml
  public static final String RETTYPE = "rettype"; // uilist/count

  // Sorting and filtering
  public static final String TERM = "term";
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
