package dev.patra.identity.app.account.usecase.emailchange;

import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangedConfirmationMail;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangedNoticeMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.account.service.IssuedEmailToken;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 改邮箱的确认：新邮箱点链接后立即生效。
///
/// 事务里：按哈希查令牌（用途不符 422）→ 锁用户行（非 `ACTIVE` 422：注销中不做写操作）→ 条件消费 →
/// 新地址这一小时内被别人注册了 409（点链接的人就是新地址的主人，不算泄露）→ 改邮箱并写验证时间 →
/// 批量作废令牌（旧地址的验证、重设令牌全废）→ 签发 7 天的撤销令牌发往旧地址。事务后先发旧邮箱的通知
/// （含撤销链接、遮掩过的新地址），再发新邮箱的「已绑定」。会话不动：申请时本人刚再认证过。
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmEmailChangeHandler
    implements CommandHandler<ConfirmEmailChangeCommand, EmailChanged> {

  private final EmailTokenRepository tokens;
  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final EmailTokenIssuer emailTokenIssuer;
  private final MailLinkPolicy links;
  private final ClientOriginResolver clientOriginResolver;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 确认。
  ///
  /// @param command 命令
  /// @return 新邮箱和验证时间
  @Override
  public EmailChanged handle(ConfirmEmailChangeCommand command) {
    EmailTokenSecret secret = parse(command.token());
    Instant now = clock.instant();
    Changed changed =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  String tokenHash = secret.hash();
                  EmailToken found = tokens.findByTokenHash(tokenHash).orElseThrow(() -> invalid());
                  if (found.getPurpose() != EmailTokenPurpose.EMAIL_CHANGE_CONFIRM) {
                    throw invalid();
                  }
                  long userId = found.getUserId();
                  User user = users.findByIdForUpdate(userId).orElseThrow(() -> invalid());
                  if (!user.isActive()) {
                    throw invalid();
                  }
                  EmailToken consumed =
                      tokens
                          .consume(tokenHash, EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, now)
                          .orElseThrow(() -> invalid());
                  EmailAddress newEmail = consumed.getEmail();
                  if (users.existsByEmail(newEmail)) {
                    throw new UserIdentifierTakenException(
                        List.of(UserFieldViolations.emailAlreadyRegistered()));
                  }
                  EmailAddress oldEmail = user.getEmail().orElseThrow(() -> invalid());
                  user.changeEmail(newEmail, now);
                  users.save(user);
                  tokens.revokeAllActive(userId, now);
                  IssuedEmailToken revert =
                      emailTokenIssuer.issue(
                          EmailTokenPurpose.EMAIL_CHANGE_REVERT,
                          userId,
                          oldEmail,
                          command.clientIp());
                  UserAccountReadModel account =
                      userReadPort.findAccount(userId).orElseThrow(() -> invalid());
                  return new Changed(
                      userId, oldEmail, newEmail, account.displayName(), revert.secret());
                }));
    // 解析放在事务提交之后、发信之前：纯计算，不占事务
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    emailDelivery.send(
        EmailChangedNoticeMail.builder()
            .to(changed.oldEmail())
            .displayName(changed.displayName())
            .newEmailMasked(changed.newEmail().masked())
            .revertUrl(links.revertEmailChangeUrl(changed.revertSecret()))
            .notice(origin.toNotice(now, EmailChangedNoticeMail.NOT_ME_ADVICE))
            .build());
    emailDelivery.send(
        EmailChangedConfirmationMail.of(
            changed.newEmail(),
            changed.displayName(),
            origin.toNotice(now, EmailChangedConfirmationMail.NOT_ME_ADVICE)));
    log.info("邮箱已更改: userId={}", changed.userId());
    return EmailChanged.of(changed.newEmail().value(), now);
  }

  /// 解析令牌原文：空 422 `REQUIRED`，格式不对 422 `INVALID`，都不查库。
  ///
  /// @param raw 请求体里的令牌
  /// @return 令牌原文
  static EmailTokenSecret parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.tokenRequired()));
    }
    return EmailTokenSecret.parse(raw).orElseThrow(ConfirmEmailChangeHandler::invalid);
  }

  /// 令牌不可用：不区分原因。
  ///
  /// @return 422 的异常
  static InvalidUserFieldsException invalid() {
    return new InvalidUserFieldsException(List.of(UserFieldViolations.tokenInvalid()));
  }

  /// 事务里的结果。
  ///
  /// @param userId 用户 ID
  /// @param oldEmail 旧邮箱
  /// @param newEmail 新邮箱
  /// @param displayName 昵称
  /// @param revertSecret 撤销令牌的原文
  private record Changed(
      long userId,
      EmailAddress oldEmail,
      EmailAddress newEmail,
      String displayName,
      EmailTokenSecret revertSecret) {}
}
