package dev.patra.identity.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/// 用 WireMock 扮演两家人机验证厂商，每种操作一个固定令牌，响应里的 `hostname`、`action` 都按真实形状返回，
/// 所以流程测试也走完整的核对。
///
/// 服务器整个 JVM 只有一个：Spring 的上下文缓存记着它的端口。地址经 spring.factories 注册的初始化器写入，
/// 所有起应用的集成测试都用得上。
public final class HumanVerificationFixture {

  /// 登录用的令牌。
  public static final String LOGIN = "flow-verification-login";

  /// 注册用的令牌。
  public static final String REGISTER = "flow-verification-register";

  /// 找回密码用的令牌。
  public static final String PASSWORD_RESET = "flow-verification-password_reset";

  /// 校验不通过的令牌。
  public static final String REJECTED = "flow-verification-rejected";

  private static final String TURNSTILE_PATH = "/turnstile/v0/siteverify";
  private static final String TRUST_TURN_PATH = "/api/tickets/consume";
  private static final String HOSTNAME = "patra.test";
  private static final String ORIGIN = "https://patra.test";
  private static final String TRUST_TURN_SITE_KEY = "test-trustturn-site";

  private static final Map<String, String> TOKEN_BY_URI =
      Map.of(
          "/auth/login", LOGIN,
          "/auth/register", REGISTER,
          "/auth/password/forgot", PASSWORD_RESET);

  private static final WireMockServer SERVER =
      new WireMockServer(wireMockConfig().dynamicPort().http2PlainDisabled(true));

  static {
    SERVER.start();
    reset();
  }

  /// 只有静态方法。
  private HumanVerificationFixture() {}

  /// 恢复默认的桩：三个操作令牌通过，其他令牌不通过。
  public static void reset() {
    SERVER.resetAll();
    SERVER.stubFor(
        post(urlEqualTo(TURNSTILE_PATH))
            .atPriority(10)
            .willReturn(
                okJson("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}")));
    SERVER.stubFor(
        post(urlEqualTo(TRUST_TURN_PATH))
            .atPriority(10)
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":false,\"error\":\"invalid_ticket\"}")));
    for (Map.Entry<String, String> action :
        Map.of(LOGIN, "login", REGISTER, "register", PASSWORD_RESET, "password_reset").entrySet()) {
      SERVER.stubFor(
          post(urlEqualTo(TURNSTILE_PATH))
              .atPriority(1)
              .withRequestBody(containing("response=" + action.getKey()))
              .willReturn(
                  okJson(
                      "{\"success\":true,\"hostname\":\""
                          + HOSTNAME
                          + "\",\"action\":\""
                          + action.getValue()
                          + "\",\"error-codes\":[]}")));
      SERVER.stubFor(
          post(urlEqualTo(TRUST_TURN_PATH))
              .atPriority(1)
              .withRequestBody(containing("\"ticket\":\"" + action.getKey() + "\""))
              .willReturn(
                  okJson(
                      "{\"success\":true,\"site_key\":\""
                          + TRUST_TURN_SITE_KEY
                          + "\",\"action\":\""
                          + action.getValue()
                          + "\",\"origin\":\""
                          + ORIGIN
                          + "\"}")));
    }
  }

  /// 让两家的校验接口都返回 500。
  public static void stubUnavailable() {
    for (String path : List.of(TURNSTILE_PATH, TRUST_TURN_PATH)) {
      SERVER.stubFor(post(urlEqualTo(path)).atPriority(0).willReturn(aResponse().withStatus(500)));
    }
  }

  /// Turnstile 校验接口收到的请求数。
  ///
  /// @return 请求数
  public static int turnstileCalls() {
    return SERVER.findAll(postRequestedFor(urlEqualTo(TURNSTILE_PATH))).size();
  }

  /// TRUSTTURN 校验接口收到的请求数。
  ///
  /// @return 请求数
  public static int trustTurnCalls() {
    return SERVER.findAll(postRequestedFor(urlEqualTo(TRUST_TURN_PATH))).size();
  }

  /// 给请求体补上这个接口对应的令牌；不是这三个接口、或已经带了令牌的原样返回。
  ///
  /// @param uri 路径
  /// @param body 请求体
  /// @return 请求体
  public static Map<String, Object> withToken(String uri, Map<String, ?> body) {
    if (body == null) {
      return null;
    }
    Map<String, Object> copy = new HashMap<>(body);
    String token = TOKEN_BY_URI.get(uri);
    if (token != null) {
      copy.putIfAbsent("verificationToken", token);
    }
    return copy;
  }

  /// 把两家的地址指到 WireMock。
  public static final class Initializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /// 写入属性。
    ///
    /// @param context 应用上下文
    @Override
    public void initialize(ConfigurableApplicationContext context) {
      String prefix = "patra.identity.human-verification.";
      TestPropertyValues.of(
              Map.of(
                  prefix + "turnstile.base-url", SERVER.baseUrl(),
                  prefix + "trust-turn.base-url", SERVER.baseUrl()))
          .applyTo(context.getEnvironment());
    }
  }
}
