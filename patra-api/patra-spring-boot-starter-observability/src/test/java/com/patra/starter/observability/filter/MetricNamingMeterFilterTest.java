package com.patra.starter.observability.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// MetricNamingMeterFilter 单元测试。
///
/// 测试指标命名规范过滤器的功能：
///
/// - 自动添加 "patra." 前缀
/// - 转换为小写
/// - 替换非法字符为下划线
/// - 应用自定义前缀
/// - 边界条件处理
///
/// @author Jobs
/// @since 1.0.0
@DisplayName("MetricNamingMeterFilter 单元测试")
class MetricNamingMeterFilterTest {

  /// 前缀添加测试 - 验证自动添加 "patra." 前缀。
  @Nested
  @DisplayName("前缀添加")
  class PrefixAdditionTest {

    private final MetricNamingMeterFilter filter = new MetricNamingMeterFilter(null);

    @Test
    @DisplayName("应为无前缀的指标添加 patra. 前缀")
    void shouldAddPatraPrefixToUnprefixedMetric() {
      Meter.Id original = createMeterId("http.requests");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.http.requests");
    }

    @Test
    @DisplayName("已有 patra. 前缀的指标不应重复添加")
    void shouldNotAddDuplicatePrefix() {
      Meter.Id original = createMeterId("patra.http.requests");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.http.requests");
      // 如果没有变化，应返回原始对象
      assertThat(normalized).isSameAs(original);
    }

    @Test
    @DisplayName("应为简单名称添加前缀")
    void shouldAddPrefixToSimpleName() {
      Meter.Id original = createMeterId("counter");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.counter");
    }
  }

  /// 大小写转换测试 - 验证转换为小写。
  @Nested
  @DisplayName("大小写转换")
  class CaseConversionTest {

    private final MetricNamingMeterFilter filter = new MetricNamingMeterFilter(null);

    @Test
    @DisplayName("应将大写字母转换为小写")
    void shouldConvertUppercaseToLowercase() {
      Meter.Id original = createMeterId("HTTP.Requests.Total");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.http.requests.total");
    }

    @Test
    @DisplayName("应将驼峰命名转换为小写")
    void shouldConvertCamelCaseToLowercase() {
      Meter.Id original = createMeterId("httpRequestCount");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.httprequestcount");
    }

    @Test
    @DisplayName("已是小写的名称不应改变")
    void shouldNotChangeLowercaseName() {
      Meter.Id original = createMeterId("patra.http.requests");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.http.requests");
    }
  }

  /// 非法字符替换测试 - 验证替换非法字符为下划线。
  @Nested
  @DisplayName("非法字符替换")
  class IllegalCharacterReplacementTest {

    private final MetricNamingMeterFilter filter = new MetricNamingMeterFilter(null);

    @Test
    @DisplayName("应将空格替换为下划线")
    void shouldReplaceSpaceWithUnderscore() {
      Meter.Id original = createMeterId("http requests total");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.http_requests_total");
    }

    @Test
    @DisplayName("应将连字符替换为下划线")
    void shouldReplaceHyphenWithUnderscore() {
      Meter.Id original = createMeterId("http-requests-total");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.http_requests_total");
    }

    @Test
    @DisplayName("应将斜杠替换为下划线")
    void shouldReplaceSlashWithUnderscore() {
      Meter.Id original = createMeterId("api/v1/users");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.api_v1_users");
    }

    @Test
    @DisplayName("应将特殊字符替换为下划线")
    void shouldReplaceSpecialCharactersWithUnderscore() {
      Meter.Id original = createMeterId("http@requests#total$count");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.http_requests_total_count");
    }

    @Test
    @DisplayName("应保留数字")
    void shouldPreserveNumbers() {
      Meter.Id original = createMeterId("api.v2.requests.200");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.api.v2.requests.200");
    }

    @Test
    @DisplayName("应合并连续的非法字符为单个点号")
    void shouldMergeConsecutiveIllegalCharacters() {
      Meter.Id original = createMeterId("http---requests___total");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.http.requests.total");
    }
  }

  /// 边界条件测试 - 验证边界条件处理。
  @Nested
  @DisplayName("边界条件")
  class EdgeCasesTest {

    private final MetricNamingMeterFilter filter = new MetricNamingMeterFilter(null);

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("空或 null 名称应返回默认名称")
    void shouldReturnDefaultNameForNullOrEmpty(String name) {
      // 由于 Meter.Id 构造函数不允许 null 名称，我们需要特殊处理
      // 这个测试主要验证 filter 内部的 normalizeName 方法
      if (name == null) {
        // 跳过 null 测试，因为 Meter.Id 不允许 null 名称
        return;
      }
      Meter.Id original = createMeterId(name.isEmpty() ? "" : name);

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.unknown");
    }

    @Test
    @DisplayName("只有点号的名称应返回默认名称")
    void shouldReturnDefaultNameForOnlyDots() {
      Meter.Id original = createMeterId("...");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.unknown");
    }

    @Test
    @DisplayName("只有下划线的名称应返回默认名称")
    void shouldReturnDefaultNameForOnlyUnderscores() {
      Meter.Id original = createMeterId("___");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.unknown");
    }

    @Test
    @DisplayName("应去除开头的点号和下划线")
    void shouldRemoveLeadingDotsAndUnderscores() {
      Meter.Id original = createMeterId("...http.requests");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.http.requests");
    }

    @Test
    @DisplayName("应去除结尾的点号和下划线")
    void shouldRemoveTrailingDotsAndUnderscores() {
      Meter.Id original = createMeterId("http.requests...");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.http.requests");
    }
  }

  /// 自定义前缀测试 - 验证应用自定义前缀。
  @Nested
  @DisplayName("自定义前缀")
  class CustomPrefixTest {

    @Test
    @DisplayName("应在 patra. 后添加自定义前缀")
    void shouldAddCustomPrefixAfterPatra() {
      MetricNamingMeterFilter filter = new MetricNamingMeterFilter("ingest");
      Meter.Id original = createMeterId("http.requests");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.ingest.http.requests");
    }

    @Test
    @DisplayName("已有自定义前缀的指标不应重复添加")
    void shouldNotAddDuplicateCustomPrefix() {
      MetricNamingMeterFilter filter = new MetricNamingMeterFilter("ingest");
      Meter.Id original = createMeterId("patra.ingest.http.requests");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.ingest.http.requests");
    }

    @Test
    @DisplayName("空字符串自定义前缀应被忽略")
    void shouldIgnoreEmptyCustomPrefix() {
      MetricNamingMeterFilter filter = new MetricNamingMeterFilter("");
      Meter.Id original = createMeterId("http.requests");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.http.requests");
    }

    @Test
    @DisplayName("null 自定义前缀应被忽略")
    void shouldIgnoreNullCustomPrefix() {
      MetricNamingMeterFilter filter = new MetricNamingMeterFilter(null);
      Meter.Id original = createMeterId("http.requests");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra.http.requests");
    }

    @ParameterizedTest
    @ValueSource(strings = {"registry", "catalog", "gateway"})
    @DisplayName("应支持不同服务的自定义前缀")
    void shouldSupportDifferentServicePrefixes(String servicePrefix) {
      MetricNamingMeterFilter filter = new MetricNamingMeterFilter(servicePrefix);
      Meter.Id original = createMeterId("http.requests");

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo("patra." + servicePrefix + ".http.requests");
    }
  }

  /// 综合规范化测试 - 验证多种规范化规则的组合应用。
  @Nested
  @DisplayName("综合规范化")
  class ComprehensiveNormalizationTest {

    @ParameterizedTest
    @CsvSource({
      "HTTP.Requests, patra.http.requests",
      "http-requests-total, patra.http_requests_total",
      "api/v1/users, patra.api_v1_users",
      "My App Counter, patra.my_app_counter",
      "___test___, patra.test",
      "...metric..., patra.metric"
    })
    @DisplayName("应正确规范化各种格式的指标名称")
    void shouldNormalizeVariousFormats(String input, String expected) {
      MetricNamingMeterFilter filter = new MetricNamingMeterFilter(null);
      Meter.Id original = createMeterId(input);

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getName()).isEqualTo(expected);
    }

    @Test
    @DisplayName("应同时应用所有规范化规则和自定义前缀")
    void shouldApplyAllNormalizationRulesWithCustomPrefix() {
      MetricNamingMeterFilter filter = new MetricNamingMeterFilter("ingest");
      Meter.Id original = createMeterId("HTTP-Requests__Total");

      Meter.Id normalized = filter.map(original);

      // 1. 转小写：http-requests__total
      // 2. 替换非法字符：http_requests__total
      // 3. 合并连续：http_requests.total
      // 4. 添加 patra.ingest. 前缀
      assertThat(normalized.getName()).isEqualTo("patra.ingest.http_requests.total");
    }
  }

  /// Meter ID 属性保留测试 - 验证规范化后保留原始 ID 的其他属性。
  @Nested
  @DisplayName("Meter ID 属性保留")
  class MeterIdPropertiesTest {

    private final MetricNamingMeterFilter filter = new MetricNamingMeterFilter(null);

    @Test
    @DisplayName("规范化后应保留 Meter 标签")
    void shouldPreserveMeterTags() {
      Meter.Id original = createMeterId("HTTP.Requests", Tags.of("method", "GET", "status", "200"));

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getTag("method")).isEqualTo("GET");
      assertThat(normalized.getTag("status")).isEqualTo("200");
    }

    @Test
    @DisplayName("规范化后应保留 Meter 类型")
    void shouldPreserveMeterType() {
      Meter.Id original = new Meter.Id("HTTP.Requests", Tags.empty(), null, null, Meter.Type.TIMER);

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getType()).isEqualTo(Meter.Type.TIMER);
    }

    @Test
    @DisplayName("规范化后应保留 Meter 描述")
    void shouldPreserveMeterDescription() {
      Meter.Id original =
          new Meter.Id(
              "HTTP.Requests", Tags.empty(), null, "HTTP request count", Meter.Type.COUNTER);

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getDescription()).isEqualTo("HTTP request count");
    }

    @Test
    @DisplayName("规范化后应保留 Meter 基础单位")
    void shouldPreserveMeterBaseUnit() {
      Meter.Id original =
          new Meter.Id("HTTP.Requests", Tags.empty(), "seconds", null, Meter.Type.TIMER);

      Meter.Id normalized = filter.map(original);

      assertThat(normalized.getBaseUnit()).isEqualTo("seconds");
    }
  }

  // ==================== 辅助方法 ====================

  /// 创建测试用 Meter.Id（无标签）。
  ///
  /// @param name 指标名称
  /// @return Meter.Id 实例
  private Meter.Id createMeterId(String name) {
    return createMeterId(name, Tags.empty());
  }

  /// 创建测试用 Meter.Id。
  ///
  /// @param name 指标名称
  /// @param tags 标签集合
  /// @return Meter.Id 实例
  private Meter.Id createMeterId(String name, Tags tags) {
    return new Meter.Id(name, tags, null, null, Meter.Type.COUNTER);
  }
}
