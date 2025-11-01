package com.patra.starter.core.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;

/**
 * Custom Logback converter for extracting SkyWalking trace ID only.
 *
 * <p>Usage in logback.xml:
 *
 * <pre>
 * &lt;conversionRule conversionWord="traceId"
 *     converterClass="com.patra.starter.core.logging.TraceIdConverter"/&gt;
 * &lt;pattern&gt;... [traceId:%traceId] ...&lt;/pattern&gt;
 * </pre>
 *
 * @since 0.1.0
 */
public class TraceIdConverter extends ClassicConverter {

  private static final String EMPTY_TRACE_ID = "N/A";

  @Override
  public String convert(ILoggingEvent event) {
    try {
      String traceId = TraceContext.traceId();
      if (traceId == null || traceId.isEmpty() || "N/A".equals(traceId)) {
        return EMPTY_TRACE_ID;
      }
      // Extract only the traceId part (before first dot)
      int dotIndex = traceId.indexOf('.');
      if (dotIndex > 0) {
        return traceId.substring(0, dotIndex);
      }
      return traceId;
    } catch (Exception e) {
      return EMPTY_TRACE_ID;
    }
  }
}
