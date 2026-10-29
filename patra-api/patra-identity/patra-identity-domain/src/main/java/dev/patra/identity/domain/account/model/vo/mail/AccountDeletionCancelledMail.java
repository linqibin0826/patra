package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.util.Map;
import java.util.Objects;

/// 「已取消注销」的安全通知，发给当前邮箱。
///
/// @param to 收件人
/// @param displayName 收件人的昵称
/// @param notice 安全通知上下文
public record AccountDeletionCancelledMail(
    EmailAddress to, String displayName, SecurityNotice notice) implements OutgoingMail {

  /// 模板名。
  public static final String TEMPLATE = "account-deletion-cancelled";

  /// 主题。
  public static final String SUBJECT = "你的 Patra 账号已取消注销";

  /// 不是本人操作时该怎么办。
  public static final String NOT_ME_ADVICE = "如果不是你，说明有人知道了你的密码：请立即修改密码，并在登录设备里退出不认识的设备；仍想注销可以重新申请";

  /// 字段都不能为 `null`。
  public AccountDeletionCancelledMail {
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
  public static AccountDeletionCancelledMail of(
      EmailAddress to, String displayName, SecurityNotice notice) {
    return new AccountDeletionCancelledMail(to, displayName, notice);
  }

  /// 模板名 `account-deletion-cancelled`。
  ///
  /// @return 模板名
  @Override
  public String template() {
    return TEMPLATE;
  }

  /// 主题「你的 Patra 账号已取消注销」。
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

  /// 不输出收件人。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AccountDeletionCancelledMail[displayName=" + displayName + "]";
  }
}
