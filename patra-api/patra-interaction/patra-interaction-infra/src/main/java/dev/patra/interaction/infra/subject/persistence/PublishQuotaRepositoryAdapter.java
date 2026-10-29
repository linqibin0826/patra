package dev.patra.interaction.infra.subject.persistence;

import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.policy.PublishQuota;
import dev.patra.interaction.domain.subject.port.repository.PublishQuotaRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `PublishQuotaRepository` 的原生 SQL 实现（`subject.publish_quota`）。写方法不开事务，由 app 层的事务包住。
@Component
@RequiredArgsConstructor
public class PublishQuotaRepositoryAdapter implements PublishQuotaRepository {

  /// 作者锁的键前缀：`subject.publish:{userId}`。
  private static final String LOCK_PREFIX = "subject.publish:";

  private final JdbcClient jdbc;

  /// 拿作者的事务级 advisory lock。
  ///
  /// @param userId 作者
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lock(long userId) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
        .param("lockKey", LOCK_PREFIX + userId)
        .query((rs, rowNum) -> 1)
        .single();
  }

  /// 按主键取一行。
  ///
  /// @param userId 作者
  /// @param kind 种类
  /// @return 计数
  @Override
  public Optional<PublishQuota> find(long userId, PublishKind kind) {
    return jdbc.sql(
            "SELECT quota_day, day_count, last_published_at FROM subject.publish_quota"
                + " WHERE user_id = :userId AND kind = :kind")
        .param("userId", userId)
        .param("kind", kind.name())
        .query(
            (rs, rowNum) ->
                new PublishQuota(
                    rs.getObject("quota_day", LocalDate.class),
                    rs.getInt("day_count"),
                    rs.getObject("last_published_at", OffsetDateTime.class).toInstant()))
        .optional();
  }

  /// 按主键 upsert。
  ///
  /// @param userId 作者
  /// @param kind 种类
  /// @param quota 新计数
  @Override
  public void save(long userId, PublishKind kind, PublishQuota quota) {
    jdbc.sql(
            """
            INSERT INTO subject.publish_quota
                   (user_id, kind, quota_day, day_count, last_published_at)
            VALUES (:userId, :kind, :day, :count, :last)
            ON CONFLICT (user_id, kind) DO UPDATE
               SET quota_day = EXCLUDED.quota_day, day_count = EXCLUDED.day_count,
                   last_published_at = EXCLUDED.last_published_at
            """)
        .param("userId", userId)
        .param("kind", kind.name())
        .param("day", quota.day())
        .param("count", quota.dayCount())
        .param("last", SubjectRows.utc(quota.lastPublishedAt()))
        .update();
  }

  /// 走主键前导列的去重游标。
  ///
  /// @param afterUserId 游标
  /// @param limit 条数
  /// @return 用户 ID
  @Override
  public List<Long> findUsersAfter(long afterUserId, int limit) {
    return jdbc.sql(
            "SELECT DISTINCT user_id FROM subject.publish_quota WHERE user_id > :after"
                + " ORDER BY user_id LIMIT :limit")
        .param("after", afterUserId)
        .param("limit", limit)
        .query(Long.class)
        .list();
  }

  /// 删这个作者的全部行。
  ///
  /// @param userId 作者
  /// @return 行数
  @Override
  public int deleteAllOf(long userId) {
    return jdbc.sql("DELETE FROM subject.publish_quota WHERE user_id = :userId")
        .param("userId", userId)
        .update();
  }
}
