package dev.patra.identity.domain.port.repository;

import dev.patra.identity.domain.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.Username;
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
  /// 登录建会话前用它复核状态：封禁对同一行的更新和这把锁互斥，两种先后都对——封禁先提交，
  /// 这里读到的就是封禁；登录先提交，封禁随后的「删全部会话」会把这条新会话一起删掉。
  /// 改名也用它，让同一用户的两次并发改名串行。
  ///
  /// @param id 用户 ID
  /// @return 用户；不存在时为空
  Optional<User> findByIdForUpdate(long id);

  /// 按规范化之后的邮箱查用户。已注销的用户没有邮箱，查不到。
  ///
  /// @param email 邮箱
  /// @return 用户；不存在时为空
  Optional<User> findByEmail(EmailAddress email);

  /// 邮箱是否已注册。已注销的用户已经释放了邮箱，不算。
  ///
  /// @param email 邮箱
  /// @return 已注册时为 `true`
  boolean existsByEmail(EmailAddress email);

  /// 按用户名查用户，包括已注销的。
  ///
  /// @param username 用户名
  /// @return 用户；不存在时为空
  Optional<User> findByUsername(Username username);

  /// 用户名是否已被占用。用户名永不释放，已注销的用户也算。
  ///
  /// @param username 用户名
  /// @return 已占用时为 `true`
  boolean existsByUsername(Username username);

  /// 保存用户。新用户在这里分配雪花 ID。
  ///
  /// @param user 用户
  /// @return 保存后的用户，带 ID 和版本
  /// @throws UserIdentifierTakenException 撞上邮箱或用户名的唯一约束（并发注册、并发改名）
  /// @throws UserModifiedConcurrentlyException 撞上乐观锁
  User save(User user);
}
