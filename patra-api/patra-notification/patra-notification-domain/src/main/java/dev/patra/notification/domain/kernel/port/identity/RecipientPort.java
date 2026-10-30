package dev.patra.notification.domain.kernel.port.identity;

import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.model.read.Recipient;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// 按用户 ID 取收件信息（identity 的 `POST /_internal/users/recipients`）。preference 的退订落地页取脱敏邮箱与
/// 账号状态；mail 取收件地址、是否已验证、用户名与显示名（PAP-146）。注销清理只要账号状态，走 `AccountStatePort`。
public interface RecipientPort {

  /// 一次最多多少个用户，等于 identity 接口的上限。
  int MAX_USERS = 100;

  /// 批量取收件信息。
  ///
  /// @param userIds 用户 ID，1 ~ {@value #MAX_USERS} 个
  /// @return 用户 ID → 收件信息；identity 不认识的省略
  /// @throws IdentityUnavailableException identity 不可用
  Map<Long, Recipient> findAll(Collection<Long> userIds);

  /// 取一个人的收件信息。
  ///
  /// @param userId 用户 ID
  /// @return 收件信息；identity 不认识时为空
  /// @throws IdentityUnavailableException identity 不可用
  default Optional<Recipient> find(long userId) {
    return Optional.ofNullable(findAll(List.of(userId)).get(userId));
  }
}
