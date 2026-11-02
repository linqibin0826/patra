package dev.patra.analytics.app.history.usecase.clear;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.history.port.repository.ReadingHistoryRepository;
import dev.patra.analytics.domain.history.port.repository.RecordingSettingRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClearReadingHistoryHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");

  private final ReadingHistoryRepository history = mock(ReadingHistoryRepository.class);
  private final RecordingSettingRepository settings = mock(RecordingSettingRepository.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final List<String> events = new ArrayList<>();
  private final ClearReadingHistoryHandler handler =
      new ClearReadingHistoryHandler(
          history,
          settings,
          currentUser,
          new RecordingTransactions(events),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void clears_rows_and_marks_cleared_in_one_transaction() {
    when(currentUser.require())
        .thenReturn(CurrentUser.of(7L, 1L, AccountType.USER, ClientType.WEB));
    doAnswer(i -> events.add("deleteAllOf:" + i.getArgument(0))).when(history).deleteAllOf(7L);
    doAnswer(i -> events.add("markCleared:" + i.getArgument(1)))
        .when(settings)
        .markCleared(7L, NOW);

    handler.handle(ClearReadingHistoryCommand.of());

    assertThat(events)
        .containsExactly(
            RecordingTransactions.BEGIN,
            "deleteAllOf:7",
            "markCleared:" + NOW,
            RecordingTransactions.END);
  }

  @Test
  void anonymous_is_rejected() {
    when(currentUser.require()).thenThrow(new AuthenticationRequiredException());
    ClearReadingHistoryCommand command = ClearReadingHistoryCommand.of();

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(AuthenticationRequiredException.class);
    verifyNoInteractions(history, settings);
  }
}
