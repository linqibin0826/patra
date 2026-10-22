package dev.patra.identity.app.usecase.ban;

import dev.patra.identity.domain.exception.UserNotFoundException;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.enums.LoginEndReason;
import dev.patra.identity.domain.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.port.repository.UserRepository;
import dev.patra.identity.domain.service.SessionTerminator;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// 封禁前台用户。幂等：已经封禁时保留原来的封禁时间。
///
/// 先对用户行加锁再动令牌：与验证邮箱、重发（都先锁用户再改令牌）同一个加锁顺序，不会死锁。
/// 保存之后作废该用户的有效邮件令牌（改邮箱撤销令牌除外）、删掉全部会话，被删会话的登录记录标成 `BANNED`。Redis 失败整个事务回滚，
/// 返回 503，管理员重试。
@Slf4j
@Component
@RequiredArgsConstructor
public class BanUserHandler implements CommandHandler<BanUserCommand, Void> {

  private final UserRepository users;
  private final SessionTerminator sessionTerminator;
  private final EmailTokenRepository tokens;
  private final Clock clock;

  /// 封禁。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  @Transactional
  public Void handle(BanUserCommand command) {
    User user = users.findByIdForUpdate(command.userId()).orElseThrow(UserNotFoundException::new);
    Instant now = clock.instant();
    user.ban(now);
    users.save(user);
    tokens.revokeAllActive(command.userId(), now);
    List<Long> revoked =
        sessionTerminator.terminateAll(command.userId(), LoginEndReason.BANNED, now);
    log.info("前台用户已封禁: userId={}, revokedSessions={}", command.userId(), revoked.size());
    return null;
  }
}
