package dev.patra.notification.domain.preference.port.repository;

import dev.patra.notification.domain.preference.model.aggregate.EmailPreference;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/// 提醒邮件偏好的持久化，一个用户一行。写方法不开事务，由 app 层的事务包住。
public interface EmailPreferenceRepository {

  /// 按用户读，不加锁。
  ///
  /// @param userId 用户 ID
  /// @return 偏好；没有行时为空（按默认值处理）
  Optional<EmailPreference> findByUserId(long userId);

  /// 按退订令牌读，不加锁。
  ///
  /// @param token 令牌
  /// @return 偏好；没有这个令牌时为空
  Optional<EmailPreference> findByToken(UnsubscribeToken token);

  /// 按用户读并锁住这一行，必须在调用方的事务里。
  ///
  /// @param userId 用户 ID
  /// @return 偏好；没有行时为空
  Optional<EmailPreference> findByUserIdForUpdate(long userId);

  /// 按退订令牌读并锁住这一行，必须在调用方的事务里。
  ///
  /// @param token 令牌
  /// @return 偏好；没有这个令牌时为空
  Optional<EmailPreference> findByTokenForUpdate(UnsubscribeToken token);

  /// 没有这个用户的行时插入，有就什么都不做（不覆盖档位和令牌）。
  ///
  /// @param preference 要插入的偏好（通常是默认值加新令牌）
  /// @param now 写入时刻
  void insertIfAbsent(EmailPreference preference, Instant now);

  /// 写回两类的档位。
  ///
  /// @param preference 改过的偏好
  /// @param now 写入时刻
  void updateModes(EmailPreference preference, Instant now);

  /// 有偏好行的用户，按用户 ID 升序的一批（注销清理用）。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  List<Long> findUsersAfter(long afterUserId, int limit);

  /// 删这个用户的偏好行，令牌随之作废。
  ///
  /// @param userId 用户 ID
  /// @return 删掉的行数
  int deleteByUserId(long userId);
}
