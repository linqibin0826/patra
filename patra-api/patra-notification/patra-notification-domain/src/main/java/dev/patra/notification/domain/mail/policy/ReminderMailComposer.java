package dev.patra.notification.domain.mail.policy;

import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import dev.patra.notification.domain.kernel.model.read.RenderedExcerpt;
import dev.patra.notification.domain.kernel.model.read.SegmentedExcerpt;
import dev.patra.notification.domain.kernel.model.read.SegmentedExcerpt.Segment;
import dev.patra.notification.domain.kernel.model.read.SubjectView;
import dev.patra.notification.domain.kernel.policy.ExcerptRenderer;
import dev.patra.notification.domain.mail.model.enums.UnsubscribeLinkCategory;
import dev.patra.notification.domain.mail.model.vo.DigestSlots;
import dev.patra.notification.domain.mail.model.vo.DigestWindow;
import dev.patra.notification.domain.mail.model.vo.content.DigestSection;
import dev.patra.notification.domain.mail.model.vo.content.DigestSectionFacts;
import dev.patra.notification.domain.mail.model.vo.content.ReminderFacts;
import dev.patra.notification.domain.mail.model.vo.content.ReminderItem;
import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent;
import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent.Layout;
import dev.patra.notification.domain.mail.model.vo.content.ReminderPlace;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/// 把一批提醒拼成一封邮件的全部文案与链接（spec 8.2～8.4）：主题、预览、小标、标题、引导句、条目（名字、动作、时间、
/// 摘录、在哪、链接）、按钮、卡片外一句与两个退订地址。模板只管排版，不再做任何判断。
public final class ReminderMailComposer {

  /// 单条邮件的摘录长度（字素），保留换行。
  static final int SINGLE_EXCERPT = 300;

  /// 列表里每条摘录的长度（字素），压成一行。
  static final int LIST_EXCERPT = 140;

  /// 单条邮件里文献标题的长度（字素）。
  static final int SINGLE_TITLE = 200;

  /// 列表里文献标题的长度（字素）。
  static final int LIST_TITLE = 64;

  /// 单条邮件预览文字的长度（字素）。
  static final int SINGLE_PREHEADER = 60;

  /// 多条与汇总预览文字里摘录的长度（字素）。
  static final int LIST_PREHEADER = 50;

  /// 主题里最多写几个名字。
  private static final int MAX_SUBJECT_NAMES = 3;

  /// 截掉内容时加的省略号。
  private static final String ELLIPSIS = "…";

  /// 先后顺序：排序时间，同一时刻按提醒 ID。
  private static final Comparator<ReminderFacts> OLDEST_FIRST =
      Comparator.comparing(ReminderFacts::occurredAt)
          .thenComparingLong(ReminderFacts::notificationId);

  private final ReminderLinks links;
  private final ReminderTimeText time;

  /// 创建。
  ///
  /// @param links 门户链接
  /// @param time 时间写法
  public ReminderMailComposer(ReminderLinks links, ReminderTimeText time) {
    this.links = Objects.requireNonNull(links, "links 不能为 null");
    this.time = Objects.requireNonNull(time, "time 不能为 null");
  }

  /// 离站逐条的一封：一条是单条版式，多条是合一版式（最多列 10 条）。
  ///
  /// @param category 类别（一封只有一类）
  /// @param entries 要列的提醒，至少一条
  /// @param mentionNames `<@ID>` 的名字（不含 `@`）
  /// @param token 退订令牌
  /// @param now 此刻（判断是不是都在发送当天）
  /// @return 邮件内容
  /// @throws IllegalArgumentException 一条提醒都没有
  public ReminderMailContent instant(
      NotificationCategory category,
      List<ReminderFacts> entries,
      Map<Long, String> mentionNames,
      String token,
      Instant now) {
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("逐条邮件至少要有一条提醒");
    }
    List<ReminderFacts> ordered = entries.stream().sorted(OLDEST_FIRST).toList();
    UnsubscribeLinkCategory unsubscribe = UnsubscribeLinkCategory.of(category);
    ReminderMailContent.ReminderMailContentBuilder mail =
        ReminderMailContent.builder()
            .eyebrow(category == NotificationCategory.MENTION ? "@ 提及 · MENTION" : "回复 · REPLY")
            .why("你收到这封邮件，是因为你把「" + categoryName(category) + "」设为离站时逐条发送。")
            .unsubscribeLabel("退订这类邮件")
            .unsubscribeUrl(links.unsubscribeConfirm(token, unsubscribe))
            .oneClickUrl(links.unsubscribeOneClick(token, unsubscribe))
            .preferencesUrl(links.settings());
    return ordered.size() == 1
        ? single(mail, ordered.getFirst(), mentionNames)
        : batch(mail, category, ordered, mentionNames, now);
  }

  /// 每日汇总的一封：设为每日汇总的类别各一节（这一类没有未读就没有这一节），合计最多列 10 条。
  ///
  /// @param window 汇总窗口
  /// @param digestCategories 此刻设为每日汇总的类别
  /// @param replies 「回复我的」这一节的事实，列出的按新的在前
  /// @param mentions 「@ 我的」这一节的事实
  /// @param mentionNames `<@ID>` 的名字
  /// @param token 退订令牌
  /// @return 邮件内容
  /// @throws IllegalArgumentException 两节合计一条未读都没有
  public ReminderMailContent digest(
      DigestWindow window,
      Set<NotificationCategory> digestCategories,
      DigestSectionFacts replies,
      DigestSectionFacts mentions,
      Map<Long, String> mentionNames,
      String token) {
    boolean replyIncluded = digestCategories.contains(NotificationCategory.REPLY);
    boolean mentionIncluded = digestCategories.contains(NotificationCategory.MENTION);
    int replyTotal = replyIncluded ? replies.total() : 0;
    int mentionTotal = mentionIncluded ? mentions.total() : 0;
    int total = replyTotal + mentionTotal;
    if (total == 0) {
      throw new IllegalArgumentException("每日汇总至少要有一条未读");
    }
    List<ReminderFacts> replyList = newestFirst(replyIncluded ? replies.listable() : List.of());
    List<ReminderFacts> mentionList =
        newestFirst(mentionIncluded ? mentions.listable() : List.of());
    DigestSlots slots =
        DigestSlotPolicy.allocate(replyTotal, replyList.size(), mentionTotal, mentionList.size());
    List<DigestSection> sections = new ArrayList<>();
    if (replyTotal > 0) {
      sections.add(
          DigestSection.of(
              "回复我的 · " + replyTotal + " 条",
              listItems(replyList.subList(0, slots.replies()), mentionNames, time::full)));
    }
    if (mentionTotal > 0) {
      sections.add(
          DigestSection.of(
              "@ 我的 · " + mentionTotal + " 条",
              listItems(mentionList.subList(0, slots.mentions()), mentionNames, time::full)));
    }
    String date = time.date(window.to());
    ReminderFacts first =
        Stream.concat(
                replyList.subList(0, slots.replies()).stream(),
                mentionList.subList(0, slots.mentions()).stream())
            .findFirst()
            .orElse(null);
    return ReminderMailContent.builder()
        .layout(Layout.DIGEST)
        .subject("每日汇总：" + digestCount(replyTotal, mentionTotal) + "（" + date + "）")
        .preheader(first == null ? null : listPreheader(first, mentionNames))
        .eyebrow("每日汇总 · " + date)
        .heading(digestHeading(replyTotal, mentionTotal))
        .lead(
            time.full(window.from())
                + " 到 "
                + time.full(window.to())
                + "（北京时间）之间，下面这些提醒你还没看过；在站内看过的不会列在这里。")
        .sections(sections)
        .overflow(
            digestOverflow(
                replyTotal - slots.replies(), mentionTotal - slots.mentions(), sections.size()))
        .buttonLabel("打开提醒页")
        .buttonUrl(links.inboxHome())
        .why("你收到这封邮件，是因为你把" + digestCategoryNames(digestCategories) + "设为每日汇总。")
        .unsubscribeLabel("退订每日汇总")
        .unsubscribeUrl(links.unsubscribeConfirm(token, UnsubscribeLinkCategory.DIGEST))
        .oneClickUrl(links.unsubscribeOneClick(token, UnsubscribeLinkCategory.DIGEST))
        .preferencesUrl(links.settings())
        .build();
  }

  /// 单条版式。
  ///
  /// @param mail 已填好页脚的构建器
  /// @param entry 那一条
  /// @param names `<@ID>` 的名字
  /// @return 邮件内容
  private ReminderMailContent single(
      ReminderMailContent.ReminderMailContentBuilder mail,
      ReminderFacts entry,
      Map<Long, String> names) {
    String actor = entry.actorName();
    String subject =
        switch (entry.type()) {
          case COMMENT_REPLY -> actor + " 回复了你的评论";
          case POST_COMMENT -> actor + " 评论了你的投稿经验";
          case MENTION -> actor + " 在评论里 @ 了你";
        };
    String heading = entry.type() == NotificationType.COMMENT_REPLY ? actor + " 回复了你" : subject;
    String link = commentLink(entry);
    ReminderItem item =
        ReminderItem.builder()
            .actorName(actor)
            .action(action(entry.type()))
            .time(time.full(entry.occurredAt()))
            .excerpt(excerpt(entry.plainText(), names, SINGLE_EXCERPT, false))
            .place(singlePlace(entry))
            .link(link)
            .build();
    return mail.layout(Layout.SINGLE)
        .subject(subject)
        .preheader(preheader(entry.plainText(), names, SINGLE_PREHEADER))
        .heading(heading)
        .single(item)
        .buttonLabel("查看并回复")
        .buttonUrl(link)
        .build();
  }

  /// 合一版式：按先后列前 10 条，其余给数字。
  ///
  /// @param mail 已填好页脚的构建器
  /// @param category 类别
  /// @param ordered 按先后排好的提醒
  /// @param names `<@ID>` 的名字
  /// @param now 此刻
  /// @return 邮件内容
  private ReminderMailContent batch(
      ReminderMailContent.ReminderMailContentBuilder mail,
      NotificationCategory category,
      List<ReminderFacts> ordered,
      Map<Long, String> names,
      Instant now) {
    int count = ordered.size();
    List<ReminderFacts> listed = ordered.subList(0, Math.min(count, DigestSlotPolicy.MAX_ITEMS));
    boolean sameDay = listed.stream().allMatch(e -> time.sameDay(e.occurredAt(), now));
    Function<Instant, String> clock = sameDay ? time::clock : time::full;
    boolean mention = category == NotificationCategory.MENTION;
    String heading = mention ? count + " 条评论 @ 了你" : count + " 条新回复";
    String overflow =
        count > listed.size() ? "还有 " + (count - listed.size()) + " 条没有列出，到提醒页查看。" : null;
    return mail.layout(Layout.BATCH)
        .subject(heading + "：" + subjectNames(ordered))
        .preheader(listPreheader(listed.getFirst(), names))
        .heading(heading)
        .lead(
            mention
                ? "你不在 Patra 上的这段时间，有 " + count + " 条 @ 你的评论还没看。"
                : "你不在 Patra 上的这段时间，有 " + count + " 条回复还没看。")
        .items(listItems(listed, names, clock))
        .overflow(overflow)
        .buttonLabel("打开提醒页")
        .buttonUrl(links.inbox(category))
        .build();
  }

  /// 列表里的条目：摘录压成一行 140 字素，「在哪」写一行。
  ///
  /// @param entries 要列的提醒
  /// @param names `<@ID>` 的名字
  /// @param clock 时间写法
  /// @return 条目
  private List<ReminderItem> listItems(
      List<ReminderFacts> entries, Map<Long, String> names, Function<Instant, String> clock) {
    return entries.stream()
        .map(
            entry ->
                ReminderItem.builder()
                    .actorName(entry.actorName())
                    .action(action(entry.type()))
                    .time(clock.apply(entry.occurredAt()))
                    .excerpt(excerpt(entry.plainText(), names, LIST_EXCERPT, true))
                    .place(ReminderPlace.of(null, null, listPlace(entry)))
                    .link(commentLink(entry))
                    .build())
        .toList();
  }

  /// 单条邮件的「在哪」：小标加内容。
  ///
  /// @param entry 提醒
  /// @return 在哪
  private static ReminderPlace singlePlace(ReminderFacts entry) {
    SubjectView subject = entry.subject();
    if (subject.type() == SubjectType.PUBLICATION) {
      return ReminderPlace.of(
          "文献讨论",
          PublicationTitleText.truncate(PublicationTitleText.plain(subject.title()), SINGLE_TITLE),
          null);
    }
    List<String> parts = new ArrayList<>(experienceHead(subject));
    if (subject.experience() != null) {
      ExperienceOutcomeText.of(
              subject.experience().firstDecision(), subject.experience().finalOutcome())
          .ifPresent(parts::add);
    }
    return ReminderPlace.of(entry.mine() ? "你的投稿经验" : "投稿经验讨论", String.join(" · ", parts), null);
  }

  /// 列表里的「在哪」一行。
  ///
  /// @param entry 提醒
  /// @return 一行文字
  private static String listPlace(ReminderFacts entry) {
    SubjectView subject = entry.subject();
    if (subject.type() == SubjectType.PUBLICATION) {
      return "文献 · "
          + PublicationTitleText.truncate(PublicationTitleText.plain(subject.title()), LIST_TITLE);
    }
    if (entry.mine()) {
      List<String> parts = new ArrayList<>(List.of("你的投稿经验"));
      parts.addAll(experienceHead(subject));
      return String.join(" · ", parts);
    }
    String journal = journal(subject);
    return journal.isEmpty() ? "一条投稿经验" : journal + " 的一条投稿经验";
  }

  /// 经验的期刊与投稿月份（`J Am Heart Assoc`、`2026 年 3 月投稿`），没有的省略。
  ///
  /// @param subject 经验的对象视图
  /// @return 文字段
  private static List<String> experienceHead(SubjectView subject) {
    List<String> parts = new ArrayList<>();
    String journal = journal(subject);
    if (!journal.isEmpty()) {
      parts.add(journal);
    }
    String month = subject.publishedDate();
    if (month != null && month.matches("\\d{4}-\\d{2}")) {
      parts.add(month.substring(0, 4) + " 年 " + Integer.parseInt(month.substring(5, 7)) + " 月投稿");
    }
    return parts;
  }

  /// 期刊名：有缩写用缩写，否则全称，都没有为空串。
  ///
  /// @param subject 对象视图
  /// @return 期刊名
  private static String journal(SubjectView subject) {
    if (subject.abbreviation() != null && !subject.abbreviation().isBlank()) {
      return subject.abbreviation();
    }
    return subject.journal() == null ? "" : subject.journal();
  }

  /// 「查看并回复」去的评论永久链接。
  ///
  /// @param entry 提醒
  /// @return 链接
  private String commentLink(ReminderFacts entry) {
    return links.comment(
        entry.subject().type() == SubjectType.PUBLICATION,
        entry.subject().key(),
        entry.commentId());
  }

  /// 摘录分段；截掉了内容时末尾加 `…`。
  ///
  /// @param plainText 纯文本全文
  /// @param names `<@ID>` 的名字
  /// @param maxGraphemes 字素上限
  /// @param singleLine 是否压成一行
  /// @return 段
  private static List<Segment> excerpt(
      String plainText, Map<Long, String> names, int maxGraphemes, boolean singleLine) {
    SegmentedExcerpt rendered =
        ExcerptRenderer.renderSegments(plainText, names, maxGraphemes, singleLine);
    if (!rendered.truncated()) {
      return rendered.segments();
    }
    List<Segment> segments = new ArrayList<>(rendered.segments());
    segments.add(Segment.of(ELLIPSIS, false));
    return segments;
  }

  /// 预览文字：摘录压成一行，截掉了内容就加 `…`。
  ///
  /// @param plainText 纯文本全文
  /// @param names `<@ID>` 的名字
  /// @param maxGraphemes 字素上限
  /// @return 预览文字
  private static String preheader(String plainText, Map<Long, String> names, int maxGraphemes) {
    RenderedExcerpt rendered = ExcerptRenderer.render(plainText, names, maxGraphemes, true);
    return rendered.truncated() ? rendered.text() + ELLIPSIS : rendered.text();
  }

  /// 多条与汇总的预览文字：`第一条的名字：摘录…`。
  ///
  /// @param first 第一条
  /// @param names `<@ID>` 的名字
  /// @return 预览文字
  private static String listPreheader(ReminderFacts first, Map<Long, String> names) {
    return first.actorName() + "：" + preheader(first.plainText(), names, LIST_PREHEADER);
  }

  /// 列表里的动作（同门户 `actionLabel`）。
  ///
  /// @param type 类型
  /// @return 动作
  private static String action(NotificationType type) {
    return switch (type) {
      case COMMENT_REPLY -> "回复了你";
      case POST_COMMENT -> "评论了你的投稿经验";
      case MENTION -> "@ 了你";
    };
  }

  /// 主题里的名字：按出现先后去重，3 个以内全写，多了写前两个加「等 N 人」。
  ///
  /// @param ordered 按先后排好的提醒
  /// @return 名字
  private static String subjectNames(List<ReminderFacts> ordered) {
    List<String> names = ordered.stream().map(ReminderFacts::actorName).distinct().toList();
    if (names.size() <= MAX_SUBJECT_NAMES) {
      return String.join("、", names);
    }
    return names.get(0) + "、" + names.get(1) + "等 " + names.size() + " 人";
  }

  /// 汇总主题里的数量：`5 条未读的回复和 @`、`2 条未读的 @`、`3 条未读的回复`。
  ///
  /// @param replies 回复未读总数
  /// @param mentions @ 未读总数
  /// @return 文字
  private static String digestCount(int replies, int mentions) {
    if (replies > 0 && mentions > 0) {
      return (replies + mentions) + " 条未读的回复和 @";
    }
    return replies > 0 ? replies + " 条未读的回复" : mentions + " 条未读的 @";
  }

  /// 汇总的标题。
  ///
  /// @param replies 回复未读总数
  /// @param mentions @ 未读总数
  /// @return 标题
  private static String digestHeading(int replies, int mentions) {
    if (replies > 0 && mentions > 0) {
      return "过去 24 小时有 " + (replies + mentions) + " 条未读";
    }
    return replies > 0 ? "过去 24 小时有 " + replies + " 条回复没看" : "过去 24 小时有 " + mentions + " 条 @ 没看";
  }

  /// 汇总里没列出的条数说明；两节都在时按节写明。
  ///
  /// @param hiddenReplies 没列出的回复
  /// @param hiddenMentions 没列出的 @
  /// @param sections 有几节
  /// @return 说明；都列出了为空
  private static String digestOverflow(int hiddenReplies, int hiddenMentions, int sections) {
    int hidden = hiddenReplies + hiddenMentions;
    if (hidden == 0) {
      return null;
    }
    if (sections < 2) {
      return "还有 " + hidden + " 条没有列出，到提醒页查看。";
    }
    List<String> parts = new ArrayList<>();
    if (hiddenReplies > 0) {
      parts.add("回复 " + hiddenReplies + " 条");
    }
    if (hiddenMentions > 0) {
      parts.add("@ " + hiddenMentions + " 条");
    }
    return "还有 " + hidden + " 条没有列出（" + String.join("、", parts) + "），到提醒页查看。";
  }

  /// 卡片外那一句里设为每日汇总的类别：`「回复我的」「@ 我的」`。
  ///
  /// @param categories 设为每日汇总的类别
  /// @return 文字
  private static String digestCategoryNames(Set<NotificationCategory> categories) {
    StringBuilder out = new StringBuilder();
    for (NotificationCategory category :
        List.of(NotificationCategory.REPLY, NotificationCategory.MENTION)) {
      if (categories.contains(category)) {
        out.append("「").append(categoryName(category)).append("」");
      }
    }
    return out.toString();
  }

  /// 类别在设置页上的名字。
  ///
  /// @param category 类别
  /// @return `回复我的` 或 `@ 我的`
  private static String categoryName(NotificationCategory category) {
    return category == NotificationCategory.MENTION ? "@ 我的" : "回复我的";
  }

  /// 新的在前。
  ///
  /// @param entries 提醒
  /// @return 排好的提醒
  private static List<ReminderFacts> newestFirst(List<ReminderFacts> entries) {
    return entries.stream().sorted(OLDEST_FIRST.reversed()).toList();
  }
}
