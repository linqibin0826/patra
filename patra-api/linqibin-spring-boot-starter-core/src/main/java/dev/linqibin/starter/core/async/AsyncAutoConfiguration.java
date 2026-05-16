package dev.linqibin.starter.core.async;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// 通用异步线程池自动配置。
///
/// 根据 `linqibin.starter.core.async.pools` 配置自动创建命名线程池，
/// 支持 Micrometer 指标监控。
///
/// **配置示例**：
///
/// ```yaml
/// linqibin:
///   starter:
///     core:
///       async:
///         enabled: true
///         pools:
///           cache-upload:
///             core-size: 2
///             max-size: 4
///             queue-capacity: 50
///             thread-name-prefix: cache-upload-
///           data-sync:
///             core-size: 4
///             max-size: 8
///             queue-capacity: 200
///             thread-name-prefix: data-sync-
/// ```
///
/// **使用方式**：
///
/// ```java
/// @Service
/// public class MyService {
///   private final AsyncExecutorRegistry asyncExecutorRegistry;
///
///   public void doAsync() {
///     CompletableFuture.runAsync(
///         () -> { ... },
///         asyncExecutorRegistry.getExecutor("cache-upload")
///     );
///   }
/// }
/// ```
///
/// **启用条件**：
///
/// - `linqibin.starter.core.async.enabled=true`（默认启用）
///
/// @author linqibin
/// @since 0.1.0
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "linqibin.starter.core.async",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(AsyncProperties.class)
public class AsyncAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(AsyncAutoConfiguration.class);

  /// 创建异步执行器注册表 Bean。
  ///
  /// 根据配置自动注册所有命名线程池，并集成 Micrometer 指标监控。
  ///
  /// @param properties 异步配置属性
  /// @param meterRegistryProvider Micrometer 注册表提供者（可选）
  /// @return 异步执行器注册表
  @Bean
  public AsyncExecutorRegistry asyncExecutorRegistry(
      AsyncProperties properties, ObjectProvider<MeterRegistry> meterRegistryProvider) {

    MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
    AsyncExecutorRegistry registry = new AsyncExecutorRegistry(meterRegistry);

    // 注册配置的线程池
    properties.getPools().forEach(registry::register);

    if (properties.getPools().isEmpty()) {
      log.info("未配置任何异步线程池。如需使用，请在 linqibin.starter.core.async.pools 下配置");
    } else {
      log.info("异步线程池自动配置完成，共注册 {} 个线程池", properties.getPools().size());
    }

    return registry;
  }
}
