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

  /// 按 ID 加行锁查用户，只能在事务里调。
  ///
  /// 登录建会话前用它复核封禁状态：封禁对同一行的更新和这把锁互斥，两种先后都对——封禁先提交，
  /// 这里读到的就是封禁；登录先提交，封禁随后的「删全部会话」会把这条新会话一起删掉。
  ///
  /// @param id 用户 ID
  /// @return 用户；不存在时为空
  Optional<User> findByIdForUpdate(long id);

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
