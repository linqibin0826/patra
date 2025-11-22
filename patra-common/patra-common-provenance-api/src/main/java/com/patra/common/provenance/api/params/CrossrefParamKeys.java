package com.patra.common.provenance.api.params;

/// Crossref Works API 参数键常量
/// 
/// 定义 Crossref 数据源的所有查询参数名称，供请求组装器和参数映射使用。
/// 
/// @author linqibin
/// @since 0.1.0
public final class CrossrefParamKeys {
  private CrossrefParamKeys() {
    throw new AssertionError("工具类不应被实例化");
  }

  /// 查询字符串
  public static final String QUERY = "query";

  /// 过滤器
  public static final String FILTER = "filter";

  /// 返回行数
  public static final String ROWS = "rows";

  /// 偏移量
  public static final String OFFSET = "offset";

  /// 排序字段
  public static final String SORT = "sort";

  /// 排序方向
  public static final String ORDER = "order";

  /// 游标（用于分页）
  public static final String CURSOR = "cursor";

  /// 选择字段
  public static final String SELECT = "select";

  /// 联系邮箱（用于礼貌池）
  public static final String MAILTO = "mailto";
}
