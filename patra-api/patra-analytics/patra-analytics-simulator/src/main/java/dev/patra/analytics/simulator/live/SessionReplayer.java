package dev.patra.analytics.simulator.live;

import dev.patra.analytics.simulator.event.CollectEnvelopeWriter;
import dev.patra.analytics.simulator.event.SimEvent;
import dev.patra.analytics.simulator.model.Duplicates;
import dev.patra.analytics.simulator.model.SimSession;
import dev.patra.analytics.simulator.support.Sleeper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import tools.jackson.databind.node.ObjectNode;

/// 按真实时间重放一个会话（spec §9）：同一发出时刻、同一来源的事件合成一个请求（模拟门户服务端处理页面请求、转发浏览器批次），
/// 睡到「会话墙钟起点 + 计划偏移」再发，`sent_at` 写实际发出时刻；送达后，被选中重发的事件 2～10 秒后在另一个请求里再发一次。
public final class SessionReplayer {

  private static final long MAX_SLEEP_MILLIS = 1_000;

  private final CollectEnvelopeWriter writer;
  private final CollectorClient client;
  private final Clock clock;
  private final Sleeper sleeper;
  private final Executor resendExecutor;
  private final LongAdder resent = new LongAdder();
  private final AtomicInteger pendingResends = new AtomicInteger();
  private volatile boolean stopped;

  /// 创建重放器。
  ///
  /// @param writer 信封写出器
  /// @param client 发送端
  /// @param clock 时钟
  /// @param sleeper 等待
  /// @param resendExecutor 重发在哪执行（压测时每个重发一个虚拟线程）
  public SessionReplayer(
      CollectEnvelopeWriter writer,
      CollectorClient client,
      Clock clock,
      Sleeper sleeper,
      Executor resendExecutor) {
    this.writer = writer;
    this.client = client;
    this.clock = clock;
    this.sleeper = sleeper;
    this.resendExecutor = resendExecutor;
  }

  /// 重放会话：第一个请求马上发，之后按计划的间隔发。
  ///
  /// @param session 会话
  /// @param random 随机源（挑 IP）
  /// @return 已送达的事件
  public List<SimEvent> replay(SimSession session, SplittableRandom random) {
    return replay(session, random, false);
  }

  /// 预热时重放一个「已经进行到一半」的会话（开始时刻在过去）：按计划的发出时刻发，已过去的请求跳过、不补发。
  ///
  /// @param session 会话
  /// @param random 随机源
  /// @return 已送达的事件
  public List<SimEvent> replayWarm(SimSession session, SplittableRandom random) {
    return replay(session, random, true);
  }

  /// 重放。
  ///
  /// @param session 会话
  /// @param random 随机源
  /// @param warm 是否预热会话
  /// @return 已送达的事件
  private List<SimEvent> replay(SimSession session, SplittableRandom random, boolean warm) {
    Map<String, List<SimEvent>> requests = new LinkedHashMap<>();
    session.events().stream()
        .sorted((a, b) -> a.sentAt().compareTo(b.sentAt()))
        .forEach(
            e ->
                requests
                    .computeIfAbsent(e.sentAt() + "#" + e.name().source(), k -> new ArrayList<>())
                    .add(e));
    List<SimEvent> delivered = new ArrayList<>();
    if (requests.isEmpty()) {
      return delivered;
    }
    Instant replayStart = clock.instant();
    Instant plannedStart = requests.values().iterator().next().getFirst().sentAt();
    Duration shift = warm ? Duration.ZERO : Duration.between(plannedStart, replayStart);
    for (List<SimEvent> events : requests.values()) {
      Instant planned = events.getFirst().sentAt();
      if (warm && planned.isBefore(replayStart)) {
        continue;
      }
      waitUntil(planned.plus(shift));
      if (stopped) {
        break;
      }
      Instant sentAt = clock.instant();
      List<ObjectNode> envelopes = new ArrayList<>(events.size());
      for (SimEvent event : events) {
        envelopes.add(writer.write(event, sentAt, random));
      }
      if (client.send(writer.batch(envelopes), events.size()) instanceof SendOutcome.Delivered) {
        delivered.addAll(events);
        for (int i = 0; i < events.size(); i++) {
          if (Duplicates.chosen(events.get(i).eventId())) {
            resend(envelopes.get(i));
          }
        }
      }
    }
    return delivered;
  }

  /// 过一会儿把同一条信封单独再发一次（内容完全不变）。
  ///
  /// @param envelope 信封
  private void resend(ObjectNode envelope) {
    pendingResends.incrementAndGet();
    resendExecutor.execute(
        () -> {
          try {
            sleeper.sleep(ThreadLocalRandom.current().nextLong(2_000, 10_001));
            if (!stopped) {
              client.send(writer.batch(List.of(envelope)), 1);
              resent.increment();
            }
          } finally {
            pendingResends.decrementAndGet();
          }
        });
  }

  /// 已重发的条数。
  ///
  /// @return 条数
  public long resent() {
    return resent.sum();
  }

  /// 还没发出的重发。
  ///
  /// @return 个数
  public int pendingResends() {
    return pendingResends.get();
  }

  /// 停止：正在等待的会话至多 1 秒内醒来并结束，之后不再发新的请求、不再重发。
  /// 压测收尾时用：还没走完的会话就此截断，已送达的部分照常进真值，真值与 ClickHouse 里的数据一致。
  public void stop() {
    stopped = true;
  }

  /// 等到某个时刻，分段睡（每段至多 1 秒），停止时提前返回。
  ///
  /// @param due 时刻
  private void waitUntil(Instant due) {
    long remaining = Duration.between(clock.instant(), due).toMillis();
    while (remaining > 0 && !stopped) {
      long chunk = Math.min(remaining, MAX_SLEEP_MILLIS);
      sleeper.sleep(chunk);
      remaining -= chunk;
    }
  }
}
