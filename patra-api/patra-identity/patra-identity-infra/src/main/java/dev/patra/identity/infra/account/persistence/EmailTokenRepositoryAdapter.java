package dev.patra.identity.infra.account.persistence;

import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.session.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.infra.account.persistence.dao.EmailTokenDao;
import dev.patra.identity.infra.account.persistence.entity.EmailTokenEntity;
import dev.patra.identity.infra.account.persistence.mapper.EmailTokenJpaMapper;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 一次性邮件令牌仓储的 JPA 实现。
///
/// 保存用 `saveAndFlush`，撞上「同用户同用途只留一个有效令牌」的部分唯一索引或哈希唯一约束时转成
/// {@link UserModifiedConcurrentlyException}。消费先加行锁重读实体，见 {@link #consume}。批量作废是 Dao 上的
/// `@Query`，不经实体审计，操作人由这里从 `AuditorAware` 取来写进 `updated_by`。
@Repository
@RequiredArgsConstructor
public class EmailTokenRepositoryAdapter implements EmailTokenRepository {

  /// 「同用户同用途只留一个有效令牌」的部分唯一索引名，见建表脚本。
  static final String ACTIVE_UNIQUE_INDEX = "uk_idn_email_token_active";

  /// 哈希唯一约束的名字，见建表脚本。
  static final String HASH_UNIQUE_CONSTRAINT = "uk_idn_email_token_hash";

  private final EmailTokenDao dao;
  private final EmailTokenJpaMapper mapper;

  /// 消费时加锁重读实体用。
  private final EntityManager entityManager;

  /// 批量作废不经实体审计，操作人从这里取。
  private final AuditorAware<Long> auditor;

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

  /// 条件消费：按哈希查出实体，加行锁重读，在锁内确认用途对、还能用（没结算、没过期）后记下消费并保存；不满足返回空。
  ///
  /// 加锁用 `refresh` 而不是加锁查询：同一事务里之前按哈希查过的实体在一级缓存里，加锁查询会返回缓存里的旧状态，
  /// 看不到别的事务刚提交的消费。行锁让并发的两次消费排队，后到的那次重读后看到已消费，返回空。
  ///
  /// @param tokenHash 令牌哈希
  /// @param purpose 要求的用途
  /// @param now 消费时间
  /// @return 消费后的令牌；没命中时为空
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<EmailToken> consume(String tokenHash, EmailTokenPurpose purpose, Instant now) {
    Optional<EmailTokenEntity> found = dao.findByTokenHash(tokenHash);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    EmailTokenEntity entity = found.get();
    entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
    EmailToken token = mapper.toAggregate(entity);
    if (token.getPurpose() != purpose || !token.isUsableAt(now)) {
      return Optional.empty();
    }
    token.markConsumed(now);
    return Optional.of(save(token));
  }

  /// 作废一个用户某种用途的全部有效令牌。
  ///
  /// @param userId 用户 ID
  /// @param purpose 用途
  /// @param now 作废时间
  /// @return 作废了几行
  @Override
  public int revokeActive(long userId, EmailTokenPurpose purpose, Instant now) {
    return dao.revokeActive(userId, purpose.name(), now, currentActor());
  }

  /// 作废一个用户除改邮箱撤销令牌之外的全部有效令牌。
  ///
  /// @param userId 用户 ID
  /// @param now 作废时间
  /// @return 作废了几行
  @Override
  public int revokeAllActive(long userId, Instant now) {
    return dao.revokeAllActive(userId, now, currentActor());
  }

  /// 只作废比指定那条更晚签发的撤销令牌。
  ///
  /// @param userId 用户 ID
  /// @param tokenId 刚消费的撤销令牌的 ID
  /// @param now 作废时间
  /// @return 作废了几行
  @Override
  public int revokeRevertTokensIssuedAfter(long userId, long tokenId, Instant now) {
    return dao.revokeRevertTokensIssuedAfter(userId, tokenId, now, currentActor());
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

  /// 判断异常是否由指定约束或索引引起。
  ///
  /// @param ex 数据完整性异常
  /// @param constraint 约束或索引名
  /// @return 是它时为 `true`
  private static boolean violates(DataIntegrityViolationException ex, String constraint) {
    return ex.getCause() instanceof ConstraintViolationException violation
        && constraint.equalsIgnoreCase(violation.getConstraintName());
  }

  /// 当前操作人，与实体审计用的是同一个来源；系统写入时为空。
  private Long currentActor() {
    return auditor.getCurrentAuditor().orElse(null);
  }
}
