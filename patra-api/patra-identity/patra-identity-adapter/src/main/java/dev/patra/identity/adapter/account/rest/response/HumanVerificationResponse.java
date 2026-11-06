package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.query.HumanVerificationView;

/// 当前人机验证厂商。门户据此渲染组件，没有任何密钥。
///
/// @param provider 厂商的线上值：`turnstile` 或 `trustturn`
/// @param siteKey 站点标识
public record HumanVerificationResponse(String provider, String siteKey) {

  /// 从查询结果映射。
  ///
  /// @param view 查询结果
  /// @return 响应
  public static HumanVerificationResponse from(HumanVerificationView view) {
    return new HumanVerificationResponse(view.provider(), view.siteKey());
  }
}
