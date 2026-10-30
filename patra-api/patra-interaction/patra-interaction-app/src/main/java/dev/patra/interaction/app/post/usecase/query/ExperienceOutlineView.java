package dev.patra.interaction.app.post.usecase.query;

import dev.patra.interaction.domain.post.model.read.ExperienceOutline;
import java.util.Objects;

/// 一条经验的结果，带上帖子 ID。
///
/// @param postId 帖子 ID
/// @param outline 结果
public record ExperienceOutlineView(long postId, ExperienceOutline outline) {

  /// 校验非空。
  public ExperienceOutlineView {
    Objects.requireNonNull(outline, "outline 不能为 null");
  }

  /// 构造。
  ///
  /// @param postId 帖子 ID
  /// @param outline 结果
  /// @return 视图
  public static ExperienceOutlineView of(long postId, ExperienceOutline outline) {
    return new ExperienceOutlineView(postId, outline);
  }
}
