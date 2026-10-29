package dev.patra.identity.api.account.dto;

import java.util.List;

/// @ 联想的结果（PAP-118）。
///
/// @param users 主页对访客可见的正常账号，不含访客本人：用户名前缀命中的在前（按用户名字节序），昵称前缀
///     命中的在后（不分大小写，按小写昵称码点序），同一个人只出一次；每项与批量用户摘要同形，
///     `accountStatus` 恒为 `ACTIVE`，`emailVerified` 与 `profileVisible` 恒为真
public record UserSuggestionsResp(List<UserSummaryResp> users) {

  /// 防御性拷贝，`null` 视为空。
  public UserSuggestionsResp {
    users = users != null ? List.copyOf(users) : List.of();
  }

  /// 创建结果。
  ///
  /// @param users 摘要
  /// @return 结果
  public static UserSuggestionsResp of(List<UserSummaryResp> users) {
    return new UserSuggestionsResp(users);
  }
}
