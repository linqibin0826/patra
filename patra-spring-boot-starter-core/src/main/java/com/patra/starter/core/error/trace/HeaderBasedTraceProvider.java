package com.patra.starter.core.error.trace;

import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * {@link TraceProvider} backed by MDC that extracts trace identifiers from configured header names.
 */
@Slf4j
public class HeaderBasedTraceProvider implements TraceProvider {

  /** Trace configuration properties. */
  private final TracingProperties tracingProperties;

  public HeaderBasedTraceProvider(TracingProperties tracingProperties) {
    this.tracingProperties = tracingProperties;
  }

  @Override
  public Optional<String> getCurrentTraceId() {
    for (String headerName : tracingProperties.getHeaderNames()) {
      String traceId = MDC.get(headerName);
      if (traceId != null && !traceId.trim().isEmpty()) {
        log.debug("Found trace ID '{}' from MDC key '{}'", traceId, headerName);
        return Optional.of(traceId.trim());
      }
    }

    log.debug(
        "No trace ID found in MDC for any configured header names: {}",
        tracingProperties.getHeaderNames());
    return Optional.empty();
  }
}
