package dev.patra.identity.domain.session.model.vo;

import dev.patra.identity.domain.session.model.enums.DeviceClass;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 解析之后的请求来源：登录记录、安全通知、会话列表都用它（PAP-99 设计第 8.1 节）。
///
/// @param ip 客户端 IP 的规范文本，可以为 `null`
/// @param userAgent User-Agent，可以为 `null`
/// @param deviceName 设备名（浏览器主版本 · 系统族），可以为 `null`
/// @param deviceClass 端
/// @param ipRegion IP 属地，内网与查不到时为 `null`
/// @param privateNetwork IP 是不是内网地址
@Builder
public record ClientOrigin(
    String ip,
    String userAgent,
    String deviceName,
    DeviceClass deviceClass,
    String ipRegion,
    boolean privateNetwork) {

  /// 内网地址在通知里的属地写法。
  public static final String PRIVATE_NETWORK_LABEL = "内网";

  /// 端不能为 `null`。
  public ClientOrigin {
    Objects.requireNonNull(deviceClass, "deviceClass 不能为 null");
  }

  /// 转成安全通知的上下文：设备、IP、属地取自本来源，内网地址的属地写「内网」。
  ///
  /// @param occurredAt 事件发生的时间
  /// @return 安全通知
  public SecurityNotice toNotice(Instant occurredAt) {
    return SecurityNotice.of(
        occurredAt, deviceName, ip, privateNetwork ? PRIVATE_NETWORK_LABEL : ipRegion);
  }
}
