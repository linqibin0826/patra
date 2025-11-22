package com.patra.common.provenance.api.params;

/// Europe PMC 搜索端点参数键常量
///
/// 定义 EPMC 数据源的所有查询参数名称，供请求组装器和参数映射使用。
///
/// @author linqibin
/// @since 0.1.0
public final class EpmcParamKeys {

  /// 私有构造函数,防止实例化工具类。
  ///
  /// @throws AssertionError 总是抛出，因为工具类不应被实例化
  private EpmcParamKeys() {
    throw new AssertionError("工具类不应被实例化");
  }

  /// 查询字符串
  public static final String QUERY = "query";

  /// 返回格式（json/xml/lite/core）
  public static final String FORMAT = "format";

  /// 每页大小
  public static final String PAGE_SIZE = "pageSize";

  /// 游标标记（用于分页）
  public static final String CURSOR_MARK = "cursorMark";

  /// 排序方式
  public static final String SORT = "sort";

  /// 结果类型（lite/core/idlist）
  public static final String RESULT_TYPE = "resultType";

  /// 是否使用同义词扩展
  public static final String SYNONYM = "synonym";

  /// 从搜索发布
  public static final String FROM_SEARCH_POST = "fromSearchPost";

  /// 搜索类型
  public static final String SEARCH_TYPE = "searchType";
}
