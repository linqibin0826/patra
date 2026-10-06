package dev.linqibin.patra.identity.app.usecase.register;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 注册前台用户。
///
/// 顺序：两个字段一起校验（有错一次报全）→ 查邮箱是否已注册 → 哈希 → 在一个事务里存用户和凭据。
/// 哈希放在事务外：事务开始时就会占住数据库连接，而哈希可能要排队几秒。
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterUserHandler
    implements CommandHandler<RegisterUserCommand, RegisterUserResult> {

  private final UserRepository users;
  private final UserPasswordCredentialRepository credentials;
  private final PasswordHashingPort passwordHashing;
  private final PasswordPolicy passwordPolicy;
  private final TransactionOperations transactions;

  /// 注册。
  ///
  /// @param command 命令
  /// @return 新用户的 ID 和邮箱
  @Override
  public RegisterUserResult handle(RegisterUserCommand command) {
    List<FieldViolation> violations = new ArrayList<>();
    EmailAddress.validate(command.email()).ifPresent(violations::add);
    passwordPolicy.validateForRegistration(command.password()).ifPresent(violations::add);
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    EmailAddress email = EmailAddress.of(command.email());
    if (users.existsByEmail(email)) {
      throw new EmailAlreadyRegisteredException();
    }
    PasswordHash hash = passwordHashing.hash(PlainPassword.of(command.password()));
    User user =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User saved = users.save(User.register(email));
                  credentials.save(UserPasswordCredential.create(saved.getId(), hash));
                  return saved;
                }));
    log.info("前台用户已注册: userId={}", user.getId());
    return RegisterUserResult.of(user.getId(), user.getEmail().value());
  }
}
