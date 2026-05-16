package dev.linqibin.patra.catalog.infra.batch.organization;

import dev.linqibin.starter.batch.core.JobParams;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// ROR 机构导入 Job 参数。
///
/// 用于 ROR Organization 批量导入任务的强类型参数。
///
/// **临时文件下载特性**：
///
/// - 使用 `downloadUrl` 存储 JSON 文件下载 URL
/// - ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录，从本地文件解析
/// - ItemReader 在 close() 时自动清理临时文件
///
/// @author linqibin
/// @since 0.1.0
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RorOrganizationJobParams implements JobParams {

  /// ROR Data Dump JSON 文件下载 URL
  private String downloadUrl;

  /// ROR 数据版本号（如 "v1.63"）
  private String rorVersion;
}
