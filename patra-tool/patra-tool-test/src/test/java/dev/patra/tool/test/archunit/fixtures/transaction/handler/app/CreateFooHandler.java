package dev.patra.tool.test.archunit.fixtures.transaction.handler.app;

import dev.patra.tool.test.archunit.fixtures.transaction.cqrs.CommandHandler;
import org.springframework.transaction.annotation.Transactional;

/// 夹具：在 `handle()` 上加 `@Transactional` 的命令 Handler，违规。
public class CreateFooHandler implements CommandHandler<String, String> {

  /// 处理命令。
  ///
  /// @param command 命令
  /// @return 结果
  @Override
  @Transactional
  public String handle(String command) {
    return command;
  }
}
