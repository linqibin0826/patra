package dev.patra.interaction.domain.notification.policy;

import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.notification.model.vo.Recipient;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 谁该收到提醒（spec 4.2、4.3）。
///
/// 发表时按「被回复的人 → 经验作者（只对根评论）→ @ 到的人」的顺序列候选，同一人只取第一次出现时的类型，
/// 最后去掉作者本人。顺序保证了「同一条里既回复又 @ 同一人只发一条、归回复我的」。
public final class NotificationRules {

  /// 只有静态方法，不实例化。
  private NotificationRules() {}

  /// 评论已发表时的收件人。
  ///
  /// @param event 事件
  /// @return 收件人，按上面的顺序
  public static List<Recipient> forPublished(CommentPublishedEvent event) {
    Map<Long, NotificationType> recipients = new LinkedHashMap<>();
    if (event.parentAuthorId() != null) {
      recipients.putIfAbsent(event.parentAuthorId(), NotificationType.COMMENT_REPLY);
    }
    if (event.parentCommentId() == null
        && event.subjectType() == SubjectType.POST
        && event.subjectOwner() != null) {
      recipients.putIfAbsent(event.subjectOwner().userId(), NotificationType.POST_COMMENT);
    }
    for (Long mentioned : event.mentionedUserIds()) {
      recipients.putIfAbsent(mentioned, NotificationType.MENTION);
    }
    recipients.remove(event.authorId());
    List<Recipient> result = new ArrayList<>();
    recipients.forEach((userId, type) -> result.add(Recipient.of(userId, type)));
    return List.copyOf(result);
  }

  /// 评论已编辑时要发 @ 提醒的人：这条评论第一次 @ 到的人，去掉作者本人。已经因这条评论收过提醒的人由存储的唯一索引挡掉。
  ///
  /// @param event 事件
  /// @return 用户 ID，保序去重
  public static List<Long> forEdited(CommentEditedEvent event) {
    Set<Long> added = new LinkedHashSet<>(event.addedMentionedUserIds());
    added.remove(event.authorId());
    return List.copyOf(added);
  }
}
