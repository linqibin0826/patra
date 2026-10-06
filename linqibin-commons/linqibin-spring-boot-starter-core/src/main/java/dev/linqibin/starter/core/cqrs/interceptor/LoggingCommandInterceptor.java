package dev.linqibin.starter.core.cqrs.interceptor;

import dev.linqibin.commons.cqrs.Command;
import dev.linqibin.commons.cqrs.CommandInterceptor;
import dev.linqibin.commons.cqrs.CommandInterceptor.CommandExecutor;
import dev.linqibin.commons.error.DomainException;
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
/// WARN  <<< 命令失败: CreateUserCommand (15ms) - User already exists
/// ERROR <<< 命令失败: CreateUserCommand (3ms) - Connection refused
/// ```
///
/// ## 启用/禁用
///
/// 通过配置 `linqibin.starter.core.command-bus.interceptors.logging=false` 禁用。
@Component
@Order(100)
@ConditionalOnProperty(
    prefix = "linqibin.starter.core.command-bus.interceptors",
    name = "logging",
    havingValue = "true",
    matchIfMissing = true)
public class LoggingCommandInterceptor implements CommandInterceptor {

  private static final Logger log = LoggerFactory.getLogger(LoggingCommandInterceptor.class);

  /// 执行命令，记录开始、完成和失败日志及耗时。
  ///
  /// 领域异常记 WARN，其他异常记 ERROR。
  ///
  /// @param command 命令
  /// @param next 下一个执行器
  /// @param <R> 命令返回值类型
  /// @return 命令执行结果
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
      if (e instanceof DomainException) {
        // 业务上的拒绝（如凭据错误、邮箱已注册），不是故障
        log.warn("<<< 命令失败: {} ({}ms) - {}", cmdName, duration, e.getMessage());
      } else {
        log.error("<<< 命令失败: {} ({}ms) - {}", cmdName, duration, e.getMessage());
      }
      throw e;
    }
  }
}
