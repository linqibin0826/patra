package com.patra.starter.feign.error.decoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.error.problem.ErrorKeys;
import com.patra.starter.feign.error.config.FeignErrorProperties;
import com.patra.starter.feign.error.exception.RemoteCallException;
import com.patra.starter.feign.error.observation.FeignErrorObservationRecorder;
import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;

/**
 * Feign {@link ErrorDecoder} implementation that prefers {@link ProblemDetail} error payloads and
 * degrades gracefully when tolerant mode is enabled.
 */
@Slf4j
public class ProblemDetailErrorDecoder implements ErrorDecoder {

  private final ObjectMapper objectMapper;
  private final FeignErrorProperties properties;
  private final FeignErrorObservationRecorder observationRecorder;

  public ProblemDetailErrorDecoder(
      ObjectMapper objectMapper,
      FeignErrorProperties properties,
      FeignErrorObservationRecorder observationRecorder) {
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.observationRecorder = observationRecorder;
  }

  @Override
  public Exception decode(String methodKey, Response response) {
    DecodingState state = new DecodingState();

    try {
      String contentType = getContentType(response);
      boolean isProblemDetail = isProblemDetailResponse(contentType);
      log.debug(
          "Decoding remote error: method={} status={} contentType={}",
          methodKey,
          response.status(),
          contentType);

      if (isProblemDetail) {
        DecodingResult result = decodeProblemDetailResponse(methodKey, response);
        state.bodyBuffer = result.bodyBuffer();
        state.decodingSuccess = result.success();
        if (result.exception() != null) {
          return result.exception();
        }
      }

      if (properties.isTolerant()) {
        state.tolerantModeUsed = true;
        if (state.bodyBuffer == null) {
          state.bodyBuffer = readResponseBody(methodKey, response);
        }
        return handleTolerantMode(methodKey, response, state.bodyBuffer);
      }

      log.debug("Strict mode active; delegating to FeignException for method={}", methodKey);
      return FeignException.errorStatus(methodKey, response);

    } catch (Exception ex) {
      return handleDecodingException(methodKey, response, state, ex);
    } finally {
      observationRecorder.recordDecodingOutcome(
          methodKey, response.status(), state.decodingSuccess, state.tolerantModeUsed);
    }
  }

  /**
   * Attempts to decode a ProblemDetail response.
   *
   * @param methodKey Feign method key
   * @param response Feign response object
   * @return decoding result containing success status, body buffer, and exception if successful
   * @throws IOException if response body reading fails
   */
  private DecodingResult decodeProblemDetailResponse(String methodKey, Response response)
      throws IOException {
    BodyBuffer bodyBuffer = readResponseBody(methodKey, response);
    ParsingResult parsingResult = parseProblemDetail(bodyBuffer);
    observationRecorder.recordProblemDetailParsing(
        methodKey, response.status(), parsingResult.durationMs(), parsingResult.success());

    if (parsingResult.success() && parsingResult.problemDetail() != null) {
      ProblemDetail problemDetail = parsingResult.problemDetail();

      TraceExtraction traceExtraction = extractTraceId(response);
      observationRecorder.recordTraceIdExtraction(
          methodKey, traceExtraction.traceId() != null, traceExtraction.headerName());
      if (traceExtraction.traceId() != null
          && (problemDetail.getProperties() == null
              || problemDetail.getProperties().get(ErrorKeys.TRACE_ID) == null)) {
        problemDetail.setProperty(ErrorKeys.TRACE_ID, traceExtraction.traceId());
      }

      return new DecodingResult(
          bodyBuffer, true, new RemoteCallException(problemDetail, methodKey));
    }

    return new DecodingResult(bodyBuffer, false, null);
  }

  /**
   * Handles exceptions during error decoding, applying tolerant mode if enabled.
   *
   * @param methodKey Feign method key
   * @param response Feign response object
   * @param state decoding state tracking success and buffers
   * @param ex exception that occurred during decoding
   * @return exception to throw (RemoteCallException or FeignException)
   */
  private Exception handleDecodingException(
      String methodKey, Response response, DecodingState state, Exception ex) {
    log.warn(
        "Failed to decode remote error: method={} status={} error={}",
        methodKey,
        response.status(),
        ex.getMessage());

    if (properties.isTolerant()) {
      state.tolerantModeUsed = true;
      try {
        if (state.bodyBuffer == null) {
          state.bodyBuffer = readResponseBody(methodKey, response);
        }
      } catch (IOException ioException) {
        log.debug(
            "Tolerant mode failed to read response body: method={} error={}",
            methodKey,
            ioException.getMessage());
      }
      return handleTolerantMode(methodKey, response, state.bodyBuffer);
    }

    return FeignException.errorStatus(methodKey, response);
  }

  private RemoteCallException handleTolerantMode(
      String methodKey, Response response, BodyBuffer bodyBuffer) {
    TraceExtraction traceExtraction = extractTraceId(response);
    observationRecorder.recordTraceIdExtraction(
        methodKey, traceExtraction.traceId() != null, traceExtraction.headerName());

    String message = buildFallbackMessage(response, bodyBuffer);
    return new RemoteCallException(
        response.status(), message, methodKey, traceExtraction.traceId());
  }

  private ParsingResult parseProblemDetail(BodyBuffer bodyBuffer) {
    if (bodyBuffer == null || bodyBuffer.content() == null || bodyBuffer.content().isBlank()) {
      return new ParsingResult(null, 0L, false);
    }

    long start = System.nanoTime();
    try {
      ProblemDetail problemDetail =
          objectMapper.readValue(bodyBuffer.content(), ProblemDetail.class);
      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      log.debug("Parsed ProblemDetail successfully: status={}", problemDetail.getStatus());
      return new ParsingResult(problemDetail, durationMs, true);
    } catch (Exception ex) {
      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      log.debug("ProblemDetail parsing failed: {}", ex.getMessage());
      return new ParsingResult(null, durationMs, false);
    }
  }

  private BodyBuffer readResponseBody(String methodKey, Response response) throws IOException {
    if (response.body() == null) {
      return BodyBuffer.empty();
    }

    long start = System.nanoTime();
    int maxSize = properties.getMaxErrorBodySize();
    byte[] bytes;
    try (InputStream inputStream = response.body().asInputStream()) {
      bytes = inputStream.readNBytes(maxSize + 1);
    }
    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    boolean truncated = bytes.length > maxSize;
    int effectiveLength = Math.min(bytes.length, maxSize);
    String content = new String(bytes, 0, effectiveLength, StandardCharsets.UTF_8);

    observationRecorder.recordResponseBodyRead(methodKey, effectiveLength, durationMs, truncated);
    return new BodyBuffer(content, effectiveLength, truncated);
  }

  private String buildFallbackMessage(Response response, BodyBuffer bodyBuffer) {
    String reason = response.reason();
    if (reason != null && !reason.isBlank()) {
      return reason;
    }

    if (bodyBuffer != null && bodyBuffer.content() != null && !bodyBuffer.content().isBlank()) {
      String content = bodyBuffer.content();
      if (content.length() > 200) {
        content = content.substring(0, 200) + "...";
      }
      return "HTTP " + response.status() + ": " + content;
    }

    return "HTTP " + response.status();
  }

  private TraceExtraction extractTraceId(Response response) {
    String[] headers = {"traceId", "X-B3-TraceId", "traceparent", "X-Trace-Id"};
    for (String header : headers) {
      Collection<String> values = response.headers().get(header);
      if (values != null && !values.isEmpty()) {
        String traceId = values.iterator().next();
        if (traceId != null && !traceId.trim().isEmpty()) {
          return new TraceExtraction(traceId.trim(), header);
        }
      }
    }
    return new TraceExtraction(null, null);
  }

  private String getContentType(Response response) {
    Collection<String> contentTypes = response.headers().get("content-type");
    if (contentTypes == null || contentTypes.isEmpty()) {
      contentTypes = response.headers().get("Content-Type");
    }
    if (contentTypes != null && !contentTypes.isEmpty()) {
      return contentTypes.iterator().next();
    }
    return null;
  }

  private boolean isProblemDetailResponse(String contentType) {
    return contentType != null && contentType.toLowerCase().contains("application/problem+json");
  }

  /** Holds response body content and metadata. */
  private record BodyBuffer(String content, int length, boolean truncated) {
    static BodyBuffer empty() {
      return new BodyBuffer(null, 0, false);
    }
  }

  /** Result of ProblemDetail parsing operation. */
  private record ParsingResult(ProblemDetail problemDetail, long durationMs, boolean success) {}

  /** Trace identifier extraction result. */
  private record TraceExtraction(String traceId, String headerName) {}

  /** Result of ProblemDetail decoding operation. */
  private record DecodingResult(BodyBuffer bodyBuffer, boolean success, Exception exception) {}

  /** Mutable state holder for tracking decoding progress. */
  private static class DecodingState {
    boolean decodingSuccess = false;
    boolean tolerantModeUsed = false;
    BodyBuffer bodyBuffer = null;
  }
}
