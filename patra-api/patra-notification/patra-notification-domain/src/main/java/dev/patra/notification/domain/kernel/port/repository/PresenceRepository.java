package dev.patra.notification.domain.kernel.port.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// 在站记录的存储：每个用户一行最后出现的时刻。
public interface PresenceRepository {

  /// 记下一次出现；只往后挪，晚到的旧时刻不覆盖新的。
  ///
  /// @param userId 用户
  /// @param seenAt 出现的时刻
  void touch(long userId, Instant seenAt);

  /// 批量读。
  ///
  /// @param userIds 用户
  /// @return 用户 ID → 最后出现的时刻；没有记录的不在结果里
  Map<Long, Instant> find(Collection<Long> userIds);

  /// 删一个人的记录（注销清理）。
  ///
  /// @param userId 用户
  /// @return 删除的行数
  int deleteAllOf(long userId);

  /// 有记录的用户，按用户 ID 升序的一批（注销清理枚举用）。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  List<Long> findUsersAfter(long afterUserId, int limit);
}
