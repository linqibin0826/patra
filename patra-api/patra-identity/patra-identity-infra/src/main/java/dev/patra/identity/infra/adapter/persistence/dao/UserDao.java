package dev.patra.identity.infra.adapter.persistence.dao;

import dev.patra.identity.infra.adapter.persistence.entity.UserEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// `idn_user` 的 Spring Data 仓库。
public interface UserDao extends JpaRepository<UserEntity, Long> {

  /// 按 ID 加行锁查（`SELECT … FOR UPDATE`），只能在事务里调。
  ///
  /// @param id 用户 ID
  /// @return 实体
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserEntity u where u.id = :id")
  Optional<UserEntity> findByIdForUpdate(@Param("id") Long id);

  /// 按规范化之后的邮箱查。
  ///
  /// @param email 邮箱
  /// @return 实体
  Optional<UserEntity> findByEmail(String email);

  /// 邮箱是否存在。
  ///
  /// @param email 邮箱
  /// @return 存在时为 `true`
  boolean existsByEmail(String email);

  /// 按规范化之后的用户名查，包括已注销的。
  ///
  /// @param username 用户名
  /// @return 实体
  Optional<UserEntity> findByUsername(String username);

  /// 用户名是否存在，包括已注销的。
  ///
  /// @param username 用户名
  /// @return 存在时为 `true`
  boolean existsByUsername(String username);

  /// 冷静期已到期的用户 ID：按 ID 升序，从游标之后取一批。
  ///
  /// @param now 当前时间，到期时间不晚于它的算到期
  /// @param afterId 游标，只取比它大的 ID
  /// @param limit 一批最多几个
  /// @return 用户 ID
  @Query(
      value =
          "SELECT id FROM idn_user WHERE status = 'PENDING_DELETION' AND deletion_due_at <= :now"
              + " AND id > :afterId ORDER BY id LIMIT :limit",
      nativeQuery = true)
  List<Long> findIdsDueForDeletion(
      @Param("now") Instant now, @Param("afterId") long afterId, @Param("limit") int limit);

  /// 该发到期前提醒的用户 ID：冷静期、没提醒过、`now < 到期 <= dueUntil`；按 ID 升序，从游标之后取一批。
  ///
  /// @param now 当前时间
  /// @param dueUntil 到期时间的上界（含）
  /// @param afterId 游标，只取比它大的 ID
  /// @param limit 一批最多几个
  /// @return 用户 ID
  @Query(
      value =
          "SELECT id FROM idn_user WHERE status = 'PENDING_DELETION'"
              + " AND deletion_reminded_at IS NULL AND deletion_due_at > :now"
              + " AND deletion_due_at <= :dueUntil AND id > :afterId ORDER BY id LIMIT :limit",
      nativeQuery = true)
  List<Long> findIdsDueForDeletionReminder(
      @Param("now") Instant now,
      @Param("dueUntil") Instant dueUntil,
      @Param("afterId") long afterId,
      @Param("limit") int limit);
}
