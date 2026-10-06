package dev.linqibin.patra.identity.infra.adapter.persistence;

import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper.UserPasswordCredentialJpaMapper;
import dev.linqibin.patra.identity.infra.adapter.persistence.dao.UserPasswordCredentialDao;
import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserPasswordCredentialEntity;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 前台用户密码凭据仓储的 JPA 实现。
@Repository
@RequiredArgsConstructor
public class UserPasswordCredentialRepositoryAdapter implements UserPasswordCredentialRepository {

  private final UserPasswordCredentialDao dao;
  private final UserPasswordCredentialJpaMapper mapper;

  /// 按用户 ID 查凭据。
  ///
  /// @param userId 用户 ID
  /// @return 凭据
  @Override
  public Optional<UserPasswordCredential> findByUserId(long userId) {
    return dao.findByUserId(userId).map(mapper::toAggregate);
  }

  /// 保存凭据，新凭据分配雪花 ID。
  ///
  /// @param credential 凭据
  /// @return 保存后的凭据
  @Override
  public UserPasswordCredential save(UserPasswordCredential credential) {
    UserPasswordCredentialEntity entity = mapper.toEntity(credential);
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
    return mapper.toAggregate(dao.saveAndFlush(entity));
  }
}
