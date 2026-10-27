package dev.patra.interaction.domain.discussion.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 要回复的评论已被删除：楼里一条已成占位或已整条去掉的回复（409 `REPLY_TARGET_GONE`）。客户端看到的楼已经过时，
/// 门户据此把回复框改成「回复这一楼」。
public final class ReplyTargetGoneException extends DomainException {

  /// 创建异常。
  public ReplyTargetGoneException() {
    super("要回复的评论已被删除", StandardErrorTrait.CONFLICT, DiscussionErrorTrait.REPLY_TARGET_GONE);
  }
}
