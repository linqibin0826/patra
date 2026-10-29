package dev.patra.catalog.infra.fulltext.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// 全文抓取的数据源与存储配置（前缀 `patra.catalog.fulltext`）。
///
/// 抓取与解析的批量、并发配置在 app 层的 `FullTextJobProperties`，两个 record 绑定同一前缀、各管各的子键。
///
/// @param bucket 存放全文文件的 MinIO 桶（私有，上传时懒建）
/// @param pmc PMC Cloud Service 配置
/// @author linqibin
/// @since 0.11.0
@ConfigurationProperties("patra.catalog.fulltext")
public record FullTextSourceProperties(
    @DefaultValue("patra-catalog-fulltext") String bucket, @DefaultValue Pmc pmc) {

  /// PMC Cloud Service 配置。
  ///
  /// @param baseUrl 公开 S3 桶的 HTTPS 地址
  public record Pmc(@DefaultValue("https://pmc-oa-opendata.s3.amazonaws.com/") String baseUrl) {}
}
