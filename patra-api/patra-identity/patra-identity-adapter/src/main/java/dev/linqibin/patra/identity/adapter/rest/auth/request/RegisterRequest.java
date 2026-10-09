package dev.linqibin.patra.identity.adapter.rest.auth.request;

/// 注册请求体。确认密码只在前端校验，不传给后端。
///
/// @param email 邮箱
/// @param password 密码
/// @param clientType 客户端类型，不传按 `web`
/// @param deviceId 设备标识，可不传
public record RegisterRequest(String email, String password, String clientType, String deviceId) {

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegisterRequest[email="
        + email
        + ", password=***, clientType="
        + clientType
        + ", deviceId="
        + deviceId
        + "]";
  }
}
