package dev.patra.identity.infra.account.persistence.dao;

import dev.patra.identity.infra.account.persistence.entity.EmailTokenEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// `idn_email_token` 的 Spring Data 仓库。消费（`UPDATE … RETURNING`）不在这里，在适配器里用 `JdbcClient` 做。
public interface EmailTokenDao extends JpaRepository<EmailTokenEntity, Long> {

  /// 按哈希查。
  ///
  /// @param tokenHash 令牌哈希
  /// @return 实体
  Optional<EmailTokenEntity> findByTokenHash(String tokenHash);

  /// 查一个用户某种用途的有效令牌：未消费、未作废、未过期。
  ///
  /// @param userId 用户 ID
  /// @param purpose 用途
  /// @param now 判过期的基准
  /// @return 实体
  @Query(
      "select t from EmailTokenEntity t where t.userId = :userId and t.purpose = :purpose"
          + " and t.consumedAt is null and t.revokedAt is null and t.expiresAt > :now")
  Optional<EmailTokenEntity> findActive(
      @Param("userId") Long userId, @Param("purpose") String purpose, @Param("now") Instant now);

  /// 作废一个用户某种用途的全部有效令牌。
  ///
  /// @param userId 用户 ID
  /// @param purpose 用途
  /// @param now 作废时间
  /// @return 作废了几行
  @Modifying
  @Query(
      "update EmailTokenEntity t set t.revokedAt = :now where t.userId = :userId"
          + " and t.purpose = :purpose and t.consumedAt is null and t.revokedAt is null")
  int revokeActive(
      @Param("userId") Long userId, @Param("purpose") String purpose, @Param("now") Instant now);

  /// 作废一个用户除改邮箱撤销令牌之外的全部有效令牌。
  ///
  /// @param userId 用户 ID
  /// @param now 作废时间
  /// @return 作废了几行
  @Modifying
  @Query(
      "update EmailTokenEntity t set t.revokedAt = :now where t.userId = :userId"
          + " and t.purpose <> 'EMAIL_CHANGE_REVERT' and t.consumedAt is null"
          + " and t.revokedAt is null")
  int revokeAllActive(@Param("userId") Long userId, @Param("now") Instant now);

  /// 作废一个用户比指定那条更晚签发（ID 更大）的有效撤销令牌。
  ///
  /// @param userId 用户 ID
  /// @param tokenId 刚消费的撤销令牌的 ID
  /// @param now 作废时间
  /// @return 作废了几行
  @Modifying
  @Query(
      "update EmailTokenEntity t set t.revokedAt = :now where t.userId = :userId"
          + " and t.purpose = 'EMAIL_CHANGE_REVERT' and t.id > :tokenId"
          + " and t.consumedAt is null and t.revokedAt is null")
  int revokeRevertTokensIssuedAfter(
      @Param("userId") Long userId, @Param("tokenId") Long tokenId, @Param("now") Instant now);

  /// 删掉过期时间早于截止时间的行，一次最多一批。
  ///
  /// @param cutoff 截止时间
  /// @param batchSize 一批最多几行
  /// @return 删了几行
  @Modifying
  @Query(
      value =
          "DELETE FROM idn_email_token WHERE id IN (SELECT id FROM idn_email_token"
              + " WHERE expires_at < :cutoff ORDER BY id LIMIT :batchSize)",
      nativeQuery = true)
  int deleteExpiredBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

  /// 删掉一个用户的全部令牌行。
  ///
  /// @param userId 用户 ID
  /// @return 删了几行
  @Modifying
  @Query("delete from EmailTokenEntity t where t.userId = :userId")
  int deleteAllByUserId(@Param("userId") Long userId);
}
