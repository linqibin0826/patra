package dev.patra.identity.infra.account.catalog;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.patra.catalog.api.mesh.endpoint.MeshEndpoint;
import dev.patra.catalog.api.organization.endpoint.OrganizationEndpoint;
import dev.patra.identity.domain.account.exception.CatalogUnavailableException;
import dev.patra.identity.domain.account.model.vo.profile.MeshUi;
import dev.patra.identity.domain.account.model.vo.profile.RorId;
import dev.patra.tool.httpclient.autoconfigure.HttpClientProperties;
import dev.patra.tool.httpclient.error.ProblemDetailErrorHandler;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.json.JsonMapper;

/// CatalogReferenceAdapter 的 WireMock 集成测试：真实 HTTP、真实 `ProblemDetailErrorHandler`，验证请求路径、
/// 快照映射和三种失败的翻译。客户端照 catalog 的 `DictionaryResolverAdapterIT` 手工建（不起 Spring）。
@WireMockTest
class CatalogReferenceAdapterIT {

  private static final RorId PUMCH = RorId.of("04jztag35");
  private static final MeshUi DIABETES = MeshUi.of("D003920");

  private static final String NOT_FOUND_BODY =
      """
      {"type":"about:blank","title":"Not Found","status":404,"detail":"机构不存在",
       "code":"CAT-0404","traits":["NOT_FOUND"]}""";

  private CatalogReferenceAdapter adapter;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMock) {
    adapter = adapterFor(wireMock.getHttpBaseUrl());
  }

  @Test
  void should_map_organization_snapshot_from_zh_name() {
    stubFor(
        get(urlEqualTo("/_internal/organizations/04jztag35"))
            .willReturn(
                okJson(
                    """
                    {"rorId":"04jztag35","displayName":"Peking Union Medical College Hospital",
                     "zhName":"北京协和医院","countryCode":"CN","types":["healthcare"]}""")));

    assertThat(adapter.organizationName(PUMCH)).contains("北京协和医院");
    verify(getRequestedFor(urlEqualTo("/_internal/organizations/04jztag35")));
  }

  @Test
  void should_fall_back_to_display_name_when_zh_name_null() {
    stubFor(
        get(urlEqualTo("/_internal/organizations/04jztag35"))
            .willReturn(
                okJson(
                    """
                    {"rorId":"04jztag35","displayName":"Peking Union Medical College Hospital",
                     "zhName":null,"countryCode":"CN","types":[]}""")));

    assertThat(adapter.organizationName(PUMCH)).contains("Peking Union Medical College Hospital");
  }

  @Test
  void should_map_mesh_name() {
    stubFor(
        get(urlEqualTo("/_internal/mesh/descriptors/D003920"))
            .willReturn(
                okJson(
                    """
                    {"ui":"D003920","name":"Diabetes Mellitus","treeNumbers":["C18.452.394.750"]}""")));

    assertThat(adapter.meshName(DIABETES)).contains("Diabetes Mellitus");
    verify(getRequestedFor(urlEqualTo("/_internal/mesh/descriptors/D003920")));
  }

  @Test
  void should_return_empty_on_problem_detail_not_found() {
    stubFor(
        get(urlEqualTo("/_internal/organizations/04jztag35"))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody(NOT_FOUND_BODY)));

    assertThat(adapter.organizationName(PUMCH)).isEmpty();
  }

  @Test
  void should_be_unavailable_on_server_error() {
    stubFor(
        get(urlEqualTo("/_internal/mesh/descriptors/D003920"))
            .willReturn(
                aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody(
                        """
                        {"type":"about:blank","title":"Internal Server Error","status":500,
                         "code":"CAT-0500","traits":["INTERNAL"]}""")));

    assertThatThrownBy(() -> adapter.meshName(DIABETES))
        .isInstanceOf(CatalogUnavailableException.class);
  }

  @Test
  void should_be_unavailable_on_read_timeout() {
    stubFor(
        get(urlEqualTo("/_internal/mesh/descriptors/D003920"))
            .willReturn(okJson("{\"ui\":\"D003920\",\"name\":\"x\"}").withFixedDelay(6000)));

    assertThatThrownBy(() -> adapter.meshName(DIABETES))
        .isInstanceOf(CatalogUnavailableException.class);
  }

  @Test
  void should_be_unavailable_when_nothing_listens() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    CatalogReferenceAdapter unreachable = adapterFor("http://localhost:" + closedPort);

    assertThatThrownBy(() -> unreachable.organizationName(PUMCH))
        .isInstanceOf(CatalogUnavailableException.class);
  }

  /// 指向给定地址的适配器：JDK HttpClient（HTTP/1.1，WireMock 默认的 h2c 会藏掉 Host）、
  /// 读超时 5 秒（CI 负载下请求偶发超过 1 秒）、
  /// 容错模式的 ProblemDetail 错误处理器；`JsonMapper` 照 Boot 的 Jackson 自动配置注册 ProblemDetail 的 mixin，
  /// 否则扩展属性（`code`、`traits`）读不出来。
  ///
  /// @param baseUrl catalog 地址
  /// @return 适配器
  private static CatalogReferenceAdapter adapterFor(String baseUrl) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1))
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(5));
    ProblemDetailErrorHandler errorHandler =
        new ProblemDetailErrorHandler(
            JsonMapper.builder()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build(),
            new HttpClientProperties.HttpInterface.ErrorHandling(true, true, 65536));
    RestClient restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .defaultStatusHandler(status -> status.isError(), errorHandler)
            .build();
    HttpServiceProxyFactory proxies =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
    return new CatalogReferenceAdapter(
        proxies.createClient(OrganizationEndpoint.class), proxies.createClient(MeshEndpoint.class));
  }
}
