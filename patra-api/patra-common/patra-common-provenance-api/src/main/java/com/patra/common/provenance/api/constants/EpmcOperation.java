package com.patra.common.provenance.api.constants;

/// Europe PMC API 操作枚举
///
/// 封装每个 EPMC API 操作的完整信息，包括操作名称、端点路径和描述。
///
/// ### 使用示例
/// ```java
/// EpmcOperation op = EpmcOperation.SEARCH;
/// metrics.recordApiCall(PROVENANCE, op.getOperationName(), ...);
/// uriBuilder.path(op.getEndpoint());
/// ```
///
/// @author linqibin
/// @since 0.1.0
public enum EpmcOperation {

  /// Search 操作 - 搜索 EPMC 数据库
  SEARCH("search", "/search", "搜索Europe PMC数据库，支持复杂查询和过滤");

  private final String operationName;
  private final String endpoint;
  private final String description;

  /// 构造 EPMC 操作枚举常量。
  ///
  /// @param operationName 操作名称
  /// @param endpoint 端点路径
  /// @param description 操作描述
  EpmcOperation(String operationName, String endpoint, String description) {
    this.operationName = operationName;
    this.endpoint = endpoint;
    this.description = description;
  }

  /// 获取操作名称（用于日志、指标、异常处理）
  ///
  /// @return 操作名称，如 "search"
  public String getOperationName() {
    return operationName;
  }

  /// 获取 API 端点路径
  ///
  /// @return 端点路径，如 "/search"
  public String getEndpoint() {
    return endpoint;
  }

  /// 获取操作描述
  ///
  /// @return 操作的中文描述
  public String getDescription() {
    return description;
  }

  /// 返回操作的字符串表示形式。
  ///
  /// @return 格式为 "操作名称 (描述)" 的字符串
  @Override
  public String toString() {
    return operationName + " (" + description + ")";
  }
}
