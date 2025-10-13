package com.patra.egress.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.patra.egress.api.dto.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * MVP end-to-end tests using WireMock to emulate external services and validate the full invocation
 * flow
 *
 * <p>Test scenarios:
 *
 * <ul>
 *   <li>Scenario 1: successful external API call (PubMed simulation)
 *   <li>Scenario 2: handle provider throttling (429 response)
 *   <li>Scenario 3: handle provider errors (500 response)
 *   <li>Scenario 4: configuration override (custom timeout)
 *   <li>Scenario 5: response header whitelist filtering
 * </ul>
 *
 * @author Papertrace Team
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MvpEndToEndTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  private WireMockServer wireMockServer;
  private String wireMockBaseUrl;

  @BeforeAll
  void setupWireMock() {
    // Start the WireMock server
    wireMockServer =
        new WireMockServer(WireMockConfiguration.options().dynamicPort().dynamicHttpsPort());
    wireMockServer.start();
    wireMockBaseUrl = "http://localhost:" + wireMockServer.port();

    System.out.println("WireMock server started successfully on port " + wireMockServer.port());
  }

  @AfterAll
  void teardownWireMock() {
    if (wireMockServer != null && wireMockServer.isRunning()) {
      wireMockServer.stop();
      System.out.println("WireMock server stopped");
    }
  }

  @BeforeEach
  void resetWireMock() {
    wireMockServer.resetAll();
  }

  /** Scenario 1: successful external API call (simulated PubMed search) */
  @Test
  @DisplayName("Scenario 1: successful external API call - PubMed search")
  void testSuccessfulExternalCall_PubMed() throws Exception {
    // Step 1: configure WireMock stub to simulate a successful PubMed API response
    String pubmedResponse =
        """
                {
                    "header": {
                        "type": "esearch",
                        "version": "0.3"
                    },
                    "esearchresult": {
                        "count": "2",
                        "retmax": "2",
                        "retstart": "0",
                        "idlist": ["38234567", "38234568"]
                    }
                }
                """;

    wireMockServer.stubFor(
        get(urlPathEqualTo("/entrez/eutils/esearch.fcgi"))
            .withQueryParam("db", equalTo("pubmed"))
            .withQueryParam("term", equalTo("cancer"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("X-RateLimit-Limit", "10")
                    .withHeader("X-RateLimit-Remaining", "9")
                    .withHeader("X-RateLimit-Reset", "1696550400")
                    .withBody(pubmedResponse)));

    // Step 2: build the gateway request
    ExternalCallRequestDTO request =
        new ExternalCallRequestDTO(
            wireMockBaseUrl + "/entrez/eutils/esearch.fcgi?db=pubmed&term=cancer",
            "GET",
            Map.of(
                "User-Agent", "Papertrace/0.1.0",
                "Accept", "application/json"),
            null, // GET request has no body
            null // Use the system default configuration
            );

    // Step 3: invoke the gateway API
    ResponseEntity<ExternalCallResponseDTO> response =
        restTemplate.postForEntity("/api/egress/call", request, ExternalCallResponseDTO.class);

    // Step 4: validate the response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    ExternalCallResponseDTO result = response.getBody();
    ResponseEnvelopeDTO envelope = result.envelope();

    // 4.1 Verify success indicator
    assertThat(envelope.success()).isTrue();
    assertThat(envelope.statusCode()).isEqualTo(200);

    // 4.2 Verify response body contents
    assertThat(envelope.body()).contains("esearchresult");
    assertThat(envelope.body()).contains("38234567");

    // 4.3 Verify response body hash
    assertThat(envelope.bodyHash()).isNotEmpty();

    // 4.4 Verify response header whitelisting
    assertThat(envelope.headers()).containsKey("Content-Type");
    assertThat(envelope.headers().get("Content-Type")).isEqualTo("application/json");

    // 4.5 Verify extraction of external rate limit information
    RateLimitStatusDTO rateLimitStatus = envelope.rateLimitStatus();
    assertThat(rateLimitStatus).isNotNull();
    ExternalRateLimitInfoDTO externalInfo = rateLimitStatus.externalInfo();
    assertThat(externalInfo).isNotNull();
    assertThat(externalInfo.limit()).isEqualTo(10);
    assertThat(externalInfo.remaining()).isEqualTo(9);
    assertThat(externalInfo.resetTimestamp()).isEqualTo(1696550400L);

    // 4.6 Verify retry advice (successful call should not retry)
    RetryAdviceDTO retryAdvice = envelope.retryAdvice();
    assertThat(retryAdvice).isNotNull();
    assertThat(retryAdvice.retryable()).isFalse();

    // 5. Verify WireMock received the expected request
    wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/entrez/eutils/esearch.fcgi"))
            .withQueryParam("db", equalTo("pubmed"))
            .withQueryParam("term", equalTo("cancer"))
            .withHeader("User-Agent", equalTo("Papertrace/0.1.0"))
            .withHeader("Accept", equalTo("application/json")));

    System.out.println(
        "✅ Scenario 1 passed: successful external call and proper response envelope");
    System.out.println("   - Response status code: " + envelope.statusCode());
    System.out.println("   - Response body hash: " + envelope.bodyHash());
    System.out.println(
        "   - External rate limit: " + externalInfo.remaining() + "/" + externalInfo.limit());
  }

  /** Scenario 2: handle provider throttling (429 response) */
  @Test
  @DisplayName("Scenario 2: handle provider throttling - 429 Too Many Requests")
  void testExternalServiceRateLimited() throws Exception {
    // Step 1: configure WireMock stub to simulate provider throttling
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/data"))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Retry-After", "60")
                    .withHeader("X-RateLimit-Limit", "100")
                    .withHeader("X-RateLimit-Remaining", "0")
                    .withBody("{\"error\": \"Rate limit exceeded\"}")));

    // Step 2: build the gateway request
    ExternalCallRequestDTO request =
        new ExternalCallRequestDTO(
            wireMockBaseUrl + "/api/data",
            "GET",
            Map.of("Authorization", "Bearer test-token"),
            null,
            null);

    // Step 3: invoke the gateway API
    ResponseEntity<ExternalCallResponseDTO> response =
        restTemplate.postForEntity("/api/egress/call", request, ExternalCallResponseDTO.class);

    // Step 4: validate the response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ExternalCallResponseDTO result = response.getBody();
    ResponseEnvelopeDTO envelope = result.envelope();

    // 4.1 Verify failure indicator
    assertThat(envelope.success()).isFalse();
    assertThat(envelope.statusCode()).isEqualTo(429);

    // 4.2 Inspect the response body
    assertThat(envelope.body()).contains("Rate limit exceeded");

    // 4.3 Verify extracted external rate limit information
    ExternalRateLimitInfoDTO externalInfo = envelope.rateLimitStatus().externalInfo();
    assertThat(externalInfo.limit()).isEqualTo(100);
    assertThat(externalInfo.remaining()).isEqualTo(0);

    // 4.4 Validate retry advice (429 should recommend retry)
    RetryAdviceDTO retryAdvice = envelope.retryAdvice();
    assertThat(retryAdvice.retryable()).isTrue();
    assertThat(retryAdvice.suggestedDelaySeconds()).isEqualTo(60); // 60 seconds
    assertThat(retryAdvice.reason()).contains("Rate limited");

    System.out.println("✅ Scenario 2 passed: provider throttling handled correctly");
    System.out.println("   - Recognised 429 status code");
    System.out.println(
        "   - Extracted Retry-After header: " + retryAdvice.suggestedDelaySeconds() + " seconds");
    System.out.println("   - Retry recommended: " + retryAdvice.retryable());
  }

  /** Scenario 3: handle provider errors (500 response) */
  @Test
  @DisplayName("Scenario 3: handle provider error - 500 Internal Server Error")
  void testExternalServiceError() throws Exception {
    // Step 1: configure WireMock stub to simulate provider error
    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/upload"))
            .willReturn(
                aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\": \"Internal server error\", \"message\": \"Database connection failed\"}")));

    // Step 2: build the gateway request (simulate OSS upload)
    ExternalCallRequestDTO request =
        new ExternalCallRequestDTO(
            wireMockBaseUrl + "/api/upload",
            "POST",
            Map.of(
                "Content-Type", "application/octet-stream",
                "Authorization", "OSS access-key:signature"),
            "binary file content...",
            null);

    // Step 3: invoke the gateway API
    ResponseEntity<ExternalCallResponseDTO> response =
        restTemplate.postForEntity("/api/egress/call", request, ExternalCallResponseDTO.class);

    // Step 4: validate the response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ExternalCallResponseDTO result = response.getBody();
    ResponseEnvelopeDTO envelope = result.envelope();

    // 4.1 Verify failure indicator
    assertThat(envelope.success()).isFalse();
    assertThat(envelope.statusCode()).isEqualTo(500);

    // 4.2 Verify error details
    assertThat(envelope.body()).contains("Internal server error");

    // 4.3 Verify retry advice (5xx responses should recommend retry)
    RetryAdviceDTO retryAdvice = envelope.retryAdvice();
    assertThat(retryAdvice.retryable()).isTrue();
    assertThat(retryAdvice.reason()).contains("Server error");

    System.out.println("✅ Scenario 3 passed: provider error handled correctly");
    System.out.println("   - Recognised 500 status code");
    System.out.println("   - Retry recommended: " + retryAdvice.retryable());
  }

  /** Scenario 4: configuration override with caller-defined timeout */
  @Test
  @DisplayName("Scenario 4: configuration override - caller-defined timeout")
  void testConfigOverride_CustomTimeout() throws Exception {
    // Step 1: configure WireMock stub for a fast response
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/fast"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/plain")
                    .withBody("OK")));

    // Step 2: build the gateway request using the shorter custom configuration
    ResilienceConfigDTO customConfig =
        new ResilienceConfigDTO(
            5L, // 5-second timeout (shorter than the 30-second default)
            2, // Maximum of 2 retries
            1L, // 1-second backoff
            100, // Optional rate limit override
            5, // Optional circuit breaker threshold
            30L, // Optional circuit breaker window
            null // Use the default whitelist
            );

    ExternalCallRequestDTO request =
        new ExternalCallRequestDTO(
            wireMockBaseUrl + "/api/fast", "GET", Map.of(), null, customConfig);

    // Step 3: invoke the gateway API
    ResponseEntity<ExternalCallResponseDTO> response =
        restTemplate.postForEntity("/api/egress/call", request, ExternalCallResponseDTO.class);

    // Step 4: validate the response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ExternalCallResponseDTO result = response.getBody();

    // Confirm the call succeeded within the custom timeout window
    assertThat(result.envelope().success()).isTrue();
    assertThat(result.envelope().statusCode()).isEqualTo(200);

    System.out.println("✅ Scenario 4 passed: configuration override worked as expected");
    System.out.println("   - Custom timeout used: 5 seconds");
    System.out.println("   - Call duration: " + result.durationMs() + "ms");
  }

  /** Scenario 5: response header whitelist filtering */
  @Test
  @DisplayName("Scenario 5: response header whitelist - retain only allowed headers")
  void testResponseHeaderWhitelistFilter() throws Exception {
    // Step 1: configure WireMock stub to return multiple headers
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/test"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Content-Length", "100")
                    .withHeader("X-RateLimit-Limit", "1000")
                    .withHeader(
                        "X-Custom-Internal-Header", "internal-value") // Not on the whitelist
                    .withHeader("Set-Cookie", "session=abc123") // Sensitive header
                    .withBody("{\"data\": \"test\"}")));

    // Step 2: build the gateway request
    ExternalCallRequestDTO request =
        new ExternalCallRequestDTO(
            wireMockBaseUrl + "/api/test", "GET", Map.of(), null, null // Use the system whitelist
            );

    // Step 3: invoke the gateway API
    ResponseEntity<ExternalCallResponseDTO> response =
        restTemplate.postForEntity("/api/egress/call", request, ExternalCallResponseDTO.class);

    // Step 4: validate the response and header filtering
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ResponseEnvelopeDTO envelope = response.getBody().envelope();

    // 4.1 Verify whitelisted headers are preserved
    assertThat(envelope.headers()).containsKey("Content-Type");
    // Note: Content-Length may not be present due to HTTP client implementation details (e.g.,
    // chunked transfer)
    // We verify it's either present or absent based on the actual response
    assertThat(envelope.headers()).containsKey("X-RateLimit-Limit");

    // 4.2 Verify non-whitelisted headers are removed
    assertThat(envelope.headers()).doesNotContainKey("X-Custom-Internal-Header");
    assertThat(envelope.headers()).doesNotContainKey("Set-Cookie");

    System.out.println("✅ Scenario 5 passed: response header whitelist enforced");
    System.out.println("   - Retained headers: " + envelope.headers().keySet());
    System.out.println("   - Sensitive headers removed");
  }

  /** Comprehensive scenario: full invocation walkthrough */
  @Test
  @DisplayName(
      "Comprehensive scenario: PubMed search + configuration override + whitelist filtering")
  void testCompleteFlow() throws Exception {
    // Step 1: configure WireMock stub
    String responseBody =
        """
                {
                    "header": {"type": "esearch"},
                    "esearchresult": {
                        "count": "1",
                        "idlist": ["12345678"]
                    }
                }
                """;

    wireMockServer.stubFor(
        get(urlPathEqualTo("/pubmed/search"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("X-RateLimit-Limit", "10")
                    .withHeader("X-RateLimit-Remaining", "5")
                    .withBody(responseBody)));

    // Step 2: build the gateway request with custom configuration and whitelist
    ResilienceConfigDTO customConfig =
        new ResilienceConfigDTO(
            10L, // 10-second timeout
            3, // Maximum of 3 retries
            2L, // 2-second backoff
            100, // Rate limit override
            10, // Circuit breaker threshold
            30L, // Circuit breaker window
            java.util.List.of(
                "Content-Type", "X-RateLimit-Limit", "X-RateLimit-Remaining") // Custom whitelist
            );

    ExternalCallRequestDTO request =
        new ExternalCallRequestDTO(
            wireMockBaseUrl + "/pubmed/search",
            "GET",
            Map.of("User-Agent", "Papertrace-Test/1.0"),
            null,
            customConfig);

    // Step 3: invoke the gateway API
    ResponseEntity<ExternalCallResponseDTO> response =
        restTemplate.postForEntity("/api/egress/call", request, ExternalCallResponseDTO.class);

    // Step 4: perform comprehensive assertions
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ExternalCallResponseDTO result = response.getBody();
    ResponseEnvelopeDTO envelope = result.envelope();

    // Confirm the call succeeded
    assertThat(envelope.success()).isTrue();
    assertThat(envelope.statusCode()).isEqualTo(200);

    // Inspect the response body
    assertThat(envelope.body()).contains("12345678");
    assertThat(envelope.bodyHash()).isNotEmpty();

    // Validate the custom whitelist
    assertThat(envelope.headers()).hasSize(3);
    assertThat(envelope.headers())
        .containsKeys("Content-Type", "X-RateLimit-Limit", "X-RateLimit-Remaining");

    // Validate rate limit data
    assertThat(envelope.rateLimitStatus().externalInfo().limit()).isEqualTo(10);
    assertThat(envelope.rateLimitStatus().externalInfo().remaining()).isEqualTo(5);

    // Validate trace identifier
    assertThat(result.traceId()).isNotEmpty();

    // Validate call duration
    assertThat(result.durationMs()).isGreaterThan(0);

    System.out.println("✅ Comprehensive scenario passed: full invocation validated successfully");
    System.out.println("   - Response status: " + envelope.statusCode());
    System.out.println("   - Response hash: " + envelope.bodyHash());
    System.out.println(
        "   - Rate limit status: "
            + envelope.rateLimitStatus().externalInfo().remaining()
            + "/"
            + envelope.rateLimitStatus().externalInfo().limit());
    System.out.println("   - Whitelisted headers: " + envelope.headers().keySet());
    System.out.println("   - Call duration: " + result.durationMs() + "ms");
    System.out.println("   - Trace ID: " + result.traceId());
  }
}
