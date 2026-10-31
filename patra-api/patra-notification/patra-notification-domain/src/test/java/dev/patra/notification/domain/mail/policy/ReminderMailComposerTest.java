package dev.patra.notification.domain.mail.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import dev.patra.notification.domain.kernel.model.read.ExperienceOutline;
import dev.patra.notification.domain.kernel.model.read.SegmentedExcerpt.Segment;
import dev.patra.notification.domain.kernel.model.read.SubjectView;
import dev.patra.notification.domain.mail.model.vo.DigestWindow;
import dev.patra.notification.domain.mail.model.vo.content.DigestSectionFacts;
import dev.patra.notification.domain.mail.model.vo.content.ReminderFacts;
import dev.patra.notification.domain.mail.model.vo.content.ReminderItem;
import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent;
import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent.Layout;
import dev.patra.notification.domain.mail.model.vo.content.ReminderPlace;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// 提醒邮件的文案、截断、链接与名额（spec 8.2～8.4；示例数据同简报：王立、周敏、林知远，Marik 2017，JAHA）。
class ReminderMailComposerTest {

  private static final String BASE = "https://patra.linqibin.dev";
  private static final String TOKEN = "T".repeat(43);
  private static final ReminderMailComposer COMPOSER =
      new ReminderMailComposer(
          ReminderLinks.of(BASE), ReminderTimeText.of(ZoneId.of("Asia/Shanghai")));

  /// 北京时间 10 月 30 日 15:02。
  private static final Instant AT_1502 = Instant.parse("2026-10-30T07:02:00Z");
  private static final Instant NOW = AT_1502.plus(Duration.ofMinutes(10));

  private static final String MARIK =
      "Hydrocortisone, Vitamin C, and Thiamine for the Treatment of <i>Severe Sepsis</i> and"
          + " Septic Shock: A Retrospective Before-After Study";

  private static final SubjectView PAPER =
      SubjectView.builder()
          .subjectId(40L)
          .type(SubjectType.PUBLICATION)
          .key("18")
          .available(true)
          .title(MARIK)
          .build();

  private static final SubjectView MY_JAHA =
      SubjectView.builder()
          .subjectId(41L)
          .type(SubjectType.POST)
          .key("32")
          .available(true)
          .journal("Journal of the American Heart Association")
          .abbreviation("J Am Heart Assoc")
          .publishedDate("2026-03")
          .ownerId(7L)
          .experience(ExperienceOutline.of("MAJOR_REVISION", "ACCEPTED", 41, 96))
          .build();

  private static final SubjectView OTHERS_CCM =
      SubjectView.builder()
          .subjectId(42L)
          .type(SubjectType.POST)
          .key("33")
          .available(true)
          .journal("Critical Care Medicine")
          .abbreviation("Crit Care Med")
          .publishedDate("2026-03")
          .ownerId(8L)
          .experience(ExperienceOutline.of("DESK_REJECT", "REJECTED", 3, 3))
          .build();

  private static final Map<Long, String> NAMES = Map.of(7L, "chenyu");

  /// 单条回复：主题、标题、小标、「在哪」、摘录、按钮、页脚，链接都按契约。
  @Test
  void single_reply_copy() {
    ReminderMailContent mail =
        COMPOSER.instant(
            NotificationCategory.REPLY,
            List.of(
                facts(
                    1L,
                    NotificationType.COMMENT_REPLY,
                    "王立",
                    AT_1502,
                    "不过作者自己也写了",
                    PAPER,
                    7L,
                    false)),
            NAMES,
            TOKEN,
            NOW);

    assertThat(mail.layout()).isEqualTo(Layout.SINGLE);
    assertThat(mail.subject()).isEqualTo("王立 回复了你的评论");
    assertThat(mail.heading()).isEqualTo("王立 回复了你");
    assertThat(mail.eyebrow()).isEqualTo("回复 · REPLY");
    ReminderItem item = mail.single();
    assertThat(item.place())
        .isEqualTo(
            ReminderPlace.of(
                "文献讨论",
                "Hydrocortisone, Vitamin C, and Thiamine for the Treatment of Severe Sepsis and Septic"
                    + " Shock: A Retrospective Before-After Study",
                null));
    assertThat(item.time()).isEqualTo("10 月 30 日 15:02");
    assertThat(item.actorName()).isEqualTo("王立");
    assertThat(item.link()).isEqualTo(BASE + "/papers/18?comment=7");
    assertThat(mail.buttonLabel()).isEqualTo("查看并回复");
    assertThat(mail.buttonUrl()).isEqualTo(BASE + "/papers/18?comment=7");
    assertThat(mail.why()).isEqualTo("你收到这封邮件，是因为你把「回复我的」设为离站时逐条发送。");
    assertThat(mail.unsubscribeLabel()).isEqualTo("退订这类邮件");
    assertThat(mail.unsubscribeUrl())
        .isEqualTo(BASE + "/unsubscribe#token=" + TOKEN + "&category=REPLY");
    assertThat(mail.oneClickUrl())
        .isEqualTo(BASE + "/unsubscribe/one-click?token=" + TOKEN + "&category=REPLY");
    assertThat(mail.preferencesUrl()).isEqualTo(BASE + "/settings/notifications");
    assertThat(mail.lead()).isNull();
    assertThat(mail.overflow()).isNull();
  }

  /// 单条：有人评论了我的投稿经验。
  @Test
  void single_post_comment_on_my_experience() {
    ReminderMailContent mail =
        single(NotificationCategory.REPLY, NotificationType.POST_COMMENT, "林知远", MY_JAHA, true);

    assertThat(mail.subject()).isEqualTo("林知远 评论了你的投稿经验");
    assertThat(mail.heading()).isEqualTo("林知远 评论了你的投稿经验");
    assertThat(mail.single().place())
        .isEqualTo(ReminderPlace.of("你的投稿经验", "J Am Heart Assoc · 2026 年 3 月投稿 · 大修 → 录用", null));
    assertThat(mail.single().link()).isEqualTo(BASE + "/posts/32?comment=7");
  }

  /// 单条：别人的投稿经验下 @ 了我。
  @Test
  void single_mention_in_others_experience() {
    ReminderMailContent mail =
        single(NotificationCategory.MENTION, NotificationType.MENTION, "林知远", OTHERS_CCM, false);

    assertThat(mail.subject()).isEqualTo("林知远 在评论里 @ 了你");
    assertThat(mail.heading()).isEqualTo("林知远 在评论里 @ 了你");
    assertThat(mail.eyebrow()).isEqualTo("@ 提及 · MENTION");
    assertThat(mail.single().place())
        .isEqualTo(ReminderPlace.of("投稿经验讨论", "Crit Care Med · 2026 年 3 月投稿 · 直接拒稿", null));
    assertThat(mail.why()).isEqualTo("你收到这封邮件，是因为你把「@ 我的」设为离站时逐条发送。");
    assertThat(mail.unsubscribeUrl()).endsWith("&category=MENTION");
  }

  /// 单条摘录 300 字素、保留换行、@ 名字单独成段，截了加省略号。
  @Test
  void single_excerpt_keeps_newlines_and_marks_mentions() {
    String plain = "<@7> 你看\n" + "字".repeat(400);

    ReminderMailContent mail =
        COMPOSER.instant(
            NotificationCategory.MENTION,
            List.of(facts(1L, NotificationType.MENTION, "周敏", AT_1502, plain, PAPER, 7L, false)),
            NAMES,
            TOKEN,
            NOW);

    List<Segment> excerpt = mail.single().excerpt();
    assertThat(excerpt.getFirst()).isEqualTo(Segment.of("@chenyu", true));
    assertThat(excerpt.get(1).text()).startsWith(" 你看\n字");
    assertThat(excerpt.getLast()).isEqualTo(Segment.of("…", false));
    String text = excerpt.stream().map(Segment::text).collect(Collectors.joining());
    assertThat(text.codePointCount(0, text.length())).isEqualTo(300 + 1);
  }

  /// 单条的预览文字是摘录前 60 字素，压成一行。
  @Test
  void preheader_single_is_first_sixty_graphemes() {
    String plain = "第一行\n" + "字".repeat(100);

    ReminderMailContent mail =
        COMPOSER.instant(
            NotificationCategory.REPLY,
            List.of(
                facts(1L, NotificationType.COMMENT_REPLY, "王立", AT_1502, plain, PAPER, 7L, false)),
            NAMES,
            TOKEN,
            NOW);

    assertThat(mail.preheader()).isEqualTo("第一行 " + "字".repeat(56) + "…");
  }

  /// 多条回复合成一封：主题、标题、引导句、按先后的条目、时间只写钟点、按钮去提醒页。
  @Test
  void batch_reply_copy() {
    ReminderMailContent mail =
        COMPOSER.instant(
            NotificationCategory.REPLY,
            List.of(
                facts(
                    3L,
                    NotificationType.POST_COMMENT,
                    "林知远",
                    AT_1502.plus(Duration.ofMinutes(7)),
                    "第二轮的小修",
                    MY_JAHA,
                    9L,
                    true),
                facts(
                    1L,
                    NotificationType.COMMENT_REPLY,
                    "王立",
                    AT_1502,
                    "不过作者自己也写了",
                    PAPER,
                    7L,
                    false),
                facts(
                    2L,
                    NotificationType.COMMENT_REPLY,
                    "周敏",
                    AT_1502.plus(Duration.ofMinutes(3)),
                    "同意。补充一点",
                    PAPER,
                    8L,
                    false)),
            NAMES,
            TOKEN,
            NOW);

    assertThat(mail.layout()).isEqualTo(Layout.BATCH);
    assertThat(mail.subject()).isEqualTo("3 条新回复：王立、周敏、林知远");
    assertThat(mail.heading()).isEqualTo("3 条新回复");
    assertThat(mail.lead()).isEqualTo("你不在 Patra 上的这段时间，有 3 条回复还没看。");
    assertThat(mail.items())
        .extracting(ReminderItem::actorName, ReminderItem::action, ReminderItem::time)
        .containsExactly(
            tuple("王立", "回复了你", "15:02"),
            tuple("周敏", "回复了你", "15:05"),
            tuple("林知远", "评论了你的投稿经验", "15:09"));
    assertThat(mail.items().getFirst().place().line())
        .isEqualTo("文献 · Hydrocortisone, Vitamin C, and Thiamine for the Treatment of Sev…");
    assertThat(mail.items().get(2).place().line())
        .isEqualTo("你的投稿经验 · J Am Heart Assoc · 2026 年 3 月投稿");
    assertThat(mail.items().get(2).link()).isEqualTo(BASE + "/posts/32?comment=9");
    assertThat(mail.preheader()).isEqualTo("王立：不过作者自己也写了");
    assertThat(mail.buttonLabel()).isEqualTo("打开提醒页");
    assertThat(mail.buttonUrl()).isEqualTo(BASE + "/notifications");
    assertThat(mail.overflow()).isNull();
  }

  /// 主题里的名字去重，多于 3 个写前两个加「等 N 人」。
  @Test
  void batch_names_over_three() {
    List<String> actors = List.of("王立", "周敏", "王立", "林知远", "陈宇", "李华");
    List<ReminderFacts> entries =
        IntStream.range(0, actors.size())
            .mapToObj(
                i ->
                    facts(
                        i + 1L,
                        NotificationType.COMMENT_REPLY,
                        actors.get(i),
                        AT_1502.plusSeconds(i),
                        "好",
                        PAPER,
                        7L + i,
                        false))
            .toList();

    ReminderMailContent mail =
        COMPOSER.instant(NotificationCategory.REPLY, entries, NAMES, TOKEN, NOW);

    assertThat(mail.subject()).isEqualTo("6 条新回复：王立、周敏等 5 人");
  }

  /// 多条 @ 合成一封：按钮去「@ 我的」标签。
  @Test
  void batch_mention_copy() {
    ReminderMailContent mail =
        COMPOSER.instant(
            NotificationCategory.MENTION,
            List.of(
                facts(1L, NotificationType.MENTION, "林知远", AT_1502, "<@7> 你上次提到", PAPER, 7L, false),
                facts(
                    2L,
                    NotificationType.MENTION,
                    "周敏",
                    AT_1502.plusSeconds(60),
                    "这个问题 <@7> 应该最清楚",
                    OTHERS_CCM,
                    8L,
                    false)),
            NAMES,
            TOKEN,
            NOW);

    assertThat(mail.subject()).isEqualTo("2 条评论 @ 了你：林知远、周敏");
    assertThat(mail.heading()).isEqualTo("2 条评论 @ 了你");
    assertThat(mail.lead()).isEqualTo("你不在 Patra 上的这段时间，有 2 条 @ 你的评论还没看。");
    assertThat(mail.items().get(1).place().line()).isEqualTo("Crit Care Med 的一条投稿经验");
    assertThat(mail.items()).extracting(ReminderItem::action).containsOnly("@ 了你");
    assertThat(mail.buttonUrl()).isEqualTo(BASE + "/notifications/mentions");
    assertThat(mail.preheader()).isEqualTo("林知远：@chenyu 你上次提到");
  }

  /// 超过 10 条只列前 10 条，其余给数字。
  @Test
  void batch_over_ten_lists_ten_and_overflow() {
    List<ReminderFacts> entries =
        IntStream.range(0, 12)
            .mapToObj(
                i ->
                    facts(
                        i + 1L,
                        NotificationType.COMMENT_REPLY,
                        "王立",
                        AT_1502.plusSeconds(i),
                        "好",
                        PAPER,
                        7L + i,
                        false))
            .toList();

    ReminderMailContent mail =
        COMPOSER.instant(NotificationCategory.REPLY, entries, NAMES, TOKEN, NOW);

    assertThat(mail.items()).hasSize(10);
    assertThat(mail.subject()).isEqualTo("12 条新回复：王立");
    assertThat(mail.overflow()).isEqualTo("还有 2 条没有列出，到提醒页查看。");
  }

  /// 有一条不在发送当天，全部写上日期。
  @Test
  void batch_across_days_uses_full_time() {
    ReminderMailContent mail =
        COMPOSER.instant(
            NotificationCategory.REPLY,
            List.of(
                facts(
                    1L,
                    NotificationType.COMMENT_REPLY,
                    "王立",
                    AT_1502.minus(Duration.ofDays(1)),
                    "好",
                    PAPER,
                    7L,
                    false),
                facts(2L, NotificationType.COMMENT_REPLY, "周敏", AT_1502, "好", PAPER, 8L, false)),
            NAMES,
            TOKEN,
            NOW);

    assertThat(mail.items())
        .extracting(ReminderItem::time)
        .containsExactly("10 月 29 日 15:02", "10 月 30 日 15:02");
  }

  /// 列表里的摘录压成一行、140 字素。
  @Test
  void batch_list_excerpt_is_single_line_140() {
    String plain = "一\n" + "字".repeat(200);

    ReminderMailContent mail =
        COMPOSER.instant(
            NotificationCategory.REPLY,
            List.of(
                facts(1L, NotificationType.COMMENT_REPLY, "王立", AT_1502, plain, PAPER, 7L, false),
                facts(2L, NotificationType.COMMENT_REPLY, "周敏", AT_1502, "好", PAPER, 8L, false)),
            NAMES,
            TOKEN,
            NOW);

    String text =
        mail.items().getFirst().excerpt().stream().map(Segment::text).collect(Collectors.joining());
    assertThat(text).doesNotContain("\n").startsWith("一 字").endsWith("…");
    assertThat(text.codePointCount(0, text.length())).isEqualTo(140 + 1);
  }

  /// 有帮助没有提醒邮件：混进来是调用方的错。
  @ParameterizedTest
  @EnumSource(
      value = NotificationType.class,
      names = {"COMMENT_HELPFUL", "POST_HELPFUL"})
  void helpful_types_are_rejected(NotificationType type) {
    assertThatThrownBy(
            () ->
                COMPOSER.instant(
                    NotificationCategory.REPLY,
                    List.of(facts(1L, type, "王立", AT_1502, "好", PAPER, 7L, false)),
                    NAMES,
                    TOKEN,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 一条都没有是调用方的错。
  @Test
  void instant_requires_entries() {
    assertThatThrownBy(
            () -> COMPOSER.instant(NotificationCategory.REPLY, List.of(), NAMES, TOKEN, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 汇总两节：主题、标题、小标、引导句、节标题、按钮、页脚。
  @Test
  void digest_both_sections_copy() {
    DigestWindow window = DigestWindow.endingAt(Instant.parse("2026-10-31T00:00:00Z"));

    ReminderMailContent mail =
        COMPOSER.digest(
            window,
            Set.of(NotificationCategory.REPLY, NotificationCategory.MENTION),
            DigestSectionFacts.of(3, replies(3)),
            DigestSectionFacts.of(2, mentions(2)),
            NAMES,
            TOKEN);

    assertThat(mail.layout()).isEqualTo(Layout.DIGEST);
    assertThat(mail.subject()).isEqualTo("每日汇总：5 条未读的回复和 @（10 月 31 日）");
    assertThat(mail.heading()).isEqualTo("过去 24 小时有 5 条未读");
    assertThat(mail.eyebrow()).isEqualTo("每日汇总 · 10 月 31 日");
    assertThat(mail.lead())
        .isEqualTo("10 月 30 日 08:00 到 10 月 31 日 08:00（北京时间）之间，下面这些提醒你还没看过；在站内看过的不会列在这里。");
    assertThat(mail.sections())
        .extracting(s -> s.title())
        .containsExactly("回复我的 · 3 条", "@ 我的 · 2 条");
    assertThat(mail.sections().getFirst().items())
        .extracting(ReminderItem::time)
        .first()
        .isEqualTo("10 月 30 日 15:04");
    assertThat(mail.why()).isEqualTo("你收到这封邮件，是因为你把「回复我的」「@ 我的」设为每日汇总。");
    assertThat(mail.unsubscribeLabel()).isEqualTo("退订每日汇总");
    assertThat(mail.unsubscribeUrl()).endsWith("&category=DIGEST");
    assertThat(mail.oneClickUrl()).endsWith("&category=DIGEST");
    assertThat(mail.buttonLabel()).isEqualTo("打开提醒页");
    assertThat(mail.buttonUrl()).isEqualTo(BASE + "/notifications");
    assertThat(mail.overflow()).isNull();
  }

  /// 汇总只有 @ 一节。
  @Test
  void digest_mentions_only_copy() {
    ReminderMailContent mail =
        COMPOSER.digest(
            DigestWindow.endingAt(Instant.parse("2026-10-31T00:00:00Z")),
            Set.of(NotificationCategory.MENTION),
            DigestSectionFacts.of(0, List.of()),
            DigestSectionFacts.of(2, mentions(2)),
            NAMES,
            TOKEN);

    assertThat(mail.subject()).isEqualTo("每日汇总：2 条未读的 @（10 月 31 日）");
    assertThat(mail.heading()).isEqualTo("过去 24 小时有 2 条 @ 没看");
    assertThat(mail.sections()).extracting(s -> s.title()).containsExactly("@ 我的 · 2 条");
    assertThat(mail.why()).isEqualTo("你收到这封邮件，是因为你把「@ 我的」设为每日汇总。");
  }

  /// 汇总只有回复一节（两类都在汇总，但 @ 没有未读）。
  @Test
  void digest_replies_only_copy() {
    ReminderMailContent mail =
        COMPOSER.digest(
            DigestWindow.endingAt(Instant.parse("2026-10-31T00:00:00Z")),
            Set.of(NotificationCategory.REPLY, NotificationCategory.MENTION),
            DigestSectionFacts.of(3, replies(3)),
            DigestSectionFacts.of(0, List.of()),
            NAMES,
            TOKEN);

    assertThat(mail.subject()).isEqualTo("每日汇总：3 条未读的回复（10 月 31 日）");
    assertThat(mail.heading()).isEqualTo("过去 24 小时有 3 条回复没看");
    assertThat(mail.sections()).hasSize(1);
    assertThat(mail.why()).isEqualTo("你收到这封邮件，是因为你把「回复我的」「@ 我的」设为每日汇总。");
  }

  /// 超过 10 条：按名额分节，其余按节给数字。
  @Test
  void digest_overflow_counts_per_section() {
    ReminderMailContent mail =
        COMPOSER.digest(
            DigestWindow.endingAt(Instant.parse("2026-10-31T00:00:00Z")),
            Set.of(NotificationCategory.REPLY, NotificationCategory.MENTION),
            DigestSectionFacts.of(9, replies(9)),
            DigestSectionFacts.of(5, mentions(5)),
            NAMES,
            TOKEN);

    assertThat(mail.sections()).extracting(s -> s.items().size()).containsExactly(7, 3);
    assertThat(mail.sections())
        .extracting(s -> s.title())
        .containsExactly("回复我的 · 9 条", "@ 我的 · 5 条");
    assertThat(mail.overflow()).isEqualTo("还有 4 条没有列出（回复 2 条、@ 2 条），到提醒页查看。");
    assertThat(mail.subject()).isEqualTo("每日汇总：14 条未读的回复和 @（10 月 31 日）");
  }

  /// 每节新的在前。
  @Test
  void digest_section_newest_first() {
    ReminderMailContent mail =
        COMPOSER.digest(
            DigestWindow.endingAt(Instant.parse("2026-10-31T00:00:00Z")),
            Set.of(NotificationCategory.REPLY),
            DigestSectionFacts.of(3, replies(3).reversed()),
            DigestSectionFacts.of(0, List.of()),
            NAMES,
            TOKEN);

    assertThat(mail.sections().getFirst().items())
        .extracting(ReminderItem::time)
        .containsExactly("10 月 30 日 15:04", "10 月 30 日 15:03", "10 月 30 日 15:02");
    assertThat(mail.preheader()).startsWith("R2：");
  }

  /// 单条邮件。
  ///
  /// @param category category
  /// @param type type
  /// @param actor actor
  /// @param subject subject
  /// @param mine mine
  /// @return 结果
  private static ReminderMailContent single(
      NotificationCategory category,
      NotificationType type,
      String actor,
      SubjectView subject,
      boolean mine) {
    return COMPOSER.instant(
        category,
        List.of(facts(1L, type, actor, AT_1502, "第二轮的小修", subject, 7L, mine)),
        NAMES,
        TOKEN,
        NOW);
  }

  /// 回复 R0..R(n-1)，时间 15:02 起每条加一分钟。
  private static List<ReminderFacts> replies(int n) {
    return IntStream.range(0, n)
        .mapToObj(
            i ->
                facts(
                    100L + i,
                    NotificationType.COMMENT_REPLY,
                    "R" + i,
                    AT_1502.plus(Duration.ofMinutes(i)),
                    "回复" + i,
                    PAPER,
                    500L + i,
                    false))
        .toList()
        .reversed();
  }

  /// @ M0..M(n-1)，时间 15:02 起每条加一分钟，新的在前。
  ///
  /// @param n n
  /// @return 结果
  private static List<ReminderFacts> mentions(int n) {
    return IntStream.range(0, n)
        .mapToObj(
            i ->
                facts(
                    200L + i,
                    NotificationType.MENTION,
                    "M" + i,
                    AT_1502.plus(Duration.ofMinutes(i)),
                    "<@7> 看" + i,
                    OTHERS_CCM,
                    600L + i,
                    false))
        .toList()
        .reversed();
  }

  /// 拼一条提醒的事实。
  ///
  /// @param id id
  /// @param type type
  /// @param actor actor
  /// @param occurredAt occurredAt
  /// @param plainText plainText
  /// @param subject subject
  /// @param commentId commentId
  /// @param mine mine
  /// @return 结果
  private static ReminderFacts facts(
      long id,
      NotificationType type,
      String actor,
      Instant occurredAt,
      String plainText,
      SubjectView subject,
      long commentId,
      boolean mine) {
    return ReminderFacts.builder()
        .notificationId(id)
        .type(type)
        .actorName(actor)
        .occurredAt(occurredAt)
        .plainText(plainText)
        .subject(subject)
        .commentId(commentId)
        .mine(mine)
        .build();
  }
}
