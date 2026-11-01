package dev.patra.analytics.domain.collect.port.rate;

import java.time.Instant;
import java.util.OptionalInt;

/// 爬虫频率规则的滑动窗口计数（同一设备 5 分钟内的文献浏览）。
public interface DeviceRatePort {

  /// 记录一条事件并返回窗口内的计数。
  ///
  /// @param deviceId 设备 ID
  /// @param eventId 事件 ID：窗口按它去重，同一事件重发只算一次
  /// @param counted 是否计入窗口（只有文献浏览计入，其他事件只读）
  /// @param at 事件收到的时刻，作窗口的时间轴
  /// @return 窗口内的文献浏览条数；计数存储出错时为空（调用方跳过频率规则）
  OptionalInt observe(String deviceId, String eventId, boolean counted, Instant at);
}
