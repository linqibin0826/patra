package dev.patra.identity.api.account.dto;

import java.util.List;

/// 按用户名批量解析的结果（PAP-118）。
///
/// @param users 解析到的那些，按请求顺序；原文完全相同的只出一行，原文不同而规范化后相同的各出一行（`userId`
///     相同）；不存在、旧名已过期、不合语法的不在其中
public record UserResolveResp(List<UserResolutionResp> users) {

  /// 防御性拷贝，`null` 视为空。
  public UserResolveResp {
    users = users != null ? List.copyOf(users) : List.of();
  }

  /// 创建结果。
  ///
  /// @param users 解析结果
  /// @return 结果
  public static UserResolveResp of(List<UserResolutionResp> users) {
    return new UserResolveResp(users);
  }
}
