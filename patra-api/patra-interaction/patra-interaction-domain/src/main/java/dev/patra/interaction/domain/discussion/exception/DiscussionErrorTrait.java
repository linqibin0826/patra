package dev.patra.interaction.domain.discussion.exception;

import dev.patra.tool.core.error.trait.ErrorTrait;

/// 讨论的原因码：随一个标准特征一起放进异常。错误引擎映射状态码时跳过它，响应的 `traits[]` 里带上它，门户把它当
/// 原因码读（同 collection 的 `CollectionErrorTrait`）。
public enum DiscussionErrorTrait implements ErrorTrait {
  /// 评论不存在、已整条去掉、不在这个对象下，或者是占位（编辑、取原文、修改历史）（404）：单楼视图据此显示
  /// 「这条评论已删除，或者不在这里的讨论中」。
  COMMENT_NOT_FOUND,
  /// 要回复的评论已被删除（占位或已整条去掉）（409）：门户把回复框改成「回复这一楼」。
  REPLY_TARGET_GONE
}
