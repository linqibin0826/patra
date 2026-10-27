package dev.patra.interaction.app.post.usecase.experience;

import dev.patra.tool.cqrs.Command;

/// 删除自己的一条投稿经验（幂等）。
///
/// @param postId 经验 ID
public record DeleteExperienceCommand(long postId) implements Command<Void> {

  /// 创建命令。
  ///
  /// @param postId 经验 ID
  /// @return 命令
  public static DeleteExperienceCommand of(long postId) {
    return new DeleteExperienceCommand(postId);
  }
}
