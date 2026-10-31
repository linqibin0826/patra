package dev.patra.interaction.support;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.extension.Extension;
import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import dev.patra.tool.security.test.TestIdentity;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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

/// 各子域流程测试的共用底座：真实 PostgreSQL、真实 RabbitMQ、真实的身份断言验签；catalog 与 identity 由 WireMock 扮演，
/// 经 Spring Cloud LoadBalancer + SimpleDiscoveryClient 走 `lb://` 这条真实的路（照 `SubjectRegistryFlowIT`）。
///
/// 子类共用同一个 Spring 上下文。每例前清空全部子域 schema 里的表（从 `pg_tables` 现查，新子域的表自动纳入）、事件发布
/// 登记表（在 public）与替通知服务绑定路由键的测试队列（PAP-141）。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(
    initializers = {
      InteractionITPostgreSQLContainerInitializer.class,
      RabbitMQContainerInitializer.class
    })
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@Import({DiscussionEventRecorder.class, InteractionEventsITSpringConfig.class})
public abstract class InteractionFlowITSupport {

  /// JSON 解析。
  protected static final JsonMapper JSON = JsonMapper.builder().build();

  /// 假 catalog。
  protected static final FakeCatalog CATALOG_DATA = new FakeCatalog();

  /// 假 identity。
  protected static final FakeIdentity IDENTITY_DATA = new FakeIdentity();

  /// 假 catalog 的 WireMock：每个测试 JVM 只启动一次，端口在整个 JVM 里不变。JUnit 扩展会在每个测试类前换一个新端口，
  /// 而流程测试共用的 Spring 上下文在第一个类启动时就记下了下游地址，同一 JVM 里跑到后面的类会连不上。
  protected static final WireMockServer catalog = start(CATALOG_DATA);

  /// 假 identity 的 WireMock，同上。
  protected static final WireMockServer identity = start(IDENTITY_DATA);

  @DynamicPropertySource
  static void downstreamInstances(DynamicPropertyRegistry registry) {
    registry.add("spring.cloud.discovery.enabled", () -> "true");
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri", catalog::baseUrl);
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-identity[0].uri", identity::baseUrl);
  }

  @Autowired protected RestTestClient restClient;
  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected AmqpAdmin amqpAdmin;

  /// 每例从干净的表与假数据开始；两个假服务对任何请求都交给转换器。
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
    jdbcTemplate.update("TRUNCATE public.event_publication");
    amqpAdmin.purgeQueue(InteractionEventsITSpringConfig.QUEUE, false);
    catalog.resetAll();
    identity.resetAll();
    CATALOG_DATA.clear();
    IDENTITY_DATA.clear();
    DiscussionEventRecorder.EVENTS.clear();
    DiscussionEventRecorder.failOnDelete = false;
    catalog.stubFor(any(anyUrl()).willReturn(ok()));
    identity.stubFor(any(anyUrl()).willReturn(ok()));
  }

  /// 启动一个 WireMock（JVM 退出时随之结束）。
  ///
  /// @param transformer 对任何请求都交给它回应的扩展
  /// @return 已启动的服务
  private static WireMockServer start(Extension transformer) {
    WireMockServer server =
        new WireMockServer(
            wireMockConfig().dynamicPort().http2PlainDisabled(true).extensions(transformer));
    server.start();
    return server;
  }

  /// 以某个用户（或匿名，`null`）发请求。
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

  /// 表行数。
  ///
  /// @param sql 计数 SQL
  /// @param args 参数
  /// @return 行数
  protected long count(String sql, Object... args) {
    return jdbcTemplate.queryForObject(sql, Long.class, args);
  }
}
