package dev.patra.notification.domain.mail.model.vo;

import java.time.Instant;
import java.util.Objects;

/// 触发这封安全通知的那次操作：发生时间、设备、IP、属地。
///
/// 设备、IP、属地在 identity 拿不到时为 `null`，渲染器显示「未知」；它们只放在这里，不进模板变量的 Map
/// （`Map.copyOf` 不接受 null）。
///
/// @param occurredAt 发生时间
/// @param device 设备描述，如 `Chrome 131 · macOS`，可为 `null`
/// @param ipAddress 客户端 IP，可为 `null`
/// @param ipLocation IP 属地，如 `中国 上海`，可为 `null`
public record OperationContext(
    Instant occurredAt, String device, String ipAddress, String ipLocation) {

  /// 发生时间不能为 `null`。
  public OperationContext {
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
  }

  /// 创建。
  ///
  /// @param occurredAt 发生时间
  /// @param device 设备描述
  /// @param ipAddress 客户端 IP
  /// @param ipLocation IP 属地
  /// @return 操作上下文
  public static OperationContext of(
      Instant occurredAt, String device, String ipAddress, String ipLocation) {
    return new OperationContext(occurredAt, device, ipAddress, ipLocation);
  }
}
