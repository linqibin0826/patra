package dev.patra.notification.app.inbox.service;

import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.notification.domain.kernel.port.gateway.UserDataCleanupGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// inbox 登记的注销清理动作：删已注销用户作为收件人的全部提醒（含有帮助条目）。他触发的提醒留在别人那里，读时
/// identity 返回已注销的占位。
@Component
@RequiredArgsConstructor
public class InboxUserDataCleanupGatewayImpl implements UserDataCleanupGateway {

  private final NotificationRepository notifications;

  /// 子域名。
  ///
  /// @return `inbox`
  @Override
  public String subdomain() {
    return "inbox";
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

  /// 删他的全部提醒。
  ///
  /// @param userId 已注销的用户
  /// @return 删除的行数
  @Override
  public int deleteAllOf(long userId) {
    return notifications.deleteAllOf(userId);
  }
}
