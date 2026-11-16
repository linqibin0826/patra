package dev.patra.notification.infra.preference.persistence;

import dev.patra.notification.domain.preference.model.aggregate.EmailPreference;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import dev.patra.notification.domain.preference.port.repository.EmailPreferenceRepository;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `EmailPreferenceRepository` 的原生 SQL 实现（`preference` schema）。写方法不开事务，由 app 层的事务包住。
@Component
@RequiredArgsConstructor
public class EmailPreferenceRepositoryAdapter implements EmailPreferenceRepository {

  private static final String COLUMNS = "user_id, reply_mode, mention_mode, unsubscribe_token";

  private final JdbcClient jdbc;

  /// 按用户读。
  ///
  /// @param userId 用户 ID
  /// @return 偏好
  @Override
  public Optional<EmailPreference> findByUserId(long userId) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM ntf_email_preference WHERE user_id = :userId")
        .param("userId", userId)
        .query(EmailPreferenceRepositoryAdapter::toPreference)
        .optional();
  }

  /// 按令牌读（走令牌的唯一索引）。
  ///
  /// @param token 令牌
  /// @return 偏好
  @Override
  public Optional<EmailPreference> findByToken(UnsubscribeToken token) {
    return jdbc.sql(
            "SELECT " + COLUMNS + " FROM ntf_email_preference WHERE unsubscribe_token = :token")
        .param("token", token.value())
        .query(EmailPreferenceRepositoryAdapter::toPreference)
        .optional();
  }

  /// 按用户读并锁行。
  ///
  /// @param userId 用户 ID
  /// @return 偏好
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<EmailPreference> findByUserIdForUpdate(long userId) {
    return jdbc.sql(
            "SELECT " + COLUMNS + " FROM ntf_email_preference WHERE user_id = :userId FOR UPDATE")
        .param("userId", userId)
        .query(EmailPreferenceRepositoryAdapter::toPreference)
        .optional();
  }

  /// 按令牌读并锁行。
  ///
  /// @param token 令牌
  /// @return 偏好
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<EmailPreference> findByTokenForUpdate(UnsubscribeToken token) {
    return jdbc.sql(
            "SELECT "
                + COLUMNS
                + " FROM ntf_email_preference WHERE unsubscribe_token = :token FOR UPDATE")
        .param("token", token.value())
        .query(EmailPreferenceRepositoryAdapter::toPreference)
        .optional();
  }

  /// `ON CONFLICT (user_id) DO NOTHING` 插入：已有行时不覆盖。令牌撞上别人的令牌时照常报唯一约束错误。
  ///
  /// 默认行由系统预建（写提醒时或首次改档位前），操作人留空。
  ///
  /// @param preference 要插入的偏好
  @Override
  public void insertIfAbsent(EmailPreference preference) {
    jdbc.sql(
            """
            INSERT INTO ntf_email_preference (id, user_id, reply_mode, mention_mode, unsubscribe_token)
            VALUES (:id, :userId, :reply, :mention, :token)
            ON CONFLICT (user_id) DO NOTHING
            """)
        .param("id", SnowflakeIdGenerator.getId())
        .param("userId", preference.getUserId())
        .param("reply", preference.getReply().name())
        .param("mention", preference.getMention().name())
        .param("token", preference.getUnsubscribeToken().value())
        .update();
  }

  /// 写回两类档位。改档位与一键退订都是用户本人（退订凭的是他的令牌），操作人就是他。
  ///
  /// @param preference 改过的偏好
  @Override
  public void updateModes(EmailPreference preference) {
    jdbc.sql(
            "UPDATE ntf_email_preference SET reply_mode = :reply, mention_mode = :mention,"
                + " updated_by = :userId WHERE user_id = :userId")
        .param("reply", preference.getReply().name())
        .param("mention", preference.getMention().name())
        .param("userId", preference.getUserId())
        .update();
  }

  /// 主键升序取游标之后的一批。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  @Override
  public List<Long> findUsersAfter(long afterUserId, int limit) {
    return jdbc.sql(
            "SELECT user_id FROM ntf_email_preference WHERE user_id > :after"
                + " ORDER BY user_id LIMIT :limit")
        .param("after", afterUserId)
        .param("limit", limit)
        .query(Long.class)
        .list();
  }

  /// 按主键删。
  ///
  /// @param userId 用户 ID
  /// @return 删掉的行数
  @Override
  public int deleteByUserId(long userId) {
    return jdbc.sql("DELETE FROM ntf_email_preference WHERE user_id = :userId")
        .param("userId", userId)
        .update();
  }

  /// 行 → 聚合。
  ///
  /// @param rs 结果集
  /// @param rowNum 行号
  /// @return 偏好
  /// @throws SQLException 读列失败
  private static EmailPreference toPreference(ResultSet rs, int rowNum) throws SQLException {
    return EmailPreference.restore(
        rs.getLong("user_id"),
        new UnsubscribeToken(rs.getString("unsubscribe_token")),
        EmailMode.valueOf(rs.getString("reply_mode")),
        EmailMode.valueOf(rs.getString("mention_mode")));
  }
}
