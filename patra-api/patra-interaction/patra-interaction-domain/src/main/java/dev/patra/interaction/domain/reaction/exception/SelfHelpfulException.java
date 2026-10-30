package dev.patra.interaction.domain.reaction.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 给自己的内容点有帮助（403 `SELF_HELPFUL`），含匿名经验的作者。门户对自己的内容不显示按钮，这里拦直接调接口的。
public final class SelfHelpfulException extends DomainException {

  /// 创建异常。
  public SelfHelpfulException() {
    super("不能给自己的内容点有帮助", StandardErrorTrait.FORBIDDEN, ReactionErrorTrait.SELF_HELPFUL);
  }
}
