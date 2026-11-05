package dev.patra.identity.api.account.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 请发「你的 Patra 注册验证码」：第三方首次注册时，证明邮箱归属。这时候账号还不存在。
///
/// 带验证码原文：拿到就能完成注册，日志与发送记录里不能出现它。
///
/// @param eventId 事件 ID：identity 构造这封邮件时生成，补投时不变；发信时用作 `Resend-Idempotency-Key`
/// @param to 收件地址：用户填的邮箱
/// @param displayName 用户填的用户名
/// @param code 验证码原文
/// @param codeExpiresAt 验证码的过期时间：已过期的不必再发
@Builder
public record RegistrationCodeMailEvent(
    UUID eventId, String to, String displayName, String code, Instant codeExpiresAt)
    implements SecurityMailEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "security-mail.registration-code";

  /// 除基本类型外的字段都不能为 `null`。
  public RegistrationCodeMailEvent {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(code, "code 不能为 null");
    Objects.requireNonNull(codeExpiresAt, "codeExpiresAt 不能为 null");
  }

  /// 只输出事件 ID：收件地址与验证码不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegistrationCodeMailEvent[eventId=" + eventId + "]";
  }
}
