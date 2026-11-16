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

/// `NotificationRepository` 的原生 SQL 实现（`ntf_notification`，按收件人哈希分区）。写方法不开事务，由 app 层的
/// 事务包住。
@Component
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {

  /// 一行的列（插入顺序）。
  private static final String COLUMNS =
      "recipient_user_id, id, category, type, actor_user_id, actor_anonymous, subject_id,"
          + " comment_id, occurred_at, notified_at";

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
            "INSERT INTO ntf_notification ("
                + COLUMNS
                + ") VALUES "
                + String.join(", ", rows)
                + " ON CONFLICT (recipient_user_id, comment_id) WHERE category IN ('REPLY', 'MENTION')"
                + " DO NOTHING"
                + " RETURNING id, recipient_user_id, category, occurred_at, notified_at")
        .params(params)
        .query(
            (rs, rowNum) ->
                CreatedNotification.builder()
                    .notificationId(rs.getLong("id"))
                    .recipientId(rs.getLong("recipient_user_id"))
                    .category(NotificationCategory.valueOf(rs.getString("category")))
                    .occurredAt(InboxRows.instant(rs, "occurred_at"))
                    .createdAt(InboxRows.instant(rs, "notified_at"))
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
            "WITH removed AS (DELETE FROM ntf_notification WHERE comment_id = :commentId RETURNING recipient_user_id, id),"
                + " actors AS (DELETE FROM ntf_notification_helpful_actor a USING removed r"
                + " WHERE a.recipient_user_id = r.recipient_user_id AND a.notification_id = r.id)"
                + " SELECT recipient_user_id FROM removed")
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
            "WITH removed AS (DELETE FROM ntf_notification WHERE subject_id = :subjectId RETURNING recipient_user_id, id),"
                + " actors AS (DELETE FROM ntf_notification_helpful_actor a USING removed r"
                + " WHERE a.recipient_user_id = r.recipient_user_id AND a.notification_id = r.id)"
                + " SELECT recipient_user_id FROM removed")
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
                "UPDATE ntf_notification SET read_at = :readAt, updated_by = :recipientId"
                    + " WHERE recipient_user_id = :recipientId AND id = :id"
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
                "SELECT EXISTS (SELECT 1 FROM ntf_notification"
                    + " WHERE recipient_user_id = :recipientId AND id = :id)")
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
            "UPDATE ntf_notification SET read_at = :readAt, updated_by = :recipientId"
                + " WHERE recipient_user_id = :recipientId AND read_at IS NULL")
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
    return jdbc.sql(
            "WITH removed AS (DELETE FROM ntf_notification WHERE occurred_at < :cutoff RETURNING recipient_user_id, id),"
                + " actors AS (DELETE FROM ntf_notification_helpful_actor a USING removed r"
                + " WHERE a.recipient_user_id = r.recipient_user_id AND a.notification_id = r.id)"
                + " SELECT count(*) FROM removed")
        .param("cutoff", InboxRows.utc(cutoff))
        .query(Integer.class)
        .single();
  }

  /// 删除收件人的全部提醒。
  ///
  /// @param recipientId 收件人
  /// @return 删除的条数
  @Override
  public int deleteAllOf(long recipientId) {
    return jdbc.sql(
            "WITH removed AS (DELETE FROM ntf_notification WHERE recipient_user_id = :recipientId RETURNING recipient_user_id, id),"
                + " actors AS (DELETE FROM ntf_notification_helpful_actor a USING removed r"
                + " WHERE a.recipient_user_id = r.recipient_user_id AND a.notification_id = r.id)"
                + " SELECT count(*) FROM removed")
        .param("recipientId", recipientId)
        .query(Integer.class)
        .single();
  }

  /// 有提醒的收件人。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID，升序
  @Override
  public List<Long> findRecipientsAfter(long afterUserId, int limit) {
    return jdbc.sql(
            "SELECT DISTINCT recipient_user_id FROM ntf_notification"
                + " WHERE recipient_user_id > :after ORDER BY recipient_user_id LIMIT :limit")
        .param("after", afterUserId)
        .param("limit", limit)
        .query(Long.class)
        .list();
  }
}
