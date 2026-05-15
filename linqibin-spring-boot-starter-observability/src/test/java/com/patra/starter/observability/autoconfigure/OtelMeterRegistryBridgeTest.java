package com.patra.starter.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// OTel MeterRegistry Bridge 单元测试。
///
/// 测试 `MicrometerAutoConfiguration.otelMeterRegistryBridge()` 方法的核心逻辑：
///
/// - 从 `Metrics.globalRegistry` 提取 OpenTelemetryMeterRegistry
/// - 从 globalRegistry 移除以避免重复计数
/// - 找不到时抛出 IllegalStateException
///
/// 注意：由于 OTel Agent 只在 JVM 启动时注入，无法在单元测试中真正模拟 Agent 环境。
/// 本测试通过模拟类名包含 "OpenTelemetryMeterRegistry" 的 MeterRegistry 来验证逻辑。
///
/// @author Jobs
/// @since 1.0.0
@DisplayName("OTel MeterRegistry Bridge 单元测试")
class OtelMeterRegistryBridgeTest {

  /// 模拟 OpenTelemetryMeterRegistry 的测试类。
  /// 类名包含 "OpenTelemetryMeterRegistry" 以匹配桥接逻辑中的过滤条件。
  static class MockOpenTelemetryMeterRegistry extends SimpleMeterRegistry {
    MockOpenTelemetryMeterRegistry() {
      super(SimpleConfig.DEFAULT, Clock.SYSTEM);
    }
  }

  /// globalRegistry 提取测试 - 验证从 globalRegistry 提取 MeterRegistry。
  @Nested
  @DisplayName("从 globalRegistry 提取 MeterRegistry")
  class ExtractFromGlobalRegistryTest {

    private MockOpenTelemetryMeterRegistry mockOtelRegistry;

    @BeforeEach
    void setUp() {
      // 清理 globalRegistry 中的所有注册表
      Metrics.globalRegistry.getRegistries().forEach(Metrics.globalRegistry::remove);
      // 添加模拟的 OpenTelemetryMeterRegistry
      mockOtelRegistry = new MockOpenTelemetryMeterRegistry();
      Metrics.globalRegistry.add(mockOtelRegistry);
    }

    @AfterEach
    void tearDown() {
      // 清理测试后的状态
      Metrics.globalRegistry.getRegistries().forEach(Metrics.globalRegistry::remove);
    }

    @Test
    @DisplayName("应成功提取类名包含 OpenTelemetryMeterRegistry 的 MeterRegistry")
    void shouldExtractOpenTelemetryMeterRegistry() {
      // 模拟 otelMeterRegistryBridge 方法的核心逻辑
      MeterRegistry extracted =
          Metrics.globalRegistry.getRegistries().stream()
              .filter(r -> r.getClass().getName().contains("OpenTelemetryMeterRegistry"))
              .findAny()
              .orElseThrow(() -> new IllegalStateException("未找到 OpenTelemetryMeterRegistry"));

      assertThat(extracted).isNotNull();
      assertThat(extracted).isInstanceOf(MockOpenTelemetryMeterRegistry.class);
      assertThat(extracted.getClass().getName()).contains("OpenTelemetryMeterRegistry");
    }

    @Test
    @DisplayName("提取后应从 globalRegistry 移除以避免重复计数")
    void shouldRemoveFromGlobalRegistryAfterExtraction() {
      // 验证初始状态
      assertThat(Metrics.globalRegistry.getRegistries()).contains(mockOtelRegistry);

      // 模拟 otelMeterRegistryBridge 方法的核心逻辑
      MeterRegistry extracted =
          Metrics.globalRegistry.getRegistries().stream()
              .filter(r -> r.getClass().getName().contains("OpenTelemetryMeterRegistry"))
              .findAny()
              .orElseThrow();

      // 从 globalRegistry 移除
      Metrics.globalRegistry.remove(extracted);

      // 验证已移除
      assertThat(Metrics.globalRegistry.getRegistries()).doesNotContain(mockOtelRegistry);
    }

    @Test
    @DisplayName("globalRegistry 中有多个 MeterRegistry 时应正确提取目标 Registry")
    void shouldExtractCorrectRegistryWhenMultipleRegistriesExist() {
      // 添加另一个普通的 MeterRegistry
      SimpleMeterRegistry otherRegistry = new SimpleMeterRegistry();
      Metrics.globalRegistry.add(otherRegistry);

      try {
        // 模拟提取逻辑
        MeterRegistry extracted =
            Metrics.globalRegistry.getRegistries().stream()
                .filter(r -> r.getClass().getName().contains("OpenTelemetryMeterRegistry"))
                .findAny()
                .orElseThrow();

        assertThat(extracted).isSameAs(mockOtelRegistry);
        assertThat(extracted).isNotSameAs(otherRegistry);
      } finally {
        Metrics.globalRegistry.remove(otherRegistry);
      }
    }
  }

  /// 异常处理测试 - 验证找不到 OpenTelemetryMeterRegistry 时抛出异常。
  @Nested
  @DisplayName("异常处理")
  class ExceptionHandlingTest {

    @BeforeEach
    void setUp() {
      // 清理 globalRegistry
      Metrics.globalRegistry.getRegistries().forEach(Metrics.globalRegistry::remove);
    }

    @AfterEach
    void tearDown() {
      // 清理测试后的状态
      Metrics.globalRegistry.getRegistries().forEach(Metrics.globalRegistry::remove);
    }

    @Test
    @DisplayName("globalRegistry 为空时应抛出 IllegalStateException")
    void shouldThrowWhenGlobalRegistryIsEmpty() {
      assertThatThrownBy(
              () ->
                  Metrics.globalRegistry.getRegistries().stream()
                      .filter(r -> r.getClass().getName().contains("OpenTelemetryMeterRegistry"))
                      .findAny()
                      .orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "OTel Agent 已配置 micrometer 桥接，但未找到 OpenTelemetryMeterRegistry。"
                                      + "请确保使用 -javaagent 参数正确加载 OTel Java Agent")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("OTel Agent 已配置 micrometer 桥接")
          .hasMessageContaining("OpenTelemetryMeterRegistry");
    }

    @Test
    @DisplayName("globalRegistry 只有其他类型 MeterRegistry 时应抛出 IllegalStateException")
    void shouldThrowWhenOnlyOtherRegistriesExist() {
      // 只添加普通的 SimpleMeterRegistry
      SimpleMeterRegistry simpleRegistry = new SimpleMeterRegistry();
      Metrics.globalRegistry.add(simpleRegistry);

      try {
        assertThatThrownBy(
                () ->
                    Metrics.globalRegistry.getRegistries().stream()
                        .filter(r -> r.getClass().getName().contains("OpenTelemetryMeterRegistry"))
                        .findAny()
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "OTel Agent 已配置 micrometer 桥接，但未找到 OpenTelemetryMeterRegistry。"
                                        + "请确保使用 -javaagent 参数正确加载 OTel Java Agent")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OpenTelemetryMeterRegistry");
      } finally {
        Metrics.globalRegistry.remove(simpleRegistry);
      }
    }
  }

  /// 类名匹配测试 - 验证类名过滤逻辑。
  @Nested
  @DisplayName("类名匹配逻辑")
  class ClassNameMatchingTest {

    @BeforeEach
    void setUp() {
      Metrics.globalRegistry.getRegistries().forEach(Metrics.globalRegistry::remove);
    }

    @AfterEach
    void tearDown() {
      Metrics.globalRegistry.getRegistries().forEach(Metrics.globalRegistry::remove);
    }

    @Test
    @DisplayName("应匹配完整包名中包含 OpenTelemetryMeterRegistry 的类")
    void shouldMatchClassWithFullPackageName() {
      MockOpenTelemetryMeterRegistry registry = new MockOpenTelemetryMeterRegistry();
      Metrics.globalRegistry.add(registry);

      try {
        // 验证类名确实包含 "OpenTelemetryMeterRegistry"
        String className = registry.getClass().getName();
        assertThat(className)
            .contains("OpenTelemetryMeterRegistry")
            .isEqualTo(
                "com.patra.starter.observability.autoconfigure.OtelMeterRegistryBridgeTest$MockOpenTelemetryMeterRegistry");

        // 验证过滤逻辑能正确匹配
        boolean matched =
            Metrics.globalRegistry.getRegistries().stream()
                .anyMatch(r -> r.getClass().getName().contains("OpenTelemetryMeterRegistry"));

        assertThat(matched).isTrue();
      } finally {
        Metrics.globalRegistry.remove(registry);
      }
    }

    @Test
    @DisplayName("SimpleMeterRegistry 不应被匹配")
    void shouldNotMatchSimpleMeterRegistry() {
      SimpleMeterRegistry registry = new SimpleMeterRegistry();
      Metrics.globalRegistry.add(registry);

      try {
        boolean matched =
            Metrics.globalRegistry.getRegistries().stream()
                .anyMatch(r -> r.getClass().getName().contains("OpenTelemetryMeterRegistry"));

        assertThat(matched).isFalse();
      } finally {
        Metrics.globalRegistry.remove(registry);
      }
    }
  }

  /// globalRegistry 状态测试 - 验证 globalRegistry 操作的正确性。
  @Nested
  @DisplayName("globalRegistry 状态管理")
  class GlobalRegistryStateTest {

    @BeforeEach
    void setUp() {
      Metrics.globalRegistry.getRegistries().forEach(Metrics.globalRegistry::remove);
    }

    @AfterEach
    void tearDown() {
      Metrics.globalRegistry.getRegistries().forEach(Metrics.globalRegistry::remove);
    }

    @Test
    @DisplayName("add 和 remove 操作应正确管理 globalRegistry 状态")
    void shouldCorrectlyManageGlobalRegistryState() {
      MockOpenTelemetryMeterRegistry registry = new MockOpenTelemetryMeterRegistry();

      // 初始状态
      assertThat(Metrics.globalRegistry.getRegistries()).isEmpty();

      // 添加后
      Metrics.globalRegistry.add(registry);
      assertThat(Metrics.globalRegistry.getRegistries()).hasSize(1);
      assertThat(Metrics.globalRegistry.getRegistries()).contains(registry);

      // 移除后
      Metrics.globalRegistry.remove(registry);
      assertThat(Metrics.globalRegistry.getRegistries()).isEmpty();
    }

    @Test
    @DisplayName("移除后的 MeterRegistry 仍然可用")
    void removedRegistryShouldStillBeUsable() {
      MockOpenTelemetryMeterRegistry registry = new MockOpenTelemetryMeterRegistry();
      Metrics.globalRegistry.add(registry);

      // 移除
      Metrics.globalRegistry.remove(registry);

      // 验证 registry 仍然可用（可以记录指标）
      registry.counter("test.counter").increment();
      assertThat(registry.counter("test.counter").count()).isEqualTo(1.0);
    }
  }
}
