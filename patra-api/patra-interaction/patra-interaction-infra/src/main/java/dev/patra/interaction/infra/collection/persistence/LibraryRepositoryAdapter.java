package dev.patra.interaction.infra.collection.persistence;

import dev.patra.interaction.domain.collection.model.read.DeletedUserData;
import dev.patra.interaction.domain.collection.model.read.LibraryEntry;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `LibraryRepository` 的原生 SQL 实现。状态窄表按 `user_id` 分区，每条语句都带 `user_id`。
@Component
@RequiredArgsConstructor
public class LibraryRepositoryAdapter implements LibraryRepository {

  /// 用户锁的键前缀。
  private static final String LOCK_PREFIX = "collection.user:";

  private final JdbcClient jdbc;

  /// 拿该用户的事务级 advisory lock。
  ///
  /// @param userId 用户
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockOwner(long userId) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
        .param("key", LOCK_PREFIX + userId)
        .query((rs, rowNum) -> 1)
        .single();
  }

  /// 收进文献库，已有就不动；真插入了才给收藏人数 +1。
  ///
  /// @param userId 用户
  /// @param subjectId 对象
  /// @param collectedAt 收藏时间
  /// @return 是否新插入
  @Override
  public boolean insertItem(long userId, SubjectId subjectId, Instant collectedAt) {
    boolean inserted =
        jdbc.sql(
                    """
                    INSERT INTO collection.library_item (user_id, subject_id, collected_at)
                    VALUES (:userId, :subjectId, :collectedAt)
                    ON CONFLICT (user_id, subject_id) DO NOTHING
                    """)
                .param("userId", userId)
                .param("subjectId", subjectId.value())
                .param("collectedAt", CollectionRows.utc(collectedAt))
                .update()
            == 1;
    if (inserted) {
      incrementCollectors(List.of(subjectId.value()));
    }
    return inserted;
  }

  /// 批量收进文献库，保序递减 1 微秒；给真插入的对象收藏人数各 +1。
  ///
  /// @param userId 用户
  /// @param subjectIds 对象
  /// @param collectedAt 第一个的收藏时间
  /// @return 新插入条数
  @Override
  public int insertItems(long userId, List<SubjectId> subjectIds, Instant collectedAt) {
    if (subjectIds.isEmpty()) {
      return 0;
    }
    List<Long> inserted =
        jdbc.sql(
                """
                INSERT INTO collection.library_item (user_id, subject_id, collected_at)
                SELECT :userId, s.id, CAST(:collectedAt AS timestamptz) - (s.ord - 1) * INTERVAL '1 microsecond'
                  FROM unnest(CAST(:ids AS bigint[])) WITH ORDINALITY AS s(id, ord)
                ON CONFLICT (user_id, subject_id) DO NOTHING
                RETURNING subject_id
                """)
            .param("userId", userId)
            .param("collectedAt", CollectionRows.utc(collectedAt))
            .param("ids", CollectionRows.idArray(subjectIds))
            .query(Long.class)
            .list();
    incrementCollectors(inserted);
    return inserted.size();
  }

  /// 条目数。
  ///
  /// @param userId 用户
  /// @return 条数
  @Override
  public int countItems(long userId) {
    return jdbc.sql("SELECT count(*) FROM collection.library_item WHERE user_id = :userId")
        .param("userId", userId)
        .query(Integer.class)
        .single();
  }

  /// 取一条。
  ///
  /// @param userId 用户
  /// @param subjectId 对象
  /// @return 条目
  @Override
  public Optional<LibraryEntry> findItem(long userId, SubjectId subjectId) {
    return jdbc.sql(
            "SELECT subject_id, note, collected_at FROM collection.library_item"
                + " WHERE user_id = :userId AND subject_id = :subjectId")
        .param("userId", userId)
        .param("subjectId", subjectId.value())
        .query(
            (rs, rowNum) ->
                LibraryEntry.of(
                    SubjectId.of(rs.getLong("subject_id")),
                    rs.getString("note"),
                    CollectionRows.instant(rs, "collected_at")))
        .optional();
  }

  /// 已在库里的。
  ///
  /// @param userId 用户
  /// @param subjectIds 对象
  /// @return 已在库里的
  @Override
  public Set<SubjectId> findExistingItems(long userId, Collection<SubjectId> subjectIds) {
    if (subjectIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbc.sql(
                "SELECT subject_id FROM collection.library_item"
                    + " WHERE user_id = :userId AND subject_id IN (:ids)")
            .param("userId", userId)
            .param("ids", CollectionRows.ids(subjectIds))
            .query((rs, rowNum) -> SubjectId.of(rs.getLong("subject_id")))
            .list());
  }

  /// 改备注。
  ///
  /// @param userId 用户
  /// @param subjectId 对象
  /// @param note 备注，可空
  /// @return 有这一条时为 `true`
  @Override
  public boolean updateNote(long userId, SubjectId subjectId, String note) {
    return jdbc.sql(
                "UPDATE collection.library_item SET note = :note"
                    + " WHERE user_id = :userId AND subject_id = :subjectId")
            .param("note", note)
            .param("userId", userId)
            .param("subjectId", subjectId.value())
            .update()
        == 1;
  }

  /// 先移出各收藏夹（按夹聚合减篇数），再删条目，给真删掉的对象收藏人数各 −1。
  ///
  /// @param userId 用户
  /// @param subjectIds 对象
  /// @param now 当前时刻
  @Override
  public void deleteItems(long userId, Collection<SubjectId> subjectIds, Instant now) {
    if (subjectIds.isEmpty()) {
      return;
    }
    jdbc.sql(
            """
            WITH removed AS (
                DELETE FROM collection.collection_item
                 WHERE user_id = :userId AND subject_id IN (:ids)
                RETURNING collection_id
            ), counts AS (
                SELECT collection_id, count(*) AS n FROM removed GROUP BY collection_id
            )
            UPDATE collection.collection c
               SET item_count = c.item_count - counts.n, updated_at = :now
              FROM counts
             WHERE c.collection_id = counts.collection_id
            """)
        .param("userId", userId)
        .param("ids", CollectionRows.ids(subjectIds))
        .param("now", CollectionRows.utc(now))
        .update();
    decrementCollectors(
        jdbc.sql(
                "DELETE FROM collection.library_item WHERE user_id = :userId"
                    + " AND subject_id IN (:ids) RETURNING subject_id")
            .param("userId", userId)
            .param("ids", CollectionRows.ids(subjectIds))
            .query(Long.class)
            .list());
  }

  /// 删这个用户的全部数据；他收藏过的对象收藏人数各 −1。
  ///
  /// @param userId 用户
  /// @return 行数
  @Override
  public DeletedUserData deleteAllOf(long userId) {
    int collectionItems = deleteByUser("collection.collection_item", userId);
    int collections = deleteByUser("collection.collection", userId);
    List<Long> removed =
        jdbc.sql("DELETE FROM collection.library_item WHERE user_id = :userId RETURNING subject_id")
            .param("userId", userId)
            .query(Long.class)
            .list();
    decrementCollectors(removed);
    return DeletedUserData.of(removed.size(), collections, collectionItems);
  }

  /// 明细按对象聚合，与统计表全外连接，两边缺行都按 0 比较。
  ///
  /// @return 不一致的对象
  @Override
  public List<SubjectId> findCollectorCountDrift() {
    return jdbc.sql(
            """
            SELECT COALESCE(d.subject_id, s.subject_id) AS subject_id
              FROM (SELECT subject_id, count(*) AS n FROM collection.library_item GROUP BY subject_id) d
              FULL JOIN collection.subject_stats s ON s.subject_id = d.subject_id
             WHERE COALESCE(d.n, 0) <> COALESCE(s.collector_count, 0)
            """)
        .query((rs, rowNum) -> SubjectId.of(rs.getLong("subject_id")))
        .list();
  }

  /// 先建出行、`FOR UPDATE` 锁住，再用新语句数：读已提交下新语句的快照晚于拿到锁，已提交的写入都看得见，没提交的
  /// 写入会在锁释放后在写下的值上再 ±1，不会丢。
  ///
  /// @param subjectId 对象
  /// @return 改了才有值
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<StatsCorrection> recountCollectorCount(SubjectId subjectId) {
    jdbc.sql(
            "INSERT INTO collection.subject_stats (subject_id, collector_count) VALUES (:id, 0)"
                + " ON CONFLICT (subject_id) DO NOTHING")
        .param("id", subjectId.value())
        .update();
    int before =
        jdbc.sql(
                "SELECT collector_count FROM collection.subject_stats WHERE subject_id = :id"
                    + " FOR UPDATE")
            .param("id", subjectId.value())
            .query(Integer.class)
            .single();
    int after =
        jdbc.sql("SELECT count(*) FROM collection.library_item WHERE subject_id = :id")
            .param("id", subjectId.value())
            .query(Integer.class)
            .single();
    if (before == after) {
      return Optional.empty();
    }
    jdbc.sql("UPDATE collection.subject_stats SET collector_count = :n WHERE subject_id = :id")
        .param("n", after)
        .param("id", subjectId.value())
        .update();
    return Optional.of(StatsCorrection.of(before, after));
  }

  /// 给这些对象的收藏人数各 +1，没有统计行就建出来。按 `subject_id` 升序处理，多行时拿行锁的顺序固定，两个批量
  /// 操作不会互等。
  ///
  /// @param subjectIds 对象，不重复
  private void incrementCollectors(Collection<Long> subjectIds) {
    if (subjectIds.isEmpty()) {
      return;
    }
    jdbc.sql(
            """
            INSERT INTO collection.subject_stats (subject_id, collector_count)
            SELECT id, 1 FROM unnest(CAST(:ids AS bigint[])) AS t(id) ORDER BY id
            ON CONFLICT (subject_id) DO UPDATE
               SET collector_count = collection.subject_stats.collector_count + 1
            """)
        .param("ids", subjectIds.toArray(Long[]::new))
        .update();
  }

  /// 给这些对象的收藏人数各 −1。先按 `subject_id` 升序锁住统计行（多行 `UPDATE` 不保证加锁顺序），再减；只用
  /// `UPDATE`：PG 先判 CHECK 再判冲突，负值的 upsert 会先撞上约束。要减时统计行一定在（插入时建出来的）。
  ///
  /// @param subjectIds 对象，不重复
  private void decrementCollectors(List<Long> subjectIds) {
    if (subjectIds.isEmpty()) {
      return;
    }
    jdbc.sql(
            "SELECT subject_id FROM collection.subject_stats WHERE subject_id IN (:ids)"
                + " ORDER BY subject_id FOR UPDATE")
        .param("ids", subjectIds)
        .query(Long.class)
        .list();
    jdbc.sql(
            "UPDATE collection.subject_stats SET collector_count = collector_count - 1"
                + " WHERE subject_id IN (:ids)")
        .param("ids", subjectIds)
        .update();
  }

  /// 按用户删一张表的行。
  ///
  /// @param table 带 schema 的表名（常量）
  /// @param userId 用户
  /// @return 行数
  private int deleteByUser(String table, long userId) {
    return jdbc.sql("DELETE FROM " + table + " WHERE user_id = :userId")
        .param("userId", userId)
        .update();
  }
}
