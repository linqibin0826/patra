package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.api.account.event.RegistrationCodeMailEvent;
import dev.patra.identity.config.FakeProviderFixture;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

/// 第三方登录流程测试的共用基座：平台由 {@link FakeProviderFixture} 扮演，发起和回调都按门户转发的样子请求。
abstract class ProviderFlowITSupport extends IdentityFlowITSupport {

  /// 把平台的端点指到 WireMock。
  ///
  /// @param registry 动态属性
  @DynamicPropertySource
  static void fakeProviders(DynamicPropertyRegistry registry) {
    FakeProviderFixture.registerEndpoints(registry);
  }

  /// 每个用例从干净的桩开始；清掉发码按 IP 的窗口：所有用例同一个 IP，时钟又都从 T0 开始，不清会攒到 429。
  @BeforeEach
  void resetProviders() {
    FakeProviderFixture.reset();
    redis.delete("idn:oauth-code:hour:ip:" + sha256Hex("127.0.0.1").substring(0, 32));
  }

  /// 走一遍 GitHub 发起和回调，拿到待补填凭证。
  ///
  /// @param id GitHub 数字 ID
  /// @param login 登录名
  /// @return 待补填凭证
  String githubTicket(long id, String login) {
    AuthorizationStart start = startLogin("github");
    FakeProviderFixture.stubGithub(id, login, null);
    RawResponse response = callback("github", start.state(), noHeaders());
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    JsonNode json = json(response);
    assertThat(json.get("result").asString()).isEqualTo("REGISTRATION_REQUIRED");
    return json.get("ticket").asString();
  }

  /// 走一遍 GitHub 登录，不断言结果。
  ///
  /// @param id GitHub 数字 ID
  /// @param login 登录名
  /// @return 回调的响应
  RawResponse githubLogin(long id, String login) {
    AuthorizationStart start = startLogin("github");
    FakeProviderFixture.stubGithub(id, login, null);
    return callback("github", start.state(), noHeaders());
  }

  /// 补填阶段的请求头。
  ///
  /// @param ticket 待补填凭证
  /// @return 请求头
  static HttpHeaders ticketHeader(String ticket) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Patra-Registration-Ticket", ticket);
    return headers;
  }

  /// 提交用户名和邮箱，要求发码成功，返回邮件里的验证码。
  ///
  /// @param ticket 待补填凭证
  /// @param username 用户名
  /// @param email 邮箱
  /// @return 验证码
  String requestCode(String ticket, String username, String email) {
    RawResponse response =
        post(
            "/auth/oauth/registration/code",
            Map.of("username", username, "email", email),
            ticketHeader(ticket));
    assertThat(response.status()).as(response.body()).isEqualTo(202);
    return mailEvent(RegistrationCodeMailEvent.class, email).code();
  }

  /// 提交验证码。
  ///
  /// @param ticket 待补填凭证
  /// @param code 验证码
  /// @return 响应
  RawResponse completeRegistration(String ticket, String code) {
    return post("/auth/oauth/registration/complete", Map.of("code", code), ticketHeader(ticket));
  }

  /// 走完 GitHub 首次注册，返回带会话的响应。
  ///
  /// @param id GitHub 数字 ID
  /// @param login 登录名
  /// @param username 用户名
  /// @param email 邮箱
  /// @return 完成注册的响应
  RawResponse registerViaGithub(long id, String login, String username, String email) {
    String ticket = githubTicket(id, login);
    RawResponse response = completeRegistration(ticket, requestCode(ticket, username, email));
    assertThat(response.status()).as(response.body()).isEqualTo(201);
    return response;
  }

  /// 发起登录：断言 302，返回跳转地址和查询参数。
  ///
  /// @param provider 平台
  /// @return 发起结果
  AuthorizationStart startLogin(String provider) {
    RawResponse response = get("/oauth2/authorization/" + provider, noHeaders());
    assertThat(response.status()).as(response.body()).isEqualTo(302);
    return AuthorizationStart.of(response.headers().getFirst(HttpHeaders.LOCATION));
  }

  /// 回调：授权码随便给，真假由令牌端点的桩决定。
  ///
  /// @param provider 平台
  /// @param state `state`
  /// @param headers 请求头
  /// @return 响应
  RawResponse callback(String provider, String state, HttpHeaders headers) {
    return get("/login/oauth2/code/" + provider + "?code=fake-code&state=" + state, headers);
  }

  /// 发起授权的结果。
  ///
  /// @param location 跳转地址
  /// @param query 解码后的查询参数
  record AuthorizationStart(String location, Map<String, String> query) {

    /// 解析跳转地址。
    ///
    /// @param location 跳转地址
    /// @return 结果
    static AuthorizationStart of(String location) {
      Map<String, String> query =
          Stream.of(URI.create(location).getRawQuery().split("&"))
              .map(pair -> pair.split("=", 2))
              .collect(
                  Collectors.toMap(
                      pair -> pair[0], pair -> UriUtils.decode(pair[1], StandardCharsets.UTF_8)));
      return new AuthorizationStart(location, query);
    }

    /// `state`。
    ///
    /// @return `state`
    String state() {
      return query.get("state");
    }

    /// `nonce`：OIDC 平台才有，放进 `id_token`。
    ///
    /// @return `nonce`
    String nonce() {
      return query.get("nonce");
    }
  }
}
