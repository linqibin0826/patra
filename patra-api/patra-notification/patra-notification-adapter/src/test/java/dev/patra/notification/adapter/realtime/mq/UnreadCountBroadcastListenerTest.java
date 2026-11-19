package dev.patra.notification.adapter.realtime.mq;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.patra.notification.app.realtime.service.UnreadCountStreamRegistry;
import dev.patra.notification.domain.realtime.model.vo.InstanceId;
import dev.patra.notification.domain.realtime.model.vo.UnreadCountBroadcast;
import java.util.Set;
import org.junit.jupiter.api.Test;

/// 收别的实例发来的未读数广播，推给本机连接；自己发的跳过。
class UnreadCountBroadcastListenerTest {

  private final UnreadCountStreamRegistry registry = mock(UnreadCountStreamRegistry.class);
  private final InstanceId self = new InstanceId("a");
  private final UnreadCountBroadcastListener listener =
      new UnreadCountBroadcastListener(registry, self);

  /// 自己发的：本机已经直推过，再推就是两次。
  @Test
  void should_ignore_broadcast_sent_by_this_instance() {
    listener.on(UnreadCountBroadcast.of(self, Set.of(7L)));

    verifyNoInteractions(registry);
  }

  /// 别的实例发的：按用户推（连接表只处理本机有连接的人）。
  @Test
  void should_push_users_from_another_instance() {
    listener.on(UnreadCountBroadcast.of(new InstanceId("b"), Set.of(7L, 8L)));

    verify(registry).push(Set.of(7L, 8L));
  }

  /// 推送出错（例如读库失败）不往外抛：抛出去会触发监听重试、拖住这条队列，丢一次推送可以接受。
  @Test
  void should_not_throw_when_pushing_fails() {
    doThrow(new IllegalStateException("读库失败")).when(registry).push(any());

    assertThatCode(() -> listener.on(UnreadCountBroadcast.of(new InstanceId("b"), Set.of(7L))))
        .doesNotThrowAnyException();
  }
}
