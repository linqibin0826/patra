package dev.patra.identity.domain.session.model.vo;

import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 安全通知邮件共用的上下文：发生时间、设备、IP 与属地，以及这封邮件专属的「不是我怎么办」一句话。
///
/// 设备、IP、属地在登录设备的受信转发链路接通之前拿不到，允许为 `null`，模板渲染成「未知」。
///
/// @param occurredAt 事件发生的时间
/// @param device 设备描述，例如 `Chrome 131 · macOS`，可以为 `null`
/// @param ipAddress 客户端 IP，可以为 `null`
/// @param ipLocation IP 属地，例如 `中国 上海`，可以为 `null`
/// @param notMeAdvice 不是本人操作时该怎么办的一句话
@Builder
public record SecurityNotice(
    Instant occurredAt, String device, String ipAddress, String ipLocation, String notMeAdvice) {

  /// 时间和建议不能为 `null`。
  public SecurityNotice {
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
    Objects.requireNonNull(notMeAdvice, "notMeAdvice 不能为 null");
  }
}
