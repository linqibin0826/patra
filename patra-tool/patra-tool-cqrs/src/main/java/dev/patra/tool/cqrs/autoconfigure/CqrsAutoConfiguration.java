package dev.patra.tool.cqrs.autoconfigure;

import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.cqrs.CommandHandler;
import dev.patra.tool.cqrs.CommandInterceptor;
import dev.patra.tool.cqrs.SimpleCommandBus;
import dev.patra.tool.cqrs.interceptor.LoggingCommandInterceptor;
import dev.patra.tool.cqrs.interceptor.MetricsCommandInterceptor;
import dev.patra.tool.cqrs.interceptor.TracingCommandInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/// 命令总线自动配置：`SimpleCommandBus`、异步执行器与三个内置拦截器。
///
/// 排在 Boot 的任务执行器之后，否则这里的 `Executor` bean 会让 `applicationTaskExecutor` 退让；
/// 排在可观测性之后，`ObservationRegistry` / `MeterRegistry` 存在与否才判得准：`MeterRegistry` bean 由
/// `CompositeMeterRegistryAutoConfiguration` 建（它本身排在 `MetricsAutoConfiguration` 之后），只排在后者之后
/// 评估 `@ConditionalOnBean(MeterRegistry)` 时还没有 bean。
@AutoConfiguration(
    after = TaskExecutionAutoConfiguration.class,
    afterName = {
      "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration",
      "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
      "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
      "dev.patra.tool.observability.autoconfigure.ObservabilityAutoConfiguration"
    })
@ConditionalOnProperty(
    prefix = "patra.cqrs",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(CqrsProperties.class)
public final class CqrsAutoConfiguration {

  /// 命令总线，收集容器里全部 handler 与拦截器。
  ///
  /// @param handlers 容器里的命令处理器
  /// @param interceptors 容器里的命令拦截器
  /// @param commandBusAsyncExecutor `handleAsync` 用的执行器
  /// @return 命令总线
  @Bean
  @ConditionalOnMissingBean
  CommandBus commandBus(
      List<CommandHandler<?, ?>> handlers,
      List<CommandInterceptor> interceptors,
      @Qualifier("commandBusAsyncExecutor") Executor commandBusAsyncExecutor) {
    return new SimpleCommandBus(handlers, interceptors, commandBusAsyncExecutor);
  }

  /// `handleAsync` 用的线程池。
  ///
  /// @param properties 命令总线配置
  /// @return 线程池执行器
  @Bean
  @ConditionalOnMissingBean(name = "commandBusAsyncExecutor")
  Executor commandBusAsyncExecutor(CqrsProperties properties) {
    CqrsProperties.Async async = properties.async();
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(async.corePoolSize());
    executor.setMaxPoolSize(async.maxPoolSize());
    executor.setQueueCapacity(async.queueCapacity());
    executor.setThreadNamePrefix(async.threadNamePrefix());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }

  /// 日志拦截器，无外部依赖。
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(
      prefix = "patra.cqrs.interceptors",
      name = "logging",
      havingValue = "true",
      matchIfMissing = true)
  static class LoggingInterceptorConfiguration {

    /// 注册日志拦截器。
    ///
    /// @return 日志拦截器
    @Bean
    @ConditionalOnMissingBean
    LoggingCommandInterceptor loggingCommandInterceptor() {
      return new LoggingCommandInterceptor();
    }
  }

  /// 追踪拦截器，要有 Micrometer Observation 且容器里已有 `ObservationRegistry`。
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(ObservationRegistry.class)
  @ConditionalOnBean(ObservationRegistry.class)
  @ConditionalOnProperty(
      prefix = "patra.cqrs.interceptors",
      name = "tracing",
      havingValue = "true",
      matchIfMissing = true)
  static class TracingInterceptorConfiguration {

    /// 注册追踪拦截器。
    ///
    /// @param observationRegistry 观测注册表
    /// @return 追踪拦截器
    @Bean
    @ConditionalOnMissingBean
    TracingCommandInterceptor tracingCommandInterceptor(ObservationRegistry observationRegistry) {
      return new TracingCommandInterceptor(observationRegistry);
    }
  }

  /// 指标拦截器，要有 Micrometer Core 且容器里已有 `MeterRegistry`。
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(MeterRegistry.class)
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnProperty(
      prefix = "patra.cqrs.interceptors",
      name = "metrics",
      havingValue = "true",
      matchIfMissing = true)
  static class MetricsInterceptorConfiguration {

    /// 注册指标拦截器。
    ///
    /// @param meterRegistry 指标注册表
    /// @return 指标拦截器
    @Bean
    @ConditionalOnMissingBean
    MetricsCommandInterceptor metricsCommandInterceptor(MeterRegistry meterRegistry) {
      return new MetricsCommandInterceptor(meterRegistry);
    }
  }
}
