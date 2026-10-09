package dev.linqibin.patra.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/// 网关签身份断言用的配置，前缀 `patra.gateway.identity-assertion`。
///
/// 只按字符串绑定、构造器不做任何校验：绑定阶段一旦抛异常，Boot 的失败报告会把配置值原样打进
/// 日志，私钥就泄露在日志里了。解析和校验在 `GatewaySecurityConfiguration.createSigner`。
///
/// @param privateKey 含私钥的 EC P-256 JWK JSON，带 `kid`；没配时为 `null`，启动失败
@ConfigurationProperties(prefix = "patra.gateway.identity-assertion")
public record GatewayIdentityAssertionProperties(String privateKey) {

  /// 私钥配置项的全名，用在错误信息里。
  public static final String PRIVATE_KEY_PROPERTY = "patra.gateway.identity-assertion.private-key";
}
