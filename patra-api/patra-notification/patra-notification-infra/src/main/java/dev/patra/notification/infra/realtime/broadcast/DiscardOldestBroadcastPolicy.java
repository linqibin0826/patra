package dev.patra.notification.infra.realtime.broadcast;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/// 未读数广播线程池排满时的处理：丢掉排得最久的一条，把新的放进去。
///
/// 排满说明 RabbitMQ 发送变慢或卡住了。广播只是「你的数变了」的信号，丢了能补（下一次变化或重连时推当前值），
/// 新的比旧的更有价值，所以丢最旧的。
///
/// RabbitMQ 卡住期间每次变化都会丢一条，日志按 {@link #WARN_INTERVAL} 限频：每段最多一行，带这段时间一共丢了几条。
@Slf4j
public final class DiscardOldestBroadcastPolicy implements RejectedExecutionHandler {

  /// 两行丢弃告警之间至少隔多久。
  static final Duration WARN_INTERVAL = Duration.ofSeconds(60);

  private final Clock clock;

  /// 上一行告警之后丢了几条（含还没打出来的）。
  private final AtomicLong droppedSinceWarn = new AtomicLong();

  /// 上一行告警的时刻；还没打过时为 `null`。
  private final AtomicReference<Instant> lastWarnAt = new AtomicReference<>();

  /// 构造。
  ///
  /// @param clock 时钟
  public DiscardOldestBroadcastPolicy(Clock clock) {
    this.clock = clock;
  }

  /// 丢掉队首、再提交这一个；线程池已关时静默丢弃。
  ///
  /// @param task 被拒的任务
  /// @param executor 线程池
  @Override
  public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
    if (executor.isShutdown()) {
      return;
    }
    if (executor.getQueue().poll() != null) {
      droppedSinceWarn.incrementAndGet();
      warnIfDue();
    }
    executor.execute(task);
  }

  /// 离上一行告警够久了就打一行，并把计数清零；并发时只有一个线程打。
  private void warnIfDue() {
    Instant now = clock.instant();
    Instant last = lastWarnAt.get();
    if (last != null && now.isBefore(last.plus(WARN_INTERVAL))) {
      return;
    }
    if (lastWarnAt.compareAndSet(last, now)) {
      log.warn("未读数广播排队已满，丢弃最旧的 {} 条（RabbitMQ 发送变慢或不可用）", droppedSinceWarn.getAndSet(0));
    }
  }
}
