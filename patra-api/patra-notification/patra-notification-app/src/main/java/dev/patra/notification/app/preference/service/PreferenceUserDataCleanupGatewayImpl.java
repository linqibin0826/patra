package dev.patra.notification.app.preference.service;

import dev.patra.notification.domain.kernel.port.gateway.UserDataCleanupGateway;
import dev.patra.notification.domain.preference.port.repository.EmailPreferenceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// preference 登记的注销清理动作：删除已注销用户的偏好行。令牌随行作废，此后他所有旧邮件里的退订链接都是
/// 「链接无效」。
@Component
@RequiredArgsConstructor
public class PreferenceUserDataCleanupGatewayImpl implements UserDataCleanupGateway {

  private final EmailPreferenceRepository preferences;

  /// 子域名。
  ///
  /// @return `preference`
  @Override
  public String subdomain() {
    return "preference";
  }

  /// 有偏好行的用户。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  @Override
  public List<Long> findUsersAfter(long afterUserId, int limit) {
    return preferences.findUsersAfter(afterUserId, limit);
  }

  /// 删除他的偏好行。
  ///
  /// @param userId 已注销的用户
  /// @return 删除的行数
  @Override
  public int deleteAllOf(long userId) {
    return preferences.deleteByUserId(userId);
  }
}
