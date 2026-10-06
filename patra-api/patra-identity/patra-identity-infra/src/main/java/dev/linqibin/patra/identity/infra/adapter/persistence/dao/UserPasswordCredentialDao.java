package dev.linqibin.patra.identity.infra.adapter.persistence.dao;

import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserPasswordCredentialEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// `idn_user_password_credential` 的 Spring Data 仓库。
public interface UserPasswordCredentialDao
    extends JpaRepository<UserPasswordCredentialEntity, Long> {

  /// 按用户 ID 查。
  ///
  /// @param userId 用户 ID
  /// @return 实体
  Optional<UserPasswordCredentialEntity> findByUserId(Long userId);
}
