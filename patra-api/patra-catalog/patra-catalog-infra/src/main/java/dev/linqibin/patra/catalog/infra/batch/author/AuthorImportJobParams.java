package dev.linqibin.patra.catalog.infra.batch.author;

import com.patra.starter.batch.core.JobParams;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// PubMed Computed Authors 导入 Job 参数。
///
/// 用于 PubMed Computed Authors 批量导入任务的强类型参数。
///
/// **临时文件下载特性**：
///
/// - 使用 `downloadUrl` 存储 JSON Lines 文件下载 URL
/// - ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录，从本地文件解析
/// - ItemReader 在 close() 时自动清理临时文件
///
/// @author linqibin
/// @since 0.1.0
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorImportJobParams implements JobParams {

  /// PubMed Computed Authors JSON Lines 文件下载 URL
  private String downloadUrl;
}
