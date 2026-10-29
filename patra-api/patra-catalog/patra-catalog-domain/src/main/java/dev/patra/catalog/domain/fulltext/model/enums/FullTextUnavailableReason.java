package dev.patra.catalog.domain.fulltext.model.enums;

/// 全文不可用的原因（`cat_publication_fulltext.unavailable_reason`，存枚举名）。
///
/// @author linqibin
/// @since 0.11.0
public enum FullTextUnavailableReason {

  /// PMC 开放获取桶里没有这篇（禁发期或当天还没到），到期再查
  NOT_IN_BUCKET,

  /// 桶里有但没有开放获取版本（如 TDM 许可的作者手稿），到期再查
  NOT_OPEN_ACCESS,

  /// PMC 标记为撤稿，终态
  RETRACTED,

  /// XML 没有正文，PDF 仍可下载，终态（解析器版本提升后重新解析）
  NO_BODY;

  /// 是否要到期再查。
  ///
  /// @return `NOT_IN_BUCKET` 与 `NOT_OPEN_ACCESS` 为 `true`
  public boolean isRecheckable() {
    return this == NOT_IN_BUCKET || this == NOT_OPEN_ACCESS;
  }
}
