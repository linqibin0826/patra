package dev.patra.identity.infra.account.persistence;

import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.session.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.identity.infra.account.persistence.dao.UserDao;
import dev.patra.identity.infra.account.persistence.entity.UserEntity;
import dev.patra.identity.infra.account.persistence.mapper.UserJpaMapper;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 前台用户仓储的 JPA 实现。
///
/// 保存用 `saveAndFlush`：唯一约束和乐观锁的冲突在这里就抛出来。邮箱、用户名的唯一约束转成
/// {@link UserIdentifierTakenException}（`errors[]` 里带对应字段），不把数据库的报错带进响应；
/// 乐观锁冲突转成 {@link UserModifiedConcurrentlyException}，`detail` 是固定文案。
@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

  /// 邮箱唯一约束的名字，见建表脚本。
  static final String EMAIL_UNIQUE_CONSTRAINT = "uk_idn_user_email";

  /// 用户名唯一约束的名字，见建表脚本。
  static final String USERNAME_UNIQUE_CONSTRAINT = "uk_idn_user_username";

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

  /// 按 ID 加行锁查用户。
  ///
  /// @param id 用户 ID
  /// @return 用户
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<User> findByIdForUpdate(long id) {
    return dao.findByIdForUpdate(id).map(mapper::toAggregate);
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

  /// 按用户名查用户。
  ///
  /// @param username 用户名
  /// @return 用户
  @Override
  public Optional<User> findByUsername(Username username) {
    return dao.findByUsername(username.value()).map(mapper::toAggregate);
  }

  /// 用户名是否已被占用。
  ///
  /// @param username 用户名
  /// @return 已占用时为 `true`
  @Override
  public boolean existsByUsername(Username username) {
    return dao.existsByUsername(username.value());
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
        throw new UserIdentifierTakenException(
            List.of(UserFieldViolations.emailAlreadyRegistered()));
      }
      if (violates(ex, USERNAME_UNIQUE_CONSTRAINT)) {
        throw new UserIdentifierTakenException(List.of(UserFieldViolations.usernameAlreadyTaken()));
      }
      throw ex;
    } catch (OptimisticLockingFailureException ex) {
      throw new UserModifiedConcurrentlyException();
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

  /// 冷静期已到期的用户 ID。
  ///
  /// @param now 当前时间
  /// @param afterId 游标
  /// @param limit 一批最多几个
  /// @return 用户 ID，按 ID 升序
  @Override
  public List<Long> findIdsDueForDeletion(Instant now, long afterId, int limit) {
    return dao.findIdsDueForDeletion(now, afterId, limit);
  }

  /// 该发到期前提醒的用户 ID。
  ///
  /// @param now 当前时间
  /// @param dueUntil 到期时间的上界（含）
  /// @param afterId 游标
  /// @param limit 一批最多几个
  /// @return 用户 ID，按 ID 升序
  @Override
  public List<Long> findIdsDueForDeletionReminder(
      Instant now, Instant dueUntil, long afterId, int limit) {
    return dao.findIdsDueForDeletionReminder(now, dueUntil, afterId, limit);
  }
}
