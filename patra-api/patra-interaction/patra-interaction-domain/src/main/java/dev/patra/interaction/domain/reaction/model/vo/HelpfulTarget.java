package dev.patra.interaction.domain.reaction.model.vo;

import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import java.util.Objects;

/// 有帮助的目标：种类 + 所属子域里的 ID。
///
/// @param type 种类
/// @param id 评论 ID 或帖子 ID
public record HelpfulTarget(HelpfulTargetType type, long id) {

  /// 校验种类。
  ///
  /// @param type 种类
  /// @param id ID
  public HelpfulTarget {
    Objects.requireNonNull(type, "type 不能为 null");
  }

  /// 创建。
  ///
  /// @param type 种类
  /// @param id ID
  /// @return 目标
  public static HelpfulTarget of(HelpfulTargetType type, long id) {
    return new HelpfulTarget(type, id);
  }
}
