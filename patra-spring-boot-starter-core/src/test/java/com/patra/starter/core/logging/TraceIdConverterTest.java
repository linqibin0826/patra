package com.patra.starter.core.logging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

/// {@link TraceIdConverter} 单元测试。
///
/// 测试策略: 基础设施组件 - 单元测试，验证 MDC 键读取优先级和回退逻辑。
///
/// 测试覆盖:
///
/// - ✅ OTel Agent 键（trace_id）优先级
/// - ✅ Micrometer Tracing 键（traceId）回退
/// - ✅ 事件 MDC 快照优先于线程 MDC
/// - ✅ 无追踪上下文时返回 "N/A"
/// - ✅ 边界条件（空值、空字符串）
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("TraceIdConverter 单元测试")
@ExtendWith(MockitoExtension.class)
class TraceIdConverterTest {

  private TraceIdConverter converter;

  @Mock private ILoggingEvent event;

  @BeforeEach
  void setUp() {
    converter = new TraceIdConverter();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Nested
  @DisplayName("事件 MDC 快照读取测试")
  class EventMdcSnapshotTests {

    @Test
    @DisplayName("应该从事件 MDC 读取 OTel Agent 键（trace_id）")
    void shouldReadOtelAgentKeyFromEventMdc() {
      // Given
      String expectedTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
      Map<String, String> mdcMap = Map.of("trace_id", expectedTraceId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedTraceId);
    }

    @Test
    @DisplayName("应该从事件 MDC 读取 Micrometer 键（traceId）")
    void shouldReadMicrometerKeyFromEventMdc() {
      // Given
      String expectedTraceId = "80f198ee56343ba864fe8b2a57d3eff7";
      Map<String, String> mdcMap = Map.of("traceId", expectedTraceId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedTraceId);
    }

    @Test
    @DisplayName("OTel 键应该优先于 Micrometer 键")
    void shouldPrioritizeOtelKeyOverMicrometerKey() {
      // Given
      String otelTraceId = "otel-trace-id-123";
      String micrometerTraceId = "micrometer-trace-id-456";
      Map<String, String> mdcMap =
          Map.of(
              "trace_id", otelTraceId,
              "traceId", micrometerTraceId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(otelTraceId);
    }
  }

  @Nested
  @DisplayName("线程 MDC 回退测试")
  class ThreadMdcFallbackTests {

    @Test
    @DisplayName("事件 MDC 为空时应该从线程 MDC 读取 OTel 键")
    void shouldFallbackToThreadMdcOtelKey() {
      // Given
      String expectedTraceId = "thread-otel-trace-id";
      when(event.getMDCPropertyMap()).thenReturn(Map.of());
      MDC.put("trace_id", expectedTraceId);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedTraceId);
    }

    @Test
    @DisplayName("事件 MDC 为空时应该从线程 MDC 读取 Micrometer 键")
    void shouldFallbackToThreadMdcMicrometerKey() {
      // Given
      String expectedTraceId = "thread-micrometer-trace-id";
      when(event.getMDCPropertyMap()).thenReturn(Map.of());
      MDC.put("traceId", expectedTraceId);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedTraceId);
    }

    @Test
    @DisplayName("线程 MDC 中 OTel 键应该优先于 Micrometer 键")
    void shouldPrioritizeOtelKeyInThreadMdc() {
      // Given
      String otelTraceId = "thread-otel-123";
      String micrometerTraceId = "thread-micrometer-456";
      when(event.getMDCPropertyMap()).thenReturn(Map.of());
      MDC.put("trace_id", otelTraceId);
      MDC.put("traceId", micrometerTraceId);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(otelTraceId);
    }

    @Test
    @DisplayName("事件 MDC 应该优先于线程 MDC")
    void shouldPrioritizeEventMdcOverThreadMdc() {
      // Given
      String eventTraceId = "event-trace-id";
      String threadTraceId = "thread-trace-id";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", eventTraceId));
      MDC.put("trace_id", threadTraceId);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(eventTraceId);
    }
  }

  @Nested
  @DisplayName("空值和边界条件测试")
  class EmptyAndBoundaryConditionsTests {

    @Test
    @DisplayName("无任何追踪上下文时应该返回 N/A")
    void shouldReturnNaWhenNoTracingContext() {
      // Given
      when(event.getMDCPropertyMap()).thenReturn(Map.of());

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo("N/A");
    }

    @Test
    @DisplayName("trace_id 为空字符串时应该回退到 traceId")
    void shouldFallbackWhenOtelKeyIsEmpty() {
      // Given
      String expectedTraceId = "fallback-trace-id";
      Map<String, String> mdcMap = new HashMap<>();
      mdcMap.put("trace_id", "");
      mdcMap.put("traceId", expectedTraceId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedTraceId);
    }

    @Test
    @DisplayName("trace_id 为 null 时应该回退到 traceId")
    void shouldFallbackWhenOtelKeyIsNull() {
      // Given
      String expectedTraceId = "fallback-trace-id";
      Map<String, String> mdcMap = new HashMap<>();
      mdcMap.put("trace_id", null);
      mdcMap.put("traceId", expectedTraceId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedTraceId);
    }

    @Test
    @DisplayName("所有键都为空时应该返回 N/A")
    void shouldReturnNaWhenAllKeysAreEmpty() {
      // Given
      Map<String, String> mdcMap = new HashMap<>();
      mdcMap.put("trace_id", "");
      mdcMap.put("traceId", "");
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo("N/A");
    }

    @Test
    @DisplayName("值为 N/A 时应该视为无效并回退")
    void shouldTreatNaAsInvalidValue() {
      // Given
      String expectedTraceId = "valid-trace-id";
      Map<String, String> mdcMap = new HashMap<>();
      mdcMap.put("trace_id", "N/A");
      mdcMap.put("traceId", expectedTraceId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedTraceId);
    }

    @Test
    @DisplayName("MDC 返回 null 时应该处理")
    void shouldHandleNullMdcMap() {
      // Given
      when(event.getMDCPropertyMap()).thenReturn(null);

      // When / Then
      assertThatCode(() -> converter.convert(event)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("实际使用场景测试")
  class RealWorldUseCaseTests {

    @Test
    @DisplayName("应该支持 OTel Java Agent 生成的 32 字符 trace_id")
    void shouldSupportOtelAgent32CharTraceId() {
      // Given - OTel trace ID 是 32 个十六进制字符
      String otelTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", otelTraceId));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(otelTraceId).hasSize(32);
    }

    @Test
    @DisplayName("应该支持 Micrometer Tracing 的 traceId 格式")
    void shouldSupportMicrometerTracingFormat() {
      // Given - Micrometer 可能使用不同长度的 trace ID
      String micrometerTraceId = "80f198ee56343ba8";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("traceId", micrometerTraceId));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(micrometerTraceId);
    }

    @Test
    @DisplayName("异步日志场景：事件 MDC 优先于当前线程 MDC")
    void shouldPreferEventMdcInAsyncLoggingScenario() {
      // Given - 异步场景中，日志事件携带原始线程的 MDC 快照
      String originalThreadTraceId = "original-thread-trace";
      String asyncThreadTraceId = "async-thread-trace";

      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", originalThreadTraceId));
      MDC.put("trace_id", asyncThreadTraceId);

      // When
      String result = converter.convert(event);

      // Then - 应该返回事件快照中的值，而非当前线程的值
      assertThat(result).isEqualTo(originalThreadTraceId);
    }
  }
}
