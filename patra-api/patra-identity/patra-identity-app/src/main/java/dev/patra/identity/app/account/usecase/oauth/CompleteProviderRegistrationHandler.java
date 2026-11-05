package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.app.account.usecase.query.PendingRegistrationQueryService;
import dev.patra.identity.domain.account.exception.ProviderAccountTakenException;
import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.CodeCheck;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.RegistrationCode;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.policy.ProviderRegistrationPolicy;
import dev.patra.identity.domain.account.port.oauth.PendingRegistrationPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import dev.patra.identity.domain.account.port.repository.FormerUsernameRepository;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.identity.domain.session.service.SessionIssuer;
import dev.patra.tool.core.error.field.FieldViolation;
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

/// 第三方首次注册：验证码通过才建账号（v0.19 决策 D）。这是防预劫持的关键：验证通过前账号不存在，老王拿自己的
/// GitHub 填小陈的邮箱，只会把验证码发到小陈的信箱，什么都建不出来。
///
/// 顺序：字段（422）→ 凭证（422）→ 校验验证码（Redis，原子，对了就用掉）→ 事务里再查占用（发码之后可能被别人抢了）、
/// 建用户（邮箱记为已验证）、空资料、外部身份、不带密码证明的会话 → 提交后删待补填记录。
///
/// 不建密码凭据，不签 `EMAIL_VERIFY` 令牌：邮箱刚证明过。
/// 事务里撞上任何占用都整个回滚，验证码已经用掉，用户回到表单改了再发一次。
@Slf4j
@Component
@RequiredArgsConstructor
public class CompleteProviderRegistrationHandler
    implements CommandHandler<CompleteProviderRegistrationCommand, AuthenticatedUser> {

  private final PendingRegistrationQueryService pendingQueries;
  private final PendingRegistrationPort pendingRegistrations;
  private final ProviderRegistrationPolicy policy;
  private final UserRepository users;
  private final UserProfileRepository profiles;
  private final FormerUsernameRepository formerUsernames;
  private final ExternalIdentityRepository identities;
  private final UserReadPort userReadPort;
  private final SessionIssuer sessionIssuer;
  private final ClientOriginResolver clientOriginResolver;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 建账号并发会话。
  ///
  /// @param command 命令
  /// @return 会话令牌和新用户的账号信息
  @Override
  public AuthenticatedUser handle(CompleteProviderRegistrationCommand command) {
    List<FieldViolation> violations = new ArrayList<>();
    Optional<RegistrationCode> parsed = parseCode(command.code(), violations);
    violations.addAll(LoginClient.validate(command.clientType(), command.deviceId()));
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    LoginClient client = LoginClient.of(command.clientType(), command.deviceId());
    PendingRegistration registration = pendingQueries.require(command.ticket());
    CodeCheck.Matched matched =
        verified(
            pendingRegistrations.verifyCode(
                command.ticket(), parsed.orElseThrow(), policy.maxCodeAttempts()));
    EmailAddress email = EmailAddress.of(matched.email());
    Username username = Username.of(matched.username());
    // 解析 UA 与属地是纯计算：放在事务之外
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    Instant now = clock.instant();
    AuthenticatedUser authenticated =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  requireFree(email, username, now);
                  User created = User.register(email, username);
                  created.markEmailVerified(now);
                  User saved = users.save(created);
                  profiles.save(UserProfile.create(saved.getId()));
                  boolean linked =
                      identities.insertIfAbsent(
                          ExternalIdentity.link(
                              saved.getId(),
                              registration.provider(),
                              registration.subject(),
                              registration.accountLabel(),
                              now));
                  if (!linked) {
                    throw new ProviderAccountTakenException();
                  }
                  IssuedUserSession session =
                      sessionIssuer.issueWithoutPasswordProof(saved.getId(), client, origin);
                  UserAccountReadModel account =
                      userReadPort
                          .findAccount(saved.getId())
                          .orElseThrow(
                              () -> new IllegalStateException("刚保存的用户读不回来: " + saved.getId()));
                  return AuthenticatedUser.of(session.token(), account);
                }));
    deletePendingQuietly(command.ticket());
    log.info(
        "前台用户已用第三方账号注册: userId={}, provider={}",
        authenticated.account().userId(),
        registration.provider());
    return authenticated;
  }

  /// 解析验证码。
  ///
  /// @param rawCode 原始输入
  /// @param violations 字段错误，不合法时往里加
  /// @return 验证码；不合法时为空
  private static Optional<RegistrationCode> parseCode(
      String rawCode, List<FieldViolation> violations) {
    if (rawCode == null || rawCode.isBlank()) {
      violations.add(UserFieldViolations.codeRequired());
      return Optional.empty();
    }
    Optional<RegistrationCode> parsed = RegistrationCode.parse(rawCode);
    if (parsed.isEmpty()) {
      violations.add(UserFieldViolations.codeInvalidFormat());
    }
    return parsed;
  }

  /// 校验结果转成通过的那一种，其余抛字段错误。
  ///
  /// @param check 校验结果
  /// @return 通过的结果
  private static CodeCheck.Matched verified(CodeCheck check) {
    return switch (check) {
      case CodeCheck.Matched matched -> matched;
      case CodeCheck.Mismatch _ ->
          throw new InvalidUserFieldsException(List.of(UserFieldViolations.codeInvalid()));
      case CodeCheck.NoActiveCode _ ->
          throw new InvalidUserFieldsException(List.of(UserFieldViolations.codeExpired()));
    };
  }

  /// 再查一次占用：发码之后邮箱或用户名可能被别人抢了。并发撞唯一约束由仓储转成同一个异常。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @param now 当前时间
  private void requireFree(EmailAddress email, Username username, Instant now) {
    List<FieldViolation> taken = new ArrayList<>();
    if (users.existsByEmail(email)) {
      taken.add(UserFieldViolations.emailAlreadyRegistered());
    }
    if (users.existsByUsername(username) || formerUsernames.existsReserved(username, now)) {
      taken.add(UserFieldViolations.usernameAlreadyTaken());
    }
    if (!taken.isEmpty()) {
      throw new UserIdentifierTakenException(taken);
    }
  }

  /// 删待补填记录。失败只记日志：账号已经建好，记录会自己过期，重复注册由唯一约束挡住。
  ///
  /// @param ticket 凭证原文
  private void deletePendingQuietly(String ticket) {
    try {
      pendingRegistrations.delete(ticket);
    } catch (RuntimeException e) {
      log.warn("注册完成后删待补填记录失败，等它自己过期: {}", e.getMessage());
    }
  }
}
