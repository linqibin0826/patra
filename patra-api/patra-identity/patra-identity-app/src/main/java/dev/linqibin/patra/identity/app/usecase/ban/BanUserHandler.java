package dev.linqibin.patra.identity.app.usecase.ban;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.patra.identity.domain.exception.UserNotFoundException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// 封禁前台用户。幂等：已经封禁时保留原来的封禁时间。
///
/// 封禁生效后要删掉该用户的全部会话、结束对应的登录记录，这一步由 PAP-64 加在保存之后。
@Slf4j
@Component
@RequiredArgsConstructor
public class BanUserHandler implements CommandHandler<BanUserCommand, Void> {

  private final UserRepository users;
  private final Clock clock;

  /// 封禁。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  @Transactional
  public Void handle(BanUserCommand command) {
    User user = users.findById(command.userId()).orElseThrow(UserNotFoundException::new);
    user.ban(clock.instant());
    users.save(user);
    log.info("前台用户已封禁: userId={}", command.userId());
    return null;
  }
}
