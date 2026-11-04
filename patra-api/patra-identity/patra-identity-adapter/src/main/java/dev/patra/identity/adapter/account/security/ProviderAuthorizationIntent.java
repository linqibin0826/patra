package dev.patra.identity.adapter.account.security;

import java.util.Map;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/// 一次授权的用途，存在授权请求的 `attributes` 里，随它进 Redis。
///
/// 没有这些属性的是登录：`/oauth2/authorization/{provider}` 走框架的过滤器，默认就是它。
sealed interface ProviderAuthorizationIntent {

  /// 用途属性名。
  String PURPOSE_ATTRIBUTE = "patra_purpose";

  /// 发起绑定的用户 ID 属性名。
  String USER_ID_ATTRIBUTE = "patra_user_id";

  /// 发起绑定的会话 ID 属性名。
  String SESSION_ID_ATTRIBUTE = "patra_session_id";

  /// 用途：绑定。
  String PURPOSE_LINK = "LINK";

  /// 登录。
  record Login() implements ProviderAuthorizationIntent {}

  /// 绑定：回调时核对当前用户和会话与发起时一致。
  ///
  /// @param userId 发起的用户 ID
  /// @param sessionId 发起的会话 ID
  record Link(long userId, long sessionId) implements ProviderAuthorizationIntent {

    /// 写进授权请求的属性。雪花 ID 存成字符串：JSON 往返不丢精度。
    ///
    /// @return 属性
    Map<String, Object> attributes() {
      return Map.of(
          PURPOSE_ATTRIBUTE,
          PURPOSE_LINK,
          USER_ID_ATTRIBUTE,
          Long.toString(userId),
          SESSION_ID_ATTRIBUTE,
          Long.toString(sessionId));
    }
  }

  /// 从授权请求读用途。
  ///
  /// @param request 授权请求
  /// @return 用途
  static ProviderAuthorizationIntent of(OAuth2AuthorizationRequest request) {
    if (!PURPOSE_LINK.equals(request.getAttribute(PURPOSE_ATTRIBUTE))) {
      return new Login();
    }
    return new Link(
        Long.parseLong(request.getAttribute(USER_ID_ATTRIBUTE)),
        Long.parseLong(request.getAttribute(SESSION_ID_ATTRIBUTE)));
  }
}
