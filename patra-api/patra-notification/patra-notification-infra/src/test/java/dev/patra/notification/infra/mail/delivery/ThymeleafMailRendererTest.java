package dev.patra.notification.infra.mail.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.mail.model.enums.SecurityMailKind;
import dev.patra.notification.domain.mail.model.vo.OperationContext;
import dev.patra.notification.domain.mail.model.vo.OutgoingMail;
import dev.patra.notification.domain.mail.model.vo.SecurityMail;
import dev.patra.notification.domain.mail.model.vo.SecurityNotice;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ThymeleafMailRenderer 单元测试：13 类安全邮件的文本与 HTML、转义、安全通知片段、有效期写法与确定性。
///
/// 以 v0.16 之前 identity 的同名测试为底改写：样本改由领域的 `SecurityMail` 组成，有效期从「N 分钟内有效」
/// 改为绝对时刻（spec 2.5）。
@DisplayName("ThymeleafMailRenderer 单元测试")
class ThymeleafMailRendererTest {

  private static final String TO = "chen.yu@example.com";
  private static final String VERIFY_URL = "https://patra.example/verify-email#token=abc";
  private static final String RESET_URL = "https://patra.example/reset-password#token=rst";
  private static final String CONFIRM_URL = "https://patra.example/confirm-email-change#token=cfm";
  private static final String REVERT_URL = "https://patra.example/revert-email-change#token=rvt";
  private static final String SECURITY_URL = "https://patra.example/settings/security";

  /// 链接过期时刻：北京时间 2026-10-22 16:00。
  private static final Instant EXPIRES = Instant.parse("2026-10-22T08:00:00Z");

  /// 页面上的有效期写法。
  private static final String VALIDITY = "2026-10-22 16:00 (UTC+8) 前有效";

  /// 注销到期时刻：北京时间 2026-11-04 08:00。
  private static final Instant DUE = Instant.parse("2026-11-04T00:00:00Z");

  /// 只有时间的操作上下文：设备、IP、属地都拿不到。
  private static final OperationContext BARE =
      OperationContext.of(Instant.parse("2026-10-22T08:30:00Z"), null, null, null);

  /// 带设备与属地的操作上下文。
  private static final OperationContext FULL =
      OperationContext.of(
          Instant.parse("2026-10-20T08:00:00Z"), "Chrome 141 · macOS", "113.92.157.29", "中国 广东 深圳");

  private final ThymeleafMailRenderer renderer =
      new ThymeleafMailRenderer(ZoneId.of("Asia/Shanghai"));

  @Test
  @DisplayName("验证邮件：主题固定；文本和 HTML 都含昵称、链接、有效期；HTML 里链接只挂在按钮上，且没有残留的 th: 属性")
  void should_render_verification_mail_in_text_and_html() {
    RenderedMail rendered = renderer.render(verification());

    assertThat(rendered.subject()).isEqualTo("验证你的 Patra 邮箱");
    assertThat(rendered.text())
        .contains("陈宇")
        .contains(VERIFY_URL)
        .contains(VALIDITY)
        .contains("Patra 不会在邮件里向你索要密码")
        .doesNotContain("<")
        .doesNotContain("[(");
    assertThat(rendered.html())
        .containsOnlyOnce("href=\"" + VERIFY_URL + "\"")
        .doesNotContain(VERIFY_URL + "</")
        .contains("陈宇")
        .contains(VALIDITY)
        .contains("Patra 不会在邮件里向你索要密码")
        .doesNotContainPattern("\\sth:[a-z]+=")
        .doesNotContain("${");
  }

  @Test
  @DisplayName("十三封都渲染得出来：主题对、含昵称与页脚，无残留的 th: 与表达式，都声明浅色与深色两种配色")
  void should_render_every_kind_without_leftover_expressions() {
    List<OutgoingMail> all = all();

    assertThat(all).extracting(OutgoingMail::template).hasSize(13).doesNotHaveDuplicates();
    for (OutgoingMail mail : all) {
      RenderedMail rendered = renderer.render(mail);
      assertThat(rendered.subject()).as(mail.template()).isEqualTo(mail.subject());
      assertThat(rendered.text())
          .as(mail.template())
          .contains("陈宇")
          .contains("Patra 不会在邮件里向你索要密码")
          .doesNotContain("[(")
          .doesNotContain("${")
          .doesNotContain("<");
      assertThat(rendered.html())
          .as(mail.template())
          .contains("陈宇")
          .contains("<meta name=\"color-scheme\" content=\"light dark\">")
          .contains("@media (prefers-color-scheme: dark)")
          .doesNotContainPattern("\\sth:[a-z]+=")
          .doesNotContain("${");
    }
  }

  @Test
  @DisplayName("同一封信渲染两次逐字节相同：Resend 对同一个幂等键配不同内容会拒收（spec 2.5）")
  void should_render_identically_twice() {
    for (OutgoingMail mail : all()) {
      assertThat(renderer.render(mail)).as(mail.template()).isEqualTo(renderer.render(mail));
      RenderedMail first = renderer.render(mail);
      RenderedMail second = renderer.render(mail);
      assertThat(first.text()).as(mail.template()).isEqualTo(second.text());
      assertThat(first.html()).as(mail.template()).isEqualTo(second.html());
    }
  }

  @Test
  @DisplayName("昵称里的 HTML 在 HTML 部分被转义，在文本部分原样")
  void should_escape_html_in_display_name_but_keep_text_raw() {
    RenderedMail rendered =
        renderer.render(
            SecurityMail.builder()
                .eventId(UUID.randomUUID())
                .kind(SecurityMailKind.EMAIL_VERIFICATION)
                .to(TO)
                .displayName("<b>x</b>")
                .facts(Map.of("verifyUrl", VERIFY_URL))
                .linkExpiresAt(EXPIRES)
                .build()
                .compose());

    assertThat(rendered.html()).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>");
    assertThat(rendered.text()).contains("<b>x</b>");
  }

  @Test
  @DisplayName("安全通知片段：时间按 Asia/Shanghai 显示并标 UTC+8，缺失的设备和 IP 显示「未知」；片段本身不带安全设置链接")
  void should_render_security_notice_fragment_with_unknown_placeholders() {
    RenderedMail rendered =
        renderer.render(
            probe(
                SecurityNotice.of(
                    OperationContext.of(Instant.parse("2026-10-21T08:30:00Z"), null, null, null),
                    "请立即修改密码")));

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
  @DisplayName("安全通知片段：设备、IP、属地齐全时原样显示，IP 后括号里是属地")
  void should_render_security_notice_fields_when_present() {
    RenderedMail rendered = renderer.render(probe(SecurityNotice.of(FULL, "x")));

    assertThat(rendered.text())
        .contains("设备：Chrome 141 · macOS")
        .contains("IP：113.92.157.29（中国 广东 深圳）");
    assertThat(rendered.html())
        .contains("Chrome 141 · macOS")
        .contains("113.92.157.29")
        .contains("中国 广东 深圳");
  }

  @Test
  @DisplayName("带链接的五封：链接在文本里原样出现；在 HTML 里只挂在按钮上；有效期写成绝对时刻")
  void should_put_links_and_absolute_validity_in_both_parts() {
    assertLink(renderer.render(verification()), VERIFY_URL);
    assertLink(renderer.render(passwordReset()), RESET_URL);
    assertLink(
        renderer.render(
            linkMail(
                SecurityMailKind.EMAIL_CHANGE_CONFIRM, Map.of("confirmUrl", CONFIRM_URL), null)),
        CONFIRM_URL);
    assertLink(renderer.render(changedNotice()), REVERT_URL);
    assertLink(
        renderer.render(
            linkMail(SecurityMailKind.EMAIL_CHANGE_REVERTED, Map.of("resetUrl", RESET_URL), BARE)),
        RESET_URL);
  }

  @Test
  @DisplayName("有效期不再写「N 分钟 / 小时 / 天内有效」：随发送时刻变的文字会让同一个幂等键配上不同内容")
  void should_not_render_relative_validity() {
    for (OutgoingMail mail : all()) {
      RenderedMail rendered = renderer.render(mail);
      assertThat(rendered.text() + rendered.html())
          .as(mail.template())
          .doesNotContainPattern("\\d+ (分钟|小时|天)内有效");
    }
  }

  @Test
  @DisplayName("「密码已修改」不建议到安全设置改密码：不是本人时旧密码已失效、登不进去，片段里的「忘记密码」指引就够了")
  void should_not_point_password_changed_mail_to_security_settings() {
    RenderedMail rendered = renderer.render(noticeMail(SecurityMailKind.PASSWORD_CHANGED, BARE));

    assertThat(rendered.text()).contains("忘记密码").doesNotContain("/settings/security");
    assertThat(rendered.html()).contains("忘记密码").doesNotContain("/settings/security");
  }

  @Test
  @DisplayName("旧邮箱的通知里「撤销」只说一遍；新地址是遮掩过的")
  void should_say_revert_advice_once_and_mask_new_email_in_old_address_notice() {
    RenderedMail rendered = renderer.render(changedNotice());

    assertThat(rendered.text())
        .containsOnlyOnce("邮箱会改回本地址")
        .doesNotContain("不是你本人的操作？如果不是你")
        .contains("n***@example.com");
    assertThat(rendered.html()).containsOnlyOnce("邮箱会改回本地址").contains("n***@example.com");
  }

  @Test
  @DisplayName("「已改回请重设密码」那封的「不是你本人的操作？」后面接的是邮箱可能被人看到的提醒")
  void should_answer_not_me_in_reverted_mail_with_mailbox_advice() {
    RenderedMail rendered =
        renderer.render(
            linkMail(SecurityMailKind.EMAIL_CHANGE_REVERTED, Map.of("resetUrl", RESET_URL), BARE));

    assertThat(rendered.text())
        .contains("不是你本人的操作？" + SecurityMailKind.EMAIL_CHANGE_REVERTED.notMeAdvice());
  }

  @Test
  @DisplayName("「到安全设置」的链接只在带它的三封里（来自事件的 securitySettingsUrl），其余没有")
  void should_render_security_url_only_where_present() {
    for (OutgoingMail mail :
        List.of(
            changedNotice(),
            securityLinkMail(SecurityMailKind.EMAIL_CHANGED_CONFIRMATION),
            securityLinkMail(SecurityMailKind.NEW_DEVICE_LOGIN))) {
      RenderedMail rendered = renderer.render(mail);
      assertThat(rendered.text()).as(mail.template()).contains(SECURITY_URL);
      assertThat(rendered.html()).as(mail.template()).contains("href=\"" + SECURITY_URL + "\"");
    }
    for (SecurityMailKind kind :
        List.of(
            SecurityMailKind.PASSWORD_RESET_DONE,
            SecurityMailKind.PASSWORD_CHANGED,
            SecurityMailKind.ACCOUNT_DELETION_CANCELLED)) {
      assertThat(renderer.render(noticeMail(kind, BARE)).text())
          .as(kind.template())
          .contains(kind.notMeAdvice())
          .doesNotContain("/settings/security");
    }
  }

  @Test
  @DisplayName("新设备登录：文本与 HTML 都有设备、IP 与属地、「不是我」一句和安全设置链接")
  void should_render_new_device_login_mail() {
    RenderedMail rendered =
        renderer.render(
            SecurityMail.builder()
                .eventId(UUID.randomUUID())
                .kind(SecurityMailKind.NEW_DEVICE_LOGIN)
                .to(TO)
                .displayName("陈宇")
                .facts(Map.of("securityUrl", SECURITY_URL))
                .context(FULL)
                .build()
                .compose());

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
  }

  @Test
  @DisplayName("注销已提交：文本与 HTML 都有到期时刻（按时区）、昵称、设备与 IP、「不是我」一句")
  void should_render_deletion_requested_with_due_time_and_notice() {
    RenderedMail rendered =
        renderer.render(
            SecurityMail.builder()
                .eventId(UUID.randomUUID())
                .kind(SecurityMailKind.ACCOUNT_DELETION_REQUESTED)
                .to(TO)
                .displayName("陈宇")
                .facts(Map.of("deletionDueAt", DUE))
                .context(FULL)
                .build()
                .compose());

    assertThat(rendered.subject()).isEqualTo("你的 Patra 账号将被注销");
    for (String part : new String[] {rendered.text(), rendered.html()}) {
      assertThat(part)
          .contains("陈宇")
          .contains("2026-11-04 08:00 (UTC+8)")
          .contains("Chrome 141 · macOS")
          .contains("113.92.157.29")
          .contains(SecurityMailKind.ACCOUNT_DELETION_REQUESTED.notMeAdvice());
    }
  }

  @Test
  @DisplayName("即将删除提醒：主题按剩余天数拼，含到期时刻，不带安全通知片段")
  void should_render_deletion_reminder_with_days_left_subject() {
    RenderedMail rendered = renderer.render(reminder());

    assertThat(rendered.subject()).isEqualTo("你的 Patra 账号将在 3 天后删除");
    for (String part : new String[] {rendered.text(), rendered.html()}) {
      assertThat(part).contains("陈宇").contains("2026-11-04 08:00 (UTC+8)").doesNotContain("设备：");
    }
  }

  @Test
  @DisplayName("已注销：带用户名，不带安全通知片段")
  void should_render_deletion_completed_with_username() {
    RenderedMail rendered = renderer.render(completed());

    assertThat(rendered.subject()).isEqualTo("你的 Patra 账号已注销");
    for (String part : new String[] {rendered.text(), rendered.html()}) {
      assertThat(part).contains("陈宇").contains("@chen-yu").doesNotContain("设备：");
    }
  }

  @Test
  @DisplayName("注销的四封邮件不放任何站内链接")
  void should_not_link_anywhere_in_deletion_mails() {
    for (OutgoingMail mail :
        List.of(
            noticeMail(SecurityMailKind.ACCOUNT_DELETION_CANCELLED, BARE),
            SecurityMail.builder()
                .eventId(UUID.randomUUID())
                .kind(SecurityMailKind.ACCOUNT_DELETION_REQUESTED)
                .to(TO)
                .displayName("陈宇")
                .facts(Map.of("deletionDueAt", DUE))
                .context(FULL)
                .build()
                .compose(),
            reminder(),
            completed())) {
      RenderedMail rendered = renderer.render(mail);
      assertThat(rendered.text()).as(mail.template()).doesNotContain("https://patra.example");
      assertThat(rendered.html()).as(mail.template()).doesNotContain("https://patra.example");
    }
  }

  /// 断言链接在文本里原样出现、在 HTML 里只挂在按钮上，绝对时刻的有效期在两部分都有。
  ///
  /// @param rendered 渲染结果
  /// @param url 链接
  private static void assertLink(RenderedMail rendered, String url) {
    assertThat(rendered.text()).contains(url).contains(VALIDITY);
    assertThat(rendered.html())
        .containsOnlyOnce("href=\"" + url + "\"")
        .doesNotContain(url + "</")
        .contains(VALIDITY);
  }

  /// 13 类各一封。
  ///
  /// @return 邮件
  private static List<OutgoingMail> all() {
    return List.of(
        verification(),
        passwordReset(),
        noticeMail(SecurityMailKind.PASSWORD_RESET_DONE, BARE),
        noticeMail(SecurityMailKind.PASSWORD_CHANGED, BARE),
        linkMail(SecurityMailKind.EMAIL_CHANGE_CONFIRM, Map.of("confirmUrl", CONFIRM_URL), null),
        changedNotice(),
        securityLinkMail(SecurityMailKind.EMAIL_CHANGED_CONFIRMATION),
        linkMail(SecurityMailKind.EMAIL_CHANGE_REVERTED, Map.of("resetUrl", RESET_URL), BARE),
        securityLinkMail(SecurityMailKind.NEW_DEVICE_LOGIN),
        SecurityMail.builder()
            .eventId(UUID.randomUUID())
            .kind(SecurityMailKind.ACCOUNT_DELETION_REQUESTED)
            .to(TO)
            .displayName("陈宇")
            .facts(Map.of("deletionDueAt", DUE))
            .context(FULL)
            .build()
            .compose(),
        noticeMail(SecurityMailKind.ACCOUNT_DELETION_CANCELLED, BARE),
        reminder(),
        completed());
  }

  /// 验证邮箱。
  ///
  /// @return 邮件
  private static OutgoingMail verification() {
    return linkMail(SecurityMailKind.EMAIL_VERIFICATION, Map.of("verifyUrl", VERIFY_URL), null);
  }

  /// 找回密码。
  ///
  /// @return 邮件
  private static OutgoingMail passwordReset() {
    return linkMail(SecurityMailKind.PASSWORD_RESET, Map.of("resetUrl", RESET_URL), null);
  }

  /// 邮箱已更改（发旧邮箱）。
  ///
  /// @return 邮件
  private static OutgoingMail changedNotice() {
    return linkMail(
        SecurityMailKind.EMAIL_CHANGED_NOTICE,
        Map.of(
            "newEmailMasked",
            "n***@example.com",
            "revertUrl",
            REVERT_URL,
            "securityUrl",
            SECURITY_URL),
        BARE);
  }

  /// 账号即将删除，还剩 3 天。
  ///
  /// @return 邮件
  private static OutgoingMail reminder() {
    return SecurityMail.builder()
        .eventId(UUID.randomUUID())
        .kind(SecurityMailKind.ACCOUNT_DELETION_REMINDER)
        .to(TO)
        .displayName("陈宇")
        .facts(Map.of("deletionDueAt", DUE, "daysLeft", 3L))
        .build()
        .compose();
  }

  /// 账号已注销。
  ///
  /// @return 邮件
  private static OutgoingMail completed() {
    return SecurityMail.builder()
        .eventId(UUID.randomUUID())
        .kind(SecurityMailKind.ACCOUNT_DELETION_COMPLETED)
        .to(TO)
        .displayName("陈宇")
        .facts(Map.of("username", "chen-yu"))
        .build()
        .compose();
  }

  /// 带一次性链接的信，链接 {@link #EXPIRES} 过期。
  ///
  /// @param kind 类别
  /// @param facts 链接等变量
  /// @param context 操作上下文，不带片段的类别为 `null`
  /// @return 邮件
  private static OutgoingMail linkMail(
      SecurityMailKind kind, Map<String, Object> facts, OperationContext context) {
    return SecurityMail.builder()
        .eventId(UUID.randomUUID())
        .kind(kind)
        .to(TO)
        .displayName("陈宇")
        .facts(facts)
        .context(context)
        .linkExpiresAt(EXPIRES)
        .build()
        .compose();
  }

  /// 带「到安全设置」链接的通知。
  ///
  /// @param kind 类别
  /// @return 邮件
  private static OutgoingMail securityLinkMail(SecurityMailKind kind) {
    return SecurityMail.builder()
        .eventId(UUID.randomUUID())
        .kind(kind)
        .to(TO)
        .displayName("陈宇")
        .facts(Map.of("securityUrl", SECURITY_URL))
        .context(BARE)
        .build()
        .compose();
  }

  /// 只带安全通知片段的信。
  ///
  /// @param kind 类别
  /// @param context 操作上下文
  /// @return 邮件
  private static OutgoingMail noticeMail(SecurityMailKind kind, OperationContext context) {
    return SecurityMail.builder()
        .eventId(UUID.randomUUID())
        .kind(kind)
        .to(TO)
        .displayName("陈宇")
        .context(context)
        .build()
        .compose();
  }

  /// 测试资源里的探针模板 `security-notice-probe`，正文只放安全通知片段。
  ///
  /// @param notice 安全通知
  /// @return 邮件
  private static OutgoingMail probe(SecurityNotice notice) {
    return OutgoingMail.builder()
        .deliveryId(UUID.randomUUID())
        .to("probe@example.com")
        .template("security-notice-probe")
        .subject("probe")
        .model(Map.of("notice", notice))
        .build();
  }
}
