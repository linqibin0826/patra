package dev.linqibin.patra.starter.security.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.JWKSet;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionClaims;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionDecoders;
import dev.linqibin.patra.starter.security.authentication.MalformedIdentityException;
import java.text.ParseException;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/// TestIdentity 与 TestSigningKey 单元测试。
@DisplayName("TestIdentity 单元测试")
class TestIdentityTest {

  @Test
  @DisplayName("默认的请求头是一个 Bearer 断言，能用测试公钥验签并还原出默认用户")
  void should_produce_bearer_header_that_decodes_to_default_user() throws ParseException {
    HttpHeaders headers = TestIdentity.headers();

    String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
    assertThat(headers.size()).isEqualTo(1);
    assertThat(authorization).startsWith("Bearer ");
    assertThat(decode(authorization.substring("Bearer ".length())))
        .isEqualTo(CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB));
  }

  @Test
  @DisplayName("可以只指定用户 ID")
  void should_use_given_user_id() throws ParseException {
    assertThat(decode(TestIdentity.assertion(TestIdentity.user(42L))))
        .isEqualTo(CurrentUser.of(42L, 2001L, AccountType.USER, ClientType.WEB));
  }

  @Test
  @DisplayName("可以指定整个用户")
  void should_use_given_user() throws ParseException {
    CurrentUser user = CurrentUser.of(7L, 8L, AccountType.USER, ClientType.WEB);

    assertThat(decode(TestIdentity.assertion(user))).isEqualTo(user);
  }

  @Test
  @DisplayName("用同一把测试私钥签任意载荷：签名有效，内容由测试自己决定")
  void should_sign_custom_claims_with_same_key() throws ParseException {
    String malformed =
        TestSigningKey.sign(TestSigningKey.claims(TestIdentity.user()).subject("abc").build());

    Jwt jwt = decoder().decode(malformed);
    assertThatThrownBy(() -> IdentityAssertionClaims.toCurrentUser(jwt))
        .isInstanceOf(MalformedIdentityException.class);
  }

  /// 用测试公钥验签并还原用户。
  private static CurrentUser decode(String assertion) throws ParseException {
    return IdentityAssertionClaims.toCurrentUser(decoder().decode(assertion));
  }

  /// 用 `TestSigningKey.publicJwkSet()` 建的解码器，和使用方的应用里一样。
  private static JwtDecoder decoder() throws ParseException {
    return IdentityAssertionDecoders.forPublicKeys(
        JWKSet.parse(TestSigningKey.publicJwkSet()), Clock.systemUTC());
  }
}
