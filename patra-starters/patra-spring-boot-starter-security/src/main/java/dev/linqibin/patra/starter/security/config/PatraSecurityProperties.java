package dev.linqibin.patra.starter.security.config;

import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// 安全 starter 的配置属性，前缀 `patra.security`。
///
/// 只在 servlet 应用里绑定，因为只有从请求头建立认证时才用到内部令牌。
///
/// @param gatewayToken 网关与下游共享的内部令牌，由部署时的 secret 注入
@ConfigurationProperties(prefix = "patra.security")
public record PatraSecurityProperties(String gatewayToken) {

  /// 可见 ASCII 字符（不含空格）。令牌要放进 HTTP 头里传输，首尾空白会被去掉。
  private static final Pattern VISIBLE_ASCII = Pattern.compile("[\\x21-\\x7E]+");

  /// 校验内部令牌。不合法时应用启动失败，错误信息指明配置项。
  public PatraSecurityProperties {
    if (gatewayToken == null || gatewayToken.isBlank()) {
      throw new IllegalArgumentException("配置项 patra.security.gateway-token 不能为空：它是网关与下游共享的内部令牌");
    }
    if (!VISIBLE_ASCII.matcher(gatewayToken).matches()) {
      throw new IllegalArgumentException(
          "配置项 patra.security.gateway-token 只能包含可见 ASCII 字符，不能带空白或换行："
              + "它要放进 HTTP 头里传输，首尾空白会被去掉，两边永远比不上");
    }
  }

  /// 不输出令牌的值，避免它经由日志泄露。
  ///
  /// @return 掩码后的字符串
  @Override
  public String toString() {
    return "PatraSecurityProperties[gatewayToken=***]";
  }
}
