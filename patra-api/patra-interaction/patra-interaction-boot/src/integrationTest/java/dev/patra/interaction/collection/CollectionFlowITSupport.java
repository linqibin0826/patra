package dev.patra.interaction.collection;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import dev.patra.tool.security.test.TestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
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

/// 收藏与收藏夹完整流程的底座：真实 PostgreSQL、真实的身份断言验签；catalog 与 identity 由 WireMock 扮演，经
/// Spring Cloud LoadBalancer + SimpleDiscoveryClient 走 `lb://` 这条真实的路（照 `SubjectRegistryFlowIT`）。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
abstract class CollectionFlowITSupport {

  /// JSON 解析。
  protected static final JsonMapper JSON = JsonMapper.builder().build();

  /// 假 catalog。
  protected static final FakeCatalog CATALOG_DATA = new FakeCatalog();

  /// 假 identity。
  protected static final FakeIdentity IDENTITY_DATA = new FakeIdentity();

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).extensions(CATALOG_DATA))
          .build();

  @RegisterExtension
  static final WireMockExtension identity =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig().dynamicPort().http2PlainDisabled(true).extensions(IDENTITY_DATA))
          .build();

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

  /// 每例从干净的表与假数据开始；两个假服务对任何请求都交给转换器。
  @BeforeEach
  void resetWorld() {
    jdbcTemplate.update(
        "TRUNCATE subject.registry, collection.library_item, collection.collection,"
            + " collection.collection_item");
    CATALOG_DATA.clear();
    IDENTITY_DATA.clear();
    catalog.stubFor(any(anyUrl()).willReturn(ok()));
    identity.stubFor(any(anyUrl()).willReturn(ok()));
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

  /// 收藏一篇，回 `itemId`。
  ///
  /// @param user 用户
  /// @param key catalog ID
  /// @return 条目 ID
  protected String collect(long user, long key) {
    return json(call(HttpMethod.PUT, user, "/me/library/publication/" + key, null))
        .get("item")
        .get("itemId")
        .asString();
  }

  /// 建收藏夹，回 `collectionId`。
  ///
  /// @param user 用户
  /// @param name 名称
  /// @return 收藏夹 ID
  protected String createCollection(long user, String name) {
    return json(call(
            HttpMethod.POST,
            user,
            "/me/collections",
            "{\"name\":\"" + name + "\",\"description\":\"每周四\"}"))
        .get("collectionId")
        .asString();
  }

  /// 放进收藏夹。
  ///
  /// @param user 用户
  /// @param collectionId 收藏夹
  /// @param itemIds 条目
  /// @return 响应
  protected JsonNode addToCollection(long user, String collectionId, String... itemIds) {
    String ids = String.join("\",\"", itemIds);
    return json(
        call(
            HttpMethod.POST,
            user,
            "/me/collections/" + collectionId + "/items",
            "{\"itemIds\":[\"" + ids + "\"]}"));
  }

  /// 设为公开。
  ///
  /// @param user 用户
  /// @param collectionId 收藏夹
  /// @return 响应
  protected EntityExchangeResult<String> publish(long user, String collectionId) {
    return call(
        HttpMethod.PUT,
        user,
        "/me/collections/" + collectionId + "/visibility",
        "{\"visibility\":\"PUBLIC\"}");
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
