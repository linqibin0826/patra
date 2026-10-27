package dev.patra.interaction.app.notification.service;

import dev.patra.interaction.domain.notification.port.repository.NotificationRepository;
import dev.patra.interaction.domain.subject.port.gateway.UserDataCleanupGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// notification 登记的注销清理动作（决策 H）：删除已注销用户作为收件人的全部提醒。他触发的提醒留在别人那里，读时
/// identity 返回已注销的占位。
@Component
@RequiredArgsConstructor
public class NotificationUserDataCleanupGatewayImpl implements UserDataCleanupGateway {

  private final NotificationRepository notifications;

  /// 子域名。
  ///
  /// @return `notification`
  @Override
  public String subdomain() {
    return "notification";
  }

  /// 有提醒的收件人。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  @Override
  public List<Long> findUsersAfter(long afterUserId, int limit) {
    return notifications.findRecipientsAfter(afterUserId, limit);
  }

  /// 删除他的全部提醒。
  ///
  /// @param userId 已注销的用户
  /// @return 删除的条数
  @Override
  public int deleteAllOf(long userId) {
    return notifications.deleteAllOf(userId);
  }
}
