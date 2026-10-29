package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/// 验证邮箱的邮件：注册后、重发时发给用户当前的邮箱。它不是安全通知，没有 {@link SecurityNotice} 片段。
///
/// @param to 收件人
/// @param displayName 收件人的昵称，用在称呼里
/// @param verifyUrl 验证链接，令牌在 URL 片段里
/// @param expiresAt 链接的过期时间
public record EmailVerificationMail(
    EmailAddress to, String displayName, String verifyUrl, Instant expiresAt)
    implements OutgoingMail {

  /// 模板名。
  public static final String TEMPLATE = "email-verification";

  /// 主题。
  public static final String SUBJECT = "验证你的 Patra 邮箱";

  /// 四个字段都不能为 `null`。
  public EmailVerificationMail {
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(verifyUrl, "verifyUrl 不能为 null");
    Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
  }

  /// 创建邮件。
  ///
  /// @param to 收件人
  /// @param displayName 收件人的昵称
  /// @param verifyUrl 验证链接
  /// @param expiresAt 链接的过期时间
  /// @return 邮件
  public static EmailVerificationMail of(
      EmailAddress to, String displayName, String verifyUrl, Instant expiresAt) {
    return new EmailVerificationMail(to, displayName, verifyUrl, expiresAt);
  }

  /// 模板名 `email-verification`。
  ///
  /// @return 模板名
  @Override
  public String template() {
    return TEMPLATE;
  }

  /// 主题「验证你的 Patra 邮箱」。
  ///
  /// @return 主题
  @Override
  public String subject() {
    return SUBJECT;
  }

  /// 模板变量：`displayName`、`verifyUrl`、`validHours`（验证令牌的有效小时数）。
  ///
  /// @return 变量
  @Override
  public Map<String, Object> model() {
    return Map.of(
        "displayName",
        displayName,
        "verifyUrl",
        verifyUrl,
        "validHours",
        EmailTokenPurpose.EMAIL_VERIFY.validity().toHours());
  }

  /// 不输出收件人和链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "EmailVerificationMail[expiresAt=" + expiresAt + "]";
  }
}
