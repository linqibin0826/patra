package com.patra.common.cqrs;

import java.util.concurrent.CompletableFuture;

/// 命令总线接口。
///
/// 作为 Adapter 层的**唯一入口**，负责将 Command 路由到对应的 Handler。
/// 支持同步和异步两种处理模式。
///
/// ## 架构位置
///
/// ```
/// Adapter 层
///     ↓ 注入 CommandBus
/// CommandBus（自动路由）
///     ↓ 根据 Command 类型分发
/// CommandHandler（处理业务逻辑）
/// ```
///
/// ## 使用示例
///
/// ```java
/// @Component
/// public class UserController {
///     private final CommandBus commandBus;
///
///     public UserId createUser(String name, String email) {
///         return commandBus.handle(new CreateUserCommand(name, email));
///     }
///
///     public CompletableFuture<UserId> createUserAsync(String name, String email) {
///         return commandBus.handleAsync(new CreateUserCommand(name, email));
///     }
/// }
/// ```
public interface CommandBus {

  /// 同步处理命令。
  ///
  /// 将 Command 路由到对应的 Handler 执行，阻塞直到完成。
  ///
  /// @param command 要处理的命令，不可为 null
  /// @param <R> 返回结果类型
  /// @return 处理结果
  /// @throws CommandHandlerNotFoundException 当找不到对应的 Handler 时
  /// @throws RuntimeException 当 Handler 处理失败时
  <R> R handle(Command<R> command);

  /// 异步处理命令。
  ///
  /// 将 Command 提交给异步执行器处理，立即返回 Future。
  ///
  /// @param command 要处理的命令，不可为 null
  /// @param <R> 返回结果类型
  /// @return 包含处理结果的 CompletableFuture
  <R> CompletableFuture<R> handleAsync(Command<R> command);
}
