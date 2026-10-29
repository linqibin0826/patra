package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/// 「账号即将删除」的提醒，到期前由定时任务发给当前邮箱。不是某次请求触发的，不带安全通知片段。
///
/// @param to 收件人
/// @param displayName 收件人的昵称
/// @param deletionDueAt 注销到期时刻，渲染器按时区格式化
/// @param daysLeft 还剩几天（提醒提前量的天数），进主题
public record AccountDeletionReminderMail(
    EmailAddress to, String displayName, Instant deletionDueAt, long daysLeft)
    implements OutgoingMail {

  /// 模板名。
  public static final String TEMPLATE = "account-deletion-reminder";

  /// 收件人、昵称、到期时刻不能为 `null`。
  public AccountDeletionReminderMail {
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(deletionDueAt, "deletionDueAt 不能为 null");
  }

  /// 创建邮件。
  ///
  /// @param to 收件人
  /// @param displayName 收件人的昵称
  /// @param deletionDueAt 注销到期时刻
  /// @param daysLeft 还剩几天
  /// @return 邮件
  public static AccountDeletionReminderMail of(
      EmailAddress to, String displayName, Instant deletionDueAt, long daysLeft) {
    return new AccountDeletionReminderMail(to, displayName, deletionDueAt, daysLeft);
  }

  /// 模板名 `account-deletion-reminder`。
  ///
  /// @return 模板名
  @Override
  public String template() {
    return TEMPLATE;
  }

  /// 主题「你的 Patra 账号将在 N 天后删除」：天数随提醒提前量，不写死。
  ///
  /// @return 主题
  @Override
  public String subject() {
    return "你的 Patra 账号将在 " + daysLeft + " 天后删除";
  }

  /// 模板变量：`displayName`、`deletionDueAt`、`daysLeft`。
  ///
  /// @return 变量
  @Override
  public Map<String, Object> model() {
    return Map.of("displayName", displayName, "deletionDueAt", deletionDueAt, "daysLeft", daysLeft);
  }

  /// 不输出收件人。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AccountDeletionReminderMail[displayName=" + displayName + "]";
  }
}
