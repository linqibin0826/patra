package dev.patra.interaction.domain.post.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 请求参数不合法（422）：统计窗口、每页条数、分页游标。
public final class InvalidPostRequestException extends DomainException {

  /// 创建异常。
  ///
  /// @param message 说明
  public InvalidPostRequestException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }
}
