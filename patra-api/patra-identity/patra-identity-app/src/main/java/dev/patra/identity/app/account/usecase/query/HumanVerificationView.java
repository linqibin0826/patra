package dev.patra.identity.app.account.usecase.query;

/// 门户渲染人机验证组件要用的信息。没有任何密钥。
///
/// @param provider 当前厂商的线上值
/// @param siteKey 当前厂商的站点标识，切到缺配置的厂商时为 `null`
public record HumanVerificationView(String provider, String siteKey) {

  /// 创建视图。
  ///
  /// @param provider 厂商的线上值
  /// @param siteKey 站点标识
  /// @return 视图
  public static HumanVerificationView of(String provider, String siteKey) {
    return new HumanVerificationView(provider, siteKey);
  }
}
