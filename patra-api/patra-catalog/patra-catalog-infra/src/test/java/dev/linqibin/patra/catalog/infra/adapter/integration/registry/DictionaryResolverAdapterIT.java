package dev.linqibin.patra.catalog.infra.adapter.integration.registry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.patra.starter.httpinterface.config.HttpInterfaceProperties;
import com.patra.starter.httpinterface.error.ProblemDetailErrorHandler;
import dev.linqibin.patra.catalog.domain.model.enums.DictionaryType;
import dev.linqibin.patra.catalog.domain.model.vo.common.SourceStandard;
import dev.linqibin.patra.registry.api.endpoint.DictionaryEndpoint;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/// DictionaryResolverAdapter WireMock 集成测试。
///
/// 使用 WireMock 模拟 Registry 服务，验证 HTTP Interface 迁移后的跨服务调用：
///
/// - HTTP Interface 代理配置正确
/// - ProblemDetail 错误响应解析
/// - ErrorTrait 语义特征传播
/// - 超时和网络错误降级处理
///
/// **与单元测试的区别**：
///
/// - 单元测试（DictionaryResolverAdapterTest）：Mock DictionaryEndpoint，验证转换逻辑
/// - 集成测试（本测试）：真实 HTTP 调用，验证 HTTP Interface + 错误处理器完整工作
///
/// @author linqibin
/// @since 0.1.0
/// @see DictionaryResolverAdapterTest
@WireMockTest
@Timeout(value = 30, unit = TimeUnit.SECONDS)
@DisplayName("DictionaryResolverAdapter WireMock 集成测试")
class DictionaryResolverAdapterIT {

  private static final String DICTIONARY_RESOLVE_PATH = "/_internal/dictionaries/resolve";

  /// 连接超时时间（秒）
  private static final int CONNECT_TIMEOUT_SECONDS = 2;

  /// 读取超时时间（秒）
  private static final int READ_TIMEOUT_SECONDS = 3;

  private DictionaryResolverAdapter adapter;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    // 构建测试用的 ObjectMapper 和错误处理配置
    ObjectMapper objectMapper = JsonMapper.builder().build();
    HttpInterfaceProperties.ErrorHandlingProperties errorProps =
        new HttpInterfaceProperties.ErrorHandlingProperties();
    errorProps.setTolerant(true); // 启用容错模式

    // 显式配置超时的 HttpClient（使用 JDK HttpClient）
    // 注意：必须使用 HTTP/1.1，因为 WireMock 默认不支持 HTTP/2
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS));

    // 手动构建 RestClient（不依赖 Spring 上下文）
    // 使用 statusPredicate 形式注册错误处理器
    ProblemDetailErrorHandler errorHandler =
        new ProblemDetailErrorHandler(objectMapper, errorProps);
    RestClient restClient =
        RestClient.builder()
            .baseUrl(wmRuntimeInfo.getHttpBaseUrl())
            .requestFactory(requestFactory)
            .defaultStatusHandler(status -> status.isError(), errorHandler)
            .build();

    // 创建 HTTP Interface 代理
    HttpServiceProxyFactory proxyFactory =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
    DictionaryEndpoint endpoint = proxyFactory.createClient(DictionaryEndpoint.class);

    // 创建被测试的 Adapter
    adapter = new DictionaryResolverAdapter(endpoint);
  }

  // ==================== 正常响应场景 ====================

  @Nested
  @DisplayName("正常响应场景")
  class SuccessScenarios {

    @Test
    @DisplayName("批量解析国家编码成功 - 返回全部映射")
    void shouldResolveAllCountriesSuccessfully() {
      // Given: WireMock stub
      stubFor(
          post(urlEqualTo(DICTIONARY_RESOLVE_PATH))
              .withHeader("Content-Type", containing("application/json"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                      .withBody(
                          """
                          {
                            "typeCode": "country",
                            "sourceStandard": "NAME_EN",
                            "items": [
                              {"rawValue": "China", "resolvedCode": "CN", "resolvedName": "中国", "status": "RESOLVED"},
                              {"rawValue": "United States", "resolvedCode": "US", "resolvedName": "美国", "status": "RESOLVED"}
                            ]
                          }
                          """)));

      // When
      Map<String, String> result =
          adapter.resolve(
              DictionaryType.COUNTRY, SourceStandard.NAME_EN, Set.of("China", "United States"));

      // Then
      assertThat(result).hasSize(2);
      assertThat(result.get("China")).isEqualTo("CN");
      assertThat(result.get("United States")).isEqualTo("US");

      // 验证请求发送正确
      verify(
          postRequestedFor(urlEqualTo(DICTIONARY_RESOLVE_PATH))
              .withRequestBody(matchingJsonPath("$.typeCode", equalTo("country")))
              .withRequestBody(matchingJsonPath("$.sourceStandard", equalTo("NAME_EN"))));
    }

    @Test
    @DisplayName("部分解析成功 - 仅返回 RESOLVED 状态的映射")
    void shouldReturnOnlyResolvedItems() {
      // Given
      stubFor(
          post(urlEqualTo(DICTIONARY_RESOLVE_PATH))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                      .withBody(
                          """
                          {
                            "typeCode": "country",
                            "sourceStandard": "NAME_EN",
                            "items": [
                              {"rawValue": "China", "resolvedCode": "CN", "resolvedName": "中国", "status": "RESOLVED"},
                              {"rawValue": "Atlantis", "resolvedCode": null, "resolvedName": null, "status": "UNKNOWN"},
                              {"rawValue": "Wakanda", "resolvedCode": null, "resolvedName": null, "status": "UNKNOWN"}
                            ]
                          }
                          """)));

      // When
      Map<String, String> result =
          adapter.resolve(
              DictionaryType.COUNTRY,
              SourceStandard.NAME_EN,
              Set.of("China", "Atlantis", "Wakanda"));

      // Then: 只返回 China 的映射
      assertThat(result).hasSize(1);
      assertThat(result.get("China")).isEqualTo("CN");
      assertThat(result).doesNotContainKey("Atlantis");
      assertThat(result).doesNotContainKey("Wakanda");
    }
  }

  // ==================== ProblemDetail 错误场景 ====================

  @Nested
  @DisplayName("ProblemDetail 错误场景")
  class ProblemDetailErrorScenarios {

    @Test
    @DisplayName("404 ProblemDetail - 降级返回空 Map")
    void shouldReturnEmptyMapOn404ProblemDetail() {
      // Given
      stubFor(
          post(urlEqualTo(DICTIONARY_RESOLVE_PATH))
              .willReturn(
                  aResponse()
                      .withStatus(404)
                      .withHeader("Content-Type", "application/problem+json")
                      .withBody(
                          """
                          {
                            "type": "about:blank",
                            "title": "Not Found",
                            "status": 404,
                            "detail": "字典类型未找到",
                            "code": "REG-0404",
                            "traits": ["NOT_FOUND"]
                          }
                          """)));

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, Set.of("China"));

      // Then: 降级返回空 Map
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("500 ProblemDetail + DEP_UNAVAILABLE Trait - 降级返回空 Map")
    void shouldReturnEmptyMapOn500WithDepUnavailableTrait() {
      // Given
      stubFor(
          post(urlEqualTo(DICTIONARY_RESOLVE_PATH))
              .willReturn(
                  aResponse()
                      .withStatus(500)
                      .withHeader("Content-Type", "application/problem+json")
                      .withBody(
                          """
                          {
                            "type": "about:blank",
                            "title": "Internal Server Error",
                            "status": 500,
                            "detail": "数据库连接失败",
                            "code": "REG-0500",
                            "traits": ["DEP_UNAVAILABLE"]
                          }
                          """)));

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, Set.of("China"));

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("422 ProblemDetail + 业务错误码 - 降级返回空 Map")
    void shouldReturnEmptyMapOn422WithBusinessErrorCode() {
      // Given
      stubFor(
          post(urlEqualTo(DICTIONARY_RESOLVE_PATH))
              .willReturn(
                  aResponse()
                      .withStatus(422)
                      .withHeader("Content-Type", "application/problem+json")
                      .withBody(
                          """
                          {
                            "type": "about:blank",
                            "title": "Unprocessable Entity",
                            "status": 422,
                            "detail": "请求参数无效",
                            "code": "REG-0422"
                          }
                          """)));

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, Set.of("China"));

      // Then
      assertThat(result).isEmpty();
    }
  }

  // ==================== 超时和网络故障场景 ====================

  @Nested
  @DisplayName("超时和网络故障场景")
  class TimeoutAndFaultScenarios {

    @Test
    @DisplayName("读取超时 - 降级返回空 Map")
    void shouldReturnEmptyMapOnReadTimeout() {
      // Given: 模拟超时（超过 READ_TIMEOUT_SECONDS 读取超时）
      stubFor(
          post(urlEqualTo(DICTIONARY_RESOLVE_PATH))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withFixedDelay(4000) // 4秒，超过 3s 读取超时
                      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                      .withBody("{}")));

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, Set.of("China"));

      // Then: 超时异常被捕获，降级返回空 Map
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("连接重置 - 降级返回空 Map")
    void shouldReturnEmptyMapOnConnectionReset() {
      // Given: 模拟连接被重置（如服务器意外关闭连接）
      stubFor(
          post(urlEqualTo(DICTIONARY_RESOLVE_PATH))
              .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, Set.of("China"));

      // Then: 连接重置异常被捕获，降级返回空 Map
      assertThat(result).isEmpty();
    }
  }

  // ==================== 非 ProblemDetail 错误场景 ====================

  @Nested
  @DisplayName("非 ProblemDetail 错误场景（容错模式）")
  class NonProblemDetailErrorScenarios {

    @Test
    @DisplayName("503 普通 JSON 错误响应 - 容错模式降级返回空 Map")
    void shouldReturnEmptyMapOnPlainJsonError() {
      // Given: 普通 JSON 错误响应（非 ProblemDetail 格式）
      stubFor(
          post(urlEqualTo(DICTIONARY_RESOLVE_PATH))
              .willReturn(
                  aResponse()
                      .withStatus(503)
                      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                      .withBody("{\"error\": \"Service Unavailable\"}")));

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, Set.of("China"));

      // Then: 容错模式下仍然降级返回空 Map
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("500 HTML 错误页面 - 容错模式降级返回空 Map")
    void shouldReturnEmptyMapOnHtmlErrorPage() {
      // Given: HTML 错误页面（如 Nginx 默认错误页）
      stubFor(
          post(urlEqualTo(DICTIONARY_RESOLVE_PATH))
              .willReturn(
                  aResponse()
                      .withStatus(502)
                      .withHeader("Content-Type", "text/html")
                      .withBody("<html><body>Bad Gateway</body></html>")));

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, Set.of("China"));

      // Then
      assertThat(result).isEmpty();
    }
  }
}
