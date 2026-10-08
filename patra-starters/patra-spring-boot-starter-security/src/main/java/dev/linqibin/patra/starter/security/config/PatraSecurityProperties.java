package dev.linqibin.patra.starter.security.config;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import java.text.ParseException;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// 安全 starter 的配置属性，前缀 `patra.security`。
///
/// 只在 servlet 应用里绑定，因为只有验签时才用到公钥。
///
/// @param identityAssertion 身份断言相关的配置
@ConfigurationProperties(prefix = "patra.security")
public record PatraSecurityProperties(IdentityAssertion identityAssertion) {

  /// 校验：没配 `identity-assertion` 这一段时应用启动失败，错误信息指明配置项。
  public PatraSecurityProperties {
    if (identityAssertion == null) {
      throw new IllegalArgumentException(IdentityAssertion.missingMessage());
    }
  }

  /// 身份断言的配置。
  ///
  /// @param publicKeys 网关的公钥，JWK Set JSON，只含公钥，可以多把
  public record IdentityAssertion(String publicKeys) {

    /// 公钥配置项的全名，用在错误信息里。
    public static final String PUBLIC_KEYS_PROPERTY =
        "patra.security.identity-assertion.public-keys";

    /// 校验公钥集合。不合法时应用启动失败，错误信息指明配置项和原因。
    public IdentityAssertion {
      parsePublicKeys(publicKeys);
    }

    /// 解析成公钥集合。启动时已经校验过，这里不会失败。
    ///
    /// @return 公钥集合
    public JWKSet publicKeySet() {
      return parsePublicKeys(publicKeys);
    }

    /// 「没配」的错误信息。
    ///
    /// @return 错误信息
    static String missingMessage() {
      return "配置项 " + PUBLIC_KEYS_PROPERTY + " 不能为空：它是网关签身份断言的公钥（JWK Set JSON）";
    }

    /// 解析并校验：是 JWK Set、至少一把、都是不含私钥的 EC P-256 公钥、都带 kid。
    ///
    /// @param json 配置的值
    /// @return 公钥集合
    /// @throws IllegalArgumentException 任何一项不满足时
    private static JWKSet parsePublicKeys(String json) {
      if (json == null || json.isBlank()) {
        throw new IllegalArgumentException(missingMessage());
      }
      JWKSet set;
      try {
        set = JWKSet.parse(json);
      } catch (ParseException e) {
        throw new IllegalArgumentException(
            "配置项 " + PUBLIC_KEYS_PROPERTY + " 不是合法的 JWK Set JSON", e);
      }
      if (set.getKeys().isEmpty()) {
        throw new IllegalArgumentException("配置项 " + PUBLIC_KEYS_PROPERTY + " 至少要有一把公钥");
      }
      for (JWK key : set.getKeys()) {
        if (key.isPrivate()) {
          throw new IllegalArgumentException(
              "配置项 " + PUBLIC_KEYS_PROPERTY + " 含有私钥：下游只能配公钥，私钥只能在网关");
        }
        if (!(key instanceof ECKey ecKey) || !Curve.P_256.equals(ecKey.getCurve())) {
          throw new IllegalArgumentException("配置项 " + PUBLIC_KEYS_PROPERTY + " 只能是 EC P-256 公钥");
        }
        if (key.getKeyID() == null || key.getKeyID().isBlank()) {
          throw new IllegalArgumentException("配置项 " + PUBLIC_KEYS_PROPERTY + " 里每把公钥都要有 kid");
        }
      }
      return set;
    }
  }
}
