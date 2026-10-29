package dev.patra.identity.infra.account.persistence;

import dev.patra.identity.domain.account.model.aggregate.FormerUsername;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.port.repository.FormerUsernameRepository;
import dev.patra.identity.infra.account.persistence.dao.FormerUsernameDao;
import dev.patra.identity.infra.account.persistence.entity.FormerUsernameEntity;
import dev.patra.identity.infra.account.persistence.mapper.FormerUsernameJpaMapper;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 旧用户名仓储的 JPA 实现。
@Repository
@RequiredArgsConstructor
public class FormerUsernameRepositoryAdapter implements FormerUsernameRepository {

  private final FormerUsernameDao dao;
  private final FormerUsernameJpaMapper mapper;

  /// 保存旧名记录，新记录分配雪花 ID。
  ///
  /// @param formerUsername 旧名记录
  /// @return 保存后的记录
  @Override
  public FormerUsername save(FormerUsername formerUsername) {
    FormerUsernameEntity entity = mapper.toEntity(formerUsername);
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
    return mapper.toAggregate(dao.saveAndFlush(entity));
  }

  /// 查此刻仍在保留期内的记录，取改名时间最近的一条。
  ///
  /// @param username 名字
  /// @param now 时刻
  /// @return 记录
  @Override
  public Optional<FormerUsername> findReserved(Username username, Instant now) {
    return dao.findFirstByUsernameAndReservedUntilAfterOrderByReplacedAtDesc(username.value(), now)
        .map(mapper::toAggregate);
  }

  /// 批量查此刻仍在保留期内的记录：一条 `IN` 查询取出全部未到期的，每个名字留改名时间最近的一条。
  ///
  /// @param usernames 名字
  /// @param now 时刻
  /// @return 记录，每个名字最多一条，不保证顺序
  @Override
  public List<FormerUsername> findAllReserved(Collection<Username> usernames, Instant now) {
    return dao
        .findByUsernameInAndReservedUntilAfter(
            usernames.stream().map(Username::value).toList(), now)
        .stream()
        .collect(
            Collectors.toMap(
                FormerUsernameEntity::getUsername,
                Function.identity(),
                BinaryOperator.maxBy(Comparator.comparing(FormerUsernameEntity::getReplacedAt))))
        .values()
        .stream()
        .map(mapper::toAggregate)
        .toList();
  }

  /// 此刻是否被任何人保留着。
  ///
  /// @param username 名字
  /// @param now 时刻
  /// @return 保留中为 `true`
  @Override
  public boolean existsReserved(Username username, Instant now) {
    return dao.existsByUsernameAndReservedUntilAfter(username.value(), now);
  }

  /// 此刻是否被别人保留着。
  ///
  /// @param username 名字
  /// @param userId 本人的用户 ID
  /// @param now 时刻
  /// @return 被别人保留中为 `true`
  @Override
  public boolean existsReservedByOther(Username username, long userId, Instant now) {
    return dao.existsByUsernameAndUserIdNotAndReservedUntilAfter(username.value(), userId, now);
  }
}
