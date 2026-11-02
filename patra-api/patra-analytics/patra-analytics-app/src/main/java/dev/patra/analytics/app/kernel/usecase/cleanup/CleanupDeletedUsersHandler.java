package dev.patra.analytics.app.kernel.usecase.cleanup;

import dev.patra.analytics.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.analytics.domain.kernel.model.enums.AccountState;
import dev.patra.analytics.domain.kernel.port.gateway.UserDataCleanupGateway;
import dev.patra.analytics.domain.kernel.port.identity.AccountStatePort;
import dev.patra.tool.cqrs.CommandHandler;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 注销清理的公共流程（照 notification 的 `CleanupDeletedUsersHandler`）：按用户 ID 升序，每轮把各子域在游标之后
/// 有数据的用户并起来，取最小的 {@value #BATCH} 个，调一次 identity 取账号状态；这一批里 `DELETED` 的人，每个子域
/// 一次删完。identity 省略的不删（省略不等于已注销），记 WARN；identity 不可用跳过这一批、计数，继续下一批，次日重跑。
///
/// 取最小的一批是对的：某个子域在游标之后的用户 u 若不在它返回的列表里，那列表里就是 {@value #BATCH} 个比 u 小的
/// 用户，它们都在并集里，于是这一批的最大值也比 u 小，u 留到下一轮。
///
/// 与 notification 不同：按一批用户删、不包事务（ClickHouse 没有事务，各子域的删除幂等，中途失败次日按同样顺序再删）。
/// 不加任务级锁：调度中心「单机串行」保证同一时间只有一个实例在跑。子域抛出的意外异常不吞，让任务失败、标红。
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupDeletedUsersHandler
    implements CommandHandler<CleanupDeletedUsersCommand, CleanupResult> {

  /// 一批的人数，等于 identity 批量接口的上限。
  static final int BATCH = AccountStatePort.MAX_USERS;

  /// 清理的用户数（标签 `outcome`：`deleted` 删掉、`skipped` 因 identity 不可用跳过）
  static final String USERS_METRIC = "analytics.cleanup.users";

  private final List<UserDataCleanupGateway> participants;
  private final AccountStatePort accounts;
  private final MeterRegistry meters;

  /// 清理。
  ///
  /// @param command 命令
  /// @return 计数
  @Override
  public CleanupResult handle(CleanupDeletedUsersCommand command) {
    int scanned = 0;
    int deletedUsers = 0;
    int failedBatches = 0;
    Map<String, Integer> usersBySubdomain = new TreeMap<>();
    long cursor = 0L;
    List<Long> batch;
    while (!(batch = nextBatch(cursor)).isEmpty()) {
      long after = cursor;
      cursor = batch.getLast();
      scanned += batch.size();
      Map<Long, AccountState> states;
      try {
        states = accounts.findStates(batch);
      } catch (IdentityUnavailableException ex) {
        failedBatches++;
        meters.counter(USERS_METRIC, "outcome", "skipped").increment(batch.size());
        log.warn("注销清理跳过一批: afterUserId={}, users={}", after, batch.size());
        continue;
      }
      List<Long> deleted = new ArrayList<>();
      for (Long userId : batch) {
        AccountState state = states.get(userId);
        if (state == null) {
          log.warn("注销清理: identity 不认识这个用户，不删: userId={}", userId);
        } else if (state == AccountState.DELETED) {
          deleted.add(userId);
        }
      }
      if (deleted.isEmpty()) {
        continue;
      }
      for (UserDataCleanupGateway participant : participants) {
        usersBySubdomain.merge(
            participant.subdomain(), participant.deleteAllOf(deleted), Integer::sum);
      }
      deletedUsers += deleted.size();
      meters.counter(USERS_METRIC, "outcome", "deleted").increment(deleted.size());
    }
    log.info(
        "注销清理完成: scannedUsers={}, deletedUsers={}, bySubdomain={}, failedBatches={}",
        scanned,
        deletedUsers,
        usersBySubdomain,
        failedBatches);
    return CleanupResult.of(scanned, deletedUsers, failedBatches);
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
