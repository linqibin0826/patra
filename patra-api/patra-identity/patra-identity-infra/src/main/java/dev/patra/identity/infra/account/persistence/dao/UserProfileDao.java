package dev.patra.identity.infra.account.persistence.dao;

import dev.patra.identity.infra.account.persistence.entity.UserProfileEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// `idn_user_profile` 的 Spring Data 仓库。
public interface UserProfileDao extends JpaRepository<UserProfileEntity, Long> {

  /// 按用户 ID 查。
  ///
  /// @param userId 用户 ID
  /// @return 实体
  Optional<UserProfileEntity> findByUserId(Long userId);

  /// 按用户 ID 批量查。
  ///
  /// @param userIds 用户 ID
  /// @return 实体，不保证顺序
  List<UserProfileEntity> findByUserIdIn(Collection<Long> userIds);
}
