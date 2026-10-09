package dev.linqibin.patra.identity.adapter.rest.auth.request;

/// 登录请求体。
///
/// @param email 邮箱
/// @param password 密码
/// @param clientType 客户端类型，不传按 `web`
/// @param deviceId 设备标识，可不传
public record LoginRequest(String email, String password, String clientType, String deviceId) {

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LoginRequest[email="
        + email
        + ", password=***, clientType="
        + clientType
        + ", deviceId="
        + deviceId
        + "]";
  }
}
