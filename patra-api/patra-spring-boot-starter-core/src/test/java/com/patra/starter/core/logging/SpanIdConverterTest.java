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

/// {@link SpanIdConverter} 单元测试。
///
/// 测试策略: 基础设施组件 - 单元测试，验证 MDC 键读取优先级和回退逻辑。
///
/// 测试覆盖:
///
/// - ✅ OTel Agent 键（span_id）优先级
/// - ✅ Micrometer Tracing 键（spanId）回退
/// - ✅ 事件 MDC 快照优先于线程 MDC
/// - ✅ 无追踪上下文时返回 "N/A"
/// - ✅ 边界条件（空值、空字符串）
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("SpanIdConverter 单元测试")
@ExtendWith(MockitoExtension.class)
class SpanIdConverterTest {

  private SpanIdConverter converter;

  @Mock private ILoggingEvent event;

  @BeforeEach
  void setUp() {
    converter = new SpanIdConverter();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Nested
  @DisplayName("事件 MDC 快照读取测试")
  class EventMdcSnapshotTests {

    @Test
    @DisplayName("应该从事件 MDC 读取 OTel Agent 键（span_id）")
    void shouldReadOtelAgentKeyFromEventMdc() {
      // Given
      String expectedSpanId = "00f067aa0ba902b7";
      Map<String, String> mdcMap = Map.of("span_id", expectedSpanId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSpanId);
    }

    @Test
    @DisplayName("应该从事件 MDC 读取 Micrometer 键（spanId）")
    void shouldReadMicrometerKeyFromEventMdc() {
      // Given
      String expectedSpanId = "b7ad6b7169203331";
      Map<String, String> mdcMap = Map.of("spanId", expectedSpanId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSpanId);
    }

    @Test
    @DisplayName("OTel 键应该优先于 Micrometer 键")
    void shouldPrioritizeOtelKeyOverMicrometerKey() {
      // Given
      String otelSpanId = "otel-span-id-123";
      String micrometerSpanId = "micrometer-span-id-456";
      Map<String, String> mdcMap =
          Map.of(
              "span_id", otelSpanId,
              "spanId", micrometerSpanId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(otelSpanId);
    }
  }

  @Nested
  @DisplayName("线程 MDC 回退测试")
  class ThreadMdcFallbackTests {

    @Test
    @DisplayName("事件 MDC 为空时应该从线程 MDC 读取 OTel 键")
    void shouldFallbackToThreadMdcOtelKey() {
      // Given
      String expectedSpanId = "thread-otel-span-id";
      when(event.getMDCPropertyMap()).thenReturn(Map.of());
      MDC.put("span_id", expectedSpanId);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSpanId);
    }

    @Test
    @DisplayName("事件 MDC 为空时应该从线程 MDC 读取 Micrometer 键")
    void shouldFallbackToThreadMdcMicrometerKey() {
      // Given
      String expectedSpanId = "thread-micrometer-span-id";
      when(event.getMDCPropertyMap()).thenReturn(Map.of());
      MDC.put("spanId", expectedSpanId);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSpanId);
    }

    @Test
    @DisplayName("线程 MDC 中 OTel 键应该优先于 Micrometer 键")
    void shouldPrioritizeOtelKeyInThreadMdc() {
      // Given
      String otelSpanId = "thread-otel-123";
      String micrometerSpanId = "thread-micrometer-456";
      when(event.getMDCPropertyMap()).thenReturn(Map.of());
      MDC.put("span_id", otelSpanId);
      MDC.put("spanId", micrometerSpanId);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(otelSpanId);
    }

    @Test
    @DisplayName("事件 MDC 应该优先于线程 MDC")
    void shouldPrioritizeEventMdcOverThreadMdc() {
      // Given
      String eventSpanId = "event-span-id";
      String threadSpanId = "thread-span-id";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("span_id", eventSpanId));
      MDC.put("span_id", threadSpanId);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(eventSpanId);
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
    @DisplayName("span_id 为空字符串时应该回退到 spanId")
    void shouldFallbackWhenOtelKeyIsEmpty() {
      // Given
      String expectedSpanId = "fallback-span-id";
      Map<String, String> mdcMap = new HashMap<>();
      mdcMap.put("span_id", "");
      mdcMap.put("spanId", expectedSpanId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSpanId);
    }

    @Test
    @DisplayName("span_id 为 null 时应该回退到 spanId")
    void shouldFallbackWhenOtelKeyIsNull() {
      // Given
      String expectedSpanId = "fallback-span-id";
      Map<String, String> mdcMap = new HashMap<>();
      mdcMap.put("span_id", null);
      mdcMap.put("spanId", expectedSpanId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSpanId);
    }

    @Test
    @DisplayName("所有键都为空时应该返回 N/A")
    void shouldReturnNaWhenAllKeysAreEmpty() {
      // Given
      Map<String, String> mdcMap = new HashMap<>();
      mdcMap.put("span_id", "");
      mdcMap.put("spanId", "");
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
      String expectedSpanId = "valid-span-id";
      Map<String, String> mdcMap = new HashMap<>();
      mdcMap.put("span_id", "N/A");
      mdcMap.put("spanId", expectedSpanId);
      when(event.getMDCPropertyMap()).thenReturn(mdcMap);

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(expectedSpanId);
    }
  }

  @Nested
  @DisplayName("实际使用场景测试")
  class RealWorldUseCaseTests {

    @Test
    @DisplayName("应该支持 OTel Java Agent 生成的 16 字符 span_id")
    void shouldSupportOtelAgent16CharSpanId() {
      // Given - OTel span ID 是 16 个十六进制字符
      String otelSpanId = "00f067aa0ba902b7";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("span_id", otelSpanId));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(otelSpanId).hasSize(16);
    }

    @Test
    @DisplayName("应该支持 Micrometer Tracing 的 spanId 格式")
    void shouldSupportMicrometerTracingFormat() {
      // Given
      String micrometerSpanId = "b7ad6b7169203331";
      when(event.getMDCPropertyMap()).thenReturn(Map.of("spanId", micrometerSpanId));

      // When
      String result = converter.convert(event);

      // Then
      assertThat(result).isEqualTo(micrometerSpanId);
    }

    @Test
    @DisplayName("异步日志场景：事件 MDC 优先于当前线程 MDC")
    void shouldPreferEventMdcInAsyncLoggingScenario() {
      // Given - 异步场景中，日志事件携带原始线程的 MDC 快照
      String originalThreadSpanId = "original-thread-span";
      String asyncThreadSpanId = "async-thread-span";

      when(event.getMDCPropertyMap()).thenReturn(Map.of("span_id", originalThreadSpanId));
      MDC.put("span_id", asyncThreadSpanId);

      // When
      String result = converter.convert(event);

      // Then - 应该返回事件快照中的值，而非当前线程的值
      assertThat(result).isEqualTo(originalThreadSpanId);
    }
  }
}
