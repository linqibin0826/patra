package dev.patra.notification.infra.inbox.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/// 信箱各表读写共用的小工具：时刻与 `timestamptz` 互转。
final class InboxRows {

  /// 只有静态方法，不实例化。
  private InboxRows() {}

  /// 读一个 `timestamptz` 列。
  ///
  /// @param rs 结果集
  /// @param column 列名
  /// @return 时刻，列为空时 `null`
  /// @throws SQLException 读取失败
  static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  /// 时刻转 UTC 偏移时间，作 `timestamptz` 参数。
  ///
  /// @param instant 时刻
  /// @return UTC 偏移时间
  static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
