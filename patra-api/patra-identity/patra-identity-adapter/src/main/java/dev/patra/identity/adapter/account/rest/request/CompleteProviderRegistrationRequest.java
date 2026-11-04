package dev.patra.identity.adapter.account.rest.request;

/// 第三方首次注册：提交验证码的请求体。
///
/// @param code 验证码
/// @param clientType 客户端类型，可以没有
/// @param deviceId 设备标识，可以没有
public record CompleteProviderRegistrationRequest(String code, String clientType, String deviceId) {

  /// 不输出验证码和设备标识。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "CompleteProviderRegistrationRequest[code=***, clientType=" + clientType + "]";
  }
}
