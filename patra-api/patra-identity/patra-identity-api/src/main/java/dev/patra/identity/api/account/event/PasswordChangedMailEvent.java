package dev.patra.identity.api.account.event;

import java.util.Objects;
import java.util.UUID;

/// 请发「你的 Patra 密码已修改」安全通知。
///
/// @param eventId 事件 ID：identity 构造这封邮件时生成，补投时不变；发信时用作 `Resend-Idempotency-Key`
/// @param to 收件地址：当前邮箱
/// @param displayName 收件人的昵称（没设时已回退成用户名）
/// @param context 触发这封通知的操作发生的时间、设备、IP 与属地
public record PasswordChangedMailEvent(
    UUID eventId, String to, String displayName, OperationContext context)
    implements SecurityMailEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "security-mail.password-changed";

  /// 除基本类型外的字段都不能为 `null`。
  public PasswordChangedMailEvent {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(context, "context 不能为 null");
  }

  /// 创建事件。
  ///
  /// @param eventId 同名字段
  /// @param to 同名字段
  /// @param displayName 同名字段
  /// @param context 同名字段
  /// @return 事件
  public static PasswordChangedMailEvent of(
      UUID eventId, String to, String displayName, OperationContext context) {
    return new PasswordChangedMailEvent(eventId, to, displayName, context);
  }

  /// 只输出事件 ID：收件地址与链接不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "PasswordChangedMailEvent[eventId=" + eventId + "]";
  }
}
