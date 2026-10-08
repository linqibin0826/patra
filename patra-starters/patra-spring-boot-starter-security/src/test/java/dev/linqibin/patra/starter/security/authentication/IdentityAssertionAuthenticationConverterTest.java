package dev.linqibin.patra.starter.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionDecoders;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

/// IdentityAssertionAuthenticationConverter 单元测试：spec 第 8.2 节那张表的四行。
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("IdentityAssertionAuthenticationConverter 单元测试")
class IdentityAssertionAuthenticationConverterTest {

  private static final Instant NOW = Instant.parse("2026-10-08T08:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);
  private static final ECKey KEY = generate();

  private final IdentityAssertionSigner signer = new IdentityAssertionSigner(KEY, CLOCK);
  private final IdentityAssertionAuthenticationConverter converter =
      new IdentityAssertionAuthenticationConverter(
          IdentityAssertionDecoders.forPublicKeys(new JWKSet(KEY.toPublicJWK()), CLOCK));

  @Test
  @DisplayName("没有 Authorization 头：返回 null，按匿名处理")
  void should_return_null_when_no_authorization_header() {
    assertThat(converter.convert(request())).isNull();
  }

  @Test
  @DisplayName("单个 Bearer 断言且有效：已登录，凭据是原始断言")
  void should_authenticate_when_assertion_valid() {
    String assertion = signer.sign(USER);

    Authentication authentication = converter.convert(request("Bearer " + assertion));

    assertThat(authentication).isInstanceOf(CurrentUserAuthentication.class);
    assertThat(authentication.getPrincipal()).isEqualTo(USER);
    assertThat(authentication.getCredentials()).isEqualTo(assertion);
  }

  @Test
  @DisplayName("方案名不区分大小写，断言两边的空白被去掉")
  void should_accept_lowercase_scheme_and_surrounding_whitespace() {
    Authentication authentication =
        converter.convert(request("bearer  " + signer.sign(USER) + " "));

    assertThat(authentication.getPrincipal()).isEqualTo(USER);
  }

  @Test
  @DisplayName("Authorization 头出现多次：401，日志记原因不记断言")
  void should_reject_multiple_authorization_headers(CapturedOutput output) {
    String assertion = signer.sign(USER);
    MockHttpServletRequest request = request("Bearer " + assertion);
    request.addHeader("Authorization", "Bearer " + assertion);

    assertThatThrownBy(() -> converter.convert(request))
        .isInstanceOf(BadCredentialsException.class);
    assertThat(output).contains("拒绝身份断言").contains("出现多次").doesNotContain(assertion);
  }

  @Test
  @DisplayName("不是 Bearer 方案：401")
  void should_reject_non_bearer_scheme() {
    assertThatThrownBy(() -> converter.convert(request("Basic dXNlcjpwYXNz")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  @DisplayName("不是合法的 JWT：401")
  void should_reject_garbage_token() {
    assertThatThrownBy(() -> converter.convert(request("Bearer not-a-jwt")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  @DisplayName("过期的断言：401，日志记原因不记断言")
  void should_reject_expired_assertion(CapturedOutput output) throws JOSEException {
    String expired = signed(claims().expirationTime(Date.from(NOW.minusSeconds(120))).build());

    assertThatThrownBy(() -> converter.convert(request("Bearer " + expired)))
        .isInstanceOf(BadCredentialsException.class);
    assertThat(output).contains("拒绝身份断言").doesNotContain(expired);
  }

  @Test
  @DisplayName("typ 里带换行的伪造断言：401，日志里不出现换行和伪造的行")
  void should_not_let_unverified_header_forge_log_lines(CapturedOutput output)
      throws JOSEException {
    ECKey attacker = generate();
    SignedJWT forged =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("x\n2026-10-08 ERROR 伪造的一行"))
                .keyID(KEY.getKeyID())
                .build(),
            claims().build());
    forged.sign(new ECDSASigner(attacker));

    assertThatThrownBy(() -> converter.convert(request("Bearer " + forged.serialize())))
        .isInstanceOf(BadCredentialsException.class);
    assertThat(output).contains("拒绝身份断言").doesNotContain("\n2026-10-08 ERROR 伪造的一行");
  }

  @Test
  @DisplayName("签名正确但 sub 不是正整数：500")
  void should_fail_with_malformed_identity_when_claims_invalid() throws JOSEException {
    String malformed = signed(claims().subject("abc").build());

    assertThatThrownBy(() -> converter.convert(request("Bearer " + malformed)))
        .isInstanceOf(MalformedIdentityException.class);
  }

  /// 不带 Authorization 头的请求。
  private static MockHttpServletRequest request() {
    return new MockHttpServletRequest("GET", "/probe");
  }

  /// 带一个 Authorization 头的请求。
  private static MockHttpServletRequest request(String authorization) {
    MockHttpServletRequest request = request();
    request.addHeader("Authorization", authorization);
    return request;
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

  /// 用测试密钥签任意载荷。
  private static String signed(JWTClaimsSet claims) throws JOSEException {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("patra-identity+jwt"))
                .keyID(KEY.getKeyID())
                .build(),
            claims);
    jwt.sign(new ECDSASigner(KEY));
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
