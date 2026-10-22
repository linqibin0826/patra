package dev.patra.identity.app.usecase.password;

import dev.patra.identity.app.usecase.auth.AuthenticatedUser;
import dev.patra.identity.app.usecase.auth.CurrentPasswordVerifier;
import dev.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.model.enums.LoginEndReason;
import dev.patra.identity.domain.model.read.UserAccountReadModel;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.PasswordHash;
import dev.patra.identity.domain.model.vo.PlainPassword;
import dev.patra.identity.domain.model.vo.UserFieldViolations;
import dev.patra.identity.domain.policy.PasswordPolicy;
import dev.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.port.mail.PasswordChangedMail;
import dev.patra.identity.domain.port.mail.SecurityNotice;
import dev.patra.identity.domain.port.read.UserReadPort;
import dev.patra.identity.domain.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.port.repository.UserRepository;
import dev.patra.identity.domain.port.session.SessionStorePort;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 改密码：每次都输当前密码（不走再认证窗口）；新密码按注册规则且不能与当前相同；
/// 事务里换哈希、按用户批量作废令牌、撤销其余会话并记 `REVOKED`、最后本机换令牌（刷新 `auth_at`）；
/// 事务提交后发「密码已修改」。
///
/// 当前密码的校验和登录共用失败限制：错是 401 + `PASSWORD_MISMATCH`，锁定 429。
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
  private final UserLoginRecordRepository records;
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
    passwordVerifier.verify(email, userId, currentPassword);
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
                  credential.rotate(hash);
                  credentials.save(credential);
                  tokens.revokeAllActive(userId, now);
                  List<Long> revoked =
                      sessions.revokeAllExcept(current.accountType(), userId, current.sessionId());
                  for (Long sessionId : revoked) {
                    records
                        .findById(sessionId)
                        .ifPresent(
                            login -> {
                              login.end(LoginEndReason.REVOKED, now);
                              records.save(login);
                            });
                  }
                  UserAccountReadModel account =
                      userReadPort
                          .findAccount(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  // 换令牌放在事务最后一步：之前任何一步失败都不碰旧令牌，用户还能在原会话里重试
                  String token =
                      sessions
                          .rotate(current.accountType(), userId, current.sessionId())
                          .orElseThrow(AuthenticationRequiredException::new);
                  return new Changed(token, account, revoked.size());
                }));
    emailDelivery.send(
        PasswordChangedMail.of(
            email,
            changed.account().displayName(),
            SecurityNotice.builder()
                .occurredAt(now)
                .notMeAdvice(PasswordChangedMail.NOT_ME_ADVICE)
                .build()));
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
