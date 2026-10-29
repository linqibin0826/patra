package dev.patra.catalog.domain.portal.model.vo;

import dev.patra.catalog.domain.kernel.model.vo.Pagination;
import java.util.Optional;

/// 文章号（e-location）：只有电子版、没有页码的文章用它定位，如 PLoS 的 `e0123456`、Sci Rep 的 `12345`。
///
/// 来源是 PubMed `ELocationID` 的 pii。pii 也可能是出版商条目号（Elsevier 形如
/// `S0140-6736(21)01624-4`），那种不是文章号；有页码时以页码为准。
///
/// @author linqibin
/// @since 0.1.0
public final class ArticleNumber {

  private ArticleNumber() {}

  /// 由 pii 与页码判定文章号。
  ///
  /// @param pii `ELocationID` 的 pii，可空
  /// @param pagination 页码
  /// @return 文章号；有页码、pii 为空或形如 Elsevier PII（含括号）时为空
  public static Optional<String> of(String pii, Pagination pagination) {
    if (pii == null || pii.isBlank() || !pagination.isEmpty() || pii.contains("(")) {
      return Optional.empty();
    }
    return Optional.of(pii.trim());
  }
}
