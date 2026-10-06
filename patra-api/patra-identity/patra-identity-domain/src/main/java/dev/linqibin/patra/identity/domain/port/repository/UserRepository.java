package dev.linqibin.patra.identity.domain.port.repository;

import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import java.util.Optional;

/// 前台用户仓储。
public interface UserRepository {

  /// 按 ID 查用户。
  ///
  /// @param id 用户 ID
  /// @return 用户；不存在时为空
  Optional<User> findById(long id);

  /// 按规范化之后的邮箱查用户。
  ///
  /// @param email 邮箱
  /// @return 用户；不存在时为空
  Optional<User> findByEmail(EmailAddress email);

  /// 邮箱是否已注册。
  ///
  /// @param email 邮箱
  /// @return 已注册时为 `true`
  boolean existsByEmail(EmailAddress email);

  /// 保存用户。新用户在这里分配雪花 ID。
  ///
  /// @param user 用户
  /// @return 保存后的用户，带 ID 和版本
  /// @throws EmailAlreadyRegisteredException 撞上邮箱唯一约束（并发注册同一个邮箱）
  User save(User user);
}
