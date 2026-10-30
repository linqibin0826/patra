package dev.patra.notification.domain.preference.port.read;

import dev.patra.notification.domain.preference.model.read.EmailSubscription;

/// mail 发提醒邮件前取收件人的邮件订阅：两类的档位与退订令牌（spec 第 9 节）。
///
/// 对调用方来说每个人「本来就有」一个退订令牌：没有偏好行时按默认值建一行（带新令牌）再返回，第一封提醒邮件
/// 发出前就有令牌可拼。notification 不知道「有新用户注册了」，没有更早的时机去建。
public interface EmailSubscriptionReadPort {

  /// 某人的邮件订阅。并发的首次调用拿到同一个令牌。
  ///
  /// @param userId 用户 ID
  /// @return 订阅
  EmailSubscription subscriptionOf(long userId);
}
