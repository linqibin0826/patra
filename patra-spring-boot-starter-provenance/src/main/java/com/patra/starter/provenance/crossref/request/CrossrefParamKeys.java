package com.patra.starter.provenance.crossref.request;

/**
 * Crossref Works API 参数键常量
 *
 * <p>定义Crossref数据源的所有查询参数名称，供请求组装器和参数映射使用。
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class CrossrefParamKeys {

  private CrossrefParamKeys() {
    // 工具类
  }

  public static final String QUERY = "query";
  public static final String FILTER = "filter";
  public static final String ROWS = "rows";
  public static final String OFFSET = "offset";
  public static final String SORT = "sort";
  public static final String ORDER = "order";
  public static final String CURSOR = "cursor";
  public static final String SELECT = "select";
  public static final String MAILTO = "mailto";
}
