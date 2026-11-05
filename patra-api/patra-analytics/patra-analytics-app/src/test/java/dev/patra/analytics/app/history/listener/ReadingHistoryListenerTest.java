package dev.patra.analytics.app.history.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.collect.event.ArticleRead;
import dev.patra.analytics.domain.collect.event.ArticlesReadEvent;
import dev.patra.analytics.domain.history.model.vo.ReadingKey;
import dev.patra.analytics.domain.history.model.vo.ReadingRecord;
import dev.patra.analytics.domain.history.model.vo.RecordingSetting;
import dev.patra.analytics.domain.history.port.repository.ReadingHistoryRepository;
import dev.patra.analytics.domain.history.port.repository.RecordingSettingRepository;
import dev.patra.tool.test.transaction.RecordingTransactions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.support.TransactionOperations;

class ReadingHistoryListenerTest {

  private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");

  private final ReadingHistoryRepository history = mock(ReadingHistoryRepository.class);
  private final RecordingSettingRepository settings = mock(RecordingSettingRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final ReadingHistoryListener listener =
      new ReadingHistoryListener(
          history, settings, TransactionOperations.withoutTransaction(), meters);

  @BeforeEach
  void noSettingsOrDeletions() {
    when(settings.findAll(any())).thenReturn(Map.of());
    when(history.findDeletedAt(any())).thenReturn(Map.of());
  }

  @Test
  void records_reads_for_enabled_users() {
    listener.on(ArticlesReadEvent.of(List.of(ArticleRead.of(7L, 9L, T0, false))));

    assertThat(written()).containsExactly(ReadingRecord.of(7L, 9L, T0, false));
    assertThat(writes("recorded")).isEqualTo(1.0);
  }

  @Test
  void duplicate_reads_in_batch_are_merged() {
    listener.on(
        ArticlesReadEvent.of(
            List.of(
                ArticleRead.of(7L, 9L, T0, false),
                ArticleRead.of(7L, 9L, T0.minusSeconds(60), true))));

    assertThat(written()).containsExactly(ReadingRecord.of(7L, 9L, T0, true));
    assertThat(writes("recorded")).isEqualTo(1.0);
  }

  @Test
  void rows_are_sorted_by_user_then_article() {
    listener.on(
        ArticlesReadEvent.of(
            List.of(
                ArticleRead.of(8L, 1L, T0, false),
                ArticleRead.of(7L, 9L, T0, false),
                ArticleRead.of(7L, 2L, T0, false))));

    assertThat(written())
        .extracting(ReadingRecord::key)
        .containsExactly(ReadingKey.of(7L, 2L), ReadingKey.of(7L, 9L), ReadingKey.of(8L, 1L));
  }

  @Test
  void disabled_users_are_skipped() {
    when(settings.findAll(any())).thenReturn(Map.of(7L, RecordingSetting.of(7L, false, null)));

    listener.on(
        ArticlesReadEvent.of(
            List.of(ArticleRead.of(7L, 9L, T0, false), ArticleRead.of(8L, 9L, T0, false))));

    assertThat(written()).extracting(ReadingRecord::userId).containsExactly(8L);
    assertThat(writes("disabled")).isEqualTo(1.0);
  }

  @Test
  void reads_not_after_cleared_at_are_erased() {
    when(settings.findAll(any())).thenReturn(Map.of(7L, RecordingSetting.of(7L, true, T0)));

    listener.on(
        ArticlesReadEvent.of(
            List.of(
                ArticleRead.of(7L, 1L, T0, false),
                ArticleRead.of(7L, 2L, T0.minusMillis(1), false),
                ArticleRead.of(7L, 3L, T0.plusMillis(1), false))));

    assertThat(written()).extracting(ReadingRecord::articleId).containsExactly(3L);
    assertThat(writes("erased")).isEqualTo(2.0);
  }

  @Test
  void reads_not_after_deletion_are_erased() {
    when(history.findDeletedAt(any()))
        .thenReturn(Map.of(ReadingKey.of(7L, 1L), T0, ReadingKey.of(7L, 2L), T0));

    listener.on(
        ArticlesReadEvent.of(
            List.of(
                ArticleRead.of(7L, 1L, T0, false),
                ArticleRead.of(7L, 2L, T0.plusMillis(1), false))));

    assertThat(written()).extracting(ReadingRecord::articleId).containsExactly(2L);
    assertThat(writes("erased")).isEqualTo(1.0);
  }

  @Test
  void nothing_left_writes_nothing() {
    when(settings.findAll(any())).thenReturn(Map.of(7L, RecordingSetting.of(7L, false, null)));

    listener.on(ArticlesReadEvent.of(List.of(ArticleRead.of(7L, 9L, T0, false))));

    verify(history, never()).upsertAll(anyList());
  }

  @Test
  void lookups_and_write_share_one_time_limited_transaction() {
    List<String> events = new ArrayList<>();
    doAnswer(i -> events.add("limit")).when(history).limitStatementTime();
    when(settings.findAll(any()))
        .thenAnswer(
            i -> {
              events.add("settings");
              return Map.of();
            });
    when(history.findDeletedAt(any()))
        .thenAnswer(
            i -> {
              events.add("deletions");
              return Map.of();
            });
    doAnswer(i -> events.add("upsert")).when(history).upsertAll(anyList());
    ReadingHistoryListener inTransaction =
        new ReadingHistoryListener(history, settings, new RecordingTransactions(events), meters);

    inTransaction.on(ArticlesReadEvent.of(List.of(ArticleRead.of(7L, 9L, T0, false))));

    assertThat(events)
        .containsExactly(
            RecordingTransactions.BEGIN,
            "limit",
            "settings",
            "deletions",
            "upsert",
            RecordingTransactions.END);
  }

  @Test
  void write_failure_is_swallowed() {
    doThrow(new QueryTimeoutException("statement timeout")).when(history).upsertAll(anyList());

    assertThatCode(
            () ->
                listener.on(
                    ArticlesReadEvent.of(
                        List.of(
                            ArticleRead.of(7L, 9L, T0, false),
                            ArticleRead.of(7L, 9L, T0, true),
                            ArticleRead.of(8L, 9L, T0, false)))))
        .doesNotThrowAnyException();
    assertThat(writes("failed")).isEqualTo(2.0);
    assertThat(meters.find("analytics.history.writes").tag("outcome", "recorded").counter())
        .isNull();
  }

  @Test
  void lookup_failure_is_swallowed() {
    when(settings.findAll(any())).thenThrow(new QueryTimeoutException("timeout"));

    assertThatCode(
            () -> listener.on(ArticlesReadEvent.of(List.of(ArticleRead.of(7L, 9L, T0, false)))))
        .doesNotThrowAnyException();
    assertThat(writes("failed")).isEqualTo(1.0);
  }

  private List<ReadingRecord> written() {
    ArgumentCaptor<List<ReadingRecord>> captor = ArgumentCaptor.captor();
    verify(history).upsertAll(captor.capture());
    return captor.getValue();
  }

  private double writes(String outcome) {
    return meters.get("analytics.history.writes").tag("outcome", outcome).counter().count();
  }
}
