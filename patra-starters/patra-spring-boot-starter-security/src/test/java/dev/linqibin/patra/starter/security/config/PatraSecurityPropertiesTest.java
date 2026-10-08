package dev.linqibin.patra.starter.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import dev.linqibin.patra.starter.security.config.PatraSecurityProperties.IdentityAssertion;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// PatraSecurityProperties 单元测试。
@DisplayName("PatraSecurityProperties 单元测试")
class PatraSecurityPropertiesTest {

  private static final String PROPERTY = "patra.security.identity-assertion.public-keys";

  @Test
  @DisplayName("合法的 JWK Set：解析出带 kid 的公钥")
  void should_parse_public_key_set() throws JOSEException {
    ECKey key = p256();

    IdentityAssertion assertion = new IdentityAssertion(publicSet(key.toPublicJWK()));

    assertThat(assertion.publicKeySet().getKeyByKeyId(key.getKeyID())).isNotNull();
    assertThat(new PatraSecurityProperties(assertion).identityAssertion()).isSameAs(assertion);
  }

  @Test
  @DisplayName("两把公钥都能解析")
  void should_accept_multiple_public_keys() throws JOSEException {
    ECKey first = p256();
    ECKey second = p256();

    IdentityAssertion assertion =
        new IdentityAssertion(publicSet(first.toPublicJWK(), second.toPublicJWK()));

    assertThat(assertion.publicKeySet().getKeys()).hasSize(2);
  }

  @Test
  @DisplayName("没配 identity-assertion 这一段：拒绝，并指明配置项")
  void should_reject_missing_section() {
    assertThatThrownBy(() -> new PatraSecurityProperties(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(PROPERTY);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "not json", "{\"kty\":\"EC\"}", "{\"keys\":[]}"})
  @DisplayName("为空、不是 JSON、不是 JWK Set、没有公钥：拒绝，并指明配置项")
  void should_reject_blank_or_unparseable_or_empty(String json) {
    assertThatThrownBy(() -> new IdentityAssertion(json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(PROPERTY);
  }

  @Test
  @DisplayName("集合里混进了私钥：拒绝，并说明私钥只能在网关")
  void should_reject_private_key() throws JOSEException {
    ECKey key = p256();

    assertThatThrownBy(() -> new IdentityAssertion(new JWKSet(key).toString(false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(PROPERTY)
        .hasMessageContaining("私钥");
  }

  @Test
  @DisplayName("不是 EC P-256 的公钥：拒绝")
  void should_reject_other_key_types() throws JOSEException {
    RSAKey rsa = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
    ECKey p384 = new ECKeyGenerator(Curve.P_384).keyIDFromThumbprint(true).generate();

    assertThatThrownBy(() -> new IdentityAssertion(publicSet(rsa.toPublicJWK())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("P-256");
    assertThatThrownBy(() -> new IdentityAssertion(publicSet(p384.toPublicJWK())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("P-256");
  }

  @Test
  @DisplayName("公钥没有 kid：拒绝")
  void should_reject_key_without_kid() throws JOSEException {
    ECKey withoutKid = new ECKeyGenerator(Curve.P_256).generate();

    assertThatThrownBy(() -> new IdentityAssertion(publicSet(withoutKid.toPublicJWK())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kid");
  }

  /// 带 kid 的 P-256 密钥对。
  private static ECKey p256() throws JOSEException {
    return new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
  }

  /// 只含公钥的 JWK Set JSON。
  private static String publicSet(JWK... keys) {
    return new JWKSet(List.of(keys)).toString(true);
  }
}
