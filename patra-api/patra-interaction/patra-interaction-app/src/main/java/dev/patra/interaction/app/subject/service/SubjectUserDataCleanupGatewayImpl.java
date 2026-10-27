package dev.patra.interaction.app.subject.service;

import dev.patra.interaction.domain.subject.port.gateway.UserDataCleanupGateway;
import dev.patra.interaction.domain.subject.port.repository.PublishQuotaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 底座登记的注销清理动作：已注销用户的发表计数（`subject.publish_quota`）。
@Component
@RequiredArgsConstructor
public class SubjectUserDataCleanupGatewayImpl implements UserDataCleanupGateway {

  private final PublishQuotaRepository quotas;

  /// 子域名。
  ///
  /// @return `subject`
  @Override
  public String subdomain() {
    return "subject";
  }

  /// 有发表计数的用户。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  @Override
  public List<Long> findUsersAfter(long afterUserId, int limit) {
    return quotas.findUsersAfter(afterUserId, limit);
  }

  /// 删他的发表计数。
  ///
  /// @param userId 已注销的用户
  /// @return 行数
  @Override
  public int deleteAllOf(long userId) {
    return quotas.deleteAllOf(userId);
  }
}
