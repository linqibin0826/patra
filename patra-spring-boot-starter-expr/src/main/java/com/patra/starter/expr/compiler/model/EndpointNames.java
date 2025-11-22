package com.patra.starter.expr.compiler.model;

/// 表达式编译器常用端点名称常量。
///
/// 端点名称用于标识不同的 API 操作类型，系统根据端点名称选择对应的端点配置和渲染规则。
///
/// **使用示例**：
///
/// ```java
/// // 使用常量代替硬编码字符串
/// CompileRequest request = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
///     .forOperation(EndpointNames.SEARCH)
///     .build();
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class EndpointNames {

  /// 搜索操作：用于检索符合条件的记录列表。
  ///
  /// 这是最常用的操作类型，通常返回 ID 列表或分页结果。
  public static final String SEARCH = "SEARCH";

  /// 详情操作：用于检索特定记录的完整信息。
  ///
  /// 通常需要提供记录 ID，返回详细的元数据信息。
  public static final String DETAIL = "DETAIL";

  /// 列表操作：用于检索简化的记录列表。
  ///
  /// 类似于 SEARCH，但可能返回更少的字段或不同的格式。
  public static final String LIST = "LIST";

  /// 计数操作：用于检索符合条件的记录数量。
  ///
  /// 仅返回数量信息，不返回实际记录数据。
  public static final String COUNT = "COUNT";

  /// 获取操作：通用数据检索操作。
  ///
  /// 某些 API 使用 FETCH 而不是 DETAIL 来表示详情检索。
  public static final String FETCH = "FETCH";

  /// 查询操作：类似于搜索，但语义上更偏向复杂查询。
  public static final String QUERY = "QUERY";

  /// 导出操作：用于批量数据导出。
  public static final String EXPORT = "EXPORT";

  /// 私有构造函数，防止实例化工具类。
  private EndpointNames() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }
}
