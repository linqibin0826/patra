package dev.patra.tool.test.archunit.fixtures.transaction.cqrs;

/// 夹具：与命令总线的 `CommandHandler` 同名的接口，规则按简单类名识别 Handler。
///
/// @param <C> 命令类型
/// @param <R> 结果类型
public interface CommandHandler<C, R> {

  /// 处理命令。
  ///
  /// @param command 命令
  /// @return 结果
  R handle(C command);
}
