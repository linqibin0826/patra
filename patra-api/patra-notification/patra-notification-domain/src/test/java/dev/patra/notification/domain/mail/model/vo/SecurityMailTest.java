package dev.patra.notification.domain.mail.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.mail.model.enums.SecurityMailKind;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SecurityMail 单元测试：组成要发的邮件、链接是否过期、不泄露内容。
@DisplayName("SecurityMail 单元测试")
class SecurityMailTest {

  private static final UUID EVENT_ID = UUID.fromString("0b6c7f3e-1d2a-4c5b-8e9f-112233445566");
  private static final String TO = "chen.yu@example.com";
  private static final String RESET_URL = "https://patra.example/reset-password#token=rst";
  private static final Instant EXPIRES = Instant.parse("2026-10-31T02:30:00Z");

  /// 投递 ID 就是事件 ID；模板名、主题取自类别；变量含昵称与这一类专属的事实。
  @Test
  void should_compose_outgoing_mail_with_event_id_as_delivery_id() {
    OutgoingMail mail = passwordReset(EXPIRES).compose();

    assertThat(mail.deliveryId()).isEqualTo(EVENT_ID);
    assertThat(mail.to()).isEqualTo(TO);
    assertThat(mail.template()).isEqualTo("password-reset");
    assertThat(mail.subject()).isEqualTo("重设你的 Patra 密码");
    assertThat(mail.model())
        .containsEntry("displayName", "陈宇")
        .containsEntry("resetUrl", RESET_URL);
  }

  /// 通知类：变量 `notice` 是带这一类「不是我怎么办」的安全通知；有链接时变量 `linkExpiresAt` 是过期时刻。
  @Test
  void should_put_notice_and_link_expiry_into_model() {
    OperationContext context =
        OperationContext.of(
            Instant.parse("2026-10-31T01:00:00Z"), "Chrome 131 · macOS", null, null);
    SecurityMail reverted =
        SecurityMail.builder()
            .eventId(EVENT_ID)
            .kind(SecurityMailKind.EMAIL_CHANGE_REVERTED)
            .to(TO)
            .displayName("陈宇")
            .facts(Map.of("resetUrl", RESET_URL))
            .context(context)
            .linkExpiresAt(EXPIRES)
            .build();

    Map<String, Object> model = reverted.compose().model();

    assertThat(model.get("notice"))
        .isEqualTo(
            SecurityNotice.of(context, SecurityMailKind.EMAIL_CHANGE_REVERTED.notMeAdvice()));
    assertThat(model).containsEntry("linkExpiresAt", EXPIRES);
  }

  /// 只有时间、设备与 IP 都拿不到的新设备登录信也能组成：可空的东西只在安全通知里，不进变量 Map。
  @Test
  void should_compose_when_context_has_only_time() {
    SecurityMail login =
        SecurityMail.builder()
            .eventId(EVENT_ID)
            .kind(SecurityMailKind.NEW_DEVICE_LOGIN)
            .to(TO)
            .displayName("陈宇")
            .facts(Map.of("securityUrl", "https://patra.example/settings/security"))
            .context(OperationContext.of(Instant.parse("2026-10-31T01:00:00Z"), null, null, null))
            .build();

    assertThat(login.compose().model()).containsKeys("notice", "securityUrl", "displayName");
  }

  /// 带安全通知片段的类别缺操作上下文时，建不出来。
  @Test
  void should_require_context_for_notice_kinds() {
    assertThatThrownBy(
            () ->
                SecurityMail.builder()
                    .eventId(EVENT_ID)
                    .kind(SecurityMailKind.PASSWORD_CHANGED)
                    .to(TO)
                    .displayName("陈宇")
                    .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("context");
  }

  /// 到了过期时刻就算过期，早一纳秒不算。
  @Test
  void should_treat_link_as_expired_at_exact_instant() {
    SecurityMail mail = passwordReset(EXPIRES);

    assertThat(mail.linkExpiredAt(EXPIRES)).isTrue();
    assertThat(mail.linkExpiredAt(EXPIRES.minusNanos(1))).isFalse();
  }

  /// 不是只为链接而发的信（邮箱已更改），链接过期了也照发。
  @Test
  void should_never_expire_notice_kinds() {
    SecurityMail notice =
        SecurityMail.builder()
            .eventId(EVENT_ID)
            .kind(SecurityMailKind.EMAIL_CHANGED_NOTICE)
            .to(TO)
            .displayName("陈宇")
            .context(OperationContext.of(EXPIRES, null, null, null))
            .linkExpiresAt(EXPIRES)
            .build();

    assertThat(notice.linkExpiredAt(EXPIRES.plusSeconds(86_400))).isFalse();
  }

  /// `toString()` 不输出收件地址与链接，只输出事件 ID 与类别。
  @Test
  void should_not_print_recipient_or_link() {
    assertThat(passwordReset(EXPIRES).toString())
        .contains(EVENT_ID.toString())
        .contains("PASSWORD_RESET")
        .doesNotContain(TO)
        .doesNotContain("token=");
  }

  /// 链接过期时刻为 `expiresAt` 的找回密码信。
  ///
  /// @param expiresAt 链接过期时刻
  /// @return 邮件
  private static SecurityMail passwordReset(Instant expiresAt) {
    return SecurityMail.builder()
        .eventId(EVENT_ID)
        .kind(SecurityMailKind.PASSWORD_RESET)
        .to(TO)
        .displayName("陈宇")
        .facts(Map.of("resetUrl", RESET_URL))
        .linkExpiresAt(expiresAt)
        .build();
  }
}
