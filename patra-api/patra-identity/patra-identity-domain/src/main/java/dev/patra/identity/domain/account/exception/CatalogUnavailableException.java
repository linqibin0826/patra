package dev.patra.identity.domain.account.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 保存资料时 catalog 不可用（连不上、超时、没有实例、回了 404 以外的错误），返回 503。
/// 不降级成「保存了但没校验」：名称快照必须来自 catalog。
public final class CatalogUnavailableException extends DomainException {

  /// 创建异常并保留原因。
  ///
  /// @param cause 底层异常
  public CatalogUnavailableException(Throwable cause) {
    super("机构与关注领域暂时无法校验，请稍后再试", cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
