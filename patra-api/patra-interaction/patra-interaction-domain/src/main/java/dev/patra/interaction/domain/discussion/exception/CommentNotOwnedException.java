package dev.patra.interaction.domain.discussion.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 编辑、删除、取编辑原文别人的评论（403）。评论是公开的，不需要用 404 隐藏它的存在。
public final class CommentNotOwnedException extends DomainException {

  /// 创建异常。
  public CommentNotOwnedException() {
    super("只能修改自己的评论", StandardErrorTrait.FORBIDDEN);
  }
}
