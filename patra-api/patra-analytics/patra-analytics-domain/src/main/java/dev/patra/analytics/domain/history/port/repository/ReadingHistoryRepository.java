package dev.patra.analytics.domain.history.port.repository;

import dev.patra.analytics.domain.history.model.vo.ReadingKey;
import dev.patra.analytics.domain.history.model.vo.ReadingRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// 阅读历史与删除记录的写端（`history.reading_history`、`history.deleted_entry`），以及注销清理要覆盖的三张表。
public interface ReadingHistoryRepository {

  /// 把调用方事务里之后每条语句的执行与锁等待各限 500 ms：PG 卡住或被锁时写历史很快失败，不拖慢采集。
  void limitStatementTime();

  /// 一条语句写入一批：没有就插入；有就时间取较晚、全文取「或」。在调用方的事务里执行（写历史时先
  /// {@link #limitStatementTime}）。
  ///
  /// @param merged 已按（用户，文献）合并、按（用户，文献）升序排好的记录
  void upsertAll(List<ReadingRecord> merged);

  /// 这些（用户，文献）的删除时刻。
  ///
  /// @param keys 要查的键
  /// @return 有删除记录的键 → 删除时刻
  Map<ReadingKey, Instant> findDeletedAt(Collection<ReadingKey> keys);

  /// 删掉一行并写（或更新）删除记录；这一行不在也照样写删除记录。
  ///
  /// @param userId 用户 ID
  /// @param articleId 文献 ID
  /// @param deletedAt 删除时刻
  void deleteOne(long userId, long articleId, Instant deletedAt);

  /// 删掉这个人的全部历史与删除记录。
  ///
  /// @param userId 用户 ID
  void deleteAllOf(long userId);

  /// 删一批早于过期线的历史。
  ///
  /// @param cutoff 过期线（不含）
  /// @param limit 这一批最多删的行数
  /// @return 删掉的行数
  int purgeReadBefore(Instant cutoff, int limit);

  /// 删一批早于过期线的删除记录。
  ///
  /// @param cutoff 过期线（不含）
  /// @param limit 这一批最多删的行数
  /// @return 删掉的行数
  int purgeDeletionsBefore(Instant cutoff, int limit);

  /// 游标之后在历史、设置、删除记录任一张表里有行的用户。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID，升序去重
  List<Long> findUsersAfter(long afterUserId, int limit);

  /// 删掉这批用户在三张表里的行。
  ///
  /// @param userIds 用户 ID
  void deleteUsers(List<Long> userIds);
}
