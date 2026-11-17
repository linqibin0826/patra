package dev.patra.analytics.infra.history.persistence;

import dev.patra.analytics.domain.history.model.vo.RecordingSetting;
import dev.patra.analytics.domain.history.port.repository.RecordingSettingRepository;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `RecordingSettingRepository` 的原生 SQL 实现（`anl_recording_setting`）：两个写方法都是按主键 upsert，
/// 各改各的列。
@Component
@RequiredArgsConstructor
public class RecordingSettingRepositoryAdapter implements RecordingSettingRepository {

  private static final String SELECT =
      "SELECT user_id, recording_enabled, cleared_at FROM anl_recording_setting";

  private final JdbcClient jdbc;

  /// 按主键查。
  ///
  /// @param userId 用户 ID
  /// @return 没有行为空
  @Override
  public Optional<RecordingSetting> find(long userId) {
    return jdbc.sql(SELECT + " WHERE user_id = :userId")
        .param("userId", userId)
        .query((rs, rowNum) -> map(rs))
        .optional();
  }

  /// 一条 `IN` 查询。
  ///
  /// @param userIds 用户 ID
  /// @return 有设置的用户 → 设置
  @Override
  public Map<Long, RecordingSetting> findAll(Collection<Long> userIds) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    return jdbc
        .sql(SELECT + " WHERE user_id IN (:ids)")
        .param("ids", new ArrayList<>(userIds))
        .query((rs, rowNum) -> map(rs))
        .list()
        .stream()
        .collect(Collectors.toUnmodifiableMap(RecordingSetting::userId, Function.identity()));
  }

  /// upsert 开关，不动 `cleared_at`。
  ///
  /// @param userId 用户 ID
  /// @param enabled 是否记录
  @Override
  public void saveEnabled(long userId, boolean enabled) {
    jdbc.sql(
            """
            INSERT INTO anl_recording_setting
                   (id, user_id, recording_enabled, created_by, updated_by)
            VALUES (:id, :userId, :enabled, :userId, :userId)
            ON CONFLICT (user_id) DO UPDATE
               SET recording_enabled = EXCLUDED.recording_enabled, updated_by = EXCLUDED.updated_by
            """)
        .param("id", SnowflakeIdGenerator.getId())
        .param("userId", userId)
        .param("enabled", enabled)
        .update();
  }

  /// upsert 清空时刻，不动开关；新插入的行是开着的。
  ///
  /// @param userId 用户 ID
  /// @param clearedAt 清空时刻
  @Override
  public void markCleared(long userId, Instant clearedAt) {
    jdbc.sql(
            """
            INSERT INTO anl_recording_setting
                   (id, user_id, recording_enabled, cleared_at, created_by, updated_by)
            VALUES (:id, :userId, TRUE, :at, :userId, :userId)
            ON CONFLICT (user_id) DO UPDATE
               SET cleared_at = EXCLUDED.cleared_at, updated_by = EXCLUDED.updated_by
            """)
        .param("id", SnowflakeIdGenerator.getId())
        .param("userId", userId)
        .param("at", clearedAt.atOffset(ZoneOffset.UTC))
        .update();
  }

  /// 映射一行。
  ///
  /// @param rs 结果集
  /// @return 设置
  /// @throws SQLException 读列失败
  private static RecordingSetting map(ResultSet rs) throws SQLException {
    OffsetDateTime clearedAt = rs.getObject("cleared_at", OffsetDateTime.class);
    return RecordingSetting.of(
        rs.getLong("user_id"),
        rs.getBoolean("recording_enabled"),
        clearedAt == null ? null : clearedAt.toInstant());
  }
}
