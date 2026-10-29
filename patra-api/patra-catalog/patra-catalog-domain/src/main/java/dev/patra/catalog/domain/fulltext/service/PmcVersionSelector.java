package dev.patra.catalog.domain.fulltext.service;

import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleMetadata;
import dev.patra.catalog.domain.fulltext.model.vo.PmcVersionSelection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/// PMC 选版本规则（工程 spec 第 5.1 节）。
///
/// 候选为开放获取的版本；有多个时优先非手稿，再取版本号最大（PMC FAQ：版本号不代表新旧，
/// 这里只作同等条件下的平局规则）。选中的版本被 PMC 标记撤稿则不可用。
///
/// @author linqibin
/// @since 0.11.0
public final class PmcVersionSelector {

  private static final Comparator<PmcArticleMetadata> PREFERENCE =
      Comparator.comparing(PmcArticleMetadata::manuscript)
          .reversed()
          .thenComparingInt(PmcArticleMetadata::versionNumber);

  private PmcVersionSelector() {}

  /// 从一篇文章的全部版本里选一个。
  ///
  /// @param versions 全部版本的元数据
  /// @return 选中的版本或不可用原因
  public static PmcVersionSelection select(List<PmcArticleMetadata> versions) {
    if (versions.isEmpty()) {
      return PmcVersionSelection.ofUnavailable(FullTextUnavailableReason.NOT_IN_BUCKET);
    }
    Optional<PmcArticleMetadata> best =
        versions.stream().filter(PmcArticleMetadata::openAccess).max(PREFERENCE);
    if (best.isEmpty()) {
      return PmcVersionSelection.ofUnavailable(FullTextUnavailableReason.NOT_OPEN_ACCESS);
    }
    if (best.get().retracted()) {
      return PmcVersionSelection.ofUnavailable(FullTextUnavailableReason.RETRACTED);
    }
    return PmcVersionSelection.ofSelected(best.get());
  }
}
