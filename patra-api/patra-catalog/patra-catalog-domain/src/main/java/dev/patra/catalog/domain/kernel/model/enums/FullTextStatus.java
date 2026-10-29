package dev.patra.catalog.domain.kernel.model.enums;

/// 文献全文记录的状态（`cat_publication_fulltext.status`，存枚举名）。
///
/// 流转见工程 spec 第 3.4 节：抓取得到 `FETCHED` / `FETCH_FAILED` / `UNAVAILABLE`，
/// 解析把 `FETCHED` 推进到 `PARSED` / `PARSE_FAILED` / `UNAVAILABLE(NO_BODY)`。
///
/// @author linqibin
/// @since 0.11.0
public enum FullTextStatus {

  /// 已从 PMC 抓到文件，待解析
  FETCHED,

  /// 抓取失败，按退避时间重试
  FETCH_FAILED,

  /// 暂不可用或不可用，原因见 `FullTextUnavailableReason`
  UNAVAILABLE,

  /// 已解析，可读
  PARSED,

  /// 解析失败，解析器版本提升后重试
  PARSE_FAILED
}
