package dev.patra.notification.infra.inbox.persistence;

import dev.patra.notification.domain.inbox.model.aggregate.HelpfulNotification;
import dev.patra.notification.domain.inbox.model.vo.HelpfulMark;
import dev.patra.notification.domain.inbox.model.vo.HelpfulTarget;
import dev.patra.notification.domain.inbox.model.vo.PostText;
import dev.patra.notification.domain.inbox.model.vo.SubjectOwner;
import dev.patra.notification.domain.inbox.port.repository.HelpfulNotificationRepository;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `HelpfulNotificationRepository` 的原生 SQL 实现：合并条目是 `inbox.notification` 的一行（`category = HELPFUL`），
/// 点过的人在附表 `inbox.helpful_actor`（级联外键随信箱行删除）。每条语句都带 `recipient_id`，只扫一个分区。
/// 三个方法都要求调用方的事务（`MANDATORY`）：加锁读要在事务里才有意义，写入与写回各是两条语句（信箱行 + 附表），
/// 事务外调用会留下没有附表行的条目。
@Component
@RequiredArgsConstructor
public class HelpfulNotificationRepositoryAdapter implements HelpfulNotificationRepository {

  /// 按合并键找信箱行并加锁；评论列的条件按目标拼（点经验时为空）。
  private static final String FIND =
      "SELECT notification_id, read_at, post_plain_text, post_plain_text_at"
          + " FROM inbox.notification"
          + " WHERE recipient_id = :recipientId AND category = 'HELPFUL'"
          + " AND subject_id = :subjectId AND helpful_day = :day AND ";

  private final JdbcClient jdbc;

  /// 按合并键找条目并锁住信箱行，再读附表里点过的人。
  ///
  /// @param recipientId 收件人
  /// @param target 被点的内容
  /// @param day 北京时间的那一天
  /// @param owner 所在对象的所有者
  /// @return 条目；没有时为空
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<HelpfulNotification> findForUpdate(
      long recipientId, HelpfulTarget target, LocalDate day, SubjectOwner owner) {
    Long commentId = target.commentId();
    JdbcClient.StatementSpec find =
        jdbc.sql(
                FIND
                    + (commentId == null ? "comment_id IS NULL" : "comment_id = :commentId")
                    + " FOR UPDATE")
            .param("recipientId", recipientId)
            .param("subjectId", target.subjectId())
            .param("day", day);
    if (commentId != null) {
      find = find.param("commentId", commentId);
    }
    return find.query(
            (rs, rowNum) -> {
              Instant textAt = InboxRows.instant(rs, "post_plain_text_at");
              return new Row(
                  rs.getLong("notification_id"),
                  InboxRows.instant(rs, "read_at"),
                  textAt == null ? null : PostText.of(rs.getString("post_plain_text"), textAt));
            })
        .optional()
        .map(
            row ->
                HelpfulNotification.restore(
                    row.notificationId(),
                    recipientId,
                    target,
                    owner,
                    day,
                    marksOf(recipientId, row.notificationId()),
                    row.readAt(),
                    row.postText()));
  }

  /// 写入新条目：信箱行与附表里的点的人。
  ///
  /// @param notification 新条目
  /// @param createdAt 写入信箱的时刻
  /// @return 新条目的 ID
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public long insert(HelpfulNotification notification, Instant createdAt) {
    long id = SnowflakeIdGenerator.getId();
    PostText postText = notification.getPostText();
    jdbc.sql(
            "INSERT INTO inbox.notification (recipient_id, notification_id, category, type,"
                + " actor_id, actor_anonymous, subject_id, comment_id, occurred_at, created_at,"
                + " read_at, helpful_day, post_plain_text, post_plain_text_at)"
                + " VALUES (:recipientId, :id, :category, :type, :actorId, :actorAnonymous,"
                + " :subjectId, :commentId, :occurredAt, :createdAt, :readAt, :day, :text, :textAt)")
        .param("recipientId", notification.getRecipientId())
        .param("id", id)
        .param("category", NotificationCategory.HELPFUL.name())
        .param("type", notification.getTarget().type().notificationType().name())
        .param("actorId", notification.getLatestActorId())
        .param("actorAnonymous", notification.isLatestActorAnonymous())
        .param("subjectId", notification.getTarget().subjectId())
        .param("commentId", notification.getTarget().commentId())
        .param("occurredAt", InboxRows.utc(notification.getOccurredAt()))
        .param("createdAt", InboxRows.utc(createdAt))
        .param(
            "readAt",
            notification.getReadAt() == null ? null : InboxRows.utc(notification.getReadAt()))
        .param("day", notification.getDay())
        .param("text", postText == null ? null : postText.value())
        .param("textAt", postText == null ? null : InboxRows.utc(postText.markedAt()))
        .update();
    notification
        .getMarks()
        .forEach((actorId, at) -> upsertActor(notification.getRecipientId(), id, actorId, at));
    return id;
  }

  /// 写回并入一次点击后的条目。
  ///
  /// @param notification 条目
  /// @param mark 刚并入的点击
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void update(HelpfulNotification notification, HelpfulMark mark) {
    long recipientId = notification.getRecipientId();
    long id = notification.getNotificationId();
    upsertActor(recipientId, id, mark.actorId(), notification.getMarks().get(mark.actorId()));
    PostText postText = notification.getPostText();
    jdbc.sql(
            "UPDATE inbox.notification SET actor_id = :actorId, actor_anonymous = :actorAnonymous,"
                + " occurred_at = :occurredAt, read_at = :readAt, post_plain_text = :text,"
                + " post_plain_text_at = :textAt"
                + " WHERE recipient_id = :recipientId AND notification_id = :id")
        .param("actorId", notification.getLatestActorId())
        .param("actorAnonymous", notification.isLatestActorAnonymous())
        .param("occurredAt", InboxRows.utc(notification.getOccurredAt()))
        .param(
            "readAt",
            notification.getReadAt() == null ? null : InboxRows.utc(notification.getReadAt()))
        .param("text", postText == null ? null : postText.value())
        .param("textAt", postText == null ? null : InboxRows.utc(postText.markedAt()))
        .param("recipientId", recipientId)
        .param("id", id)
        .update();
  }

  /// 附表写一个人：没有就插入，有了取较早的时刻。
  ///
  /// @param recipientId 收件人
  /// @param notificationId 条目
  /// @param actorId 点的人
  /// @param firstMarkedAt 他第一次点的时刻
  private void upsertActor(
      long recipientId, long notificationId, long actorId, Instant firstMarkedAt) {
    jdbc.sql(
            "INSERT INTO inbox.helpful_actor (recipient_id, notification_id, actor_id,"
                + " first_marked_at) VALUES (:recipientId, :id, :actorId, :at)"
                + " ON CONFLICT (recipient_id, notification_id, actor_id) DO UPDATE"
                + " SET first_marked_at = LEAST(inbox.helpful_actor.first_marked_at,"
                + " EXCLUDED.first_marked_at)")
        .param("recipientId", recipientId)
        .param("id", notificationId)
        .param("actorId", actorId)
        .param("at", InboxRows.utc(firstMarkedAt))
        .update();
  }

  /// 读一个条目里点过的人。
  ///
  /// @param recipientId 收件人
  /// @param notificationId 条目
  /// @return 点的人 → 第一次点的时刻
  private Map<Long, Instant> marksOf(long recipientId, long notificationId) {
    Map<Long, Instant> marks = new HashMap<>();
    jdbc.sql(
            "SELECT actor_id, first_marked_at FROM inbox.helpful_actor"
                + " WHERE recipient_id = :recipientId AND notification_id = :id")
        .param("recipientId", recipientId)
        .param("id", notificationId)
        .query(
            rs -> {
              marks.put(rs.getLong("actor_id"), InboxRows.instant(rs, "first_marked_at"));
            });
    return marks;
  }

  /// 信箱行上恢复聚合要的列。
  ///
  /// @param notificationId 条目
  /// @param readAt 已读时刻
  /// @param postText 经验正文
  private record Row(long notificationId, Instant readAt, PostText postText) {}
}
