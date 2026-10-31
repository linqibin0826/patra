package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 「账号即将删除」的提醒，到期前由定时任务发给当前邮箱。不是某次请求触发的，不带安全通知。
///
/// @param eventId 事件 ID：构造时生成，补投时不变
/// @param to 收件人
/// @param displayName 收件人的昵称
/// @param deletionDueAt 注销到期时刻
/// @param daysLeft 还剩几天（提醒提前量的天数）
@Builder
public record AccountDeletionReminderMail(
    UUID eventId, EmailAddress to, String displayName, Instant deletionDueAt, long daysLeft)
    implements OutgoingMail {

  /// 除基本类型外的字段都不能为 `null`。
  public AccountDeletionReminderMail {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(deletionDueAt, "deletionDueAt 不能为 null");
  }

  /// 不输出收件人与链接。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AccountDeletionReminderMail[eventId=" + eventId + ", displayName=" + displayName + "]";
  }

  /// 构造器：不指定事件 ID 时用构造时生成的新 ID。
  public static class AccountDeletionReminderMailBuilder {

    /// 事件 ID，默认在创建构造器时生成。
    private UUID eventId = UUID.randomUUID();
  }
}
