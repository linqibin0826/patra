package dev.patra.analytics.app.history.usecase.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.history.port.repository.RecordingSettingRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import org.junit.jupiter.api.Test;

class ChangeRecordingHandlerTest {

  private final RecordingSettingRepository settings = mock(RecordingSettingRepository.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);

  @Test
  void saves_and_returns_new_value() {
    when(currentUser.require())
        .thenReturn(CurrentUser.of(7L, 1L, AccountType.USER, ClientType.WEB));
    ChangeRecordingHandler handler = new ChangeRecordingHandler(settings, currentUser);

    assertThat(handler.handle(ChangeRecordingCommand.of(false)))
        .isEqualTo(RecordingResult.of(false));
    verify(settings).saveEnabled(7L, false);
  }
}
