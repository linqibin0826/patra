package dev.patra.identity.infra.account.persistence.dao;

import dev.patra.identity.infra.account.persistence.entity.UserEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
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

  /// 按规范化之后的用户名批量查，包括已注销的。
  ///
  /// @param usernames 用户名
  /// @return 实体，不保证顺序
  List<UserEntity> findByUsernameIn(Collection<String> usernames);

  /// 用户名是否存在，包括已注销的。
  ///
  /// @param username 用户名
  /// @return 存在时为 `true`
  boolean existsByUsername(String username);

  /// 用户名落在 `[from, to)` 里的正常账号，按用户名的字节序升序取前 `limit` 个（@ 联想，PAP-118）。
  ///
  /// 比较与排序都按 `COLLATE "C"`（库的默认排序规则下前缀范围不连续），走 `V2` 的部分索引
  /// `idx_idn_user_username_prefix`。写范围谓词而不是 `LIKE`：`_` 是 `LIKE` 的通配符，参数化的 `LIKE`
  /// 在通用计划下也推不出索引范围。`status` 写字面量，部分索引的谓词在通用计划下才匹配得上。
  ///
  /// @param from 下界（含），即前缀本身
  /// @param to 上界（不含），前缀末字符的码位加一
  /// @param limit 最多几个
  /// @return 实体，按用户名字节序
  @Query(
      value =
          "SELECT * FROM idn_user WHERE status = 'ACTIVE'"
              + " AND username COLLATE \"C\" >= :from AND username COLLATE \"C\" < :to"
              + " ORDER BY username COLLATE \"C\" LIMIT :limit",
      nativeQuery = true)
  List<UserEntity> findActiveByUsernameRange(
      @Param("from") String from, @Param("to") String to, @Param("limit") int limit);

  /// 已设昵称、小写昵称落在 `[from, to)` 里的正常账号，按小写昵称的码点序升序、同名按用户名，取前
  /// `limit` 个（@ 联想，PAP-118）。
  ///
  /// 比较与排序按 `COLLATE "C"`（UTF-8 字节序即码点序），走 `V2` 的表达式索引
  /// `idx_idn_user_profile_display_name_prefix`；`lower()` 按列的排序规则做大小写映射。状态在用户表，资料表上的
  /// 部分索引覆盖不到，`ACTIVE` 由联表过滤；`status`、`IS NOT NULL` 写字面量。
  ///
  /// @param from 下界（含），即已转小写的前缀
  /// @param to 上界（不含），前缀末码点加一
  /// @param limit 最多几个
  /// @return 实体，按小写昵称的码点序
  @Query(
      value =
          "SELECT u.* FROM idn_user u JOIN idn_user_profile p ON p.user_id = u.id"
              + " WHERE u.status = 'ACTIVE' AND p.display_name IS NOT NULL"
              + " AND lower(p.display_name) COLLATE \"C\" >= :from"
              + " AND lower(p.display_name) COLLATE \"C\" < :to"
              + " ORDER BY lower(p.display_name) COLLATE \"C\", u.username COLLATE \"C\""
              + " LIMIT :limit",
      nativeQuery = true)
  List<UserEntity> findActiveByDisplayNameRange(
      @Param("from") String from, @Param("to") String to, @Param("limit") int limit);

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
