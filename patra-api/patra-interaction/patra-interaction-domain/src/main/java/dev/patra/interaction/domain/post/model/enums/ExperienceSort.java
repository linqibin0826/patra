package dev.patra.interaction.domain.post.model.enums;

import dev.patra.interaction.domain.post.exception.InvalidPostRequestException;

/// 期刊页经验列表的排序。
public enum ExperienceSort {
  /// 最新投稿（默认）：投稿日期倒序，同日按发布时间倒序。
  LATEST,
  /// 最有帮助：有帮助数降序，相同时发布时间新的在前。
  HELPFUL;

  /// 解析请求参数（`latest` / `helpful`），缺省为 `LATEST`。
  ///
  /// @param param 参数，可空
  /// @return 排序
  /// @throws InvalidPostRequestException 未知取值
  public static ExperienceSort fromParam(String param) {
    if (param == null || param.isBlank()) {
      return LATEST;
    }
    return switch (param) {
      case "latest" -> LATEST;
      case "helpful" -> HELPFUL;
      default -> throw new InvalidPostRequestException("排序只能是 latest 或 helpful：" + param);
    };
  }
}
