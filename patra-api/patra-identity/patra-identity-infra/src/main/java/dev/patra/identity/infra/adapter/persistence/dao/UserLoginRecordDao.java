package dev.patra.identity.infra.adapter.persistence.dao;

import dev.patra.identity.infra.adapter.persistence.entity.UserLoginRecordEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// `idn_user_login_record` 的 Spring Data 仓库。
public interface UserLoginRecordDao extends JpaRepository<UserLoginRecordEntity, Long> {

  /// 这个账号有没有用这台设备、在某个时刻之后还可能在用的、不是被本人踢掉的记录。
  ///
  /// @param userId 用户 ID
  /// @param deviceId 设备标识
  /// @param since 窗口起点，和记录的「结束或过期的时刻」比
  /// @return 有时为 `true`
  @Query(
      """
      SELECT COUNT(r) > 0 FROM UserLoginRecordEntity r
      WHERE r.userId = :userId AND r.deviceId = :deviceId
        AND COALESCE(r.endedAt, r.expiresAt) >= :since
        AND (r.endReason IS NULL OR r.endReason <> 'REVOKED')
      """)
  boolean existsTrustedDevice(
      @Param("userId") long userId,
      @Param("deviceId") String deviceId,
      @Param("since") Instant since);

  /// 删掉「结束或过期的时刻」早于截止时间的行，一次最多一批。
  ///
  /// @param cutoff 截止时间
  /// @param batchSize 一批最多几行
  /// @return 删了几行
  @Modifying
  @Query(
      value =
          "DELETE FROM idn_user_login_record WHERE id IN (SELECT id FROM idn_user_login_record"
              + " WHERE COALESCE(ended_at, expires_at) < :cutoff ORDER BY id LIMIT :batchSize)",
      nativeQuery = true)
  int deleteRetiredBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
