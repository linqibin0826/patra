package dev.patra.identity.domain.account.port.useragent;

import dev.patra.identity.domain.account.model.vo.DeviceProfile;

/// 把 User-Agent 解析成设备名与端。
public interface UserAgentParsingPort {

  /// 解析。不抛异常：解析不出就返回名字为空、端为 `UNKNOWN` 的结果。
  ///
  /// @param userAgent User-Agent，可以为 `null`
  /// @return 设备
  DeviceProfile parse(String userAgent);
}
