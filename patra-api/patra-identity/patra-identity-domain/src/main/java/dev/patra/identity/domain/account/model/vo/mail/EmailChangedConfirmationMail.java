package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.util.Map;
import java.util.Objects;

/// 「本邮箱已绑定」的安全通知，改邮箱生效后发给新邮箱。
///
/// @param to 收件人（新邮箱）
/// @param displayName 收件人的昵称
/// @param notice 安全通知上下文
public record EmailChangedConfirmationMail(
    EmailAddress to, String displayName, SecurityNotice notice) implements OutgoingMail {

  /// 模板名。
  public static final String TEMPLATE = "email-changed-confirmation";

  /// 主题。
  public static final String SUBJECT = "本邮箱已绑定你的 Patra 账号";

  /// 不是本人操作时该怎么办。
  public static final String NOT_ME_ADVICE = "如果不是你，请联系旧邮箱的主人或忽略";

  /// 字段都不能为 `null`。
  public EmailChangedConfirmationMail {
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(notice, "notice 不能为 null");
  }

  /// 创建邮件。
  ///
  /// @param to 收件人（新邮箱）
  /// @param displayName 收件人的昵称
  /// @param notice 安全通知上下文
  /// @return 邮件
  public static EmailChangedConfirmationMail of(
      EmailAddress to, String displayName, SecurityNotice notice) {
    return new EmailChangedConfirmationMail(to, displayName, notice);
  }

  /// 模板名 `email-changed-confirmation`。
  ///
  /// @return 模板名
  @Override
  public String template() {
    return TEMPLATE;
  }

  /// 主题「本邮箱已绑定你的 Patra 账号」。
  ///
  /// @return 主题
  @Override
  public String subject() {
    return SUBJECT;
  }

  /// 模板变量：`displayName`、`notice`。
  ///
  /// @return 变量
  @Override
  public Map<String, Object> model() {
    return Map.of("displayName", displayName, "notice", notice);
  }

  /// 不输出收件人和链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "EmailChangedConfirmationMail[displayName=" + displayName + "]";
  }
}
