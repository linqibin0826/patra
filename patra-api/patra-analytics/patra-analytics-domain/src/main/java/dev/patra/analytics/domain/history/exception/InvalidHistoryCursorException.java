package dev.patra.analytics.domain.history.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 阅读历史的翻页游标解析不了：不是本服务发出的游标，返回 422。
public final class InvalidHistoryCursorException extends DomainException {

  /// 创建异常。
  ///
  /// @param cursor 游标原文
  public InvalidHistoryCursorException(String cursor) {
    super("无效的阅读历史游标: " + cursor, StandardErrorTrait.RULE_VIOLATION);
  }
}
