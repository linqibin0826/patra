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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 发注销到期前提醒（PAP-100 设计第 10 节）：按 ID 游标分批扫「冷静期、没提醒过、到期在提前量之内」的用户，
/// 每个用户一个事务、加锁复核后记下提醒时间，并在同一事务里登记「将在 N 天后删除」（PAP-104）。标记与邮件
/// 同成同败：登记失败时标记随事务回滚，这个用户计入失败、下次运行重试；提交了就一定会发、不会重复。
/// 单个用户失败不影响其他。
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
          if (remindOne(id, now, lead)) {
            sent++;
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

  /// 在一个事务里给一个用户记下提醒时间并登记提醒邮件。锁内复核不需要提醒就跳过。
  ///
  /// @param id 用户 ID
  /// @param now 本轮的当前时间
  /// @param lead 提前量
  /// @return 登记了提醒为 `true`；跳过时为 `false`
  private boolean remindOne(long id, Instant now, Duration lead) {
    return Boolean.TRUE.equals(
        transactions.execute(
            status -> {
              Optional<User> found = users.findByIdForUpdate(id);
              if (found.isEmpty() || !found.get().needsDeletionReminder(now, lead)) {
                return false;
              }
              User user = found.get();
              user.markDeletionReminded(now);
              users.save(user);
              String displayName =
                  userReadPort
                      .findAccount(id)
                      .map(UserAccountReadModel::displayName)
                      .orElse(user.getUsername().value());
              emailDelivery.send(
                  AccountDeletionReminderMail.builder()
                      .to(user.getEmail().orElseThrow())
                      .displayName(displayName)
                      .deletionDueAt(user.getDeletionDueAt())
                      .daysLeft(lead.toDays())
                      .build());
              return true;
            }));
  }
}
