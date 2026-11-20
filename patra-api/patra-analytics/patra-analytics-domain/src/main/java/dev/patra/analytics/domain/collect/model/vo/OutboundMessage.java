package dev.patra.analytics.domain.collect.model.vo;

import dev.patra.analytics.domain.kernel.model.enums.EventStream;
import java.util.Objects;

/// 要写出的一条消息。
///
/// @param stream 写往哪里
/// @param key 分区键：富化事件是设备 ID，不合格记录为 `null`
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
  public static OutboundMessage enriched(String deviceId, String payload) {
    return new OutboundMessage(
        EventStream.ENRICHED, Objects.requireNonNull(deviceId, "deviceId"), payload);
  }

  /// 一条不合格记录，写往 rejected topic；格式见 dlq-record schema。
  ///
  /// @param payload 不合格记录 JSON
  /// @return 消息
  public static OutboundMessage rejected(String payload) {
    return new OutboundMessage(EventStream.REJECTED, null, payload);
  }
}
