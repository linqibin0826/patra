package dev.patra.notification.app.kernel.service;

import dev.patra.notification.domain.kernel.port.gateway.UserDataCleanupGateway;
import dev.patra.notification.domain.kernel.port.repository.PresenceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 底座自己登记的注销清理动作：删已注销用户的在站记录（「什么时候在站上」也是个人数据）。
@Component
@RequiredArgsConstructor
public class KernelUserDataCleanupGatewayImpl implements UserDataCleanupGateway {

  private final PresenceRepository presence;

  /// 子域名。
  ///
  /// @return `kernel`
  @Override
  public String subdomain() {
    return "kernel";
  }

  /// 有在站记录的用户。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  @Override
  public List<Long> findUsersAfter(long afterUserId, int limit) {
    return presence.findUsersAfter(afterUserId, limit);
  }

  /// 删他的在站记录。
  ///
  /// @param userId 已注销的用户
  /// @return 删除的行数
  @Override
  public int deleteAllOf(long userId) {
    return presence.deleteAllOf(userId);
  }
}
