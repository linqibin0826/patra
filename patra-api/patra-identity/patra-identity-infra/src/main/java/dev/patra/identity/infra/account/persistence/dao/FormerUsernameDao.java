package dev.patra.identity.infra.account.persistence.dao;

import dev.patra.identity.infra.account.persistence.entity.FormerUsernameEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// `idn_user_former_username` 的 Spring Data 仓库。
public interface FormerUsernameDao extends JpaRepository<FormerUsernameEntity, Long> {

  /// 某个名字在此刻仍在保留期内的记录里，改名时间最近的一条。
  ///
  /// @param username 名字
  /// @param now 时刻
  /// @return 实体
  Optional<FormerUsernameEntity> findFirstByUsernameAndReservedUntilAfterOrderByReplacedAtDesc(
      String username, Instant now);

  /// 一组名字在此刻仍在保留期内的全部记录。
  ///
  /// @param usernames 名字
  /// @param now 时刻
  /// @return 实体，同一个名字可能有多条，不保证顺序
  List<FormerUsernameEntity> findByUsernameInAndReservedUntilAfter(
      Collection<String> usernames, Instant now);

  /// 某个名字在此刻是否被任何人保留着。
  ///
  /// @param username 名字
  /// @param now 时刻
  /// @return 保留中为 `true`
  boolean existsByUsernameAndReservedUntilAfter(String username, Instant now);

  /// 某个名字在此刻是否被指定用户之外的人保留着。
  ///
  /// @param username 名字
  /// @param userId 排除的用户 ID
  /// @param now 时刻
  /// @return 被别人保留中为 `true`
  boolean existsByUsernameAndUserIdNotAndReservedUntilAfter(
      String username, Long userId, Instant now);
}
