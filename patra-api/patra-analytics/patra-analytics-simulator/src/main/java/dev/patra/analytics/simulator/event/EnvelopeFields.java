package dev.patra.analytics.simulator.event;

import java.time.Instant;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// 采集信封与富化事件共有的字段（埋点方案 §4.2），按样例里的顺序写。
final class EnvelopeFields {

  /// 事件格式版本
  static final String SCHEMA_VERSION = "1-0-0";

  private EnvelopeFields() {}

  /// 写出共有字段。
  ///
  /// @param jsonMapper JSON
  /// @param event 事件
  /// @param sentAt 发出时刻
  /// @return 节点（可继续追加）
  static ObjectNode common(JsonMapper jsonMapper, SimEvent event, Instant sentAt) {
    ObjectNode node = jsonMapper.createObjectNode();
    node.put("event_id", event.eventId().toString());
    node.put("event_name", event.name().wire());
    node.put("schema_version", SCHEMA_VERSION);
    node.put("source", event.name().source().wire());
    node.put("created_at", Timestamps.format(event.createdAt()));
    node.put("sent_at", Timestamps.format(sentAt));
    node.put("device_id", event.person().deviceId());
    node.put("session_id", event.sessionId().toString());
    Long userId = event.person().userId();
    if (userId != null) {
      node.put("user_id", userId.toString());
    } else {
      node.putNull("user_id");
    }
    node.put("page_path", event.pagePath());
    node.put("page_type", event.pageType().wire());
    node.put("referrer_host", event.referrerHost());
    node.put("is_synthetic", true);
    node.set("properties", jsonMapper.valueToTree(event.properties()));
    return node;
  }
}
