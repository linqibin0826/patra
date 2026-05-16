package dev.linqibin.patra.catalog.adapter.scheduler.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// ROR 机构数据源配置属性。
///
/// 配置 ROR（Research Organization Registry）机构数据文件的下载 URL。
/// 版本号会从文件名自动推断（如 `v2.0-2025-12-16-ror-data.zip` → `v2.0`）。
///
/// **配置示例**：
///
/// ```yaml
/// patra:
///   catalog:
///     ror:
///       download-url:
// https://zenodo.org/records/17953395/files/v2.0-2025-12-16-ror-data.zip?download=1
/// ```
///
/// **数据源说明**：
///
/// - ROR 数据发布在 Zenodo，每个版本有独立的下载链接
/// - 最新版本下载地址：<https://ror.readme.io/docs/data-dump>
/// - ZIP 文件约 60MB（压缩），包含约 120,000 条机构记录
/// - 从 v2.0 开始仅提供 schema v2 格式
///
/// **文件名格式**：
///
/// `v{version}-{date}-ror-data.zip`，如 `v2.0-2025-12-16-ror-data.zip`
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "patra.catalog.ror")
public class RorDataSourceProperties {

  /// ROR Data Dump ZIP 文件下载 URL。
  ///
  /// 文件名格式必须为 `v{version}-{date}-ror-data.zip`。
  /// 版本号会从文件名自动推断。
  ///
  /// **获取最新 URL**：
  ///
  /// 1. 访问 <https://ror.readme.io/docs/data-dump>
  /// 2. 找到最新版本的 Zenodo 链接
  /// 3. 复制 ZIP 文件的下载 URL
  @NotBlank(message = "patra.catalog.ror.download-url 不能为空")
  private String downloadUrl;
}
