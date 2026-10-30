package dev.patra.interaction.domain.reaction.port.repository;

import dev.patra.interaction.domain.reaction.model.vo.HelpfulMark;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import java.time.Instant;
import java.util.List;
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

  /// 按明细重算第一步：明细条数与有帮助数统计行不一致的目标（任一边缺行按 0 算）。不加锁。
  ///
  /// @return 目标
  List<HelpfulTarget> findHelpfulCountDrift();

  /// 按明细重算第二步：调用方已拿目标锁（点、取消、删除联动都拿它，锁内没有并发写），按明细数、不一致才写；明细没了
  /// 置 0、保留行。须在调用方的事务里调用。
  ///
  /// @param target 目标
  /// @return 改了才有值
  Optional<StatsCorrection> recountHelpfulCount(HelpfulTarget target);

  /// 按明细重算第一步：有受益人的明细条数与获赞统计行不一致的用户（任一边缺行按 0 算）。不加锁。
  ///
  /// @return 用户 ID
  List<Long> findReceivedDrift();

  /// 按明细重算第二步：写方不拿用户级锁，所以先建出统计行、`FOR UPDATE` 锁住，再用新语句数，不一致才写。只拿用户行
  /// 锁，与写方「目标锁 → 目标行 → 用户行」的顺序不成环。须在调用方的事务里调用。
  ///
  /// @param userId 用户
  /// @return 改了才有值
  Optional<StatsCorrection> recountReceived(long userId);
}
