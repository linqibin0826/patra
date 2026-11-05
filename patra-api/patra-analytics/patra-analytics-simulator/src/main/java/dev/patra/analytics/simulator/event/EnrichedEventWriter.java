package dev.patra.analytics.simulator.event;

import dev.patra.analytics.simulator.model.BotLabeler;
import java.time.Instant;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// 灌历史的输出：富化事件 ③ 的一行 JSON（`events` 表的 `JSONEachRow` 行，spec §6）。
///
/// 地区、浏览器、爬虫标记由模拟器自己填（与采集端点的富化结果由测试钉住一致）；`received_at` 比发出晚 20 毫秒，
/// `derived_at` 按采集端点的公式算：`received_at − (sent_at − created_at)`。
public final class EnrichedEventWriter {

  private final JsonMapper jsonMapper;

  /// 创建写出器。
  ///
  /// @param jsonMapper JSON
  public EnrichedEventWriter(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /// 写一行。
  ///
  /// @param event 事件
  /// @return JSON 文本（不含换行）
  public String write(SimEvent event) {
    ObjectNode node = EnvelopeFields.common(jsonMapper, event, event.sentAt());
    node.put("received_at", Timestamps.format(event.sentAt().plus(BotLabeler.RECEIVE_DELAY)));
    node.put("derived_at", Timestamps.format(derivedAt(event)));
    node.put("country_code", event.person().region().countryCode());
    node.put("province", event.person().region().province());
    node.put("browser_family", event.person().ua().browserFamily());
    node.put("os_family", event.person().ua().osFamily());
    node.put("device_class", event.person().ua().deviceClass());
    node.put("is_bot", event.botReason() != null);
    node.put("bot_reason", event.botReason());
    return jsonMapper.writeValueAsString(node);
  }

  /// 灌历史里这条事件的 `derived_at`（决定落在哪天的分区）。
  ///
  /// @param event 事件
  /// @return 时刻
  public static Instant derivedAt(SimEvent event) {
    return event.createdAt().plus(BotLabeler.RECEIVE_DELAY);
  }
}
