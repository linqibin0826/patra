package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.model.vo.PendingCode;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;

/// 补填页要显示的内容。
///
/// @param registration 待补填记录
/// @param pendingCode 发出去还没用掉的验证码的公开部分；没有时为 `null`
public record PendingRegistrationView(PendingRegistration registration, PendingCode pendingCode) {

  /// 创建。
  ///
  /// @param registration 待补填记录
  /// @param pendingCode 验证码的公开部分，可以为 `null`
  /// @return 视图
  public static PendingRegistrationView of(
      PendingRegistration registration, PendingCode pendingCode) {
    return new PendingRegistrationView(registration, pendingCode);
  }
}
