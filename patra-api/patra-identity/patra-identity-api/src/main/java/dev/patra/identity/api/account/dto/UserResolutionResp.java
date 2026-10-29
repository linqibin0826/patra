package dev.patra.identity.api.account.dto;

/// 一个用户名的解析结果（PAP-118）。
///
/// @param username 请求里的原文，未规范化：调用方按它把结果配回正文里的 `@xxx`
/// @param userId 解析到的用户 ID：当前用户名的主人，或保留期内旧名的原主
/// @param accountStatus 该用户此刻的账号状态，四种都可能；只有 `ACTIVE` 该当成提及
public record UserResolutionResp(String username, Long userId, AccountStatus accountStatus) {

  /// 创建结果。
  ///
  /// @param username 请求里的原文
  /// @param userId 用户 ID
  /// @param accountStatus 账号状态
  /// @return 结果
  public static UserResolutionResp of(String username, Long userId, AccountStatus accountStatus) {
    return new UserResolutionResp(username, userId, accountStatus);
  }
}
