package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.query.PendingRegistrationView;
import dev.patra.identity.domain.account.model.vo.PendingCode;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import java.time.Instant;
import lombok.Builder;

/// 补填页要显示的内容。
///
/// @param provider 平台的小写名
/// @param accountLabel 账号标识
/// @param suggestedUsername 预填的用户名，可以为 `null`
/// @param suggestedEmail 预填的邮箱，可以为 `null`
/// @param expiresAt 待补填记录的过期时间
/// @param pendingCode 发出去还没用掉的验证码；有值时刷新页面可以直接回到输验证码这一步
@Builder
public record PendingRegistrationResponse(
    String provider,
    String accountLabel,
    String suggestedUsername,
    String suggestedEmail,
    Instant expiresAt,
    PendingCodeResponse pendingCode) {

  /// 发出去还没用掉的验证码。
  ///
  /// @param username 发码时绑定的用户名：重发要同时提交用户名和邮箱，刷新页面后门户只能从这里拿
  /// @param email 发往的邮箱
  /// @param codeExpiresAt 验证码的过期时间
  /// @param nextResendAt 最早可以重发的时间
  public record PendingCodeResponse(
      String username, String email, Instant codeExpiresAt, Instant nextResendAt) {

    /// 从领域值映射。
    ///
    /// @param code 验证码的公开部分
    /// @return 响应；没有时为 `null`
    static PendingCodeResponse from(PendingCode code) {
      return code == null
          ? null
          : new PendingCodeResponse(
              code.username(), code.email(), code.codeExpiresAt(), code.nextResendAt());
    }
  }

  /// 从视图映射。
  ///
  /// @param view 视图
  /// @return 响应
  public static PendingRegistrationResponse from(PendingRegistrationView view) {
    PendingRegistration registration = view.registration();
    return PendingRegistrationResponse.builder()
        .provider(registration.provider().registrationId())
        .accountLabel(registration.accountLabel())
        .suggestedUsername(registration.suggestedUsername())
        .suggestedEmail(registration.suggestedEmail())
        .expiresAt(registration.expiresAt())
        .pendingCode(PendingCodeResponse.from(view.pendingCode()))
        .build();
  }
}
