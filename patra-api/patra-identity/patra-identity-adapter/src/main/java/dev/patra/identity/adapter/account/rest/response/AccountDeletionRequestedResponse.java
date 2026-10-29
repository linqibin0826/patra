package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.deletion.AccountDeletionRequested;
import java.time.Instant;

/// 发起注销的响应体：门户的结果屏要写到期日，而这时本机会话已被撤销。
///
/// @param deletionDueAt 注销到期时间
public record AccountDeletionRequestedResponse(Instant deletionDueAt) {

  /// 从用例结果映射。
  ///
  /// @param result 用例结果
  /// @return 响应
  public static AccountDeletionRequestedResponse from(AccountDeletionRequested result) {
    return new AccountDeletionRequestedResponse(result.deletionDueAt());
  }
}
