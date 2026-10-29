package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.util.Map;
import java.util.Objects;

/// 「密码已修改」的安全通知，发给当前邮箱。
///
/// @param to 收件人
/// @param displayName 收件人的昵称
/// @param notice 安全通知上下文
public record PasswordChangedMail(EmailAddress to, String displayName, SecurityNotice notice)
    implements OutgoingMail {

  /// 模板名。
  public static final String TEMPLATE = "password-changed";

  /// 主题。
  public static final String SUBJECT = "你的 Patra 密码已修改";

  /// 不是本人操作时该怎么办。
  public static final String NOT_ME_ADVICE = "如果不是你，你的邮箱可能已被盗：请先找回邮箱，再用『忘记密码』重设密码";

  /// 字段都不能为 `null`。
  public PasswordChangedMail {
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(notice, "notice 不能为 null");
  }

  /// 创建邮件。
  ///
  /// @param to 收件人
  /// @param displayName 收件人的昵称
  /// @param notice 安全通知上下文
  /// @return 邮件
  public static PasswordChangedMail of(EmailAddress to, String displayName, SecurityNotice notice) {
    return new PasswordChangedMail(to, displayName, notice);
  }

  /// 模板名 `password-changed`。
  ///
  /// @return 模板名
  @Override
  public String template() {
    return TEMPLATE;
  }

  /// 主题「你的 Patra 密码已修改」。
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
    return "PasswordChangedMail[displayName=" + displayName + "]";
  }
}
