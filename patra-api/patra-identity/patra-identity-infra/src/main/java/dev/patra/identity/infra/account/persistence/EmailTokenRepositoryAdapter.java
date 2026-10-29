package dev.patra.identity.infra.account.persistence;

import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.session.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.infra.account.persistence.dao.EmailTokenDao;
import dev.patra.identity.infra.account.persistence.entity.EmailTokenEntity;
import dev.patra.identity.infra.account.persistence.mapper.EmailTokenJpaMapper;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 一次性邮件令牌仓储的 JPA 实现。
///
/// 保存用 `saveAndFlush`，撞上「同用户同用途只留一个有效令牌」的部分唯一索引或哈希唯一约束时转成
/// {@link UserModifiedConcurrentlyException}。消费是一条 `UPDATE … RETURNING`，用 `JdbcClient` 直接执行、
/// 不经 JPA：行锁让并发的两次消费只有一个命中，拿到的就是更新后的行，不会被一级缓存里的旧实例顶掉。
@Repository
@RequiredArgsConstructor
public class EmailTokenRepositoryAdapter implements EmailTokenRepository {

  /// 「同用户同用途只留一个有效令牌」的部分唯一索引名，见建表脚本。
  static final String ACTIVE_UNIQUE_INDEX = "uk_idn_email_token_active";

  /// 哈希唯一约束的名字，见建表脚本。
  static final String HASH_UNIQUE_CONSTRAINT = "uk_idn_email_token_hash";

  private static final String CONSUME_SQL =
      """
      UPDATE idn_email_token
         SET consumed_at = :now, version = version + 1
       WHERE token_hash = :hash AND purpose = :purpose
         AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > :now
      RETURNING id, user_id, purpose, email, token_hash, expires_at, consumed_at, revoked_at,
                requested_ip, version, created_at, updated_at
      """;

  private final EmailTokenDao dao;
  private final EmailTokenJpaMapper mapper;

  /// 与 JPA 共用同一个事务连接的 JDBC 模板，消费语句用它。
  private final JdbcTemplate jdbcTemplate;

  /// 保存令牌，新令牌分配雪花 ID。
  ///
  /// @param token 令牌
  /// @return 保存后的令牌
  @Override
  public EmailToken save(EmailToken token) {
    EmailTokenEntity entity = mapper.toEntity(token);
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
    try {
      return mapper.toAggregate(dao.saveAndFlush(entity));
    } catch (DataIntegrityViolationException ex) {
      if (violates(ex, ACTIVE_UNIQUE_INDEX) || violates(ex, HASH_UNIQUE_CONSTRAINT)) {
        throw new UserModifiedConcurrentlyException();
      }
      throw ex;
    }
  }

  /// 按哈希查，不加锁。
  ///
  /// @param tokenHash 令牌哈希
  /// @return 令牌
  @Override
  public Optional<EmailToken> findByTokenHash(String tokenHash) {
    return dao.findByTokenHash(tokenHash).map(mapper::toAggregate);
  }

  /// 查有效令牌。
  ///
  /// @param userId 用户 ID
  /// @param purpose 用途
  /// @param now 判过期的基准
  /// @return 令牌
  @Override
  public Optional<EmailToken> findActive(long userId, EmailTokenPurpose purpose, Instant now) {
    return dao.findActive(userId, purpose.name(), now).map(mapper::toAggregate);
  }

  /// 条件消费。绕过 JPA 直接改库：同一事务里之前按哈希查出的实体不会跟着变，调用方用返回值，不要再读旧实例。
  ///
  /// @param tokenHash 令牌哈希
  /// @param purpose 要求的用途
  /// @param now 消费时间
  /// @return 消费后的令牌；没命中时为空
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<EmailToken> consume(String tokenHash, EmailTokenPurpose purpose, Instant now) {
    return JdbcClient.create(jdbcTemplate)
        .sql(CONSUME_SQL)
        .param("hash", tokenHash)
        .param("purpose", purpose.name())
        .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
        .query(EmailTokenRepositoryAdapter::toAggregate)
        .optional();
  }

  /// 作废一个用户某种用途的全部有效令牌。
  ///
  /// @param userId 用户 ID
  /// @param purpose 用途
  /// @param now 作废时间
  /// @return 作废了几行
  @Override
  public int revokeActive(long userId, EmailTokenPurpose purpose, Instant now) {
    return dao.revokeActive(userId, purpose.name(), now);
  }

  /// 作废一个用户除改邮箱撤销令牌之外的全部有效令牌。
  ///
  /// @param userId 用户 ID
  /// @param now 作废时间
  /// @return 作废了几行
  @Override
  public int revokeAllActive(long userId, Instant now) {
    return dao.revokeAllActive(userId, now);
  }

  /// 只作废比指定那条更晚签发的撤销令牌。
  ///
  /// @param userId 用户 ID
  /// @param tokenId 刚消费的撤销令牌的 ID
  /// @param now 作废时间
  /// @return 作废了几行
  @Override
  public int revokeRevertTokensIssuedAfter(long userId, long tokenId, Instant now) {
    return dao.revokeRevertTokensIssuedAfter(userId, tokenId, now);
  }

  /// 删掉过期时间早于截止时间的行，一次最多一批。
  ///
  /// @param cutoff 截止时间
  /// @param batchSize 一批最多几行
  /// @return 删了几行
  @Override
  public int deleteExpiredBefore(Instant cutoff, int batchSize) {
    return dao.deleteExpiredBefore(cutoff, batchSize);
  }

  /// 删掉一个用户的全部令牌行。
  ///
  /// @param userId 用户 ID
  /// @return 删了几行
  @Override
  public int deleteAllByUserId(long userId) {
    return dao.deleteAllByUserId(userId);
  }

  /// `RETURNING` 的一行转成聚合。
  ///
  /// @param rs 结果集，已定位到当前行
  /// @param rowNum 行号
  /// @return 令牌
  /// @throws SQLException 读列失败
  private static EmailToken toAggregate(ResultSet rs, int rowNum) throws SQLException {
    return EmailToken.restore()
        .id(rs.getLong("id"))
        .userId(rs.getLong("user_id"))
        .purpose(EmailTokenPurpose.valueOf(rs.getString("purpose")))
        .email(new EmailAddress(rs.getString("email")))
        .tokenHash(rs.getString("token_hash"))
        .expiresAt(instant(rs, "expires_at"))
        .consumedAt(instant(rs, "consumed_at"))
        .revokedAt(instant(rs, "revoked_at"))
        .requestedIp(rs.getString("requested_ip"))
        .version(rs.getLong("version"))
        .createdAt(instant(rs, "created_at"))
        .updatedAt(instant(rs, "updated_at"))
        .build();
  }

  /// 读一个 `timestamptz` 列。
  ///
  /// @param rs 结果集
  /// @param column 列名
  /// @return 时刻；列为空时为 `null`
  /// @throws SQLException 读列失败
  private static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  /// 判断异常是否由指定约束或索引引起。
  ///
  /// @param ex 数据完整性异常
  /// @param constraint 约束或索引名
  /// @return 是它时为 `true`
  private static boolean violates(DataIntegrityViolationException ex, String constraint) {
    return ex.getCause() instanceof ConstraintViolationException violation
        && constraint.equalsIgnoreCase(violation.getConstraintName());
  }
}
