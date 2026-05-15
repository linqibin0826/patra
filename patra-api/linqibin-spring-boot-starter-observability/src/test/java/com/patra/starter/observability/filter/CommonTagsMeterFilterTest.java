package com.patra.starter.observability.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// CommonTagsMeterFilter 单元测试。
///
/// 测试公共标签过滤器的功能：
///
/// - 添加系统标签（application、environment、region、cluster）
/// - 添加用户自定义标签
/// - 不覆盖已存在的标签
/// - 边界条件处理
///
/// @author Jobs
/// @since 1.0.0
@DisplayName("CommonTagsMeterFilter 单元测试")
class CommonTagsMeterFilterTest {

  /// 系统标签添加测试 - 验证系统标签被正确添加。
  @Nested
  @DisplayName("系统标签添加")
  class SystemTagsAdditionTest {

    @Test
    @DisplayName("应添加 application 标签")
    void shouldAddApplicationTag() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter("my-app", null, null, null, null);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("application")).isEqualTo("my-app");
    }

    @Test
    @DisplayName("应添加 environment 标签")
    void shouldAddEnvironmentTag() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter(null, "prod", null, null, null);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("environment")).isEqualTo("prod");
    }

    @Test
    @DisplayName("应添加 region 标签")
    void shouldAddRegionTag() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter(null, null, "cn-east-1", null, null);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("region")).isEqualTo("cn-east-1");
    }

    @Test
    @DisplayName("应添加 cluster 标签")
    void shouldAddClusterTag() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter(null, null, null, "primary", null);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("cluster")).isEqualTo("primary");
    }

    @Test
    @DisplayName("应同时添加所有系统标签")
    void shouldAddAllSystemTags() {
      CommonTagsMeterFilter filter =
          new CommonTagsMeterFilter("my-app", "prod", "cn-east-1", "primary", null);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("application")).isEqualTo("my-app");
      assertThat(mapped.getTag("environment")).isEqualTo("prod");
      assertThat(mapped.getTag("region")).isEqualTo("cn-east-1");
      assertThat(mapped.getTag("cluster")).isEqualTo("primary");
    }
  }

  /// 自定义标签添加测试 - 验证用户自定义标签被正确添加。
  @Nested
  @DisplayName("自定义标签添加")
  class CustomTagsAdditionTest {

    @Test
    @DisplayName("应添加用户自定义标签")
    void shouldAddCustomTags() {
      Map<String, String> customTags = Map.of("team", "platform", "version", "1.0.0");
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter(null, null, null, null, customTags);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("team")).isEqualTo("platform");
      assertThat(mapped.getTag("version")).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("应同时添加系统标签和自定义标签")
    void shouldAddBothSystemAndCustomTags() {
      Map<String, String> customTags = Map.of("team", "platform");
      CommonTagsMeterFilter filter =
          new CommonTagsMeterFilter("my-app", "prod", null, null, customTags);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("application")).isEqualTo("my-app");
      assertThat(mapped.getTag("environment")).isEqualTo("prod");
      assertThat(mapped.getTag("team")).isEqualTo("platform");
    }

    @Test
    @DisplayName("空自定义标签 Map 不应影响其他标签")
    void shouldHandleEmptyCustomTags() {
      CommonTagsMeterFilter filter =
          new CommonTagsMeterFilter("my-app", null, null, null, Map.of());
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("application")).isEqualTo("my-app");
    }

    @Test
    @DisplayName("null 自定义标签不应抛出异常")
    void shouldHandleNullCustomTags() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter("my-app", null, null, null, null);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("application")).isEqualTo("my-app");
    }
  }

  /// 标签不覆盖测试 - 验证已存在的标签不会被覆盖。
  @Nested
  @DisplayName("标签不覆盖")
  class NoOverwriteTest {

    @Test
    @DisplayName("不应覆盖已存在的 application 标签")
    void shouldNotOverwriteExistingApplicationTag() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter("new-app", null, null, null, null);
      Meter.Id original = createMeterId("test.metric", Tags.of("application", "existing-app"));

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("application")).isEqualTo("existing-app");
    }

    @Test
    @DisplayName("不应覆盖已存在的 environment 标签")
    void shouldNotOverwriteExistingEnvironmentTag() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter(null, "prod", null, null, null);
      Meter.Id original = createMeterId("test.metric", Tags.of("environment", "dev"));

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("environment")).isEqualTo("dev");
    }

    @Test
    @DisplayName("不应覆盖已存在的自定义标签")
    void shouldNotOverwriteExistingCustomTag() {
      Map<String, String> customTags = Map.of("team", "new-team");
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter(null, null, null, null, customTags);
      Meter.Id original = createMeterId("test.metric", Tags.of("team", "existing-team"));

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("team")).isEqualTo("existing-team");
    }

    @Test
    @DisplayName("应为不存在的标签添加值，保留已存在的标签")
    void shouldAddMissingTagsAndPreserveExisting() {
      CommonTagsMeterFilter filter =
          new CommonTagsMeterFilter("my-app", "prod", "cn-east-1", null, null);
      Meter.Id original =
          createMeterId("test.metric", Tags.of("environment", "dev", "method", "GET"));

      Meter.Id mapped = filter.map(original);

      // 已存在的 environment 不被覆盖
      assertThat(mapped.getTag("environment")).isEqualTo("dev");
      // 新增的 application 和 region
      assertThat(mapped.getTag("application")).isEqualTo("my-app");
      assertThat(mapped.getTag("region")).isEqualTo("cn-east-1");
      // 原有的其他标签保留
      assertThat(mapped.getTag("method")).isEqualTo("GET");
    }
  }

  /// 空值处理测试 - 验证空值和 null 值的处理。
  @Nested
  @DisplayName("空值处理")
  class EmptyValueHandlingTest {

    @Test
    @DisplayName("null applicationName 不应添加 application 标签")
    void shouldNotAddApplicationTagWhenNull() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter(null, "prod", null, null, null);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("application")).isNull();
      assertThat(mapped.getTag("environment")).isEqualTo("prod");
    }

    @Test
    @DisplayName("空字符串 applicationName 不应添加 application 标签")
    void shouldNotAddApplicationTagWhenEmpty() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter("", "prod", null, null, null);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("application")).isNull();
      assertThat(mapped.getTag("environment")).isEqualTo("prod");
    }

    @Test
    @DisplayName("所有参数为 null 时不应添加任何标签")
    void shouldNotAddAnyTagsWhenAllNull() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter(null, null, null, null, null);
      Meter.Id original = createMeterId("test.metric", Tags.of("method", "GET"));

      Meter.Id mapped = filter.map(original);

      // 只保留原有标签
      assertThat(mapped.getTags()).containsExactly(Tag.of("method", "GET"));
    }

    @Test
    @DisplayName("空字符串 environment 不应添加 environment 标签")
    void shouldNotAddEnvironmentTagWhenEmpty() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter("my-app", "", null, null, null);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("application")).isEqualTo("my-app");
      assertThat(mapped.getTag("environment")).isNull();
    }
  }

  /// Meter ID 属性保留测试 - 验证添加标签后保留原始 ID 的其他属性。
  @Nested
  @DisplayName("Meter ID 属性保留")
  class MeterIdPropertiesTest {

    private final CommonTagsMeterFilter filter =
        new CommonTagsMeterFilter("my-app", "prod", null, null, null);

    @Test
    @DisplayName("添加标签后应保留 Meter 名称")
    void shouldPreserveMeterName() {
      Meter.Id original = createMeterId("patra.http.requests");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getName()).isEqualTo("patra.http.requests");
    }

    @Test
    @DisplayName("添加标签后应保留 Meter 类型")
    void shouldPreserveMeterType() {
      Meter.Id original = new Meter.Id("test.metric", Tags.empty(), null, null, Meter.Type.TIMER);

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getType()).isEqualTo(Meter.Type.TIMER);
    }

    @Test
    @DisplayName("添加标签后应保留 Meter 描述")
    void shouldPreserveMeterDescription() {
      Meter.Id original =
          new Meter.Id("test.metric", Tags.empty(), null, "Test description", Meter.Type.COUNTER);

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getDescription()).isEqualTo("Test description");
    }

    @Test
    @DisplayName("添加标签后应保留 Meter 基础单位")
    void shouldPreserveMeterBaseUnit() {
      Meter.Id original =
          new Meter.Id("test.metric", Tags.empty(), "milliseconds", null, Meter.Type.TIMER);

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getBaseUnit()).isEqualTo("milliseconds");
    }

    @Test
    @DisplayName("添加标签后应保留原有标签")
    void shouldPreserveExistingTags() {
      Meter.Id original = createMeterId("test.metric", Tags.of("method", "GET", "status", "200"));

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("method")).isEqualTo("GET");
      assertThat(mapped.getTag("status")).isEqualTo("200");
      assertThat(mapped.getTag("application")).isEqualTo("my-app");
      assertThat(mapped.getTag("environment")).isEqualTo("prod");
    }
  }

  /// 环境值测试 - 验证不同环境值的处理。
  @Nested
  @DisplayName("环境值处理")
  class EnvironmentValuesTest {

    @Test
    @DisplayName("应正确添加 dev 环境标签")
    void shouldAddDevEnvironment() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter(null, "dev", null, null, null);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("environment")).isEqualTo("dev");
    }

    @Test
    @DisplayName("应正确添加 staging 环境标签")
    void shouldAddStagingEnvironment() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter(null, "staging", null, null, null);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("environment")).isEqualTo("staging");
    }

    @Test
    @DisplayName("应正确添加 prod 环境标签")
    void shouldAddProdEnvironment() {
      CommonTagsMeterFilter filter = new CommonTagsMeterFilter(null, "prod", null, null, null);
      Meter.Id original = createMeterId("test.metric");

      Meter.Id mapped = filter.map(original);

      assertThat(mapped.getTag("environment")).isEqualTo("prod");
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
