package dev.patra.analytics.domain.history.port.repository;

import dev.patra.analytics.domain.history.model.vo.RecordingSetting;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/// 阅读记录设置的读写（`history.recording_setting`）：只有改过开关或清空过的人才有一行。
public interface RecordingSettingRepository {

  /// 一个人的设置。
  ///
  /// @param userId 用户 ID
  /// @return 没有设置过为空
  Optional<RecordingSetting> find(long userId);

  /// 一批人的设置。
  ///
  /// @param userIds 用户 ID
  /// @return 有设置的用户 → 设置
  Map<Long, RecordingSetting> findAll(Collection<Long> userIds);

  /// 保存开关，保留清空时刻。
  ///
  /// @param userId 用户 ID
  /// @param enabled 是否记录
  /// @param now 现在
  void saveEnabled(long userId, boolean enabled, Instant now);

  /// 记下清空时刻，开关不变；没有这一行时插入一行开着的。
  ///
  /// @param userId 用户 ID
  /// @param clearedAt 清空时刻
  void markCleared(long userId, Instant clearedAt);
}
