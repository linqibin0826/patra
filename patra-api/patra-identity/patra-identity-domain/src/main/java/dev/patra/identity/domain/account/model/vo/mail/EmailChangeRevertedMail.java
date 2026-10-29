package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.util.Map;
import java.util.Objects;

/// 「邮箱已改回，请重设密码」的安全通知，撤销改邮箱后发给改回的旧邮箱，内含重设密码的链接。
///
/// @param to 收件人（改回的旧邮箱）
/// @param displayName 收件人的昵称
/// @param resetUrl 重设密码的链接，令牌在 URL 片段里
/// @param notice 安全通知上下文
public record EmailChangeRevertedMail(
    EmailAddress to, String displayName, String resetUrl, SecurityNotice notice)
    implements OutgoingMail {

  /// 模板名。
  public static final String TEMPLATE = "email-change-reverted";

  /// 主题。
  public static final String SUBJECT = "你的 Patra 邮箱已改回，请重设密码";

  /// 不是本人操作时该怎么办：撤销只能由能读到旧邮箱邮件的人点，不是本人就说明邮箱被人看到了。
  public static final String NOT_ME_ADVICE = "说明有人能看到这个邮箱里的邮件：先保护好邮箱（改邮箱密码、检查收件转发），再用上面的链接设置新密码";

  /// 字段都不能为 `null`。
  public EmailChangeRevertedMail {
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(resetUrl, "resetUrl 不能为 null");
    Objects.requireNonNull(notice, "notice 不能为 null");
  }

  /// 创建邮件。
  ///
  /// @param to 收件人（改回的旧邮箱）
  /// @param displayName 收件人的昵称
  /// @param resetUrl 重设密码的链接，令牌在 URL 片段里
  /// @param notice 安全通知上下文
  /// @return 邮件
  public static EmailChangeRevertedMail of(
      EmailAddress to, String displayName, String resetUrl, SecurityNotice notice) {
    return new EmailChangeRevertedMail(to, displayName, resetUrl, notice);
  }

  /// 模板名 `email-change-reverted`。
  ///
  /// @return 模板名
  @Override
  public String template() {
    return TEMPLATE;
  }

  /// 主题「你的 Patra 邮箱已改回，请重设密码」。
  ///
  /// @return 主题
  @Override
  public String subject() {
    return SUBJECT;
  }

  /// 模板变量：`displayName`、`resetUrl`、`validMinutes`、`notice`。
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
        EmailTokenPurpose.PASSWORD_RESET.validity().toMinutes(),
        "notice",
        notice);
  }

  /// 不输出收件人和链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "EmailChangeRevertedMail[displayName=" + displayName + "]";
  }
}
