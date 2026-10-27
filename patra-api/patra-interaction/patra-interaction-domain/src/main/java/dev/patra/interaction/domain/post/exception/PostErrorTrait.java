package dev.patra.interaction.domain.post.exception;

import dev.patra.tool.core.error.trait.ErrorTrait;

/// post 子域的原因码：随一个标准特征一起放进异常。错误引擎映射状态码时跳过它，响应的 `traits[]` 里带上它。
public enum PostErrorTrait implements ErrorTrait {
  /// 同一作者在同一期刊下已有同一投稿日期的经验（409），响应另带 `existingPostId`。
  DUPLICATE_SUBMISSION
}
