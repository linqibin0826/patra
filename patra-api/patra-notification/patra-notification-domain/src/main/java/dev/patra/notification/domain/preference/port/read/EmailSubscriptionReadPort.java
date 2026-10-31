package dev.patra.notification.domain.preference.port.read;

import dev.patra.notification.domain.preference.model.read.EmailModes;
import dev.patra.notification.domain.preference.model.read.EmailSubscription;
import java.util.List;

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

  /// 只读某人两类的档位，不建行；没有行就是默认值（两类都是离站时逐条）。
  ///
  /// @param userId 用户 ID
  /// @return 档位
  EmailModes findSubscription(long userId);

  /// 至少一类设为每日汇总的人（汇总名单），按用户 ID 升序分页，不建行。
  ///
  /// @param afterUserId 游标：只取比它大的
  /// @param limit 最多几个
  /// @return 订阅
  List<EmailSubscription> digestSubscribers(long afterUserId, int limit);
}
