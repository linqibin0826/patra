package dev.patra.notification.infra.preference.persistence;

import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.read.EmailModes;
import dev.patra.notification.domain.preference.model.read.EmailSubscription;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import dev.patra.notification.domain.preference.port.read.EmailSubscriptionReadPort;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `EmailSubscriptionReadPort` 的原生 SQL 实现：先 `INSERT … ON CONFLICT (user_id) DO NOTHING` 一行默认值，
/// 再按用户读回。两条语句各自提交，不开事务：并发的首次调用里只有一个插入成功，其余插入落空、读到同一行。
///
/// 新令牌与用户已有的令牌重复的概率是 2⁻²⁵⁶，不处理。
@Component
@RequiredArgsConstructor
public class EmailSubscriptionReadAdapter implements EmailSubscriptionReadPort {

  private final JdbcClient jdbc;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  /// 某人的邮件订阅，没有行时先建。
  ///
  /// @param userId 用户 ID
  /// @return 订阅
  @Override
  public EmailSubscription subscriptionOf(long userId) {
    jdbc.sql(
            """
            INSERT INTO preference.email_preference
                   (user_id, reply_mode, mention_mode, unsubscribe_token, created_at, updated_at)
            VALUES (:userId, :mode, :mode, :token, :now, :now)
            ON CONFLICT (user_id) DO NOTHING
            """)
        .param("userId", userId)
        .param("mode", EmailMode.INSTANT.name())
        .param("token", UnsubscribeToken.generate(random).value())
        .param("now", clock.instant().atOffset(ZoneOffset.UTC))
        .update();
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
        .single();
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
