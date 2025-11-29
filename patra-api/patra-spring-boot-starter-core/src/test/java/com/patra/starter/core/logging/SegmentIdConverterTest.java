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

/// {@link SegmentIdConverter} 单元测试。
///
/// 测试策略: 基础设施组件 - 单元测试，验证 MDC 键读取优先级、回退逻辑和截断行为。
///
/// 测试覆盖:
///
/// - ✅ OTel Agent 键（trace_id）优先级
/// - ✅ Micrometer Tracing 键（traceId）回退
/// - ✅ 事件 MDC 快照优先于线程 MDC
/// - ✅ 无追踪上下文时返回 "N/A"
/// - ✅ traceId 截断为前 16 位
/// - ✅ 边界条件（短于 16 位、空值）
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("SegmentIdConverter 单元测试")
@ExtendWith(MockitoExtension.class)
class SegmentIdConverterTest {

  private SegmentIdConverter converter;

  @Mock private ILoggingEvent event;

  @BeforeEach
  void setUp() {
    converter = new SegmentIdConverter();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Nested
  @DisplayName("截断逻辑测试")
  class TruncationTests {

    @Test
    @DisplayName("应该将 32 字符 traceId 截断为前 16 位")
    void shouldTruncate32CharTraceIdTo16Chars() {
      // Given - OTel trace ID 是 32 个十六进制字符
      String fullTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
      String expectedSegmentId = "4bf92f3577b34da6";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", fullTraceId));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSegmentId).hasSize(16);
    }

    @Test
    @DisplayName("应该将超长 traceId 截断为前 16 位")
    void shouldTruncateLongTraceIdTo16Chars() {
      // Given
      String longTraceId = "abcdef1234567890" + "extra_characters_here";
      String expectedSegmentId = "abcdef1234567890";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", longTraceId));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSegmentId).hasSize(16);
    }

    @Test
    @DisplayName("短于 16 位的 traceId 应该保持原样")
    void shouldKeepShortTraceIdAsIs() {
      // Given
      String shortTraceId = "abc123";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", shortTraceId));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(shortTraceId).hasSize(6);
    }

    @Test
    @DisplayName("正好 16 位的 traceId 应该保持原样")
    void shouldKeepExactly16CharTraceIdAsIs() {
      // Given
      String exactly16Chars = "1234567890abcdef";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", exactly16Chars));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(exactly16Chars).hasSize(16);
    }

    @Test
    @DisplayName("17 位的 traceId 应该截断为 16 位")
    void shouldTruncate17CharTraceIdTo16Chars() {
      // Given
      String char17 = "1234567890abcdefg";
      String expectedSegmentId = "1234567890abcdef";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", char17));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSegmentId).hasSize(16);
    }
  }

  @Nested
  @DisplayName("事件 MDC 快照读取测试")
  class EventMdcSnapshotTests {

    @Test
    @DisplayName("应该从事件 MDC 读取 OTel Agent 键并截断")
    void shouldReadOtelAgentKeyFromEventMdc() {
      // Given
      String fullTraceId = "80f198ee56343ba864fe8b2a57d3eff7";
      String expectedSegmentId = "80f198ee56343ba8";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", fullTraceId));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSegmentId);
    }

    @Test
    @DisplayName("应该从事件 MDC 读取 Micrometer 键并截断")
    void shouldReadMicrometerKeyFromEventMdc() {
      // Given
      String fullTraceId = "a3ce929d0e0e47364bf92f3577b34da6";
      String expectedSegmentId = "a3ce929d0e0e4736";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("traceId", fullTraceId));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSegmentId);
    }

    @Test
    @DisplayName("OTel 键应该优先于 Micrometer 键")
    void shouldPrioritizeOtelKeyOverMicrometerKey() {
      // Given
      String otelTraceId = "otel12345678901234567890abcdef";
      String micrometerTraceId = "micrometer123456789012345678";
      Map<String, String> mdcMap =
          Map.of(
              "trace_id", otelTraceId,
              "traceId", micrometerTraceId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo("otel123456789012");
    }
  }

  @Nested
  @DisplayName("线程 MDC 回退测试")
  class ThreadMdcFallbackTests {

    @Test
    @DisplayName("事件 MDC 为空时应该从线程 MDC 读取 OTel 键并截断")
    void shouldFallbackToThreadMdcOtelKey() {
      // Given
      String fullTraceId = "thread_otel_trace_id_1234567890";
      String expectedSegmentId = "thread_otel_trac";
      when(event.getMDCPropertyMap()).thenReturn(Map.of());
      MDC.put("trace_id", fullTraceId);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSegmentId);
    }

    @Test
    @DisplayName("事件 MDC 为空时应该从线程 MDC 读取 Micrometer 键并截断")
    void shouldFallbackToThreadMdcMicrometerKey() {
      // Given
      String fullTraceId = "thread_micrometer_trace_id_123";
      String expectedSegmentId = "thread_micromete"; // 前 16 位
      when(event.getMDCPropertyMap()).thenReturn(Map.of());
      MDC.put("traceId", fullTraceId);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSegmentId);
    }

    @Test
    @DisplayName("事件 MDC 应该优先于线程 MDC")
    void shouldPrioritizeEventMdcOverThreadMdc() {
      // Given
      String eventTraceId = "event_trace_id_1234567890123456";
      String threadTraceId = "thread_trace_id_9876543210987654";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", eventTraceId));
      MDC.put("trace_id", threadTraceId);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo("event_trace_id_1");
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
      String fallbackTraceId = "fallback_trace_id_1234567890";
      Map<String, String> mdcMap = new HashMap<>();
      mdcMap.put("trace_id", "");
      mdcMap.put("traceId", fallbackTraceId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo("fallback_trace_i");
    }

    @Test
    @DisplayName("trace_id 为 null 时应该回退到 traceId")
    void shouldFallbackWhenOtelKeyIsNull() {
      // Given
      String fallbackTraceId = "fallback_null_trace_1234567890";
      Map<String, String> mdcMap = new HashMap<>();
      mdcMap.put("trace_id", null);
      mdcMap.put("traceId", fallbackTraceId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo("fallback_null_tr");
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
      String validTraceId = "valid_trace_id_1234567890";
      Map<String, String> mdcMap = new HashMap<>();
      mdcMap.put("trace_id", "N/A");
      mdcMap.put("traceId", validTraceId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo("valid_trace_id_1");
    }

    @Test
    @DisplayName("单字符 traceId 应该保持原样")
    void shouldHandleSingleCharTraceId() {
      // Given
      String singleChar = "X";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", singleChar));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo("X");
    }
  }

  @Nested
  @DisplayName("实际使用场景测试")
  class RealWorldUseCaseTests {

    @Test
    @DisplayName("应该正确截断标准 OTel trace_id（W3C 格式）")
    void shouldTruncateStandardOtelTraceId() {
      // Given - W3C Trace Context 的 trace-id 是 32 个十六进制字符
      String w3cTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
      String expectedSegmentId = "4bf92f3577b34da6";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", w3cTraceId));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSegmentId).hasSize(16).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("异步日志场景：事件 MDC 优先于当前线程 MDC")
    void shouldPreferEventMdcInAsyncLoggingScenario() {
      // Given
      String originalThreadTraceId = "original_thread_trace_id_123456";
      String asyncThreadTraceId = "async_thread_trace_id_654321ab";

      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", originalThreadTraceId));
      MDC.put("trace_id", asyncThreadTraceId);

      // When
      String result = converter.convert(event);

      // Then - 应该返回事件快照中的值的前 16 位
      assertThat(result).isEqualTo("original_thread_");
    }

    @Test
    @DisplayName("segmentId 用于日志中快速识别请求")
    void shouldProvideShortIdentifierForQuickRecognition() {
      // Given - 完整的 32 位 trace ID 在日志中太长
      String fullTraceId = "abcdef1234567890fedcba0987654321";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("trace_id", fullTraceId));

      // When
      String segmentId = converter.convert(event);

      // Then - 前 16 位足够唯一识别，同时更易读
      assertThat(segmentId).hasSize(16).isEqualTo("abcdef1234567890");
    }
  }
}
