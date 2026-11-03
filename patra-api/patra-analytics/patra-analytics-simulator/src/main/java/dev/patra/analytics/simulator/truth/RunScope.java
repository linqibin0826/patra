package dev.patra.analytics.simulator.truth;

import dev.patra.analytics.simulator.event.SyntheticIds;

/// 对账时只取本次运行的数据（spec §4）：事件按设备 ID 前缀，汇总表的人按设备前缀或用户 ID 区间。
public final class RunScope {

  private final int run;
  private final SyntheticIds ids;

  /// 按运行号建范围。
  ///
  /// @param run 运行号
  public RunScope(int run) {
    this.run = run;
    this.ids = new SyntheticIds(run);
  }

  /// 运行号。
  ///
  /// @return 运行号
  public int run() {
    return run;
  }

  /// `events` 表的过滤条件。
  ///
  /// @return SQL 条件
  public String eventsFilter() {
    return "startsWith(device_id, '" + ids.devicePrefix() + "')";
  }

  /// 汇总表 `person_id` 的过滤条件。
  ///
  /// @return SQL 条件
  public String personFilter() {
    return "(startsWith(person_id, 'd:"
        + ids.devicePrefix()
        + "') OR (startsWith(person_id, 'u:') AND toUInt64OrZero(substring(person_id, 3)) BETWEEN "
        + ids.userIdLow()
        + " AND "
        + ids.userIdHigh()
        + "))";
  }
}
