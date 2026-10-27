package dev.patra.interaction.domain.discussion.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 评论不存在、已整条去掉、不在这个对象下，或者是占位而这个操作要正文（404 `COMMENT_NOT_FOUND`）。
public final class CommentNotFoundException extends DomainException {

  /// 创建异常。
  public CommentNotFoundException() {
    super(
        "这条评论已删除，或者不在这里的讨论中", StandardErrorTrait.NOT_FOUND, DiscussionErrorTrait.COMMENT_NOT_FOUND);
  }
}
