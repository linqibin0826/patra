package dev.linqibin.patra.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionClaims;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionDecoders;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner;
import dev.linqibin.patra.starter.security.test.TestSigningKey;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.filter.ForwardedHeaderFilter;

/// `createSigner`：四种启动失败和一种成功；错误信息点名配置项、不带密钥内容。
class GatewaySecurityConfigurationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC);
  private static final ECKey KEY = TestSigningKey.key();
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

  private final JwtDecoder decoder =
      IdentityAssertionDecoders.forPublicKeys(new JWKSet(KEY.toPublicJWK()), CLOCK);

  @Test
  void should_fail_when_private_key_is_missing() {
    for (String missing : Arrays.asList(null, "", "   ")) {
      assertThatThrownBy(() -> GatewaySecurityConfiguration.createSigner(missing, CLOCK, decoder))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(GatewayIdentityAssertionProperties.PRIVATE_KEY_PROPERTY);
    }
  }

  @Test
  void should_fail_when_value_is_not_a_jwk() {
    assertThatThrownBy(() -> GatewaySecurityConfiguration.createSigner("not-a-jwk", CLOCK, decoder))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(GatewayIdentityAssertionProperties.PRIVATE_KEY_PROPERTY)
        .hasMessageContaining("JWK");
  }

  @Test
  void should_fail_when_key_has_no_private_part() {
    String publicOnly = KEY.toPublicJWK().toJSONString();

    assertThatThrownBy(() -> GatewaySecurityConfiguration.createSigner(publicOnly, CLOCK, decoder))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(GatewayIdentityAssertionProperties.PRIVATE_KEY_PROPERTY)
        .hasMessageContaining("私钥");
  }

  @Test
  void should_fail_when_key_does_not_match_configured_public_keys() throws JOSEException {
    ECKey other = new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();

    assertThatThrownBy(
            () -> GatewaySecurityConfiguration.createSigner(other.toJSONString(), CLOCK, decoder))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("不配对")
        .hasMessageContaining(other.getKeyID())
        .hasMessageNotContaining(other.getD().toString())
        .hasMessageNotContaining(other.getX().toString());
  }

  @Test
  void should_sign_assertions_the_configured_decoder_accepts() {
    IdentityAssertionSigner signer =
        GatewaySecurityConfiguration.createSigner(KEY.toJSONString(), CLOCK, decoder);

    Jwt jwt = decoder.decode(signer.sign(USER));

    assertThat(IdentityAssertionClaims.toCurrentUser(jwt)).isEqualTo(USER);
    assertThat(jwt.getHeaders()).containsEntry("kid", KEY.getKeyID());
  }

  /// 网关前面没有反向代理：入站的 `Forwarded` / `X-Forwarded-*` 在 servlet 层直接剥掉、不解释，
  /// 路径和主机都按真实请求算；注册得先于 Security 和路由。
  @Test
  void should_strip_inbound_forwarded_headers_without_applying_them() throws Exception {
    FilterRegistrationBean<ForwardedHeaderFilter> registration =
        new GatewaySecurityConfiguration().forwardedHeaderFilter();
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/patra-catalog/portal/venues");
    request.addHeader("X-Forwarded-Prefix", "/evil");
    request.addHeader("X-Forwarded-Host", "evil.example");
    request.addHeader("Forwarded", "for=203.0.113.9;host=evil.example;proto=https");
    MockFilterChain chain = new MockFilterChain();

    registration.getFilter().doFilter(request, new MockHttpServletResponse(), chain);

    HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
    assertThat(downstream.getRequestURI()).isEqualTo("/patra-catalog/portal/venues");
    assertThat(downstream.getContextPath()).isEmpty();
    assertThat(downstream.getServerName()).isEqualTo("localhost");
    assertThat(downstream.getHeader("X-Forwarded-Prefix")).isNull();
    assertThat(downstream.getHeader("X-Forwarded-Host")).isNull();
    assertThat(downstream.getHeader("Forwarded")).isNull();
    assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }
}
