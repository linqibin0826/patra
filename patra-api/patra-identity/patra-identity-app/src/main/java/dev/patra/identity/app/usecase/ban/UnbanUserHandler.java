package dev.patra.identity.app.usecase.ban;

import dev.patra.identity.domain.exception.UserNotFoundException;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.port.repository.UserRepository;
import dev.patra.tool.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 解封前台用户。幂等：已经正常时什么都不变。
@Slf4j
@Component
@RequiredArgsConstructor
public class UnbanUserHandler implements CommandHandler<UnbanUserCommand, Void> {

  private final UserRepository users;
  private final TransactionOperations transactions;

  /// 解封。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  public Void handle(UnbanUserCommand command) {
    transactions.executeWithoutResult(
        status -> {
          User user = users.findById(command.userId()).orElseThrow(UserNotFoundException::new);
          user.unban();
          users.save(user);
        });
    log.info("前台用户已解封: userId={}", command.userId());
    return null;
  }
}
