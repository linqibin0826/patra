package dev.patra.analytics.infra.history.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.analytics.domain.history.model.vo.ReadingKey;
import dev.patra.analytics.domain.history.model.vo.ReadingRecord;
import dev.patra.analytics.domain.history.port.repository.ReadingHistoryRepository;
import dev.patra.analytics.domain.history.port.repository.RecordingSettingRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

/// 历史写端：upsert 只留一行、时间不倒退、全文不被抹掉，锁住时快速失败；删一条、清空、过期清理、注销清理。
class ReadingHistoryRepositoryAdapterIT extends HistoryPersistenceITSupport {

  private static final Instant T0 = Instant.parse("2026-11-02T08:00:00.123Z");

  @Autowired private ReadingHistoryRepository history;
  @Autowired private RecordingSettingRepository settings;

  @Test
  void reopen_updates_time_once() {
    upsert(ReadingRecord.of(1L, 100L, T0, false));
    upsert(ReadingRecord.of(1L, 100L, T0.plusSeconds(3600), false));

    assertThat(count("SELECT count(*) FROM history.reading_history")).isEqualTo(1L);
    assertThat(lastReadAt(1L, 100L)).isEqualTo(T0.plusSeconds(3600));
  }

  @Test
  void older_read_does_not_move_time_back() {
    upsert(ReadingRecord.of(1L, 100L, T0, true));
    upsert(ReadingRecord.of(1L, 100L, T0.minusSeconds(60), false));

    assertThat(lastReadAt(1L, 100L)).isEqualTo(T0);
    assertThat(fulltext(1L, 100L)).isTrue();
  }

  @Test
  void plain_view_keeps_fulltext_flag() {
    upsert(ReadingRecord.of(1L, 100L, T0, true));
    upsert(ReadingRecord.of(1L, 100L, T0.plusSeconds(60), false));

    assertThat(lastReadAt(1L, 100L)).isEqualTo(T0.plusSeconds(60));
    assertThat(fulltext(1L, 100L)).isTrue();
  }

  @Test
  void write_times_out_when_row_is_locked() throws Exception {
    insertHistory(1L, 100L, T0, false);
    CountDownLatch locked = new CountDownLatch(1);
    CompletableFuture<Void> holder =
        CompletableFuture.runAsync(
            () ->
                transactions.executeWithoutResult(
                    s -> {
                      jdbcTemplate.queryForList(
                          "SELECT * FROM history.reading_history WHERE user_id = 1 FOR UPDATE");
                      locked.countDown();
                      sleep(Duration.ofSeconds(3));
                    }));
    assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

    long started = System.nanoTime();
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    s -> {
                      history.limitStatementTime();
                      history.upsertAll(
                          List.of(ReadingRecord.of(1L, 100L, T0.plusSeconds(60), true)));
                    }))
        .isInstanceOf(DataAccessException.class);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

    holder.get(10, TimeUnit.SECONDS);
    assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    assertThat(lastReadAt(1L, 100L)).isEqualTo(T0);
  }

  @Test
  void lookups_time_out_when_table_is_locked() throws Exception {
    CountDownLatch locked = new CountDownLatch(1);
    CompletableFuture<Void> holder =
        CompletableFuture.runAsync(
            () ->
                transactions.executeWithoutResult(
                    s -> {
                      jdbcTemplate.execute(
                          "LOCK TABLE history.recording_setting IN ACCESS EXCLUSIVE MODE");
                      locked.countDown();
                      sleep(Duration.ofSeconds(3));
                    }));
    assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

    long started = System.nanoTime();
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    s -> {
                      history.limitStatementTime();
                      settings.findAll(List.of(1L));
                    }))
        .isInstanceOf(DataAccessException.class);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

    holder.get(10, TimeUnit.SECONDS);
    assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void delete_one_writes_deletion_and_is_idempotent() {
    insertHistory(1L, 100L, T0, false);
    insertHistory(1L, 101L, T0, false);

    transactions.executeWithoutResult(s -> history.deleteOne(1L, 100L, T0.plusSeconds(10)));
    transactions.executeWithoutResult(s -> history.deleteOne(1L, 100L, T0.plusSeconds(20)));
    transactions.executeWithoutResult(s -> history.deleteOne(1L, 999L, T0.plusSeconds(30)));

    assertThat(count("SELECT count(*) FROM history.reading_history WHERE article_id = 100"))
        .isZero();
    assertThat(count("SELECT count(*) FROM history.reading_history WHERE article_id = 101"))
        .isEqualTo(1L);
    assertThat(
            history.findDeletedAt(
                List.of(ReadingKey.of(1L, 100L), ReadingKey.of(1L, 101L), ReadingKey.of(1L, 999L))))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                ReadingKey.of(1L, 100L), T0.plusSeconds(20),
                ReadingKey.of(1L, 999L), T0.plusSeconds(30)));
  }

  @Test
  void delete_all_removes_rows_and_deletions_of_that_user_only() {
    insertHistory(1L, 100L, T0, false);
    insertHistory(2L, 100L, T0, false);
    insertDeletion(1L, 101L, T0);
    insertDeletion(2L, 101L, T0);

    transactions.executeWithoutResult(s -> history.deleteAllOf(1L));

    assertThat(count("SELECT count(*) FROM history.reading_history WHERE user_id = 1")).isZero();
    assertThat(count("SELECT count(*) FROM history.deleted_entry WHERE user_id = 1")).isZero();
    assertThat(count("SELECT count(*) FROM history.reading_history WHERE user_id = 2"))
        .isEqualTo(1L);
    assertThat(count("SELECT count(*) FROM history.deleted_entry WHERE user_id = 2")).isEqualTo(1L);
  }

  @Test
  void purge_deletes_in_batches_until_done() {
    Instant cutoff = T0.minus(Duration.ofDays(365));
    for (long article = 1; article <= 25; article++) {
      insertHistory(1L, article, cutoff.minusSeconds(article), false);
    }
    insertHistory(1L, 100L, cutoff, false);
    insertHistory(1L, 101L, cutoff.plusSeconds(1), false);
    insertHistory(2L, 100L, T0, false);

    List<Integer> batches =
        List.of(
            purgeRead(cutoff, 10),
            purgeRead(cutoff, 10),
            purgeRead(cutoff, 10),
            purgeRead(cutoff, 10));

    assertThat(batches).containsExactly(10, 10, 5, 0);
    assertThat(count("SELECT count(*) FROM history.reading_history")).isEqualTo(3L);
  }

  @Test
  void purge_deletions_before_cutoff() {
    Instant cutoff = T0.minus(Duration.ofDays(2));
    insertDeletion(1L, 100L, cutoff.minusSeconds(1));
    insertDeletion(1L, 101L, cutoff.minusSeconds(3600));
    insertDeletion(1L, 102L, cutoff);

    Integer purged = transactions.execute(s -> history.purgeDeletionsBefore(cutoff, 10));

    assertThat(purged).isEqualTo(2);
    assertThat(count("SELECT count(*) FROM history.deleted_entry WHERE article_id = 102"))
        .isEqualTo(1L);
  }

  @Test
  void finds_users_across_three_tables() {
    insertHistory(3L, 100L, T0, false);
    insertSetting(5L, false);
    insertDeletion(9L, 100L, T0);
    insertHistory(9L, 101L, T0, false);

    assertThat(history.findUsersAfter(0L, 10)).containsExactly(3L, 5L, 9L);
    assertThat(history.findUsersAfter(3L, 1)).containsExactly(5L);
    assertThat(history.findUsersAfter(9L, 10)).isEmpty();
  }

  @Test
  void delete_users_clears_three_tables() {
    insertHistory(3L, 100L, T0, false);
    insertSetting(3L, true);
    insertDeletion(5L, 100L, T0);
    insertSetting(5L, false);
    insertHistory(9L, 100L, T0, false);
    insertSetting(9L, false);

    transactions.executeWithoutResult(s -> history.deleteUsers(List.of(3L, 5L)));

    assertThat(history.findUsersAfter(0L, 10)).containsExactly(9L);
    assertThat(count("SELECT count(*) FROM history.recording_setting")).isEqualTo(1L);
  }

  private void upsert(ReadingRecord record) {
    transactions.executeWithoutResult(s -> history.upsertAll(List.of(record)));
  }

  private int purgeRead(Instant cutoff, int limit) {
    return transactions.execute(s -> history.purgeReadBefore(cutoff, limit));
  }

  private boolean fulltext(long userId, long articleId) {
    return jdbcTemplate.queryForObject(
        "SELECT fulltext_read FROM history.reading_history WHERE user_id = ? AND article_id = ?",
        Boolean.class,
        userId,
        articleId);
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
