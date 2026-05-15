package dev.linqibin.patra.catalog.app.usecase.organization.command;

/// ROR 机构导入结果。
///
/// 包含导入任务的执行信息。
///
/// @param executionId Spring Batch Job Execution ID
/// @param url 导入使用的 URL
/// @param rorVersion ROR 版本号
/// @author linqibin
/// @since 0.1.0
public record RorOrganizationImportResult(Long executionId, String url, String rorVersion) {

  /// 创建成功结果。
  ///
  /// @param executionId Job Execution ID
  /// @param url 导入 URL
  /// @param rorVersion ROR 版本
  /// @return 导入结果
  public static RorOrganizationImportResult success(
      Long executionId, String url, String rorVersion) {
    return new RorOrganizationImportResult(executionId, url, rorVersion);
  }
}
