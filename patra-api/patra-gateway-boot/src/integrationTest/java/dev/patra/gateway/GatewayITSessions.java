package dev.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.security.SecureRandom;
import java.util.Base64;

/// 集成测试里让 WireMock 顶替的 identity 回答查会话（PAP-106）：网关只认 identity 说 active 与否。
///
/// 响应和 identity 的真实输出同形：`userId`、`sessionId` 是字符串（identity 的 MVC 把 Long 写成字符串）。
/// 查会话的桩优先级最高，压过用例自己给 identity 打的 `any(urlPathMatching("/.*"))` 兜底桩；
/// 没登记的令牌落到优先级最低的 inactive 兜底。
final class GatewayITSessions {

  /// 查会话接口在 identity 上的路径。
  static final String INTROSPECT_PATH = "/_internal/sessions/introspect";

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int SESSION_PRIORITY = 1;
  private static final int FALLBACK_PRIORITY = 100;

  /// 工具类，不允许实例化。
  private GatewayITSessions() {}

  /// 让 identity 对任何令牌都回 inactive。用例清空 WireMock 后先调它。
  ///
  /// @param identity WireMock 顶替的 identity
  static void inactiveByDefault(WireMockExtension identity) {
    identity.stubFor(
        post(urlPathEqualTo(INTROSPECT_PATH))
            .atPriority(FALLBACK_PRIORITY)
            .willReturn(okJson("{\"active\":false}")));
  }

  /// 登记一条前台网页端会话，返回令牌原文。
  ///
  /// @param identity WireMock 顶替的 identity
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 令牌，形如 `patra_user_…`
  static String issue(WireMockExtension identity, long userId, long sessionId) {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String token = "patra_user_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    identity.stubFor(
        introspectionOf(token)
            .willReturn(
                okJson(
                    "{\"active\":true,\"userId\":\""
                        + userId
                        + "\",\"sessionId\":\""
                        + sessionId
                        + "\",\"accountType\":\"USER\",\"clientType\":\"WEB\"}")));
    return token;
  }

  /// 让一个令牌从此回 inactive：登出、封禁、踢设备之后的样子。
  ///
  /// @param identity WireMock 顶替的 identity
  /// @param token 令牌原文
  static void revoke(WireMockExtension identity, String token) {
    identity.stubFor(introspectionOf(token).willReturn(okJson("{\"active\":false}")));
  }

  /// 匹配某个令牌的查会话请求。同优先级时后登记的生效，所以 `revoke` 能盖过 `issue`。
  ///
  /// @param token 令牌原文
  /// @return 桩的构建器
  static MappingBuilder introspectionOf(String token) {
    return post(urlPathEqualTo(INTROSPECT_PATH))
        .atPriority(SESSION_PRIORITY)
        .withRequestBody(matchingJsonPath("$.token", equalTo(token)));
  }
}
