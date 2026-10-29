package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.util.Map;
import java.util.Objects;

/// 找回密码的邮件：带重设链接，发给申请的邮箱。它不是安全通知，没有 {@link SecurityNotice} 片段。
///
/// @param to 收件人
/// @param displayName 收件人的昵称
/// @param resetUrl 重设密码的链接，令牌在 URL 片段里
public record PasswordResetMail(EmailAddress to, String displayName, String resetUrl)
    implements OutgoingMail {

  /// 模板名。
  public static final String TEMPLATE = "password-reset";

  /// 主题。
  public static final String SUBJECT = "重设你的 Patra 密码";

  /// 字段都不能为 `null`。
  public PasswordResetMail {
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(resetUrl, "resetUrl 不能为 null");
  }

  /// 创建邮件。
  ///
  /// @param to 收件人
  /// @param displayName 收件人的昵称
  /// @param resetUrl 重设密码的链接，令牌在 URL 片段里
  /// @return 邮件
  public static PasswordResetMail of(EmailAddress to, String displayName, String resetUrl) {
    return new PasswordResetMail(to, displayName, resetUrl);
  }

  /// 模板名 `password-reset`。
  ///
  /// @return 模板名
  @Override
  public String template() {
    return TEMPLATE;
  }

  /// 主题「重设你的 Patra 密码」。
  ///
  /// @return 主题
  @Override
  public String subject() {
    return SUBJECT;
  }

  /// 模板变量：`displayName`、`resetUrl`、`validMinutes`（重设令牌的有效分钟数）。
  ///
  /// @return 变量
  @Override
  public Map<String, Object> model() {
    return Map.of(
        "displayName",
        displayName,
        "resetUrl",
        resetUrl,
        "validMinutes",
        EmailTokenPurpose.PASSWORD_RESET.validity().toMinutes());
  }

  /// 不输出收件人和链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "PasswordResetMail[displayName=" + displayName + "]";
  }
}
