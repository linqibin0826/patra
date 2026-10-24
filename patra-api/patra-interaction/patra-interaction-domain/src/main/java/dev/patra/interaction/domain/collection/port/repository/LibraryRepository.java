package dev.patra.interaction.domain.collection.port.repository;

import dev.patra.interaction.domain.collection.model.read.DeletedUserData;
import dev.patra.interaction.domain.collection.model.read.LibraryEntry;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// 文献库条目的写端。写方法不开事务，由 app 层的事务包住；改某个用户数据的事务先调 {@link #lockOwner}。
public interface LibraryRepository {

  /// 拿该用户的事务级锁（`collection.user:{userId}`），之后的「查上限 / 查重名 → 写」与该用户的其他写事务串行。
  ///
  /// @param userId 用户
  void lockOwner(long userId);

  /// 收进文献库，已有就什么都不做。
  ///
  /// @param userId 用户
  /// @param subjectId 对象
  /// @param collectedAt 收藏时间
  /// @return 是否新插入
  boolean insertItem(long userId, SubjectId subjectId, Instant collectedAt);

  /// 批量收进文献库（复制收藏夹），已有的跳过；第 `i` 个的收藏时间是 `collectedAt - i` 微秒，保住源顺序。
  ///
  /// @param userId 用户
  /// @param subjectIds 对象，按源顺序
  /// @param collectedAt 第一个的收藏时间
  /// @return 新插入的条数
  int insertItems(long userId, List<SubjectId> subjectIds, Instant collectedAt);

  /// 文献库条目数。
  ///
  /// @param userId 用户
  /// @return 条数
  int countItems(long userId);

  /// 取一条。
  ///
  /// @param userId 用户
  /// @param subjectId 对象
  /// @return 条目
  Optional<LibraryEntry> findItem(long userId, SubjectId subjectId);

  /// 给定对象里哪些已在库里。
  ///
  /// @param userId 用户
  /// @param subjectIds 对象
  /// @return 已在库里的
  Set<SubjectId> findExistingItems(long userId, Collection<SubjectId> subjectIds);

  /// 改备注。
  ///
  /// @param userId 用户
  /// @param subjectId 对象
  /// @param note 新备注，`null` 为删除
  /// @return 有这一条时为 `true`
  boolean updateNote(long userId, SubjectId subjectId, String note);

  /// 从文献库移除，同时移出它们所在的每个收藏夹（篇数减少、更新时间写成 `now`）。不在库里的忽略。
  ///
  /// @param userId 用户
  /// @param subjectIds 对象
  /// @param now 当前时刻
  void deleteItems(long userId, Collection<SubjectId> subjectIds, Instant now);

  /// 删这个用户的全部收藏夹条目、收藏夹与文献库条目（注销清理）。
  ///
  /// @param userId 用户
  /// @return 删掉的行数
  DeletedUserData deleteAllOf(long userId);
}
