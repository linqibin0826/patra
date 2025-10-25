package com.patra.starter.web.error.builder;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.problem.ErrorKeys;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.spi.ProblemFieldContributor;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.web.error.config.WebErrorProperties;
import com.patra.starter.web.error.spi.WebProblemFieldContributor;
import com.patra.starter.web.error.util.HttpStatusConverter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builder for RFC 7807 {@link org.springframework.http.ProblemDetail} responses.
 *
 * <p>Features include:
 *
 * <ul>
 *   <li>Masking of sensitive information before it leaves the service.
 *   <li>Proxy-aware path extraction (Forwarded / X-Forwarded-* headers).
 *   <li>Support for both core and Web-specific extension field contributors.
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.web.error.config.WebErrorAutoConfiguration
 */
@Slf4j
public class ProblemDetailBuilder {

  @SuppressWarnings("unused")
  private final ErrorProperties errorProperties;

  private final WebErrorProperties webProperties;
  private final TraceProvider traceProvider;
  private final List<ProblemFieldContributor> coreFieldContributors;
  private final List<WebProblemFieldContributor> webFieldContributors;

  public ProblemDetailBuilder(
      ErrorProperties errorProperties,
      WebErrorProperties webProperties,
      TraceProvider traceProvider,
      List<ProblemFieldContributor> coreFieldContributors,
      List<WebProblemFieldContributor> webFieldContributors) {
    this.errorProperties = errorProperties;
    this.webProperties = webProperties;
    this.traceProvider = traceProvider;
    this.coreFieldContributors = coreFieldContributors;
    this.webFieldContributors = webFieldContributors;
  }

  /**
   * Build a {@link ProblemDetail} instance from the resolved error metadata and HTTP context.
   *
   * @param resolution error resolution containing code and status
   * @param exception exception being handled
   * @param request HTTP request associated with the failure
   * @return fully populated {@link ProblemDetail}
   */
  public ProblemDetail build(
      ErrorResolution resolution, Throwable exception, HttpServletRequest request) {
    log.debug(
        "Building ProblemDetail for error [{}] with HTTP status {}",
        resolution.errorCode().code(),
        resolution.httpStatus());

    HttpStatus httpStatus = HttpStatusConverter.toHttpStatus(resolution.httpStatus());
    ProblemDetail problemDetail = ProblemDetail.forStatus(httpStatus);

    setupStandardFields(problemDetail, resolution, exception, request);
    addTraceIdIfAvailable(problemDetail);
    contributeCoreProblemFields(problemDetail, exception);
    contributeWebProblemFields(problemDetail, exception, request);

    log.debug(
        "ProblemDetail built successfully: type={}, code={}",
        problemDetail.getType(),
        resolution.errorCode().code());

    return problemDetail;
  }

  /**
   * Populates standard RFC 7807 fields and common extension properties.
   *
   * @param problemDetail target problem detail instance
   * @param resolution error resolution metadata
   * @param exception source exception
   * @param request HTTP request context
   */
  private void setupStandardFields(
      ProblemDetail problemDetail,
      ErrorResolution resolution,
      Throwable exception,
      HttpServletRequest request) {
    problemDetail.setType(buildTypeUri(resolution.errorCode()));
    problemDetail.setTitle(resolution.errorCode().code());
    problemDetail.setDetail(maskSensitiveData(exception.getMessage()));

    problemDetail.setProperty(ErrorKeys.CODE, resolution.errorCode().code());
    problemDetail.setProperty(ErrorKeys.PATH, extractPath(request));
    problemDetail.setProperty(
        ErrorKeys.TIMESTAMP, Instant.now().atOffset(ZoneOffset.UTC).toString());
  }

  /**
   * Adds trace ID to problem detail when available from the trace provider.
   *
   * @param problemDetail target problem detail instance
   */
  private void addTraceIdIfAvailable(ProblemDetail problemDetail) {
    traceProvider
        .getCurrentTraceId()
        .ifPresent(
            traceId -> {
              log.debug("Adding distributed trace ID [{}] to ProblemDetail", traceId);
              problemDetail.setProperty(ErrorKeys.TRACE_ID, traceId);
            });
  }

  /**
   * Invokes core field contributors to add platform-wide extension fields.
   *
   * @param problemDetail target problem detail instance
   * @param exception source exception
   */
  private void contributeCoreProblemFields(ProblemDetail problemDetail, Throwable exception) {
    Map<String, Object> coreFields = new HashMap<>();
    coreFieldContributors.forEach(
        contributor -> {
          try {
            contributor.contribute(coreFields, exception);
          } catch (Exception e) {
            log.warn(
                "Core field contributor [{}] failed with error: {}",
                contributor.getClass().getSimpleName(),
                e.getMessage());
          }
        });
    coreFields.forEach(problemDetail::setProperty);
  }

  /**
   * Invokes web-specific field contributors to add HTTP context extension fields.
   *
   * @param problemDetail target problem detail instance
   * @param exception source exception
   * @param request HTTP request context
   */
  private void contributeWebProblemFields(
      ProblemDetail problemDetail, Throwable exception, HttpServletRequest request) {
    Map<String, Object> webFields = new HashMap<>();
    webFieldContributors.forEach(
        contributor -> {
          try {
            contributor.contribute(webFields, exception, request);
          } catch (Exception e) {
            log.warn(
                "Web field contributor [{}] failed with error: {}",
                contributor.getClass().getSimpleName(),
                e.getMessage());
          }
        });
    webFields.forEach(problemDetail::setProperty);
  }

  /**
   * Extracts request path in proxy-aware fashion using precedence: Forwarded header, X-Forwarded-*
   * headers, then requestURI.
   *
   * @param request HTTP request context
   * @return resolved path value
   */
  private String extractPath(HttpServletRequest request) {
    String forwarded = request.getHeader("Forwarded");
    if (forwarded != null && !forwarded.isEmpty()) {
      String path = parseForwardedPath(forwarded);
      if (path != null) {
        return path;
      }
    }

    String forwardedPath = request.getHeader("X-Forwarded-Path");
    if (forwardedPath != null && !forwardedPath.isEmpty()) {
      return forwardedPath;
    }

    String forwardedUri = request.getHeader("X-Forwarded-Uri");
    if (forwardedUri != null && !forwardedUri.isEmpty()) {
      return forwardedUri;
    }

    String requestUri = request.getRequestURI();
    if (log.isDebugEnabled()) {
      log.debug("Resolved request path: {}", requestUri);
    }
    return requestUri;
  }

  /**
   * Parses path attribute from RFC 7239 Forwarded header value.
   *
   * @param forwarded header value
   * @return extracted path or {@code null} when absent
   */
  private String parseForwardedPath(String forwarded) {
    String[] parts = forwarded.split(";");
    for (String part : parts) {
      String trimmed = part.trim();
      if (trimmed.startsWith("path=")) {
        return trimmed.substring(5).replaceAll("^\"|\"$", "");
      }
    }
    return null;
  }

  /**
   * Applies lightweight masking to sensitive key-value pairs in error messages.
   *
   * @param message original detail message
   * @return masked message when sensitive tokens are present
   */
  private String maskSensitiveData(String message) {
    if (message == null) {
      return null;
    }

    return message
        .replaceAll("(?i)(password|token|secret|key)=[^\\s,}]+", "$1=***")
        .replaceAll("(?i)(password|token|secret|key)\":\\s*\"[^\"]+\"", "$1\":\"***\"");
  }

  /**
   * Constructs ProblemDetail type URI from logical error code.
   *
   * @param errorCode platform error code abstraction
   * @return fully-qualified type URI
   */
  private URI buildTypeUri(ErrorCodeLike errorCode) {
    String baseUrl = webProperties.getTypeBaseUrl();
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }
    return URI.create(baseUrl + errorCode.code().toLowerCase());
  }
}
