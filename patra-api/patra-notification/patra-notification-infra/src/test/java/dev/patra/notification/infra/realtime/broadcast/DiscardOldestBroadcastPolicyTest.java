package dev.patra.notification.infra.realtime.broadcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/// 广播线程池排满时的处理：丢最旧的、留最新的，日志限频。
@ExtendWith(OutputCaptureExtension.class)
class DiscardOldestBroadcastPolicyTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-13T00:00:00Z"));
  private final DiscardOldestBroadcastPolicy policy = new DiscardOldestBroadcastPolicy(clock);
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), policy);
  private final CountDownLatch release = new CountDownLatch(1);
  private final List<String> ran = new CopyOnWriteArrayList<>();

  @AfterEach
  void tearDown() throws InterruptedException {
    release.countDown();
    executor.shutdownNow();
    executor.awaitTermination(1, TimeUnit.SECONDS);
  }

  /// 线程被占住、队列（容量 2）满了再来一个：丢掉排得最久的 A，执行 B、C。
  @Test
  void should_drop_oldest_and_keep_newest_when_queue_is_full() throws InterruptedException {
    occupyThread();
    executor.execute(record("A"));
    executor.execute(record("B"));
    executor.execute(record("C"));

    release.countDown();
    executor.shutdown();
    assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();

    assertThat(ran).containsExactly("B", "C");
  }

  /// RabbitMQ 卡住时每次变化都会丢一条，日志每 60 秒最多一行，带这段时间一共丢了几条。
  @Test
  void should_warn_once_per_minute_with_dropped_count(CapturedOutput output) {
    occupyThread();
    executor.execute(record("A"));
    executor.execute(record("B"));

    executor.execute(record("C"));
    executor.execute(record("D"));
    executor.execute(record("E"));
    assertThat(warnings(output)).containsExactly("未读数广播排队已满，丢弃最旧的 1 条（RabbitMQ 发送变慢或不可用）");

    clock.advanceSeconds(61);
    executor.execute(record("F"));
    assertThat(warnings(output))
        .containsExactly(
            "未读数广播排队已满，丢弃最旧的 1 条（RabbitMQ 发送变慢或不可用）", "未读数广播排队已满，丢弃最旧的 3 条（RabbitMQ 发送变慢或不可用）");
  }

  /// 输出里的丢弃告警，只取消息部分。
  ///
  /// @param output 捕获的输出
  /// @return 告警消息
  private static List<String> warnings(CapturedOutput output) {
    return output
        .getAll()
        .lines()
        .filter(line -> line.contains("未读数广播排队已满"))
        .map(line -> line.substring(line.indexOf("未读数广播排队已满")))
        .toList();
  }

  /// 停机后还有广播进来：静默丢掉，不抛异常、不执行。
  @Test
  void should_discard_quietly_after_shutdown() {
    executor.shutdownNow();

    assertThatCode(() -> policy.rejectedExecution(record("late"), executor))
        .doesNotThrowAnyException();
    assertThat(ran).isEmpty();
  }

  /// 让唯一的线程卡在闩上，后面提交的任务只能排队。
  private void occupyThread() {
    executor.execute(
        () -> {
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
  }

  /// 执行时记下名字的任务。
  ///
  /// @param name 名字
  /// @return 任务
  private Runnable record(String name) {
    return () -> ran.add(name);
  }

  /// 可以拨的时钟。
  static final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;

    MutableClock(Instant start) {
      this.now = new AtomicReference<>(start);
    }

    void advanceSeconds(long seconds) {
      now.updateAndGet(instant -> instant.plusSeconds(seconds));
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }
}
