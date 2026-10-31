package dev.patra.identity.domain.session.model.vo;

import java.time.Instant;
import java.util.Objects;

/// 安全通知邮件共用的上下文：触发这封通知的操作发生的时间、设备、IP 与属地。
///
/// 设备、IP、属地在登录设备的受信转发链路接通之前拿不到，允许为 `null`，邮件里显示「未知」。「不是我怎么办」是
/// 邮件文案，归通知服务（PAP-104）。
///
/// @param occurredAt 事件发生的时间
/// @param device 设备描述，例如 `Chrome 131 · macOS`，可以为 `null`
/// @param ipAddress 客户端 IP，可以为 `null`
/// @param ipLocation IP 属地，例如 `中国 上海`，可以为 `null`
public record SecurityNotice(
    Instant occurredAt, String device, String ipAddress, String ipLocation) {

  /// 时间不能为 `null`。
  public SecurityNotice {
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
  }

  /// 创建通知上下文。
  ///
  /// @param occurredAt 事件发生的时间
  /// @param device 设备描述，可以为 `null`
  /// @param ipAddress 客户端 IP，可以为 `null`
  /// @param ipLocation IP 属地，可以为 `null`
  /// @return 通知上下文
  public static SecurityNotice of(
      Instant occurredAt, String device, String ipAddress, String ipLocation) {
    return new SecurityNotice(occurredAt, device, ipAddress, ipLocation);
  }
}
