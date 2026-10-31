package dev.patra.notification.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.patra.notification.infra.config.NotificationITPostgreSQLContainerInitializer;
import dev.patra.tool.security.test.TestIdentity;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 各子域流程测试的共用底座：真实 PostgreSQL 与 RabbitMQ（信箱的监听在这里打开）、真实的身份断言验签；identity 与
/// interaction 由 WireMock 扮演，
/// 经 Spring Cloud LoadBalancer + SimpleDiscoveryClient 走 `lb://` 这条真实的路。
/// 写法同 interaction 的 `InteractionFlowITSupport`。
///
/// 子类共用同一个 Spring 上下文。每例前清空全部子域 schema 里的表（从 `pg_tables` 现查，新子域的表自动纳入）。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(
    initializers = {
      NotificationITPostgreSQLContainerInitializer.class,
      RabbitMQContainerInitializer.class
    })
@ActiveProfiles("test")
@AutoConfigureRestTestClient
public abstract class NotificationFlowITSupport {

  /// JSON 解析。
  protected static final JsonMapper JSON = JsonMapper.builder().build();

  /// 假 identity 的 WireMock：每个测试 JVM 只启动一次，端口在整个 JVM 里不变（理由同 interaction）。
  protected static final WireMockServer identity = start();

  /// 假 interaction 的 WireMock（提醒列表读时现查对象视图与经验结果）。
  protected static final WireMockServer interaction = start();

  @DynamicPropertySource
  static void downstreamInstances(DynamicPropertyRegistry registry) {
    registry.add("spring.cloud.discovery.enabled", () -> "true");
    registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "true");
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-identity[0].uri", identity::baseUrl);
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-interaction[0].uri",
        interaction::baseUrl);
  }

  @Autowired protected RestTestClient restClient;
  @Autowired protected JdbcTemplate jdbcTemplate;

  /// 每例从干净的表与空的假 identity、interaction 开始：没打桩的请求回 404。
  @BeforeEach
  void resetWorld() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT quote_ident(schemaname) || '.' || quote_ident(tablename) FROM pg_tables"
                + " WHERE schemaname NOT IN ('public', 'pg_catalog', 'information_schema')",
            String.class);
    if (!tables.isEmpty()) {
      jdbcTemplate.update("TRUNCATE " + String.join(", ", tables));
    }
    identity.resetAll();
    interaction.resetAll();
  }

  /// identity 对这个人的收件信息查询回这样一条。
  ///
  /// @param userId 用户 ID
  /// @param email 邮箱，已注销时为 `null`
  /// @param status 账号状态
  protected static void stubRecipient(long userId, String email, String status) {
    String emailJson = email == null ? "null" : "\"" + email + "\"";
    identity.stubFor(
        post(urlPathEqualTo("/_internal/users/recipients"))
            .withRequestBody(equalToJson("{\"userIds\":[" + userId + "]}"))
            .willReturn(
                okJson(
                    "{\"recipients\":[{\"userId\":\""
                        + userId
                        + "\",\"email\":"
                        + emailJson
                        + ",\"emailVerified\":"
                        + (email != null)
                        + ",\"accountStatus\":\""
                        + status
                        + "\",\"username\":null,\"displayName\":null}]}")));
  }

  /// identity 的批量用户摘要对任何请求都回这些人（JSON 数组的元素，写法同 `UserSummaryResp`）。
  ///
  /// @param usersJson 摘要，逗号分隔的 JSON 对象
  protected static void stubSummaries(String usersJson) {
    identity.stubFor(
        post(urlPathEqualTo("/_internal/users/summaries"))
            .willReturn(okJson("{\"users\":[" + usersJson + "]}")));
  }

  /// interaction 的对象视图接口对任何请求都回这些视图（写法同 `SubjectViewResp`）。
  ///
  /// @param subjectsJson 视图，逗号分隔的 JSON 对象
  protected static void stubSubjectViews(String subjectsJson) {
    interaction.stubFor(
        post(urlPathEqualTo("/_internal/subjects/views"))
            .willReturn(okJson("{\"subjects\":[" + subjectsJson + "]}")));
  }

  /// interaction 的经验结果接口对任何请求都回这些结果（写法同 `ExperienceOutlineResp`）。
  ///
  /// @param outlinesJson 结果，逗号分隔的 JSON 对象
  protected static void stubPostOutlines(String outlinesJson) {
    interaction.stubFor(
        post(urlPathEqualTo("/_internal/posts/outlines"))
            .willReturn(okJson("{\"outlines\":[" + outlinesJson + "]}")));
  }

  /// interaction 对任何请求都回 503。
  protected static void interactionDown() {
    interaction.stubFor(
        any(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(503)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                    .withBody("{\"code\":\"INTERACTION-0503\",\"traits\":[\"DEP_UNAVAILABLE\"]}")));
  }

  /// identity 对任何请求都回 503。
  protected static void identityDown() {
    identity.stubFor(
        any(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(503)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                    .withBody("{\"code\":\"IDN-0503\",\"traits\":[\"DEP_UNAVAILABLE\"]}")));
  }

  /// 以某个用户（或匿名，`null`）发 JSON 请求。
  ///
  /// @param method 方法
  /// @param user 用户 ID，匿名为 `null`
  /// @param uri 路径
  /// @param body 请求体（JSON 字符串），可空
  /// @return 状态与响应体
  protected EntityExchangeResult<String> call(
      HttpMethod method, Long user, String uri, String body) {
    HttpHeaders headers = user == null ? new HttpHeaders() : TestIdentity.headers(user);
    RestTestClient.RequestBodySpec spec =
        restClient.method(method).uri(uri).headers(h -> h.addAll(headers));
    if (body != null) {
      spec.contentType(MediaType.APPLICATION_JSON).body(body);
    }
    return spec.exchange().expectBody(String.class).returnResult();
  }

  /// 匿名发一个表单 POST（邮箱服务商的一键退订就是这样发的）。
  ///
  /// @param uri 路径
  /// @param body 表单体，可空（不带请求体）
  /// @return 状态与响应体
  protected EntityExchangeResult<String> form(String uri, String body) {
    RestTestClient.RequestBodySpec spec = restClient.post().uri(uri);
    if (body != null) {
      spec.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body);
    }
    return spec.exchange().expectBody(String.class).returnResult();
  }

  /// 状态码。
  ///
  /// @param result 响应
  /// @return 状态码
  protected static int status(EntityExchangeResult<String> result) {
    return result.getStatus().value();
  }

  /// 解析响应体。
  ///
  /// @param result 响应
  /// @return JSON
  protected static JsonNode json(EntityExchangeResult<String> result) {
    return JSON.readTree(result.getResponseBody());
  }

  /// 启动一个 WireMock（JVM 退出时随之结束）。
  ///
  /// @return 已启动的服务
  private static WireMockServer start() {
    WireMockServer server =
        new WireMockServer(wireMockConfig().dynamicPort().http2PlainDisabled(true));
    server.start();
    return server;
  }
}
