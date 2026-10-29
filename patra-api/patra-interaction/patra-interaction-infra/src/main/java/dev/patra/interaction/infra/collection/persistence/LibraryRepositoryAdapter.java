package dev.patra.interaction.infra.collection.persistence;

import dev.patra.interaction.domain.collection.model.read.DeletedUserData;
import dev.patra.interaction.domain.collection.model.read.LibraryEntry;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
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

  /// 收进文献库，已有就不动。
  ///
  /// @param userId 用户
  /// @param subjectId 对象
  /// @param collectedAt 收藏时间
  /// @return 是否新插入
  @Override
  public boolean insertItem(long userId, SubjectId subjectId, Instant collectedAt) {
    return jdbc.sql(
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
  }

  /// 批量收进文献库，保序递减 1 微秒。
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
    return jdbc.sql(
            """
            INSERT INTO collection.library_item (user_id, subject_id, collected_at)
            SELECT :userId, s.id, CAST(:collectedAt AS timestamptz) - (s.ord - 1) * INTERVAL '1 microsecond'
              FROM unnest(CAST(:ids AS bigint[])) WITH ORDINALITY AS s(id, ord)
            ON CONFLICT (user_id, subject_id) DO NOTHING
            """)
        .param("userId", userId)
        .param("collectedAt", CollectionRows.utc(collectedAt))
        .param("ids", CollectionRows.idArray(subjectIds))
        .update();
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

  /// 先移出各收藏夹（按夹聚合减篇数），再删条目。
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
    jdbc.sql("DELETE FROM collection.library_item WHERE user_id = :userId AND subject_id IN (:ids)")
        .param("userId", userId)
        .param("ids", CollectionRows.ids(subjectIds))
        .update();
  }

  /// 删这个用户的全部数据。
  ///
  /// @param userId 用户
  /// @return 行数
  @Override
  public DeletedUserData deleteAllOf(long userId) {
    int collectionItems = deleteByUser("collection.collection_item", userId);
    int collections = deleteByUser("collection.collection", userId);
    int libraryItems = deleteByUser("collection.library_item", userId);
    return DeletedUserData.of(libraryItems, collections, collectionItems);
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
