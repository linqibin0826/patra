package dev.patra.identity.app.account.usecase.deletion;

import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionRequestedMail;
import dev.patra.identity.domain.account.policy.AccountDeletionPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.identity.domain.session.service.ReauthenticationGuard;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 发起注销（PAP-100 设计第 7.1 节）：字段 → 状态守卫 → 用户名比对 → 再认证 → 事务里进冷静期、作废令牌、
/// 撤销全部会话（含本机）→ 提交后发「注销已提交」。
///
/// 用户名比对放在再认证之前：门户本来先比一遍，错了直接提示，不用先弹密码框。比对只去首尾空白、转小写，
/// 不走用户名规则：格式不合法的输入也只是「不一致」。改邮箱的撤销令牌不作废（release spec 2.1.7：冷静期内
/// 也能撤销改邮箱），`revokeAllActive` 本来就跳过它。Redis 失败整个事务回滚，503，状态不变。
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestAccountDeletionHandler
    implements CommandHandler<RequestAccountDeletionCommand, AccountDeletionRequested> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final ReauthenticationGuard reauthenticationGuard;
  private final EmailTokenRepository tokens;
  private final SessionTerminator sessionTerminator;
  private final ClientOriginResolver clientOriginResolver;
  private final EmailDeliveryPort emailDelivery;
  private final AccountDeletionPolicy policy;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 发起注销。
  ///
  /// @param command 命令
  /// @return 到期时间
  @Override
  public AccountDeletionRequested handle(RequestAccountDeletionCommand command) {
    CurrentUser current = currentUserPort.require();
    if (command.username() == null || command.username().isBlank()) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.usernameRequired()));
    }
    long userId = current.userId();
    User user = users.findById(userId).orElseThrow(AuthenticationRequiredException::new);
    user.requireActive();
    String typed = command.username().strip().toLowerCase(Locale.ROOT);
    if (!typed.equals(user.getUsername().value())) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.usernameMismatch()));
    }
    reauthenticationGuard.requireRecent(current);
    Instant now = clock.instant();
    Requested requested =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User locked =
                      users
                          .findByIdForUpdate(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  locked.requireActive();
                  locked.requestDeletion(now, policy.coolingPeriod());
                  User saved = users.save(locked);
                  tokens.revokeAllActive(userId, now);
                  List<Long> revoked =
                      sessionTerminator.terminateAll(
                          userId, LoginEndReason.DELETION_REQUESTED, now);
                  UserAccountReadModel account =
                      userReadPort
                          .findAccount(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  EmailAddress email =
                      saved.getEmail().orElseThrow(AuthenticationRequiredException::new);
                  return new Requested(
                      email, account.displayName(), saved.getDeletionDueAt(), revoked.size());
                }));
    // 解析放在事务提交之后、发信之前：纯计算，不占事务
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    boolean accepted =
        emailDelivery.send(
            AccountDeletionRequestedMail.of(
                requested.email(),
                requested.displayName(),
                requested.deletionDueAt(),
                origin.toNotice(now, AccountDeletionRequestedMail.NOT_ME_ADVICE)));
    if (!accepted) {
      log.warn("「注销已提交」邮件被发信执行器拒收: userId={}", userId);
    }
    log.info(
        "前台用户已申请注销: userId={}, deletionDueAt={}, revokedSessions={}",
        userId,
        requested.deletionDueAt(),
        requested.revokedSessions());
    return AccountDeletionRequested.of(requested.deletionDueAt());
  }

  /// 事务里一起产出的收件人、昵称、到期时间和撤销的会话数。
  ///
  /// @param email 收件人
  /// @param displayName 昵称
  /// @param deletionDueAt 到期时间
  /// @param revokedSessions 撤销了几条会话
  private record Requested(
      EmailAddress email, String displayName, Instant deletionDueAt, int revokedSessions) {}
}
