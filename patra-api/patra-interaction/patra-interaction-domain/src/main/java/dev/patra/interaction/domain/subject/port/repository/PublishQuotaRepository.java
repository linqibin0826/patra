package dev.patra.interaction.domain.subject.port.repository;

import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.policy.PublishQuota;
import java.util.List;
import java.util.Optional;

/// 发表计数的持久化：每个作者每种发表一行。写方法须在调用方的事务里调用，读改写之前先 {@link #lock}。
public interface PublishQuotaRepository {

  /// 拿该作者的事务级锁（`subject.publish:{userId}`），同一作者的并发发表在锁上排队，锁随事务释放。
  ///
  /// @param userId 作者
  void lock(long userId);

  /// 取计数。
  ///
  /// @param userId 作者
  /// @param kind 种类
  /// @return 计数；没有发表过时为空
  Optional<PublishQuota> find(long userId, PublishKind kind);

  /// 写回计数（有就覆盖）。
  ///
  /// @param userId 作者
  /// @param kind 种类
  /// @param quota 新计数
  void save(long userId, PublishKind kind, PublishQuota quota);

  /// 有计数的作者，按用户 ID 升序的一批（注销清理）。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  List<Long> findUsersAfter(long afterUserId, int limit);

  /// 删这个作者的全部计数（注销清理）。
  ///
  /// @param userId 作者
  /// @return 删掉的行数
  int deleteAllOf(long userId);
}
