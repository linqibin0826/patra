package com.patra.common.provenance.api.params;

/**
 * DOAJ 搜索端点参数键常量
 *
 * <p>定义 DOAJ (Directory of Open Access Journals) 数据源的所有查询参数名称，供请求组装器和参数映射使用。
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class DoajParamKeys {
  private DoajParamKeys() {
    throw new AssertionError("工具类不应被实例化");
  }

  /** 查询字符串 */
  public static final String QUERY = "query";

  /** 页码（从 1 开始） */
  public static final String PAGE = "page";

  /** 每页大小 */
  public static final String PAGE_SIZE = "pageSize";

  /** 排序方式 */
  public static final String SORT = "sort";
}
