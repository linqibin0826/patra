package dev.patra.notification.app.realtime.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.patra.notification.app.realtime.service.UnreadCountStreamRegistry;
import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.domain.realtime.port.broadcast.UnreadCountBroadcastPort;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// 未读数变了：先推本机连接，再广播给其他实例（#406）。「提交后」「换线程」由集成测试证明
/// （`UnreadCountStreamFlowIT`），跨实例由 `UnreadCountBroadcastFlowIT` 证明。
class UnreadCountPushListenerTest {

  private final UnreadCountStreamRegistry registry = mock(UnreadCountStreamRegistry.class);
  private final UnreadCountBroadcastPort broadcast = mock(UnreadCountBroadcastPort.class);
  private final UnreadCountPushListener listener = new UnreadCountPushListener(registry, broadcast);

  /// 本机推送与以前完全一样，之后再广播。
  @Test
  void should_push_locally_then_broadcast_to_other_instances() {
    listener.on(UnreadCountsChangedEvent.of(Set.of(7L, 8L)));

    InOrder order = inOrder(registry, broadcast);
    order.verify(registry).push(Set.of(7L, 8L));
    order.verify(broadcast).broadcast(Set.of(7L, 8L));
  }

  /// 本机推送失败（例如读库失败）照样广播：别的实例上的连接不该因为这台出错而收不到；本机的异常照常抛出。
  @Test
  void should_still_broadcast_when_local_push_fails() {
    doThrow(new IllegalStateException("读库失败")).when(registry).push(any());

    assertThatThrownBy(() -> listener.on(UnreadCountsChangedEvent.of(Set.of(7L))))
        .isInstanceOf(IllegalStateException.class);
    verify(broadcast).broadcast(Set.of(7L));
  }
}
