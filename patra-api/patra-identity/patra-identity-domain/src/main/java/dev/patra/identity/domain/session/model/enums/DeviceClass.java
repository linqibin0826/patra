package dev.patra.identity.domain.session.model.enums;

/// 设备的「端」：门户据此选图标。由 User-Agent 解析得来，解析不出或是机器人时为 `UNKNOWN`。
public enum DeviceClass {
  /// 桌面浏览器。
  DESKTOP,
  /// 手机。
  MOBILE,
  /// 平板。
  TABLET,
  /// 解析不出，或不是人用的终端（机器人、脚本）。
  UNKNOWN
}
