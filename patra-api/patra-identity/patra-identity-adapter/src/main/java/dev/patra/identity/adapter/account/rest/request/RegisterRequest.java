package dev.patra.identity.adapter.account.rest.request;

/// 注册请求体。确认密码只在前端校验，不传给后端。
///
/// @param email 邮箱
/// @param username 用户名，注册必填
/// @param password 密码
/// @param clientType 客户端类型，不传按 `web`
/// @param deviceId 设备标识，可不传
/// @param verificationToken 人机验证令牌，可以没有
public record RegisterRequest(
    String email,
    String username,
    String password,
    String clientType,
    String deviceId,
    String verificationToken) {

  /// 不输出密码和人机验证令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegisterRequest[email="
        + email
        + ", username="
        + username
        + ", password=***, clientType="
        + clientType
        + ", deviceId="
        + deviceId
        + "]";
  }
}
