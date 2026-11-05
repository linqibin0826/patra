package dev.patra.analytics.simulator.model;

/// 一个模拟的「人」：一台设备，注册用户另有用户 ID；带固定的浏览器与地区。
///
/// @param index 运行内的下标（真值账本用它代表人）
/// @param deviceId 设备 ID
/// @param userId 合成用户 ID；访客为 `null`
/// @param ua 浏览器
/// @param region 地区
public record Person(int index, String deviceId, Long userId, UaProfile ua, Region region) {

  /// 埋点方案 §6.1 的「人」：登录按用户 ID，访客按设备 ID，与 ClickHouse 的 `person_id` 写法一致。
  ///
  /// @return `u:{userId}` 或 `d:{deviceId}`
  public String personKey() {
    return userId != null ? "u:" + userId : "d:" + deviceId;
  }
}
