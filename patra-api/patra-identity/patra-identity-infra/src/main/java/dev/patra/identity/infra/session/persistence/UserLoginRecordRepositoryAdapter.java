package dev.patra.identity.infra.session.persistence;

import dev.patra.identity.domain.session.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.infra.session.persistence.dao.UserLoginRecordDao;
import dev.patra.identity.infra.session.persistence.entity.UserLoginRecordEntity;
import dev.patra.identity.infra.session.persistence.mapper.UserLoginRecordJpaMapper;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/// 登录记录仓储的 JPA 实现。保存用 `saveAndFlush`，乐观锁冲突在这里就抛出来，
/// 转成 {@link UserModifiedConcurrentlyException}（同一条记录同时被登出和被挤掉 / 封禁时），
/// 不把实体名和 ID 带进响应。
@Repository
@RequiredArgsConstructor
public class UserLoginRecordRepositoryAdapter implements UserLoginRecordRepository {

  private final UserLoginRecordDao dao;
  private final UserLoginRecordJpaMapper mapper;

  /// 保存记录，新记录分配雪花 ID。
  ///
  /// @param login 记录
  /// @return 保存后的记录
  @Override
  public UserLoginRecord save(UserLoginRecord login) {
    UserLoginRecordEntity entity = mapper.toEntity(login);
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
    try {
      return mapper.toAggregate(dao.saveAndFlush(entity));
    } catch (OptimisticLockingFailureException ex) {
      throw new UserModifiedConcurrentlyException();
    }
  }

  /// 按 ID 查记录。
  ///
  /// @param id 记录 ID
  /// @return 记录
  @Override
  public Optional<UserLoginRecord> findById(long id) {
    return dao.findById(id).map(mapper::toAggregate);
  }

  /// 按 ID 批量查。
  ///
  /// @param ids 记录 ID
  /// @return 存在的记录
  @Override
  public List<UserLoginRecord> findAllByIds(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return dao.findAllById(ids).stream().map(mapper::toAggregate).toList();
  }

  /// 是否见过这台设备。
  ///
  /// @param userId 用户 ID
  /// @param deviceId 设备标识
  /// @param since 回看窗口的起点
  /// @return 见过时为 `true`
  @Override
  public boolean existsTrustedDevice(long userId, DeviceId deviceId, Instant since) {
    return dao.existsTrustedDevice(userId, deviceId.value(), since);
  }

  /// 按退役时刻删一批。
  ///
  /// @param cutoff 截止时间
  /// @param batchSize 一批最多几行
  /// @return 删了几行
  @Override
  public int deleteRetiredBefore(Instant cutoff, int batchSize) {
    return dao.deleteRetiredBefore(cutoff, batchSize);
  }
}
