package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import java.time.Instant;
import lombok.Builder;

/// 第三方登录回调：新用户，要补填。
///
/// @param result 固定 `REGISTRATION_REQUIRED`
/// @param ticket 待补填凭证，门户写进 HttpOnly Cookie，不进 URL
/// @param provider 平台的小写名
/// @param accountLabel 账号标识
/// @param suggestedUsername 预填的用户名，可以为 `null`
/// @param suggestedEmail 预填的邮箱，可以为 `null`
/// @param expiresAt 待补填记录的过期时间
@Builder
public record ProviderRegistrationRequiredResponse(
    String result,
    String ticket,
    String provider,
    String accountLabel,
    String suggestedUsername,
    String suggestedEmail,
    Instant expiresAt) {

  /// 从待补填记录映射。
  ///
  /// @param ticket 待补填凭证
  /// @param pending 待补填记录
  /// @return 响应
  public static ProviderRegistrationRequiredResponse from(
      String ticket, PendingRegistration pending) {
    return ProviderRegistrationRequiredResponse.builder()
        .result("REGISTRATION_REQUIRED")
        .ticket(ticket)
        .provider(pending.provider().registrationId())
        .accountLabel(pending.accountLabel())
        .suggestedUsername(pending.suggestedUsername())
        .suggestedEmail(pending.suggestedEmail())
        .expiresAt(pending.expiresAt())
        .build();
  }

  /// 不输出凭证。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ProviderRegistrationRequiredResponse[ticket=***, provider=" + provider + "]";
  }
}
