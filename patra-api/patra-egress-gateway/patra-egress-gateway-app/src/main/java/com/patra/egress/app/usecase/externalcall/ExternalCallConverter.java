package com.patra.egress.app.usecase.externalcall;

import com.patra.egress.api.dto.*;
import com.patra.egress.domain.model.vo.*;
import java.time.Duration;

/**
 * Converter for transforming between API DTOs and Application layer Command/Result objects
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class ExternalCallConverter {

  private ExternalCallConverter() {
    // Utility class, prevent instantiation
  }

  /**
   * Convert ExternalCallRequestDTO to ExternalCallCommand
   *
   * @param dto request DTO
   * @return external call command
   */
  public static ExternalCallCommand toCommand(ExternalCallRequestDTO dto) {
    if (dto == null) {
      throw new IllegalArgumentException("Request DTO cannot be null");
    }

    // Convert HTTP request
    HttpRequest httpRequest =
        new HttpRequest(dto.url(), parseHttpMethod(dto.method()), dto.headers(), dto.body());

    // Convert resilience config (optional)
    ResilienceConfig resilienceConfig =
        dto.config() != null ? toResilienceConfig(dto.config()) : null;

    return new ExternalCallCommand(httpRequest, resilienceConfig);
  }

  /**
   * Convert ExternalCallResult to ExternalCallResponseDTO
   *
   * @param result external call result
   * @return response DTO
   */
  public static ExternalCallResponseDTO toResponseDTO(ExternalCallResult result) {
    if (result == null) {
      throw new IllegalArgumentException("Result cannot be null");
    }

    ResponseEnvelopeDTO envelopeDTO = toResponseEnvelopeDTO(result.envelope());

    return new ExternalCallResponseDTO(
        envelopeDTO, result.duration().toMillis(), result.retryCount(), result.traceId());
  }

  /**
   * Parse HTTP method string to enum
   *
   * @param method HTTP method string
   * @return HttpMethod enum
   */
  private static HttpMethod parseHttpMethod(String method) {
    if (method == null || method.isBlank()) {
      throw new IllegalArgumentException("HTTP method cannot be null or blank");
    }

    try {
      return HttpMethod.valueOf(method.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid HTTP method: " + method, e);
    }
  }

  /**
   * Convert ResilienceConfigDTO to ResilienceConfig
   *
   * @param dto resilience config DTO
   * @return resilience config
   */
  private static ResilienceConfig toResilienceConfig(ResilienceConfigDTO dto) {
    return new ResilienceConfig(
        dto.timeoutSeconds() != null
            ? Duration.ofSeconds(dto.timeoutSeconds())
            : Duration.ofSeconds(30),
        dto.maxRetries() != null ? dto.maxRetries() : 3,
        dto.retryBackoffSeconds() != null
            ? Duration.ofSeconds(dto.retryBackoffSeconds())
            : Duration.ofSeconds(1),
        dto.rateLimit() != null ? dto.rateLimit() : 100,
        dto.circuitBreakerThreshold() != null ? dto.circuitBreakerThreshold() : 10,
        dto.circuitBreakerWindowSeconds() != null
            ? Duration.ofSeconds(dto.circuitBreakerWindowSeconds())
            : Duration.ofMinutes(1),
        dto.responseHeaderWhitelist());
  }

  /**
   * Convert ResponseEnvelope to ResponseEnvelopeDTO
   *
   * @param envelope response envelope
   * @return response envelope DTO
   */
  private static ResponseEnvelopeDTO toResponseEnvelopeDTO(ResponseEnvelope envelope) {
    return new ResponseEnvelopeDTO(
        envelope.success(),
        envelope.statusCode(),
        envelope.headers(),
        envelope.body(),
        envelope.bodyHash(),
        toRateLimitStatusDTO(envelope.rateLimitStatus()),
        toRetryAdviceDTO(envelope.retryAdvice()),
        envelope.snapshotMode());
  }

  /**
   * Convert RateLimitStatus to RateLimitStatusDTO
   *
   * @param status rate limit status
   * @return rate limit status DTO
   */
  private static RateLimitStatusDTO toRateLimitStatusDTO(RateLimitStatus status) {
    ExternalRateLimitInfoDTO externalInfo =
        status.externalInfo() != null ? toExternalRateLimitInfoDTO(status.externalInfo()) : null;

    return new RateLimitStatusDTO(
        status.limit(), status.remaining(), status.resetAfter().getSeconds(), externalInfo);
  }

  /**
   * Convert ExternalRateLimitInfo to ExternalRateLimitInfoDTO
   *
   * @param info external rate limit info
   * @return external rate limit info DTO
   */
  private static ExternalRateLimitInfoDTO toExternalRateLimitInfoDTO(ExternalRateLimitInfo info) {
    return new ExternalRateLimitInfoDTO(info.limit(), info.remaining(), info.resetTimestamp());
  }

  /**
   * Convert RetryAdvice to RetryAdviceDTO
   *
   * @param advice retry advice
   * @return retry advice DTO
   */
  private static RetryAdviceDTO toRetryAdviceDTO(RetryAdvice advice) {
    return new RetryAdviceDTO(
        advice.retryable(), advice.suggestedDelay().getSeconds(), advice.reason());
  }
}
