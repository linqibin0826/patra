package com.patra.starter.core.async;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/// 异步执行器注册表。
///
/// 管理所有命名线程池的创建、获取和生命周期。
/// 支持 Micrometer 指标监控集成。
///
/// **功能特性**：
///
/// - 命名线程池管理：按业务场景创建独立的线程池
/// - Micrometer 集成：自动注册线程池指标（pool.size、active、queued 等）
/// - 优雅关闭：实现 {@link DisposableBean}，确保所有线程池正确关闭
/// - 线程安全：使用 {@link ConcurrentHashMap} 存储线程池
///
/// **指标**：
///
/// 每个线程池自动注册以下指标（通过 {@link ExecutorServiceMetrics}）：
///
/// - `executor.pool.size` - 当前池大小
/// - `executor.pool.core` - 核心线程数
/// - `executor.pool.max` - 最大线程数
/// - `executor.active` - 活跃线程数
/// - `executor.queued` - 队列中等待的任务数
/// - `executor.completed` - 已完成任务数
///
/// 标签：`name={poolName}`
///
/// @author linqibin
/// @since 0.1.0
public class AsyncExecutorRegistry implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(AsyncExecutorRegistry.class);

  /// 线程池存储映射。
  private final Map<String, ThreadPoolTaskExecutor> executors = new ConcurrentHashMap<>();

  /// Micrometer 指标注册表（可选）。
  private final MeterRegistry meterRegistry;

  /// 构造异步执行器注册表。
  ///
  /// @param meterRegistry Micrometer 指标注册表（可为 null）
  public AsyncExecutorRegistry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /// 获取指定名称的执行器。
  ///
  /// @param poolName 线程池名称
  /// @return 线程池执行器
  /// @throws IllegalArgumentException 如果线程池不存在
  public Executor getExecutor(String poolName) {
    ThreadPoolTaskExecutor executor = executors.get(poolName);
    if (executor == null) {
      throw new IllegalArgumentException(
          String.format("线程池 '%s' 不存在。可用线程池：%s", poolName, executors.keySet()));
    }
    return executor;
  }

  /// 检查指定名称的线程池是否存在。
  ///
  /// @param poolName 线程池名称
  /// @return 如果存在返回 true
  public boolean hasExecutor(String poolName) {
    return executors.containsKey(poolName);
  }

  /// 获取所有已注册的线程池名称。
  ///
  /// @return 不可变的线程池名称集合
  public Set<String> getPoolNames() {
    return Collections.unmodifiableSet(executors.keySet());
  }

  /// 注册新的线程池。
  ///
  /// 如果线程池已存在，将记录警告并跳过注册。
  ///
  /// @param poolName 线程池名称
  /// @param properties 线程池配置属性
  public void register(String poolName, AsyncPoolProperties properties) {
    if (executors.containsKey(poolName)) {
      log.warn("线程池 '{}' 已存在，跳过注册", poolName);
      return;
    }

    ThreadPoolTaskExecutor executor = createExecutor(poolName, properties);
    executor.initialize();

    // 注册 Micrometer 指标
    if (meterRegistry != null) {
      new ExecutorServiceMetrics(
              executor.getThreadPoolExecutor(), poolName, Collections.emptyList())
          .bindTo(meterRegistry);
      log.debug("已注册线程池 '{}' 的 Micrometer 指标", poolName);
    }

    executors.put(poolName, executor);
    log.info(
        "注册线程池 '{}' [core={}, max={}, queue={}]",
        poolName,
        properties.getCoreSize(),
        properties.getMaxSize(),
        properties.getQueueCapacity());
  }

  /// 创建线程池执行器。
  ///
  /// @param poolName 线程池名称
  /// @param properties 线程池配置属性
  /// @return 线程池执行器
  private ThreadPoolTaskExecutor createExecutor(String poolName, AsyncPoolProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.getCoreSize());
    executor.setMaxPoolSize(properties.getMaxSize());
    executor.setQueueCapacity(properties.getQueueCapacity());
    executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());

    // 线程名前缀
    String threadNamePrefix = properties.getThreadNamePrefix();
    if (threadNamePrefix == null || threadNamePrefix.isEmpty()) {
      threadNamePrefix = "async-" + poolName + "-";
    }
    executor.setThreadNamePrefix(threadNamePrefix);

    // 优雅关闭配置
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);

    return executor;
  }

  /// 优雅关闭所有线程池。
  ///
  /// 在 Spring 容器关闭时自动调用。
  @Override
  public void destroy() {
    log.info("开始关闭所有异步线程池，数量：{}", executors.size());
    executors.forEach(
        (name, executor) -> {
          try {
            executor.shutdown();
            log.info("线程池 '{}' 已关闭", name);
          } catch (Exception e) {
            log.warn("关闭线程池 '{}' 失败", name, e);
          }
        });
    executors.clear();
  }
}
