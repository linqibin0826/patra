package dev.patra.identity.infra.account.persistence.dao;

import dev.patra.identity.infra.account.persistence.entity.UserPasswordCredentialEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// `idn_user_password_credential` 的 Spring Data 仓库。
public interface UserPasswordCredentialDao
    extends JpaRepository<UserPasswordCredentialEntity, Long> {

  /// 按用户 ID 查。
  ///
  /// @param userId 用户 ID
  /// @return 实体
  Optional<UserPasswordCredentialEntity> findByUserId(Long userId);

  /// 按用户 ID 删。批量语句而不是派生删除：派生删除先查再逐个 remove，flush 时排在插入之后。
  ///
  /// @param userId 用户 ID
  /// @return 删了几行
  @Modifying
  @Query("delete from UserPasswordCredentialEntity c where c.userId = :userId")
  int deleteByUserId(@Param("userId") Long userId);
}
