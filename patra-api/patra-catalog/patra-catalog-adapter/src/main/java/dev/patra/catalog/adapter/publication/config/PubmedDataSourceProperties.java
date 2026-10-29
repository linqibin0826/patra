package dev.patra.catalog.adapter.publication.config;

import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// PubMed 数据源配置属性。
///
/// 配置 PubMed Baseline 和 Update 文件的下载 URL、文件名前缀与导入范围。
///
/// **配置示例**：
///
/// ```yaml
/// patra:
///   catalog:
///     pubmed:
///       baseline-url: https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/
///       update-url: https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/
///       file-prefix: pubmed26n
///       scope: EXISTING_ONLY
///       update-start-seq: 1335
/// ```
///
/// **数据源说明**：
///
/// - 2026 Baseline 共 1334 个 gzip 压缩 XML 文件
/// - 每文件约 30,000 条记录，总计约 3,700 万条
/// - 文件名格式：`pubmed26n{0001-1274}.xml.gz`
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "patra.catalog.pubmed")
public class PubmedDataSourceProperties {

  /// PubMed Baseline 基础 URL。
  ///
  /// 指向 NCBI FTP 服务器的 baseline 目录。
  /// 实际下载时会追加文件名（如 `pubmed26n0001.xml.gz`）。
  @NotBlank(message = "patra.catalog.pubmed.baseline-url 不能为空")
  private String baselineUrl = "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/";

  /// PubMed Update 基础 URL。
  ///
  /// 指向 NCBI FTP 服务器的 updatefiles 目录，增量同步从这里读目录索引与下载文件。
  @NotBlank(message = "patra.catalog.pubmed.update-url 不能为空")
  private String updateUrl = "https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/";

  /// 文件名前缀，baseline 与 updatefiles 共用（如 `pubmed26n`，随年份变化）。
  @NotBlank(message = "patra.catalog.pubmed.file-prefix 不能为空")
  private String filePrefix = "pubmed26n";

  /// 导入范围的默认值（任务参数没给 `scope` 时用）：`ALL` 新文献也导入，`EXISTING_ONLY` 只更新库里已有的。
  ///
  /// 默认 `EXISTING_ONLY`：更安全，误配时不会把大量老文献的修订当成新文献灌进来。
  @NotNull(message = "patra.catalog.pubmed.scope 不能为空")
  private PubmedImportScope scope = PubmedImportScope.EXISTING_ONLY;

  /// 进度表为空时增量同步的起始文件号（2026 年 updatefiles 的第一个文件是 1335）。
  @Min(value = 1, message = "patra.catalog.pubmed.update-start-seq 必须不小于 1")
  private int updateStartSeq = 1335;
}
