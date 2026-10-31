package dev.patra.notification.infra.kernel.persistence;

import dev.patra.notification.domain.kernel.port.repository.PresenceRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `PresenceRepository` 的原生 SQL 实现（`kernel.presence`）。
@Component
@RequiredArgsConstructor
public class PresenceRepositoryAdapter implements PresenceRepository {

  private final JdbcClient jdbc;

  /// 主键 upsert，取新旧中较晚的那个。
  ///
  /// @param userId 用户
  /// @param seenAt 出现的时刻
  @Override
  public void touch(long userId, Instant seenAt) {
    jdbc.sql(
            "INSERT INTO kernel.presence AS p (user_id, last_seen_at) VALUES (:userId, :seenAt)"
                + " ON CONFLICT (user_id) DO UPDATE"
                + " SET last_seen_at = GREATEST(p.last_seen_at, EXCLUDED.last_seen_at)")
        .param("userId", userId)
        .param("seenAt", seenAt.atOffset(ZoneOffset.UTC))
        .update();
  }

  /// 批量读。
  ///
  /// @param userIds 用户
  /// @return 用户 ID → 时刻
  @Override
  public Map<Long, Instant> find(Collection<Long> userIds) {
    Map<Long, Instant> result = new HashMap<>();
    jdbc.sql("SELECT user_id, last_seen_at FROM kernel.presence WHERE user_id IN (:userIds)")
        .param("userIds", List.copyOf(userIds))
        .query(
            rs -> {
              result.put(
                  rs.getLong("user_id"),
                  rs.getObject("last_seen_at", OffsetDateTime.class).toInstant());
            });
    return Map.copyOf(result);
  }

  /// 删一个人的记录。
  ///
  /// @param userId 用户
  /// @return 删除的行数
  @Override
  public int deleteAllOf(long userId) {
    return jdbc.sql("DELETE FROM kernel.presence WHERE user_id = :userId")
        .param("userId", userId)
        .update();
  }

  /// 有记录的用户。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID，升序
  @Override
  public List<Long> findUsersAfter(long afterUserId, int limit) {
    return jdbc.sql(
            "SELECT user_id FROM kernel.presence WHERE user_id > :after"
                + " ORDER BY user_id LIMIT :limit")
        .param("after", afterUserId)
        .param("limit", limit)
        .query(Long.class)
        .list();
  }
}
