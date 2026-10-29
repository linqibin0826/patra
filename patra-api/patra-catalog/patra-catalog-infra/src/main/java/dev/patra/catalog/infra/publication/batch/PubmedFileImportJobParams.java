package dev.patra.catalog.infra.publication.batch;

import dev.patra.tool.batch.core.JobParams;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// PubMed 导入作业参数（文件导入与本地重算共用；全部是字符串，作为标识参数）。
///
/// 本地重算没有 `downloadUrl`，`reparse` 为 `"true"`。
///
/// @author linqibin
/// @since 0.1.0
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PubmedFileImportJobParams implements JobParams {

  /// 文件下载地址（本地重算为空）。
  private String downloadUrl;

  /// 写进 `cat_publication_metadata.import_batch` 的批次名。
  private String importBatch;

  /// 导入范围（`PubmedImportScope` 名称）。
  private String scope;

  /// 是否本地重算（`"true"` / 空）。
  private String reparse;

  /// 作业 generation，可空。
  private String generation;
}
