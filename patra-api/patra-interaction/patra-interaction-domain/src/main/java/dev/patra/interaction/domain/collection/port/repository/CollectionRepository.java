package dev.patra.interaction.domain.collection.port.repository;

import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.model.vo.CollectionName;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// 收藏夹与收藏夹条目的写端。写方法不开事务，由 app 层的事务包住，并已持有所有者的锁。
public interface CollectionRepository {

  /// 收藏夹个数。
  ///
  /// @param userId 用户
  /// @return 个数
  int countCollections(long userId);

  /// 取自己的收藏夹。
  ///
  /// @param userId 用户
  /// @param id 收藏夹
  /// @return 不存在或不是他的为空
  Optional<CollectionSummary> findOwned(long userId, CollectionId id);

  /// 名称是否已被他的另一个收藏夹占用。
  ///
  /// @param userId 用户
  /// @param name 规范化后的名称
  /// @param excluding 排除的收藏夹（改名时是自己），可空
  /// @return 已占用时为 `true`
  boolean nameTaken(long userId, String name, CollectionId excluding);

  /// 他的全部收藏夹名称（复制时定序号）。
  ///
  /// @param userId 用户
  /// @return 名称
  Set<String> findNames(long userId);

  /// 新建私有的空收藏夹。
  ///
  /// @param userId 用户
  /// @param name 名称
  /// @param description 简介，可空
  /// @param now 创建时刻
  /// @return 新收藏夹
  CollectionSummary create(long userId, CollectionName name, String description, Instant now);

  /// 改名称与简介。
  ///
  /// @param userId 用户
  /// @param id 收藏夹
  /// @param name 名称
  /// @param description 简介，可空
  /// @param now 更新时刻
  /// @return 改后的收藏夹
  CollectionSummary updateText(
      long userId, CollectionId id, CollectionName name, String description, Instant now);

  /// 改可见性。
  ///
  /// @param userId 用户
  /// @param id 收藏夹
  /// @param visibility 可见性
  /// @param now 更新时刻
  /// @return 改后的收藏夹
  CollectionSummary updateVisibility(
      long userId, CollectionId id, CollectionVisibility visibility, Instant now);

  /// 删收藏夹与它的条目（文献库条目不动）。
  ///
  /// @param userId 用户
  /// @param id 收藏夹
  void delete(long userId, CollectionId id);

  /// 给定对象里哪些已在这个收藏夹里。
  ///
  /// @param userId 所有者
  /// @param id 收藏夹
  /// @param subjectIds 对象
  /// @return 已在夹里的
  Set<SubjectId> findMembers(long userId, CollectionId id, Collection<SubjectId> subjectIds);

  /// 放进收藏夹（调用方保证不在夹里、已在库里、不超上限）；第 `i` 个的加入时间是 `addedAt - i` 微秒；
  /// 篇数增加，更新时间写成 `addedAt`。
  ///
  /// @param userId 所有者
  /// @param id 收藏夹
  /// @param subjectIds 对象，按顺序
  /// @param addedAt 第一个的加入时间
  void addMembers(long userId, CollectionId id, List<SubjectId> subjectIds, Instant addedAt);

  /// 移出收藏夹；有移出时篇数减少、更新时间写成 `now`。
  ///
  /// @param userId 所有者
  /// @param id 收藏夹
  /// @param subjectIds 对象
  /// @param now 当前时刻
  /// @return 实际移出的条数
  int removeMembers(long userId, CollectionId id, Collection<SubjectId> subjectIds, Instant now);
}
