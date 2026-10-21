package dev.patra.identity.infra.adapter.mail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.policy.MailLinkPolicy;
import dev.patra.identity.domain.port.mail.EmailVerificationMail;
import dev.patra.identity.domain.port.mail.OutgoingMail;
import dev.patra.identity.domain.port.mail.SecurityNotice;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ThymeleafMailRenderer 单元测试：验证邮件的文本与 HTML、转义、安全通知片段。
@DisplayName("ThymeleafMailRenderer 单元测试")
class ThymeleafMailRendererTest {

  private static final MailLinkPolicy LINKS =
      MailLinkPolicy.of("https://patra.example", "/verify-email", "/settings/security");
  private static final String VERIFY_URL = "https://patra.example/verify-email#token=abc";

  private final ThymeleafMailRenderer renderer =
      new ThymeleafMailRenderer(LINKS, ZoneId.of("Asia/Shanghai"));

  @Test
  @DisplayName("验证邮件：主题固定；文本和 HTML 都含昵称、链接、有效期；HTML 里按钮指向链接且没有残留的 th: 属性")
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
        .contains("href=\"" + VERIFY_URL + "\"")
        .contains(VERIFY_URL + "</")
        .contains("陈宇")
        .contains("24 小时")
        .contains("Patra 不会在邮件里向你索要密码")
        .doesNotContainPattern("\\sth:[a-z]+=")
        .doesNotContain("${");
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
  @DisplayName("安全通知片段：时间按 Asia/Shanghai 显示并标 UTC+8，缺失的设备和 IP 显示「未知」，带安全设置链接")
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
        .contains("https://patra.example/settings/security");
    assertThat(rendered.html())
        .contains("2026-10-21 16:30 (UTC+8)")
        .contains("未知")
        .contains("请立即修改密码")
        .contains("href=\"https://patra.example/settings/security\"")
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
}
