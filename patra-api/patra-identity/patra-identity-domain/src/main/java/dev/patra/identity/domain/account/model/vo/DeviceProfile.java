package dev.patra.identity.domain.account.model.vo;

import dev.patra.identity.domain.session.model.enums.DeviceClass;
import java.util.Objects;

/// User-Agent 解析出的设备：设备名（浏览器主版本 · 系统族）与端。
///
/// @param name 设备名，例如 `Chrome 141 · macOS`；解析不出为 `null`
/// @param deviceClass 端
public record DeviceProfile(String name, DeviceClass deviceClass) {

  /// 端不能为 `null`。
  public DeviceProfile {
    Objects.requireNonNull(deviceClass, "deviceClass 不能为 null");
  }

  /// 创建。
  ///
  /// @param name 设备名，可以为 `null`
  /// @param deviceClass 端
  /// @return 设备
  public static DeviceProfile of(String name, DeviceClass deviceClass) {
    return new DeviceProfile(name, deviceClass);
  }

  /// 什么都解析不出的设备。
  ///
  /// @return 名字为空、端为 `UNKNOWN`
  public static DeviceProfile unknown() {
    return new DeviceProfile(null, DeviceClass.UNKNOWN);
  }
}
