package dev.patra.identity.api.account.event;

import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 请发「你的 Patra 账号绑定了新的登录方式」安全通知。
///
/// @param eventId 事件 ID：identity 构造这封邮件时生成，补投时不变；发信时用作 `Resend-Idempotency-Key`
/// @param to 收件地址：当前邮箱
/// @param displayName 收件人的昵称（没设时已回退成用户名）
/// @param providerName 平台名，如 `GitHub`
/// @param accountLabel 第三方账号标识
/// @param securitySettingsUrl 门户的安全设置页
/// @param context 触发这封通知的操作发生的时间、设备、IP 与属地
@Builder
public record LoginMethodLinkedMailEvent(
    UUID eventId,
    String to,
    String displayName,
    String providerName,
    String accountLabel,
    String securitySettingsUrl,
    OperationContext context)
    implements SecurityMailEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "security-mail.login-method-linked";

  /// 除基本类型外的字段都不能为 `null`。
  public LoginMethodLinkedMailEvent {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(providerName, "providerName 不能为 null");
    Objects.requireNonNull(accountLabel, "accountLabel 不能为 null");
    Objects.requireNonNull(securitySettingsUrl, "securitySettingsUrl 不能为 null");
    Objects.requireNonNull(context, "context 不能为 null");
  }

  /// 只输出事件 ID：收件地址不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LoginMethodLinkedMailEvent[eventId=" + eventId + "]";
  }
}
