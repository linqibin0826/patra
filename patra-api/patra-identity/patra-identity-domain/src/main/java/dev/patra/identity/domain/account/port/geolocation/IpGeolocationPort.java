package dev.patra.identity.domain.account.port.geolocation;

import java.util.Optional;

/// 查 IP 属地。
public interface IpGeolocationPort {

  /// 查属地：中国境内到国家 / 省 / 市，境外到国家，空格分隔。只对公网地址调用；不抛异常。
  ///
  /// @param ip 规范化之后的 IP 文本
  /// @return 属地；查不到或格式不对时为空
  Optional<String> locate(String ip);
}
