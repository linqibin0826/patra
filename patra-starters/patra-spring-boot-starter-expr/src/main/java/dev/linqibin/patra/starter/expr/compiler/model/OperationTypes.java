package dev.linqibin.patra.starter.expr.compiler.model;

/// 常用操作类型常量，帮助调用者避免硬编码字符串。
///
/// 操作类型用于区分同一数据源在不同业务场景下的配置切片。
///
/// **使用示例**：
///
/// ```java
/// CompileRequest request = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
///     .forOperationType(OperationTypes.UPDATE)
///     .build();
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class OperationTypes {

  /// 初始全量采集，通常用于首次爬取或重建大型时间窗口。
  public static final String HARVEST = "HARVEST";

  /// 历史回填或补救运行。
  public static final String BACKFILL = "BACKFILL";

  /// 增量更新，用于定期同步新数据或变更数据。
  ///
  /// 这是最常见的操作类型，通常使用激进的参数来捕获最新内容。
  public static final String UPDATE = "UPDATE";

  /// 全量同步，覆盖完整数据集。
  ///
  /// 通常用于初始化或定期刷新，需要对大数据量进行特殊处理。
  public static final String FULL = "FULL";

  /// 交互式搜索场景。
  ///
  /// 通常针对延迟进行优化，可能依赖不同的超时或重试策略。
  public static final String SEARCH = "SEARCH";

  /// 指标或分析聚合。
  public static final String METRICS = "METRICS";

  /// 监控或健康检查操作，使用轻量级查询。
  public static final String MONITOR = "MONITOR";

  /// 数据验证或质量保证流程。
  public static final String VALIDATE = "VALIDATE";

  /// 分析操作，支持复杂调查或报告用例。
  public static final String ANALYZE = "ANALYZE";

  /// 批量数据导出。
  public static final String EXPORT = "EXPORT";

  /// 私有构造函数，防止实例化工具类。
  private OperationTypes() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }
}
