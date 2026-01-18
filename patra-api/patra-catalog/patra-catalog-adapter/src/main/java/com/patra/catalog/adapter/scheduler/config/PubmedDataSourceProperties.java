package com.patra.catalog.adapter.scheduler.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// PubMed 数据源配置属性。
///
/// 配置 PubMed Baseline 和 Update 文件的下载 URL。
///
/// **配置示例**：
///
/// ```yaml
/// patra:
///   catalog:
///     pubmed:
///       baseline-url: https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/
///       update-url: https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/
/// ```
///
/// **数据源说明**：
///
/// - 2025 Baseline 共 1274 个 gzip 压缩 XML 文件
/// - 每文件约 30,000 条记录，总计约 3,700 万条
/// - 文件名格式：`pubmed25n{0001-1274}.xml.gz`
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
  /// 实际下载时会追加文件名（如 `pubmed25n0001.xml.gz`）。
  @NotBlank(message = "patra.catalog.pubmed.baseline-url 不能为空")
  private String baselineUrl = "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/";

  /// PubMed Update 基础 URL。
  ///
  /// 指向 NCBI FTP 服务器的 updatefiles 目录。
  /// 用于增量更新（当前版本暂不支持）。
  private String updateUrl = "https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/";
}
