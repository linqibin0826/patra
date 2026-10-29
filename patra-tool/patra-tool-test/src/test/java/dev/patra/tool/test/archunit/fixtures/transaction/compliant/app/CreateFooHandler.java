package dev.patra.tool.test.archunit.fixtures.transaction.compliant.app;

import dev.patra.tool.test.archunit.fixtures.transaction.cqrs.CommandHandler;

/// 夹具：不带 `@Transactional` 的命令 Handler，合规。
public class CreateFooHandler implements CommandHandler<String, String> {

  /// 处理命令。
  ///
  /// @param command 命令
  /// @return 结果
  @Override
  public String handle(String command) {
    return command;
  }
}
