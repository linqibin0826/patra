package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import java.util.Map;
import java.util.Objects;

/// 「账号已注销」的通知，到期执行后发给注销前的邮箱。不是某次请求触发的，不带安全通知片段。
///
/// @param to 收件人：注销前的邮箱
/// @param displayName 注销前的昵称
/// @param username 用户名，永久占用、不再释放
public record AccountDeletionCompletedMail(EmailAddress to, String displayName, String username)
    implements OutgoingMail {

  /// 模板名。
  public static final String TEMPLATE = "account-deletion-completed";

  /// 主题。
  public static final String SUBJECT = "你的 Patra 账号已注销";

  /// 字段都不能为 `null`。
  public AccountDeletionCompletedMail {
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(username, "username 不能为 null");
  }

  /// 创建邮件。
  ///
  /// @param to 收件人
  /// @param displayName 注销前的昵称
  /// @param username 用户名
  /// @return 邮件
  public static AccountDeletionCompletedMail of(
      EmailAddress to, String displayName, String username) {
    return new AccountDeletionCompletedMail(to, displayName, username);
  }

  /// 模板名 `account-deletion-completed`。
  ///
  /// @return 模板名
  @Override
  public String template() {
    return TEMPLATE;
  }

  /// 主题「你的 Patra 账号已注销」。
  ///
  /// @return 主题
  @Override
  public String subject() {
    return SUBJECT;
  }

  /// 模板变量：`displayName`、`username`。
  ///
  /// @return 变量
  @Override
  public Map<String, Object> model() {
    return Map.of("displayName", displayName, "username", username);
  }

  /// 不输出收件人。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AccountDeletionCompletedMail[displayName=" + displayName + "]";
  }
}
