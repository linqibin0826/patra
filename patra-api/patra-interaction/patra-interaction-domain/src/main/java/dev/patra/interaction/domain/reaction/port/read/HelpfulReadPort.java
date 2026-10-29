package dev.patra.interaction.domain.reaction.port.read;

/// 有帮助的读端：只查 `reaction` schema。
public interface HelpfulReadPort {

  /// 用户收到的有帮助（获赞）：本人未删除、非匿名的评论与经验收到的有帮助总数；没有记录为 0。
  ///
  /// @param userId 用户
  /// @return 获赞
  long helpfulReceived(long userId);
}
