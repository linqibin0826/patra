package dev.patra.analytics.domain.insight.policy;

import dev.patra.analytics.domain.insight.model.vo.HotReadingCandidate;
import dev.patra.analytics.domain.insight.model.vo.HotReadingEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 近期热读的规则（release spec 2.1、insight spec §5.1）。
///
/// 分数与门槛在 ClickHouse 里按这里的常量算；本类负责把合格的候选与最新收录拼成快照。
public final class HotReadingRule {

  /// 窗口：今天与之前 13 天。
  public static final int WINDOW_DAYS = 14;

  /// 上榜门槛的窗口：今天与之前 6 天。
  public static final int ELIGIBLE_DAYS = 7;

  /// 上榜门槛：近 7 天不同读者数。
  public static final int MIN_READERS = 3;

  /// 读全文的分量是只看摘要的几倍。
  public static final int FULLTEXT_WEIGHT = 3;

  /// 半衰期（天）。
  public static final int HALF_LIFE_DAYS = 3;

  /// 向 ClickHouse 取的候选数（给撤稿、软删留余量）。
  public static final int CANDIDATE_LIMIT = 150;

  /// 榜单长度。
  public static final int BOARD_SIZE = 100;

  /// 首页一页的篇数，上榜不够时补位到这么多。
  public static final int PAGE_SIZE = 14;

  /// 补位时向 catalog 取的最新收录篇数（去掉已上榜的仍够补满一页）。
  public static final int LATEST_FETCH = 28;

  private HotReadingRule() {}

  /// 拼出快照：合格的候选按顺序取前 100 名；不足一页时用最新收录补到一页，去掉已上榜的。
  ///
  /// @param eligibleInOrder 合格的候选，按名次
  /// @param latestIds 最新收录的文献 ID，新的在前
  /// @return 快照的全部行，`position` 从 1 连续编号
  public static List<HotReadingEntry> board(
      List<HotReadingCandidate> eligibleInOrder, List<Long> latestIds) {
    List<HotReadingEntry> board = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    for (HotReadingCandidate c : eligibleInOrder) {
      if (board.size() == BOARD_SIZE) {
        break;
      }
      seen.add(c.articleId());
      board.add(
          HotReadingEntry.builder()
              .position(board.size() + 1)
              .articleId(c.articleId())
              .ranked(true)
              .score(c.score())
              .readers7d(c.readers7d())
              .build());
    }
    for (Long id : latestIds) {
      if (board.size() >= PAGE_SIZE) {
        break;
      }
      if (seen.add(id)) {
        board.add(
            HotReadingEntry.builder()
                .position(board.size() + 1)
                .articleId(id)
                .ranked(false)
                .build());
      }
    }
    return List.copyOf(board);
  }
}
