package dev.patra.identity.api.account.dto;

import java.util.List;

/// 批量收件信息的结果。
///
/// @param recipients 收件信息，按去重后的请求顺序；identity 不认识的 ID 不在其中（不等于已注销）
public record UserRecipientsResp(List<UserRecipientResp> recipients) {

  /// 防御性拷贝，`null` 视为空。
  public UserRecipientsResp {
    recipients = recipients != null ? List.copyOf(recipients) : List.of();
  }

  /// 创建结果。
  ///
  /// @param recipients 收件信息
  /// @return 结果
  public static UserRecipientsResp of(List<UserRecipientResp> recipients) {
    return new UserRecipientsResp(recipients);
  }
}
