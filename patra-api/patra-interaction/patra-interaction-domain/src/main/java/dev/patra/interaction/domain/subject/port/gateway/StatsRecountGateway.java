package dev.patra.interaction.domain.subject.port.gateway;

import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import java.util.List;
import java.util.Optional;

/// 按明细重算的扩展点（release spec v0.15 决策 A：不做每晚对账，手动触发的重算兜底）：一个实现管一项计数。
///
/// 由各子域在自己的 app 层实现（命名 `{计数}RecountGatewayImpl`），底座的重算流程统一开事务、汇总差异。与
/// `UserDataCleanupGateway` 一样是「底座调子域」的方向，放在 `port.gateway`、实现类叫 `*GatewayImpl`。
///
/// 分两步，是因为不能一条语句「按明细重数再覆盖」：读已提交下子查询用语句开始时的快照，会把并发写入刚加的 ±1 覆盖掉。
///
/// @param <K> 统计行的键，如对象 ID、用户 ID、目标
public interface StatsRecountGateway<K> {

  /// 计数名，报告与日志里用它，如 `collection.collector_count`。
  ///
  /// @return 计数名
  String counter();

  /// 第一步：不加锁地对比明细与统计，找出不一致的键。正在写的键可能被误报，第二步会在锁内再核对。
  ///
  /// @return 不一致的键
  List<K> findDrifted();

  /// 第二步：在底座开的事务里调用，一个键一个事务。先拿与写方相同的锁，再用新语句按明细数，不一致才写。
  ///
  /// @param key 键
  /// @return 改了才有值
  Optional<StatsCorrection> recount(K key);
}
