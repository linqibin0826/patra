package dev.patra.identity.domain.session.port.throttle;

import dev.patra.identity.domain.session.model.vo.DeviceId;
import java.time.Instant;

/// 新设备提醒的限流：同一账号同一设备一个冷却期内最多 1 封（PAP-99 设计第 12.3 节）。
///
/// 放行即已计数。Redis 暂时不可用时抛带 `DEP_UNAVAILABLE` 特征的异常；调用方只记日志、不发信。
public interface NewDeviceMailThrottlePort {

  /// 取一次发信许可。
  ///
  /// @param userId 用户 ID
  /// @param device 设备标识；没有时为 `null`，按同一个「无设备」桶算
  /// @param now 当前时间
  /// @return 放行时为 `true`；冷却期内为 `false`
  boolean acquire(long userId, DeviceId device, Instant now);

  /// 清掉一台设备的冷却：本人在设备列表里踢掉它之后，它再登录要立即提醒。
  ///
  /// @param userId 用户 ID
  /// @param device 设备标识；没有时为 `null`，清「无设备」桶
  void reset(long userId, DeviceId device);
}
