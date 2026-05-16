package dev.linqibin.patra.ingest.domain.model.vo.expression;

/// 表达式编译请求 Value Object。
///
/// 包含表达式编译所需的最小信息集:
///
/// - provenanceCode - 数据源标识(如 PUBMED、EPMC)
///   - endpointName - 端点名称(如 SEARCH、DETAIL),可选,可为 null
///   - rawExpression - 待编译的 JSON 表达式快照
///
/// **业务场景:**
///
/// - 将注册中心的表达式配置快照编译为可执行的表达式实例
///   - 支持多数据源、多端点的表达式编译
///   - endpointName 为 null 时表示全局配置
///
/// @param provenanceCode 数据源代码(必需)
/// @param endpointName 端点名称(可选,可为 null)
/// @param rawExpression 原始表达式 JSON 字符串(必需)
/// @author linqibin
/// @since 0.1.0
public record ExprCompilationRequest(
    String provenanceCode, String endpointName, String rawExpression) {
  /// 便捷构造器: 不指定 endpointName(默认为 null)。
  ///
  /// @param provenanceCode 数据源代码
  /// @param rawExpression 原始表达式 JSON 字符串
  public ExprCompilationRequest(String provenanceCode, String rawExpression) {
    this(provenanceCode, null, rawExpression);
  }
}
