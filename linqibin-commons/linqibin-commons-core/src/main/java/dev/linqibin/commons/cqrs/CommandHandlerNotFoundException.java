package dev.linqibin.commons.cqrs;

import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.commons.error.codes.CoreErrorCode;

/// 当找不到对应的 CommandHandler 时抛出。
///
/// 这通常意味着：
/// - 忘记为 Command 创建对应的 Handler
/// - Handler 没有正确注册到 Spring 容器
/// - Command 类型泛型参数配置错误
///
/// @see CoreErrorCode#COMMAND_HANDLER_NOT_FOUND
public class CommandHandlerNotFoundException extends ApplicationException {

  private final Class<?> commandType;

  /// 构造异常。
  ///
  /// @param commandType 找不到 Handler 的 Command 类型
  public CommandHandlerNotFoundException(Class<?> commandType) {
    super(
        CoreErrorCode.COMMAND_HANDLER_NOT_FOUND,
        "No handler found for command: " + commandType.getName());
    this.commandType = commandType;
  }

  /// 获取找不到 Handler 的 Command 类型。
  ///
  /// @return Command 类型
  public Class<?> getCommandType() {
    return commandType;
  }
}
