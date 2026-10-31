package dev.patra.interaction.api.post.dto;

import java.util.List;

/// 批量取经验结果的结果。
///
/// @param outlines 结果，按去重后的请求顺序；查不到的 ID 不在其中
public record ExperienceOutlinesResp(List<ExperienceOutlineResp> outlines) {

  /// 防御性拷贝，`null` 视为空。
  public ExperienceOutlinesResp {
    outlines = outlines != null ? List.copyOf(outlines) : List.of();
  }

  /// 创建结果。
  ///
  /// @param outlines 结果
  /// @return 结果
  public static ExperienceOutlinesResp of(List<ExperienceOutlineResp> outlines) {
    return new ExperienceOutlinesResp(outlines);
  }
}
