package dev.linqibin.patra.starter.security.assertion;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionKeyGenerator.GeneratedKey;
import java.text.ParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// IdentityAssertionKeyGenerator 单元测试。
@DisplayName("IdentityAssertionKeyGenerator 单元测试")
class IdentityAssertionKeyGeneratorTest {

  @Test
  @DisplayName("生成一对 P-256 密钥：私钥 JWK 含 d，公钥 JWK Set 不含 d，kid 相同且等于指纹")
  void should_generate_matching_private_jwk_and_public_jwk_set()
      throws ParseException, JOSEException {
    GeneratedKey generated = IdentityAssertionKeyGenerator.generate();

    ECKey privateKey = ECKey.parse(generated.privateJwk());
    JWKSet publicSet = JWKSet.parse(generated.publicJwkSet());
    JWK publicKey = publicSet.getKeys().getFirst();

    assertThat(privateKey.isPrivate()).isTrue();
    assertThat(privateKey.getCurve()).isEqualTo(Curve.P_256);
    assertThat(publicSet.getKeys()).hasSize(1);
    assertThat(publicKey.isPrivate()).isFalse();
    assertThat(publicKey.getKeyID())
        .isEqualTo(privateKey.getKeyID())
        .isEqualTo(privateKey.computeThumbprint().toString());
    assertThat(generated.privateJwk()).doesNotContain("\n");
    assertThat(generated.publicJwkSet()).doesNotContain("\n");
  }

  @Test
  @DisplayName("每次生成的密钥都不同")
  void should_generate_fresh_key_each_time() throws JOSEException {
    assertThat(IdentityAssertionKeyGenerator.generate().privateJwk())
        .isNotEqualTo(IdentityAssertionKeyGenerator.generate().privateJwk());
  }
}
