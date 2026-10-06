package dev.linqibin.patra.identity.infra.adapter.persistence;

import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper.UserJpaMapper;
import dev.linqibin.patra.identity.infra.adapter.persistence.dao.UserDao;
import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserEntity;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/// 前台用户仓储的 JPA 实现。
///
/// 保存用 `saveAndFlush`：唯一约束和乐观锁的冲突在这里就抛出来，
/// 邮箱唯一约束转成 {@link EmailAlreadyRegisteredException}，不把数据库的报错带进响应。
@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

  /// 邮箱唯一约束的名字，见建表脚本。
  static final String EMAIL_UNIQUE_CONSTRAINT = "uk_idn_user_email";

  private final UserDao dao;
  private final UserJpaMapper mapper;

  /// 按 ID 查用户。
  ///
  /// @param id 用户 ID
  /// @return 用户
  @Override
  public Optional<User> findById(long id) {
    return dao.findById(id).map(mapper::toAggregate);
  }

  /// 按邮箱查用户。
  ///
  /// @param email 邮箱
  /// @return 用户
  @Override
  public Optional<User> findByEmail(EmailAddress email) {
    return dao.findByEmail(email.value()).map(mapper::toAggregate);
  }

  /// 邮箱是否已注册。
  ///
  /// @param email 邮箱
  /// @return 已注册时为 `true`
  @Override
  public boolean existsByEmail(EmailAddress email) {
    return dao.existsByEmail(email.value());
  }

  /// 保存用户，新用户分配雪花 ID。
  ///
  /// @param user 用户
  /// @return 保存后的用户
  @Override
  public User save(User user) {
    UserEntity entity = mapper.toEntity(user);
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
    try {
      return mapper.toAggregate(dao.saveAndFlush(entity));
    } catch (DataIntegrityViolationException ex) {
      if (violates(ex, EMAIL_UNIQUE_CONSTRAINT)) {
        throw new EmailAlreadyRegisteredException();
      }
      throw ex;
    }
  }

  /// 判断异常是否由指定约束引起。
  ///
  /// @param ex 数据完整性异常
  /// @param constraint 约束名
  /// @return 是该约束时为 `true`
  private static boolean violates(DataIntegrityViolationException ex, String constraint) {
    return ex.getCause() instanceof ConstraintViolationException violation
        && constraint.equalsIgnoreCase(violation.getConstraintName());
  }
}
