package dev.patra.analytics.simulator.event;

import dev.patra.analytics.simulator.model.Person;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/// 模拟器的中性事件：两种输出（富化事件、采集信封）都从它序列化（spec §6）。
///
/// @param eventId 事件 ID（UUIDv7）
/// @param name 事件名
/// @param createdAt 发生时刻
/// @param sentAt 计划发出时刻（实时压测按实际发出时刻改写）
/// @param person 人
/// @param sessionId 会话 ID
/// @param pagePath 页面路径
/// @param pageType 页面类型
/// @param referrerHost 来源域名；站内为 `null`
/// @param properties 事件属性（保持插入顺序：同参数两次序列化逐字节相同）
/// @param articleIndex 文献在文献池里的排名；不涉及单篇文献为 -1
/// @param botReason 爬虫原因（灌历史由模拟器填）；非爬虫为 `null`
@Builder(toBuilder = true)
public record SimEvent(
    UUID eventId,
    SimEventName name,
    Instant createdAt,
    Instant sentAt,
    Person person,
    UUID sessionId,
    String pagePath,
    PageType pageType,
    String referrerHost,
    Map<String, Object> properties,
    int articleIndex,
    String botReason) {

  /// 属性只读且保持顺序（`Map.copyOf` 的迭代顺序每个 JVM 随机，会让重跑的写入内容不同、被令牌误判）。
  public SimEvent {
    properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
  }

  /// 换上爬虫原因。
  ///
  /// @param reason 原因；`null` 表示非爬虫
  /// @return 新事件
  public SimEvent withBotReason(String reason) {
    return toBuilder().botReason(reason).build();
  }
}
