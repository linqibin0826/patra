package dev.patra.identity.infra.account.mail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionCancelledMail;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionCompletedMail;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionReminderMail;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionRequestedMail;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangeConfirmMail;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangeRevertedMail;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangedConfirmationMail;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangedNoticeMail;
import dev.patra.identity.domain.account.model.vo.mail.EmailVerificationMail;
import dev.patra.identity.domain.account.model.vo.mail.NewDeviceLoginMail;
import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.model.vo.mail.PasswordChangedMail;
import dev.patra.identity.domain.account.model.vo.mail.PasswordResetDoneMail;
import dev.patra.identity.domain.account.model.vo.mail.PasswordResetMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ThymeleafMailRenderer 单元测试：验证邮件的文本与 HTML、转义、安全通知片段。
@DisplayName("ThymeleafMailRenderer 单元测试")
class ThymeleafMailRendererTest {

  private static final MailLinkPolicy LINKS =
      MailLinkPolicy.builder()
          .baseUrl("https://patra.example")
          .verifyEmailPath("/verify-email")
          .securitySettingsPath("/settings/security")
          .resetPasswordPath("/reset-password")
          .confirmEmailChangePath("/confirm-email-change")
          .revertEmailChangePath("/revert-email-change")
          .build();
  private static final String VERIFY_URL = "https://patra.example/verify-email#token=abc";
  private static final String RESET_URL = "https://patra.example/reset-password#token=rst";
  private static final String CONFIRM_URL = "https://patra.example/confirm-email-change#token=cfm";
  private static final String REVERT_URL = "https://patra.example/revert-email-change#token=rvt";
  private static final EmailAddress TO = EmailAddress.of("chen.yu@example.com");
  private static final SecurityNotice NOTICE =
      SecurityNotice.builder()
          .occurredAt(Instant.parse("2026-10-22T08:30:00Z"))
          .notMeAdvice("不是我怎么办的一句话")
          .build();
  private static final List<OutgoingMail> SEVEN =
      List.of(
          PasswordResetMail.of(TO, "陈宇", RESET_URL),
          PasswordChangedMail.of(TO, "陈宇", NOTICE),
          PasswordResetDoneMail.of(TO, "陈宇", NOTICE),
          EmailChangeConfirmMail.of(TO, "陈宇", CONFIRM_URL),
          EmailChangedNoticeMail.builder()
              .to(TO)
              .displayName("陈宇")
              .newEmailMasked("n***@example.com")
              .revertUrl(REVERT_URL)
              .notice(NOTICE)
              .build(),
          EmailChangedConfirmationMail.of(TO, "陈宇", NOTICE),
          EmailChangeRevertedMail.of(TO, "陈宇", RESET_URL, NOTICE));

  private final ThymeleafMailRenderer renderer =
      new ThymeleafMailRenderer(LINKS, ZoneId.of("Asia/Shanghai"));

  @Test
  @DisplayName("验证邮件：主题固定；文本和 HTML 都含昵称、链接、有效期；HTML 里链接只挂在按钮上、不另外列出全文，且没有残留的 th: 属性")
  void should_render_verification_mail_in_text_and_html() {
    RenderedMail rendered =
        renderer.render(
            EmailVerificationMail.of(
                EmailAddress.of("chen.yu@example.com"),
                "陈宇",
                VERIFY_URL,
                Instant.parse("2026-10-22T08:00:00Z")));

    assertThat(rendered.subject()).isEqualTo("验证你的 Patra 邮箱");
    assertThat(rendered.text())
        .contains("陈宇")
        .contains(VERIFY_URL)
        .contains("24 小时")
        .contains("Patra 不会在邮件里向你索要密码")
        .doesNotContain("<")
        .doesNotContain("[(");
    assertThat(rendered.html())
        .containsOnlyOnce("href=\"" + VERIFY_URL + "\"")
        .doesNotContain(VERIFY_URL + "</")
        .contains("陈宇")
        .contains("24 小时")
        .contains("Patra 不会在邮件里向你索要密码")
        .doesNotContainPattern("\\sth:[a-z]+=")
        .doesNotContain("${");
  }

  @Test
  @DisplayName("十三封的 HTML 都声明支持浅色与深色两种配色，客户端按系统主题切换而不是自己反色")
  void should_declare_light_and_dark_color_schemes_in_every_html() {
    List<OutgoingMail> all = new ArrayList<>(SEVEN);
    all.add(EmailVerificationMail.of(TO, "陈宇", VERIFY_URL, Instant.EPOCH));
    all.add(NewDeviceLoginMail.of(TO, "陈宇", NOTICE));
    all.add(AccountDeletionRequestedMail.of(TO, "陈宇", DUE, NOTICE));
    all.add(AccountDeletionReminderMail.of(TO, "陈宇", DUE, 3));
    all.add(AccountDeletionCancelledMail.of(TO, "陈宇", NOTICE));
    all.add(AccountDeletionCompletedMail.of(TO, "陈宇", "chen-yu"));

    assertThat(all).hasSize(13);
    for (OutgoingMail mail : all) {
      assertThat(renderer.render(mail).html())
          .as(mail.template())
          .contains("<meta name=\"color-scheme\" content=\"light dark\">")
          .contains("@media (prefers-color-scheme: dark)");
    }
  }

  @Test
  @DisplayName("昵称里的 HTML 在 HTML 部分被转义，在文本部分原样")
  void should_escape_html_in_display_name_but_keep_text_raw() {
    RenderedMail rendered =
        renderer.render(
            EmailVerificationMail.of(
                EmailAddress.of("a@example.com"), "<b>x</b>", VERIFY_URL, Instant.EPOCH));

    assertThat(rendered.html()).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>");
    assertThat(rendered.text()).contains("<b>x</b>");
  }

  @Test
  @DisplayName("安全通知片段：时间按 Asia/Shanghai 显示并标 UTC+8，缺失的设备和 IP 显示「未知」；片段本身不带安全设置链接")
  void should_render_security_notice_fragment_with_unknown_placeholders() {
    OutgoingMail probe =
        probe(
            SecurityNotice.builder()
                .occurredAt(Instant.parse("2026-10-21T08:30:00Z"))
                .notMeAdvice("请立即修改密码")
                .build());

    RenderedMail rendered = renderer.render(probe);

    assertThat(rendered.subject()).isEqualTo("probe");
    assertThat(rendered.text())
        .contains("2026-10-21 16:30 (UTC+8)")
        .contains("设备：未知")
        .contains("IP：未知")
        .contains("请立即修改密码")
        .doesNotContain("/settings/security");
    assertThat(rendered.html())
        .contains("2026-10-21 16:30 (UTC+8)")
        .contains("未知")
        .contains("请立即修改密码")
        .doesNotContain("/settings/security")
        .doesNotContainPattern("\\sth:[a-z]+=");
  }

  @Test
  @DisplayName("安全通知片段：设备、IP、属地齐全时原样显示")
  void should_render_security_notice_fields_when_present() {
    OutgoingMail probe =
        probe(
            SecurityNotice.builder()
                .occurredAt(Instant.parse("2026-10-21T08:30:00Z"))
                .device("Chrome 131 · macOS")
                .ipAddress("203.0.113.9")
                .ipLocation("中国 上海")
                .notMeAdvice("x")
                .build());

    RenderedMail rendered = renderer.render(probe);

    assertThat(rendered.text()).contains("设备：Chrome 131 · macOS").contains("IP：203.0.113.9（中国 上海）");
    assertThat(rendered.html())
        .contains("Chrome 131 · macOS")
        .contains("203.0.113.9")
        .contains("中国 上海");
  }

  @Test
  @DisplayName("七个模板的文本与 HTML 都渲染得出来：含昵称、主题，无残留的 th: 与表达式")
  void should_render_all_seven_templates_in_text_and_html() {
    for (OutgoingMail mail : SEVEN) {
      RenderedMail rendered = renderer.render(mail);
      assertThat(rendered.subject()).as(mail.template()).isEqualTo(mail.subject());
      assertThat(rendered.text())
          .as(mail.template())
          .contains("陈宇")
          .contains("Patra 不会在邮件里向你索要密码")
          .doesNotContain("[(")
          .doesNotContain("<");
      assertThat(rendered.html())
          .as(mail.template())
          .contains("陈宇")
          .contains("Patra 不会在邮件里向你索要密码")
          .doesNotContainPattern("\\sth:[a-z]+=")
          .doesNotContain("${");
    }
  }

  @Test
  @DisplayName("带链接的四封：链接在文本里原样出现；在 HTML 里只挂在按钮上，不另外列出全文")
  void should_put_links_in_both_parts() {
    assertLink(renderer.render(PasswordResetMail.of(TO, "陈宇", RESET_URL)), RESET_URL, "60 分钟");
    assertLink(
        renderer.render(EmailChangeConfirmMail.of(TO, "陈宇", CONFIRM_URL)), CONFIRM_URL, "60 分钟");
    assertLink(renderer.render(SEVEN.get(4)), REVERT_URL, "7 天");
    assertLink(
        renderer.render(EmailChangeRevertedMail.of(TO, "陈宇", RESET_URL, NOTICE)),
        RESET_URL,
        "60 分钟");
  }

  @Test
  @DisplayName("「密码已修改」不再建议到安全设置改密码：不是本人时旧密码已失效、登不进去，片段里的「忘记密码」指引就够了")
  void should_not_point_password_changed_mail_to_security_settings() {
    RenderedMail rendered =
        renderer.render(
            PasswordChangedMail.of(
                TO,
                "陈宇",
                SecurityNotice.builder()
                    .occurredAt(Instant.parse("2026-10-22T08:30:00Z"))
                    .notMeAdvice(PasswordChangedMail.NOT_ME_ADVICE)
                    .build()));

    assertThat(rendered.text()).contains("忘记密码").doesNotContain("/settings/security");
    assertThat(rendered.html()).contains("忘记密码").doesNotContain("/settings/security");
  }

  @Test
  @DisplayName("旧邮箱的通知里「撤销」只说一遍：正文不再重复片段里的那句")
  void should_say_revert_advice_once_in_old_address_notice() {
    RenderedMail rendered =
        renderer.render(
            EmailChangedNoticeMail.builder()
                .to(TO)
                .displayName("陈宇")
                .newEmailMasked("n***@example.com")
                .revertUrl(REVERT_URL)
                .notice(
                    SecurityNotice.builder()
                        .occurredAt(Instant.parse("2026-10-22T08:30:00Z"))
                        .notMeAdvice(EmailChangedNoticeMail.NOT_ME_ADVICE)
                        .build())
                .build());

    assertThat(rendered.text()).containsOnlyOnce("邮箱会改回本地址").doesNotContain("不是你本人的操作？如果不是你");
    assertThat(rendered.html()).containsOnlyOnce("邮箱会改回本地址");
  }

  @Test
  @DisplayName("「已改回请重设密码」那封的「不是你本人的操作？」后面接的是邮箱可能被人看到的提醒，不是「账号已锁定」")
  void should_answer_not_me_in_reverted_mail_with_mailbox_advice() {
    RenderedMail rendered =
        renderer.render(
            EmailChangeRevertedMail.of(
                TO,
                "陈宇",
                RESET_URL,
                SecurityNotice.builder()
                    .occurredAt(Instant.parse("2026-10-22T08:30:00Z"))
                    .notMeAdvice(EmailChangeRevertedMail.NOT_ME_ADVICE)
                    .build()));

    assertThat(EmailChangeRevertedMail.NOT_ME_ADVICE).doesNotContain("账号已锁定").contains("邮箱");
    assertThat(rendered.text()).contains("不是你本人的操作？" + EmailChangeRevertedMail.NOT_ME_ADVICE);
  }

  @Test
  @DisplayName("安全通知四项都在；「到安全设置」一句只在需要它的模板里，「已改回请重设密码」和「密码已修改」那两封没有")
  void should_render_notice_four_items_without_fixed_security_line_in_fragment() {
    RenderedMail reverted =
        renderer.render(EmailChangeRevertedMail.of(TO, "陈宇", RESET_URL, NOTICE));
    assertThat(reverted.text())
        .contains("时间：2026-10-22 16:30 (UTC+8)")
        .contains("设备：未知")
        .contains("IP：未知")
        .contains("不是我怎么办的一句话")
        .doesNotContain("/settings/security");
    assertThat(reverted.html()).doesNotContain("/settings/security");

    for (OutgoingMail mail :
        List.of(SEVEN.get(4), EmailChangedConfirmationMail.of(TO, "陈宇", NOTICE))) {
      RenderedMail rendered = renderer.render(mail);
      assertThat(rendered.text())
          .as(mail.template())
          .contains("https://patra.example/settings/security");
      assertThat(rendered.html())
          .as(mail.template())
          .contains("href=\"https://patra.example/settings/security\"");
    }
    assertThat(renderer.render(PasswordResetDoneMail.of(TO, "陈宇", NOTICE)).text())
        .contains("不是我怎么办的一句话")
        .doesNotContain("/settings/security");
  }

  @Test
  @DisplayName("旧邮箱的通知里新地址是遮掩过的")
  void should_mask_new_email_in_old_address_notice() {
    RenderedMail rendered = renderer.render(SEVEN.get(4));

    assertThat(rendered.text()).contains("n***@example.com");
    assertThat(rendered.html()).contains("n***@example.com");
  }

  /// 断言链接在文本里原样出现、在 HTML 里只挂在按钮上，有效期文案也在。
  ///
  /// @param rendered 渲染结果
  /// @param url 链接
  /// @param validity 有效期文案
  private static void assertLink(RenderedMail rendered, String url, String validity) {
    assertThat(rendered.text()).contains(url).contains(validity);
    assertThat(rendered.html())
        .containsOnlyOnce("href=\"" + url + "\"")
        .doesNotContain(url + "</")
        .contains(validity);
  }

  /// 测试资源里的探针模板 `security-notice-probe`，正文只放安全通知片段。
  ///
  /// @param notice 通知上下文
  /// @return 邮件
  private static OutgoingMail probe(SecurityNotice notice) {
    return new OutgoingMail() {
      @Override
      public EmailAddress to() {
        return EmailAddress.of("probe@example.com");
      }

      @Override
      public String template() {
        return "security-notice-probe";
      }

      @Override
      public String subject() {
        return "probe";
      }

      @Override
      public Map<String, Object> model() {
        return Map.of("notice", notice);
      }
    };
  }

  @Test
  @DisplayName("新设备登录：文本与 HTML 都有设备、IP 与属地、「不是我」一句和安全设置链接")
  void should_render_new_device_login_mail() {
    RenderedMail rendered =
        renderer.render(
            NewDeviceLoginMail.of(
                TO,
                "陈宇",
                SecurityNotice.builder()
                    .occurredAt(Instant.parse("2026-10-22T08:30:00Z"))
                    .device("Chrome 141 · macOS")
                    .ipAddress("113.92.157.29")
                    .ipLocation("中国 广东 深圳")
                    .notMeAdvice(NewDeviceLoginMail.NOT_ME_ADVICE)
                    .build()));

    assertThat(rendered.subject()).isEqualTo("你的 Patra 账号在新设备上登录");
    for (String part : new String[] {rendered.text(), rendered.html()}) {
      assertThat(part)
          .contains("陈宇")
          .contains("新设备")
          .contains("Chrome 141 · macOS")
          .contains("113.92.157.29")
          .contains("中国 广东 深圳")
          .contains("修改密码")
          .contains("/settings/security");
    }
    assertThat(rendered.text()).doesNotContain("[(").doesNotContain("<");
    assertThat(rendered.html()).doesNotContainPattern("\\sth:[a-z]+=").doesNotContain("${");
  }

  /// 注销到期时刻：北京时间 2026-11-04 08:00。
  private static final Instant DUE = Instant.parse("2026-11-04T00:00:00Z");

  /// 带设备与属地的通知。
  private static final SecurityNotice FULL_NOTICE =
      SecurityNotice.builder()
          .occurredAt(Instant.parse("2026-10-20T08:00:00Z"))
          .device("Chrome 141 · macOS")
          .ipAddress("113.92.157.29")
          .ipLocation("中国 广东 深圳")
          .notMeAdvice(AccountDeletionRequestedMail.NOT_ME_ADVICE)
          .build();

  @Test
  @DisplayName("注销已提交：文本与 HTML 都有到期时刻（按时区）、昵称、设备与 IP")
  void should_render_deletion_requested_with_due_time_and_notice() {
    RenderedMail rendered =
        renderer.render(AccountDeletionRequestedMail.of(TO, "陈宇", DUE, FULL_NOTICE));

    assertThat(rendered.subject()).isEqualTo("你的 Patra 账号将被注销");
    for (String part : new String[] {rendered.text(), rendered.html()}) {
      assertThat(part)
          .contains("陈宇")
          .contains("2026-11-04 08:00 (UTC+8)")
          .contains("Chrome 141 · macOS")
          .contains("113.92.157.29")
          .contains(AccountDeletionRequestedMail.NOT_ME_ADVICE);
    }
  }

  @Test
  @DisplayName("即将删除提醒：主题按剩余天数拼，含到期时刻，不带安全通知片段")
  void should_render_deletion_reminder_with_days_left_subject() {
    RenderedMail rendered = renderer.render(AccountDeletionReminderMail.of(TO, "陈宇", DUE, 3));

    assertThat(rendered.subject()).isEqualTo("你的 Patra 账号将在 3 天后删除");
    for (String part : new String[] {rendered.text(), rendered.html()}) {
      assertThat(part).contains("陈宇").contains("2026-11-04 08:00 (UTC+8)").doesNotContain("设备：");
    }
  }

  @Test
  @DisplayName("已取消注销：带安全通知片段与「不是我」一句")
  void should_render_deletion_cancelled_with_notice() {
    RenderedMail rendered =
        renderer.render(
            AccountDeletionCancelledMail.of(
                TO,
                "陈宇",
                SecurityNotice.builder()
                    .occurredAt(Instant.parse("2026-10-21T08:00:00Z"))
                    .notMeAdvice(AccountDeletionCancelledMail.NOT_ME_ADVICE)
                    .build()));

    assertThat(rendered.subject()).isEqualTo("你的 Patra 账号已取消注销");
    for (String part : new String[] {rendered.text(), rendered.html()}) {
      assertThat(part).contains("陈宇").contains(AccountDeletionCancelledMail.NOT_ME_ADVICE);
    }
  }

  @Test
  @DisplayName("已注销：带用户名，不带安全通知片段")
  void should_render_deletion_completed_with_username() {
    RenderedMail rendered = renderer.render(AccountDeletionCompletedMail.of(TO, "陈宇", "chen-yu"));

    assertThat(rendered.subject()).isEqualTo("你的 Patra 账号已注销");
    for (String part : new String[] {rendered.text(), rendered.html()}) {
      assertThat(part).contains("陈宇").contains("@chen-yu").doesNotContain("设备：");
    }
  }

  @Test
  @DisplayName("注销的四封邮件不放任何站内链接，也没有残留的模板语法")
  void should_not_link_anywhere_in_deletion_mails() {
    List<OutgoingMail> mails =
        List.of(
            AccountDeletionRequestedMail.of(TO, "陈宇", DUE, FULL_NOTICE),
            AccountDeletionReminderMail.of(TO, "陈宇", DUE, 3),
            AccountDeletionCancelledMail.of(TO, "陈宇", NOTICE),
            AccountDeletionCompletedMail.of(TO, "陈宇", "chen-yu"));

    for (OutgoingMail mail : mails) {
      RenderedMail rendered = renderer.render(mail);
      assertThat(rendered.text())
          .doesNotContain("https://patra.example")
          .doesNotContain("#token=")
          .doesNotContain("[(");
      assertThat(rendered.html())
          .doesNotContain("https://patra.example")
          .doesNotContainPattern("\\sth:[a-z]+=")
          .doesNotContain("${");
    }
  }
}
