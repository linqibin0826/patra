package dev.patra.interaction.adapter.post.rest.response;

import dev.patra.interaction.app.post.usecase.query.UserExperienceView;

/// 用户主页「投稿经验」的一条：所在期刊与经验（经验不带作者）。
///
/// @param journal 所在期刊
/// @param experience 经验
public record UserExperienceResponse(JournalRefResponse journal, ExperienceResponse experience) {

  /// 转换。
  ///
  /// @param view 条目
  /// @return 响应
  public static UserExperienceResponse from(UserExperienceView view) {
    return new UserExperienceResponse(
        JournalRefResponse.from(view.journal()), ExperienceResponse.from(view.experience()));
  }
}
