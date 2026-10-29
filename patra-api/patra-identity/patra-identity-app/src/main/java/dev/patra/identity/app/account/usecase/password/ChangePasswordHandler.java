package dev.patra.identity.app.account.usecase.password;

import dev.patra.identity.app.account.service.CurrentPasswordVerifier;
import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.domain.account.exception.CurrentPasswordMismatchException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.mail.PasswordChangedMail;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.SessionRotation;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 改密码：每次都输当前密码（不走再认证窗口）；新密码按注册规则且不能与当前相同；
/// 事务里换哈希、按用户批量作废令牌、撤销其余会话并记 `PASSWORD_CHANGED`、最后本机换令牌（刷新 `auth_at`）；
/// 事务提交后发「密码已修改」。
///
/// 当前密码的校验和登录共用失败限制：错是 401 + `PASSWORD_MISMATCH`，锁定 429。校验在锁外（Argon2 不占行锁），
/// 锁内再比一次凭据哈希还是不是校验过的那个：两次并发改密码只有先到的生效，后到的 401。
/// 新旧相同比较的是 NFKC 规范化后的原文（哈希前做的同一个规范化），不做第二次 Argon2。
/// Redis 失败整个事务回滚、503：密码没改成，用户重试。
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangePasswordHandler
    implements CommandHandler<ChangePasswordCommand, AuthenticatedUser> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final UserPasswordCredentialRepository credentials;
  private final UserReadPort userReadPort;
  private final PasswordHashingPort passwordHashing;
  private final PasswordPolicy passwordPolicy;
  private final CurrentPasswordVerifier passwordVerifier;
  private final EmailTokenRepository tokens;
  private final SessionStorePort sessions;
  private final SessionTerminator sessionTerminator;
  private final ClientOriginResolver clientOriginResolver;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 改密码。
  ///
  /// @param command 命令
  /// @return 新令牌和账号信息
  @Override
  public AuthenticatedUser handle(ChangePasswordCommand command) {
    CurrentUser current = currentUserPort.require();
    List<FieldViolation> violations = new ArrayList<>();
    PlainPassword.validate(command.currentPassword(), UserFieldViolations.CURRENT_PASSWORD)
        .ifPresent(violations::add);
    passwordPolicy
        .validate(command.newPassword(), UserFieldViolations.NEW_PASSWORD)
        .ifPresent(violations::add);
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    PlainPassword currentPassword = PlainPassword.of(command.currentPassword());
    PlainPassword newPassword = PlainPassword.of(command.newPassword());
    long userId = current.userId();
    User user = users.findById(userId).orElseThrow(AuthenticationRequiredException::new);
    user.requireActive();
    EmailAddress email = user.getEmail().orElseThrow(AuthenticationRequiredException::new);
    PasswordHash verified = passwordVerifier.verify(email, userId, currentPassword);
    if (currentPassword.normalized().equals(newPassword.normalized())) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.newPasswordUnchanged()));
    }
    PasswordHash hash = passwordHashing.hash(newPassword);
    Instant now = clock.instant();
    Changed changed =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User locked =
                      users
                          .findByIdForUpdate(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  locked.requireActive();
                  UserPasswordCredential credential =
                      credentials
                          .findByUserId(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  if (!credential.getPasswordHash().equals(verified)) {
                    // 校验和加锁之间密码已被另一次请求改掉：刚输的「当前密码」已经不对，不计失败
                    throw new CurrentPasswordMismatchException();
                  }
                  credential.rotate(hash);
                  credentials.save(credential);
                  tokens.revokeAllActive(userId, now);
                  List<Long> revoked =
                      sessionTerminator.terminateAllExcept(
                          userId, current.sessionId(), LoginEndReason.PASSWORD_CHANGED, now);
                  UserAccountReadModel account =
                      userReadPort
                          .findAccount(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  // 换令牌放在事务最后一步：之前任何一步失败都不碰旧令牌，用户还能在原会话里重试
                  // 改密码每次都换（宽限期为零）：输过当前密码就是一次再认证，旧令牌必须作废
                  String token =
                      sessions
                          .rotate(current.accountType(), userId, current.sessionId(), Duration.ZERO)
                          .flatMap(SessionRotation::newToken)
                          .orElseThrow(AuthenticationRequiredException::new);
                  return new Changed(token, account, revoked.size());
                }));
    // 解析放在事务提交之后、发信之前：纯计算，不占事务
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    emailDelivery.send(
        PasswordChangedMail.of(
            email,
            changed.account().displayName(),
            origin.toNotice(now, PasswordChangedMail.NOT_ME_ADVICE)));
    log.info(
        "前台用户已改密码: userId={}, sessionId={}, revokedSessions={}",
        userId,
        current.sessionId(),
        changed.revokedSessions());
    return AuthenticatedUser.of(changed.token(), changed.account());
  }

  /// 事务里一起产出的新令牌、账号信息和撤销的会话数。
  ///
  /// @param token 新令牌
  /// @param account 账号信息
  /// @param revokedSessions 撤销了几条其他会话
  private record Changed(String token, UserAccountReadModel account, int revokedSessions) {}
}
