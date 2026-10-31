package dev.patra.identity.infra.account.mail;

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
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import java.util.List;

/// 13 种安全邮件各一封的样本：映射、外送配置、序列化往返的测试都拿它逐个过一遍。
///
/// 新设备登录那封的安全通知没有设备、IP 与属地（受信转发链路没接通时的真实情况），其余齐全。
public final class SecurityMailFixture {

  /// 收件人。
  public static final EmailAddress TO = EmailAddress.of("chen.yu@example.com");

  /// 改邮箱时的另一个地址：确认信、「已绑定」发到这里，变更通知里遮掩显示它。
  public static final EmailAddress NEW_EMAIL = EmailAddress.of("chen.new@example.com");

  /// 带令牌链接的过期时间。
  public static final Instant LINK_EXPIRES = Instant.parse("2026-10-22T09:00:00Z");

  /// 注销到期时刻。
  public static final Instant DUE = Instant.parse("2026-11-04T00:00:00Z");

  /// 设备、IP、属地齐全的安全通知。
  public static final SecurityNotice NOTICE =
      SecurityNotice.of(
          Instant.parse("2026-10-22T08:30:00Z"), "Chrome 141 · macOS", "113.92.157.29", "中国 广东 深圳");

  /// 设备、IP、属地都缺的安全通知。
  public static final SecurityNotice NOTICE_WITHOUT_ORIGIN =
      SecurityNotice.of(Instant.parse("2026-10-22T08:30:00Z"), null, null, null);

  private SecurityMailFixture() {}

  /// 13 种各一封，每次调用都是新的事件 ID。
  ///
  /// @return 样本
  public static List<OutgoingMail> all() {
    return List.of(
        EmailVerificationMail.builder()
            .to(TO)
            .displayName("陈宇")
            .verifyUrl("https://patra.example/verify-email#token=" + "v".repeat(43))
            .linkExpiresAt(LINK_EXPIRES)
            .build(),
        PasswordResetMail.builder()
            .to(TO)
            .displayName("陈宇")
            .resetUrl("https://patra.example/reset-password#token=" + "r".repeat(43))
            .linkExpiresAt(LINK_EXPIRES)
            .build(),
        PasswordResetDoneMail.of(TO, "陈宇", NOTICE),
        PasswordChangedMail.of(TO, "陈宇", NOTICE),
        EmailChangeConfirmMail.builder()
            .to(NEW_EMAIL)
            .displayName("陈宇")
            .confirmUrl("https://patra.example/confirm-email-change#token=" + "c".repeat(43))
            .linkExpiresAt(LINK_EXPIRES)
            .build(),
        EmailChangedNoticeMail.builder()
            .to(TO)
            .displayName("陈宇")
            .newEmailMasked(NEW_EMAIL.masked())
            .revertUrl("https://patra.example/revert-email-change#token=" + "u".repeat(43))
            .linkExpiresAt(LINK_EXPIRES)
            .notice(NOTICE)
            .build(),
        EmailChangedConfirmationMail.of(NEW_EMAIL, "陈宇", NOTICE),
        EmailChangeRevertedMail.builder()
            .to(TO)
            .displayName("陈宇")
            .resetUrl("https://patra.example/reset-password#token=" + "s".repeat(43))
            .linkExpiresAt(LINK_EXPIRES)
            .notice(NOTICE)
            .build(),
        NewDeviceLoginMail.of(TO, "陈宇", NOTICE_WITHOUT_ORIGIN),
        AccountDeletionRequestedMail.builder()
            .to(TO)
            .displayName("陈宇")
            .deletionDueAt(DUE)
            .notice(NOTICE)
            .build(),
        AccountDeletionCancelledMail.of(TO, "陈宇", NOTICE),
        AccountDeletionReminderMail.builder()
            .to(TO)
            .displayName("陈宇")
            .deletionDueAt(DUE)
            .daysLeft(3)
            .build(),
        AccountDeletionCompletedMail.of(TO, "陈宇", "chen-yu"));
  }
}
