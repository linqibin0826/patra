package dev.patra.interaction.adapter.post.rest.response;

import dev.patra.interaction.app.post.usecase.query.JournalRefView;

/// 经验所在的期刊。门户按 `key` 向 catalog 批量取期刊卡片（ISSN、OA）；`title`、`abbreviation` 是登记快照，兜底用。
///
/// @param key catalog 期刊 ID；catalog 里整行消失时为 `null`
/// @param state `ACTIVE` / `UNAVAILABLE`
/// @param title 期刊全称
/// @param abbreviation MEDLINE 缩写，可空
public record JournalRefResponse(String key, String state, String title, String abbreviation) {

  /// 转换。
  ///
  /// @param view 期刊
  /// @return 响应
  public static JournalRefResponse from(JournalRefView view) {
    return new JournalRefResponse(
        view.key(), view.state().name(), view.title(), view.abbreviation());
  }
}
