package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.emailchange.EmailChangeReverted;

/// 撤销改邮箱的响应体。
///
/// @param email 改回的旧邮箱
public record EmailChangeRevertedResponse(String email) {

  /// 从用例结果映射。
  ///
  /// @param result 用例结果
  /// @return 响应
  public static EmailChangeRevertedResponse from(EmailChangeReverted result) {
    return new EmailChangeRevertedResponse(result.email());
  }
}
