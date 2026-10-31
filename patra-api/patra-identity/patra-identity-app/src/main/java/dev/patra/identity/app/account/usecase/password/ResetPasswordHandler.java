package dev.patra.identity.app.account.usecase.password;

import dev.patra.identity.domain.account.exception.PasswordResetLimitedException;
import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.mail.PasswordResetDoneMail;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.identity.domain.account.port.throttle.PasswordResetThrottlePort;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 找回密码的提交：用令牌设置新密码。
///
/// 顺序：提交限流（对猜令牌的限制，格式不对也计）→ 字段（令牌为空 / 格式不对与密码不合规一次报全，
/// 令牌不被消费）→ 哈希（事务外）→
/// 事务里：按哈希查令牌（用途不符 422）→ 锁用户行（不存在、封禁、注销 422）→ 条件消费 → 令牌邮箱
/// 必须等于当前邮箱 → 换哈希（没有凭据行就新建）→ 批量作废令牌（待确认的改邮箱随之取消）→
/// 写验证时间 → 撤销全部会话并记 `PASSWORD_RESET` → 登记「密码已重设」（与重设同成同败，PAP-104）→
/// 事务后清零登录失败。不自动登录。
@Slf4j
@Component
@RequiredArgsConstructor
public class ResetPasswordHandler implements CommandHandler<ResetPasswordCommand, PasswordReset> {

  private final PasswordResetThrottlePort passwordResetThrottle;
  private final PasswordPolicy passwordPolicy;
  private final PasswordHashingPort passwordHashing;
  private final EmailTokenRepository tokens;
  private final UserRepository users;
  private final UserPasswordCredentialRepository credentials;
  private final UserReadPort userReadPort;
  private final SessionTerminator sessionTerminator;
  private final LoginThrottlePort loginThrottle;
  private final ClientOriginResolver clientOriginResolver;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 重设。
  ///
  /// @param command 命令
  /// @return 重设了密码的账号邮箱
  @Override
  public PasswordReset handle(ResetPasswordCommand command) {
    Instant now = clock.instant();
    passwordResetThrottle
        .acquireSubmit(command.clientIp(), now)
        .ifPresent(
            wait -> {
              throw new PasswordResetLimitedException(wait);
            });
    List<FieldViolation> violations = new ArrayList<>();
    Optional<EmailTokenSecret> parsed = Optional.empty();
    if (command.token() == null || command.token().isBlank()) {
      violations.add(UserFieldViolations.tokenRequired());
    } else {
      parsed = EmailTokenSecret.parse(command.token());
      if (parsed.isEmpty()) {
        violations.add(UserFieldViolations.tokenInvalid());
      }
    }
    passwordPolicy
        .validate(command.newPassword(), UserFieldViolations.NEW_PASSWORD)
        .ifPresent(violations::add);
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    EmailTokenSecret secret = parsed.orElseThrow();
    PasswordHash hash = passwordHashing.hash(PlainPassword.of(command.newPassword()));
    // 解析 UA 与属地是纯计算：放在事务之外
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    Done done =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  String tokenHash = secret.hash();
                  EmailToken found = tokens.findByTokenHash(tokenHash).orElseThrow(() -> invalid());
                  if (found.getPurpose() != EmailTokenPurpose.PASSWORD_RESET) {
                    throw invalid();
                  }
                  long userId = found.getUserId();
                  User user = users.findByIdForUpdate(userId).orElseThrow(() -> invalid());
                  if (user.isBanned() || user.isDeleted()) {
                    throw invalid();
                  }
                  EmailToken consumed =
                      tokens
                          .consume(tokenHash, EmailTokenPurpose.PASSWORD_RESET, now)
                          .orElseThrow(() -> invalid());
                  EmailAddress email = user.getEmail().orElseThrow(() -> invalid());
                  if (!email.equals(consumed.getEmail())) {
                    throw invalid();
                  }
                  UserPasswordCredential credential =
                      credentials
                          .findByUserId(userId)
                          .map(
                              existing -> {
                                existing.rotate(hash);
                                return existing;
                              })
                          .orElseGet(() -> UserPasswordCredential.create(userId, hash));
                  credentials.save(credential);
                  tokens.revokeAllActive(userId, now);
                  user.markEmailVerified(now);
                  users.save(user);
                  List<Long> revoked =
                      sessionTerminator.terminateAll(userId, LoginEndReason.PASSWORD_RESET, now);
                  UserAccountReadModel account =
                      userReadPort.findAccount(userId).orElseThrow(() -> invalid());
                  emailDelivery.send(
                      PasswordResetDoneMail.of(email, account.displayName(), origin.toNotice(now)));
                  return new Done(userId, email, revoked.size());
                }));
    // 清零登录失败与会话无关，放在提交之后（transaction.md 第 2 节）；重设已生效，清理失败只记日志
    try {
      loginThrottle.clear(AccountType.USER, done.email());
    } catch (RuntimeException e) {
      log.warn("重设密码后清零登录失败计数失败: userId={}", done.userId(), e);
    }
    log.info("密码已重设: userId={}, revokedSessions={}", done.userId(), done.revokedSessions());
    return PasswordReset.of(done.email().value());
  }

  /// 令牌不可用：不区分原因。
  ///
  /// @return 422 的异常
  private static InvalidUserFieldsException invalid() {
    return new InvalidUserFieldsException(List.of(UserFieldViolations.tokenInvalid()));
  }

  /// 事务里的结果。
  ///
  /// @param userId 用户 ID
  /// @param email 账号邮箱
  /// @param revokedSessions 撤销了几条会话
  private record Done(long userId, EmailAddress email, int revokedSessions) {}
}
