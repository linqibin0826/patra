package dev.patra.interaction.app.reaction.usecase.helpful;

import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.tool.cqrs.Command;

/// 取消自己点过的有帮助（幂等）。
///
/// @param type 目标种类
/// @param targetId 评论 ID 或帖子 ID
public record UnmarkHelpfulCommand(HelpfulTargetType type, long targetId)
    implements Command<HelpfulResult> {

  /// 创建命令。
  ///
  /// @param type 目标种类
  /// @param targetId 目标 ID
  /// @return 命令
  public static UnmarkHelpfulCommand of(HelpfulTargetType type, long targetId) {
    return new UnmarkHelpfulCommand(type, targetId);
  }
}
