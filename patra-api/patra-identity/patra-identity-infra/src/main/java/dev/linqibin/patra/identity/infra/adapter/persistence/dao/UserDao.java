package dev.linqibin.patra.identity.infra.adapter.persistence.dao;

import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserEntity;
import jakarta.persistence.LockModeType;
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
}
