package dev.patra.analytics.domain.collect.port.geolocation;

import dev.patra.analytics.domain.collect.model.vo.GeoLocation;

/// IP 查地区。
public interface GeolocationPort {

  /// 查一个 IP 所在的国家与省份。
  ///
  /// @param ip IPv4 或 IPv6 文本，可能不合法
  /// @return 地区；不合法、私有地址或查不到时两个字段都是 `null`
  GeoLocation locate(String ip);
}
