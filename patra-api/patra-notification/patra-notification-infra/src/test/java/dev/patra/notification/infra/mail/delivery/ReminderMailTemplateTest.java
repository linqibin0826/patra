package dev.patra.notification.infra.mail.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import dev.patra.notification.domain.kernel.model.read.SubjectView;
import dev.patra.notification.domain.mail.model.vo.DigestWindow;
import dev.patra.notification.domain.mail.model.vo.OutgoingMail;
import dev.patra.notification.domain.mail.model.vo.content.DigestSectionFacts;
import dev.patra.notification.domain.mail.model.vo.content.ReminderFacts;
import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent;
import dev.patra.notification.domain.mail.policy.ReminderLinks;
import dev.patra.notification.domain.mail.policy.ReminderMailComposer;
import dev.patra.notification.domain.mail.policy.ReminderTimeText;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 提醒邮件的三个模板（单条、多条合一、每日汇总）经 PAP-105 的渲染器出 HTML 与纯文本（spec 8.5、简报 §3.7～§3.11）。
@DisplayName("提醒邮件模板")
class ReminderMailTemplateTest {

  private static final String BASE = "https://patra.linqibin.dev";
  private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
  private static final Instant AT = Instant.parse("2026-10-30T07:02:00Z");
  private static final ReminderMailComposer COMPOSER =
      new ReminderMailComposer(ReminderLinks.of(BASE), ReminderTimeText.of(BEIJING));
  private static final SubjectView PAPER =
      SubjectView.builder()
          .subjectId(40L)
          .type(SubjectType.PUBLICATION)
          .key("18")
          .available(true)
          .title("Effect of <i>Vitamin C</i> &amp; Sepsis <script>x</script>")
          .build();

  private final ThymeleafMailRenderer renderer = new ThymeleafMailRenderer(BEIJING);

  /// 单条：刊头类别、小标、标题、在哪、引用块、按钮、预览、版权页第二行、卡片外一句与两个链接。
  @Test
  void single_html_has_quote_block_button_and_aside_links() {
    ReminderMailContent content = single("不过作者自己也写了\n第二行");

    RenderedMail mail = render("notification-single", content);

    assertThat(mail.html())
        .contains("讨论提醒")
        .contains("回复 · REPLY")
        .contains("王立 回复了你")
        .contains("文献讨论")
        .contains("p-quote")
        .contains("不过作者自己也写了\n第二行")
        .contains("href=\"" + BASE + "/papers/18?comment=500\"")
        .contains("查看并回复 →")
        .contains("display:none")
        .contains("此邮件由 Patra 系统自动发出，直接回复不会出现在讨论里。")
        .doesNotContain("请勿回复")
        .contains("你收到这封邮件，是因为你把「回复我的」设为离站时逐条发送。")
        .contains(
            "href=\"" + BASE + "/unsubscribe#token=" + "T".repeat(43) + "&amp;category=REPLY\"")
        .contains(">退订这类邮件</a>")
        .contains("href=\"" + BASE + "/settings/notifications\"")
        .contains(">通知设置</a>")
        .doesNotContain("${")
        .doesNotContain("th:text")
        .doesNotContain("th:each");
  }

  /// 多条合一：引导句、每条的动作、时间、在哪与自己的链接，底部打开提醒页。
  @Test
  void batch_html_lists_items_with_their_links() {
    ReminderMailContent content =
        COMPOSER.instant(
            NotificationCategory.REPLY,
            List.of(facts(1L, "王立", AT, "甲"), facts(2L, "周敏", AT.plusSeconds(180), "乙")),
            Map.of(),
            "T".repeat(43),
            AT.plus(Duration.ofMinutes(10)));

    RenderedMail mail = render("notification-batch", content);

    assertThat(mail.html())
        .contains("2 条新回复")
        .contains("你不在 Patra 上的这段时间，有 2 条回复还没看。")
        .contains("15:02")
        .contains("15:05")
        .contains("回复了你")
        .contains(BASE + "/papers/18?comment=501")
        .contains(BASE + "/papers/18?comment=502")
        .contains("打开提醒页 →")
        .contains("href=\"" + BASE + "/notifications\"");
  }

  /// 每日汇总：两节、节标题、超过 10 条的说明、退订每日汇总。
  @Test
  void digest_html_has_two_sections_and_overflow() {
    ReminderMailContent content =
        COMPOSER.digest(
            DigestWindow.endingAt(Instant.parse("2026-10-31T00:00:00Z")),
            Set.of(NotificationCategory.REPLY, NotificationCategory.MENTION),
            DigestSectionFacts.of(9, List.of(facts(1L, "王立", AT, "甲"))),
            DigestSectionFacts.of(5, List.of(facts(2L, "周敏", AT, "乙"))),
            Map.of(),
            "T".repeat(43));

    RenderedMail mail = render("notification-digest", content);

    assertThat(mail.html())
        .contains("每日汇总 · 10 月 31 日")
        .contains("过去 24 小时有 14 条未读")
        .contains("回复我的 · 9 条")
        .contains("@ 我的 · 5 条")
        .contains("还有 12 条没有列出（回复 8 条、@ 4 条），到提醒页查看。")
        .contains(">退订每日汇总</a>")
        .contains("category=DIGEST");
    assertThat(mail.text()).contains("回复我的 · 9 条").contains("@ 我的 · 5 条");
  }

  /// 摘录里的提及用链接色的 span。
  @Test
  void mention_segments_use_link_color_span() {
    ReminderMailContent content =
        COMPOSER.instant(
            NotificationCategory.MENTION,
            List.of(
                ReminderFacts.builder()
                    .notificationId(1L)
                    .type(NotificationType.MENTION)
                    .actorName("林知远")
                    .occurredAt(AT)
                    .plainText("<@7> 你看")
                    .subject(PAPER)
                    .commentId(500L)
                    .build()),
            Map.of(7L, "chenyu"),
            "T".repeat(43),
            AT);

    assertThat(render("notification-single", content).html())
        .contains("color:#97441e;font-weight:500;\">@chenyu</span>");
  }

  /// 摘录与标题里的字面标签按文字输出，不执行。
  @Test
  void escapes_literal_markup_in_excerpt_and_title() {
    RenderedMail mail =
        render("notification-single", single("看这个 <b>粗</b> <script>alert(1)</script>"));

    assertThat(mail.html())
        .contains("&lt;b&gt;粗&lt;/b&gt;")
        .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
        .contains("Effect of Vitamin C &amp; Sepsis &lt;script&gt;x&lt;/script&gt;")
        .doesNotContain("<script>");
  }

  /// 纯文本部分照简报 §3.11：标题、在哪、名字与时间、摘录、链接、页脚四行。
  @Test
  void text_parts_follow_brief_layout() {
    RenderedMail mail = render("notification-single", single("不过作者自己也写了"));

    assertThat(mail.text())
        .contains("王立 回复了你\n")
        .contains("文献讨论：Effect of Vitamin C & Sepsis <script>x</script>\n")
        .contains("王立 · 10 月 30 日 15:02\n")
        .contains("不过作者自己也写了\n")
        .contains("查看并回复：" + BASE + "/papers/18?comment=500\n")
        .contains("——\n你收到这封邮件，是因为你把「回复我的」设为离站时逐条发送。\n")
        .contains("退订这类邮件：" + BASE + "/unsubscribe#token=")
        .contains("通知设置：" + BASE + "/settings/notifications\n")
        .contains("此邮件由 Patra 系统自动发出，直接回复不会出现在讨论里。");
  }

  /// 安全邮件不设新变量：版权页仍是「请勿回复」，卡片外一句仍是纯文字，没有预览块。
  @Test
  void security_layout_without_new_variables_is_unchanged() {
    RenderedMail mail =
        renderer.render(
            OutgoingMail.builder()
                .deliveryId(UUID.randomUUID())
                .to("chen@example.com")
                .template("email-verification")
                .subject("验证邮箱")
                .model(
                    Map.of("displayName", "陈宇", "verifyUrl", BASE + "/verify", "linkExpiresAt", AT))
                .build());

    assertThat(mail.html())
        .contains("此邮件由 Patra 系统自动发出，请勿回复。")
        .doesNotContain("直接回复不会出现在讨论里")
        .doesNotContain("display:none")
        .doesNotContain("通知设置</a>");
  }

  /// 一封单条回复邮件的内容。
  ///
  /// @param plainText plainText
  /// @return 结果
  private static ReminderMailContent single(String plainText) {
    return COMPOSER.instant(
        NotificationCategory.REPLY,
        List.of(
            ReminderFacts.builder()
                .notificationId(1L)
                .type(NotificationType.COMMENT_REPLY)
                .actorName("王立")
                .occurredAt(AT)
                .plainText(plainText)
                .subject(PAPER)
                .commentId(500L)
                .build()),
        Map.of(),
        "T".repeat(43),
        AT);
  }

  /// 拼一条回复提醒的事实。
  ///
  /// @param id id
  /// @param actor actor
  /// @param at at
  /// @param text text
  /// @return 结果
  private static ReminderFacts facts(long id, String actor, Instant at, String text) {
    return ReminderFacts.builder()
        .notificationId(id)
        .type(NotificationType.COMMENT_REPLY)
        .actorName(actor)
        .occurredAt(at)
        .plainText(text)
        .subject(PAPER)
        .commentId(500L + id)
        .build();
  }

  /// 照 app 的 `ReminderMailModel` 组模板变量（infra 不依赖 app，这里重写一遍同样的键）。
  private RenderedMail render(String template, ReminderMailContent content) {
    Map<String, Object> model = new HashMap<>();
    model.put("mail", content);
    if (content.preheader() != null) {
      model.put("preheader", content.preheader());
    }
    model.put("footnote", "此邮件由 Patra 系统自动发出，直接回复不会出现在讨论里。");
    model.put(
        "asideLinks",
        List.of(
            new Link(content.unsubscribeLabel(), content.unsubscribeUrl()),
            new Link("通知设置", content.preferencesUrl())));
    return renderer.render(
        OutgoingMail.builder()
            .deliveryId(UUID.randomUUID())
            .to("chen@example.com")
            .template(template)
            .subject(content.subject())
            .model(model)
            .build());
  }

  /// 卡片外的链接（同 app 的 `AsideLink`）。
  ///
  /// @param label 文字
  /// @param url 链接
  record Link(String label, String url) {}
}
