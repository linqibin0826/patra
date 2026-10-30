package dev.patra.interaction.domain.discussion.model.enums;

import dev.patra.interaction.domain.discussion.exception.InvalidDiscussionRequestException;

/// 根评论的排序（楼内回复永远正序）。
public enum ThreadSort {
  /// 正序：最早的在前（默认）。
  ASC,
  /// 最新：最新的在前。
  LATEST,
  /// 最有帮助：根评论自己的有帮助数降序，相同时新的在前。
  HELPFUL;

  /// 解析请求参数（`asc` / `latest` / `helpful`），缺省为 `ASC`。
  ///
  /// @param param 参数，可空
  /// @return 排序
  /// @throws InvalidDiscussionRequestException 未知取值
  public static ThreadSort fromParam(String param) {
    if (param == null || param.isBlank()) {
      return ASC;
    }
    return switch (param) {
      case "asc" -> ASC;
      case "latest" -> LATEST;
      case "helpful" -> HELPFUL;
      default -> throw new InvalidDiscussionRequestException("不支持的排序");
    };
  }
}
