package dev.patra.identity.infra.account.persistence.dao;

import dev.patra.identity.infra.account.persistence.entity.ExternalIdentityEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// `idn_user_external_identity` 的 Spring Data 仓库。
public interface ExternalIdentityDao extends JpaRepository<ExternalIdentityEntity, Long> {

  /// 按平台和主体查。
  ///
  /// @param provider 平台
  /// @param subject 主体
  /// @return 实体
  Optional<ExternalIdentityEntity> findByProviderAndSubject(String provider, String subject);

  /// 按用户和平台查。
  ///
  /// @param userId 用户 ID
  /// @param provider 平台
  /// @return 实体
  Optional<ExternalIdentityEntity> findByUserIdAndProvider(Long userId, String provider);

  /// 按用户查全部。
  ///
  /// @param userId 用户 ID
  /// @return 实体
  List<ExternalIdentityEntity> findAllByUserId(Long userId);

  /// 按用户计数。
  ///
  /// @param userId 用户 ID
  /// @return 行数
  long countByUserId(Long userId);

  /// 写入一行，撞上任一唯一约束时什么都不做。
  ///
  /// @param id 雪花 ID
  /// @param userId 用户 ID
  /// @param provider 平台
  /// @param subject 主体
  /// @param accountLabel 账号标识
  /// @param linkedAt 绑定时间
  /// @return 写入了几行
  @Modifying
  @Query(
      value =
          """
          INSERT INTO idn_user_external_identity
              (id, user_id, provider, subject, account_label, linked_at)
          VALUES (:id, :userId, :provider, :subject, :accountLabel, :linkedAt)
          ON CONFLICT DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("id") long id,
      @Param("userId") long userId,
      @Param("provider") String provider,
      @Param("subject") String subject,
      @Param("accountLabel") String accountLabel,
      @Param("linkedAt") Instant linkedAt);

  /// 按用户和平台删。批量语句：不用先查再 remove。
  ///
  /// @param userId 用户 ID
  /// @param provider 平台
  /// @return 删了几行
  @Modifying
  @Query("delete from ExternalIdentityEntity e where e.userId = :userId and e.provider = :provider")
  int deleteByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);

  /// 按用户删全部。
  ///
  /// @param userId 用户 ID
  /// @return 删了几行
  @Modifying
  @Query("delete from ExternalIdentityEntity e where e.userId = :userId")
  int deleteAllByUserId(@Param("userId") Long userId);
}
