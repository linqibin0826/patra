package dev.patra.interaction.infra.collection.persistence;

import dev.patra.interaction.domain.collection.model.enums.LibraryView;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.read.ItemRef;
import dev.patra.interaction.domain.collection.model.read.LibraryCounts;
import dev.patra.interaction.domain.collection.model.read.LibraryEntry;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `LibraryReadPort` 的原生 SQL 实现。
@Component
@RequiredArgsConstructor
public class LibraryReadAdapter implements LibraryReadPort {

  /// 不在任何收藏夹里。
  private static final String UNFILED =
      " AND NOT EXISTS (SELECT 1 FROM collection.collection_item ci"
          + " WHERE ci.user_id = li.user_id AND ci.subject_id = li.subject_id)";

  private static final RowMapper<ItemRef> LIBRARY_REF =
      (rs, rowNum) ->
          ItemRef.of(
              SubjectId.of(rs.getLong("subject_id")), CollectionRows.instant(rs, "collected_at"));

  private static final RowMapper<ItemRef> COLLECTION_REF =
      (rs, rowNum) ->
          ItemRef.of(
              SubjectId.of(rs.getLong("subject_id")), CollectionRows.instant(rs, "added_at"));

  private final JdbcClient jdbc;

  /// 两个计数。
  ///
  /// @param userId 用户
  /// @return 计数
  @Override
  public LibraryCounts countLibrary(long userId) {
    return LibraryCounts.of(
        (int) countView(userId, LibraryView.ALL), (int) countView(userId, LibraryView.UNFILED));
  }

  /// 视图条数。
  ///
  /// @param userId 用户
  /// @param view 视图
  /// @return 条数
  @Override
  public long countView(long userId, LibraryView view) {
    return jdbc.sql(
            "SELECT count(*) FROM collection.library_item li WHERE li.user_id = :userId"
                + filter(view))
        .param("userId", userId)
        .query(Long.class)
        .single();
  }

  /// 我的收藏夹。
  ///
  /// @param userId 用户
  /// @return 收藏夹
  @Override
  public List<CollectionSummary> findCollections(long userId) {
    return jdbc.sql(
            "SELECT "
                + CollectionRows.COLLECTION_COLUMNS
                + " FROM collection.collection WHERE user_id = :userId"
                + " ORDER BY updated_at DESC, collection_id DESC")
        .param("userId", userId)
        .query(CollectionRows.COLLECTION)
        .list();
  }

  /// 按 ID 取。
  ///
  /// @param id 收藏夹
  /// @return 收藏夹
  @Override
  public Optional<CollectionSummary> findCollection(CollectionId id) {
    return jdbc.sql(
            "SELECT "
                + CollectionRows.COLLECTION_COLUMNS
                + " FROM collection.collection WHERE collection_id = :id")
        .param("id", id.value())
        .query(CollectionRows.COLLECTION)
        .optional();
  }

  /// 公开收藏夹。
  ///
  /// @param ownerId 所有者
  /// @return 收藏夹
  @Override
  public List<CollectionSummary> findPublicCollections(long ownerId) {
    return jdbc.sql(
            "SELECT "
                + CollectionRows.COLLECTION_COLUMNS
                + " FROM collection.collection WHERE user_id = :userId AND visibility = 'PUBLIC'"
                + " ORDER BY updated_at DESC, collection_id DESC")
        .param("userId", ownerId)
        .query(CollectionRows.COLLECTION)
        .list();
  }

  /// 文献库一页。
  ///
  /// @param userId 用户
  /// @param view 视图
  /// @param offset 跳过
  /// @param limit 条数
  /// @return 引用
  @Override
  public List<ItemRef> pageLibrary(long userId, LibraryView view, int offset, int limit) {
    return jdbc.sql(librarySql(view) + " LIMIT :limit OFFSET :offset")
        .param("userId", userId)
        .param("limit", limit)
        .param("offset", offset)
        .query(LIBRARY_REF)
        .list();
  }

  /// 文献库全部。
  ///
  /// @param userId 用户
  /// @param view 视图
  /// @return 引用
  @Override
  public List<ItemRef> allLibrary(long userId, LibraryView view) {
    return jdbc.sql(librarySql(view)).param("userId", userId).query(LIBRARY_REF).list();
  }

  /// 收藏夹一页。
  ///
  /// @param ownerId 所有者
  /// @param id 收藏夹
  /// @param offset 跳过
  /// @param limit 条数
  /// @return 引用
  @Override
  public List<ItemRef> pageCollection(long ownerId, CollectionId id, int offset, int limit) {
    return jdbc.sql(COLLECTION_SQL + " LIMIT :limit OFFSET :offset")
        .param("userId", ownerId)
        .param("id", id.value())
        .param("limit", limit)
        .param("offset", offset)
        .query(COLLECTION_REF)
        .list();
  }

  /// 收藏夹全部。
  ///
  /// @param ownerId 所有者
  /// @param id 收藏夹
  /// @return 引用
  @Override
  public List<ItemRef> allCollection(long ownerId, CollectionId id) {
    return jdbc.sql(COLLECTION_SQL)
        .param("userId", ownerId)
        .param("id", id.value())
        .query(COLLECTION_REF)
        .list();
  }

  /// 条目。
  ///
  /// @param userId 用户
  /// @param subjectIds 对象
  /// @return 对象 → 条目
  @Override
  public Map<SubjectId, LibraryEntry> findEntries(long userId, Collection<SubjectId> subjectIds) {
    if (subjectIds.isEmpty()) {
      return Map.of();
    }
    return jdbc
        .sql(
            "SELECT subject_id, note, collected_at FROM collection.library_item"
                + " WHERE user_id = :userId AND subject_id IN (:ids)")
        .param("userId", userId)
        .param("ids", CollectionRows.ids(subjectIds))
        .query(
            (rs, rowNum) ->
                LibraryEntry.of(
                    SubjectId.of(rs.getLong("subject_id")),
                    rs.getString("note"),
                    CollectionRows.instant(rs, "collected_at")))
        .list()
        .stream()
        .collect(Collectors.toUnmodifiableMap(LibraryEntry::subjectId, e -> e));
  }

  /// 归属。
  ///
  /// @param userId 用户
  /// @param subjectIds 对象
  /// @return 对象 → 收藏夹
  @Override
  public Map<SubjectId, List<CollectionId>> findMemberships(
      long userId, Collection<SubjectId> subjectIds) {
    if (subjectIds.isEmpty()) {
      return Map.of();
    }
    Map<SubjectId, List<CollectionId>> result = new HashMap<>();
    jdbc.sql(
            "SELECT subject_id, collection_id FROM collection.collection_item"
                + " WHERE user_id = :userId AND subject_id IN (:ids) ORDER BY collection_id")
        .param("userId", userId)
        .param("ids", CollectionRows.ids(subjectIds))
        .query(
            rs -> {
              result
                  .computeIfAbsent(SubjectId.of(rs.getLong("subject_id")), k -> new ArrayList<>())
                  .add(CollectionId.of(rs.getLong("collection_id")));
            });
    return result.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
  }

  /// 有数据的用户。两个分支各自按 `user_id` 有序取前 `limit` 个再合并：分区表上是各分区主键索引的有序归并，
  /// 取够就停，每批的代价与游标位置无关。
  ///
  /// @param afterUserId 游标
  /// @param limit 条数
  /// @return 用户 ID
  @Override
  public List<Long> findOwnersAfter(long afterUserId, int limit) {
    return jdbc.sql(
            """
            SELECT user_id FROM (
                (SELECT DISTINCT user_id FROM collection.library_item WHERE user_id > :after
                  ORDER BY user_id LIMIT :limit)
                UNION
                (SELECT DISTINCT user_id FROM collection.collection WHERE user_id > :after
                  ORDER BY user_id LIMIT :limit)
            ) owners
            ORDER BY user_id LIMIT :limit
            """)
        .param("after", afterUserId)
        .param("limit", limit)
        .query(Long.class)
        .list();
  }

  /// 收藏夹按加入时间。
  private static final String COLLECTION_SQL =
      "SELECT subject_id, added_at FROM collection.collection_item"
          + " WHERE user_id = :userId AND collection_id = :id"
          + " ORDER BY added_at DESC, subject_id DESC";

  /// 文献库按收藏时间。
  ///
  /// @param view 视图
  /// @return SQL
  private static String librarySql(LibraryView view) {
    return "SELECT li.subject_id, li.collected_at FROM collection.library_item li"
        + " WHERE li.user_id = :userId"
        + filter(view)
        + " ORDER BY li.collected_at DESC, li.subject_id DESC";
  }

  /// 视图条件。
  ///
  /// @param view 视图
  /// @return SQL 片段
  private static String filter(LibraryView view) {
    return view == LibraryView.UNFILED ? UNFILED : "";
  }
}
