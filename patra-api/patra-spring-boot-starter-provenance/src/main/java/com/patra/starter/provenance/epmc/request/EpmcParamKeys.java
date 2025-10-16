package com.patra.starter.provenance.epmc.request;

/** Provider parameter keys used by the Europe PMC search endpoint. */
public final class EpmcParamKeys {

  private EpmcParamKeys() {
    // utility class
  }

  public static final String QUERY = "query";
  public static final String FORMAT = "format";
  public static final String PAGE_SIZE = "pageSize";
  public static final String CURSOR_MARK = "cursorMark";
  public static final String SORT = "sort";
  public static final String RESULT_TYPE = "resultType";
  public static final String SYNONYM = "synonym";
  public static final String FROM_SEARCH_POST = "fromSearchPost";
  public static final String SEARCH_TYPE = "searchType";
}
