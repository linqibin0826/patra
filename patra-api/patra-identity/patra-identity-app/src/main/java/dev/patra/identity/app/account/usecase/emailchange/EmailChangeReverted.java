package dev.patra.identity.app.account.usecase.emailchange;

import java.util.Objects;

/// 撤销改邮箱的结果。
///
/// @param email 改回的旧邮箱
public record EmailChangeReverted(String email) {

  /// 校验非空。
  public EmailChangeReverted {
    Objects.requireNonNull(email, "email 不能为 null");
  }

  /// 创建结果。
  ///
  /// @param email 改回的旧邮箱
  /// @return 结果
  public static EmailChangeReverted of(String email) {
    return new EmailChangeReverted(email);
  }
}
