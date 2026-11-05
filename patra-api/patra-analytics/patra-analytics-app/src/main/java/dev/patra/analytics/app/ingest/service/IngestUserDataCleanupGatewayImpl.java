package dev.patra.analytics.app.ingest.service;

import dev.patra.analytics.domain.ingest.port.store.EventStorePort;
import dev.patra.analytics.domain.kernel.port.gateway.UserDataCleanupGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// ingest 子域的注销清理：用户从日活表找，删除覆盖明细、dlq、文献 × 会话汇总与日活（入库设计 §6）。
@Component
@RequiredArgsConstructor
public class IngestUserDataCleanupGatewayImpl implements UserDataCleanupGateway {

  private final EventStorePort store;

  /// 子域名。
  ///
  /// @return `ingest`
  @Override
  public String subdomain() {
    return "ingest";
  }

  /// 游标之后在 ClickHouse 里有数据的登录用户。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID，升序
  @Override
  public List<Long> findUsersAfter(long afterUserId, int limit) {
    return store.findUsersAfter(afterUserId, limit);
  }

  /// 一条语句一张表地删掉这批用户。
  ///
  /// @param userIds 已注销的用户
  /// @return 清理的用户数
  @Override
  public int deleteAllOf(List<Long> userIds) {
    if (userIds.isEmpty()) {
      return 0;
    }
    store.deleteUsers(userIds);
    return userIds.size();
  }
}
