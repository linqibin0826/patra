package dev.patra.identity.app.usecase.username;

import dev.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.model.aggregate.FormerUsername;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.vo.UserFieldViolations;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.domain.policy.UsernameChangePolicy;
import dev.patra.identity.domain.policy.UsernamePolicy;
import dev.patra.identity.domain.port.repository.FormerUsernameRepository;
import dev.patra.identity.domain.port.repository.UserRepository;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.security.AuthenticationRequiredException;
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

/// 当前用户改用户名。
///
/// 顺序：校验新名（语法、保留字）→ 加行锁读用户（同一用户的两次并发改名串行）→ 聚合判状态、
/// 是否和当前相同、冷却 → 查占用（`idn_user` 里有人用，或别人的旧名未到期）→ 保存用户 → 旧名记录保留 90 天。
/// 并发撞上用户名唯一约束由仓储转成同一个 409。不做再认证，不撤销会话，不碰资料表。
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeUsernameHandler
    implements CommandHandler<ChangeUsernameCommand, ChangeUsernameResult> {

  private final CurrentUserPort currentUserPort;
  private final UsernamePolicy usernamePolicy;
  private final UserRepository users;
  private final FormerUsernameRepository formerUsernames;
  private final UsernameChangePolicy policy;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 改名。
  ///
  /// @param command 命令
  /// @return 新用户名和下次可以改名的时间
  @Override
  public ChangeUsernameResult handle(ChangeUsernameCommand command) {
    CurrentUser current = currentUserPort.require();
    Optional<FieldViolation> violation = usernamePolicy.validate(command.username());
    if (violation.isPresent()) {
      throw new InvalidUserFieldsException(List.of(violation.get()));
    }
    Username next = Username.of(command.username());
    Instant now = clock.instant();
    Username previous =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User user =
                      users
                          .findByIdForUpdate(current.userId())
                          .orElseThrow(AuthenticationRequiredException::new);
                  Username old = user.changeUsername(next, now, policy);
                  if (users.existsByUsername(next)
                      || formerUsernames.existsReservedByOther(next, user.getId(), now)) {
                    throw new UserIdentifierTakenException(
                        List.of(UserFieldViolations.usernameAlreadyTaken()));
                  }
                  users.save(user);
                  formerUsernames.save(
                      FormerUsername.reserve(user.getId(), old, now, policy.reservationPeriod()));
                  return old;
                }));
    log.info(
        "前台用户已改名: userId={}, from={}, to={}", current.userId(), previous.value(), next.value());
    return ChangeUsernameResult.of(next.value(), policy.nextChangeAt(now));
  }
}
