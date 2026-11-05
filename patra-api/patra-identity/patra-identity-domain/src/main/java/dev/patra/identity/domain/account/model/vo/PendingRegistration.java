package dev.patra.identity.domain.account.model.vo;

import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 待补填记录：平台确认过身份，但还没有账号。账号要等邮箱验证通过才创建（v0.19 决策 D），在那之前只有这条
/// Redis 记录，丢了重走一遍即可。
///
/// @param provider 平台
/// @param subject 规范化之后的主体
/// @param accountLabel 账号标识
/// @param suggestedUsername 预填的用户名，已过用户名规则，可以为 `null`
/// @param suggestedEmail 预填的邮箱，已规范化，可以为 `null`
/// @param expiresAt 记录的过期时间
@Builder
public record PendingRegistration(
    IdentityProvider provider,
    String subject,
    String accountLabel,
    String suggestedUsername,
    String suggestedEmail,
    Instant expiresAt) {

  /// 除两个预填值外都不能为 `null`。
  public PendingRegistration {
    Objects.requireNonNull(provider, "provider 不能为 null");
    Objects.requireNonNull(subject, "subject 不能为 null");
    Objects.requireNonNull(accountLabel, "accountLabel 不能为 null");
    Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
  }
}
