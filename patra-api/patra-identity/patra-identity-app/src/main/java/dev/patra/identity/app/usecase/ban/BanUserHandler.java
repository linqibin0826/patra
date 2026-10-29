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
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

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
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 封禁。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  public Void handle(BanUserCommand command) {
    Instant now = clock.instant();
    // 撤销会话留在锁住用户行的事务里（transaction.md 第 2 节的例外）：Redis 失败则封禁回滚，并挡住并发登录
    List<Long> revoked =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User user =
                      users
                          .findByIdForUpdate(command.userId())
                          .orElseThrow(UserNotFoundException::new);
                  user.ban(now);
                  users.save(user);
                  tokens.revokeAllActive(command.userId(), now);
                  return sessionTerminator.terminateAll(
                      command.userId(), LoginEndReason.BANNED, now);
                }));
    log.info("前台用户已封禁: userId={}, revokedSessions={}", command.userId(), revoked.size());
    return null;
  }
}
