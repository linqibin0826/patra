package dev.patra.analytics.domain.collect.model.vo;

import dev.patra.analytics.domain.collect.model.enums.EventStream;
import java.util.Objects;

/// 要写出的一条消息。
///
/// @param stream 写往哪里
/// @param key 分区键：富化事件是设备 ID，dlq 记录为 `null`
/// @param payload 消息内容（JSON 文本）
public record OutboundMessage(EventStream stream, String key, String payload) {

  /// 去向与内容必填。
  public OutboundMessage {
    Objects.requireNonNull(stream, "stream");
    Objects.requireNonNull(payload, "payload");
  }

  /// 一条富化事件。
  ///
  /// @param deviceId 设备 ID，作分区键
  /// @param payload 富化事件 JSON
  /// @return 消息
  public static OutboundMessage raw(String deviceId, String payload) {
    return new OutboundMessage(
        EventStream.RAW, Objects.requireNonNull(deviceId, "deviceId"), payload);
  }

  /// 一条 dlq 记录。
  ///
  /// @param payload dlq 记录 JSON
  /// @return 消息
  public static OutboundMessage dlq(String payload) {
    return new OutboundMessage(EventStream.DLQ, null, payload);
  }
}
