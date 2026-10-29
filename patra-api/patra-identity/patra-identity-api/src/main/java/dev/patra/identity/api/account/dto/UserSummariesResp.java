package dev.patra.identity.api.account.dto;

import java.util.List;

/// 批量用户摘要的结果。
///
/// @param users 摘要，按去重后的请求顺序；identity 不认识的 ID 不在其中（不等于已注销）
public record UserSummariesResp(List<UserSummaryResp> users) {

  /// 防御性拷贝，`null` 视为空。
  public UserSummariesResp {
    users = users != null ? List.copyOf(users) : List.of();
  }

  /// 创建结果。
  ///
  /// @param users 摘要
  /// @return 结果
  public static UserSummariesResp of(List<UserSummaryResp> users) {
    return new UserSummariesResp(users);
  }
}
