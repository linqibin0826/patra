package dev.patra.identity.domain.account.service;

import dev.patra.identity.domain.account.model.vo.DeviceProfile;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.port.geolocation.IpGeolocationPort;
import dev.patra.identity.domain.account.port.useragent.UserAgentParsingPort;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.IpAddressScope;
import java.util.Objects;

/// 把请求来源解析成设备名、端与属地：内网地址不查属地，公网地址查。
///
/// 两个端口约定不抛异常；万一抛了（规则或数据文件出问题）也按「未知」处理，不往外抛：安全通知在事务
/// 提交之后才解析，抛出会让已经生效的操作回 500、通知也发不出去。
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
    DeviceProfile device = parse(origin.userAgent());
    String ip = origin.ip();
    boolean privateNetwork = IpAddressScope.isPrivate(ip);
    String region = ip == null || privateNetwork ? null : locate(ip);
    return ClientOrigin.builder()
        .ip(ip)
        .userAgent(origin.userAgent())
        .deviceName(device.name())
        .deviceClass(device.deviceClass())
        .ipRegion(region)
        .privateNetwork(privateNetwork)
        .build();
  }

  /// 解析 UA，出错按未知设备。
  ///
  /// @param userAgent User-Agent，可以为 `null`
  /// @return 设备
  private DeviceProfile parse(String userAgent) {
    try {
      return userAgents.parse(userAgent);
    } catch (RuntimeException e) {
      return DeviceProfile.unknown();
    }
  }

  /// 查属地，出错按查不到。
  ///
  /// @param ip 公网 IP
  /// @return 属地；查不到时为 `null`
  private String locate(String ip) {
    try {
      return geolocation.locate(ip).orElse(null);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
