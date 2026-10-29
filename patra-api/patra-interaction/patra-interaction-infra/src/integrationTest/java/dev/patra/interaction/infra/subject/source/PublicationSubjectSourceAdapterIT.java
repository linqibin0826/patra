package dev.patra.interaction.infra.subject.source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.patra.catalog.api.publication.endpoint.PublicationEndpoint;
import dev.patra.interaction.domain.subject.exception.SubjectSourceUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.NaturalKeyScheme;
import dev.patra.interaction.domain.subject.model.source.SourceRecord;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.tool.httpclient.autoconfigure.HttpClientProperties;
import dev.patra.tool.httpclient.error.ProblemDetailErrorHandler;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.json.JsonMapper;

/// 文献来源策略的 WireMock 集成测试：真实 HTTP、真实 `ProblemDetailErrorHandler`，验证请求体、
/// 字符串形式的 ID 读回与失败翻译。客户端照 identity 的 `CatalogReferenceAdapterIT` 手工建。
@WireMockTest
class PublicationSubjectSourceAdapterIT {

  private static final String RESOLVED_100 =
      """
      {"items":[{"key":"100","exists":true,"state":"ACTIVE","id":"100","pmid":"1",
        "title":"A <i>title</i>","journal":"The Journal","year":2019,"publishedDate":"2019-03-15"}]}""";

  private PublicationSubjectSourceAdapter adapter;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMock) {
    adapter = adapterFor(wireMock.getHttpBaseUrl());
  }

  @Test
  void should_post_ids_and_read_string_id_back() {
    stubFor(
        post(urlEqualTo("/_internal/publications/resolve/by-ids"))
            .withRequestBody(equalToJson("{\"ids\":[100]}"))
            .willReturn(okJson(RESOLVED_100)));

    SourceRecord record = adapter.resolveByKey(100L).orElseThrow();

    assertThat(record.sourceId()).isEqualTo(100L);
    assertThat(record.naturalKey()).isEqualTo(NaturalKey.of(NaturalKeyScheme.PMID, "1"));
    assertThat(record.snapshot().title()).isEqualTo("A <i>title</i>");
    assertThat(record.snapshot().publishedDate()).isEqualTo("2019-03-15");
  }

  @Test
  void should_post_pmids_without_prefix() {
    stubFor(
        post(urlEqualTo("/_internal/publications/resolve/by-pmids"))
            .withRequestBody(equalToJson("{\"pmids\":[\"1\",\"2\"]}"))
            .willReturn(
                okJson(
                    """
                    {"items":[{"key":"1","exists":true,"state":"ACTIVE","id":"100","pmid":"1",
                      "title":"T","journal":"J","year":2019,"publishedDate":"2019"},
                     {"key":"2","exists":false}]}""")));

    Map<NaturalKey, SourceRecord> result =
        adapter.resolveByNaturalKeys(
            List.of(
                NaturalKey.of(NaturalKeyScheme.PMID, "1"),
                NaturalKey.of(NaturalKeyScheme.PMID, "2")));

    assertThat(result).containsOnlyKeys(NaturalKey.of(NaturalKeyScheme.PMID, "1"));
  }

  @Test
  void should_translate_503_problem_detail() {
    stubFor(
        post(urlEqualTo("/_internal/publications/resolve/by-ids"))
            .willReturn(
                aResponse()
                    .withStatus(503)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody(
                        """
                        {"type":"about:blank","title":"Service Unavailable","status":503,
                         "code":"CAT-0503","traits":["DEP_UNAVAILABLE"]}""")));

    assertThatThrownBy(() -> adapter.resolveByKey(100L))
        .isInstanceOf(SubjectSourceUnavailableException.class);
  }

  @Test
  void should_translate_connection_refused() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    PublicationSubjectSourceAdapter unreachable = adapterFor("http://localhost:" + closedPort);

    assertThatThrownBy(() -> unreachable.resolveByKey(100L))
        .isInstanceOf(SubjectSourceUnavailableException.class);
  }

  /// 容错模式的 ProblemDetail 错误处理器；`JsonMapper` 照 Boot 的 Jackson 自动配置注册 ProblemDetail 的
  /// mixin，否则扩展属性（`code`、`traits`）读不出来。
  ///
  /// @param baseUrl catalog 地址
  /// @return 适配器
  private static PublicationSubjectSourceAdapter adapterFor(String baseUrl) {
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
    return new PublicationSubjectSourceAdapter(proxies.createClient(PublicationEndpoint.class));
  }
}
