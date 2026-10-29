package dev.patra.identity.app.account.usecase.email;

import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 用邮件里的令牌验证邮箱：消费令牌、写验证时间、撤销该用户此前的其他会话。
///
/// 顺序：空或格式不对直接 422（不查库）→ 按哈希无锁查令牌（不存在、用途不符 422）→ 锁用户行
/// （和重发、封禁同一个加锁顺序，不会死锁）→ 条件消费（这期间过期、被重发作废、被抢先消费 422）
/// → 令牌的邮箱必须等于用户当前邮箱 → 写验证时间 → 撤销会话。
///
/// 验证的人证明了对邮箱的控制，验证前建立的会话一律不再信任：当前请求带着令牌所属用户自己的会话时
/// 保留这一条，其余全删；匿名或登录成别人时删该用户的全部。被删会话的登录记录标 `EMAIL_VERIFIED`。
/// 这条路由在网关上是公开的，所以这里用 `current()` 不用 `require()`。
/// 四种不可用一律同一个 422 `token` / `INVALID`，不给出任何能用来探测的信号。
@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyEmailHandler implements CommandHandler<VerifyEmailCommand, EmailVerified> {

  private final CurrentUserPort currentUserPort;
  private final EmailTokenRepository tokens;
  private final UserRepository users;
  private final SessionTerminator sessionTerminator;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 验证。
  ///
  /// @param command 命令
  /// @return 验证的邮箱和验证时间
  @Override
  public EmailVerified handle(VerifyEmailCommand command) {
    if (command.token() == null || command.token().isBlank()) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.tokenRequired()));
    }
    EmailTokenSecret secret = EmailTokenSecret.parse(command.token()).orElseThrow(() -> invalid());
    String hash = secret.hash();
    EmailToken found = tokens.findByTokenHash(hash).orElseThrow(() -> invalid());
    if (found.getPurpose() != EmailTokenPurpose.EMAIL_VERIFY) {
      throw invalid();
    }
    long userId = found.getUserId();
    Instant now = clock.instant();
    Optional<CurrentUser> current = currentUserPort.current();
    // 撤销会话留在锁住用户行的事务里（transaction.md 第 2 节的例外）
    return Objects.requireNonNull(
        transactions.execute(
            status -> {
              User user = users.findByIdForUpdate(userId).orElseThrow(() -> invalid());
              EmailToken consumed =
                  tokens
                      .consume(hash, EmailTokenPurpose.EMAIL_VERIFY, now)
                      .orElseThrow(() -> invalid());
              if (!user.getEmail().map(consumed.getEmail()::equals).orElse(false)) {
                throw invalid();
              }
              user.markEmailVerified(now);
              User saved = users.save(user);
              List<Long> revoked =
                  current
                      .filter(requester -> requester.userId() == userId)
                      .map(
                          self ->
                              sessionTerminator.terminateAllExcept(
                                  userId, self.sessionId(), LoginEndReason.EMAIL_VERIFIED, now))
                      .orElseGet(
                          () ->
                              sessionTerminator.terminateAll(
                                  userId, LoginEndReason.EMAIL_VERIFIED, now));
              log.info(
                  "邮箱已验证: userId={}, tokenId={}, revokedSessions={}",
                  userId,
                  consumed.getId(),
                  revoked.size());
              return EmailVerified.of(consumed.getEmail().value(), saved.getEmailVerifiedAt());
            }));
  }

  /// 令牌不可用：不区分原因。
  ///
  /// @return 422 的异常
  private static InvalidUserFieldsException invalid() {
    return new InvalidUserFieldsException(List.of(UserFieldViolations.tokenInvalid()));
  }
}
