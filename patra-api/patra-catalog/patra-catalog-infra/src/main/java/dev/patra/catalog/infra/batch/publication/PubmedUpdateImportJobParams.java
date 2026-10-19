package dev.patra.catalog.infra.batch.publication;

import dev.patra.tool.batch.core.JobParams;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// 增量同步作业 `pubmedUpdateImportJob` 的参数，全部是标识参数。
///
/// 不加时间戳：同一文件同一范围只有一个作业实例，失败后再跑从断点续传；
/// 重放已完成的文件时换 `generation`。
///
/// @author linqibin
/// @since 0.11.0
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PubmedUpdateImportJobParams implements JobParams {

  /// 文件下载地址。
  private String downloadUrl;

  /// 导入批次，如 `update-pubmed26n1335`。
  private String importBatch;

  /// 导入范围（`PubmedUpdateScope` 的名字）。
  private String scope;

  /// 重放代次，可空。
  private String generation;
}
