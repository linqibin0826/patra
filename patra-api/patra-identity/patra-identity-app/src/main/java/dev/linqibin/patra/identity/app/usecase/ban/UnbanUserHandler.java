package dev.linqibin.patra.identity.app.usecase.ban;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.patra.identity.domain.exception.UserNotFoundException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// 解封前台用户。幂等：已经正常时什么都不变。
@Slf4j
@Component
@RequiredArgsConstructor
public class UnbanUserHandler implements CommandHandler<UnbanUserCommand, Void> {

  private final UserRepository users;

  /// 解封。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  @Transactional
  public Void handle(UnbanUserCommand command) {
    User user = users.findById(command.userId()).orElseThrow(UserNotFoundException::new);
    user.unban();
    users.save(user);
    log.info("前台用户已解封: userId={}", command.userId());
    return null;
  }
}
