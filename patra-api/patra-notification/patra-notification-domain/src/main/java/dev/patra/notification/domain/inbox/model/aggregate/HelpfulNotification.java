package dev.patra.notification.domain.inbox.model.aggregate;

import dev.patra.notification.domain.inbox.model.enums.HelpfulChange;
import dev.patra.notification.domain.inbox.model.enums.HelpfulTargetType;
import dev.patra.notification.domain.inbox.model.vo.HelpfulMark;
import dev.patra.notification.domain.inbox.model.vo.HelpfulTarget;
import dev.patra.notification.domain.inbox.model.vo.PostText;
import dev.patra.notification.domain.inbox.model.vo.SubjectOwner;
import dev.patra.notification.domain.inbox.policy.HelpfulDay;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;

/// 有帮助的合并条目：同一条内容在北京时间同一天被点的所有次数合成一条（PAP-144 spec 6）。
///
/// 记的是「点过的人 → 他第一次点的时刻」：人数 = 人数；最近一位 = 第一次点得最晚的人，条目的时间就是这个时刻。
/// 同一个人的第二条消息（取消后再点）不算又有人点。合并只取每人的最小时刻、正文只取最晚的一条，所以结果与消息
/// 到达的先后、重复几次都无关。
@Getter
public final class HelpfulNotification {

  /// 最近一位的比较：先比第一次点的时刻，同一时刻比用户 ID，保证两种到达顺序选出同一个人。
  private static final Comparator<Map.Entry<Long, Instant>> LATEST =
      Map.Entry.<Long, Instant>comparingByValue().thenComparing(Map.Entry.comparingByKey());

  private final Long notificationId;
  private final long recipientId;
  private final HelpfulTarget target;
  private final SubjectOwner owner;
  private final LocalDate day;

  @Getter(AccessLevel.NONE)
  private final Map<Long, Instant> marks;

  private Instant readAt;
  private PostText postText;

  /// 构造。
  ///
  /// @param notificationId 信箱行 ID；还没写入时为空
  /// @param recipientId 收件人（被点内容的作者）
  /// @param target 被点的内容
  /// @param owner 所在对象的所有者
  /// @param day 北京时间的那一天
  /// @param marks 点过的人 → 第一次点的时刻，至少一个
  /// @param readAt 已读时刻，可空
  /// @param postText 点经验时的正文；点评论时为空
  private HelpfulNotification(
      Long notificationId,
      long recipientId,
      HelpfulTarget target,
      SubjectOwner owner,
      LocalDate day,
      Map<Long, Instant> marks,
      Instant readAt,
      PostText postText) {
    this.notificationId = notificationId;
    this.recipientId = recipientId;
    this.target = Objects.requireNonNull(target, "target 不能为 null");
    this.owner = Objects.requireNonNull(owner, "owner 不能为 null");
    this.day = Objects.requireNonNull(day, "day 不能为 null");
    if (marks.isEmpty()) {
      throw new IllegalArgumentException("合并条目至少有一个点的人");
    }
    this.marks = new HashMap<>(marks);
    this.readAt = readAt;
    this.postText = postText;
  }

  /// 这一天这条内容的第一次点击：新建一条未读的条目。
  ///
  /// @param recipientId 收件人
  /// @param target 被点的内容
  /// @param owner 所在对象的所有者
  /// @param mark 这次点击
  /// @param plainText 事件带来的正文：点经验时存进条目，点评论时忽略（评论正文在评论快照里）
  /// @return 条目
  public static HelpfulNotification open(
      long recipientId,
      HelpfulTarget target,
      SubjectOwner owner,
      HelpfulMark mark,
      String plainText) {
    PostText postText =
        target.type() == HelpfulTargetType.POST ? PostText.of(plainText, mark.markedAt()) : null;
    return new HelpfulNotification(
        null,
        recipientId,
        target,
        owner,
        HelpfulDay.of(mark.markedAt()),
        Map.of(mark.actorId(), mark.markedAt()),
        null,
        postText);
  }

  /// 从库里恢复。
  ///
  /// @param notificationId 信箱行 ID
  /// @param recipientId 收件人
  /// @param target 被点的内容
  /// @param owner 所在对象的所有者（来自这次事件：同一条内容恒定）
  /// @param day 北京时间的那一天
  /// @param marks 点过的人 → 第一次点的时刻
  /// @param readAt 已读时刻，可空
  /// @param postText 点经验时的正文；点评论时为空
  /// @return 条目
  public static HelpfulNotification restore(
      long notificationId,
      long recipientId,
      HelpfulTarget target,
      SubjectOwner owner,
      LocalDate day,
      Map<Long, Instant> marks,
      Instant readAt,
      PostText postText) {
    return new HelpfulNotification(
        notificationId, recipientId, target, owner, day, marks, readAt, postText);
  }

  /// 并入一次点击（spec 6.2）：
  ///
  /// - 新的人：加进来、重新未读（最近一位按时刻重算，他更早就不换）；
  /// - 已在里面、这次更早（他较早那次的消息晚到）：把他的时刻改早，不重新未读；
  /// - 已在里面、这次不早于记下的（取消后再点）：人这边什么都不变；
  /// - 点经验时，这次比条目上的正文晚就换正文（与上面独立）。
  ///
  /// @param mark 这次点击
  /// @param plainText 事件带来的正文（点经验时用）
  /// @return 条目变了没有
  public HelpfulChange absorb(HelpfulMark mark, String plainText) {
    Instant previous = marks.get(mark.actorId());
    boolean peopleChanged = previous == null || mark.markedAt().isBefore(previous);
    if (peopleChanged) {
      marks.put(mark.actorId(), mark.markedAt());
    }
    if (previous == null) {
      readAt = null;
    }
    PostText advanced = postText == null ? null : postText.advance(plainText, mark.markedAt());
    boolean textStored = advanced != postText;
    boolean textShown = textStored && !Objects.equals(advanced.value(), postText.value());
    postText = advanced;
    if (peopleChanged || textShown) {
      return HelpfulChange.VISIBLE;
    }
    return textStored ? HelpfulChange.STORED_ONLY : HelpfulChange.NONE;
  }

  /// 点过的人数（取消不减）。
  ///
  /// @return 人数
  public int getActorCount() {
    return marks.size();
  }

  /// 最近一位：第一次点得最晚的人。
  ///
  /// @return 用户 ID
  public long getLatestActorId() {
    return latest().getKey();
  }

  /// 条目的时间（排序、分组与行尾时间）：最近一位第一次点的时刻。
  ///
  /// @return 时刻
  public Instant getOccurredAt() {
    return latest().getValue();
  }

  /// 最近一位要不要显示成「匿名作者」（他是所在匿名经验的作者本人，spec 6.3）。
  ///
  /// @return 要藏起来为 `true`
  public boolean isLatestActorAnonymous() {
    return owner.hides(getLatestActorId());
  }

  /// 点过的人与各自第一次点的时刻（只读副本）。
  ///
  /// @return 映射
  public Map<Long, Instant> getMarks() {
    return Map.copyOf(marks);
  }

  /// 第一次点得最晚的那一项。
  ///
  /// @return 用户 ID 与时刻
  private Map.Entry<Long, Instant> latest() {
    return marks.entrySet().stream().max(LATEST).orElseThrow();
  }
}
