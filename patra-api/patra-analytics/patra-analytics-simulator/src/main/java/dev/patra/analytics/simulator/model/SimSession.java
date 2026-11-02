package dev.patra.analytics.simulator.model;

import dev.patra.analytics.simulator.event.SimEvent;
import java.util.List;
import java.util.UUID;

/// 一个模拟会话：同一个人连续的一串事件。
///
/// @param sessionId 会话 ID
/// @param person 人
/// @param kind 真实身份
/// @param events 事件，按发生时刻升序
public record SimSession(UUID sessionId, Person person, SessionKind kind, List<SimEvent> events) {

  /// 防御性拷贝。
  public SimSession {
    events = List.copyOf(events);
  }

  /// 换一组事件（如打上爬虫标记后）。
  ///
  /// @param replaced 新事件
  /// @return 新会话
  public SimSession withEvents(List<SimEvent> replaced) {
    return new SimSession(sessionId, person, kind, replaced);
  }
}
