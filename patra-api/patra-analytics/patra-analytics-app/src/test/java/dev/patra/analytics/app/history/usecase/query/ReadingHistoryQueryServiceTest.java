package dev.patra.analytics.app.history.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.history.exception.InvalidHistoryCursorException;
import dev.patra.analytics.domain.history.model.vo.HistoryCursor;
import dev.patra.analytics.domain.history.model.vo.ReadingEntry;
import dev.patra.analytics.domain.history.model.vo.RecordingSetting;
import dev.patra.analytics.domain.history.port.read.ReadingHistoryReadPort;
import dev.patra.analytics.domain.history.port.repository.RecordingSettingRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadingHistoryQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");

  private final ReadingHistoryReadPort reads = mock(ReadingHistoryReadPort.class);
  private final RecordingSettingRepository settings = mock(RecordingSettingRepository.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final ReadingHistoryQueryService service =
      new ReadingHistoryQueryService(
          reads, settings, currentUser, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void signIn() {
    when(currentUser.require())
        .thenReturn(CurrentUser.of(7L, 1L, AccountType.USER, ClientType.WEB));
    when(settings.find(7L)).thenReturn(Optional.empty());
  }

  @Test
  void first_page_has_next_cursor_when_more() {
    when(reads.findPage(eq(7L), any(), isNull(), eq(31))).thenReturn(rows(31));

    ReadingHistoryPage page = service.page(null);

    assertThat(page.items()).hasSize(30);
    assertThat(page.nextCursor()).isEqualTo(HistoryCursor.of(NOW.minusSeconds(30), 30L).encode());
  }

  @Test
  void last_page_has_null_cursor() {
    HistoryCursor after = HistoryCursor.of(NOW, 99L);
    when(reads.findPage(eq(7L), any(), eq(after), eq(31))).thenReturn(rows(5));

    ReadingHistoryPage page = service.page(after.encode());

    assertThat(page.items()).hasSize(5);
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void since_is_twelve_months_ago() {
    when(reads.findPage(anyLong(), any(), any(), anyInt())).thenReturn(List.of());

    service.page(null);

    verify(reads).findPage(7L, Instant.parse("2025-11-02T08:00:00Z"), null, 31);
  }

  @Test
  void recording_enabled_defaults_true() {
    when(reads.findPage(anyLong(), any(), any(), anyInt())).thenReturn(List.of());

    assertThat(service.page(null).recordingEnabled()).isTrue();
    assertThat(service.recordingEnabled()).isTrue();

    when(settings.find(7L)).thenReturn(Optional.of(RecordingSetting.of(7L, false, null)));
    assertThat(service.page(null).recordingEnabled()).isFalse();
    assertThat(service.recordingEnabled()).isFalse();
  }

  @Test
  void bad_cursor_propagates() {
    assertThatThrownBy(() -> service.page("!!")).isInstanceOf(InvalidHistoryCursorException.class);
  }

  private static List<ReadingEntry> rows(int n) {
    return LongStream.rangeClosed(1, n)
        .mapToObj(i -> ReadingEntry.of(i, NOW.minusSeconds(i), false))
        .toList();
  }
}
