package dev.patra.notification.infra.inbox.persistence;

import dev.patra.notification.domain.inbox.model.enums.MarkReadOutcome;
import dev.patra.notification.domain.inbox.model.read.CreatedNotification;
import dev.patra.notification.domain.inbox.model.vo.NotificationDraft;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `NotificationRepository` 的原生 SQL 实现（`inbox.notification`，按收件人哈希分区）。写方法不开事务，由 app 层的
/// 事务包住。
@Component
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {

  /// 一行的列（插入顺序）。
  private static final String COLUMNS =
      "recipient_id, notification_id, category, type, actor_id, actor_anonymous, subject_id,"
          + " comment_id, occurred_at, created_at";

  private final JdbcClient jdbc;

  /// 多行插入；同一收件人同一评论已有回复或 @ 提醒时跳过（`uk_notification_comment`）。
  ///
  /// @param drafts 待写入的提醒
  /// @return 真写入的提醒
  @Override
  public List<CreatedNotification> insertAll(List<NotificationDraft> drafts) {
    if (drafts.isEmpty()) {
      return List.of();
    }
    List<String> rows = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();
    for (int i = 0; i < drafts.size(); i++) {
      NotificationDraft draft = drafts.get(i);
      rows.add(
          "(:r%1$d, :id%1$d, :category%1$d, :type%1$d, :actor%1$d, :anon%1$d, :subject%1$d,"
                  .formatted(i)
              + " :comment%1$d, :occurred%1$d, :created%1$d)".formatted(i));
      params.put("r" + i, draft.recipientId());
      params.put("id" + i, SnowflakeIdGenerator.getId());
      params.put("category" + i, draft.type().category().name());
      params.put("type" + i, draft.type().name());
      params.put("actor" + i, draft.actorId());
      params.put("anon" + i, draft.actorAnonymous());
      params.put("subject" + i, draft.subjectId());
      params.put("comment" + i, draft.commentId());
      params.put("occurred" + i, InboxRows.utc(draft.occurredAt()));
      params.put("created" + i, InboxRows.utc(draft.createdAt()));
    }
    return jdbc.sql(
            "INSERT INTO inbox.notification ("
                + COLUMNS
                + ") VALUES "
                + String.join(", ", rows)
                + " ON CONFLICT (recipient_id, comment_id) WHERE category IN ('REPLY', 'MENTION')"
                + " DO NOTHING"
                + " RETURNING notification_id, recipient_id, category, occurred_at, created_at")
        .params(params)
        .query(
            (rs, rowNum) ->
                CreatedNotification.builder()
                    .notificationId(rs.getLong("notification_id"))
                    .recipientId(rs.getLong("recipient_id"))
                    .category(NotificationCategory.valueOf(rs.getString("category")))
                    .occurredAt(InboxRows.instant(rs, "occurred_at"))
                    .createdAt(InboxRows.instant(rs, "created_at"))
                    .build())
        .list();
  }

  /// 删掉一条评论的全部提醒（走 `idx_notification_comment`）。
  ///
  /// @param commentId 评论
  /// @return 被删了提醒的收件人
  @Override
  public Set<Long> deleteByComment(long commentId) {
    return jdbc.sql(
            "DELETE FROM inbox.notification WHERE comment_id = :commentId RETURNING recipient_id")
        .param("commentId", commentId)
        .query(Long.class)
        .set();
  }

  /// 删掉一个对象下的全部提醒（走 `idx_notification_subject`）。
  ///
  /// @param subjectId 对象
  /// @return 被删了提醒的收件人
  @Override
  public Set<Long> deleteBySubject(long subjectId) {
    return jdbc.sql(
            "DELETE FROM inbox.notification WHERE subject_id = :subjectId RETURNING recipient_id")
        .param("subjectId", subjectId)
        .query(Long.class)
        .set();
  }

  /// 只把未读的标成已读；没标到时再查这条在不在，分出「本来已读」与「不存在」。
  ///
  /// @param recipientId 收件人
  /// @param notificationId 提醒
  /// @param readAt 已读时刻
  /// @return 结果
  @Override
  public MarkReadOutcome markRead(long recipientId, long notificationId, Instant readAt) {
    int marked =
        jdbc.sql(
                "UPDATE inbox.notification SET read_at = :readAt"
                    + " WHERE recipient_id = :recipientId AND notification_id = :id"
                    + " AND read_at IS NULL")
            .param("readAt", InboxRows.utc(readAt))
            .param("recipientId", recipientId)
            .param("id", notificationId)
            .update();
    if (marked == 1) {
      return MarkReadOutcome.MARKED;
    }
    boolean exists =
        jdbc.sql(
                "SELECT EXISTS (SELECT 1 FROM inbox.notification"
                    + " WHERE recipient_id = :recipientId AND notification_id = :id)")
            .param("recipientId", recipientId)
            .param("id", notificationId)
            .query(Boolean.class)
            .single();
    return exists ? MarkReadOutcome.ALREADY_READ : MarkReadOutcome.NOT_FOUND;
  }

  /// 全部已读（走 `idx_notification_unread`）。
  ///
  /// @param recipientId 收件人
  /// @param readAt 已读时刻
  /// @return 标记的条数
  @Override
  public int markAllRead(long recipientId, Instant readAt) {
    return jdbc.sql(
            "UPDATE inbox.notification SET read_at = :readAt"
                + " WHERE recipient_id = :recipientId AND read_at IS NULL")
        .param("readAt", InboxRows.utc(readAt))
        .param("recipientId", recipientId)
        .update();
  }

  /// 删除排序时间早于截止时刻的。
  ///
  /// @param cutoff 截止时刻（不含）
  /// @return 删除的条数
  @Override
  public int deleteOccurredBefore(Instant cutoff) {
    return jdbc.sql("DELETE FROM inbox.notification WHERE occurred_at < :cutoff")
        .param("cutoff", InboxRows.utc(cutoff))
        .update();
  }

  /// 删除收件人的全部提醒。
  ///
  /// @param recipientId 收件人
  /// @return 删除的条数
  @Override
  public int deleteAllOf(long recipientId) {
    return jdbc.sql("DELETE FROM inbox.notification WHERE recipient_id = :recipientId")
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
            "SELECT DISTINCT recipient_id FROM inbox.notification"
                + " WHERE recipient_id > :after ORDER BY recipient_id LIMIT :limit")
        .param("after", afterUserId)
        .param("limit", limit)
        .query(Long.class)
        .list();
  }
}
