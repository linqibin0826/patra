package dev.patra.interaction.domain.discussion.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 请求参数不合法（422）：未知的对象类型、排序，分页越界，游标不合法，回复的评论 ID 不合法或不在这个讨论里。
public final class InvalidDiscussionRequestException extends DomainException {

  /// 创建异常。
  ///
  /// @param message 说明
  public InvalidDiscussionRequestException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }
}
