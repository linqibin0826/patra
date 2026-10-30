package dev.patra.notification.domain.preference.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 退订链接无效（404）：几种原因不区分（spec 5.4）。文案固定，不拼令牌，命令失败时日志会打出它。
public final class UnsubscribeLinkInvalidException extends DomainException {

  /// 创建异常。
  public UnsubscribeLinkInvalidException() {
    super("退订链接无效", StandardErrorTrait.NOT_FOUND, PreferenceErrorTrait.UNSUBSCRIBE_LINK_INVALID);
  }
}
