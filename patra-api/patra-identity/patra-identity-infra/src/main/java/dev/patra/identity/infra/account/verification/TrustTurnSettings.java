package dev.patra.identity.infra.account.verification;

import java.util.Set;

/// TRUSTTURN 校验要用的配置。每次校验取一次，配置刷新后下一次校验即用上新值。
///
/// @param baseUrl 接口地址
/// @param siteKey 站点的公开标识，没配时为空
/// @param appSecret 签名密钥，没配时为空
/// @param allowedOrigins 允许的来源
public record TrustTurnSettings(
    String baseUrl, String siteKey, String appSecret, Set<String> allowedOrigins) {

  /// 防御性拷贝。
  public TrustTurnSettings {
    allowedOrigins = allowedOrigins == null ? Set.of() : Set.copyOf(allowedOrigins);
  }

  /// 创建配置。
  ///
  /// @param baseUrl 接口地址
  /// @param siteKey 站点的公开标识
  /// @param appSecret 签名密钥
  /// @param allowedOrigins 允许的来源
  /// @return 配置
  public static TrustTurnSettings of(
      String baseUrl, String siteKey, String appSecret, Set<String> allowedOrigins) {
    return new TrustTurnSettings(baseUrl, siteKey, appSecret, allowedOrigins);
  }

  /// 不输出密钥。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "TrustTurnSettings[baseUrl="
        + baseUrl
        + ", siteKey="
        + siteKey
        + ", allowedOrigins="
        + allowedOrigins
        + "]";
  }
}
