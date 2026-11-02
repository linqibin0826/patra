package dev.patra.analytics.infra.history.persistence;

import dev.patra.analytics.domain.history.model.vo.ReadingKey;
import dev.patra.analytics.domain.history.model.vo.ReadingRecord;
import dev.patra.analytics.domain.history.port.repository.ReadingHistoryRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `ReadingHistoryRepository` 的原生 SQL 实现（`history` schema）。写方法与限时要求调用方的事务。
@Component
@RequiredArgsConstructor
public class ReadingHistoryRepositoryAdapter implements ReadingHistoryRepository {

  private final JdbcClient jdbc;

  /// 两条 `SET LOCAL`：只在当前事务里生效，提交或回滚后连接回到默认值。
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void limitStatementTime() {
    jdbc.sql("SET LOCAL statement_timeout = '500ms'").update();
    jdbc.sql("SET LOCAL lock_timeout = '500ms'").update();
  }

  /// 一条多行 `INSERT ... ON CONFLICT DO UPDATE` 写入。
  ///
  /// @param merged 已合并、已排序的记录
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void upsertAll(List<ReadingRecord> merged) {
    if (merged.isEmpty()) {
      return;
    }
    StringJoiner values = new StringJoiner(", ");
    Map<String, Object> params = new HashMap<>();
    for (int i = 0; i < merged.size(); i++) {
      ReadingRecord record = merged.get(i);
      values.add("(:u" + i + ", :a" + i + ", :t" + i + ", :f" + i + ")");
      params.put("u" + i, record.userId());
      params.put("a" + i, record.articleId());
      params.put("t" + i, utc(record.readAt()));
      params.put("f" + i, record.fulltext());
    }
    jdbc.sql(
            "INSERT INTO history.reading_history (user_id, article_id, last_read_at, fulltext_read)"
                + " VALUES "
                + values
                + """
                 ON CONFLICT (user_id, article_id) DO UPDATE
                   SET last_read_at = GREATEST(history.reading_history.last_read_at,
                                               EXCLUDED.last_read_at),
                       fulltext_read = history.reading_history.fulltext_read
                                       OR EXCLUDED.fulltext_read
                """)
        .params(params)
        .update();
  }

  /// 一条 `IN` 查询按（用户，文献）取删除时刻。
  ///
  /// @param keys 要查的键
  /// @return 有删除记录的键 → 删除时刻
  @Override
  public Map<ReadingKey, Instant> findDeletedAt(Collection<ReadingKey> keys) {
    if (keys.isEmpty()) {
      return Map.of();
    }
    StringJoiner tuples = new StringJoiner(", ");
    Map<String, Object> params = new HashMap<>();
    int i = 0;
    for (ReadingKey key : keys) {
      tuples.add("(:u" + i + ", :a" + i + ")");
      params.put("u" + i, key.userId());
      params.put("a" + i, key.articleId());
      i++;
    }
    Map<ReadingKey, Instant> deletions = new HashMap<>();
    jdbc.sql(
            "SELECT user_id, article_id, deleted_at FROM history.deleted_entry"
                + " WHERE (user_id, article_id) IN ("
                + tuples
                + ")")
        .params(params)
        .query(
            rs -> {
              deletions.put(
                  ReadingKey.of(rs.getLong("user_id"), rs.getLong("article_id")),
                  rs.getObject("deleted_at", OffsetDateTime.class).toInstant());
            });
    return Map.copyOf(deletions);
  }

  /// 删这一行，再 upsert 删除记录。
  ///
  /// @param userId 用户 ID
  /// @param articleId 文献 ID
  /// @param deletedAt 删除时刻
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void deleteOne(long userId, long articleId, Instant deletedAt) {
    jdbc.sql(
            "DELETE FROM history.reading_history WHERE user_id = :userId"
                + " AND article_id = :articleId")
        .param("userId", userId)
        .param("articleId", articleId)
        .update();
    jdbc.sql(
            """
            INSERT INTO history.deleted_entry (user_id, article_id, deleted_at)
            VALUES (:userId, :articleId, :at)
            ON CONFLICT (user_id, article_id) DO UPDATE SET deleted_at = EXCLUDED.deleted_at
            """)
        .param("userId", userId)
        .param("articleId", articleId)
        .param("at", utc(deletedAt))
        .update();
  }

  /// 两条语句删历史与删除记录。
  ///
  /// @param userId 用户 ID
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void deleteAllOf(long userId) {
    jdbc.sql("DELETE FROM history.reading_history WHERE user_id = :userId")
        .param("userId", userId)
        .update();
    jdbc.sql("DELETE FROM history.deleted_entry WHERE user_id = :userId")
        .param("userId", userId)
        .update();
  }

  /// 按 `ctid` 删一批：走 `idx_reading_history_time` 找出最多 `limit` 行。
  ///
  /// @param cutoff 过期线（不含）
  /// @param limit 这一批最多删的行数
  /// @return 删掉的行数
  @Override
  public int purgeReadBefore(Instant cutoff, int limit) {
    return jdbc.sql(
            """
            DELETE FROM history.reading_history WHERE ctid IN (
              SELECT ctid FROM history.reading_history WHERE last_read_at < :cutoff LIMIT :limit)
            """)
        .param("cutoff", utc(cutoff))
        .param("limit", limit)
        .update();
  }

  /// 按 `ctid` 删一批：走 `idx_deleted_entry_time` 找出最多 `limit` 行。
  ///
  /// @param cutoff 过期线（不含）
  /// @param limit 这一批最多删的行数
  /// @return 删掉的行数
  @Override
  public int purgeDeletionsBefore(Instant cutoff, int limit) {
    return jdbc.sql(
            """
            DELETE FROM history.deleted_entry WHERE ctid IN (
              SELECT ctid FROM history.deleted_entry WHERE deleted_at < :cutoff LIMIT :limit)
            """)
        .param("cutoff", utc(cutoff))
        .param("limit", limit)
        .update();
  }

  /// 三张表各按主键（首列 `user_id`）取游标之后的前 `limit` 个用户，合并去重后再取前 `limit` 个。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID，升序
  @Override
  public List<Long> findUsersAfter(long afterUserId, int limit) {
    return jdbc.sql(
            """
            SELECT user_id FROM (
              (SELECT DISTINCT user_id FROM history.reading_history WHERE user_id > :after
                ORDER BY user_id LIMIT :limit)
              UNION
              (SELECT user_id FROM history.recording_setting WHERE user_id > :after
                ORDER BY user_id LIMIT :limit)
              UNION
              (SELECT DISTINCT user_id FROM history.deleted_entry WHERE user_id > :after
                ORDER BY user_id LIMIT :limit)
            ) u ORDER BY user_id LIMIT :limit
            """)
        .param("after", afterUserId)
        .param("limit", limit)
        .query(Long.class)
        .list();
  }

  /// 三张表各一条 `IN` 删除。
  ///
  /// @param userIds 用户 ID
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void deleteUsers(List<Long> userIds) {
    if (userIds.isEmpty()) {
      return;
    }
    List<Long> ids = new ArrayList<>(userIds);
    for (String table :
        List.of("history.reading_history", "history.recording_setting", "history.deleted_entry")) {
      jdbc.sql("DELETE FROM " + table + " WHERE user_id IN (:ids)").param("ids", ids).update();
    }
  }

  /// 按 UTC 绑定时间参数。
  ///
  /// @param instant 时刻
  /// @return UTC 偏移的时间
  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
