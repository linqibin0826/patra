package dev.patra.identity.api.account.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 请发「确认你的新 Patra 邮箱」：改邮箱申请，发到新邮箱。
///
/// 带一次性令牌链接：拿到链接就能操作账号，日志与发送记录里不能出现它。
///
/// @param eventId 事件 ID：identity 构造这封邮件时生成，补投时不变；发信时用作 `Resend-Idempotency-Key`
/// @param to 收件地址：**新邮箱**
/// @param displayName 收件人的昵称（没设时已回退成用户名）
/// @param confirmUrl 确认链接，一次性令牌在 URL 片段 `#token=` 里
/// @param linkExpiresAt 链接的过期时间：按发送时刻算剩余有效期，已过期的不必再发
@Builder
public record EmailChangeConfirmMailEvent(
    UUID eventId, String to, String displayName, String confirmUrl, Instant linkExpiresAt)
    implements SecurityMailEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "security-mail.email-change-confirm";

  /// 除基本类型外的字段都不能为 `null`。
  public EmailChangeConfirmMailEvent {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(confirmUrl, "confirmUrl 不能为 null");
    Objects.requireNonNull(linkExpiresAt, "linkExpiresAt 不能为 null");
  }

  /// 只输出事件 ID：收件地址与链接不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "EmailChangeConfirmMailEvent[eventId=" + eventId + "]";
  }
}
