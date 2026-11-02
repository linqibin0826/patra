package dev.patra.analytics.app.history.service;

import dev.patra.analytics.domain.history.port.repository.ReadingHistoryRepository;
import dev.patra.analytics.domain.kernel.port.gateway.UserDataCleanupGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// history 子域的注销清理（spec §7.2）：用户从历史、设置、删除记录三张表找，一个事务里删掉这批用户在三张表里的行。
@Component
@RequiredArgsConstructor
public class HistoryUserDataCleanupGatewayImpl implements UserDataCleanupGateway {

  private final ReadingHistoryRepository history;
  private final TransactionOperations transactions;

  /// 子域名。
  ///
  /// @return `history`
  @Override
  public String subdomain() {
    return "history";
  }

  /// 游标之后在三张表里有行的用户。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID，升序
  @Override
  public List<Long> findUsersAfter(long afterUserId, int limit) {
    return history.findUsersAfter(afterUserId, limit);
  }

  /// 一个事务删掉这批用户。
  ///
  /// @param userIds 已注销的用户
  /// @return 清理的用户数
  @Override
  public int deleteAllOf(List<Long> userIds) {
    if (userIds.isEmpty()) {
      return 0;
    }
    transactions.executeWithoutResult(status -> history.deleteUsers(userIds));
    return userIds.size();
  }
}
