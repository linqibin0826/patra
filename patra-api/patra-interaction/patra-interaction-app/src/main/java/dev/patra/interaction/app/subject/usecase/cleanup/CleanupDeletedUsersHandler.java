package dev.patra.interaction.app.subject.usecase.cleanup;

import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.port.gateway.UserDataCleanupGateway;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 注销清理的公共流程（决策 H）：按用户 ID 升序，每轮把各子域在游标之后有数据的用户并起来，取最小的
/// {@value #BATCH} 个，调一次 identity（访客为空）；只对 `DELETED` 的人，逐个子域各开一个事务删除。identity 省略的
/// 不删（省略不等于已注销），记 WARN；identity 不可用跳过这一批、计数，继续下一批，次日重跑。
///
/// 取最小的一批是对的：某个子域在游标之后的用户 u 若不在它返回的列表里，那列表里就是 {@value #BATCH} 个比 u 小的
/// 用户，它们都在并集里，于是这一批的最大值也比 u 小，u 留到下一轮。
///
/// 不加任务级锁：调度中心「单机串行」保证同一时间只有一个实例在跑。子域抛出的意外异常不吞，让任务失败、标红。
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupDeletedUsersHandler
    implements CommandHandler<CleanupDeletedUsersCommand, CleanupResult> {

  /// 一批的人数，等于 identity 批量接口的上限。
  static final int BATCH = UserDirectoryPort.MAX_USERS;

  private final List<UserDataCleanupGateway> participants;
  private final UserDirectoryPort users;
  private final TransactionOperations transactions;

  /// 清理。
  ///
  /// @param command 命令
  /// @return 计数
  @Override
  public CleanupResult handle(CleanupDeletedUsersCommand command) {
    int scanned = 0;
    int deletedUsers = 0;
    int deletedRows = 0;
    int failedBatches = 0;
    Map<String, Integer> rowsBySubdomain = new TreeMap<>();
    long cursor = 0L;
    List<Long> batch;
    while (!(batch = nextBatch(cursor)).isEmpty()) {
      long after = cursor;
      cursor = batch.getLast();
      scanned += batch.size();
      Map<Long, UserCard> cards;
      try {
        cards = users.findCards(batch, null);
      } catch (UserDirectoryUnavailableException ex) {
        failedBatches++;
        log.warn("注销清理跳过一批: afterUserId={}, users={}", after, batch.size());
        continue;
      }
      for (Long userId : batch) {
        UserCard card = cards.get(userId);
        if (card == null) {
          log.warn("注销清理: identity 不认识这个用户，不删: userId={}", userId);
          continue;
        }
        if (card.state() != AccountState.DELETED) {
          continue;
        }
        for (UserDataCleanupGateway participant : participants) {
          int rows =
              Objects.requireNonNull(
                  transactions.execute(status -> participant.deleteAllOf(userId)));
          deletedRows += rows;
          rowsBySubdomain.merge(participant.subdomain(), rows, Integer::sum);
        }
        deletedUsers++;
      }
    }
    log.info(
        "注销清理完成: scannedUsers={}, deletedUsers={}, deletedRows={}, bySubdomain={},"
            + " failedBatches={}",
        scanned,
        deletedUsers,
        deletedRows,
        rowsBySubdomain,
        failedBatches);
    return CleanupResult.of(scanned, deletedUsers, deletedRows, failedBatches);
  }

  /// 各子域在游标之后有数据的用户，并起来取最小的一批。
  ///
  /// @param cursor 游标（不含）
  /// @return 升序的一批，最多 {@value #BATCH} 个
  private List<Long> nextBatch(long cursor) {
    TreeSet<Long> merged = new TreeSet<>();
    for (UserDataCleanupGateway participant : participants) {
      merged.addAll(participant.findUsersAfter(cursor, BATCH));
    }
    return merged.stream().limit(BATCH).toList();
  }
}
