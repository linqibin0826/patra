package dev.patra.interaction.adapter.post.rest.response;

import dev.patra.interaction.app.post.usecase.query.JournalExperiencePage;
import java.util.List;

/// 期刊页经验列表的一页。`nextCursor` 原样传回下一次请求的 `cursor`（同一种排序）；为 `null` 时没有下一页。
///
/// @param items 本页
/// @param nextCursor 下一页的游标，可空
/// @param total 期刊下未删除的经验数（「N 条投稿经验」，不受分页影响）
public record JournalExperiencePageResponse(
    List<ExperienceResponse> items, String nextCursor, long total) {

  /// 防御性拷贝。
  public JournalExperiencePageResponse {
    items = List.copyOf(items);
  }

  /// 转换。
  ///
  /// @param page 一页
  /// @return 响应
  public static JournalExperiencePageResponse from(JournalExperiencePage page) {
    return new JournalExperiencePageResponse(
        page.items().stream().map(ExperienceResponse::from).toList(),
        page.nextCursor(),
        page.total());
  }
}
