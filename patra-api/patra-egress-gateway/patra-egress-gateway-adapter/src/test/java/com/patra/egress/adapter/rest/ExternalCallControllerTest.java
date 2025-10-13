package com.patra.egress.adapter.rest;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.egress.api.dto.ResilienceConfigDTO;
import com.patra.egress.app.usecase.externalcall.ExternalCallResult;
import com.patra.egress.app.usecase.externalcall.ExternalCallUseCase;
import com.patra.egress.domain.model.vo.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for ExternalCallController
 *
 * @author linqibin
 * @since 0.1.0
 */
@WebMvcTest(controllers = ExternalCallController.class)
@ContextConfiguration(classes = {ExternalCallController.class})
class ExternalCallControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private ExternalCallUseCase externalCallUseCase;

  @Test
  void shouldCallExternalServiceSuccessfully() throws Exception {
    // Given
    ExternalCallRequestDTO request =
        new ExternalCallRequestDTO(
            "https://api.example.com/data",
            "GET",
            Map.of("Accept", "application/json"),
            null,
            null);

    ResponseEnvelope envelope =
        new ResponseEnvelope(
            true,
            200,
            Map.of("content-type", "application/json"),
            "{\"result\":\"success\"}",
            "abc123",
            new RateLimitStatus(100, 99, Duration.ofMinutes(1), null),
            new RetryAdvice(false, Duration.ZERO, "Not retryable"),
            "META_PLUS_BODY");

    ExternalCallResult result =
        new ExternalCallResult(envelope, Duration.ofMillis(150), 0, "trace-123");

    when(externalCallUseCase.execute(any())).thenReturn(result);

    // When & Then
    mockMvc
        .perform(
            post("/api/egress/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.envelope.success").value(true))
        .andExpect(jsonPath("$.envelope.statusCode").value(200))
        .andExpect(jsonPath("$.envelope.body").value("{\"result\":\"success\"}"))
        .andExpect(jsonPath("$.durationMs").value(150))
        .andExpect(jsonPath("$.retryCount").value(0))
        .andExpect(jsonPath("$.traceId").value("trace-123"));
  }

  @Test
  void shouldReturnBadRequestWhenUrlIsBlank() throws Exception {
    // Given - URL is blank
    ExternalCallRequestDTO request = new ExternalCallRequestDTO("", "GET", null, null, null);

    // When & Then - In unit test environment without full error handling, we only verify status
    // code
    mockMvc
        .perform(
            post("/api/egress/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnBadRequestWhenMethodIsBlank() throws Exception {
    // Given - HTTP method is blank
    ExternalCallRequestDTO request =
        new ExternalCallRequestDTO("https://api.example.com/data", "", null, null, null);

    // When & Then - In unit test environment without full error handling, we only verify status
    // code
    mockMvc
        .perform(
            post("/api/egress/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnBadRequestWhenResilienceConfigIsInvalid() throws Exception {
    // Given - ResilienceConfig with negative timeout
    ResilienceConfigDTO invalidConfig =
        new ResilienceConfigDTO(
            -10L, // negative timeout
            3,
            1L,
            100,
            10,
            60L,
            List.of("Content-Type"));

    ExternalCallRequestDTO request =
        new ExternalCallRequestDTO(
            "https://api.example.com/data", "GET", null, null, invalidConfig);

    // When & Then - In unit test environment without full error handling, we only verify status
    // code
    mockMvc
        .perform(
            post("/api/egress/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldHandleExternalServiceError() throws Exception {
    // Given
    ExternalCallRequestDTO request =
        new ExternalCallRequestDTO("https://api.example.com/data", "GET", null, null, null);

    ResponseEnvelope envelope =
        new ResponseEnvelope(
            false,
            500,
            Map.of("content-type", "text/plain"),
            "Internal Server Error",
            "def456",
            new RateLimitStatus(100, 100, Duration.ZERO, null),
            new RetryAdvice(true, Duration.ofSeconds(5), "Server error"),
            "META_PLUS_BODY");

    ExternalCallResult result =
        new ExternalCallResult(envelope, Duration.ofMillis(200), 0, "trace-456");

    when(externalCallUseCase.execute(any())).thenReturn(result);

    // When & Then
    mockMvc
        .perform(
            post("/api/egress/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.envelope.success").value(false))
        .andExpect(jsonPath("$.envelope.statusCode").value(500))
        .andExpect(jsonPath("$.envelope.retryAdvice.retryable").value(true))
        .andExpect(jsonPath("$.envelope.retryAdvice.suggestedDelaySeconds").value(5));
  }

  @Test
  void shouldIncludeRateLimitStatusInResponse() throws Exception {
    // Given
    ExternalCallRequestDTO request =
        new ExternalCallRequestDTO("https://api.example.com/data", "GET", null, null, null);

    ExternalRateLimitInfo externalInfo =
        new ExternalRateLimitInfo(1000, 950, System.currentTimeMillis() / 1000 + 3600);

    ResponseEnvelope envelope =
        new ResponseEnvelope(
            true,
            200,
            Map.of(),
            "{}",
            "hash123",
            new RateLimitStatus(100, 95, Duration.ofMinutes(1), externalInfo),
            RetryAdvice.notRetryable(),
            "META_PLUS_BODY");

    ExternalCallResult result =
        new ExternalCallResult(envelope, Duration.ofMillis(100), 0, "trace-789");

    when(externalCallUseCase.execute(any())).thenReturn(result);

    // When & Then
    mockMvc
        .perform(
            post("/api/egress/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.envelope.rateLimitStatus.limit").value(100))
        .andExpect(jsonPath("$.envelope.rateLimitStatus.remaining").value(95))
        .andExpect(jsonPath("$.envelope.rateLimitStatus.externalInfo.limit").value(1000))
        .andExpect(jsonPath("$.envelope.rateLimitStatus.externalInfo.remaining").value(950));
  }

  @Test
  void shouldAcceptRequestWithCustomResilienceConfig() throws Exception {
    // Given
    ResilienceConfigDTO config =
        new ResilienceConfigDTO(10L, 2, 2L, 50, 5, 30L, List.of("Content-Type", "X-Custom-Header"));

    ExternalCallRequestDTO request =
        new ExternalCallRequestDTO(
            "https://api.example.com/data",
            "POST",
            Map.of("Content-Type", "application/json"),
            "{\"key\":\"value\"}",
            config);

    ResponseEnvelope envelope =
        new ResponseEnvelope(
            true,
            201,
            Map.of("content-type", "application/json"),
            "{\"id\":123}",
            "xyz789",
            new RateLimitStatus(50, 49, Duration.ofMinutes(1), null),
            RetryAdvice.notRetryable(),
            "META_PLUS_BODY");

    ExternalCallResult result =
        new ExternalCallResult(envelope, Duration.ofMillis(180), 0, "trace-custom");

    when(externalCallUseCase.execute(any())).thenReturn(result);

    // When & Then
    mockMvc
        .perform(
            post("/api/egress/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.envelope.statusCode").value(201))
        .andExpect(jsonPath("$.traceId").value("trace-custom"));
  }
}
