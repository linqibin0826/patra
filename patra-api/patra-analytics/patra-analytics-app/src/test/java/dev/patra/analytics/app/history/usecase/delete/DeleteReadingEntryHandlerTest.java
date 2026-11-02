package dev.patra.analytics.app.history.usecase.delete;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.history.port.repository.ReadingHistoryRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class DeleteReadingEntryHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");

  private final ReadingHistoryRepository history = mock(ReadingHistoryRepository.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);

  @Test
  void deletes_with_now_as_deleted_at() {
    when(currentUser.require())
        .thenReturn(CurrentUser.of(7L, 1L, AccountType.USER, ClientType.WEB));
    DeleteReadingEntryHandler handler =
        new DeleteReadingEntryHandler(
            history,
            currentUser,
            TransactionOperations.withoutTransaction(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    handler.handle(DeleteReadingEntryCommand.of(39871245L));

    verify(history).deleteOne(7L, 39871245L, NOW);
  }

  @Test
  void rejects_non_positive_article_id() {
    assertThatThrownBy(() -> DeleteReadingEntryCommand.of(0L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
