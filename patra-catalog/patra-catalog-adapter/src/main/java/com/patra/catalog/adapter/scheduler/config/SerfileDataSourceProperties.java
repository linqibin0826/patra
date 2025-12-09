package com.patra.catalog.adapter.scheduler.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// NLM Serfile 数据源配置属性。
///
/// 配置 NLM SerfileBase XML 数据文件的下载 URL。
/// 版本号会从文件名自动推断（如 `serfilebase2025.xml` → `2025`）。
///
/// **配置示例**：
///
/// ```yaml
/// patra:
///   catalog:
///     serfile:
///       serfile-url: https://ftp.ncbi.nlm.nih.gov/pubmed/Serfile/serfilebase2025.xml
/// ```
///
/// **数据源说明**：
///
/// - NLM 官方 Serfile XML 文件下载地址
/// - SerfileBase 文件包含约 35,000 条期刊记录
/// - 每年更新一次，文件名包含年份信息
///
/// **备选数据源**：
///
/// - 主要：`https://ftp.ncbi.nlm.nih.gov/pubmed/Serfile/serfilebase{year}.xml`
/// - 备选：`https://nlmpubs.nlm.nih.gov/projects/serials/Serfiles/serfilebase{year}.xml`
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "patra.catalog.serfile")
public class SerfileDataSourceProperties {

  /// NLM Serfile XML 文件 URL。
  ///
  /// 文件名格式必须为 `serfilebase{year}.xml`，如 `serfilebase2025.xml`。
  /// 默认使用 2025 年版本。
  @NotBlank(message = "patra.catalog.serfile.serfile-url 不能为空")
  private String serfileUrl = "https://ftp.ncbi.nlm.nih.gov/pubmed/Serfile/serfilebase2025.xml";
}
