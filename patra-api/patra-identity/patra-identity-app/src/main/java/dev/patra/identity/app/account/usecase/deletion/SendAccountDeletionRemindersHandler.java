package dev.patra.identity.app.account.usecase.deletion;

import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionReminderMail;
import dev.patra.identity.domain.account.policy.AccountDeletionPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 发注销到期前提醒（PAP-100 设计第 10 节）：按 ID 游标分批扫「冷静期、没提醒过、到期在提前量之内」的用户，
/// 每个用户一个事务、加锁复核后记下提醒时间，提交后发「将在 N 天后删除」。先记后发，不会重复。
///
/// 发信被执行器拒收时另开一个短事务把标记清回去，下次运行重发，这个用户计入失败（与新设备提醒被拒时退回冷却
/// 同一做法）。被接下之后真正发送失败只记日志，不重发。单个用户失败不影响其他。
@Slf4j
@Component
@RequiredArgsConstructor
public class SendAccountDeletionRemindersHandler
    implements CommandHandler<SendAccountDeletionRemindersCommand, ReminderRunResult> {

  /// 一批最多扫几个用户。
  static final int BATCH_SIZE = 100;

  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final AccountDeletionPolicy policy;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 执行一轮。
  ///
  /// @param command 命令
  /// @return 发出与失败的数量
  @Override
  public ReminderRunResult handle(SendAccountDeletionRemindersCommand command) {
    Instant now = clock.instant();
    Duration lead = policy.reminderLead();
    int sent = 0;
    int failed = 0;
    long afterId = 0L;
    List<Long> ids;
    do {
      ids = users.findIdsDueForDeletionReminder(now, now.plus(lead), afterId, BATCH_SIZE);
      for (long id : ids) {
        afterId = id;
        try {
          Optional<AccountDeletionReminderMail> mail = markOne(id, now, lead);
          if (mail.isEmpty()) {
            continue;
          }
          if (emailDelivery.send(mail.get())) {
            sent++;
          } else {
            log.warn("注销提醒邮件被发信执行器拒收，退回标记待下次重发: userId={}", id);
            unmark(id);
            failed++;
          }
        } catch (RuntimeException e) {
          log.error("注销提醒失败: userId={}", id, e);
          failed++;
        }
      }
    } while (ids.size() >= BATCH_SIZE);
    log.info("注销到期前提醒完成: sent={}, failed={}", sent, failed);
    return ReminderRunResult.of(sent, failed);
  }

  /// 在一个事务里给一个用户记下提醒时间，并备好邮件。锁内复核不需要提醒就跳过。
  ///
  /// @param id 用户 ID
  /// @param now 本轮的当前时间
  /// @param lead 提前量
  /// @return 要发的提醒；跳过时为空
  private Optional<AccountDeletionReminderMail> markOne(long id, Instant now, Duration lead) {
    return Objects.requireNonNull(
        transactions.execute(
            status -> {
              Optional<User> found = users.findByIdForUpdate(id);
              if (found.isEmpty() || !found.get().needsDeletionReminder(now, lead)) {
                return Optional.empty();
              }
              User user = found.get();
              user.markDeletionReminded(now);
              users.save(user);
              String displayName =
                  userReadPort
                      .findAccount(id)
                      .map(UserAccountReadModel::displayName)
                      .orElse(user.getUsername().value());
              return Optional.of(
                  AccountDeletionReminderMail.of(
                      user.getEmail().orElseThrow(),
                      displayName,
                      user.getDeletionDueAt(),
                      lead.toDays()));
            }));
  }

  /// 在另一个事务里把提醒标记清回去，让下一轮重发。
  ///
  /// @param id 用户 ID
  private void unmark(long id) {
    transactions.executeWithoutResult(
        status ->
            users
                .findByIdForUpdate(id)
                .ifPresent(
                    user -> {
                      user.clearDeletionReminder();
                      users.save(user);
                    }));
  }
}
