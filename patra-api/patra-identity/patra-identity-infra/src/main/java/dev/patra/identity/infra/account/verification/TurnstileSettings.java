package dev.patra.identity.infra.account.verification;

import java.util.Set;

/// Turnstile 校验要用的配置。每次校验取一次，配置刷新后下一次校验即用上新值。
///
/// @param baseUrl 接口地址
/// @param secret 服务端密钥，没配时为空
/// @param allowedHostnames 允许的站点域名
public record TurnstileSettings(String baseUrl, String secret, Set<String> allowedHostnames) {

  /// 防御性拷贝。
  public TurnstileSettings {
    allowedHostnames = allowedHostnames == null ? Set.of() : Set.copyOf(allowedHostnames);
  }

  /// 创建配置。
  ///
  /// @param baseUrl 接口地址
  /// @param secret 服务端密钥
  /// @param allowedHostnames 允许的站点域名
  /// @return 配置
  public static TurnstileSettings of(String baseUrl, String secret, Set<String> allowedHostnames) {
    return new TurnstileSettings(baseUrl, secret, allowedHostnames);
  }

  /// 不输出密钥。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "TurnstileSettings[baseUrl=" + baseUrl + ", allowedHostnames=" + allowedHostnames + "]";
  }
}
