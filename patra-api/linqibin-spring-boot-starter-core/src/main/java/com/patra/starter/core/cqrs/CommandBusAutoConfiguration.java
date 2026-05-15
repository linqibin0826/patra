package com.patra.starter.core.cqrs;

import com.patra.common.cqrs.CommandBus;
import com.patra.common.cqrs.CommandHandler;
import com.patra.common.cqrs.CommandInterceptor;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/// CommandBus 自动配置类。
///
/// 自动装配 CommandBus 及其依赖组件：
/// - [SimpleCommandBus] - CommandBus 实现
/// - [ThreadPoolTaskExecutor] - 异步执行器
///
/// ## 条件装配
///
/// - 当容器中不存在 `CommandBus` Bean 时才创建
/// - 当容器中不存在 `commandBusAsyncExecutor` Bean 时才创建执行器
///
/// ## 自动收集
///
/// Spring 会自动收集所有 `CommandHandler` 和 `CommandInterceptor` Bean
/// 并注入到 SimpleCommandBus 构造器。
@AutoConfiguration
@EnableConfigurationProperties(CommandBusProperties.class)
public class CommandBusAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(CommandBusAutoConfiguration.class);

  /// 创建 CommandBus Bean。
  ///
  /// @param handlers 所有 CommandHandler Bean（可能为空）
  /// @param interceptors 所有 CommandInterceptor Bean（可能为空）
  /// @param asyncExecutor 异步执行器
  /// @return CommandBus 实例
  @Bean
  @ConditionalOnMissingBean
  public CommandBus commandBus(
      List<CommandHandler<?, ?>> handlers,
      List<CommandInterceptor> interceptors,
      Executor commandBusAsyncExecutor) {
    // 处理可能的 null 值（当没有任何 Handler/Interceptor 时）
    List<CommandHandler<?, ?>> safeHandlers = handlers != null ? handlers : List.of();
    List<CommandInterceptor> safeInterceptors = interceptors != null ? interceptors : List.of();

    log.info("创建 CommandBus: {} 个 Handler, {} 个拦截器", safeHandlers.size(), safeInterceptors.size());

    return new SimpleCommandBus(safeHandlers, safeInterceptors, commandBusAsyncExecutor);
  }

  /// 创建异步执行器 Bean。
  ///
  /// @param properties 配置属性
  /// @return 线程池执行器
  @Bean
  @ConditionalOnMissingBean(name = "commandBusAsyncExecutor")
  public Executor commandBusAsyncExecutor(CommandBusProperties properties) {
    CommandBusProperties.Async asyncConfig = properties.getAsync();

    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(asyncConfig.getCorePoolSize());
    executor.setMaxPoolSize(asyncConfig.getMaxPoolSize());
    executor.setQueueCapacity(asyncConfig.getQueueCapacity());
    executor.setThreadNamePrefix(asyncConfig.getThreadNamePrefix());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();

    log.info(
        "创建 CommandBus 异步执行器: core={}, max={}, queue={}",
        asyncConfig.getCorePoolSize(),
        asyncConfig.getMaxPoolSize(),
        asyncConfig.getQueueCapacity());

    return executor;
  }
}
