package dev.patra.interaction.adapter.post.rest.response;

import dev.patra.interaction.app.post.usecase.query.ExperienceDetailView;

/// 经验详情（`/posts/{id}`）。已删除时 `deleted: true`、`experience` 为 `null`，只有期刊。
///
/// @param postId 经验 ID
/// @param deleted 是否已删除
/// @param journal 所在期刊
/// @param experience 经验，已删除时为 `null`
public record ExperienceDetailResponse(
    Long postId, boolean deleted, JournalRefResponse journal, ExperienceResponse experience) {

  /// 转换。
  ///
  /// @param view 详情
  /// @return 响应
  public static ExperienceDetailResponse from(ExperienceDetailView view) {
    return new ExperienceDetailResponse(
        view.postId().value(),
        view.deleted(),
        JournalRefResponse.from(view.journal()),
        view.experience() == null ? null : ExperienceResponse.from(view.experience()));
  }
}
