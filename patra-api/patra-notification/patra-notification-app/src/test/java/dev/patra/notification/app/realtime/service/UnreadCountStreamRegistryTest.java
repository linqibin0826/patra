package dev.patra.notification.app.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.kernel.port.gateway.PresenceGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// 推送连接表：登记推当前值、按用户推、心跳与在站、剔除坏连接、停机全部结束、并发时最后一条是最新值。
class UnreadCountStreamRegistryTest {

  private static final long USER = 7L;
  private static final long OTHER = 8L;
  private static final long THIRD = 9L;

  private final InboxReadPort inboxReadPort = mock(InboxReadPort.class);
  private final PresenceGateway presenceGateway = mock(PresenceGateway.class);
  private final UnreadCountStreamRegistry registry =
      new UnreadCountStreamRegistry(inboxReadPort, presenceGateway);

  @Test
  void should_send_current_counts_and_mark_seen_on_open() {
    when(inboxReadPort.unreadCounts(USER)).thenReturn(UnreadCounts.of(2, 1, 0));
    RecordingStream stream = new RecordingStream();

    registry.open(USER, stream);

    assertThat(stream.sent).containsExactly(UnreadCounts.of(2, 1, 0));
    verify(presenceGateway).markSeen(USER);
  }

  @Test
  void should_push_fresh_counts_to_every_stream_of_recipient_only() {
    when(inboxReadPort.unreadCounts(USER))
        .thenReturn(UnreadCounts.of(0, 0, 0), UnreadCounts.of(0, 0, 0), UnreadCounts.of(1, 0, 0));
    when(inboxReadPort.unreadCounts(OTHER)).thenReturn(UnreadCounts.of(5, 0, 0));
    RecordingStream first = new RecordingStream();
    RecordingStream second = new RecordingStream();
    RecordingStream others = new RecordingStream();
    registry.open(USER, first);
    registry.open(USER, second);
    registry.open(OTHER, others);

    registry.push(Set.of(USER));

    assertThat(first.sent).last().isEqualTo(UnreadCounts.of(1, 0, 0));
    assertThat(second.sent).last().isEqualTo(UnreadCounts.of(1, 0, 0));
    assertThat(others.sent).containsExactly(UnreadCounts.of(5, 0, 0));
  }

  @Test
  void should_withdraw_stream_and_rethrow_when_initial_counts_cannot_be_read() {
    IllegalStateException databaseDown = new IllegalStateException("数据库挂了");
    when(inboxReadPort.unreadCounts(USER)).thenThrow(databaseDown);
    RecordingStream stream = new RecordingStream();

    assertThatThrownBy(() -> registry.open(USER, stream)).isSameAs(databaseDown);
    registry.push(Set.of(USER));

    verify(inboxReadPort, times(1)).unreadCounts(USER);
    verify(presenceGateway, never()).markSeen(USER);
  }

  @Test
  void should_not_read_counts_for_users_without_streams() {
    registry.push(Set.of(OTHER));

    verifyNoInteractions(inboxReadPort);
  }

  @Test
  void should_drop_and_close_stream_whose_send_fails_and_keep_others() {
    when(inboxReadPort.unreadCounts(USER)).thenReturn(UnreadCounts.of(1, 0, 0));
    RecordingStream broken = new RecordingStream();
    RecordingStream healthy = new RecordingStream();
    registry.open(USER, broken);
    registry.open(USER, healthy);
    broken.failNextSend = true;

    registry.push(Set.of(USER));
    registry.push(Set.of(USER));

    assertThat(broken.closed).isTrue();
    assertThat(broken.sent).hasSize(1);
    assertThat(healthy.sent).hasSize(3);
  }

  @Test
  void should_not_throw_when_counts_cannot_be_read_during_push() {
    when(inboxReadPort.unreadCounts(USER))
        .thenReturn(UnreadCounts.of(0, 0, 0))
        .thenThrow(new IllegalStateException("数据库挂了"))
        .thenReturn(UnreadCounts.of(2, 0, 0));
    RecordingStream stream = new RecordingStream();
    registry.open(USER, stream);

    assertThatCode(() -> registry.push(Set.of(USER))).doesNotThrowAnyException();
    registry.push(Set.of(USER));

    assertThat(stream.closed).isFalse();
    assertThat(stream.sent).containsExactly(UnreadCounts.of(0, 0, 0), UnreadCounts.of(2, 0, 0));
  }

  @Test
  void should_stop_pushing_to_closed_stream() {
    when(inboxReadPort.unreadCounts(USER)).thenReturn(UnreadCounts.of(1, 0, 0));
    RecordingStream stream = new RecordingStream();
    registry.open(USER, stream);

    registry.close(USER, stream);
    registry.close(USER, stream);
    registry.push(Set.of(USER));

    assertThat(stream.sent).hasSize(1);
  }

  @Test
  void should_send_heartbeat_to_all_streams_and_mark_each_user_seen_once() {
    when(inboxReadPort.unreadCounts(anyLong())).thenReturn(UnreadCounts.of(0, 0, 0));
    RecordingStream first = new RecordingStream();
    RecordingStream second = new RecordingStream();
    RecordingStream others = new RecordingStream();
    RecordingStream lonelyBroken = new RecordingStream();
    registry.open(USER, first);
    registry.open(USER, second);
    registry.open(OTHER, others);
    registry.open(THIRD, lonelyBroken);
    lonelyBroken.failNextSend = true;
    clearInvocations(presenceGateway);

    registry.heartbeat();

    assertThat(List.of(first, second, others))
        .allSatisfy(stream -> assertThat(stream.heartbeats).isEqualTo(1));
    assertThat(lonelyBroken.closed).isTrue();
    verify(presenceGateway, times(1)).markSeen(USER);
    verify(presenceGateway, times(1)).markSeen(OTHER);
    verify(presenceGateway, never()).markSeen(THIRD);
  }

  /// 同一个用户的两次变化在两个线程里推：A 先读到旧值、卡住；B 后来读到新值。B 必须等 A 推完再读，
  /// 最后一条才是新值；否则 B 先推新值、A 再推旧值，铃铛停在旧值上。
  @Test
  @Timeout(2)
  void should_end_with_latest_counts_when_two_pushes_race() throws Exception {
    UnreadCounts initial = UnreadCounts.of(0, 0, 0);
    UnreadCounts stale = UnreadCounts.of(1, 0, 0);
    UnreadCounts latest = UnreadCounts.of(2, 0, 0);
    CountDownLatch firstPushReading = new CountDownLatch(1);
    CountDownLatch releaseFirstPush = new CountDownLatch(1);
    AtomicInteger reads = new AtomicInteger();
    when(inboxReadPort.unreadCounts(USER))
        .thenAnswer(
            invocation ->
                switch (reads.getAndIncrement()) {
                  case 0 -> initial;
                  case 1 -> {
                    firstPushReading.countDown();
                    releaseFirstPush.await();
                    yield stale;
                  }
                  default -> latest;
                });
    RecordingStream stream = new RecordingStream();
    registry.open(USER, stream);

    Thread first = Thread.ofPlatform().start(() -> registry.push(Set.of(USER)));
    firstPushReading.await();
    Thread second = Thread.ofPlatform().start(() -> registry.push(Set.of(USER)));
    while (second.isAlive() && second.getState() != Thread.State.BLOCKED) {
      Thread.onSpinWait();
    }
    releaseFirstPush.countDown();
    first.join();
    second.join();

    assertThat(stream.sent).last().isEqualTo(latest);
  }

  @Test
  void should_close_every_stream_on_shutdown() {
    when(inboxReadPort.unreadCounts(anyLong())).thenReturn(UnreadCounts.of(0, 0, 0));
    RecordingStream first = new RecordingStream();
    RecordingStream others = new RecordingStream();
    registry.open(USER, first);
    registry.open(OTHER, others);
    clearInvocations(inboxReadPort);

    registry.closeAll();
    registry.push(Set.of(USER, OTHER));

    assertThat(first.closed).isTrue();
    assertThat(others.closed).isTrue();
    verifyNoInteractions(inboxReadPort);
  }

  /// 记下收到了什么的假连接；可以让下一次发送失败。
  private static final class RecordingStream implements UnreadCountStream {

    private final List<UnreadCounts> sent = new ArrayList<>();
    private int heartbeats;
    private boolean closed;
    private boolean failNextSend;

    /// 记下推送的数；`failNextSend` 时抛出。
    ///
    /// @param counts 未读数
    @Override
    public void sendCounts(UnreadCounts counts) {
      failIfAsked();
      sent.add(counts);
    }

    /// 记一次心跳；`failNextSend` 时抛出。
    @Override
    public void sendHeartbeat() {
      failIfAsked();
      heartbeats++;
    }

    /// 记下被结束。
    @Override
    public void close() {
      closed = true;
    }

    /// 按设置让这一次发送失败。
    private void failIfAsked() {
      if (failNextSend) {
        failNextSend = false;
        throw new IllegalStateException("浏览器已走");
      }
    }
  }
}
