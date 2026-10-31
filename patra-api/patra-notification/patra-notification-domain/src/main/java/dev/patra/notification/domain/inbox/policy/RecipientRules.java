package dev.patra.notification.domain.inbox.policy;

import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.inbox.model.vo.CommentFacts;
import dev.patra.notification.domain.inbox.model.vo.Recipient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 谁该收到提醒（搬自 interaction 的 `NotificationRules`，spec 7.3）。
///
/// 发表时按「被回复的人 → 经验作者（只对经验下的根评论）→ @ 到的人」的顺序列候选，同一人只取第一次出现时的类型，
/// 最后去掉作者本人。顺序保证了「同一条里既回复又 @ 同一人只发一条、归回复我的」。
public final class RecipientRules {

  /// 只有静态方法，不实例化。
  private RecipientRules() {}

  /// 评论已发表时的收件人。
  ///
  /// @param facts 发表时的事实
  /// @return 收件人，按上面的顺序
  public static List<Recipient> forPublished(CommentFacts facts) {
    Map<Long, NotificationType> recipients = new LinkedHashMap<>();
    if (facts.parentAuthorId() != null) {
      recipients.putIfAbsent(facts.parentAuthorId(), NotificationType.COMMENT_REPLY);
    }
    if (facts.rootOnPost()) {
      recipients.putIfAbsent(facts.subjectOwnerId(), NotificationType.POST_COMMENT);
    }
    for (Long mentioned : facts.mentionedUserIds()) {
      recipients.putIfAbsent(mentioned, NotificationType.MENTION);
    }
    recipients.remove(facts.authorId());
    List<Recipient> result = new ArrayList<>();
    recipients.forEach((userId, type) -> result.add(Recipient.of(userId, type)));
    return List.copyOf(result);
  }

  /// 评论已编辑时要发 @ 提醒的人：这条评论第一次 @ 到的人，去掉作者本人，再去掉发表时就会收到提醒的被回复者与
  /// 经验作者。后者由发表负责，编辑不碰他们，结果就与两个事件的到达顺序无关（编辑先到时不会先占成「@ 我的」）。
  ///
  /// @param facts 编辑后的事实
  /// @param addedMentionedUserIds 这条评论第一次 @ 到的人
  /// @return 用户 ID，保序去重
  public static List<Long> forEdited(CommentFacts facts, List<Long> addedMentionedUserIds) {
    Set<Long> added = new LinkedHashSet<>(addedMentionedUserIds);
    added.remove(facts.authorId());
    if (facts.parentAuthorId() != null) {
      added.remove(facts.parentAuthorId());
    }
    if (facts.rootOnPost()) {
      added.remove(facts.subjectOwnerId());
    }
    return List.copyOf(added);
  }
}
