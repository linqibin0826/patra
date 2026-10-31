package dev.patra.identity.api.account.event;

import java.time.Instant;
import java.util.Objects;

/// 触发安全通知的那次操作：发生时间、设备、IP 与属地。设备、IP、属地在受信转发链路接通之前可能拿不到，为 `null`
/// 时邮件里显示「未知」。
///
/// @param occurredAt 操作发生的时间
/// @param device 设备描述，如 `Chrome 141 · macOS`，可以为 `null`
/// @param ipAddress 客户端 IP，可以为 `null`
/// @param ipLocation IP 属地，如 `中国 广东 深圳`，可以为 `null`
public record OperationContext(
    Instant occurredAt, String device, String ipAddress, String ipLocation) {

  /// 发生时间不能为 `null`。
  public OperationContext {
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
  }

  /// 创建上下文。
  ///
  /// @param occurredAt 操作发生的时间
  /// @param device 设备描述，可以为 `null`
  /// @param ipAddress 客户端 IP，可以为 `null`
  /// @param ipLocation IP 属地，可以为 `null`
  /// @return 上下文
  public static OperationContext of(
      Instant occurredAt, String device, String ipAddress, String ipLocation) {
    return new OperationContext(occurredAt, device, ipAddress, ipLocation);
  }
}
