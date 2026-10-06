package dev.linqibin.patra.identity.infra.adapter.persistence.dao;

import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// `idn_user` 的 Spring Data 仓库。
public interface UserDao extends JpaRepository<UserEntity, Long> {

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
