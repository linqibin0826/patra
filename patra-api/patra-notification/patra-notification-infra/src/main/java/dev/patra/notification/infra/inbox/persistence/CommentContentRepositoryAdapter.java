package dev.patra.notification.infra.inbox.persistence;

import dev.patra.notification.domain.inbox.port.repository.CommentContentRepository;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `CommentContentRepository` 的原生 SQL 实现（`ntf_comment`）。
///
/// 写快照与写墓碑都用 `INSERT … ON CONFLICT DO UPDATE`：无论行在不在，语句结束时这一行都被当前事务锁住，同一条
/// 评论的发表与删除因此串行；版本比较放在 `SET` 的 `CASE` 里，一条语句完成「比较 + 写」。
@Component
@RequiredArgsConstructor
public class CommentContentRepositoryAdapter implements CommentContentRepository {

  private final JdbcClient jdbc;

  /// 写入一版正文，版本更新才覆盖；返回这条评论是否还在（不是墓碑）。
  ///
  /// @param commentId 评论
  /// @param subjectId 所在对象
  /// @param version 这一版的版本
  /// @param plainText 这一版的纯文本全文
  /// @param now 处理时刻
  /// @return 不是墓碑为 `true`
  @Override
  public boolean saveIfNewer(
      long commentId, long subjectId, int version, String plainText, Instant now) {
    return jdbc.sql(
            "INSERT INTO ntf_comment AS c"
                + " (id, comment_id, subject_id, source_version, plain_text, synced_at)"
                + " VALUES (:rowId, :commentId, :subjectId, :version, :plainText, :now)"
                + " ON CONFLICT (comment_id) DO UPDATE SET"
                + " source_version = CASE WHEN c.source_deleted_at IS NULL AND c.source_version < EXCLUDED.source_version"
                + " THEN EXCLUDED.source_version ELSE c.source_version END,"
                + " plain_text = CASE WHEN c.source_deleted_at IS NULL AND c.source_version < EXCLUDED.source_version"
                + " THEN EXCLUDED.plain_text ELSE c.plain_text END,"
                + " synced_at = CASE WHEN c.source_deleted_at IS NULL AND c.source_version < EXCLUDED.source_version"
                + " THEN EXCLUDED.synced_at ELSE c.synced_at END"
                + " RETURNING c.source_deleted_at IS NULL")
        .param("rowId", SnowflakeIdGenerator.getId())
        .param("commentId", commentId)
        .param("subjectId", subjectId)
        .param("version", version)
        .param("plainText", plainText)
        .param("now", InboxRows.utc(now))
        .query(Boolean.class)
        .single();
  }

  /// 标成墓碑，正文置空；已是墓碑时保留第一次的删除时刻。
  ///
  /// @param commentId 评论
  /// @param subjectId 所在对象
  /// @param deletedAt 删除时刻
  /// @param now 处理时刻
  @Override
  public void markDeleted(long commentId, long subjectId, Instant deletedAt, Instant now) {
    jdbc.sql(
            "INSERT INTO ntf_comment AS c"
                + " (id, comment_id, subject_id, source_version, plain_text, source_deleted_at,"
                + " synced_at) VALUES (:rowId, :commentId, :subjectId, 0, NULL, :deletedAt, :now)"
                + " ON CONFLICT (comment_id) DO UPDATE SET plain_text = NULL,"
                + " source_deleted_at = COALESCE(c.source_deleted_at, EXCLUDED.source_deleted_at),"
                + " synced_at = EXCLUDED.synced_at")
        .param("rowId", SnowflakeIdGenerator.getId())
        .param("commentId", commentId)
        .param("subjectId", subjectId)
        .param("deletedAt", InboxRows.utc(deletedAt))
        .param("now", InboxRows.utc(now))
        .update();
  }

  /// 是否墓碑。
  ///
  /// @param commentId 评论
  /// @return 已删除为 `true`
  @Override
  public boolean isDeleted(long commentId) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM ntf_comment"
                + " WHERE comment_id = :commentId AND source_deleted_at IS NOT NULL)")
        .param("commentId", commentId)
        .query(Boolean.class)
        .single();
  }

  /// 删掉一个对象下的全部快照与墓碑。
  ///
  /// @param subjectId 对象
  /// @return 删除的行数
  @Override
  public int deleteBySubject(long subjectId) {
    return jdbc.sql("DELETE FROM ntf_comment WHERE subject_id = :subjectId")
        .param("subjectId", subjectId)
        .update();
  }

  /// 删掉早于截止时刻写入、且没有提醒引用的行（`NOT EXISTS` 走各分区的 `idx_notification_comment`）。
  ///
  /// @param cutoff 截止时刻（不含）
  /// @return 删除的行数
  @Override
  public int purgeUnreferencedBefore(Instant cutoff) {
    return jdbc.sql(
            "DELETE FROM ntf_comment c WHERE c.synced_at < :cutoff AND NOT EXISTS"
                + " (SELECT 1 FROM ntf_notification n WHERE n.comment_id = c.comment_id)")
        .param("cutoff", InboxRows.utc(cutoff))
        .update();
  }
}
