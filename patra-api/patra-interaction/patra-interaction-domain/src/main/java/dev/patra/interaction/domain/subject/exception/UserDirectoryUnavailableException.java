package dev.patra.interaction.domain.subject.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// identity 不可用（503）：用户名片端口取不到摘要。调用方默认直接传播；本人看自己的内容等可以降级的场景捕获它。
public final class UserDirectoryUnavailableException extends DomainException {

  /// 创建异常。
  ///
  /// @param cause 下游失败
  public UserDirectoryUnavailableException(Throwable cause) {
    super("用户信息暂时无法获取，请稍后再试", cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
