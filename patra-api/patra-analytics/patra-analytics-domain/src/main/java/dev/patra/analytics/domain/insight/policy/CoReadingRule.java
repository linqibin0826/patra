package dev.patra.analytics.domain.insight.policy;

import dev.patra.analytics.domain.insight.model.vo.CoReadingEntry;
import dev.patra.analytics.domain.insight.model.vo.CoReadingPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 看过这篇的人还看了的共现规则（release spec 2.1、insight spec §6.1）。
///
/// 配对与相似度在 ClickHouse 里按这里的常量算；本类负责去掉不合格的文献、每篇留前 20 名。
public final class CoReadingRule {

  /// 窗口：今天与之前 13 天（用户 11/02 定）。
  public static final int WINDOW_DAYS = 14;

  /// 每组（同一人同一天）最多取当天最先读的几篇。
  public static final int GROUP_LIMIT = 20;

  /// 一起读过的不同人数门槛。
  public static final int MIN_CO_READERS = 3;

  /// 向 ClickHouse 每篇取的候选数（给核对留余量）。
  public static final int CANDIDATE_LIMIT = 25;

  /// 每篇留几篇。
  public static final int KEEP = 20;

  private CoReadingRule() {}

  /// 去掉 X 或 Y 不合格的配对，每篇 X 按原顺序留前 20 名并重新编号。
  ///
  /// @param candidates 配对，每篇 X 内已按名次排好
  /// @param eligible 合格的文献 ID
  /// @return 快照的全部行
  public static List<CoReadingEntry> keep(List<CoReadingPair> candidates, Set<Long> eligible) {
    List<CoReadingEntry> kept = new ArrayList<>();
    Map<Long, Integer> counts = new HashMap<>();
    for (CoReadingPair p : candidates) {
      if (!eligible.contains(p.articleId()) || !eligible.contains(p.relatedArticleId())) {
        continue;
      }
      int position = counts.merge(p.articleId(), 1, Integer::sum);
      if (position > KEEP) {
        continue;
      }
      kept.add(
          CoReadingEntry.builder()
              .articleId(p.articleId())
              .position(position)
              .relatedArticleId(p.relatedArticleId())
              .similarity(p.similarity())
              .coReaders(p.coReaders())
              .build());
    }
    return List.copyOf(kept);
  }
}
