package dev.linqibin.patra.common.security;

import java.io.Serializable;
import java.util.Objects;

/// 当前用户：一次操作里「是谁在做」的最小描述。
///
/// 不放邮箱等个人信息。实现 `Serializable`，是因为它会作为 Spring Security 认证对象的主体，
/// 而认证对象本身是可序列化的。
///
/// @param userId 用户 ID（雪花 Long，正数）
/// @param sessionId 会话 ID（正数），登出时靠它定位删哪条会话
/// @param accountType 账号类型
/// @param clientType 客户端类型
public record CurrentUser(
    long userId, long sessionId, AccountType accountType, ClientType clientType)
    implements Serializable {

  /// 校验两个 ID 为正数、两个枚举非空。
  public CurrentUser {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId 必须是正数，实际值: " + userId);
    }
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId 必须是正数，实际值: " + sessionId);
    }
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    Objects.requireNonNull(clientType, "clientType 不能为 null");
  }

  /// 创建当前用户。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @param accountType 账号类型
  /// @param clientType 客户端类型
  /// @return 当前用户
  public static CurrentUser of(
      long userId, long sessionId, AccountType accountType, ClientType clientType) {
    return new CurrentUser(userId, sessionId, accountType, clientType);
  }
}
