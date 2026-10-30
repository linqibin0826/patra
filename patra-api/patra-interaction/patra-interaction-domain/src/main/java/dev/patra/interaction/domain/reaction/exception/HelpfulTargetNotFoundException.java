package dev.patra.interaction.domain.reaction.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 目标不能点（404 `HELPFUL_TARGET_NOT_FOUND`）：不存在、是占位、已删除，或评论所在的对象已失效。
public final class HelpfulTargetNotFoundException extends DomainException {

  /// 创建异常。
  public HelpfulTargetNotFoundException() {
    super(
        "这条内容不存在或已删除，不能点有帮助",
        StandardErrorTrait.NOT_FOUND,
        ReactionErrorTrait.HELPFUL_TARGET_NOT_FOUND);
  }
}
