package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.model.enums.UserStatus;

/// 用户名批量解析里一个元素的结果（PAP-118）。
///
/// @param username 请求里的原文，未规范化：调用方按它把结果配回正文
/// @param userId 解析到的用户 ID
/// @param status 该用户此刻的状态，四种都可能
public record ResolvedUser(String username, long userId, UserStatus status) {

  /// 工厂方法。
  ///
  /// @param username 请求里的原文
  /// @param userId 用户 ID
  /// @param status 状态
  /// @return 结果
  public static ResolvedUser of(String username, long userId, UserStatus status) {
    return new ResolvedUser(username, userId, status);
  }
}
