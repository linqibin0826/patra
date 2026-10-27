package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.app.discussion.usecase.query.SuggestionView;
import lombok.Builder;

/// @ 联想的一项。
///
/// @param userId 用户 ID
/// @param username 用户名（选中后插入 `@用户名 `）
/// @param displayName 昵称
/// @param avatarUrl 头像地址，可空
/// @param avatarPalette 默认头像色号（0–11）
/// @param group `PARTICIPANT`（本讨论的参与者）或 `OTHER`（identity 联想）
@Builder
public record SuggestionResponse(
    Long userId,
    String username,
    String displayName,
    String avatarUrl,
    int avatarPalette,
    String group) {

  /// 从视图转换。
  ///
  /// @param view 视图
  /// @return 响应
  public static SuggestionResponse from(SuggestionView view) {
    return SuggestionResponse.builder()
        .userId(view.user().userId())
        .username(view.user().username())
        .displayName(view.user().displayName())
        .avatarUrl(view.user().avatarUrl())
        .avatarPalette(view.user().avatarPalette())
        .group(view.participant() ? "PARTICIPANT" : "OTHER")
        .build();
  }
}
