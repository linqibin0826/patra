package com.patra.catalog.adapter.scheduler.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// NLM LSIOU 数据源配置属性。
///
/// 配置 NLM LSIOU (List of Serials Indexed for Online Users) XML 数据文件的下载 URL。
/// 版本号会从文件名自动推断（如 `lsi2024.xml` → `2024`）。
///
/// **配置示例**：
///
/// ```yaml
/// patra:
///   catalog:
///     lsiou:
///       url: ftp://ftp.nlm.nih.gov/online/journals/lsi2024.xml
/// ```
///
/// **数据源说明**：
///
/// LSIOU 是 NLM 官方提供的 MEDLINE 索引期刊列表，包含约 15,000+ 条期刊记录。
/// 相比 SerfileBase（~35,000 条 NLM Catalog 记录），LSIOU 专注于被 MEDLINE 索引的期刊，
/// 包含 MedlineTA（期刊缩写）、索引信息等，更适合用于 PubMed Venue 富化场景。
///
/// - 数据格式：NLM Serials DTD (`SerialsSet` / `Serial`)
/// - 更新频率：每年更新，文件名包含年份信息
/// - 传输协议：FTP（需要 commons-net 依赖）
/// - 若主目录文件不存在，将自动回退到 `/online/journals/archive`
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "patra.catalog.lsiou")
public class LsiouDataSourceProperties {

  /// NLM LSIOU XML 文件 URL。
  ///
  /// 支持的文件名格式：`lsi{year}.xml`（如 `lsi2024.xml`）。
  /// 默认使用 2024 年版本。
  @NotBlank(message = "patra.catalog.lsiou.url 不能为空")
  private String url = "ftp://ftp.nlm.nih.gov/online/journals/lsi2024.xml";
}
