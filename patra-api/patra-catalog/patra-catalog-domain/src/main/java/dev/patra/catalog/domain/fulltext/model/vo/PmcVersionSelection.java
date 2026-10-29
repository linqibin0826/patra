package dev.patra.catalog.domain.fulltext.model.vo;

import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;

/// 选版本的结果：要么选中一个版本，要么给出不可用原因，两者恰有一个非空。
///
/// @param selected 选中的版本
/// @param unavailableReason 不可用原因
/// @author linqibin
/// @since 0.11.0
public record PmcVersionSelection(
    PmcArticleMetadata selected, FullTextUnavailableReason unavailableReason) {

  /// 校验两者恰有一个非空。
  public PmcVersionSelection {
    if ((selected == null) == (unavailableReason == null)) {
      throw new IllegalArgumentException("selected 与 unavailableReason 必须恰有一个非空");
    }
  }

  /// 选中一个版本。
  ///
  /// @param selected 版本
  /// @return 结果
  public static PmcVersionSelection ofSelected(PmcArticleMetadata selected) {
    return new PmcVersionSelection(selected, null);
  }

  /// 不可用。
  ///
  /// @param reason 原因
  /// @return 结果
  public static PmcVersionSelection ofUnavailable(FullTextUnavailableReason reason) {
    return new PmcVersionSelection(null, reason);
  }
}
