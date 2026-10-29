package dev.patra.identity.app.account.usecase.deletion;

import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionCompletedMail;
import dev.patra.identity.domain.account.port.avatar.AvatarStoragePort;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.service.SessionTerminator;
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

/// 执行到期的注销（PAP-100 设计第 9、11 节）：按 ID 游标分批扫冷静期已到期的用户，每个用户一个事务、
/// 加锁复核后匿名化——转 `DELETED`（邮箱置空、行与用户名保留）、删凭据、清资料（行保留）、删令牌、
/// 撤销冷静期内登录的会话；提交后给注销前的邮箱发「已注销」。
///
/// 单个用户失败只记日志、计数，接着处理下一个，它仍在冷静期，下次运行再试；按 ID 往后翻，不会反复撞上它。
/// 锁内复核挡住「扫描之后刚取消」与并发的两次运行，重跑幂等。「已注销」被发信执行器拒收时邮箱已经置空，
/// 没法重发，只记日志，注销本身照样算完成。头像对象在加锁事务之前按前缀删，删之前不加锁复核仍到期（PAP-97）。
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteDueAccountDeletionsHandler
    implements CommandHandler<ExecuteDueAccountDeletionsCommand, DeletionRunResult> {

  /// 一批最多扫几个用户。
  static final int BATCH_SIZE = 100;

  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final UserPasswordCredentialRepository credentials;
  private final UserProfileRepository profiles;
  private final EmailTokenRepository tokens;
  private final AvatarStoragePort avatarStorage;
  private final SessionTerminator sessionTerminator;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 执行一轮。
  ///
  /// @param command 命令
  /// @return 完成与失败的用户数
  @Override
  public DeletionRunResult handle(ExecuteDueAccountDeletionsCommand command) {
    Instant now = clock.instant();
    int completed = 0;
    int failed = 0;
    long afterId = 0L;
    List<Long> ids;
    do {
      ids = users.findIdsDueForDeletion(now, afterId, BATCH_SIZE);
      for (long id : ids) {
        afterId = id;
        try {
          deleteAvatarObjectsIfDue(id, now);
          Optional<Completed> done = completeOne(id, now);
          if (done.isPresent()) {
            notifyCompleted(id, done.get());
            completed++;
          }
        } catch (RuntimeException e) {
          log.error("注销到期执行失败: userId={}", id, e);
          failed++;
        }
      }
    } while (ids.size() >= BATCH_SIZE);
    log.info("注销到期执行完成: completed={}, failed={}", completed, failed);
    return DeletionRunResult.of(completed, failed);
  }

  /// 头像对象不参与数据库事务，在加锁事务之前按前缀删光：先删对象后提交，失败时用户还在冷静期、下次重试，
  /// 重删空前缀天然幂等。删之前不加锁复读一次：扫描之后刚取消的用户不能丢头像（剩下复读到加锁之间的毫秒级
  /// 窗口，撞上时该用户头像读取 404，重新上传即恢复）。
  ///
  /// @param id 用户 ID
  /// @param now 本轮的当前时间
  private void deleteAvatarObjectsIfDue(long id, Instant now) {
    if (users.findById(id).filter(user -> user.isDeletionDue(now)).isPresent()) {
      avatarStorage.deleteAll(id);
    }
  }

  /// 在一个事务里匿名化一个用户。锁内复核不到期（刚取消、被封禁、已完成）就跳过。
  ///
  /// @param id 用户 ID
  /// @param now 本轮的当前时间
  /// @return 完成时给「已注销」邮件用的收件人、昵称、用户名；跳过时为空
  private Optional<Completed> completeOne(long id, Instant now) {
    return Objects.requireNonNull(
        transactions.execute(
            status -> {
              Optional<User> found = users.findByIdForUpdate(id);
              if (found.isEmpty() || !found.get().isDeletionDue(now)) {
                return Optional.empty();
              }
              User user = found.get();
              EmailAddress email = user.getEmail().orElseThrow();
              String displayName =
                  userReadPort
                      .findAccount(id)
                      .map(UserAccountReadModel::displayName)
                      .orElse(user.getUsername().value());
              user.completeDeletion(now);
              users.save(user);
              credentials.deleteByUserId(id);
              profiles
                  .findByUserId(id)
                  .ifPresent(
                      profile -> {
                        profile.erase();
                        profiles.save(profile);
                      });
              tokens.deleteAllByUserId(id);
              sessionTerminator.terminateAll(id, LoginEndReason.DELETION_COMPLETED, now);
              return Optional.of(new Completed(email, displayName, user.getUsername().value()));
            }));
  }

  /// 给注销前的邮箱发「已注销」，并打日志。
  ///
  /// @param id 用户 ID
  /// @param completed 收件人、昵称、用户名
  private void notifyCompleted(long id, Completed completed) {
    boolean accepted =
        emailDelivery.send(
            AccountDeletionCompletedMail.of(
                completed.email(), completed.displayName(), completed.username()));
    if (!accepted) {
      log.warn("「已注销」邮件被发信执行器拒收: userId={}", id);
    }
    log.info("前台用户已注销: userId={}", id);
  }

  /// 匿名化之前留下的收件人、昵称和用户名。
  ///
  /// @param email 注销前的邮箱
  /// @param displayName 注销前的昵称
  /// @param username 用户名
  private record Completed(EmailAddress email, String displayName, String username) {}
}
