package dev.patra.interaction.api.subject.dto;

import java.util.List;

/// 批量取对象视图的结果。
///
/// @param subjects 视图，按去重后的请求顺序；查不到的 ID 不在其中
public record SubjectViewsResp(List<SubjectViewResp> subjects) {

  /// 防御性拷贝，`null` 视为空。
  public SubjectViewsResp {
    subjects = subjects != null ? List.copyOf(subjects) : List.of();
  }

  /// 创建结果。
  ///
  /// @param subjects 视图
  /// @return 结果
  public static SubjectViewsResp of(List<SubjectViewResp> subjects) {
    return new SubjectViewsResp(subjects);
  }
}
