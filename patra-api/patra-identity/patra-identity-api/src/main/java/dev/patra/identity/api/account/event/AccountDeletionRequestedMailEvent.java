package dev.patra.identity.api.account.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 请发「你的 Patra 账号将被注销」安全通知：写明到期时刻和怎么取消（不放链接）。
///
/// @param eventId 事件 ID：identity 构造这封邮件时生成，补投时不变；发信时用作 `Resend-Idempotency-Key`
/// @param to 收件地址：当前邮箱
/// @param displayName 收件人的昵称（没设时已回退成用户名）
/// @param deletionDueAt 注销到期时刻
/// @param context 触发这封通知的操作发生的时间、设备、IP 与属地
@Builder
public record AccountDeletionRequestedMailEvent(
    UUID eventId, String to, String displayName, Instant deletionDueAt, OperationContext context)
    implements SecurityMailEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "security-mail.account-deletion-requested";

  /// 除基本类型外的字段都不能为 `null`。
  public AccountDeletionRequestedMailEvent {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(deletionDueAt, "deletionDueAt 不能为 null");
    Objects.requireNonNull(context, "context 不能为 null");
  }

  /// 只输出事件 ID：收件地址与链接不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AccountDeletionRequestedMailEvent[eventId=" + eventId + "]";
  }
}
