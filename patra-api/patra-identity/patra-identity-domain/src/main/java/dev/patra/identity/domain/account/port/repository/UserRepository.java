package dev.patra.identity.domain.account.port.repository;

import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.session.exception.UserModifiedConcurrentlyException;
import java.time.Instant;
import java.util.List;
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

  /// 冷静期已到期（到期时间不晚于 `now`）的用户 ID，按 ID 升序，从游标之后取一批：注销到期执行任务用。
  ///
  /// @param now 当前时间
  /// @param afterId 游标，只取比它大的 ID
  /// @param limit 一批最多几个
  /// @return 用户 ID
  List<Long> findIdsDueForDeletion(Instant now, long afterId, int limit);

  /// 该发到期前提醒的用户 ID：冷静期、没提醒过、`now < 到期时间 <= dueUntil`；按 ID 升序，从游标之后取一批。
  ///
  /// @param now 当前时间
  /// @param dueUntil 到期时间的上界（含），即现在加提前量
  /// @param afterId 游标，只取比它大的 ID
  /// @param limit 一批最多几个
  /// @return 用户 ID
  List<Long> findIdsDueForDeletionReminder(Instant now, Instant dueUntil, long afterId, int limit);
}
