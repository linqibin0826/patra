package com.patra.starter.core.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;

/**
 * Custom Logback converter for extracting SkyWalking segment ID.
 *
 * <p>Usage in logback.xml:
 *
 * <pre>
 * &lt;conversionRule conversionWord="segmentId"
 *     converterClass="com.patra.starter.core.logging.SegmentIdConverter"/&gt;
 * &lt;pattern&gt;... [segmentId:%segmentId] ...&lt;/pattern&gt;
 * </pre>
 *
 * @since 0.1.0
 */
public class SegmentIdConverter extends ClassicConverter {

  private static final String EMPTY_SEGMENT_ID = "N/A";

  @Override
  public String convert(ILoggingEvent event) {
    try {
      String traceId = TraceContext.traceId();
      if (traceId == null || traceId.isEmpty() || "N/A".equals(traceId)) {
        return EMPTY_SEGMENT_ID;
      }
      // Extract segment ID (between first and second dot)
      // Format: traceId.segmentId.spanId
      String[] parts = traceId.split("\\.");
      if (parts.length >= 2) {
        return parts[1];
      }
      return EMPTY_SEGMENT_ID;
    } catch (Exception e) {
      return EMPTY_SEGMENT_ID;
    }
  }
}
