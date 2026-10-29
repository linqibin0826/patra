package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.util.Map;
import java.util.Objects;

/// 改邮箱的确认邮件：带确认链接，发给新邮箱。它不是安全通知，没有 {@link SecurityNotice} 片段。
///
/// @param to 收件人（新邮箱）
/// @param displayName 收件人的昵称
/// @param confirmUrl 确认链接，令牌在 URL 片段里
public record EmailChangeConfirmMail(EmailAddress to, String displayName, String confirmUrl)
    implements OutgoingMail {

  /// 模板名。
  public static final String TEMPLATE = "email-change-confirm";

  /// 主题。
  public static final String SUBJECT = "确认你的新 Patra 邮箱";

  /// 字段都不能为 `null`。
  public EmailChangeConfirmMail {
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(confirmUrl, "confirmUrl 不能为 null");
  }

  /// 创建邮件。
  ///
  /// @param to 收件人（新邮箱）
  /// @param displayName 收件人的昵称
  /// @param confirmUrl 确认链接，令牌在 URL 片段里
  /// @return 邮件
  public static EmailChangeConfirmMail of(EmailAddress to, String displayName, String confirmUrl) {
    return new EmailChangeConfirmMail(to, displayName, confirmUrl);
  }

  /// 模板名 `email-change-confirm`。
  ///
  /// @return 模板名
  @Override
  public String template() {
    return TEMPLATE;
  }

  /// 主题「确认你的新 Patra 邮箱」。
  ///
  /// @return 主题
  @Override
  public String subject() {
    return SUBJECT;
  }

  /// 模板变量：`displayName`、`confirmUrl`、`validMinutes`（确认令牌的有效分钟数）。
  ///
  /// @return 变量
  @Override
  public Map<String, Object> model() {
    return Map.of(
        "displayName",
        displayName,
        "confirmUrl",
        confirmUrl,
        "validMinutes",
        EmailTokenPurpose.EMAIL_CHANGE_CONFIRM.validity().toMinutes());
  }

  /// 不输出收件人和链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "EmailChangeConfirmMail[displayName=" + displayName + "]";
  }
}
