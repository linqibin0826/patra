package dev.patra.notification.infra.preference.persistence;

import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.read.EmailModes;
import dev.patra.notification.domain.preference.model.read.EmailSubscription;
import dev.patra.notification.domain.preference.port.read.EmailSubscriptionReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `EmailSubscriptionReadPort` 的原生 SQL 实现，只读。
///
/// 会收到邮件的人在写提醒时已由 preference 的监听器预建了偏好行，这里读不到就是不变量被破坏。
@Component
@RequiredArgsConstructor
public class EmailSubscriptionReadAdapter implements EmailSubscriptionReadPort {

  private final JdbcClient jdbc;

  /// 某人的邮件订阅。
  ///
  /// @param userId 用户 ID
  /// @return 订阅
  /// @throws IllegalStateException 没有偏好行（写提醒时应已预建）
  @Override
  public EmailSubscription subscriptionOf(long userId) {
    return jdbc.sql(
            "SELECT reply_mode, mention_mode, unsubscribe_token FROM preference.email_preference"
                + " WHERE user_id = :userId")
        .param("userId", userId)
        .query(
            (rs, rowNum) ->
                EmailSubscription.of(
                    userId,
                    EmailMode.valueOf(rs.getString("reply_mode")),
                    EmailMode.valueOf(rs.getString("mention_mode")),
                    rs.getString("unsubscribe_token")))
        .optional()
        .orElseThrow(() -> new IllegalStateException("收件人没有邮件偏好行: userId=" + userId));
  }

  /// 只读档位，不建行。
  ///
  /// @param userId 用户 ID
  /// @return 档位；没有行时是默认值
  @Override
  public EmailModes findSubscription(long userId) {
    return jdbc.sql(
            "SELECT reply_mode, mention_mode FROM preference.email_preference WHERE user_id = :userId")
        .param("userId", userId)
        .query(
            (rs, rowNum) ->
                EmailModes.of(
                    EmailMode.valueOf(rs.getString("reply_mode")),
                    EmailMode.valueOf(rs.getString("mention_mode"))))
        .optional()
        .orElse(EmailModes.DEFAULT);
  }

  /// 至少一类设为每日汇总的人。
  ///
  /// @param afterUserId 游标
  /// @param limit 最多几个
  /// @return 订阅
  @Override
  public List<EmailSubscription> digestSubscribers(long afterUserId, int limit) {
    return jdbc.sql(
            """
            SELECT user_id, reply_mode, mention_mode, unsubscribe_token FROM preference.email_preference
            WHERE (reply_mode = 'DIGEST' OR mention_mode = 'DIGEST') AND user_id > :after
            ORDER BY user_id LIMIT :limit
            """)
        .param("after", afterUserId)
        .param("limit", limit)
        .query(
            (rs, rowNum) ->
                EmailSubscription.of(
                    rs.getLong("user_id"),
                    EmailMode.valueOf(rs.getString("reply_mode")),
                    EmailMode.valueOf(rs.getString("mention_mode")),
                    rs.getString("unsubscribe_token")))
        .list();
  }
}
