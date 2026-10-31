package dev.patra.identity.api.account.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 请发「你的 Patra 账号将在 N 天后删除」提醒：到期前由定时任务发出，不带安全通知片段。
///
/// @param eventId 事件 ID：identity 构造这封邮件时生成，补投时不变；发信时用作 `Resend-Idempotency-Key`
/// @param to 收件地址：当前邮箱
/// @param displayName 收件人的昵称（没设时已回退成用户名）
/// @param deletionDueAt 注销到期时刻
/// @param daysLeft 还剩几天（提醒提前量的天数），进主题
@Builder
public record AccountDeletionReminderMailEvent(
    UUID eventId, String to, String displayName, Instant deletionDueAt, long daysLeft)
    implements SecurityMailEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "security-mail.account-deletion-reminder";

  /// 除基本类型外的字段都不能为 `null`。
  public AccountDeletionReminderMailEvent {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(deletionDueAt, "deletionDueAt 不能为 null");
  }

  /// 只输出事件 ID：收件地址与链接不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AccountDeletionReminderMailEvent[eventId=" + eventId + "]";
  }
}
