package dev.patra.notification.app.inbox.usecase.poll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.kernel.port.gateway.PresenceGateway;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// 门户拉未读数：先记在站（决策 H），再读三个未读数。
class PollUnreadCountsHandlerTest {

  private final PresenceGateway presence = mock(PresenceGateway.class);
  private final InboxReadPort reads = mock(InboxReadPort.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final PollUnreadCountsHandler handler =
      new PollUnreadCountsHandler(presence, reads, currentUser);

  /// 记在站、返回未读数。
  @Test
  void should_mark_presence_then_read_counts() {
    when(currentUser.require())
        .thenReturn(CurrentUser.of(2L, 1L, AccountType.USER, ClientType.WEB));
    when(reads.unreadCounts(2L)).thenReturn(UnreadCounts.of(2, 1, 0));

    assertThat(handler.handle(PollUnreadCountsCommand.of())).isEqualTo(UnreadCounts.of(2, 1, 0));

    InOrder order = inOrder(presence, reads);
    order.verify(presence).markSeen(2L);
    order.verify(reads).unreadCounts(2L);
  }
}
