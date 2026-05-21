package dev.linqibin.commons.cqrs;

/// 命令标记接口。
///
/// 所有命令对象必须实现此接口，泛型参数 R 表示命令执行后的返回类型。
/// 推荐使用 Java Record 实现，确保不可变性。
///
/// ## 使用示例
///
/// ```java
/// public record CreateUserCommand(String name, String email) implements Command<UserId> {
///     public CreateUserCommand {
///         Objects.requireNonNull(name, "name must not be null");
///         Objects.requireNonNull(email, "email must not be null");
///     }
/// }
/// ```
///
/// @param <R> 命令执行结果类型
public interface Command<R> {}
