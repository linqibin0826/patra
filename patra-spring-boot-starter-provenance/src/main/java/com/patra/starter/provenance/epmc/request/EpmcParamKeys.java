package com.patra.starter.provenance.epmc.request;

/**
 * Europe PMC 搜索端点参数键常量
 *
 * <p>定义EPMC数据源的所有查询参数名称，供请求组装器和参数映射使用。
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class EpmcParamKeys {

  private EpmcParamKeys() {
    // 工具类
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
