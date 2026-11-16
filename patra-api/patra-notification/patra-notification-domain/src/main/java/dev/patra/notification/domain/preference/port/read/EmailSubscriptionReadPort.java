package dev.patra.notification.domain.preference.port.read;

import dev.patra.notification.domain.preference.model.read.EmailModes;
import dev.patra.notification.domain.preference.model.read.EmailSubscription;
import java.util.List;

/// mail 发提醒邮件前取收件人的邮件订阅：两类的档位与退订令牌（spec 第 9 节）。
///
/// 只读。会收到邮件的人在写提醒时已预建了默认偏好行（带退订令牌，见 preference 订阅信箱事件的监听器），所以第一封
/// 提醒邮件发出前就有令牌可拼；notification 不知道「有新用户注册了」，写提醒是能建行的最早时机。
public interface EmailSubscriptionReadPort {

  /// 某人的邮件订阅。
  ///
  /// @param userId 用户 ID
  /// @return 订阅
  /// @throws IllegalStateException 没有偏好行：要发信的人写提醒时应已预建，读不到说明不变量被破坏
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
