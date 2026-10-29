package dev.patra.identity.domain.account.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 头像对象读写失败（对象存储不可用等），返回 503，资料不改。
public final class AvatarStorageException extends DomainException {

  /// 创建异常。
  ///
  /// @param message 说明
  /// @param cause 原因
  public AvatarStorageException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
