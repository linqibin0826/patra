package dev.patra.analytics.simulator.model;

import dev.patra.analytics.simulator.event.SimEvent;
import dev.patra.analytics.simulator.event.SimEventName;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// 灌历史时由模拟器自己打爬虫标记，逐条复刻采集端点的规则（`CollectEventsHandler.classify` 与 `device-rate.lua`，spec §5.5）：
///
/// - UA 命中 COUNTER-Robots → 全部 `ua_list`，不进频率窗口；
/// - 否则按收到时刻（发出 + 20 毫秒）顺序维护设备的 `article_viewed` 窗口（5 分钟、成员是事件 ID），
///   窗口超过 30 条时这条事件（不论什么事件）标 `rate`，直到窗口回落。
///
/// 窗口按会话维护即与服务端一致，前提是爬虫每个会话都是一次性设备、真人任意 5 分钟不超过 15 条。
public final class BotLabeler {

  /// 灌历史里「收到」比「发出」晚的毫秒数（与富化事件的 `received_at` 一致）
  public static final Duration RECEIVE_DELAY = Duration.ofMillis(20);

  private static final Duration WINDOW = Duration.ofMinutes(5);
  private static final int THRESHOLD = 30;

  private BotLabeler() {}

  /// 给会话的事件打爬虫标记。
  ///
  /// @param session 会话
  /// @return 打好标记的会话（事件顺序不变）
  public static SimSession label(SimSession session) {
    if (session.person().ua().crawler()) {
      return session.withEvents(
          session.events().stream().map(e -> e.withBotReason("ua_list")).toList());
    }
    List<SimEvent> byArrival = new ArrayList<>(session.events());
    byArrival.sort(Comparator.comparing(SimEvent::sentAt));
    Deque<Instant> times = new ArrayDeque<>();
    Deque<UUID> members = new ArrayDeque<>();
    Set<UUID> inWindow = new HashSet<>();
    Map<SimEvent, String> reasons = new IdentityHashMap<>();
    for (SimEvent event : byArrival) {
      Instant now = event.sentAt().plus(RECEIVE_DELAY);
      Instant floor = now.minus(WINDOW);
      while (!times.isEmpty() && !times.peekFirst().isAfter(floor)) {
        times.pollFirst();
        inWindow.remove(members.pollFirst());
      }
      if (event.name() == SimEventName.ARTICLE_VIEWED && inWindow.add(event.eventId())) {
        times.addLast(now);
        members.addLast(event.eventId());
      }
      if (inWindow.size() > THRESHOLD) {
        reasons.put(event, "rate");
      }
    }
    return session.withEvents(
        session.events().stream().map(e -> e.withBotReason(reasons.get(e))).toList());
  }
}
