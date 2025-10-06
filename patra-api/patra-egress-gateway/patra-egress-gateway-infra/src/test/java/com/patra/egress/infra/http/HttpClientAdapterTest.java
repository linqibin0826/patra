package com.patra.egress.infra.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.patra.egress.domain.model.vo.HttpMethod;
import com.patra.egress.domain.model.vo.HttpRequest;
import com.patra.egress.domain.model.vo.HttpResponse;
import com.patra.egress.domain.model.vo.ResilienceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for HttpClientAdapter using WireMock
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("HttpClientAdapter 集成测试")
class HttpClientAdapterTest {

    private WireMockServer wireMockServer;
    private HttpClientAdapter httpClientAdapter;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        // Start WireMock server
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        baseUrl = "http://localhost:" + wireMockServer.port();

        // Create RestClient and HttpClientAdapter
        RestClient restClient = RestClient.builder().build();
        httpClientAdapter = new HttpClientAdapter(restClient);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("应该成功执行 GET 请求")
    void shouldSuccessfullyExecuteGetRequest() {
        // Given: Mock GET endpoint
        wireMockServer.stubFor(get(urlEqualTo("/api/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"success\"}")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/test",
                HttpMethod.GET,
                Map.of("User-Agent", "Test-Client"),
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify response
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.body()).isEqualTo("{\"message\":\"success\"}");
        // HTTP headers are case-insensitive; modern HTTP clients may return lowercase keys
        assertThat(response.headers()).containsKey("content-type");
    }

    @Test
    @DisplayName("应该成功执行 POST 请求（带 Body）")
    void shouldSuccessfullyExecutePostRequestWithBody() {
        // Given: Mock POST endpoint
        String requestBody = "{\"name\":\"test\"}";
        String responseBody = "{\"id\":123,\"name\":\"test\"}";

        wireMockServer.stubFor(post(urlEqualTo("/api/create"))
                .withRequestBody(equalToJson(requestBody))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/create",
                HttpMethod.POST,
                Map.of("Content-Type", "application/json"),
                requestBody
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify response
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.body()).isEqualTo(responseBody);
    }

    @Test
    @DisplayName("应该正确处理 4xx 错误响应")
    void shouldHandleClientErrorResponse() {
        // Given: Mock 404 response
        wireMockServer.stubFor(get(urlEqualTo("/api/notfound"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Not Found")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/notfound",
                HttpMethod.GET,
                null,
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify response
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.body()).isEqualTo("Not Found");
    }

    @Test
    @DisplayName("应该正确处理 5xx 服务器错误")
    void shouldHandleServerErrorResponse() {
        // Given: Mock 500 response
        wireMockServer.stubFor(get(urlEqualTo("/api/error"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/error",
                HttpMethod.GET,
                null,
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify response
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("应该正确返回响应头")
    void shouldReturnResponseHeaders() {
        // Given: Mock response with custom headers
        wireMockServer.stubFor(get(urlEqualTo("/api/headers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Custom-Header", "custom-value")
                        .withHeader("X-RateLimit-Remaining", "100")
                        .withBody("OK")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/headers",
                HttpMethod.GET,
                null,
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify headers (using lowercase keys as HTTP/2 normalizes to lowercase)
        assertThat(response.headers()).containsKey("x-custom-header");
        assertThat(response.headers().get("x-custom-header")).containsExactly("custom-value");
        assertThat(response.headers()).containsKey("x-ratelimit-remaining");
        assertThat(response.headers().get("x-ratelimit-remaining")).containsExactly("100");
    }

    @Test
    @DisplayName("应该支持 PUT 请求")
    void shouldSupportPutRequest() {
        // Given: Mock PUT endpoint
        wireMockServer.stubFor(put(urlEqualTo("/api/update/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Updated")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/update/123",
                HttpMethod.PUT,
                Map.of("Content-Type", "application/json"),
                "{\"status\":\"active\"}"
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify response
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Updated");
    }

    @Test
    @DisplayName("应该支持 DELETE 请求")
    void shouldSupportDeleteRequest() {
        // Given: Mock DELETE endpoint
        wireMockServer.stubFor(delete(urlEqualTo("/api/delete/123"))
                .willReturn(aResponse()
                        .withStatus(204)));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/delete/123",
                HttpMethod.DELETE,
                null,
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify response
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.isSuccess()).isTrue();
    }

    /**
     * Create default ResilienceConfig for testing
     */
    private ResilienceConfig createDefaultConfig() {
        return new ResilienceConfig(
                Duration.ofSeconds(30),      // timeout
                3,                            // maxRetries
                Duration.ofSeconds(1),        // retryBackoff
                100,                          // rateLimit
                10,                           // circuitBreakerThreshold
                Duration.ofSeconds(30),       // circuitBreakerWindow
                List.of("Content-Type", "X-RateLimit-Remaining")  // responseHeaderWhitelist
        );
    }
}
