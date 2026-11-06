package dev.patra.identity.infra.account.verification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.patra.identity.domain.account.model.enums.HumanVerificationAction;
import dev.patra.identity.domain.account.model.enums.HumanVerificationResult;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/// TrustTurnAdapter 集成测试：用 WireMock 模拟 `/api/tickets/consume`。
@DisplayName("TrustTurnAdapter 集成测试")
class TrustTurnAdapterIT {

  private static final String PATH = "/api/tickets/consume";
  private static final String SITE_KEY = "site-under-test";
  private static final String ORIGIN = "https://patra.linqibin.dev";
  private static final String SIGNING_INPUT = "S".repeat(40);
  private static final String TICKET = "T".repeat(64);
  private static final Instant NOW = Instant.parse("2026-11-05T09:00:00.123Z");

  @RegisterExtension
  static final WireMockExtension REMOTE =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  @Test
  @DisplayName("success 为真且 site_key、action、origin 都对上：通过")
  void passed_when_success_and_site_key_action_origin_match() {
    stubBody(successBody(SITE_KEY, "login", ORIGIN));

    assertThat(verify()).isEqualTo(HumanVerificationResult.PASSED);
  }

  @Test
  @DisplayName("site_key 对不上：拒绝")
  void rejected_when_site_key_differs() {
    stubBody(successBody("other", "login", ORIGIN));

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("action 对不上：拒绝")
  void rejected_when_action_differs() {
    stubBody(successBody(SITE_KEY, "register", ORIGIN));

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("origin 不在允许名单：拒绝")
  void rejected_when_origin_not_allowed() {
    stubBody(successBody(SITE_KEY, "login", "https://evil.example.com"));

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("票据无效：拒绝")
  void rejected_on_invalid_ticket() {
    stubError(400, "invalid_ticket");

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("动作不一致：拒绝")
  void rejected_on_action_mismatch() {
    stubError(400, "action_mismatch");

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("凭证已用过：拒绝")
  void rejected_on_replayed_token() {
    stubError(400, "replayed_token");

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("环境风险：拒绝")
  void rejected_on_environment_risk() {
    stubError(403, "environment_risk");

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("签名错误：验证不了，是我们的配置问题")
  void unavailable_on_invalid_request_signature() {
    stubError(401, "invalid_request_signature");

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
  }

  @Test
  @DisplayName("nonce 重复：验证不了")
  void unavailable_on_replayed_request() {
    stubError(409, "replayed_request");

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
  }

  @Test
  @DisplayName("站点授权到期：验证不了")
  void unavailable_on_site_authorization_expired() {
    stubError(403, "site_authorization_expired");

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
  }

  @Test
  @DisplayName("没有错误码也没有 success：验证不了")
  void unavailable_when_error_code_unreadable() {
    stubBody("{\"message\":\"nope\"}");

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
  }

  @Test
  @DisplayName("500：验证不了，不重试，票据可能已被消费")
  void unavailable_on_500_without_retry() {
    REMOTE.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
    REMOTE.verify(1, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName("读超时：验证不了，不重试")
  void unavailable_on_read_timeout() {
    REMOTE.stubFor(
        post(urlEqualTo(PATH))
            .willReturn(okJson(successBody(SITE_KEY, "login", ORIGIN)).withFixedDelay(5_000)));

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
    REMOTE.verify(1, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName("200 但不是 JSON：验证不了，绝不算通过")
  void unavailable_when_body_is_not_json() {
    stubBody("<html>gateway</html>");

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
  }

  @Test
  @DisplayName("success 不是布尔值：验证不了，绝不算通过")
  void unavailable_when_success_is_not_boolean() {
    stubBody(
        "{\"success\":\"true\",\"site_key\":\""
            + SITE_KEY
            + "\",\"action\":\"login\",\"origin\":\""
            + ORIGIN
            + "\"}");

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
  }

  @Test
  @DisplayName("密钥没配：验证不了，不发请求")
  void unavailable_when_app_secret_blank_without_calling_remote() {
    assertThat(adapterWith(" ").verify(TICKET, HumanVerificationAction.LOGIN))
        .isEqualTo(HumanVerificationResult.UNAVAILABLE);
    REMOTE.verify(0, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName("票据含空白：拒绝，不发请求")
  void rejected_without_calling_remote_when_ticket_has_whitespace() {
    assertThat(adapterWith(SIGNING_INPUT).verify("a b", HumanVerificationAction.LOGIN))
        .isEqualTo(HumanVerificationResult.REJECTED);
    REMOTE.verify(0, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName("请求头的时间戳、nonce 和签名对得上实际发出的请求体")
  void request_headers_are_signed_correctly() {
    stubBody(successBody(SITE_KEY, "login", ORIGIN));

    verify();

    LoggedRequest request = onlyRequest();
    String timestamp = request.getHeader("X-TrustTurn-Timestamp");
    String nonce = request.getHeader("X-TrustTurn-Nonce");
    assertThat(timestamp).isEqualTo(String.valueOf(NOW.toEpochMilli()));
    assertThat(request.getHeader("X-TrustTurn-Signature"))
        .isEqualTo(
            TrustTurnRequestSigner.sign(
                SIGNING_INPUT, Long.parseLong(timestamp), nonce, request.getBody()));
  }

  @Test
  @DisplayName("每次请求的 nonce 都不同")
  void nonce_differs_between_requests() {
    stubBody(successBody(SITE_KEY, "login", ORIGIN));

    verify();
    verify();

    List<String> nonces =
        REMOTE.findAll(postRequestedFor(urlEqualTo(PATH))).stream()
            .map(request -> request.getHeader("X-TrustTurn-Nonce"))
            .toList();
    assertThat(nonces).hasSize(2).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("请求体只有 site_key、ticket、expected_action，不传任何用户数据")
  void body_contains_only_site_key_ticket_expected_action() {
    stubBody(successBody(SITE_KEY, "login", ORIGIN));

    verify();

    Map<?, ?> body = JsonMapper.builder().build().readValue(onlyRequest().getBody(), Map.class);
    assertThat(body)
        .isEqualTo(Map.of("site_key", SITE_KEY, "ticket", TICKET, "expected_action", "login"));
  }

  @Test
  @DisplayName("接口地址被刷新成非法值：验证不了，不抛异常")
  void unavailable_when_base_url_invalid() {
    TrustTurnAdapter adapter =
        new TrustTurnAdapter(
            RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient()))
                .build(),
            JsonMapper.builder().build(),
            () -> TrustTurnSettings.of("not a url", SITE_KEY, SIGNING_INPUT, Set.of(ORIGIN)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(adapter.verify(TICKET, HumanVerificationAction.LOGIN))
        .isEqualTo(HumanVerificationResult.UNAVAILABLE);
  }

  @Test
  @DisplayName("provider 是 TRUSTTURN")
  void provider_is_trust_turn() {
    assertThat(adapterWith(SIGNING_INPUT).provider().wireValue()).isEqualTo("trustturn");
  }

  /// 用默认配置校验一次登录票据。
  ///
  /// @return 结果
  private HumanVerificationResult verify() {
    return adapterWith(SIGNING_INPUT).verify(TICKET, HumanVerificationAction.LOGIN);
  }

  /// 指向 WireMock 的适配器，读超时 2 秒，高负载下第一个请求也够用，时钟固定。
  ///
  /// @param appSecret 签名密钥
  /// @return 适配器
  private TrustTurnAdapter adapterWith(String appSecret) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
    factory.setReadTimeout(Duration.ofSeconds(2));
    return new TrustTurnAdapter(
        RestClient.builder().requestFactory(factory).build(),
        JsonMapper.builder().build(),
        () -> TrustTurnSettings.of(REMOTE.baseUrl(), SITE_KEY, appSecret, Set.of(ORIGIN)),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /// 取唯一的一次请求。
  ///
  /// @return 请求
  private LoggedRequest onlyRequest() {
    List<LoggedRequest> requests = REMOTE.findAll(postRequestedFor(urlEqualTo(PATH)));
    assertThat(requests).hasSize(1);
    return requests.getFirst();
  }

  /// 让接口返回 200 和给定的响应体。
  ///
  /// @param body 响应体
  private void stubBody(String body) {
    REMOTE.stubFor(
        post(urlEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  /// 让接口返回错误码。形状来自真实响应：`code` 是错误码，`error` 是给人看的中文说明。
  ///
  /// @param status 状态码
  /// @param code 错误码
  private void stubError(int status, String code) {
    REMOTE.stubFor(
        post(urlEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":false,\"code\":\"" + code + "\",\"error\":\"给人看的说明\"}")));
  }

  /// 成功响应。
  ///
  /// @param siteKey 响应里的 site_key
  /// @param action 响应里的 action
  /// @param origin 响应里的 origin
  /// @return 响应体
  private static String successBody(String siteKey, String action, String origin) {
    return "{\"success\":true,\"site_key\":\""
        + siteKey
        + "\",\"action\":\""
        + action
        + "\",\"origin\":\""
        + origin
        + "\",\"risk\":{\"decision\":\"allow\",\"score\":0}}";
  }
}
