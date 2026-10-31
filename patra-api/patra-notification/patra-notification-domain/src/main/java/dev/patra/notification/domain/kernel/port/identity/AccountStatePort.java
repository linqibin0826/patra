package dev.patra.notification.domain.kernel.port.identity;

import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.AccountState;
import java.util.Collection;
import java.util.Map;

/// 按用户 ID 批量取账号状态（identity 的 `POST /_internal/users/summaries`）。给注销清理用：每天要扫全部有数据的
/// 用户，只要状态，不用把所有人的邮箱都拉一遍，所以不走收件信息接口（同 interaction 的注销清理）。
public interface AccountStatePort {

  /// 一次最多多少个用户，等于 identity 接口的上限。
  int MAX_USERS = 100;

  /// 批量取账号状态。
  ///
  /// @param userIds 用户 ID，1 ~ {@value #MAX_USERS} 个
  /// @return 用户 ID → 账号状态；identity 不认识的省略（省略不等于已注销）
  /// @throws IdentityUnavailableException identity 不可用
  Map<Long, AccountState> findStates(Collection<Long> userIds);
}
