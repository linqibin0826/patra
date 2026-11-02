package dev.patra.analytics.simulator.event;

import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/// 实时压测的输出：采集信封 ②（带 IP 与 UA，交给采集端点去富化，spec §6）。
public final class CollectEnvelopeWriter {

  private final JsonMapper jsonMapper;

  /// 创建写出器。
  ///
  /// @param jsonMapper JSON
  public CollectEnvelopeWriter(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /// 写一条信封。
  ///
  /// @param event 事件
  /// @param sentAt 实际发出时刻
  /// @param random 随机源（从这个人地区的 IP 里挑一个）
  /// @return 信封
  public ObjectNode write(SimEvent event, Instant sentAt, SplittableRandom random) {
    ObjectNode node = EnvelopeFields.common(jsonMapper, event, sentAt);
    List<String> ips = event.person().region().ips();
    node.put("client_ip", ips.get(random.nextInt(ips.size())));
    node.put("user_agent", event.person().ua().userAgent());
    return node;
  }

  /// 拼成一个采集批次（`collect-batch`，1～500 条）。
  ///
  /// @param envelopes 信封
  /// @return 请求体
  public String batch(List<ObjectNode> envelopes) {
    ObjectNode root = jsonMapper.createObjectNode();
    ArrayNode events = root.putArray("events");
    envelopes.forEach(events::add);
    return jsonMapper.writeValueAsString(root);
  }
}
