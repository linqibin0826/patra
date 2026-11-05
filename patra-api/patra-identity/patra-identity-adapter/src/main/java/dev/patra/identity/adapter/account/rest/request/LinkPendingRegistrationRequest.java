package dev.patra.identity.adapter.account.rest.request;

/// 邮箱撞车时就地绑定：提交验证码的请求体。
///
/// @param code 验证码
/// @param clientType 客户端类型，可以没有
/// @param deviceId 设备标识，可以没有
public record LinkPendingRegistrationRequest(String code, String clientType, String deviceId) {

  /// 不输出验证码和设备标识。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LinkPendingRegistrationRequest[code=***, clientType=" + clientType + "]";
  }
}
