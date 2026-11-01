package dev.patra.analytics.domain.collect.model.vo;

import java.util.Objects;

/// UA 解析结果。
///
/// @param browserFamily 浏览器名；解析不出为 `null`
/// @param osFamily 操作系统名；解析不出为 `null`
/// @param deviceClass 设备类别（`Desktop`、`Phone`、`Robot` 等），总有值
public record UserAgentInfo(String browserFamily, String osFamily, String deviceClass) {

  /// 设备类别不能为空。
  public UserAgentInfo {
    Objects.requireNonNull(deviceClass, "deviceClass");
  }

  /// 创建解析结果。
  ///
  /// @param browserFamily 浏览器名
  /// @param osFamily 操作系统名
  /// @param deviceClass 设备类别
  /// @return 解析结果
  public static UserAgentInfo of(String browserFamily, String osFamily, String deviceClass) {
    return new UserAgentInfo(browserFamily, osFamily, deviceClass);
  }
}
