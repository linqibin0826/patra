package dev.patra.analytics.domain.kernel.port.gateway;

import java.util.List;

/// 注销清理的扩展点：一个子域里属于某个用户、在他注销后要删除的数据（ingest 的 ClickHouse 事件与汇总，以后
/// history 的阅读历史）。
///
/// 由各子域在自己的 app 层实现（命名 `{子域}UserDataCleanupGatewayImpl`），底座的注销清理流程统一枚举用户、回查
/// identity，再逐个子域调 {@link #deleteAllOf}。接口照 notification 的同名扩展点，但按一批用户删：ClickHouse 的
/// 每条删除都要扫全表，逐个用户删会扫上百遍（入库设计 §6）。
public interface UserDataCleanupGateway {

  /// 子域名，日志里按它列出各子域清理的用户数。
  ///
  /// @return 子域名，如 `ingest`
  String subdomain();

  /// 在这个子域里有数据的用户，按用户 ID 升序的一批。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  List<Long> findUsersAfter(long afterUserId, int limit);

  /// 删这批用户在这个子域里的全部数据。不在事务里调用（ClickHouse 没有事务），删除须幂等：中途失败次日重跑。
  ///
  /// @param userIds 已注销的用户
  /// @return 清理的用户数
  int deleteAllOf(List<Long> userIds);
}
