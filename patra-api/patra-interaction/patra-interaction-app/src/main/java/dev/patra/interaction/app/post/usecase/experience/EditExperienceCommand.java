package dev.patra.interaction.app.post.usecase.experience;

import dev.patra.interaction.app.post.usecase.query.ExperienceDetailView;
import dev.patra.interaction.domain.post.model.vo.ExperienceInput;
import dev.patra.tool.cqrs.Command;

/// 编辑一条投稿经验（整体替换字段与正文；不能改匿名、不能换期刊）。
///
/// @param postId 经验 ID
/// @param input 新内容
public record EditExperienceCommand(long postId, ExperienceInput input)
    implements Command<ExperienceDetailView> {

  /// 创建命令。
  ///
  /// @param postId 经验 ID
  /// @param input 新内容
  /// @return 命令
  public static EditExperienceCommand of(long postId, ExperienceInput input) {
    return new EditExperienceCommand(postId, input);
  }
}
