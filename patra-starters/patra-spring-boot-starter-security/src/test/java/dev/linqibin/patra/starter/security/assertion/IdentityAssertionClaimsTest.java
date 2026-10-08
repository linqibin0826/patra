package dev.linqibin.patra.starter.security.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.authentication.MalformedIdentityException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

/// IdentityAssertionClaims 单元测试。
@DisplayName("IdentityAssertionClaims 单元测试")
class IdentityAssertionClaimsTest {

  @Test
  @DisplayName("格式常量是设计定下的值")
  void should_expose_format_constants() {
    assertThat(IdentityAssertionClaims.TYPE).isEqualTo("patra-identity+jwt");
    assertThat(IdentityAssertionClaims.ISSUER).isEqualTo("patra-gateway");
    assertThat(IdentityAssertionClaims.AUDIENCE).isEqualTo("patra");
    assertThat(IdentityAssertionClaims.LIFETIME.toSeconds()).isEqualTo(60);
  }

  @Test
  @DisplayName("声明齐全且合法：还原出当前用户")
  void should_build_current_user_from_claims() {
    CurrentUser user = IdentityAssertionClaims.toCurrentUser(jwt(validClaims()));

    assertThat(user).isEqualTo(CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "0", "-1", "+1001", "01001", "1001.0", "99999999999999999999"})
  @DisplayName("sub 不是规范写法的正整数：内容不合法")
  void should_reject_invalid_subject(String subject) {
    Map<String, Object> claims = validClaims();
    claims.put("sub", subject);

    assertThatThrownBy(() -> IdentityAssertionClaims.toCurrentUser(jwt(claims)))
        .isInstanceOf(MalformedIdentityException.class)
        .hasMessageContaining("sub");
  }

  @Test
  @DisplayName("缺 sid：内容不合法")
  void should_reject_missing_session_id() {
    Map<String, Object> claims = validClaims();
    claims.remove("sid");

    assertThatThrownBy(() -> IdentityAssertionClaims.toCurrentUser(jwt(claims)))
        .isInstanceOf(MalformedIdentityException.class)
        .hasMessageContaining("sid");
  }

  @Test
  @DisplayName("账号类型不认识：内容不合法")
  void should_reject_unknown_account_type() {
    Map<String, Object> claims = validClaims();
    claims.put("account_type", "staff");

    assertThatThrownBy(() -> IdentityAssertionClaims.toCurrentUser(jwt(claims)))
        .isInstanceOf(MalformedIdentityException.class)
        .hasMessageContaining("account_type");
  }

  @Test
  @DisplayName("客户端类型不认识：内容不合法")
  void should_reject_unknown_client_type() {
    Map<String, Object> claims = validClaims();
    claims.put("client_type", "ios");

    assertThatThrownBy(() -> IdentityAssertionClaims.toCurrentUser(jwt(claims)))
        .isInstanceOf(MalformedIdentityException.class)
        .hasMessageContaining("client_type");
  }

  /// 合法的载荷，测试各自改坏一处。
  private static Map<String, Object> validClaims() {
    Map<String, Object> claims = new HashMap<>();
    claims.put("iss", "patra-gateway");
    claims.put("aud", "patra");
    claims.put("sub", "1001");
    claims.put("sid", "2001");
    claims.put("account_type", "user");
    claims.put("client_type", "web");
    return claims;
  }

  /// 不验签的 Jwt 对象：这里只测声明到当前用户的转换。
  private static Jwt jwt(Map<String, Object> claims) {
    Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "ES256");
    claims.forEach(builder::claim);
    return builder.build();
  }
}
