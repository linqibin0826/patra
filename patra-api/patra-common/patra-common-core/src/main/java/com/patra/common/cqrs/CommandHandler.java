package com.patra.common.cqrs;

/// 命令处理器接口。
///
/// 每个 Command 类型对应一个唯一的 Handler，通过泛型绑定。
/// Handler 应该是无状态的，支持并发调用。
///
/// ## 设计原则
///
/// - **单一职责**：每个 Handler 只处理一种 Command
/// - **无状态**：Handler 不应持有可变状态，保证线程安全
/// - **事务边界**：使用 `@Transactional` 管理事务（在 Spring 环境中）
///
/// ## 使用示例
///
/// ```java
/// @Component
/// public class CreateUserHandler implements CommandHandler<CreateUserCommand, UserId> {
///
///     private final UserRepository userRepository;
///
///     @Override
///     @Transactional
///     public UserId handle(CreateUserCommand command) {
///         User user = User.create(command.name(), command.email());
///         return userRepository.save(user).getId();
///     }
/// }
/// ```
///
/// @param <C> 命令类型，必须实现 `Command<R>`
/// @param <R> 返回结果类型
public interface CommandHandler<C extends Command<R>, R> {

  /// 处理命令并返回结果。
  ///
  /// @param command 要处理的命令，不可为 null
  /// @return 处理结果
  /// @throws RuntimeException 当处理失败时抛出相应异常
  R handle(C command);
}
