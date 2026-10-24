package dev.patra.interaction.domain.collection.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 设为公开被拒（409）：账号状态异常（`ACCOUNT_RESTRICTED`）或邮箱未验证（`EMAIL_UNVERIFIED`）。
public final class PublishNotAllowedException extends DomainException {

  /// 创建异常。
  ///
  /// @param message 默认文案
  /// @param reason 原因码
  private PublishNotAllowedException(String message, CollectionErrorTrait reason) {
    super(message, StandardErrorTrait.CONFLICT, reason);
  }

  /// 账号状态异常。
  ///
  /// @return 异常
  public static PublishNotAllowedException accountRestricted() {
    return new PublishNotAllowedException(
        "账号状态异常，暂时不能公开收藏夹", CollectionErrorTrait.ACCOUNT_RESTRICTED);
  }

  /// 邮箱未验证。
  ///
  /// @return 异常
  public static PublishNotAllowedException emailUnverified() {
    return new PublishNotAllowedException("验证邮箱后才能公开收藏夹", CollectionErrorTrait.EMAIL_UNVERIFIED);
  }
}
