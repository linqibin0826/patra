package dev.patra.notification.app.realtime.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.patra.notification.app.realtime.service.UnreadCountStreamRegistry;
import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import java.util.Set;
import org.junit.jupiter.api.Test;

/// 未读数变了：交给连接表按收件人推。「提交后」「换线程」由集成测试证明（`UnreadCountStreamFlowIT`）。
class UnreadCountPushListenerTest {

  private final UnreadCountStreamRegistry registry = mock(UnreadCountStreamRegistry.class);
  private final UnreadCountPushListener listener = new UnreadCountPushListener(registry);

  @Test
  void should_push_to_every_recipient_of_the_event() {
    listener.on(UnreadCountsChangedEvent.of(Set.of(7L, 8L)));

    verify(registry).push(Set.of(7L, 8L));
  }
}
