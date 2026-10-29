package dev.patra.interaction.domain.discussion.model.read;

import dev.patra.interaction.domain.subject.model.enums.AccountState;

/// identity 把一个 `@用户名` 解析到的人：当前用户名或 90 天保留期内旧名的主人。
///
/// @param userId 用户 ID
/// @param state 他此刻的账号状态：只有 `ACTIVE` 的才换成提及（编辑时承接的除外）
public record MentionTarget(long userId, AccountState state) {

  /// 创建。
  ///
  /// @param userId 用户 ID
  /// @param state 账号状态
  /// @return 解析结果
  public static MentionTarget of(long userId, AccountState state) {
    return new MentionTarget(userId, state);
  }
}
