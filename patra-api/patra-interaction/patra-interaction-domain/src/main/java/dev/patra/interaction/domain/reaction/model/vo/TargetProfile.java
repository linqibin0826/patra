package dev.patra.interaction.domain.reaction.model.vo;

import dev.patra.interaction.domain.reaction.exception.HelpfulTargetNotFoundException;
import dev.patra.interaction.domain.reaction.exception.SelfHelpfulException;
import java.util.Objects;

/// 目标画像：作者、现在能不能点、是否匿名（决定计不计获赞）。
///
/// @param target 目标
/// @param authorId 作者
/// @param available 能不能点：评论正常且所在对象正常，经验未删除
/// @param anonymous 匿名经验，或作者在自己匿名经验下发的评论：收到的有帮助不计入作者获赞
public record TargetProfile(
    HelpfulTarget target, long authorId, boolean available, boolean anonymous) {

  /// 校验目标。
  ///
  /// @param target 目标
  /// @param authorId 作者
  /// @param available 能不能点
  /// @param anonymous 是否匿名
  public TargetProfile {
    Objects.requireNonNull(target, "target 不能为 null");
  }

  /// 创建。
  ///
  /// @param target 目标
  /// @param authorId 作者
  /// @param available 能不能点
  /// @param anonymous 是否匿名
  /// @return 画像
  public static TargetProfile of(
      HelpfulTarget target, long authorId, boolean available, boolean anonymous) {
    return new TargetProfile(target, authorId, available, anonymous);
  }

  /// 这一条有帮助计入谁的获赞。
  ///
  /// @return 作者；匿名时为 `null`
  public Long creditedUserId() {
    return anonymous ? null : authorId;
  }

  /// 要求现在能点（锁内复核只判这一项：作者不会变）。
  ///
  /// @throws HelpfulTargetNotFoundException 占位、已删除或所在对象已失效
  public void requireAvailable() {
    if (!available) {
      throw new HelpfulTargetNotFoundException();
    }
  }

  /// 要求这个人能点：先判能不能点，再判是不是自己的内容（匿名经验的作者同样不能点）。
  ///
  /// @param userId 点的人
  /// @throws HelpfulTargetNotFoundException 不能点
  /// @throws SelfHelpfulException 自己的内容
  public void requireMarkableBy(long userId) {
    requireAvailable();
    if (authorId == userId) {
      throw new SelfHelpfulException();
    }
  }
}
