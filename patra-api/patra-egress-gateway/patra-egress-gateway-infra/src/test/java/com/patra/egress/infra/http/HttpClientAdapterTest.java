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
@DisplayName("HttpClientAdapter integration tests")
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
    @DisplayName("Should execute GET requests successfully")
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
    @DisplayName("Should execute POST requests with a body")
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
    @DisplayName("Should handle 4xx error responses")
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
    @DisplayName("Should handle 5xx server errors")
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
    @DisplayName("Should return response headers correctly")
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
    @DisplayName("Should support PUT requests")
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
    @DisplayName("Should support DELETE requests")
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

    @Test
    @DisplayName("Should support PATCH requests")
    void shouldSupportPatchRequest() {
        // Given: Mock PATCH endpoint
        String patchBody = "{\"status\":\"inactive\"}";
        wireMockServer.stubFor(patch(urlEqualTo("/api/patch/123"))
                .withRequestBody(equalToJson(patchBody))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"id\":123,\"status\":\"inactive\"}")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/patch/123",
                HttpMethod.PATCH,
                Map.of("Content-Type", "application/json"),
                patchBody
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify response
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("inactive");
    }

    @Test
    @DisplayName("Should support HEAD requests")
    void shouldSupportHeadRequest() {
        // Given: Mock HEAD endpoint
        wireMockServer.stubFor(head(urlEqualTo("/api/resource"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Length", "1024")
                        .withHeader("Last-Modified", "Mon, 01 Jan 2024 00:00:00 GMT")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/resource",
                HttpMethod.HEAD,
                null,
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify response (HEAD should have no body)
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.headers()).containsKey("content-length");
        assertThat(response.headers()).containsKey("last-modified");
    }

    @Test
    @DisplayName("Should support OPTIONS requests")
    void shouldSupportOptionsRequest() {
        // Given: Mock OPTIONS endpoint
        wireMockServer.stubFor(options(urlEqualTo("/api/resource"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Allow", "GET, POST, PUT, DELETE")
                        .withHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/resource",
                HttpMethod.OPTIONS,
                null,
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify response
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.headers()).containsKey("allow");
    }

    @Test
    @DisplayName("Should handle 429 rate limit responses")
    void shouldHandleRateLimitResponse() {
        // Given: Mock 429 rate limit response
        wireMockServer.stubFor(get(urlEqualTo("/api/ratelimited"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "60")
                        .withHeader("X-RateLimit-Limit", "100")
                        .withHeader("X-RateLimit-Remaining", "0")
                        .withHeader("X-RateLimit-Reset", "1696550400")
                        .withBody("{\"error\":\"Rate limit exceeded\"}")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/ratelimited",
                HttpMethod.GET,
                null,
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify response and rate limit headers
        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.headers()).containsKey("retry-after");
        assertThat(response.headers().get("retry-after")).containsExactly("60");
        assertThat(response.headers()).containsKey("x-ratelimit-limit");
        assertThat(response.headers().get("x-ratelimit-limit")).containsExactly("100");
        assertThat(response.headers()).containsKey("x-ratelimit-remaining");
        assertThat(response.headers().get("x-ratelimit-remaining")).containsExactly("0");
    }

    @Test
    @DisplayName("Should propagate custom request headers")
    void shouldPassCustomRequestHeaders() {
        // Given: Mock endpoint that expects specific headers
        wireMockServer.stubFor(get(urlEqualTo("/api/secured"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withHeader("X-Custom-Header", equalTo("custom-value"))
                .withHeader("User-Agent", equalTo("Test-Client/1.0"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Authorized")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/secured",
                HttpMethod.GET,
                Map.of(
                        "Authorization", "Bearer test-token",
                        "X-Custom-Header", "custom-value",
                        "User-Agent", "Test-Client/1.0"
                ),
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify request was accepted (headers matched)
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Authorized");

        // Verify WireMock received the headers
        wireMockServer.verify(getRequestedFor(urlEqualTo("/api/secured"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withHeader("X-Custom-Header", equalTo("custom-value")));
    }

    @Test
    @DisplayName("Should handle empty response bodies")
    void shouldHandleEmptyResponseBody() {
        // Given: Mock endpoint with empty body
        wireMockServer.stubFor(get(urlEqualTo("/api/empty"))
                .willReturn(aResponse()
                        .withStatus(200)));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/empty",
                HttpMethod.GET,
                null,
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify response with empty body
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
    }

    @Test
    @DisplayName("Should handle large response bodies")
    void shouldHandleLargeResponseBody() {
        // Given: Mock endpoint with large response
        String largeBody = "x".repeat(10000); // 10KB response
        wireMockServer.stubFor(get(urlEqualTo("/api/large"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(largeBody)));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/large",
                HttpMethod.GET,
                null,
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify large response is handled correctly
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).hasSize(10000);
        assertThat(response.body()).isEqualTo(largeBody);
    }

    @Test
    @DisplayName("Should handle special characters in JSON bodies")
    void shouldHandleSpecialCharactersInJsonResponse() {
        // Given: Mock endpoint with special characters
        String jsonWithSpecialChars = "{\"message\":\"Hello\\nWorld\",\"emoji\":\"😀\",\"locale\":\"hello-world\"}";
        wireMockServer.stubFor(get(urlEqualTo("/api/special"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBody(jsonWithSpecialChars)));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/special",
                HttpMethod.GET,
                null,
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify special characters are preserved
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Hello\\nWorld");
        assertThat(response.body()).contains("😀");
        assertThat(response.body()).contains("hello-world");
    }

    @Test
    @DisplayName("Should handle URL query parameters correctly")
    void shouldHandleUrlQueryParameters() {
        // Given: Mock endpoint with query parameters
        wireMockServer.stubFor(get(urlPathEqualTo("/api/search"))
                .withQueryParam("q", equalTo("cancer"))
                .withQueryParam("limit", equalTo("10"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"results\":[]}")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/search?q=cancer&limit=10&offset=0",
                HttpMethod.GET,
                null,
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify query parameters were sent
        assertThat(response.statusCode()).isEqualTo(200);
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/search"))
                .withQueryParam("q", equalTo("cancer"))
                .withQueryParam("limit", equalTo("10"))
                .withQueryParam("offset", equalTo("0")));
    }

    @Test
    @DisplayName("Should handle multi-valued response headers")
    void shouldHandleMultiValueResponseHeaders() {
        // Given: Mock endpoint with multi-value headers
        wireMockServer.stubFor(get(urlEqualTo("/api/multiheader"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Set-Cookie", "session1=abc123")
                        .withHeader("Set-Cookie", "session2=xyz789")
                        .withHeader("Cache-Control", "no-cache, no-store")
                        .withBody("OK")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/multiheader",
                HttpMethod.GET,
                null,
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify multi-value headers are captured
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers()).containsKey("set-cookie");
        // Multi-value headers should be in a list
        List<String> setCookieValues = response.headers().get("set-cookie");
        assertThat(setCookieValues).hasSize(2);
        assertThat(setCookieValues).contains("session1=abc123", "session2=xyz789");
    }

    @Test
    @DisplayName("Should honour Content-Type for POST requests")
    void shouldHandlePostRequestContentType() {
        // Given: Mock endpoint expecting specific content type
        wireMockServer.stubFor(post(urlEqualTo("/api/data"))
                .withHeader("Content-Type", matching("application/json.*"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withBody("{\"id\":1}")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/data",
                HttpMethod.POST,
                Map.of("Content-Type", "application/json; charset=UTF-8"),
                "{\"name\":\"test\"}"
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify content type was sent correctly
        assertThat(response.statusCode()).isEqualTo(201);
        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/data"))
                .withHeader("Content-Type", matching("application/json.*")));
    }

    @Test
    @DisplayName("Should handle requests without headers")
    void shouldHandleRequestWithoutHeaders() {
        // Given: Mock simple endpoint
        wireMockServer.stubFor(get(urlEqualTo("/api/simple"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Simple response")));

        HttpRequest request = new HttpRequest(
                baseUrl + "/api/simple",
                HttpMethod.GET,
                null,  // No headers
                null
        );

        ResilienceConfig config = createDefaultConfig();

        // When: Execute request
        HttpResponse response = httpClientAdapter.call(request, config);

        // Then: Verify request succeeds without headers
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Simple response");
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
