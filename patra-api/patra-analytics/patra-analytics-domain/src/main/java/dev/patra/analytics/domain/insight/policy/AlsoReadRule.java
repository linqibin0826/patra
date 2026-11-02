package dev.patra.analytics.domain.insight.policy;

import dev.patra.analytics.domain.insight.model.vo.AlsoReadItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 「看过这篇的人还看了」的拼装：一起读的在前，不足 6 篇时用同主题邻居补位（insight spec §6.3）。
public final class AlsoReadRule {

  /// 最多展示几篇。
  public static final int DISPLAY = 6;

  /// 向 catalog 固定要几个邻居：一起读的最多 5 篇时去重后仍够补满。
  public static final int NEIGHBOR_FETCH = 11;

  private AlsoReadRule() {}

  /// 拼出最多 6 篇。
  ///
  /// @param coRead 一起读的文献 ID，按相似度
  /// @param neighbors 同主题邻居，按 catalog 给的顺序
  /// @return 按展示顺序
  public static List<AlsoReadItem> assemble(List<Long> coRead, List<AlsoReadItem> neighbors) {
    List<AlsoReadItem> items = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    for (Long id : coRead) {
      if (items.size() == DISPLAY) {
        return List.copyOf(items);
      }
      if (seen.add(id)) {
        items.add(AlsoReadItem.coRead(id));
      }
    }
    for (AlsoReadItem n : neighbors) {
      if (items.size() == DISPLAY) {
        break;
      }
      if (seen.add(n.articleId())) {
        items.add(n);
      }
    }
    return List.copyOf(items);
  }
}
