package dev.patra.notification.domain.inbox.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.inbox.model.enums.HelpfulChange;
import dev.patra.notification.domain.inbox.model.enums.HelpfulTargetType;
import dev.patra.notification.domain.inbox.model.vo.HelpfulMark;
import dev.patra.notification.domain.inbox.model.vo.HelpfulTarget;
import dev.patra.notification.domain.inbox.model.vo.SubjectOwner;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// 有帮助的合并条目（PAP-144 spec 6.2）：每个人只算第一次点，最近一位是第一次点得最晚的人，新的人让条目重新未读。
class HelpfulNotificationTest {

  private static final long AUTHOR = 101L;
  private static final long C = 11L;
  private static final long D = 12L;
  private static final long E = 13L;
  private static final Instant T0 = Instant.parse("2026-10-31T06:00:00Z");
  private static final Instant READ_AT = Instant.parse("2026-10-31T07:00:00Z");
  private static final HelpfulTarget POST = HelpfulTarget.of(HelpfulTargetType.POST, 55L, 41L);
  private static final SubjectOwner OWNER = SubjectOwner.of(AUTHOR, false);

  /// 第一次点：一个人、未读，时间就是他点的时刻，日期是北京时间那一天。
  @Test
  void open_creates_unread_entry_with_one_actor() {
    HelpfulNotification entry = HelpfulNotification.open(AUTHOR, POST, OWNER, mark(C, 0), "正文");

    assertThat(entry.getActorCount()).isEqualTo(1);
    assertThat(entry.getLatestActorId()).isEqualTo(C);
    assertThat(entry.getOccurredAt()).isEqualTo(T0);
    assertThat(entry.getReadAt()).isNull();
    assertThat(entry.getDay()).isEqualTo(LocalDate.of(2026, 10, 31));
    assertThat(entry.getNotificationId()).isNull();
  }

  /// 已读后新的人更晚点：人数加一、换成他、时间改成他的、重新未读。
  @Test
  void new_later_actor_becomes_latest_and_reunreads() {
    HelpfulNotification entry = readEntry(Map.of(C, T0));

    HelpfulChange change = entry.absorb(mark(D, 5), "正文");

    assertThat(change).isEqualTo(HelpfulChange.VISIBLE);
    assertThat(entry.getActorCount()).isEqualTo(2);
    assertThat(entry.getLatestActorId()).isEqualTo(D);
    assertThat(entry.getOccurredAt()).isEqualTo(T0.plusSeconds(300));
    assertThat(entry.getReadAt()).isNull();
  }

  /// 新的人的点击比最近一位早（他的消息晚到）：人数加一、重新未读，名字与时间不变。
  @Test
  void new_earlier_actor_adds_count_but_keeps_latest() {
    HelpfulNotification entry = readEntry(Map.of(D, T0.plusSeconds(300)));

    HelpfulChange change = entry.absorb(mark(C, 0), "正文");

    assertThat(change).isEqualTo(HelpfulChange.VISIBLE);
    assertThat(entry.getActorCount()).isEqualTo(2);
    assertThat(entry.getLatestActorId()).isEqualTo(D);
    assertThat(entry.getOccurredAt()).isEqualTo(T0.plusSeconds(300));
    assertThat(entry.getReadAt()).isNull();
  }

  /// 同一个人更晚再点（取消后再点）：什么都不变，已读的仍是已读。
  @Test
  void same_actor_later_mark_changes_nothing() {
    HelpfulNotification entry = readEntry(Map.of(C, T0, D, T0.plusSeconds(300)));

    HelpfulChange change = entry.absorb(mark(C, 9), "正文");

    assertThat(change).isEqualTo(HelpfulChange.NONE);
    assertThat(entry.getActorCount()).isEqualTo(2);
    assertThat(entry.getLatestActorId()).isEqualTo(D);
    assertThat(entry.getReadAt()).isEqualTo(READ_AT);
  }

  /// 同一个人较早那次点击的消息最后才到：他的时刻改早，最近一位随之重算（这里换成 D）；人没变多，不重新未读。
  @Test
  void same_actor_earlier_mark_moves_his_time_back() {
    HelpfulNotification entry = readEntry(Map.of(C, T0.plusSeconds(180), D, T0.plusSeconds(120)));

    HelpfulChange change = entry.absorb(mark(C, 0), "正文");

    assertThat(change).isEqualTo(HelpfulChange.VISIBLE);
    assertThat(entry.getLatestActorId()).isEqualTo(D);
    assertThat(entry.getOccurredAt()).isEqualTo(T0.plusSeconds(120));
    assertThat(entry.getMarks()).containsEntry(C, T0);
    assertThat(entry.getReadAt()).isEqualTo(READ_AT);
  }

  /// 两个人在同一时刻点：取用户 ID 大的，两种到达顺序结果相同。
  @Test
  void same_instant_ties_break_on_larger_user_id() {
    HelpfulNotification cFirst = HelpfulNotification.open(AUTHOR, POST, OWNER, mark(C, 0), null);
    cFirst.absorb(mark(D, 0), null);
    HelpfulNotification dFirst = HelpfulNotification.open(AUTHOR, POST, OWNER, mark(D, 0), null);
    dFirst.absorb(mark(C, 0), null);

    assertThat(cFirst.getLatestActorId()).isEqualTo(D);
    assertThat(dFirst.getLatestActorId()).isEqualTo(D);
  }

  /// 经验正文以点击时刻最晚的那条为准，与谁是最近一位无关；只推进时刻、正文没变时只需写库。
  @Test
  void post_text_follows_latest_event_time() {
    HelpfulNotification entry = HelpfulNotification.open(AUTHOR, POST, OWNER, mark(C, 0), "旧");

    assertThat(entry.absorb(mark(D, 5), "新")).isEqualTo(HelpfulChange.VISIBLE);
    assertThat(entry.getPostText().value()).isEqualTo("新");

    assertThat(entry.absorb(mark(C, 9), "更新")).isEqualTo(HelpfulChange.VISIBLE);
    assertThat(entry.getPostText().value()).isEqualTo("更新");
    assertThat(entry.getLatestActorId()).isEqualTo(D);

    assertThat(entry.absorb(mark(C, 10), "更新")).isEqualTo(HelpfulChange.STORED_ONLY);
    assertThat(entry.getPostText().markedAt()).isEqualTo(T0.plusSeconds(600));

    entry.absorb(mark(D, 1), "很旧");
    assertThat(entry.getPostText().value()).isEqualTo("更新");
  }

  /// 点评论的条目不存正文（评论正文在评论快照里）。
  @Test
  void comment_target_keeps_no_post_text() {
    HelpfulTarget comment = HelpfulTarget.of(HelpfulTargetType.COMMENT, 500L, 40L);
    HelpfulNotification entry =
        HelpfulNotification.open(AUTHOR, comment, OWNER, mark(C, 0), "评论正文");

    entry.absorb(mark(D, 5), "评论正文");

    assertThat(entry.getPostText()).isNull();
  }

  /// 匿名经验的作者点了自己经验下别人的评论：他是最近一位时藏起来，别人更晚点了之后最近一位换人，不再藏。
  @Test
  void anonymous_owner_is_hidden_only_while_latest() {
    HelpfulTarget comment = HelpfulTarget.of(HelpfulTargetType.COMMENT, 500L, 41L);
    SubjectOwner anonymousOwner = SubjectOwner.of(C, true);
    HelpfulNotification entry =
        HelpfulNotification.open(202L, comment, anonymousOwner, mark(C, 0), "x");

    assertThat(entry.isLatestActorAnonymous()).isTrue();

    entry.absorb(mark(D, 5), "x");

    assertThat(entry.isLatestActorAnonymous()).isFalse();
  }

  /// 同一组点击（含同一人取消前后的两次与一次重复）按任意顺序并入，人数、最近一位、时间、各人的时刻、正文都相同。
  @Test
  void result_is_independent_of_arrival_order() {
    List<HelpfulMark> marks = List.of(mark(C, 1), mark(C, 3), mark(D, 2), mark(E, 4), mark(D, 2));
    HelpfulNotification expected = replay(marks);

    for (List<HelpfulMark> order : permutations(marks)) {
      HelpfulNotification actual = replay(order);

      assertThat(actual.getActorCount()).as("顺序 %s", order).isEqualTo(3);
      assertThat(actual.getLatestActorId()).as("顺序 %s", order).isEqualTo(E);
      assertThat(actual.getOccurredAt()).as("顺序 %s", order).isEqualTo(expected.getOccurredAt());
      assertThat(actual.getMarks()).as("顺序 %s", order).isEqualTo(expected.getMarks());
      assertThat(actual.getPostText()).as("顺序 %s", order).isEqualTo(expected.getPostText());
    }
    assertThat(expected.getMarks()).containsEntry(C, T0.plusSeconds(60));
    assertThat(expected.getPostText().value()).isEqualTo("正文@4");
  }

  /// 按顺序并入一组点击：第一条开条目；每条带的正文写明它的分钟，便于看出取的是哪一条。
  ///
  /// @param order 点击顺序
  /// @return 条目
  private static HelpfulNotification replay(List<HelpfulMark> order) {
    HelpfulNotification entry =
        HelpfulNotification.open(AUTHOR, POST, OWNER, order.getFirst(), textOf(order.getFirst()));
    for (HelpfulMark mark : order.subList(1, order.size())) {
      entry.absorb(mark, textOf(mark));
    }
    return entry;
  }

  /// 一次点击带的正文：「正文@分钟」。
  ///
  /// @param mark 点击
  /// @return 正文
  private static String textOf(HelpfulMark mark) {
    return "正文@" + (mark.markedAt().getEpochSecond() - T0.getEpochSecond()) / 60;
  }

  /// 全部排列（5 条 → 120 种）。
  ///
  /// @param items 元素
  /// @return 排列
  private static List<List<HelpfulMark>> permutations(List<HelpfulMark> items) {
    if (items.size() <= 1) {
      return List.of(items);
    }
    List<List<HelpfulMark>> result = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      List<HelpfulMark> rest = new ArrayList<>(items);
      HelpfulMark head = rest.remove(i);
      for (List<HelpfulMark> tail : permutations(rest)) {
        List<HelpfulMark> order = new ArrayList<>();
        order.add(head);
        order.addAll(tail);
        result.add(order);
      }
    }
    return result;
  }

  /// 一条已读的经验条目：评论目标的条目没有正文，这里用经验。
  ///
  /// @param marks 点过的人
  /// @return 条目
  private static HelpfulNotification readEntry(Map<Long, Instant> marks) {
    return HelpfulNotification.restore(
        900L, AUTHOR, POST, OWNER, LocalDate.of(2026, 10, 31), marks, READ_AT, null);
  }

  /// 某人在 T0 之后若干分钟点。
  ///
  /// @param actorId 点的人
  /// @param minutes 分钟
  /// @return 点击
  private static HelpfulMark mark(long actorId, long minutes) {
    return HelpfulMark.of(actorId, T0.plusSeconds(minutes * 60));
  }
}
