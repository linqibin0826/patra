package dev.patra.interaction.domain.reaction.exception;

import dev.patra.tool.core.error.trait.ErrorTrait;

/// 有帮助的原因码：随一个标准特征一起放进异常，响应的 `traits[]` 里带上它，门户把它当原因码读（同
/// `DiscussionErrorTrait`）。
public enum ReactionErrorTrait implements ErrorTrait {
  /// 目标不存在、是占位、已删除，或评论所在的对象已失效（404）。
  HELPFUL_TARGET_NOT_FOUND,
  /// 给自己的内容点，含匿名经验的作者（403）。
  SELF_HELPFUL
}
