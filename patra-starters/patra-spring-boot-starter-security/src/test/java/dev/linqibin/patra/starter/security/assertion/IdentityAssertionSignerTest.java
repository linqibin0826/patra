package dev.linqibin.patra.starter.security.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// IdentityAssertionSigner 单元测试。
@DisplayName("IdentityAssertionSigner 单元测试")
class IdentityAssertionSignerTest {

  private static final Instant NOW = Instant.parse("2026-10-08T08:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

  @Test
  @DisplayName("签出的断言：头、载荷、签名都符合设计")
  void should_sign_assertion_with_designed_header_and_claims()
      throws JOSEException, ParseException {
    ECKey key = generateKey();
    IdentityAssertionSigner signer = new IdentityAssertionSigner(key, CLOCK);

    SignedJWT jwt = SignedJWT.parse(signer.sign(USER));

    assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
    assertThat(jwt.getHeader().getType()).isEqualTo(new JOSEObjectType("patra-identity+jwt"));
    assertThat(jwt.getHeader().getKeyID()).isEqualTo(key.getKeyID());
    JWTClaimsSet claims = jwt.getJWTClaimsSet();
    assertThat(claims.getIssuer()).isEqualTo("patra-gateway");
    assertThat(claims.getAudience()).containsExactly("patra");
    assertThat(claims.getSubject()).isEqualTo("1001");
    assertThat(claims.getStringClaim("sid")).isEqualTo("2001");
    assertThat(claims.getStringClaim("account_type")).isEqualTo("user");
    assertThat(claims.getStringClaim("client_type")).isEqualTo("web");
    assertThat(claims.getIssueTime()).isEqualTo(Date.from(NOW));
    assertThat(claims.getExpirationTime()).isEqualTo(Date.from(NOW.plusSeconds(60)));
    assertThat(claims.getClaims()).doesNotContainKeys("nbf", "jti");
    assertThat(jwt.verify(new ECDSAVerifier(key.toPublicJWK()))).isTrue();
  }

  @Test
  @DisplayName("只有公钥的密钥不能签名")
  void should_reject_public_only_key() throws JOSEException {
    ECKey publicOnly = generateKey().toPublicJWK();

    assertThatThrownBy(() -> new IdentityAssertionSigner(publicOnly, CLOCK))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("私钥");
  }

  @Test
  @DisplayName("没有 kid 的密钥不能签名")
  void should_reject_key_without_kid() throws JOSEException {
    ECKey withoutKid = new ECKeyGenerator(Curve.P_256).generate();

    assertThatThrownBy(() -> new IdentityAssertionSigner(withoutKid, CLOCK))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kid");
  }

  @Test
  @DisplayName("不是 P-256 的密钥不能签名")
  void should_reject_key_on_other_curve() throws JOSEException {
    ECKey p384 = new ECKeyGenerator(Curve.P_384).keyIDFromThumbprint(true).generate();

    assertThatThrownBy(() -> new IdentityAssertionSigner(p384, CLOCK))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("P-256");
  }

  /// 带 kid（公钥指纹）的 P-256 密钥对。
  static ECKey generateKey() throws JOSEException {
    return new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
  }
}
