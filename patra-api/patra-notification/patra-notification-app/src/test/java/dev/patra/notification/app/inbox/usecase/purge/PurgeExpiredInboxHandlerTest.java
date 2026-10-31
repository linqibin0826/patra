package dev.patra.notification.app.inbox.usecase.purge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.inbox.port.repository.CommentContentRepository;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.notification.domain.inbox.port.repository.SubjectTombstoneRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/// 保留期清理：提醒、评论快照、经验墓碑共用「北京时间减 5 个自然月」。
class PurgeExpiredInboxHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
  private static final Instant CUTOFF = Instant.parse("2026-05-31T00:00:00Z");

  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final CommentContentRepository comments = mock(CommentContentRepository.class);
  private final SubjectTombstoneRepository tombstones = mock(SubjectTombstoneRepository.class);
  private final PurgeExpiredInboxHandler handler =
      new PurgeExpiredInboxHandler(
          notifications,
          comments,
          tombstones,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 先删提醒，再删没人引用的快照，最后删墓碑；返回各删了几行。
  @Test
  void should_purge_with_beijing_five_month_cutoff() {
    when(notifications.deleteOccurredBefore(CUTOFF)).thenReturn(5);
    when(comments.purgeUnreferencedBefore(CUTOFF)).thenReturn(3);
    when(tombstones.purgeBefore(CUTOFF)).thenReturn(1);

    assertThat(handler.handle(PurgeExpiredInboxCommand.of())).isEqualTo(PurgeResult.of(5, 3, 1));
  }
}
