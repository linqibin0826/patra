package com.patra.catalog.infra.batch;

import com.patra.starter.batch.core.JobParams;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// MeSH 导入 Job 参数。
///
/// 用于 MeSH Descriptor 批量导入任务的强类型参数。
///
/// @author linqibin
/// @since 0.1.0
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeshImportJobParams implements JobParams {

  /// MeSH XML 文件路径
  private String filePath;

  /// MeSH 版本号（如 "2025"）
  private String meshVersion;
}
