package dev.linqibin.starter.observability.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// HighCardinalityMeterFilter 单元测试。
///
/// 测试高基数标签过滤器的功能：
///
/// - 默认黑名单过滤（userId、traceId 等）
/// - 自定义黑名单扩展
/// - 保留正常标签
/// - 边界条件处理
///
/// @author Jobs
/// @since 1.0.0
@DisplayName("HighCardinalityMeterFilter 单元测试")
class HighCardinalityMeterFilterTest {

  /// 默认黑名单测试 - 验证默认高基数标签被正确过滤。
  @Nested
  @DisplayName("默认黑名单过滤")
  class DefaultBlacklistTest {

    private final HighCardinalityMeterFilter filter = new HighCardinalityMeterFilter();

    @Test
    @DisplayName("应过滤 userId 标签")
    void shouldFilterUserId() {
      Meter.Id original = createMeterId("test.metric", Tags.of("userId", "12345", "method", "GET"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("userId")).isNull();
      assertThat(filtered.getTag("method")).isEqualTo("GET");
    }

    @Test
    @DisplayName("应过滤 user_id 标签（下划线格式）")
    void shouldFilterUserIdWithUnderscore() {
      Meter.Id original =
          createMeterId("test.metric", Tags.of("user_id", "12345", "status", "200"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("user_id")).isNull();
      assertThat(filtered.getTag("status")).isEqualTo("200");
    }

    @Test
    @DisplayName("应过滤 traceId 标签")
    void shouldFilterTraceId() {
      Meter.Id original =
          createMeterId("test.metric", Tags.of("traceId", "abc123", "application", "test-app"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("traceId")).isNull();
      assertThat(filtered.getTag("application")).isEqualTo("test-app");
    }

    @Test
    @DisplayName("应过滤 requestId 标签")
    void shouldFilterRequestId() {
      Meter.Id original = createMeterId("test.metric", Tags.of("requestId", "req-001"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("requestId")).isNull();
    }

    @Test
    @DisplayName("应过滤 sessionId 标签")
    void shouldFilterSessionId() {
      Meter.Id original = createMeterId("test.metric", Tags.of("sessionId", "sess-xyz"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("sessionId")).isNull();
    }

    @Test
    @DisplayName("应过滤 spanId 标签")
    void shouldFilterSpanId() {
      Meter.Id original = createMeterId("test.metric", Tags.of("spanId", "span-001"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("spanId")).isNull();
    }

    @Test
    @DisplayName("应过滤 email 标签")
    void shouldFilterEmail() {
      Meter.Id original = createMeterId("test.metric", Tags.of("email", "test@example.com"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("email")).isNull();
    }

    @Test
    @DisplayName("应过滤 ip 标签")
    void shouldFilterIp() {
      Meter.Id original = createMeterId("test.metric", Tags.of("ip", "192.168.1.1"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("ip")).isNull();
    }

    @Test
    @DisplayName("应过滤 uuid 标签")
    void shouldFilterUuid() {
      Meter.Id original =
          createMeterId("test.metric", Tags.of("uuid", "550e8400-e29b-41d4-a716-446655440000"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("uuid")).isNull();
    }

    @Test
    @DisplayName("应过滤 timestamp 标签")
    void shouldFilterTimestamp() {
      Meter.Id original = createMeterId("test.metric", Tags.of("timestamp", "1699999999"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("timestamp")).isNull();
    }

    @Test
    @DisplayName("应过滤 phone 标签")
    void shouldFilterPhone() {
      Meter.Id original = createMeterId("test.metric", Tags.of("phone", "13800138000"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("phone")).isNull();
    }

    @Test
    @DisplayName("应同时过滤多个高基数标签")
    void shouldFilterMultipleHighCardinalityTags() {
      Meter.Id original =
          createMeterId(
              "test.metric",
              Tags.of(
                  "userId", "user1",
                  "traceId", "trace1",
                  "method", "POST",
                  "status", "200"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("userId")).isNull();
      assertThat(filtered.getTag("traceId")).isNull();
      assertThat(filtered.getTag("method")).isEqualTo("POST");
      assertThat(filtered.getTag("status")).isEqualTo("200");
    }
  }

  /// 正常标签保留测试 - 验证非高基数标签不受影响。
  @Nested
  @DisplayName("正常标签保留")
  class NormalTagsPreservationTest {

    private final HighCardinalityMeterFilter filter = new HighCardinalityMeterFilter();

    @Test
    @DisplayName("应保留 method 标签")
    void shouldPreserveMethodTag() {
      Meter.Id original = createMeterId("test.metric", Tags.of("method", "GET"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("method")).isEqualTo("GET");
    }

    @Test
    @DisplayName("应保留 status 标签")
    void shouldPreserveStatusTag() {
      Meter.Id original = createMeterId("test.metric", Tags.of("status", "200"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("status")).isEqualTo("200");
    }

    @Test
    @DisplayName("应保留 application 标签")
    void shouldPreserveApplicationTag() {
      Meter.Id original = createMeterId("test.metric", Tags.of("application", "my-app"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("application")).isEqualTo("my-app");
    }

    @Test
    @DisplayName("应保留 environment 标签")
    void shouldPreserveEnvironmentTag() {
      Meter.Id original = createMeterId("test.metric", Tags.of("environment", "prod"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("environment")).isEqualTo("prod");
    }

    @Test
    @DisplayName("无高基数标签时应返回原始 ID")
    void shouldReturnOriginalIdWhenNoHighCardinalityTags() {
      Meter.Id original =
          createMeterId("test.metric", Tags.of("method", "GET", "status", "200", "uri", "/api"));

      Meter.Id filtered = filter.map(original);

      // 没有高基数标签时，应返回原始对象（引用相等）
      assertThat(filtered).isSameAs(original);
    }

    @Test
    @DisplayName("空标签列表应返回原始 ID")
    void shouldReturnOriginalIdWhenNoTags() {
      Meter.Id original = createMeterId("test.metric", Tags.empty());

      Meter.Id filtered = filter.map(original);

      assertThat(filtered).isSameAs(original);
    }
  }

  /// 自定义黑名单测试 - 验证用户自定义高基数标签被正确过滤。
  @Nested
  @DisplayName("自定义黑名单扩展")
  class CustomBlacklistTest {

    @Test
    @DisplayName("应过滤自定义高基数标签")
    void shouldFilterCustomHighCardinalityTag() {
      Set<String> customKeys = Set.of("customId", "orderId");
      HighCardinalityMeterFilter filter = new HighCardinalityMeterFilter(customKeys);

      Meter.Id original =
          createMeterId(
              "test.metric",
              Tags.of("customId", "custom-123", "orderId", "order-456", "type", "A"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("customId")).isNull();
      assertThat(filtered.getTag("orderId")).isNull();
      assertThat(filtered.getTag("type")).isEqualTo("A");
    }

    @Test
    @DisplayName("自定义黑名单应与默认黑名单合并")
    void shouldMergeCustomWithDefaultBlacklist() {
      Set<String> customKeys = Set.of("customId");
      HighCardinalityMeterFilter filter = new HighCardinalityMeterFilter(customKeys);

      // userId 是默认黑名单，customId 是自定义黑名单
      Meter.Id original =
          createMeterId(
              "test.metric", Tags.of("userId", "user1", "customId", "custom1", "method", "GET"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("userId")).isNull(); // 默认黑名单
      assertThat(filtered.getTag("customId")).isNull(); // 自定义黑名单
      assertThat(filtered.getTag("method")).isEqualTo("GET"); // 正常标签
    }

    @Test
    @DisplayName("空自定义黑名单应只使用默认黑名单")
    void shouldUseOnlyDefaultBlacklistWhenCustomIsEmpty() {
      HighCardinalityMeterFilter filter = new HighCardinalityMeterFilter(Set.of());

      Meter.Id original = createMeterId("test.metric", Tags.of("userId", "user1", "method", "GET"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("userId")).isNull();
      assertThat(filtered.getTag("method")).isEqualTo("GET");
    }

    @Test
    @DisplayName("null 自定义黑名单应只使用默认黑名单")
    void shouldUseOnlyDefaultBlacklistWhenCustomIsNull() {
      HighCardinalityMeterFilter filter = new HighCardinalityMeterFilter(null);

      Meter.Id original =
          createMeterId("test.metric", Tags.of("traceId", "trace1", "status", "200"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getTag("traceId")).isNull();
      assertThat(filtered.getTag("status")).isEqualTo("200");
    }
  }

  /// Meter ID 属性保留测试 - 验证过滤后保留原始 ID 的其他属性。
  @Nested
  @DisplayName("Meter ID 属性保留")
  class MeterIdPropertiesTest {

    private final HighCardinalityMeterFilter filter = new HighCardinalityMeterFilter();

    @Test
    @DisplayName("过滤后应保留 Meter 名称")
    void shouldPreserveMeterName() {
      Meter.Id original = createMeterId("patra.http.requests", Tags.of("userId", "user1"));

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getName()).isEqualTo("patra.http.requests");
    }

    @Test
    @DisplayName("过滤后应保留 Meter 类型")
    void shouldPreserveMeterType() {
      Meter.Id original =
          new Meter.Id("test.counter", Tags.of("userId", "user1"), null, null, Meter.Type.COUNTER);

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getType()).isEqualTo(Meter.Type.COUNTER);
    }

    @Test
    @DisplayName("过滤后应保留 Meter 描述")
    void shouldPreserveMeterDescription() {
      Meter.Id original =
          new Meter.Id(
              "test.metric",
              Tags.of("userId", "user1"),
              null,
              "Test description",
              Meter.Type.GAUGE);

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getDescription()).isEqualTo("Test description");
    }

    @Test
    @DisplayName("过滤后应保留 Meter 基础单位")
    void shouldPreserveMeterBaseUnit() {
      Meter.Id original =
          new Meter.Id(
              "test.metric", Tags.of("userId", "user1"), "milliseconds", null, Meter.Type.TIMER);

      Meter.Id filtered = filter.map(original);

      assertThat(filtered.getBaseUnit()).isEqualTo("milliseconds");
    }
  }

  // ==================== 辅助方法 ====================

  /// 创建测试用 Meter.Id。
  ///
  /// @param name 指标名称
  /// @param tags 标签集合
  /// @return Meter.Id 实例
  private Meter.Id createMeterId(String name, Tags tags) {
    return new Meter.Id(name, tags, null, null, Meter.Type.COUNTER);
  }
}
