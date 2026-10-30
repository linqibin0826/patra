package dev.patra.interaction.app.post.usecase.query;

import java.util.List;

/// 期刊页经验列表的一页，带期刊下的总条数（「N 条投稿经验」，不受分页影响）。
///
/// @param items 本页
/// @param nextCursor 下一页的游标；没有下一页为 `null`
/// @param total 期刊下未删除的经验数
public record JournalExperiencePage(List<ExperienceItemView> items, String nextCursor, long total) {

  /// 防御性拷贝。
  public JournalExperiencePage {
    items = List.copyOf(items);
  }

  /// 创建。
  ///
  /// @param items 本页
  /// @param nextCursor 游标，可空
  /// @param total 总条数
  /// @return 一页
  public static JournalExperiencePage of(
      List<ExperienceItemView> items, String nextCursor, long total) {
    return new JournalExperiencePage(items, nextCursor, total);
  }
}
