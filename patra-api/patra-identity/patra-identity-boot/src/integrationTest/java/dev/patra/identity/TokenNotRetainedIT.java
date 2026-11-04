package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.api.account.event.PasswordResetMailEvent;
import dev.patra.identity.config.FakeProviderFixture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.DataType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

/// 第三方令牌不留痕（release spec D10）：走完流程后，Redis、外部身份表和框架的内存仓库里都没有 access token。
class TokenNotRetainedIT extends ProviderFlowITSupport {

  @Autowired OAuth2AuthorizedClientService authorizedClients;

  /// Redis 里每个键的全部内容拼起来。
  private String redisDump() {
    StringBuilder dump = new StringBuilder();
    for (String key : redis.keys("*")) {
      dump.append(key).append('\n');
      DataType type = redis.type(key);
      if (type == DataType.STRING) {
        dump.append(redis.opsForValue().get(key)).append('\n');
      } else if (type == DataType.HASH) {
        dump.append(redis.opsForHash().entries(key)).append('\n');
      } else if (type == DataType.ZSET) {
        dump.append(redis.opsForZSet().range(key, 0, -1)).append('\n');
      }
    }
    return dump.toString();
  }

  @Test
  void should_not_keep_token_after_login_and_registration() {
    RawResponse registered =
        registerViaGithub(11001L, "token-gh", "token-gh", "flow.token@example.com");
    AuthorizationStart google = startLogin("google");
    FakeProviderFixture.stubGoogle("token-sub", "flow.token.g@example.com", google.nonce());
    callback("google", google.state(), noHeaders());
    long userId = userIdOf(registered);

    assertThat(redisDump()).doesNotContain(FakeProviderFixture.ACCESS_TOKEN);
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT * FROM idn_user_external_identity WHERE user_id = ?", userId);
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().toString()).doesNotContain(FakeProviderFixture.ACCESS_TOKEN);
    OAuth2AuthorizedClient github = authorizedClients.loadAuthorizedClient("github", "11001");
    OAuth2AuthorizedClient googleClient =
        authorizedClients.loadAuthorizedClient("google", "token-sub");
    assertThat(github).isNull();
    assertThat(googleClient).isNull();
  }

  /// Review Focus：第三方注册的账号走找回密码，外部身份不变，还能继续用第三方登录。
  @Test
  void should_keep_identity_after_password_reset() {
    String email = "flow.token.reset@example.com";
    long userId = userIdOf(registerViaGithub(11002L, "token-rs", "token-rs", email));
    post("/auth/password/forgot", Map.of("email", email), noHeaders());
    String token = tokenIn(mailEvent(PasswordResetMailEvent.class, email).resetUrl());

    RawResponse reset =
        post(
            "/auth/password/reset",
            Map.of("token", token, "newPassword", "Reset-Password-2026"),
            noHeaders());

    assertThat(reset.status()).as(reset.body()).isEqualTo(200);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT subject FROM idn_user_external_identity WHERE user_id = ?",
                String.class,
                userId))
        .isEqualTo("11002");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT email_verified_at IS NOT NULL FROM idn_user WHERE id = ?",
                Boolean.class,
                userId))
        .isTrue();
    RawResponse viaGithub = githubLogin(11002L, "token-rs");
    assertThat(json(viaGithub).get("result").asString()).isEqualTo("AUTHENTICATED");
    assertThat(userIdOf(viaGithub)).isEqualTo(userId);
  }
}
