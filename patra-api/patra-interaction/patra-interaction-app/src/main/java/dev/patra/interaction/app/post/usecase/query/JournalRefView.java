package dev.patra.interaction.app.post.usecase.query;

import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.read.SubjectView;

/// 经验所在的期刊：门户按 `key`（catalog 期刊 ID）批量取期刊卡片（ISSN、OA）；全称与缩写是登记快照，兜底用。
///
/// @param key catalog 期刊 ID；catalog 里整行消失时为 `null`
/// @param state 登记状态
/// @param title 期刊全称
/// @param abbreviation MEDLINE 缩写，可空
public record JournalRefView(String key, SubjectState state, String title, String abbreviation) {

  /// 从登记视图转换；查不到登记行时只给 UNAVAILABLE。
  ///
  /// @param view 登记视图，可空
  /// @return 期刊
  public static JournalRefView of(SubjectView view) {
    return view == null
        ? new JournalRefView(null, SubjectState.UNAVAILABLE, null, null)
        : new JournalRefView(
            view.key(), view.state(), view.snapshot().title(), view.snapshot().abbreviation());
  }
}
