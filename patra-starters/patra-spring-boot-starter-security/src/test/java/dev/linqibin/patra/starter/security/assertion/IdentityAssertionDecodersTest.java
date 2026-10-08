package dev.linqibin.patra.starter.security.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/// IdentityAssertionDecoders 单元测试。
@DisplayName("IdentityAssertionDecoders 单元测试")
class IdentityAssertionDecodersTest {

  private static final Instant NOW = Instant.parse("2026-10-08T08:00:00Z");
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);
  private static final ECKey KEY_A = generate();
  private static final ECKey KEY_B = generate();
  private static final JWKSet PUBLIC_A = new JWKSet(KEY_A.toPublicJWK());

  @Test
  @DisplayName("已知公钥签的断言：接受，声明原样可读")
  void should_accept_assertion_signed_by_known_key() {
    Jwt jwt = decoder(PUBLIC_A, NOW).decode(signer(KEY_A).sign(USER));

    assertThat(jwt.getSubject()).isEqualTo("1001");
    assertThat(jwt.getClaimAsString("sid")).isEqualTo("2001");
    assertThat(jwt.getAudience()).containsExactly("patra");
  }

  @Test
  @DisplayName("未知密钥签的断言：拒绝")
  void should_reject_assertion_signed_by_unknown_key() {
    String assertion = signer(KEY_B).sign(USER);

    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(assertion))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("过期 20 秒在容差内放行，过期 31 秒拒绝")
  void should_apply_clock_skew_window() {
    String assertion = signer(KEY_A).sign(USER);

    assertThat(decoder(PUBLIC_A, NOW.plusSeconds(80)).decode(assertion)).isNotNull();
    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW.plusSeconds(91)).decode(assertion))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("typ 不是 patra-identity+jwt 或缺省：拒绝")
  void should_reject_wrong_or_missing_type() throws JOSEException {
    String plainType =
        signed(KEY_A, header(KEY_A).type(JOSEObjectType.JWT).build(), claims().build());
    String noType = signed(KEY_A, header(KEY_A).type(null).build(), claims().build());

    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(plainType))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(noType))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("iss 或 aud 不对：拒绝")
  void should_reject_wrong_issuer_or_audience() throws JOSEException {
    String wrongIssuer =
        signed(KEY_A, header(KEY_A).build(), claims().issuer("someone-else").build());
    String wrongAudience =
        signed(KEY_A, header(KEY_A).build(), claims().audience("other-system").build());

    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(wrongIssuer))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(wrongAudience))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("alg 为 none 或 HS256：拒绝")
  void should_reject_unsecured_and_hmac_tokens() throws JOSEException {
    String unsecured = new PlainJWT(claims().build()).serialize();
    SignedJWT hmac =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(new JOSEObjectType("patra-identity+jwt"))
                .keyID(KEY_A.getKeyID())
                .build(),
            claims().build());
    hmac.sign(new MACSigner(new byte[32]));

    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(unsecured))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(hmac.serialize()))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("集合里有两把公钥时按 kid 选钥；kid 对不上时拒绝")
  void should_select_key_by_kid() throws JOSEException {
    JWKSet both = new JWKSet(List.of(KEY_A.toPublicJWK(), KEY_B.toPublicJWK()));
    String signedByB = signer(KEY_B).sign(USER);
    String unknownKid = signed(KEY_A, header(KEY_A).keyID("nope").build(), claims().build());

    assertThat(decoder(both, NOW).decode(signedByB).getSubject()).isEqualTo("1001");
    assertThatThrownBy(() -> decoder(both, NOW).decode(unknownKid))
        .isInstanceOf(JwtException.class);
  }

  /// 固定时钟的解码器。
  private static JwtDecoder decoder(JWKSet keys, Instant now) {
    return IdentityAssertionDecoders.forPublicKeys(keys, Clock.fixed(now, ZoneOffset.UTC));
  }

  /// 在 NOW 签名的签名器。
  private static IdentityAssertionSigner signer(ECKey key) {
    return new IdentityAssertionSigner(key, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /// 合法的载荷，测试各自改坏一处。
  private static JWTClaimsSet.Builder claims() {
    return new JWTClaimsSet.Builder()
        .issuer("patra-gateway")
        .audience("patra")
        .subject("1001")
        .claim("sid", "2001")
        .claim("account_type", "user")
        .claim("client_type", "web")
        .issueTime(Date.from(NOW))
        .expirationTime(Date.from(NOW.plusSeconds(60)));
  }

  /// 合法的头，测试各自改坏一处。
  private static JWSHeader.Builder header(ECKey key) {
    return new JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(new JOSEObjectType("patra-identity+jwt"))
        .keyID(key.getKeyID());
  }

  /// 用指定密钥签任意头和载荷。
  private static String signed(ECKey key, JWSHeader header, JWTClaimsSet claims)
      throws JOSEException {
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(new ECDSASigner(key));
    return jwt.serialize();
  }

  /// 带 kid 的 P-256 密钥对。
  private static ECKey generate() {
    try {
      return new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("生成测试密钥失败", e);
    }
  }
}
