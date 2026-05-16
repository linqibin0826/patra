package dev.linqibin.commons.cqrs;

/// 命令拦截器接口。
///
/// 实现横切关注点（日志、指标、追踪、事务等），形成责任链模式。
/// 拦截器按优先级顺序执行，外层拦截器先进后出。
///
/// ## 执行顺序
///
/// 假设有两个拦截器 A（Order=1）和 B（Order=2）：
///
/// ```
/// A.before → B.before → Handler.handle → B.after → A.after
/// ```
///
/// ## 使用示例
///
/// ```java
/// @Component
/// @Order(100)
/// public class LoggingInterceptor implements CommandInterceptor {
///
///     @Override
///     public <R> R intercept(Command<R> command, CommandExecutor<R> next) {
///         log.info(">>> 执行命令: {}", command.getClass().getSimpleName());
///         try {
///             R result = next.execute(command);
///             log.info("<<< 命令完成");
///             return result;
///         } catch (Exception e) {
///             log.error("<<< 命令失败: {}", e.getMessage());
///             throw e;
///         }
///     }
/// }
/// ```
public interface CommandInterceptor {

  /// 拦截命令执行。
  ///
  /// @param command 要处理的命令
  /// @param next 下一个处理环节（可能是下一个拦截器或最终 Handler）
  /// @param <R> 返回结果类型
  /// @return 处理结果
  <R> R intercept(Command<R> command, CommandExecutor<R> next);

  /// 命令执行器函数式接口，用于传递执行链。
  @FunctionalInterface
  interface CommandExecutor<R> {

    /// 执行命令。
    ///
    /// @param command 要执行的命令
    /// @return 执行结果
    R execute(Command<R> command);
  }
}
