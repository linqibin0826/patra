package dev.patra.notification.domain.inbox.port.repository;

import java.time.Instant;

/// 投稿经验的墓碑：经验删除后，晚到的、它下面评论的事件都丢弃。
public interface SubjectTombstoneRepository {

  /// 对象是否已删除。
  ///
  /// @param subjectId 对象
  /// @return 有墓碑为 `true`
  boolean exists(long subjectId);

  /// 记下对象已删除；已有墓碑时不变。
  ///
  /// @param subjectId 对象
  /// @param deletedAt 删除时刻（事件里的）
  void save(long subjectId, Instant deletedAt);

  /// 删掉删除时刻早于截止时刻的墓碑（保留期清理）。
  ///
  /// @param cutoff 截止时刻（不含）
  /// @return 删除的行数
  int purgeBefore(Instant cutoff);
}
