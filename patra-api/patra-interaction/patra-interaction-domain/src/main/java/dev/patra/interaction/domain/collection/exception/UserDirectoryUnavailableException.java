package dev.patra.interaction.domain.collection.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// identity 不可用（503）：设公开、读公开收藏夹、主页收藏夹列表、注销清理都要它。
public final class UserDirectoryUnavailableException extends DomainException {

  /// 创建异常。
  ///
  /// @param cause 下游失败
  public UserDirectoryUnavailableException(Throwable cause) {
    super("用户信息暂时无法获取，请稍后再试", cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
