package dev.patra.identity.api.account.event;

import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 请发「本邮箱已绑定你的 Patra 账号」安全通知：改邮箱生效后发到新邮箱。
///
/// @param eventId 事件 ID：identity 构造这封邮件时生成，补投时不变；发信时用作 `Resend-Idempotency-Key`
/// @param to 收件地址：**新邮箱**
/// @param displayName 收件人的昵称（没设时已回退成用户名）
/// @param securitySettingsUrl 门户安全设置页的地址
/// @param context 触发这封通知的操作发生的时间、设备、IP 与属地
@Builder
public record EmailChangedConfirmationMailEvent(
    UUID eventId,
    String to,
    String displayName,
    String securitySettingsUrl,
    OperationContext context)
    implements SecurityMailEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "security-mail.email-changed-confirmation";

  /// 除基本类型外的字段都不能为 `null`。
  public EmailChangedConfirmationMailEvent {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(securitySettingsUrl, "securitySettingsUrl 不能为 null");
    Objects.requireNonNull(context, "context 不能为 null");
  }

  /// 只输出事件 ID：收件地址与链接不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "EmailChangedConfirmationMailEvent[eventId=" + eventId + "]";
  }
}
