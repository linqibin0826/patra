package dev.patra.catalog.app.fulltext.usecase.parse;

/// 一次解析运行的统计。
///
/// @param processed 处理篇数
/// @param parsed 解析成功篇数
/// @param noBody 无正文篇数
/// @param failed 失败篇数（含解析失败与读存储失败）
/// @author linqibin
/// @since 0.11.0
public record FullTextParseRunStats(int processed, int parsed, int noBody, int failed) {

  /// 创建统计。
  ///
  /// @param processed 处理篇数
  /// @param parsed 成功篇数
  /// @param noBody 无正文篇数
  /// @param failed 失败篇数
  /// @return 统计
  public static FullTextParseRunStats of(int processed, int parsed, int noBody, int failed) {
    return new FullTextParseRunStats(processed, parsed, noBody, failed);
  }

  /// 全为 0 的统计。
  ///
  /// @return 空统计
  public static FullTextParseRunStats empty() {
    return new FullTextParseRunStats(0, 0, 0, 0);
  }

  /// 计一篇成功。
  ///
  /// @return 新统计
  FullTextParseRunStats plusParsed() {
    return new FullTextParseRunStats(processed + 1, parsed + 1, noBody, failed);
  }

  /// 计一篇无正文。
  ///
  /// @return 新统计
  FullTextParseRunStats plusNoBody() {
    return new FullTextParseRunStats(processed + 1, parsed, noBody + 1, failed);
  }

  /// 计一篇失败。
  ///
  /// @return 新统计
  FullTextParseRunStats plusFailed() {
    return new FullTextParseRunStats(processed + 1, parsed, noBody, failed + 1);
  }

  /// 一句话摘要。
  ///
  /// @return 摘要
  public String message() {
    return "全文解析完成：处理 %d 篇，成功 %d、无正文 %d、失败 %d".formatted(processed, parsed, noBody, failed);
  }
}
