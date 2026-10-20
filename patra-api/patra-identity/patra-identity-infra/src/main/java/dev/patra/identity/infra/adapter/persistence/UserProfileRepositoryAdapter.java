package dev.patra.identity.infra.adapter.persistence;

import dev.patra.identity.domain.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.domain.model.aggregate.UserProfile;
import dev.patra.identity.domain.port.repository.UserProfileRepository;
import dev.patra.identity.infra.adapter.persistence.converter.mapper.UserProfileJpaMapper;
import dev.patra.identity.infra.adapter.persistence.dao.UserProfileDao;
import dev.patra.identity.infra.adapter.persistence.entity.UserProfileEntity;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/// 资料仓储的 JPA 实现。
///
/// 保存用 `saveAndFlush`：唯一约束和乐观锁的冲突在这里就抛出来；乐观锁冲突转成
/// {@link UserModifiedConcurrentlyException}，`detail` 是固定文案。
@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryAdapter implements UserProfileRepository {

  private final UserProfileDao dao;
  private final UserProfileJpaMapper mapper;

  /// 按用户 ID 查资料。
  ///
  /// @param userId 用户 ID
  /// @return 资料
  @Override
  public Optional<UserProfile> findByUserId(long userId) {
    return dao.findByUserId(userId).map(mapper::toAggregate);
  }

  /// 保存资料，新资料分配雪花 ID。
  ///
  /// @param profile 资料
  /// @return 保存后的资料
  @Override
  public UserProfile save(UserProfile profile) {
    UserProfileEntity entity = mapper.toEntity(profile);
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
    try {
      return mapper.toAggregate(dao.saveAndFlush(entity));
    } catch (OptimisticLockingFailureException ex) {
      throw new UserModifiedConcurrentlyException();
    }
  }
}
