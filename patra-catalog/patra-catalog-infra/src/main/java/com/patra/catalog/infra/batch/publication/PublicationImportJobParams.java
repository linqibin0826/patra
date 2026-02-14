package com.patra.catalog.infra.batch.publication;

import com.patra.starter.batch.core.JobParams;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// Publication Baseline 导入 Job 参数。
///
/// 用于 PubMed Baseline 文献批量导入任务的强类型参数。
///
/// **临时文件下载特性**：
///
/// - 使用 `downloadUrl` 存储 XML.gz 文件下载 URL
/// - ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录，解压并解析
/// - ItemReader 在 close() 时自动清理临时文件
///
/// **断点续传**：
///
/// 由于不添加时间戳（`addTimestamp=false`），相同 downloadUrl 的 Job 只会执行一次。
/// 如果 Job 失败，重新启动时会从上次中断的位置继续执行。
///
/// @author linqibin
/// @since 0.1.0
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicationImportJobParams implements JobParams {

  /// PubMed XML.gz 文件下载 URL。
  ///
  /// 格式：`{baseUrl}pubmed26n{fileIndex:04d}.xml.gz`
  ///
  /// 示例：`https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/pubmed26n0001.xml.gz`
  private String downloadUrl;

  /// 导入批次标识。
  ///
  /// 用于标记本次导入的文献来源批次，供 Processor 写入 `PublicationMetadata.importBatch`。
  ///
  /// 格式：`baseline-{fileName}`（不含扩展名）
  ///
  /// 示例：`baseline-pubmed26n0001`
  private String importBatch;
}
