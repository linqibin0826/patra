package dev.patra.interaction.infra.collection.persistence;

import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.model.vo.CollectionName;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `CollectionRepository` 的原生 SQL 实现。
@Component
@RequiredArgsConstructor
public class CollectionRepositoryAdapter implements CollectionRepository {

  private final JdbcClient jdbc;

  /// 个数。
  ///
  /// @param userId 用户
  /// @return 个数
  @Override
  public int countCollections(long userId) {
    return jdbc.sql("SELECT count(*) FROM collection.collection WHERE user_id = :userId")
        .param("userId", userId)
        .query(Integer.class)
        .single();
  }

  /// 自己的收藏夹。
  ///
  /// @param userId 用户
  /// @param id 收藏夹
  /// @return 收藏夹
  @Override
  public Optional<CollectionSummary> findOwned(long userId, CollectionId id) {
    return jdbc.sql(
            "SELECT "
                + CollectionRows.COLLECTION_COLUMNS
                + " FROM collection.collection WHERE collection_id = :id AND user_id = :userId")
        .param("id", id.value())
        .param("userId", userId)
        .query(CollectionRows.COLLECTION)
        .optional();
  }

  /// 名称是否被占用。
  ///
  /// @param userId 用户
  /// @param name 名称
  /// @param excluding 排除的收藏夹，可空
  /// @return 被占用时为 `true`
  @Override
  public boolean nameTaken(long userId, String name, CollectionId excluding) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM collection.collection WHERE user_id = :userId"
                + " AND name = :name AND collection_id <> :excluding)")
        .param("userId", userId)
        .param("name", name)
        .param("excluding", excluding == null ? 0L : excluding.value())
        .query(Boolean.class)
        .single();
  }

  /// 全部名称。
  ///
  /// @param userId 用户
  /// @return 名称
  @Override
  public Set<String> findNames(long userId) {
    return new HashSet<>(
        jdbc.sql("SELECT name FROM collection.collection WHERE user_id = :userId")
            .param("userId", userId)
            .query(String.class)
            .list());
  }

  /// 新建。
  ///
  /// @param userId 用户
  /// @param name 名称
  /// @param description 简介
  /// @param now 创建时刻
  /// @return 新收藏夹
  @Override
  public CollectionSummary create(
      long userId, CollectionName name, String description, Instant now) {
    return jdbc.sql(
            """
            INSERT INTO collection.collection
                   (collection_id, user_id, name, description, visibility, created_at, updated_at)
            VALUES (:id, :userId, :name, :description, 'PRIVATE', :now, :now)
            RETURNING\s"""
                + CollectionRows.COLLECTION_COLUMNS)
        .param("id", SnowflakeIdGenerator.getId())
        .param("userId", userId)
        .param("name", name.value())
        .param("description", description)
        .param("now", CollectionRows.utc(now))
        .query(CollectionRows.COLLECTION)
        .single();
  }

  /// 改名称与简介。
  ///
  /// @param userId 用户
  /// @param id 收藏夹
  /// @param name 名称
  /// @param description 简介
  /// @param now 更新时刻
  /// @return 改后的收藏夹
  @Override
  public CollectionSummary updateText(
      long userId, CollectionId id, CollectionName name, String description, Instant now) {
    return jdbc.sql(
            """
            UPDATE collection.collection SET name = :name, description = :description, updated_at = :now
             WHERE collection_id = :id AND user_id = :userId
            RETURNING\s"""
                + CollectionRows.COLLECTION_COLUMNS)
        .param("name", name.value())
        .param("description", description)
        .param("now", CollectionRows.utc(now))
        .param("id", id.value())
        .param("userId", userId)
        .query(CollectionRows.COLLECTION)
        .single();
  }

  /// 改可见性。
  ///
  /// @param userId 用户
  /// @param id 收藏夹
  /// @param visibility 可见性
  /// @param now 更新时刻
  /// @return 改后的收藏夹
  @Override
  public CollectionSummary updateVisibility(
      long userId, CollectionId id, CollectionVisibility visibility, Instant now) {
    return jdbc.sql(
            """
            UPDATE collection.collection SET visibility = :visibility, updated_at = :now
             WHERE collection_id = :id AND user_id = :userId
            RETURNING\s"""
                + CollectionRows.COLLECTION_COLUMNS)
        .param("visibility", visibility.name())
        .param("now", CollectionRows.utc(now))
        .param("id", id.value())
        .param("userId", userId)
        .query(CollectionRows.COLLECTION)
        .single();
  }

  /// 删收藏夹与它的条目。
  ///
  /// @param userId 用户
  /// @param id 收藏夹
  @Override
  public void delete(long userId, CollectionId id) {
    jdbc.sql(
            "DELETE FROM collection.collection_item WHERE user_id = :userId AND collection_id = :id")
        .param("userId", userId)
        .param("id", id.value())
        .update();
    jdbc.sql("DELETE FROM collection.collection WHERE user_id = :userId AND collection_id = :id")
        .param("userId", userId)
        .param("id", id.value())
        .update();
  }

  /// 已在夹里的。
  ///
  /// @param userId 所有者
  /// @param id 收藏夹
  /// @param subjectIds 对象
  /// @return 已在夹里的
  @Override
  public Set<SubjectId> findMembers(
      long userId, CollectionId id, Collection<SubjectId> subjectIds) {
    if (subjectIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbc.sql(
                "SELECT subject_id FROM collection.collection_item WHERE user_id = :userId"
                    + " AND collection_id = :id AND subject_id IN (:ids)")
            .param("userId", userId)
            .param("id", id.value())
            .param("ids", CollectionRows.ids(subjectIds))
            .query((rs, rowNum) -> SubjectId.of(rs.getLong("subject_id")))
            .list());
  }

  /// 放进收藏夹并更新篇数。
  ///
  /// @param userId 所有者
  /// @param id 收藏夹
  /// @param subjectIds 对象
  /// @param addedAt 第一个的加入时间
  @Override
  public void addMembers(
      long userId, CollectionId id, List<SubjectId> subjectIds, Instant addedAt) {
    if (subjectIds.isEmpty()) {
      return;
    }
    int added =
        jdbc.sql(
                """
                INSERT INTO collection.collection_item (user_id, collection_id, subject_id, added_at)
                SELECT :userId, :id, s.id, CAST(:addedAt AS timestamptz) - (s.ord - 1) * INTERVAL '1 microsecond'
                  FROM unnest(CAST(:ids AS bigint[])) WITH ORDINALITY AS s(id, ord)
                ON CONFLICT (user_id, collection_id, subject_id) DO NOTHING
                """)
            .param("userId", userId)
            .param("id", id.value())
            .param("addedAt", CollectionRows.utc(addedAt))
            .param("ids", CollectionRows.idArray(subjectIds))
            .update();
    adjustCount(userId, id, added, addedAt);
  }

  /// 移出并更新篇数。
  ///
  /// @param userId 所有者
  /// @param id 收藏夹
  /// @param subjectIds 对象
  /// @param now 当前时刻
  /// @return 移出条数
  @Override
  public int removeMembers(
      long userId, CollectionId id, Collection<SubjectId> subjectIds, Instant now) {
    if (subjectIds.isEmpty()) {
      return 0;
    }
    int removed =
        jdbc.sql(
                "DELETE FROM collection.collection_item WHERE user_id = :userId"
                    + " AND collection_id = :id AND subject_id IN (:ids)")
            .param("userId", userId)
            .param("id", id.value())
            .param("ids", CollectionRows.ids(subjectIds))
            .update();
    adjustCount(userId, id, -removed, now);
    return removed;
  }

  /// 篇数增减，有变化时推进更新时间。
  ///
  /// @param userId 所有者
  /// @param id 收藏夹
  /// @param delta 增减
  /// @param now 当前时刻
  private void adjustCount(long userId, CollectionId id, int delta, Instant now) {
    if (delta == 0) {
      return;
    }
    jdbc.sql(
            "UPDATE collection.collection SET item_count = item_count + :delta, updated_at = :now"
                + " WHERE collection_id = :id AND user_id = :userId")
        .param("delta", delta)
        .param("now", CollectionRows.utc(now))
        .param("id", id.value())
        .param("userId", userId)
        .update();
  }
}
