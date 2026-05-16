package dev.linqibin.starter.core.cqrs.interceptor;

import dev.linqibin.commons.cqrs.Command;
import dev.linqibin.commons.cqrs.CommandInterceptor;
import dev.linqibin.commons.cqrs.CommandInterceptor.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/// 日志记录拦截器。
///
/// 记录命令的开始、完成和失败信息，包括执行耗时。
///
/// ## 日志输出示例
///
/// ```
/// INFO  >>> 执行命令: CreateUserCommand
/// INFO  <<< 命令完成: CreateUserCommand (42ms)
/// ERROR <<< 命令失败: CreateUserCommand (15ms) - User already exists
/// ```
///
/// ## 启用/禁用
///
/// 通过配置 `patra.command-bus.interceptors.logging=false` 禁用。
@Component
@Order(100)
@ConditionalOnProperty(
    prefix = "patra.command-bus.interceptors",
    name = "logging",
    havingValue = "true",
    matchIfMissing = true)
public class LoggingCommandInterceptor implements CommandInterceptor {

  private static final Logger log = LoggerFactory.getLogger(LoggingCommandInterceptor.class);

  @Override
  public <R> R intercept(Command<R> command, CommandExecutor<R> next) {
    String cmdName = command.getClass().getSimpleName();
    log.info(">>> 执行命令: {}", cmdName);

    long startTime = System.currentTimeMillis();
    try {
      R result = next.execute(command);
      long duration = System.currentTimeMillis() - startTime;
      log.info("<<< 命令完成: {} ({}ms)", cmdName, duration);
      return result;
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      log.error("<<< 命令失败: {} ({}ms) - {}", cmdName, duration, e.getMessage());
      throw e;
    }
  }
}
