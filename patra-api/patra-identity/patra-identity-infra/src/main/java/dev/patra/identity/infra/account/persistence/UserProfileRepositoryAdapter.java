package dev.patra.identity.infra.account.persistence;

import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.identity.domain.session.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.infra.account.persistence.dao.UserProfileDao;
import dev.patra.identity.infra.account.persistence.dao.UserProfileLinkDao;
import dev.patra.identity.infra.account.persistence.dao.UserResearchInterestDao;
import dev.patra.identity.infra.account.persistence.entity.UserProfileEntity;
import dev.patra.identity.infra.account.persistence.entity.UserProfileLinkEntity;
import dev.patra.identity.infra.account.persistence.entity.UserResearchInterestEntity;
import dev.patra.identity.infra.account.persistence.mapper.UserProfileJpaMapper;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 资料仓储的 JPA 实现：资料表一行，加链接、关注领域两张有序子表。
///
/// 保存用 `saveAndFlush`：唯一约束和乐观锁的冲突在这里就抛出来；乐观锁冲突转成
/// {@link UserModifiedConcurrentlyException}，`detail` 是固定文案。两张子表每次整组替换：批量删 + flush，
/// 再按新顺序插入并 flush，重排与删减都不会撞 `(user_id, position)` 唯一约束。
@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryAdapter implements UserProfileRepository {

  private final UserProfileDao dao;
  private final UserProfileLinkDao linkDao;
  private final UserResearchInterestDao interestDao;
  private final UserProfileJpaMapper mapper;

  /// 按用户 ID 查资料，连带链接与关注领域。
  ///
  /// @param userId 用户 ID
  /// @return 资料
  @Override
  public Optional<UserProfile> findByUserId(long userId) {
    return dao.findByUserId(userId).map(this::assemble);
  }

  /// 保存资料，新资料分配雪花 ID；两张子表整组替换，所以要求调用方的事务（`MANDATORY`）。
  ///
  /// @param profile 资料
  /// @return 保存后的资料
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public UserProfile save(UserProfile profile) {
    UserProfileEntity entity = mapper.toEntity(profile);
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
    UserProfileEntity saved;
    try {
      saved = dao.saveAndFlush(entity);
    } catch (OptimisticLockingFailureException ex) {
      throw new UserModifiedConcurrentlyException();
    }
    replaceChildren(profile);
    return assemble(saved);
  }

  /// 删掉旧的子表行，按新顺序插入。
  ///
  /// @param profile 资料
  private void replaceChildren(UserProfile profile) {
    Long userId = profile.getUserId();
    linkDao.deleteAllByUserId(userId);
    interestDao.deleteAllByUserId(userId);
    linkDao.flush();
    interestDao.flush();
    List<UserProfileLinkEntity> links = mapper.toLinkEntities(profile);
    links.forEach(link -> link.setId(SnowflakeIdGenerator.getId()));
    List<UserResearchInterestEntity> interests = mapper.toInterestEntities(profile);
    interests.forEach(interest -> interest.setId(SnowflakeIdGenerator.getId()));
    linkDao.saveAllAndFlush(links);
    interestDao.saveAllAndFlush(interests);
  }

  /// 资料行加子表行拼成聚合。
  ///
  /// @param entity 资料行
  /// @return 资料
  private UserProfile assemble(UserProfileEntity entity) {
    Long userId = entity.getUserId();
    return mapper.toAggregate(
        entity,
        linkDao.findByUserIdOrderByPositionAsc(userId),
        interestDao.findByUserIdOrderByPositionAsc(userId));
  }
}
