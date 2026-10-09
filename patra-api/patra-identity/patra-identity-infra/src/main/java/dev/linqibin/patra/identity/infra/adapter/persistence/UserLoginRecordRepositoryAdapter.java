package dev.linqibin.patra.identity.infra.adapter.persistence;

import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper.UserLoginRecordJpaMapper;
import dev.linqibin.patra.identity.infra.adapter.persistence.dao.UserLoginRecordDao;
import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserLoginRecordEntity;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 登录记录仓储的 JPA 实现。保存用 `saveAndFlush`，乐观锁冲突在这里就抛出来。
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
    return mapper.toAggregate(dao.saveAndFlush(entity));
  }

  /// 按 ID 查记录。
  ///
  /// @param id 记录 ID
  /// @return 记录
  @Override
  public Optional<UserLoginRecord> findById(long id) {
    return dao.findById(id).map(mapper::toAggregate);
  }
}
