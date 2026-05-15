package dev.linqibin.patra.catalog.adapter.scheduler.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// PubMed Computed Authors 数据源配置属性。
///
/// **配置示例**：
///
/// ```yaml
/// patra:
///   catalog:
///     author:
///       computed-authors-url:
// https://ftp.ncbi.nlm.nih.gov/pub/lu/ComputedAuthors/computed_authors.json
/// ```
///
/// **数据源说明**：
///
/// - NLM FTP 站点的 PubMed Computed Authors JSON Lines 文件
/// - 文件约 3.6GB，包含约 2100 万+ 作者记录
/// - JSON Lines 格式（每行一个 JSON 对象）
/// - 每周更新，无版本号概念
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "patra.catalog.author")
public class AuthorDataSourceProperties {

  /// PubMed Computed Authors JSON Lines 文件 URL。
  ///
  /// 默认值指向 NLM FTP 站点的最新数据文件。
  @NotBlank(message = "patra.catalog.author.computed-authors-url 不能为空")
  private String computedAuthorsUrl =
      "https://ftp.ncbi.nlm.nih.gov/pub/lu/ComputedAuthors/computed_authors.json";
}
