package dev.patra.identity.api.account.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 请发「重设你的 Patra 密码」：找回密码申请。
///
/// 带一次性令牌链接：拿到链接就能操作账号，日志与发送记录里不能出现它。
///
/// @param eventId 事件 ID：identity 构造这封邮件时生成，补投时不变；发信时用作 `Resend-Idempotency-Key`
/// @param to 收件地址：申请找回的邮箱
/// @param displayName 收件人的昵称（没设时已回退成用户名）
/// @param resetUrl 重设密码的链接，一次性令牌在 URL 片段 `#token=` 里
/// @param linkExpiresAt 链接的过期时间：按发送时刻算剩余有效期，已过期的不必再发
@Builder
public record PasswordResetMailEvent(
    UUID eventId, String to, String displayName, String resetUrl, Instant linkExpiresAt)
    implements SecurityMailEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "security-mail.password-reset";

  /// 除基本类型外的字段都不能为 `null`。
  public PasswordResetMailEvent {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(resetUrl, "resetUrl 不能为 null");
    Objects.requireNonNull(linkExpiresAt, "linkExpiresAt 不能为 null");
  }

  /// 只输出事件 ID：收件地址与链接不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "PasswordResetMailEvent[eventId=" + eventId + "]";
  }
}
