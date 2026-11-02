package dev.patra.analytics.simulator.truth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ReconcilerTest {

  private static final LocalDate D = LocalDate.of(2026, 11, 1);

  @Test
  void identical_sets_have_zero_error() {
    List<TruthRow> rows = List.of(new TruthRow(D, 1, 10, 3), new TruthRow(D, 2, 5, 0));

    ReconcileResult result = Reconciler.compare(rows, rows);

    assertThat(result.viewersError()).isZero();
    assertThat(result.fulltextError()).isZero();
    assertThat(result.mismatchedRows()).isZero();
    assertThat(result.passed()).isTrue();
  }

  @Test
  void error_is_sum_of_absolute_differences_over_truth() {
    List<TruthRow> truth = List.of(new TruthRow(D, 1, 10, 0), new TruthRow(D, 2, 10, 0));
    List<TruthRow> actual = List.of(new TruthRow(D, 1, 11, 0), new TruthRow(D, 2, 9, 0));

    ReconcileResult result = Reconciler.compare(truth, actual);

    assertThat(result.truthViewers()).isEqualTo(20);
    assertThat(result.actualViewers()).isEqualTo(20);
    assertThat(result.viewersError()).isCloseTo(0.10, within(1e-9));
    assertThat(result.mismatchedRows()).isEqualTo(2);
    assertThat(result.sampleMismatches()).hasSize(2);
    assertThat(result.passed()).isFalse();
  }

  @Test
  void missing_rows_on_either_side_count_fully() {
    List<TruthRow> truth = List.of(new TruthRow(D, 1, 100, 10));
    List<TruthRow> actual = List.of(new TruthRow(D, 1, 100, 10), new TruthRow(D, 9, 3, 1));

    ReconcileResult result = Reconciler.compare(truth, actual);

    assertThat(result.viewersError()).isCloseTo(0.03, within(1e-9));
    assertThat(result.fulltextError()).isCloseTo(0.10, within(1e-9));
  }

  @Test
  void passes_at_one_percent_fails_above() {
    List<TruthRow> truth = List.of(new TruthRow(D, 1, 1_000, 1_000));

    assertThat(Reconciler.compare(truth, List.of(new TruthRow(D, 1, 990, 1_010))).passed())
        .isTrue();
    assertThat(Reconciler.compare(truth, List.of(new TruthRow(D, 1, 989, 1_000))).passed())
        .isFalse();
  }

  @Test
  void run_scope_filters_by_device_prefix_and_user_range() {
    RunScope scope = new RunScope(7);

    assertThat(scope.eventsFilter()).isEqualTo("startsWith(device_id, 'sim007')");
    assertThat(scope.personFilter())
        .isEqualTo(
            "(startsWith(person_id, 'd:sim007') OR (startsWith(person_id, 'u:')"
                + " AND toUInt64OrZero(substring(person_id, 3))"
                + " BETWEEN 9000000000070000000 AND 9000000000079999999))");
  }

  @Test
  void fails_when_clickhouse_has_fewer_unique_events_than_delivered() {
    // 「看过人数」按人去重，丢掉整类曝光或 1% 的查看事件也几乎不动它；送达多少条就该查到多少条
    List<TruthRow> rows = List.of(new TruthRow(D, 1, 10, 3));
    ReconcileResult rowsMatch = Reconciler.compare(rows, rows);

    assertThat(Reconciler.judge(rowsMatch, 1_000, 0, 990, 1_010).passed()).isFalse();
    assertThat(Reconciler.judge(rowsMatch, 1_000, 0, 1_000, 1_010).passed()).isTrue();
  }

  @Test
  void events_with_unknown_outcome_may_or_may_not_be_in_clickhouse() {
    List<TruthRow> rows = List.of(new TruthRow(D, 1, 10, 3));
    ReconcileResult rowsMatch = Reconciler.compare(rows, rows);

    assertThat(Reconciler.judge(rowsMatch, 1_000, 20, 1_015, 1_035).passed()).isTrue();
    assertThat(Reconciler.judge(rowsMatch, 1_000, 20, 1_021, 1_035).passed()).isFalse();
    assertThat(Reconciler.judge(rowsMatch, 1_000, 20, 1_015, 1_035).uncertainEvents())
        .isEqualTo(20);
  }

  @Test
  void accumulating_day_by_day_equals_comparing_all_at_once() {
    LocalDate next = D.plusDays(1);
    List<TruthRow> truth1 = List.of(new TruthRow(D, 1, 10, 2), new TruthRow(D, 2, 4, 0));
    List<TruthRow> actual1 = List.of(new TruthRow(D, 1, 11, 2));
    List<TruthRow> truth2 = List.of(new TruthRow(next, 1, 7, 1));
    List<TruthRow> actual2 = List.of(new TruthRow(next, 1, 7, 1), new TruthRow(next, 5, 1, 0));

    ReconcileResult once =
        Reconciler.compare(
            Stream.concat(truth1.stream(), truth2.stream()).toList(),
            Stream.concat(actual1.stream(), actual2.stream()).toList());
    ReconcileResult daily = new Comparison().add(truth1, actual1).add(truth2, actual2).result();

    assertThat(daily).isEqualTo(once);
  }
}
