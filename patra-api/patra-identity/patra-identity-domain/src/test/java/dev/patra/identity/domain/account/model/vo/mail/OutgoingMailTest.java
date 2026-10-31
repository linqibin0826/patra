package dev.patra.identity.domain.account.model.vo.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// 13 封安全邮件共有的规则：构造时生成事件 ID（补投去重靠它，不能在别处生成），指定时原样保留，
/// `toString` 带事件 ID、不带收件人与链接。
@DisplayName("安全邮件的事件 ID")
class OutgoingMailTest {

  private static final EmailAddress TO = EmailAddress.of("chen.yu@example.com");
  private static final String LINK = "https://p/x#token=" + "t".repeat(43);
  private static final Instant AT = Instant.parse("2026-10-22T08:00:00Z");
  private static final SecurityNotice NOTICE = SecurityNotice.of(AT, null, null, null);
  private static final UUID GIVEN = UUID.fromString("00000000-0000-0000-0000-000000000001");

  /// 每种邮件一个不指定事件 ID 的构造方式。
  ///
  /// @return 邮件种类名与构造方式
  static Stream<Arguments> mails() {
    return Stream.of(
        mail(
            "EmailVerificationMail",
            () ->
                EmailVerificationMail.builder()
                    .to(TO)
                    .displayName("陈宇")
                    .verifyUrl(LINK)
                    .linkExpiresAt(AT)
                    .build()),
        mail(
            "PasswordResetMail",
            () ->
                PasswordResetMail.builder()
                    .to(TO)
                    .displayName("陈宇")
                    .resetUrl(LINK)
                    .linkExpiresAt(AT)
                    .build()),
        mail("PasswordResetDoneMail", () -> PasswordResetDoneMail.of(TO, "陈宇", NOTICE)),
        mail("PasswordChangedMail", () -> PasswordChangedMail.of(TO, "陈宇", NOTICE)),
        mail(
            "EmailChangeConfirmMail",
            () ->
                EmailChangeConfirmMail.builder()
                    .to(TO)
                    .displayName("陈宇")
                    .confirmUrl(LINK)
                    .linkExpiresAt(AT)
                    .build()),
        mail(
            "EmailChangedNoticeMail",
            () ->
                EmailChangedNoticeMail.builder()
                    .to(TO)
                    .displayName("陈宇")
                    .newEmailMasked("n***@example.com")
                    .revertUrl(LINK)
                    .linkExpiresAt(AT)
                    .notice(NOTICE)
                    .build()),
        mail(
            "EmailChangedConfirmationMail",
            () -> EmailChangedConfirmationMail.of(TO, "陈宇", NOTICE)),
        mail(
            "EmailChangeRevertedMail",
            () ->
                EmailChangeRevertedMail.builder()
                    .to(TO)
                    .displayName("陈宇")
                    .resetUrl(LINK)
                    .linkExpiresAt(AT)
                    .notice(NOTICE)
                    .build()),
        mail("NewDeviceLoginMail", () -> NewDeviceLoginMail.of(TO, "陈宇", NOTICE)),
        mail(
            "AccountDeletionRequestedMail",
            () ->
                AccountDeletionRequestedMail.builder()
                    .to(TO)
                    .displayName("陈宇")
                    .deletionDueAt(AT)
                    .notice(NOTICE)
                    .build()),
        mail(
            "AccountDeletionCancelledMail",
            () -> AccountDeletionCancelledMail.of(TO, "陈宇", NOTICE)),
        mail(
            "AccountDeletionReminderMail",
            () ->
                AccountDeletionReminderMail.builder()
                    .to(TO)
                    .displayName("陈宇")
                    .deletionDueAt(AT)
                    .daysLeft(3)
                    .build()),
        mail(
            "AccountDeletionCompletedMail",
            () -> AccountDeletionCompletedMail.of(TO, "陈宇", "chen-yu")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mails")
  @DisplayName("不指定时构造即生成事件 ID，两次构造各不相同")
  void should_generate_event_id_when_built_without_one(String kind, Supplier<OutgoingMail> build) {
    OutgoingMail first = build.get();
    OutgoingMail second = build.get();

    assertThat(first.eventId()).isNotNull().isNotEqualTo(second.eventId());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mails")
  @DisplayName("toString 带事件 ID，不带收件人与链接")
  void should_show_event_id_but_not_recipient_or_link_in_to_string(
      String kind, Supplier<OutgoingMail> build) {
    OutgoingMail mail = build.get();

    assertThat(mail.toString())
        .contains(mail.eventId().toString())
        .doesNotContain("chen.yu")
        .doesNotContain("token=");
  }

  @Test
  @DisplayName("用 builder 的邮件指定事件 ID 时原样保留")
  void should_keep_given_event_id() {
    EmailVerificationMail mail =
        EmailVerificationMail.builder()
            .eventId(GIVEN)
            .to(TO)
            .displayName("陈宇")
            .verifyUrl(LINK)
            .linkExpiresAt(AT)
            .build();

    assertThat(mail.eventId()).isEqualTo(GIVEN);
  }

  @Test
  @DisplayName("事件 ID 不能为 null：不在构造器里补生成，免得反序列化缺字段时每次得到新 ID")
  void should_reject_null_event_id() {
    assertThatThrownBy(() -> new PasswordChangedMail(null, TO, "陈宇", NOTICE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                EmailVerificationMail.builder()
                    .eventId(null)
                    .to(TO)
                    .displayName("陈宇")
                    .verifyUrl(LINK)
                    .linkExpiresAt(AT)
                    .build())
        .isInstanceOf(NullPointerException.class);
  }

  /// 参数化用例的一行。
  ///
  /// @param kind 邮件种类名
  /// @param build 构造方式
  /// @return 参数
  private static Arguments mail(String kind, Supplier<OutgoingMail> build) {
    return Arguments.of(kind, build);
  }
}
