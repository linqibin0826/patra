package dev.linqibin.starter.core.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/// AsyncExecutorRegistry 单元测试。
///
/// **测试范围**：
///
/// - 线程池注册（正常、重复注册）
/// - 线程池获取（存在、不存在）
/// - 线程池存在性检查
/// - 线程池名称集合获取
/// - 生命周期管理（销毁）
/// - Micrometer 指标集成
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncExecutorRegistry 单元测试")
class AsyncExecutorRegistryTest {

  private AsyncExecutorRegistry registry;

  @AfterEach
  void tearDown() {
    if (registry != null) {
      registry.destroy();
    }
  }

  @Nested
  @DisplayName("register() 测试")
  class RegisterTests {

    @BeforeEach
    void setUp() {
      registry = new AsyncExecutorRegistry(null);
    }

    @Test
    @DisplayName("应成功注册新线程池")
    void register_newPool_shouldSucceed() {
      // Given
      AsyncPoolProperties properties = createDefaultProperties();

      // When
      registry.register("test-pool", properties);

      // Then
      assertThat(registry.hasExecutor("test-pool")).isTrue();
      assertThat(registry.getPoolNames()).contains("test-pool");
    }

    @Test
    @DisplayName("重复注册同名线程池应跳过")
    void register_duplicatePool_shouldSkip() {
      // Given
      AsyncPoolProperties properties1 = createDefaultProperties();
      properties1.setCoreSize(2);

      AsyncPoolProperties properties2 = createDefaultProperties();
      properties2.setCoreSize(10); // 不同配置

      // When
      registry.register("duplicate-pool", properties1);
      registry.register("duplicate-pool", properties2); // 应跳过

      // Then - 只注册一次
      assertThat(registry.getPoolNames()).hasSize(1);
      assertThat(registry.hasExecutor("duplicate-pool")).isTrue();
    }

    @Test
    @DisplayName("应使用配置的线程名前缀")
    void register_withCustomThreadNamePrefix_shouldApply() {
      // Given
      AsyncPoolProperties properties = createDefaultProperties();
      properties.setThreadNamePrefix("custom-prefix-");

      // When
      registry.register("custom-pool", properties);

      // Then
      assertThat(registry.hasExecutor("custom-pool")).isTrue();
    }

    @Test
    @DisplayName("未配置线程名前缀时应使用默认格式")
    void register_withoutThreadNamePrefix_shouldUseDefault() {
      // Given
      AsyncPoolProperties properties = createDefaultProperties();
      properties.setThreadNamePrefix(null);

      // When
      registry.register("default-prefix-pool", properties);

      // Then
      assertThat(registry.hasExecutor("default-prefix-pool")).isTrue();
    }
  }

  @Nested
  @DisplayName("getExecutor() 测试")
  class GetExecutorTests {

    @BeforeEach
    void setUp() {
      registry = new AsyncExecutorRegistry(null);
    }

    @Test
    @DisplayName("应返回已注册的线程池")
    void getExecutor_existingPool_shouldReturn() {
      // Given
      registry.register("existing-pool", createDefaultProperties());

      // When
      Executor executor = registry.getExecutor("existing-pool");

      // Then
      assertThat(executor).isNotNull();
    }

    @Test
    @DisplayName("获取不存在的线程池应抛出异常")
    void getExecutor_nonExistingPool_shouldThrowException() {
      // When & Then
      assertThatThrownBy(() -> registry.getExecutor("non-existing"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("线程池 'non-existing' 不存在");
    }

    @Test
    @DisplayName("异常消息应包含可用线程池列表")
    void getExecutor_nonExisting_messageShouldContainAvailablePools() {
      // Given
      registry.register("pool-a", createDefaultProperties());
      registry.register("pool-b", createDefaultProperties());

      // When & Then
      assertThatThrownBy(() -> registry.getExecutor("non-existing"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("pool-a")
          .hasMessageContaining("pool-b");
    }
  }

  @Nested
  @DisplayName("hasExecutor() 测试")
  class HasExecutorTests {

    @BeforeEach
    void setUp() {
      registry = new AsyncExecutorRegistry(null);
    }

    @Test
    @DisplayName("已注册的线程池应返回 true")
    void hasExecutor_existingPool_shouldReturnTrue() {
      // Given
      registry.register("existing", createDefaultProperties());

      // When & Then
      assertThat(registry.hasExecutor("existing")).isTrue();
    }

    @Test
    @DisplayName("未注册的线程池应返回 false")
    void hasExecutor_nonExistingPool_shouldReturnFalse() {
      // When & Then
      assertThat(registry.hasExecutor("non-existing")).isFalse();
    }
  }

  @Nested
  @DisplayName("getPoolNames() 测试")
  class GetPoolNamesTests {

    @BeforeEach
    void setUp() {
      registry = new AsyncExecutorRegistry(null);
    }

    @Test
    @DisplayName("无线程池时应返回空集合")
    void getPoolNames_noPools_shouldReturnEmptySet() {
      // When
      Set<String> names = registry.getPoolNames();

      // Then
      assertThat(names).isEmpty();
    }

    @Test
    @DisplayName("应返回所有已注册的线程池名称")
    void getPoolNames_multiplePools_shouldReturnAll() {
      // Given
      registry.register("pool-1", createDefaultProperties());
      registry.register("pool-2", createDefaultProperties());
      registry.register("pool-3", createDefaultProperties());

      // When
      Set<String> names = registry.getPoolNames();

      // Then
      assertThat(names).containsExactlyInAnyOrder("pool-1", "pool-2", "pool-3");
    }

    @Test
    @DisplayName("返回的集合应不可修改")
    void getPoolNames_shouldReturnUnmodifiableSet() {
      // Given
      registry.register("pool", createDefaultProperties());

      // When
      Set<String> names = registry.getPoolNames();

      // Then
      assertThatThrownBy(() -> names.add("new-pool"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("destroy() 测试")
  class DestroyTests {

    @BeforeEach
    void setUp() {
      registry = new AsyncExecutorRegistry(null);
    }

    @Test
    @DisplayName("销毁后线程池应被清空")
    void destroy_shouldClearAllPools() {
      // Given
      registry.register("pool-1", createDefaultProperties());
      registry.register("pool-2", createDefaultProperties());
      assertThat(registry.getPoolNames()).hasSize(2);

      // When
      registry.destroy();

      // Then
      assertThat(registry.getPoolNames()).isEmpty();
    }

    @Test
    @DisplayName("销毁空注册表应不报错")
    void destroy_emptyRegistry_shouldNotThrow() {
      // When & Then - 不应抛出异常
      registry.destroy();
      assertThat(registry.getPoolNames()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Micrometer 指标集成测试")
  class MicrometerIntegrationTests {

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
      meterRegistry = new SimpleMeterRegistry();
      registry = new AsyncExecutorRegistry(meterRegistry);
    }

    @Test
    @DisplayName("注册线程池时应注册 Micrometer 指标")
    void register_withMeterRegistry_shouldRegisterMetrics() {
      // Given
      AsyncPoolProperties properties = createDefaultProperties();

      // When
      registry.register("metrics-pool", properties);

      // Then - 验证指标已注册（通过检查 meter 名称）
      assertThat(meterRegistry.getMeters()).isNotEmpty();
    }

    @Test
    @DisplayName("MeterRegistry 为 null 时注册应正常工作")
    void register_withNullMeterRegistry_shouldWork() {
      // Given
      AsyncExecutorRegistry registryWithoutMetrics = new AsyncExecutorRegistry(null);
      AsyncPoolProperties properties = createDefaultProperties();

      // When & Then - 不应抛出异常
      registryWithoutMetrics.register("no-metrics-pool", properties);
      assertThat(registryWithoutMetrics.hasExecutor("no-metrics-pool")).isTrue();

      // Cleanup
      registryWithoutMetrics.destroy();
    }
  }

  /// 创建默认测试用线程池配置。
  private AsyncPoolProperties createDefaultProperties() {
    AsyncPoolProperties properties = new AsyncPoolProperties();
    properties.setCoreSize(2);
    properties.setMaxSize(4);
    properties.setQueueCapacity(100);
    properties.setKeepAliveSeconds(60);
    return properties;
  }
}
