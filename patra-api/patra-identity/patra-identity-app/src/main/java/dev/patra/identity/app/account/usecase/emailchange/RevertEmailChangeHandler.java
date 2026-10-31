package dev.patra.identity.app.account.usecase.emailchange;

import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangeRevertedMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.account.service.IssuedEmailToken;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 改邮箱的撤销：旧邮箱点「不是我」。
///
/// 事务里：按哈希查令牌（用途不符 422）→ 锁用户行（封禁、注销 422；`PENDING_DELETION` 放行——攻击者
/// 拿到账号后申请注销是很自然的事，原主必须能撤回，撤回不碰注销状态）→ 条件消费 → 旧地址 7 天内被别人
/// 注册了 409 且事务回滚（令牌没被消费；当前邮箱已经是旧地址时不算占用）→ 改回并写验证时间 → 作废全部令牌 + 只作废比本条更晚签发的
/// 撤销令牌（更早的保留：X → Y → Z 时攻击者先点 Y 的也废不掉原主 X 的）→ 删凭据
/// （作废密码）→ 撤销全部会话并记 `EMAIL_REVERTED` → 签发重设令牌 → 登记发往改回地址的含重设链接的通知
/// （与撤销同成同败，PAP-104）。
/// 不通知被踢掉的新地址。
@Slf4j
@Component
@RequiredArgsConstructor
public class RevertEmailChangeHandler
    implements CommandHandler<RevertEmailChangeCommand, EmailChangeReverted> {

  private final EmailTokenRepository tokens;
  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final UserPasswordCredentialRepository credentials;
  private final SessionTerminator sessionTerminator;
  private final EmailTokenIssuer emailTokenIssuer;
  private final MailLinkPolicy links;
  private final ClientOriginResolver clientOriginResolver;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 撤销。
  ///
  /// @param command 命令
  /// @return 改回的旧邮箱
  @Override
  public EmailChangeReverted handle(RevertEmailChangeCommand command) {
    EmailTokenSecret secret = ConfirmEmailChangeHandler.parse(command.token());
    // 解析 UA 与属地是纯计算：放在事务之外
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    Instant now = clock.instant();
    Reverted reverted =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  String tokenHash = secret.hash();
                  EmailToken found =
                      tokens
                          .findByTokenHash(tokenHash)
                          .orElseThrow(ConfirmEmailChangeHandler::invalid);
                  if (found.getPurpose() != EmailTokenPurpose.EMAIL_CHANGE_REVERT) {
                    throw ConfirmEmailChangeHandler.invalid();
                  }
                  long userId = found.getUserId();
                  User user =
                      users
                          .findByIdForUpdate(userId)
                          .orElseThrow(ConfirmEmailChangeHandler::invalid);
                  if (user.isBanned() || user.isDeleted()) {
                    throw ConfirmEmailChangeHandler.invalid();
                  }
                  EmailToken consumed =
                      tokens
                          .consume(tokenHash, EmailTokenPurpose.EMAIL_CHANGE_REVERT, now)
                          .orElseThrow(ConfirmEmailChangeHandler::invalid);
                  EmailAddress restore = consumed.getEmail();
                  // X → Y → X 之后点 R_X：当前邮箱已经是要改回的地址，existsByEmail 查到的是自己
                  boolean alreadyRestored = user.getEmail().filter(restore::equals).isPresent();
                  if (!alreadyRestored && users.existsByEmail(restore)) {
                    throw new UserIdentifierTakenException(
                        List.of(UserFieldViolations.emailAlreadyRegistered()));
                  }
                  user.changeEmail(restore, now);
                  users.save(user);
                  tokens.revokeAllActive(userId, now);
                  tokens.revokeRevertTokensIssuedAfter(userId, consumed.getId(), now);
                  credentials.deleteByUserId(userId);
                  List<Long> revoked =
                      sessionTerminator.terminateAll(userId, LoginEndReason.EMAIL_REVERTED, now);
                  IssuedEmailToken reset =
                      emailTokenIssuer.issue(
                          EmailTokenPurpose.PASSWORD_RESET, userId, restore, command.clientIp());
                  UserAccountReadModel account =
                      userReadPort
                          .findAccount(userId)
                          .orElseThrow(ConfirmEmailChangeHandler::invalid);
                  emailDelivery.send(
                      EmailChangeRevertedMail.builder()
                          .to(restore)
                          .displayName(account.displayName())
                          .resetUrl(links.resetPasswordUrl(reset.secret()))
                          .linkExpiresAt(reset.token().getExpiresAt())
                          .notice(origin.toNotice(now))
                          .build());
                  return new Reverted(userId, restore, revoked.size());
                }));
    log.info(
        "邮箱更改已撤销: userId={}, revokedSessions={}", reverted.userId(), reverted.revokedSessions());
    return EmailChangeReverted.of(reverted.email().value());
  }

  /// 事务里的结果。
  ///
  /// @param userId 用户 ID
  /// @param email 改回的旧邮箱
  /// @param revokedSessions 撤销了几条会话
  private record Reverted(long userId, EmailAddress email, int revokedSessions) {}
}
