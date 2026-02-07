package com.patra.catalog.infra.adapter.batch.mesh;

import com.patra.starter.batch.core.JobParams;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// MeSH 导入 Job 参数。
///
/// 用于 MeSH Descriptor 批量导入任务的强类型参数。
///
/// **流式处理特性**：
///
/// - 使用 `downloadUrl` 存储 XML 文件下载 URL
/// - ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录，从本地文件解析
/// - 无临时文件清理逻辑
///
/// @author linqibin
/// @since 0.1.0
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeshImportJobParams implements JobParams {

  /// MeSH XML 文件下载 URL
  private String downloadUrl;

  /// MeSH 版本号（如 "2025"）
  private String meshVersion;
}
