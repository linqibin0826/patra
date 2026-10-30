package dev.patra.notification.infra.inbox.persistence;

import dev.patra.notification.domain.inbox.port.repository.SubjectTombstoneRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `SubjectTombstoneRepository` 的原生 SQL 实现（`inbox.subject_tombstone`）。
@Component
@RequiredArgsConstructor
public class SubjectTombstoneRepositoryAdapter implements SubjectTombstoneRepository {

  private final JdbcClient jdbc;

  /// 对象是否已删除。
  ///
  /// @param subjectId 对象
  /// @return 有墓碑为 `true`
  @Override
  public boolean exists(long subjectId) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM inbox.subject_tombstone WHERE subject_id = :subjectId)")
        .param("subjectId", subjectId)
        .query(Boolean.class)
        .single();
  }

  /// 记下已删除；已有时不变。
  ///
  /// @param subjectId 对象
  /// @param deletedAt 删除时刻
  @Override
  public void save(long subjectId, Instant deletedAt) {
    jdbc.sql(
            "INSERT INTO inbox.subject_tombstone (subject_id, deleted_at)"
                + " VALUES (:subjectId, :deletedAt) ON CONFLICT (subject_id) DO NOTHING")
        .param("subjectId", subjectId)
        .param("deletedAt", InboxRows.utc(deletedAt))
        .update();
  }

  /// 删掉早于截止时刻的墓碑。
  ///
  /// @param cutoff 截止时刻（不含）
  /// @return 删除的行数
  @Override
  public int purgeBefore(Instant cutoff) {
    return jdbc.sql("DELETE FROM inbox.subject_tombstone WHERE deleted_at < :cutoff")
        .param("cutoff", InboxRows.utc(cutoff))
        .update();
  }
}
