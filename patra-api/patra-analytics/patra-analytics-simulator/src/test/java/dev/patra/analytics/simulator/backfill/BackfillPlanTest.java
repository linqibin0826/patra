package dev.patra.analytics.simulator.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BackfillPlanTest {

  @Test
  void days_end_yesterday_and_span_requested_count() {
    BackfillParams params =
        new BackfillParams(
            1,
            1L,
            102_000_000L,
            88,
            LocalDate.of(2026, 11, 1),
            null,
            null,
            BackfillFixture.ARTICLES,
            null);

    BackfillPlan plan = BackfillPlan.of(params, BackfillFixture.MODEL, 18.0);

    assertThat(plan.days()).hasSize(88);
    assertThat(plan.days().getFirst()).isEqualTo(LocalDate.of(2026, 8, 6));
    assertThat(plan.days().getLast()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(plan.sessionsPerChunk()).isEqualTo(13_889);
    assertThat(plan.days().stream().mapToLong(plan::events).sum()).isEqualTo(102_000_000L);
    LocalDate day = plan.days().get(10);
    assertThat(plan.sessions(day)).isEqualTo(Math.round(plan.events(day) / 18.0));
    assertThat(plan.chunks(day))
        .isEqualTo((int) Math.ceil(plan.sessions(day) / (double) plan.sessionsPerChunk()));
  }

  @Test
  void chunk_ordinals_are_unique_and_consecutive() {
    BackfillPlan plan = BackfillPlan.of(BackfillFixture.params(), BackfillFixture.MODEL, 18.0);

    int expected = 0;
    for (LocalDate day : plan.days()) {
      for (int chunk = 0; chunk < plan.chunks(day); chunk++) {
        assertThat(plan.chunkOrdinal(day, chunk)).isEqualTo(expected++);
      }
    }
  }

  @Test
  void token_format_and_fingerprint_change_with_params() {
    assertThat(BackfillTokens.of(1, "ab12cd34", LocalDate.of(2026, 11, 1), "3"))
        .isEqualTo("backfill:1:ab12cd34:20261101:3");
    BackfillParams base = BackfillFixture.params();
    BackfillParams otherSeed =
        new BackfillParams(
            3,
            43L,
            60_000L,
            3,
            LocalDate.of(2026, 11, 1),
            null,
            null,
            BackfillFixture.ARTICLES,
            null);
    BackfillParams onlyDays =
        new BackfillParams(
            3,
            42L,
            60_000L,
            3,
            LocalDate.of(2026, 11, 1),
            1,
            LocalDate.of(2026, 10, 31),
            BackfillFixture.ARTICLES,
            null);

    assertThat(base.fingerprint()).hasSize(8).matches("^[0-9a-f]{8}$");
    assertThat(otherSeed.fingerprint()).isNotEqualTo(base.fingerprint());
    assertThat(onlyDays.fingerprint()).isEqualTo(base.fingerprint());
  }

  @Test
  void window_comes_from_end_day_not_from_the_clock() {
    // 试灌与正式灌之间跨过北京时间午夜，窗口也不能挪：否则同一令牌对应的内容变了，ClickHouse 整块静默丢弃
    BackfillParams params =
        new BackfillParams(
            3,
            42L,
            60_000L,
            3,
            LocalDate.of(2026, 10, 15),
            null,
            null,
            BackfillFixture.ARTICLES,
            null);
    BackfillParams nextDay =
        new BackfillParams(
            3,
            42L,
            60_000L,
            3,
            LocalDate.of(2026, 10, 16),
            null,
            null,
            BackfillFixture.ARTICLES,
            null);

    BackfillPlan plan = BackfillPlan.of(params, BackfillFixture.MODEL, 18.0);

    assertThat(plan.days())
        .containsExactly(
            LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15));
    assertThat(nextDay.fingerprint()).isNotEqualTo(params.fingerprint());
  }
}
