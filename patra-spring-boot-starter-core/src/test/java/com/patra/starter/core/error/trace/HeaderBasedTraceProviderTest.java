package com.patra.starter.core.error.trace;

import static org.assertj.core.api.Assertions.*;

import com.patra.starter.core.error.config.TracingProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

/// {@link HeaderBasedTraceProvider} 单元测试。
///
/// 测试策略: 应用层服务 - 单元测试，验证 MDC 提取逻辑。
///
/// 测试覆盖:
///
/// - ✅ Header 提取逻辑（单个和多个 Header）
///   - ✅ Header 优先级顺序
///   - ✅ 空值和 trim 处理
///   - ✅ 回退场景（无 Header）
///   - ✅ 不同 Trace ID 格式支持
///
/// @author Patra Team
/// @since 2.0
@DisplayName("HeaderBasedTraceProvider 单元测试")
@ExtendWith(MockitoExtension.class)
class HeaderBasedTraceProviderTest {

  private TracingProperties tracingProperties;
  private HeaderBasedTraceProvider traceProvider;

  @BeforeEach
  void setUp() {
    tracingProperties = new TracingProperties();
    traceProvider = new HeaderBasedTraceProvider(tracingProperties);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Nested
  @DisplayName("单个 Header 提取测试")
  class SingleHeaderExtractionTests {

    @Test
    @DisplayName("应该从默认 traceId Header 提取追踪 ID")
    void shouldExtractFromDefaultTraceIdHeader() {
      // Given
      String expectedTraceId = "trace-123-456";
      MDC.put("traceId", expectedTraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(expectedTraceId);
    }

    @Test
    @DisplayName("应该从 X-B3-TraceId Header 提取追踪 ID")
    void shouldExtractFromB3TraceIdHeader() {
      // Given
      String expectedTraceId = "b3-trace-abc123";
      MDC.put("X-B3-TraceId", expectedTraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(expectedTraceId);
    }

    @Test
    @DisplayName("应该从 traceparent Header 提取追踪 ID")
    void shouldExtractFromTraceparentHeader() {
      // Given
      String expectedTraceId = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
      MDC.put("traceparent", expectedTraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(expectedTraceId);
    }

    @Test
    @DisplayName("应该 trim 追踪 ID 两端空格")
    void shouldTrimTraceId() {
      // Given
      String traceIdWithSpaces = "  trace-with-spaces  ";
      String expectedTraceId = "trace-with-spaces";
      MDC.put("traceId", traceIdWithSpaces);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(expectedTraceId);
    }
  }

  @Nested
  @DisplayName("多个 Header 优先级测试")
  class MultipleHeaderPriorityTests {

    @Test
    @DisplayName("应该按配置顺序优先提取第一个有值的 Header")
    void shouldExtractFirstAvailableHeaderInConfiguredOrder() {
      // Given - 默认顺序: traceId > X-B3-TraceId > traceparent
      String expectedTraceId = "trace-from-first-header";
      MDC.put("traceId", expectedTraceId);
      MDC.put("X-B3-TraceId", "should-be-ignored");
      MDC.put("traceparent", "also-ignored");

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(expectedTraceId);
    }

    @Test
    @DisplayName("第一个 Header 为空时应该回退到第二个 Header")
    void shouldFallbackToSecondHeaderWhenFirstIsEmpty() {
      // Given
      String expectedTraceId = "b3-trace-fallback";
      // traceId 不存在
      MDC.put("X-B3-TraceId", expectedTraceId);
      MDC.put("traceparent", "should-be-ignored");

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(expectedTraceId);
    }

    @Test
    @DisplayName("前两个 Header 为空时应该回退到第三个 Header")
    void shouldFallbackToThirdHeaderWhenFirstTwoAreEmpty() {
      // Given
      String expectedTraceId = "traceparent-fallback";
      // traceId 和 X-B3-TraceId 都不存在
      MDC.put("traceparent", expectedTraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(expectedTraceId);
    }

    @Test
    @DisplayName("应该支持自定义 Header 顺序")
    void shouldSupportCustomHeaderOrder() {
      // Given
      TracingProperties customProperties = new TracingProperties();
      customProperties.setHeaderNames(List.of("custom-trace-id", "another-trace-header"));
      HeaderBasedTraceProvider customProvider = new HeaderBasedTraceProvider(customProperties);

      String expectedTraceId = "custom-trace-123";
      MDC.put("custom-trace-id", expectedTraceId);
      MDC.put("traceId", "should-be-ignored"); // 默认 header 应该被忽略

      // When
      Optional<String> traceId = customProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(expectedTraceId);
    }

    @Test
    @DisplayName("应该支持单个 Header 配置")
    void shouldSupportSingleHeaderConfiguration() {
      // Given
      TracingProperties singleHeaderProps = new TracingProperties();
      singleHeaderProps.setHeaderNames(List.of("my-trace-id"));
      HeaderBasedTraceProvider singleHeaderProvider =
          new HeaderBasedTraceProvider(singleHeaderProps);

      String expectedTraceId = "single-header-trace";
      MDC.put("my-trace-id", expectedTraceId);

      // When
      Optional<String> traceId = singleHeaderProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(expectedTraceId);
    }
  }

  @Nested
  @DisplayName("空值和边界条件测试")
  class EmptyAndBoundaryConditionsTests {

    @Test
    @DisplayName("所有 Header 都不存在时应该返回空 Optional")
    void shouldReturnEmptyWhenNoHeadersArePresent() {
      // Given - MDC 完全为空

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isEmpty();
    }

    @Test
    @DisplayName("Header 值为空字符串时应该返回空 Optional")
    void shouldReturnEmptyWhenHeaderValueIsEmptyString() {
      // Given
      MDC.put("traceId", "");

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isEmpty();
    }

    @Test
    @DisplayName("Header 值仅包含空格时应该返回空 Optional")
    void shouldReturnEmptyWhenHeaderValueIsOnlyWhitespace() {
      // Given
      MDC.put("traceId", "   ");

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isEmpty();
    }

    @Test
    @DisplayName("Header 值为 null 时应该返回空 Optional")
    void shouldReturnEmptyWhenHeaderValueIsNull() {
      // Given
      MDC.put("traceId", null);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isEmpty();
    }

    @Test
    @DisplayName("应该处理空的 Header 名称列表")
    void shouldHandleEmptyHeaderNamesList() {
      // Given
      TracingProperties emptyHeadersProps = new TracingProperties();
      emptyHeadersProps.setHeaderNames(List.of());
      HeaderBasedTraceProvider emptyHeadersProvider =
          new HeaderBasedTraceProvider(emptyHeadersProps);

      MDC.put("traceId", "should-not-be-found");

      // When
      Optional<String> traceId = emptyHeadersProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isEmpty();
    }

    @Test
    @DisplayName("应该处理包含 null 元素的 Header 名称列表")
    void shouldHandleNullElementsInHeaderNamesList() {
      // Given
      TracingProperties propsWithNull = new TracingProperties();
      List<String> headersWithNull = new java.util.ArrayList<>();
      headersWithNull.add("valid-header");
      headersWithNull.add(null);
      headersWithNull.add("another-header");
      propsWithNull.setHeaderNames(headersWithNull);
      HeaderBasedTraceProvider providerWithNull = new HeaderBasedTraceProvider(propsWithNull);

      String expectedTraceId = "valid-trace";
      MDC.put("valid-header", expectedTraceId);

      // When
      Optional<String> traceId = providerWithNull.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(expectedTraceId);
    }
  }

  @Nested
  @DisplayName("不同 Trace ID 格式支持测试")
  class DifferentTraceIdFormatsTests {

    @Test
    @DisplayName("应该支持简单 UUID 格式")
    void shouldSupportSimpleUuidFormat() {
      // Given
      String uuidTraceId = "550e8400-e29b-41d4-a716-446655440000";
      MDC.put("traceId", uuidTraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(uuidTraceId);
    }

    @Test
    @DisplayName("应该支持 Zipkin B3 格式")
    void shouldSupportZipkinB3Format() {
      // Given
      String b3TraceId = "80f198ee56343ba864fe8b2a57d3eff7";
      MDC.put("X-B3-TraceId", b3TraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(b3TraceId);
    }

    @Test
    @DisplayName("应该支持 W3C Trace Context 格式")
    void shouldSupportW3cTraceContextFormat() {
      // Given
      String w3cTraceId = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
      MDC.put("traceparent", w3cTraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(w3cTraceId);
    }

    @Test
    @DisplayName("应该支持自定义格式的 Trace ID")
    void shouldSupportCustomTraceIdFormat() {
      // Given
      String customTraceId = "patra-trace-2024-12-31-abc123";
      MDC.put("traceId", customTraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(customTraceId);
    }

    @Test
    @DisplayName("应该支持包含特殊字符的 Trace ID")
    void shouldSupportTraceIdWithSpecialCharacters() {
      // Given
      String specialCharsTraceId = "trace_id-123.456:789";
      MDC.put("traceId", specialCharsTraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(specialCharsTraceId);
    }

    @Test
    @DisplayName("应该支持超长 Trace ID")
    void shouldSupportVeryLongTraceId() {
      // Given
      String longTraceId = "a".repeat(500);
      MDC.put("traceId", longTraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(longTraceId);
    }

    @Test
    @DisplayName("应该支持单字符 Trace ID")
    void shouldSupportSingleCharacterTraceId() {
      // Given
      String singleCharTraceId = "A";
      MDC.put("traceId", singleCharTraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(singleCharTraceId);
    }
  }

  @Nested
  @DisplayName("实际使用场景测试")
  class RealWorldUseCaseTests {

    @Test
    @DisplayName("应该支持 Spring Cloud Sleuth 集成场景")
    void shouldSupportSpringCloudSleuthIntegration() {
      // Given - Sleuth 使用 X-B3-TraceId
      String sleuthTraceId = "5e8edd1818b8cc5e";
      MDC.put("X-B3-TraceId", sleuthTraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(sleuthTraceId);
    }

    @Test
    @DisplayName("应该支持 OpenTelemetry 集成场景")
    void shouldSupportOpenTelemetryIntegration() {
      // Given - OpenTelemetry 使用 traceparent
      String otelTraceId = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
      MDC.put("traceparent", otelTraceId);

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(otelTraceId);
    }

    @Test
    @DisplayName("应该支持自定义追踪系统集成场景")
    void shouldSupportCustomTracingSystemIntegration() {
      // Given
      TracingProperties customTracingProps = new TracingProperties();
      customTracingProps.setHeaderNames(List.of("X-Custom-Request-Id", "X-Request-Id"));
      HeaderBasedTraceProvider customTracingProvider =
          new HeaderBasedTraceProvider(customTracingProps);

      String customRequestId = "req-123456789";
      MDC.put("X-Custom-Request-Id", customRequestId);

      // When
      Optional<String> traceId = customTracingProvider.getCurrentTraceId();

      // Then
      assertThat(traceId).isPresent().contains(customRequestId);
    }

    @Test
    @DisplayName("应该支持多追踪系统共存场景")
    void shouldSupportMultipleTracingSystemsCoexistence() {
      // Given - 同时存在多个追踪系统的 Header
      MDC.put("traceId", "custom-trace-001");
      MDC.put("X-B3-TraceId", "zipkin-trace-002");
      MDC.put("traceparent", "otel-trace-003");

      // When
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      // Then - 应该返回优先级最高的（第一个）
      assertThat(traceId).isPresent().contains("custom-trace-001");
    }

    @Test
    @DisplayName("应该支持 MDC 动态更新场景")
    void shouldSupportDynamicMdcUpdates() {
      // Given - 初始状态
      MDC.put("traceId", "initial-trace");

      // When - 第一次获取
      Optional<String> firstTraceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(firstTraceId).isPresent().contains("initial-trace");

      // When - 更新 MDC
      MDC.put("traceId", "updated-trace");
      Optional<String> secondTraceId = traceProvider.getCurrentTraceId();

      // Then
      assertThat(secondTraceId).isPresent().contains("updated-trace");
    }

    @Test
    @DisplayName("应该支持 MDC 清除后回退场景")
    void shouldSupportMdcClearFallback() {
      // Given
      MDC.put("traceId", "existing-trace");
      Optional<String> beforeClear = traceProvider.getCurrentTraceId();
      assertThat(beforeClear).isPresent();

      // When - 清除 MDC
      MDC.clear();
      Optional<String> afterClear = traceProvider.getCurrentTraceId();

      // Then
      assertThat(afterClear).isEmpty();
    }
  }
}
