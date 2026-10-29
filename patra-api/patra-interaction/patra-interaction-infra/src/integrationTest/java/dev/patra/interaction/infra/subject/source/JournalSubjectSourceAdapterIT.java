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
import dev.patra.catalog.api.venue.endpoint.VenueEndpoint;
import dev.patra.interaction.domain.subject.exception.SubjectSourceUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.NaturalKeyScheme;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SourceRecord;
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

/// 期刊来源策略的 WireMock 集成测试：真实 HTTP、真实 `ProblemDetailErrorHandler`，验证路径与请求体、字符串形式的
/// ID 读回、NLM ID 原样保留（前导零、`R` 后缀）与失败翻译。客户端照 `PublicationSubjectSourceAdapterIT` 手工建。
@WireMockTest
class JournalSubjectSourceAdapterIT {

  private JournalSubjectSourceAdapter adapter;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMock) {
    adapter = adapterFor(wireMock.getHttpBaseUrl());
  }

  @Test
  void should_post_ids_and_read_string_id_back() {
    stubFor(
        post(urlEqualTo("/_internal/venues/resolve/by-ids"))
            .withRequestBody(equalToJson("{\"ids\":[100]}"))
            .willReturn(
                okJson(
                    """
                    {"items":[{"key":"100","exists":true,"state":"ACTIVE","id":"100",
                      "nlmId":"0404401","title":"Schweizerische medizinische Wochenschrift",
                      "abbreviatedTitle":"Swiss Med Wkly"}]}""")));

    SourceRecord record = adapter.resolveByKey(100L).orElseThrow();

    assertThat(record.sourceId()).isEqualTo(100L);
    assertThat(record.naturalKey()).isEqualTo(NaturalKey.of(NaturalKeyScheme.NLM, "0404401"));
    assertThat(record.snapshot().abbreviation()).isEqualTo("Swiss Med Wkly");
  }

  @Test
  void should_post_nlm_ids_as_strings() {
    stubFor(
        post(urlEqualTo("/_internal/venues/resolve/by-nlm-ids"))
            .withRequestBody(equalToJson("{\"nlmIds\":[\"0404401\",\"2985213R\"]}"))
            .willReturn(
                okJson(
                    """
                    {"items":[{"key":"0404401","exists":true,"state":"ACTIVE","id":"200",
                      "nlmId":"100970884","title":"T","abbreviatedTitle":null},
                     {"key":"2985213R","exists":false,"state":null,"id":null,"nlmId":null,
                      "title":null,"abbreviatedTitle":null}]}""")));

    Map<NaturalKey, SourceRecord> result =
        adapter.resolveByNaturalKeys(
            List.of(
                NaturalKey.of(NaturalKeyScheme.NLM, "0404401"),
                NaturalKey.of(NaturalKeyScheme.NLM, "2985213R")));

    assertThat(result).containsOnlyKeys(NaturalKey.of(NaturalKeyScheme.NLM, "0404401"));
    assertThat(result.values()).extracting(SourceRecord::sourceId).containsExactly(200L);
  }

  @Test
  void should_translate_503_and_refused_connection() throws IOException {
    stubFor(
        post(urlEqualTo("/_internal/venues/resolve/by-ids"))
            .willReturn(
                aResponse()
                    .withStatus(503)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody(
                        """
                        {"type":"about:blank","title":"Service Unavailable","status":503,
                         "code":"CAT-0503","traits":["DEP_UNAVAILABLE"]}""")));
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    JournalSubjectSourceAdapter unreachable = adapterFor("http://localhost:" + closedPort);

    assertThatThrownBy(() -> adapter.resolveByKey(100L))
        .isInstanceOf(SubjectSourceUnavailableException.class);
    assertThatThrownBy(() -> unreachable.resolveByKey(100L))
        .isInstanceOf(SubjectSourceUnavailableException.class);
  }

  /// 容错模式的 ProblemDetail 错误处理器；`JsonMapper` 照 Boot 的 Jackson 自动配置注册 ProblemDetail 的 mixin。
  ///
  /// @param baseUrl catalog 地址
  /// @return 适配器
  private static JournalSubjectSourceAdapter adapterFor(String baseUrl) {
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
    return new JournalSubjectSourceAdapter(proxies.createClient(VenueEndpoint.class));
  }
}
