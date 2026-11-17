package dev.patra.interaction.infra.reaction.persistence;

import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulMark;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.port.repository.HelpfulRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `HelpfulRepository` 的原生 SQL 实现（`itr_helpful` 与两张计数表）。写方法不开事务，由 app 层的事务包住。
///
/// 三张表都是其他表：新行的 `id` 由应用预分配雪花 ID，冲突时保留原行的 `id`；`created_at` 由数据库触发器填。
@Component
@RequiredArgsConstructor
public class HelpfulRepositoryAdapter implements HelpfulRepository {

  /// 目标锁的键前缀：`reaction.target:{种类}:{ID}`。
  private static final String LOCK_PREFIX = "reaction.target:";

  private final JdbcClient jdbc;

  /// 拿目标的事务级 advisory lock。
  ///
  /// @param target 目标
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockTarget(HelpfulTarget target) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
        .param("lockKey", LOCK_PREFIX + target.type().name() + ":" + target.id())
        .query((rs, rowNum) -> 1)
        .single();
  }

  /// 按主键查有没有。
  ///
  /// @param userId 点的人
  /// @param target 目标
  /// @return 有为 `true`
  @Override
  public boolean exists(long userId, HelpfulTarget target) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM itr_helpful WHERE user_id = :userId"
                + " AND target_type = :type AND target_id = :id)")
        .param("userId", userId)
        .param("type", target.type().name())
        .param("id", target.id())
        .query(Boolean.class)
        .single();
  }

  /// `ON CONFLICT DO NOTHING` 插入。
  ///
  /// @param userId 点的人
  /// @param target 目标
  /// @param creditedUserId 受益人，可空
  /// @return 影响行数为 1 时为 `true`
  @Override
  public boolean insert(long userId, HelpfulTarget target, Long creditedUserId) {
    return jdbc.sql(
                """
                INSERT INTO itr_helpful (id, user_id, target_type, target_id, credited_user_id)
                VALUES (:rowId, :userId, :type, :id, :credited)
                ON CONFLICT (user_id, target_type, target_id) DO NOTHING
                """)
            .param("rowId", SnowflakeIdGenerator.getId())
            .param("userId", userId)
            .param("type", target.type().name())
            .param("id", target.id())
            .param("credited", creditedUserId)
            .update()
        == 1;
  }

  /// 按主键删，带回受益人。
  ///
  /// @param userId 点的人
  /// @param target 目标
  /// @return 删掉的那条
  @Override
  public Optional<HelpfulMark> delete(long userId, HelpfulTarget target) {
    return jdbc.sql(
            "DELETE FROM itr_helpful WHERE user_id = :userId AND target_type = :type"
                + " AND target_id = :id RETURNING credited_user_id")
        .param("userId", userId)
        .param("type", target.type().name())
        .param("id", target.id())
        .query((rs, rowNum) -> HelpfulMark.of(rs.getObject("credited_user_id", Long.class)))
        .optional();
  }

  /// 走 `idx_itr_helpful_target` 删明细，Java 里按受益人计数，再删统计行。
  ///
  /// @param target 目标
  /// @return 受益人 → 条数
  @Override
  public Map<Long, Integer> deleteAllOn(HelpfulTarget target) {
    List<Long> credited =
        jdbc.sql(
                "DELETE FROM itr_helpful WHERE target_type = :type AND target_id = :id"
                    + " RETURNING credited_user_id")
            .param("type", target.type().name())
            .param("id", target.id())
            .query((rs, rowNum) -> rs.getObject("credited_user_id", Long.class))
            .list();
    jdbc.sql("DELETE FROM itr_helpful_target_stats WHERE target_type = :type AND target_id = :id")
        .param("type", target.type().name())
        .param("id", target.id())
        .update();
    Map<Long, Integer> counts = new HashMap<>();
    credited.stream().filter(id -> id != null).forEach(id -> counts.merge(id, 1, Integer::sum));
    return Map.copyOf(counts);
  }

  /// 加：upsert；减：只 `UPDATE`（要减时统计行一定在）。不能都用 upsert：PG 先对候选行判 CHECK、再判冲突，
  /// `VALUES (-1)` 会先撞上 `ck_itr_helpful_target_stats_count`。
  ///
  /// @param target 目标
  /// @param delta 增量
  @Override
  public void adjustTargetCount(HelpfulTarget target, int delta) {
    String sql =
        delta >= 0
            ? """
              INSERT INTO itr_helpful_target_stats (id, target_type, target_id, helpful_count)
              VALUES (:rowId, :type, :id, :delta)
              ON CONFLICT (target_type, target_id) DO UPDATE
                 SET helpful_count = itr_helpful_target_stats.helpful_count + EXCLUDED.helpful_count
              """
            : "UPDATE itr_helpful_target_stats SET helpful_count = helpful_count + :delta"
                + " WHERE target_type = :type AND target_id = :id";
    jdbc.sql(sql)
        .param("rowId", SnowflakeIdGenerator.getId())
        .param("type", target.type().name())
        .param("id", target.id())
        .param("delta", delta)
        .update();
  }

  /// 加：upsert；减：只 `UPDATE`（理由同 {@link #adjustTargetCount}）。
  ///
  /// @param userId 用户
  /// @param delta 增量
  @Override
  public void adjustReceived(long userId, long delta) {
    String sql =
        delta >= 0
            ? """
              INSERT INTO itr_helpful_user_stats (id, user_id, helpful_received)
              VALUES (:rowId, :userId, :delta)
              ON CONFLICT (user_id) DO UPDATE
                 SET helpful_received = itr_helpful_user_stats.helpful_received
                                        + EXCLUDED.helpful_received
              """
            : "UPDATE itr_helpful_user_stats SET helpful_received = helpful_received + :delta"
                + " WHERE user_id = :userId";
    jdbc.sql(sql)
        .param("rowId", SnowflakeIdGenerator.getId())
        .param("userId", userId)
        .param("delta", delta)
        .update();
  }

  /// 读统计行，没有为 0。
  ///
  /// @param target 目标
  /// @return 有帮助数
  @Override
  public int countOf(HelpfulTarget target) {
    return jdbc.sql(
            "SELECT helpful_count FROM itr_helpful_target_stats WHERE target_type = :type"
                + " AND target_id = :id")
        .param("type", target.type().name())
        .param("id", target.id())
        .query(Integer.class)
        .optional()
        .orElse(0);
  }

  /// 明细按目标聚合，与统计表全外连接，两边缺行都按 0 比较。
  ///
  /// @return 不一致的目标
  @Override
  public List<HelpfulTarget> findHelpfulCountDrift() {
    return jdbc.sql(
            """
            SELECT COALESCE(d.target_type, s.target_type) AS target_type,
                   COALESCE(d.target_id, s.target_id) AS target_id
              FROM (SELECT target_type, target_id, count(*) AS n
                      FROM itr_helpful GROUP BY target_type, target_id) d
              FULL JOIN itr_helpful_target_stats s
                ON s.target_type = d.target_type AND s.target_id = d.target_id
             WHERE COALESCE(d.n, 0) <> COALESCE(s.helpful_count, 0)
            """)
        .query(
            (rs, rowNum) ->
                HelpfulTarget.of(
                    HelpfulTargetType.valueOf(rs.getString("target_type")),
                    rs.getLong("target_id")))
        .list();
  }

  /// 目标锁内没有并发写：直接数、读旧值（缺行为 0），不一致就 upsert 成明细数。
  ///
  /// @param target 目标
  /// @return 改了才有值
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<StatsCorrection> recountHelpfulCount(HelpfulTarget target) {
    int after =
        jdbc.sql(
                "SELECT count(*) FROM itr_helpful WHERE target_type = :type"
                    + " AND target_id = :id")
            .param("type", target.type().name())
            .param("id", target.id())
            .query(Integer.class)
            .single();
    int before = countOf(target);
    if (before == after) {
      return Optional.empty();
    }
    jdbc.sql(
            """
            INSERT INTO itr_helpful_target_stats (id, target_type, target_id, helpful_count)
            VALUES (:rowId, :type, :id, :n)
            ON CONFLICT (target_type, target_id) DO UPDATE SET helpful_count = EXCLUDED.helpful_count
            """)
        .param("rowId", SnowflakeIdGenerator.getId())
        .param("type", target.type().name())
        .param("id", target.id())
        .param("n", after)
        .update();
    return Optional.of(StatsCorrection.of(before, after));
  }

  /// 有受益人的明细按受益人聚合，与统计表全外连接，两边缺行都按 0 比较。
  ///
  /// @return 不一致的用户
  @Override
  public List<Long> findReceivedDrift() {
    return jdbc.sql(
            """
            SELECT COALESCE(d.user_id, s.user_id) AS user_id
              FROM (SELECT credited_user_id AS user_id, count(*) AS n
                      FROM itr_helpful
                     WHERE credited_user_id IS NOT NULL
                     GROUP BY credited_user_id) d
              FULL JOIN itr_helpful_user_stats s ON s.user_id = d.user_id
             WHERE COALESCE(d.n, 0) <> COALESCE(s.helpful_received, 0)
            """)
        .query(Long.class)
        .list();
  }

  /// 先建出行、`FOR UPDATE` 锁住，再用新语句数：读已提交下新语句的快照晚于拿到锁，没提交的写入会在锁释放后在写下的
  /// 值上再 ±1，不会丢。
  ///
  /// @param userId 用户
  /// @return 改了才有值
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<StatsCorrection> recountReceived(long userId) {
    jdbc.sql(
            "INSERT INTO itr_helpful_user_stats (id, user_id, helpful_received)"
                + " VALUES (:rowId, :userId, 0) ON CONFLICT (user_id) DO NOTHING")
        .param("rowId", SnowflakeIdGenerator.getId())
        .param("userId", userId)
        .update();
    long before =
        jdbc.sql(
                "SELECT helpful_received FROM itr_helpful_user_stats WHERE user_id = :userId"
                    + " FOR UPDATE")
            .param("userId", userId)
            .query(Long.class)
            .single();
    long after =
        jdbc.sql("SELECT count(*) FROM itr_helpful WHERE credited_user_id = :userId")
            .param("userId", userId)
            .query(Long.class)
            .single();
    if (before == after) {
      return Optional.empty();
    }
    jdbc.sql("UPDATE itr_helpful_user_stats SET helpful_received = :n WHERE user_id = :userId")
        .param("n", after)
        .param("userId", userId)
        .update();
    return Optional.of(StatsCorrection.of(before, after));
  }
}
