package dev.patra.identity.infra.account.verification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.patra.identity.domain.account.model.enums.HumanVerificationAction;
import dev.patra.identity.domain.account.model.enums.HumanVerificationResult;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/// TurnstileAdapter 集成测试：用 WireMock 模拟 `siteverify`。
@DisplayName("TurnstileAdapter 集成测试")
class TurnstileAdapterIT {

  private static final String PATH = "/turnstile/v0/siteverify";
  private static final String HOSTNAME = "patra.linqibin.dev";
  private static final String CREDENTIAL = "F".repeat(35);
  private static final String VERIFICATION = "V".repeat(64);

  @RegisterExtension
  static final WireMockExtension REMOTE =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  @Test
  @DisplayName("success 为真且 hostname、action 都对上：通过")
  void passed_when_success_and_hostname_and_action_match() {
    stubBody(successBody(HOSTNAME, "login"));

    assertThat(verify()).isEqualTo(HumanVerificationResult.PASSED);
  }

  @Test
  @DisplayName("hostname 不在允许名单：拒绝")
  void rejected_when_hostname_not_allowed() {
    stubBody(successBody("evil.example.com", "login"));

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("action 对不上：拒绝，防止别的操作拿到的令牌被挪用")
  void rejected_when_action_mismatch() {
    stubBody(successBody(HOSTNAME, "register"));

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("响应缺 hostname：拒绝")
  void rejected_when_hostname_missing() {
    stubBody("{\"success\":true,\"action\":\"login\"}");

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("令牌已用过：拒绝")
  void rejected_on_timeout_or_duplicate() {
    stubBody(failureBody("timeout-or-duplicate"));

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("令牌无效：拒绝")
  void rejected_on_invalid_input_response() {
    stubBody(failureBody("invalid-input-response"));

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("官方测试密钥：跳过 hostname 和 action 核对（响应形状来自 2026-11-05 实测）")
  void passed_with_testing_key_marker_skips_hostname_and_action() {
    stubBody(
        "{\"success\":true,\"hostname\":\"example.com\",\"error-codes\":[],"
            + "\"metadata\":{\"result_with_testing_key\":true}}");

    assertThat(verify()).isEqualTo(HumanVerificationResult.PASSED);
  }

  @Test
  @DisplayName("带测试密钥标记但 success 为假：仍然拒绝")
  void rejected_when_success_false_even_with_testing_key_marker() {
    stubBody(
        "{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"],"
            + "\"metadata\":{\"result_with_testing_key\":true}}");

    assertThat(verify()).isEqualTo(HumanVerificationResult.REJECTED);
  }

  @Test
  @DisplayName("密钥错误（400）：验证不了，不重试")
  void unavailable_on_invalid_input_secret() {
    REMOTE.stubFor(
        post(urlEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody(failureBody("invalid-input-secret"))));

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
    REMOTE.verify(1, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName("internal-error 两次都出现：验证不了")
  void unavailable_on_internal_error() {
    stubBody(failureBody("internal-error"));

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
    REMOTE.verify(2, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName("500 后重试一次并成功，两次用同一个 idempotency_key")
  void retries_once_on_500_with_same_idempotency_key() {
    REMOTE.stubFor(
        post(urlEqualTo(PATH))
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("second"));
    REMOTE.stubFor(
        post(urlEqualTo(PATH))
            .inScenario("retry")
            .whenScenarioStateIs("second")
            .willReturn(okJson(successBody(HOSTNAME, "login"))));

    assertThat(verify()).isEqualTo(HumanVerificationResult.PASSED);

    List<String> keys =
        REMOTE.findAll(postRequestedFor(urlEqualTo(PATH))).stream()
            .map(request -> idempotencyKey(request.getBodyAsString()))
            .toList();
    assertThat(keys).hasSize(2);
    assertThat(keys.get(0)).isEqualTo(keys.get(1));
  }

  @Test
  @DisplayName("两次都 500：验证不了")
  void unavailable_after_second_failure() {
    REMOTE.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
    REMOTE.verify(2, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName("读超时：验证不了")
  void unavailable_on_read_timeout() {
    REMOTE.stubFor(
        post(urlEqualTo(PATH))
            .willReturn(okJson(successBody(HOSTNAME, "login")).withFixedDelay(5_000)));

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
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
    stubBody("{\"success\":\"true\",\"hostname\":\"patra.linqibin.dev\",\"action\":\"login\"}");

    assertThat(verify()).isEqualTo(HumanVerificationResult.UNAVAILABLE);
  }

  @Test
  @DisplayName("密钥没配：验证不了，不发请求")
  void unavailable_when_secret_blank_without_calling_remote() {
    TurnstileAdapter adapter = adapterWith(" ");

    assertThat(adapter.verify(VERIFICATION, HumanVerificationAction.LOGIN))
        .isEqualTo(HumanVerificationResult.UNAVAILABLE);
    REMOTE.verify(0, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName("令牌超长：拒绝，不发请求")
  void rejected_without_calling_remote_when_token_too_long() {
    assertThat(adapterWith(CREDENTIAL).verify("a".repeat(2049), HumanVerificationAction.LOGIN))
        .isEqualTo(HumanVerificationResult.REJECTED);
    REMOTE.verify(0, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName("令牌含空白：拒绝，不发请求")
  void rejected_without_calling_remote_when_token_has_whitespace() {
    assertThat(adapterWith(CREDENTIAL).verify("abc def", HumanVerificationAction.LOGIN))
        .isEqualTo(HumanVerificationResult.REJECTED);
    REMOTE.verify(0, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName("请求只带 secret、response、idempotency_key，不传访客 IP")
  void request_contains_no_remoteip() {
    stubBody(successBody(HOSTNAME, "login"));

    verify();

    REMOTE.verify(
        postRequestedFor(urlEqualTo(PATH))
            .withRequestBody(containing("secret=" + CREDENTIAL))
            .withRequestBody(containing("response=" + VERIFICATION))
            .withRequestBody(containing("idempotency_key="))
            .withRequestBody(notContaining("remoteip")));
  }

  @Test
  @DisplayName("接口地址被刷新成非法值：验证不了，不抛异常")
  void unavailable_when_base_url_invalid() {
    TurnstileAdapter adapter =
        new TurnstileAdapter(
            RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient()))
                .build(),
            JsonMapper.builder().build(),
            () -> TurnstileSettings.of("not a url", CREDENTIAL, Set.of(HOSTNAME)));

    assertThat(adapter.verify(VERIFICATION, HumanVerificationAction.LOGIN))
        .isEqualTo(HumanVerificationResult.UNAVAILABLE);
  }

  @Test
  @DisplayName("provider 是 Turnstile")
  void provider_is_turnstile() {
    assertThat(adapterWith(CREDENTIAL).provider().wireValue()).isEqualTo("turnstile");
  }

  /// 用默认配置校验一次登录令牌。
  ///
  /// @return 结果
  private HumanVerificationResult verify() {
    return adapterWith(CREDENTIAL).verify(VERIFICATION, HumanVerificationAction.LOGIN);
  }

  /// 指向 WireMock 的适配器，读超时 2 秒，高负载下第一个请求也够用。
  ///
  /// @param secret 密钥
  /// @return 适配器
  private TurnstileAdapter adapterWith(String secret) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
    factory.setReadTimeout(Duration.ofSeconds(2));
    return new TurnstileAdapter(
        RestClient.builder().requestFactory(factory).build(),
        JsonMapper.builder().build(),
        () -> TurnstileSettings.of(REMOTE.baseUrl(), secret, Set.of(HOSTNAME)));
  }

  /// 让 `siteverify` 返回 200 和给定的响应体。
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

  /// 成功响应。
  ///
  /// @param hostname 响应里的 hostname
  /// @param action 响应里的 action
  /// @return 响应体
  private static String successBody(String hostname, String action) {
    return "{\"success\":true,\"hostname\":\""
        + hostname
        + "\",\"action\":\""
        + action
        + "\",\"error-codes\":[]}";
  }

  /// 失败响应。
  ///
  /// @param code 错误码
  /// @return 响应体
  private static String failureBody(String code) {
    return "{\"success\":false,\"error-codes\":[\"" + code + "\"]}";
  }

  /// 从表单请求体里取 `idempotency_key`。
  ///
  /// @param body 请求体
  /// @return 值
  private static String idempotencyKey(String body) {
    Matcher matcher = Pattern.compile("idempotency_key=([^&]+)").matcher(body);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }
}
