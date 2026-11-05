package dev.patra.identity.api.account.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 请发「绑定登录方式的验证码」：第三方首次登录时邮箱撞车，要把第三方账号绑到这个邮箱对应的账号。
///
/// 带验证码原文：拿到就能完成绑定，日志与发送记录里不能出现它。
///
/// @param eventId 事件 ID：identity 构造这封邮件时生成，补投时不变；发信时用作 `Resend-Idempotency-Key`
/// @param to 收件地址：已有账号的邮箱
/// @param displayName 收件人的昵称
/// @param username 要绑到的账号的用户名
/// @param code 验证码原文
/// @param codeExpiresAt 验证码的过期时间：已过期的不必再发
/// @param providerName 平台名，如 `GitHub`
/// @param accountLabel 第三方账号标识
@Builder
public record LoginMethodLinkCodeMailEvent(
    UUID eventId,
    String to,
    String displayName,
    String username,
    String code,
    Instant codeExpiresAt,
    String providerName,
    String accountLabel)
    implements SecurityMailEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "security-mail.login-method-link-code";

  /// 除基本类型外的字段都不能为 `null`。
  public LoginMethodLinkCodeMailEvent {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(username, "username 不能为 null");
    Objects.requireNonNull(code, "code 不能为 null");
    Objects.requireNonNull(codeExpiresAt, "codeExpiresAt 不能为 null");
    Objects.requireNonNull(providerName, "providerName 不能为 null");
    Objects.requireNonNull(accountLabel, "accountLabel 不能为 null");
  }

  /// 只输出事件 ID：收件地址与验证码不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LoginMethodLinkCodeMailEvent[eventId=" + eventId + "]";
  }
}
