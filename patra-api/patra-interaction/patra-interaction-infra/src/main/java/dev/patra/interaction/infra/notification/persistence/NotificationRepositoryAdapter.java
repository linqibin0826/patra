package dev.patra.interaction.infra.notification.persistence;

import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.model.vo.NotificationDraft;
import dev.patra.interaction.domain.notification.port.repository.NotificationRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `NotificationRepository` 的原生 SQL 实现。
@Component
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {

  /// 一行的列（插入顺序）。
  private static final String COLUMNS =
      "recipient_id, notification_id, type, actor_id, actor_anonymous, subject_id, comment_id,"
          + " excerpt, excerpt_truncated, created_at";

  private final JdbcClient jdbc;

  /// 多行插入；同一收件人同一评论已有提醒时跳过。
  ///
  /// @param drafts 待写入的提醒
  /// @return 新写入的条数
  @Override
  public int insertAll(List<NotificationDraft> drafts) {
    if (drafts.isEmpty()) {
      return 0;
    }
    List<String> rows = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();
    for (int i = 0; i < drafts.size(); i++) {
      NotificationDraft draft = drafts.get(i);
      rows.add(
          "(:r%1$d, :id%1$d, :type%1$d, :actor%1$d, :anon%1$d, :subject%1$d, :comment%1$d,"
                  .formatted(i)
              + " :excerpt%1$d, :truncated%1$d, :at%1$d)".formatted(i));
      params.put("r" + i, draft.recipientId());
      params.put("id" + i, SnowflakeIdGenerator.getId());
      params.put("type" + i, draft.type().name());
      params.put("actor" + i, draft.actorId());
      params.put("anon" + i, draft.actorAnonymous());
      params.put("subject" + i, draft.subjectId().value());
      params.put("comment" + i, draft.commentId());
      params.put("excerpt" + i, draft.excerpt().stored());
      params.put("truncated" + i, draft.excerpt().truncated());
      params.put("at" + i, NotificationRows.utc(draft.createdAt()));
    }
    return jdbc.sql(
            "INSERT INTO notification.notification ("
                + COLUMNS
                + ") VALUES "
                + String.join(", ", rows)
                + " ON CONFLICT (recipient_id, comment_id) WHERE comment_id IS NOT NULL DO NOTHING")
        .params(params)
        .update();
  }

  /// 刷新一条评论的全部提醒的摘录。
  ///
  /// @param commentId 评论
  /// @param excerpt 新摘录
  /// @return 更新的条数
  @Override
  public int refreshExcerpt(long commentId, ExcerptText excerpt) {
    return jdbc.sql(
            "UPDATE notification.notification SET excerpt = :excerpt, excerpt_truncated = :truncated"
                + " WHERE comment_id = :commentId")
        .param("excerpt", excerpt.stored())
        .param("truncated", excerpt.truncated())
        .param("commentId", commentId)
        .update();
  }

  /// 删掉一条评论的全部提醒。
  ///
  /// @param commentId 评论
  /// @return 删除的条数
  @Override
  public int deleteByComment(long commentId) {
    return jdbc.sql("DELETE FROM notification.notification WHERE comment_id = :commentId")
        .param("commentId", commentId)
        .update();
  }

  /// 删掉一个对象下的全部提醒（走 `idx_notification_subject`）。
  ///
  /// @param subjectId 对象
  /// @return 删除的条数
  @Override
  public int deleteBySubject(SubjectId subjectId) {
    return jdbc.sql("DELETE FROM notification.notification WHERE subject_id = :subjectId")
        .param("subjectId", subjectId.value())
        .update();
  }

  /// 标一条为已读；已读的保持原来的时刻。
  ///
  /// @param recipientId 收件人
  /// @param notificationId 提醒
  /// @param readAt 已读时刻
  /// @return 存在且属于收件人时为 `true`
  @Override
  public boolean markRead(long recipientId, long notificationId, Instant readAt) {
    return jdbc.sql(
                "UPDATE notification.notification SET read_at = COALESCE(read_at, :readAt)"
                    + " WHERE recipient_id = :recipientId AND notification_id = :id")
            .param("readAt", NotificationRows.utc(readAt))
            .param("recipientId", recipientId)
            .param("id", notificationId)
            .update()
        == 1;
  }

  /// 全部已读。
  ///
  /// @param recipientId 收件人
  /// @param readAt 已读时刻
  /// @return 标记的条数
  @Override
  public int markAllRead(long recipientId, Instant readAt) {
    return jdbc.sql(
            "UPDATE notification.notification SET read_at = :readAt"
                + " WHERE recipient_id = :recipientId AND read_at IS NULL")
        .param("readAt", NotificationRows.utc(readAt))
        .param("recipientId", recipientId)
        .update();
  }

  /// 删除截止时刻之前生成的。
  ///
  /// @param cutoff 截止时刻（不含）
  /// @return 删除的条数
  @Override
  public int deleteCreatedBefore(Instant cutoff) {
    return jdbc.sql("DELETE FROM notification.notification WHERE created_at < :cutoff")
        .param("cutoff", NotificationRows.utc(cutoff))
        .update();
  }

  /// 删除收件人的全部提醒。
  ///
  /// @param recipientId 收件人
  /// @return 删除的条数
  @Override
  public int deleteAllOf(long recipientId) {
    return jdbc.sql("DELETE FROM notification.notification WHERE recipient_id = :recipientId")
        .param("recipientId", recipientId)
        .update();
  }

  /// 有提醒的收件人。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID，升序
  @Override
  public List<Long> findRecipientsAfter(long afterUserId, int limit) {
    return jdbc.sql(
            "SELECT DISTINCT recipient_id FROM notification.notification"
                + " WHERE recipient_id > :after ORDER BY recipient_id LIMIT :limit")
        .param("after", afterUserId)
        .param("limit", limit)
        .query(Long.class)
        .list();
  }
}
