package dev.patra.interaction.domain.reaction.port.repository;

import dev.patra.interaction.domain.reaction.model.vo.HelpfulMark;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/// 有帮助的持久化：明细 `reaction.helpful`、目标计数 `reaction.target_stats`、获赞 `reaction.user_stats`。
///
/// 写方法须在调用方的事务里调用，且先 {@link #lockTarget}。同一事务里改两种统计行时先目标行、后用户行，多个用户行按
/// 用户 ID 升序。
public interface HelpfulRepository {

  /// 拿目标的事务级锁（`reaction.target:{种类}:{ID}`）：点、取消与删除联动在锁上排队，锁随事务释放。
  ///
  /// @param target 目标
  void lockTarget(HelpfulTarget target);

  /// 这个人点过没有。
  ///
  /// @param userId 点的人
  /// @param target 目标
  /// @return 点过为 `true`
  boolean exists(long userId, HelpfulTarget target);

  /// 插入一条明细，已经有了就什么都不做。
  ///
  /// @param userId 点的人
  /// @param target 目标
  /// @param creditedUserId 计入谁的获赞，匿名目标为 `null`
  /// @param markedAt 点的时刻
  /// @return 真插入了为 `true`
  boolean insert(long userId, HelpfulTarget target, Long creditedUserId, Instant markedAt);

  /// 删一条明细。
  ///
  /// @param userId 点的人
  /// @param target 目标
  /// @return 真删掉时为它计入了谁；没有这条为空
  Optional<HelpfulMark> delete(long userId, HelpfulTarget target);

  /// 删目标的全部明细与它的统计行（删除联动）。
  ///
  /// @param target 目标
  /// @return 受益人 → 删掉的条数；受益人为空的不在结果里
  Map<Long, Integer> deleteAllOn(HelpfulTarget target);

  /// 目标的有帮助数加减：加时没有统计行就建一行；减时统计行必须已经存在（只在真删掉一条明细后减）。
  ///
  /// @param target 目标
  /// @param delta 增量
  void adjustTargetCount(HelpfulTarget target, int delta);

  /// 用户的获赞加减：加时没有统计行就建一行；减时统计行必须已经存在（只在真删掉计入他的明细后减）。
  ///
  /// @param userId 用户
  /// @param delta 增量
  void adjustReceived(long userId, long delta);

  /// 目标当前的有帮助数。
  ///
  /// @param target 目标
  /// @return 有帮助数；没有统计行为 0
  int countOf(HelpfulTarget target);
}
