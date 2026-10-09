package dev.linqibin.patra.identity.app.usecase.ban;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.UserNotFoundException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.domain.port.session.SessionStorePort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// 封禁前台用户。幂等：已经封禁时保留原来的封禁时间。
///
/// 保存之后删掉该用户的全部会话，被删会话的登录记录标成 `BANNED`。Redis 失败整个事务回滚，
/// 返回 503，管理员重试。
@Slf4j
@Component
@RequiredArgsConstructor
public class BanUserHandler implements CommandHandler<BanUserCommand, Void> {

  private final UserRepository users;
  private final SessionStorePort sessions;
  private final UserLoginRecordRepository records;
  private final Clock clock;

  /// 封禁。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  @Transactional
  public Void handle(BanUserCommand command) {
    User user = users.findById(command.userId()).orElseThrow(UserNotFoundException::new);
    Instant now = clock.instant();
    user.ban(now);
    users.save(user);
    List<Long> revoked = sessions.revokeAll(AccountType.USER, command.userId());
    for (Long sessionId : revoked) {
      records
          .findById(sessionId)
          .ifPresent(
              login -> {
                login.end(LoginEndReason.BANNED, now);
                records.save(login);
              });
    }
    log.info("前台用户已封禁: userId={}, revokedSessions={}", command.userId(), revoked.size());
    return null;
  }
}
