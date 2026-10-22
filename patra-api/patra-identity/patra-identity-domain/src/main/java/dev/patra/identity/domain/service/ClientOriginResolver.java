package dev.patra.identity.domain.service;

import dev.patra.identity.domain.model.vo.ClientOrigin;
import dev.patra.identity.domain.model.vo.DeviceProfile;
import dev.patra.identity.domain.model.vo.RequestOrigin;
import dev.patra.identity.domain.policy.IpAddressScope;
import dev.patra.identity.domain.port.geolocation.IpGeolocationPort;
import dev.patra.identity.domain.port.useragent.UserAgentParsingPort;
import java.util.Objects;

/// 把请求来源解析成设备名、端与属地：内网地址不查属地，公网地址查。
public final class ClientOriginResolver {

  private final UserAgentParsingPort userAgents;
  private final IpGeolocationPort geolocation;

  /// 创建领域服务。
  ///
  /// @param userAgents User-Agent 解析端口
  /// @param geolocation IP 属地端口
  public ClientOriginResolver(UserAgentParsingPort userAgents, IpGeolocationPort geolocation) {
    this.userAgents = Objects.requireNonNull(userAgents, "userAgents 不能为 null");
    this.geolocation = Objects.requireNonNull(geolocation, "geolocation 不能为 null");
  }

  /// 解析。
  ///
  /// @param origin 请求来源
  /// @return 解析之后的来源
  public ClientOrigin resolve(RequestOrigin origin) {
    Objects.requireNonNull(origin, "origin 不能为 null");
    DeviceProfile device = userAgents.parse(origin.userAgent());
    String ip = origin.ip();
    boolean privateNetwork = IpAddressScope.isPrivate(ip);
    String region = ip == null || privateNetwork ? null : geolocation.locate(ip).orElse(null);
    return ClientOrigin.builder()
        .ip(ip)
        .userAgent(origin.userAgent())
        .deviceName(device.name())
        .deviceClass(device.deviceClass())
        .ipRegion(region)
        .privateNetwork(privateNetwork)
        .build();
  }
}
