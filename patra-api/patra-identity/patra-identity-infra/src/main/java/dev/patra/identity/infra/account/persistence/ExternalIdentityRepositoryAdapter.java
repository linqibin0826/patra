package dev.patra.identity.infra.account.persistence;

import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import dev.patra.identity.infra.account.persistence.dao.ExternalIdentityDao;
import dev.patra.identity.infra.account.persistence.mapper.ExternalIdentityJpaMapper;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 外部身份仓储的 JPA 实现。
///
/// 新行走 `ON CONFLICT DO NOTHING`：唯一约束冲突是业务分支（已绑给别人），PostgreSQL 的事务遇到约束错误就作废，
/// 不能 catch 后继续用（`transaction.md` 第 3 节）。
@Repository
@RequiredArgsConstructor
public class ExternalIdentityRepositoryAdapter implements ExternalIdentityRepository {

  private final ExternalIdentityDao dao;
  private final ExternalIdentityJpaMapper mapper;

  /// 按平台和主体查。
  ///
  /// @param provider 平台
  /// @param subject 主体
  /// @return 外部身份
  @Override
  public Optional<ExternalIdentity> findByProviderAndSubject(
      IdentityProvider provider, String subject) {
    return dao.findByProviderAndSubject(provider.name(), subject).map(mapper::toAggregate);
  }

  /// 按用户和平台查。
  ///
  /// @param userId 用户 ID
  /// @param provider 平台
  /// @return 外部身份
  @Override
  public Optional<ExternalIdentity> findByUserIdAndProvider(
      long userId, IdentityProvider provider) {
    return dao.findByUserIdAndProvider(userId, provider.name()).map(mapper::toAggregate);
  }

  /// 按用户查全部。
  ///
  /// @param userId 用户 ID
  /// @return 外部身份
  @Override
  public List<ExternalIdentity> findAllByUserId(long userId) {
    return dao.findAllByUserId(userId).stream().map(mapper::toAggregate).toList();
  }

  /// 按用户计数。
  ///
  /// @param userId 用户 ID
  /// @return 行数
  @Override
  public long countByUserId(long userId) {
    return dao.countByUserId(userId);
  }

  /// 写入一行，撞上唯一约束时什么都不做。
  ///
  /// @param identity 新的外部身份
  /// @return 真的写入时为 `true`
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean insertIfAbsent(ExternalIdentity identity) {
    return dao.insertIfAbsent(
            SnowflakeIdGenerator.getId(),
            identity.getUserId(),
            identity.getProvider().name(),
            identity.getSubject(),
            identity.getAccountLabel(),
            identity.getLinkedAt())
        > 0;
  }

  /// 保存已有的一行。
  ///
  /// @param identity 外部身份
  @Override
  public void save(ExternalIdentity identity) {
    dao.saveAndFlush(mapper.toEntity(identity));
  }

  /// 解绑。
  ///
  /// @param userId 用户 ID
  /// @param provider 平台
  /// @return 真的删掉了一行时为 `true`
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean deleteByUserIdAndProvider(long userId, IdentityProvider provider) {
    return dao.deleteByUserIdAndProvider(userId, provider.name()) > 0;
  }

  /// 按用户删全部。
  ///
  /// @param userId 用户 ID
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void deleteAllByUserId(long userId) {
    dao.deleteAllByUserId(userId);
  }
}
