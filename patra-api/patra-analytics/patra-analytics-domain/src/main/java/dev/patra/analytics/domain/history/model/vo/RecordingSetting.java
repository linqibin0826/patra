package dev.patra.analytics.domain.history.model.vo;

import java.time.Instant;

/// 一个人的阅读记录设置：开关与最近一次清空的时刻。没有设置过的人等同 {@link #defaults}。
///
/// @param userId 用户 ID
/// @param enabled 是否记录
/// @param clearedAt 最近一次清空的时刻；没清空过为 `null`
public record RecordingSetting(long userId, boolean enabled, Instant clearedAt) {

  /// 创建设置。
  ///
  /// @param userId 用户 ID
  /// @param enabled 是否记录
  /// @param clearedAt 最近一次清空的时刻，可空
  /// @return 设置
  public static RecordingSetting of(long userId, boolean enabled, Instant clearedAt) {
    return new RecordingSetting(userId, enabled, clearedAt);
  }

  /// 没有设置过的人：开着、没清空过。
  ///
  /// @param userId 用户 ID
  /// @return 默认设置
  public static RecordingSetting defaults(long userId) {
    return of(userId, true, null);
  }

  /// 这一刻发生的打开要不要记：开着，且晚于最近一次清空（等于也不记，spec §4.4）。
  ///
  /// @param readAt 打开的时刻
  /// @return 要记为 `true`
  public boolean accepts(Instant readAt) {
    return enabled && (clearedAt == null || readAt.isAfter(clearedAt));
  }
}
