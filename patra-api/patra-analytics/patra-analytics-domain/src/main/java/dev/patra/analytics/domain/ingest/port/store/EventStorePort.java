package dev.patra.analytics.domain.ingest.port.store;

import dev.patra.analytics.domain.ingest.exception.IngestRejectedException;
import dev.patra.analytics.domain.ingest.exception.IngestUnavailableException;
import java.util.List;

/// 事件仓库（ClickHouse）：整批写明细与 dlq，以及注销清理要的「找用户、按一批用户删」。
public interface EventStorePort {

  /// 把一批富化事件（每行一条 JSON）一次写进明细表；同一令牌在去重窗口内重复写入会被丢弃。
  ///
  /// @param jsonLines 每条一行 JSON
  /// @param token 批次去重令牌
  /// @throws IngestRejectedException 数据被拒（重试也不会好）
  /// @throws IngestUnavailableException 其余失败（重试可能会好）
  void insertEvents(List<String> jsonLines, String token);

  /// 把一批 dlq 记录（每行一条 JSON）一次写进 dlq 表。
  ///
  /// @param jsonLines 每条一行 JSON
  /// @param token 批次去重令牌
  /// @throws IngestRejectedException 数据被拒
  /// @throws IngestUnavailableException 其余失败
  void insertDlq(List<String> jsonLines, String token);

  /// 游标之后有数据的登录用户，按 ID 升序。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 最多几个
  /// @return 用户 ID
  List<Long> findUsersAfter(long afterUserId, int limit);

  /// 删掉这批用户的全部数据（明细、dlq、文献 × 会话汇总、日活）；未登录时的设备数据不删。
  ///
  /// 日活表最后删：它是找用户的唯一来源，中途失败时其他表的残留还能在下次找回来。删除幂等。
  ///
  /// @param userIds 用户 ID
  /// @throws IngestUnavailableException 删除失败
  void deleteUsers(List<Long> userIds);
}
