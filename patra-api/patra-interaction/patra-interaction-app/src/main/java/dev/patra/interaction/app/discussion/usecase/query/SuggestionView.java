package dev.patra.interaction.app.discussion.usecase.query;

import dev.patra.interaction.domain.subject.model.read.UserCard;

/// @ 联想的一项。
///
/// @param user 名片（正常、主页对访客可见，带用户名）
/// @param participant 是本讨论的参与者（`PARTICIPANT` 分组）；否则来自 identity 的联想（`OTHER`）
public record SuggestionView(UserCard user, boolean participant) {

  /// 创建。
  ///
  /// @param user 名片
  /// @param participant 是否参与者
  /// @return 一项
  public static SuggestionView of(UserCard user, boolean participant) {
    return new SuggestionView(user, participant);
  }
}
