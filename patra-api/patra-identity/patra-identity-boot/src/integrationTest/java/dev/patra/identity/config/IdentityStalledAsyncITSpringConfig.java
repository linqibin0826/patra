package dev.patra.identity.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;

/// 模拟「登记了、还没外送就没了」的 identity：异步任务一律丢掉。
///
/// 事件模块的外送器是提交后的异步监听器，任务被丢掉，登记行就停在 `PUBLISHED`，和进程在外送开始前被杀掉时库里
/// 留下的一样。按名字 `taskExecutor` 注册：`@Async` 找不到唯一的 `TaskExecutor` 时按这个名字取（cqrs 模块另有
/// 一个执行器）。
@TestConfiguration(proxyBeanMethods = false)
public class IdentityStalledAsyncITSpringConfig {

  /// 什么也不执行的执行器。
  ///
  /// @return 执行器
  @Bean(name = "taskExecutor")
  public TaskExecutor taskExecutor() {
    return task -> {};
  }
}
