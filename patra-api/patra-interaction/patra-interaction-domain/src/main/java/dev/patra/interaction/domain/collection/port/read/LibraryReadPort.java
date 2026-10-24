package dev.patra.interaction.domain.collection.port.read;

import dev.patra.interaction.domain.collection.model.enums.LibraryView;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.read.ItemRef;
import dev.patra.interaction.domain.collection.model.read.LibraryCounts;
import dev.patra.interaction.domain.collection.model.read.LibraryEntry;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// 文献库与收藏夹的读端（CQRS 读侧），只读本地表。
///
/// 「按加入时间」的顺序：时间倒序，同时间按对象 ID 倒序。
public interface LibraryReadPort {

  /// 「全部」「未分类」两个计数。
  ///
  /// @param userId 用户
  /// @return 计数
  LibraryCounts countLibrary(long userId);

  /// 一个视图的条数。
  ///
  /// @param userId 用户
  /// @param view 视图
  /// @return 条数
  long countView(long userId, LibraryView view);

  /// 他的全部收藏夹，按更新时间倒序。
  ///
  /// @param userId 用户
  /// @return 收藏夹
  List<CollectionSummary> findCollections(long userId);

  /// 按 ID 取收藏夹，不限所有者（公开读用）。
  ///
  /// @param id 收藏夹
  /// @return 收藏夹
  Optional<CollectionSummary> findCollection(CollectionId id);

  /// 他的公开收藏夹，按更新时间倒序。
  ///
  /// @param ownerId 所有者
  /// @return 收藏夹
  List<CollectionSummary> findPublicCollections(long ownerId);

  /// 文献库视图按加入时间的一页。
  ///
  /// @param userId 用户
  /// @param view 视图
  /// @param offset 跳过条数
  /// @param limit 条数
  /// @return 引用
  List<ItemRef> pageLibrary(long userId, LibraryView view, int offset, int limit);

  /// 文献库视图的全部（≤ 20,000），按加入时间。
  ///
  /// @param userId 用户
  /// @param view 视图
  /// @return 引用
  List<ItemRef> allLibrary(long userId, LibraryView view);

  /// 收藏夹按加入该夹时间的一页。
  ///
  /// @param ownerId 所有者
  /// @param id 收藏夹
  /// @param offset 跳过条数
  /// @param limit 条数
  /// @return 引用
  List<ItemRef> pageCollection(long ownerId, CollectionId id, int offset, int limit);

  /// 收藏夹的全部（≤ 5,000），按加入该夹的时间。
  ///
  /// @param ownerId 所有者
  /// @param id 收藏夹
  /// @return 引用
  List<ItemRef> allCollection(long ownerId, CollectionId id);

  /// 取文献库条目（备注、收藏时间）。
  ///
  /// @param userId 用户
  /// @param subjectIds 对象
  /// @return 对象 → 条目；不在库里的不在结果里
  Map<SubjectId, LibraryEntry> findEntries(long userId, Collection<SubjectId> subjectIds);

  /// 条目所在的收藏夹。
  ///
  /// @param userId 用户
  /// @param subjectIds 对象
  /// @return 对象 → 收藏夹；不在任何夹里的不在结果里
  Map<SubjectId, List<CollectionId>> findMemberships(long userId, Collection<SubjectId> subjectIds);

  /// 有收藏数据的用户（文献库条目或收藏夹），按用户 ID 升序的一批（注销清理）。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  List<Long> findOwnersAfter(long afterUserId, int limit);
}
