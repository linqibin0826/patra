package dev.patra.notification.infra.realtime.broadcast;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.patra.notification.domain.realtime.model.vo.InstanceId;
import dev.patra.notification.domain.realtime.model.vo.UnreadCountBroadcast;
import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/// 未读数广播的发送：交给专用线程池，发到 fanout 交换机；不阻塞、不抛异常。
class UnreadCountBroadcastAdapterTest {

  private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
  private final InstanceId self = new InstanceId("a");

  /// 消息带本实例 ID 和用户 ID，发到 fanout 交换机（路由键无意义，给空串）。
  @Test
  void should_send_origin_and_user_ids_to_fanout_exchange() {
    new UnreadCountBroadcastAdapter(rabbit, self, Runnable::run).broadcast(Set.of(7L, 8L));

    verify(rabbit)
        .convertAndSend(
            "notification.unread.broadcast", "", UnreadCountBroadcast.of(self, Set.of(7L, 8L)));
  }

  /// 没有人变，不发。
  @Test
  void should_skip_empty_user_ids() {
    new UnreadCountBroadcastAdapter(rabbit, self, Runnable::run).broadcast(Set.of());

    verifyNoInteractions(rabbit);
  }

  /// RabbitMQ 连不上：接住打 WARN，不往外抛（线程池同步执行时异常会直接冒到调用方）。
  @Test
  void should_not_throw_when_sending_fails() {
    doThrow(new AmqpConnectException(new ConnectException("拒绝连接")))
        .when(rabbit)
        .convertAndSend(anyString(), anyString(), any(Object.class));

    assertThatCode(
            () ->
                new UnreadCountBroadcastAdapter(rabbit, self, Runnable::run).broadcast(Set.of(7L)))
        .doesNotThrowAnyException();
  }

  /// RabbitMQ 卡住（发送挂在那里不返回）：`broadcast` 照样立刻返回，连续调用也是。这就是「广播卡住时本机推送不变慢」
  /// 的证据——推送任务调完本机推送再调这里，不会被拖住。
  @Test
  void should_return_immediately_while_sending_is_stuck() throws InterruptedException {
    CountDownLatch stuck = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              stuck.await();
              return null;
            })
        .when(rabbit)
        .convertAndSend(anyString(), anyString(), any(Object.class));
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(500),
            new DiscardOldestBroadcastPolicy(Clock.systemUTC()));
    UnreadCountBroadcastAdapter adapter = new UnreadCountBroadcastAdapter(rabbit, self, executor);
    try {
      assertTimeoutPreemptively(
          Duration.ofSeconds(1),
          () -> {
            adapter.broadcast(Set.of(7L));
            adapter.broadcast(Set.of(8L));
            adapter.broadcast(Set.of(9L));
          });
    } finally {
      stuck.countDown();
      executor.shutdown();
      executor.awaitTermination(1, TimeUnit.SECONDS);
    }
  }

  /// 停机时线程池先关了，还有推送任务进来：不抛异常，也不发。
  @Test
  void should_not_throw_after_executor_shutdown() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.initialize();
    executor.shutdown();

    assertThatCode(
            () -> new UnreadCountBroadcastAdapter(rabbit, self, executor).broadcast(Set.of(7L)))
        .doesNotThrowAnyException();
    verifyNoInteractions(rabbit);
  }
}
