package dev.patra.catalog.app.fulltext.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// 全文抓取与解析任务的批量与并发配置（前缀 `patra.catalog.fulltext`）。
///
/// 数据源与存储配置在 infra 层的 `FullTextSourceProperties`（app 不依赖 infra）。
///
/// @param fetch 抓取任务配置
/// @param parse 解析任务配置
/// @author linqibin
/// @since 0.11.0
@ConfigurationProperties("patra.catalog.fulltext")
public record FullTextJobProperties(@DefaultValue Fetch fetch, @DefaultValue Parse parse) {

  /// 抓取任务配置。
  ///
  /// @param batchSize 每批候选数
  /// @param concurrency 并发抓取的线程数
  /// @param maxAttempts 抓取失败的重试上限，超过后停在 `FETCH_FAILED` 等人工处理
  public record Fetch(
      @DefaultValue("200") int batchSize,
      @DefaultValue("4") int concurrency,
      @DefaultValue("8") int maxAttempts) {}

  /// 解析任务配置。
  ///
  /// @param batchSize 每批候选数
  public record Parse(@DefaultValue("500") int batchSize) {}
}
