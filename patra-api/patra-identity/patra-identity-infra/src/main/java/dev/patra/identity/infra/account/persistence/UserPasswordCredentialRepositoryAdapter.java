package dev.patra.identity.infra.account.persistence;

import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.infra.account.persistence.dao.UserPasswordCredentialDao;
import dev.patra.identity.infra.account.persistence.entity.UserPasswordCredentialEntity;
import dev.patra.identity.infra.account.persistence.mapper.UserPasswordCredentialJpaMapper;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
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

  /// 按用户 ID 删凭据，立刻刷到库里。
  ///
  /// @param userId 用户 ID
  /// @return 真的删掉了一行时为 `true`
  @Override
  public boolean deleteByUserId(long userId) {
    int deleted = dao.deleteByUserId(userId);
    dao.flush();
    return deleted > 0;
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
