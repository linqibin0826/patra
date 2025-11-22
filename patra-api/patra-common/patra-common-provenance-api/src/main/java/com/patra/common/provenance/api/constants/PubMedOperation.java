package com.patra.common.provenance.api.constants;

/// PubMed E-utilities API 操作枚举
///
/// 封装每个 PubMed API 操作的完整信息，包括操作名称、端点路径和描述。
///
/// ### 使用示例
/// ```java
/// PubMedOperation op = PubMedOperation.ESEARCH;
/// metrics.recordApiCall(PROVENANCE, op.getOperationName(), ...);
/// uriBuilder.path(op.getEndpoint());
/// ```
///
/// @author linqibin
/// @since 0.1.0
public enum PubMedOperation {

  /// ESearch 操作 - 搜索 PubMed 数据库并返回 PMID 列表
  ESEARCH("esearch", "/esearch.fcgi", "搜索PubMed数据库，返回匹配的PMID列表"),

  /// EFetch 操作 - 获取文档的详细元数据
  EFETCH("efetch", "/efetch.fcgi", "批量获取文章详细元数据（支持XML/JSON格式）"),

  /// EPost 操作 - 上传 ID 列表到历史服务器
  EPOST("epost", "/epost.fcgi", "上传PMID列表到历史服务器，返回WebEnv和QueryKey");

  private final String operationName;
  private final String endpoint;
  private final String description;

  PubMedOperation(String operationName, String endpoint, String description) {
    this.operationName = operationName;
    this.endpoint = endpoint;
    this.description = description;
  }

  /// 获取操作名称（用于日志、指标、异常处理）
  ///
  /// @return 操作名称，如 "esearch"
  public String getOperationName() {
    return operationName;
  }

  /// 获取 API 端点路径
  ///
  /// @return 端点路径，如 "/esearch.fcgi"
  public String getEndpoint() {
    return endpoint;
  }

  /// 获取操作描述
  ///
  /// @return 操作的中文描述
  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    return operationName + " (" + description + ")";
  }
}
