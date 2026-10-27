package dev.patra.interaction.domain.subject.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 发表资格不符（409）：账号状态异常（`ACCOUNT_RESTRICTED`）或邮箱未验证（`EMAIL_UNVERIFIED`）。
public final class PublishIneligibleException extends DomainException {

  /// 创建异常。
  ///
  /// @param message 默认文案
  /// @param reason 原因码
  private PublishIneligibleException(String message, SubjectErrorTrait reason) {
    super(message, StandardErrorTrait.CONFLICT, reason);
  }

  /// 账号状态异常：封禁、注销冷静期、已注销，或 identity 不认识这个人。
  ///
  /// @return 异常
  public static PublishIneligibleException accountRestricted() {
    return new PublishIneligibleException("账号状态异常，暂时不能发表", SubjectErrorTrait.ACCOUNT_RESTRICTED);
  }

  /// 邮箱未验证。
  ///
  /// @return 异常
  public static PublishIneligibleException emailUnverified() {
    return new PublishIneligibleException("验证邮箱后才能发表", SubjectErrorTrait.EMAIL_UNVERIFIED);
  }
}
