package dev.patra.interaction.app.subject.usecase.recount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.port.gateway.StatsRecountGateway;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// 按明细重算的公共流程：每个计数先找差异、再逐键在各自的事务里修正，汇总每个计数真改了的行数。
class RecountStatsHandlerTest {

  private final List<String> events = new ArrayList<>();

  /// 每个差异键一个事务；只有改了的才计入。
  @Test
  void should_recount_each_drifted_key_in_its_own_transaction() {
    FakeGateway a = new FakeGateway("a", List.of(1L, 2L, 3L), Map.of(1L, 5L, 3L, 0L), events);
    FakeGateway b = new FakeGateway("b", List.of(), Map.of(), events);
    RecountStatsHandler handler =
        new RecountStatsHandler(List.of(a, b), new RecordingTransactions(events));

    RecountResult result = handler.handle(RecountStatsCommand.of());

    assertThat(result.counters())
        .containsExactly(CounterResult.of("a", 2), CounterResult.of("b", 0));
    assertThat(result.corrected()).isEqualTo(2);
    assertThat(events)
        .containsExactly(
            "find:a",
            RecordingTransactions.BEGIN,
            "recount:a:1",
            RecordingTransactions.END,
            RecordingTransactions.BEGIN,
            "recount:a:2",
            RecordingTransactions.END,
            RecordingTransactions.BEGIN,
            "recount:a:3",
            RecordingTransactions.END,
            "find:b");
  }

  /// 结果按计数名排序，与注入顺序无关。
  @Test
  void should_order_counters_by_name() {
    RecountStatsHandler handler =
        new RecountStatsHandler(
            List.of(
                new FakeGateway("reaction.helpful_count", List.of(), Map.of(), events),
                new FakeGateway("collection.collector_count", List.of(), Map.of(), events)),
            new RecordingTransactions(events));

    RecountResult result = handler.handle(RecountStatsCommand.of());

    assertThat(result.counters())
        .extracting(CounterResult::counter)
        .containsExactly("collection.collector_count", "reaction.helpful_count");
    assertThat(result.summary())
        .isEqualTo("{collection.collector_count=0, reaction.helpful_count=0}");
    assertThat(events)
        .containsExactly("find:collection.collector_count", "find:reaction.helpful_count");
  }

  /// 子域抛出的意外异常不吞，任务失败。
  @Test
  void should_propagate_unexpected_exception() {
    StatsRecountGateway<Long> broken =
        new FakeGateway("a", List.of(1L), Map.of(), events) {
          @Override
          public Optional<StatsCorrection> recount(Long key) {
            throw new IllegalStateException("boom");
          }
        };
    RecountStatsHandler handler =
        new RecountStatsHandler(List.of(broken), new RecordingTransactions(events));

    assertThatThrownBy(() -> handler.handle(RecountStatsCommand.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  /// 手写的计数：差异键与「改了的键 → 新值」都是给定的，调用记进事件列表。
  private static class FakeGateway implements StatsRecountGateway<Long> {

    private final String counter;
    private final List<Long> drifted;
    private final Map<Long, Long> corrections;
    private final List<String> events;

    /// 创建。
    ///
    /// @param counter 计数名
    /// @param drifted 差异键
    /// @param corrections 改了的键 → 新值
    /// @param events 事件列表
    FakeGateway(
        String counter, List<Long> drifted, Map<Long, Long> corrections, List<String> events) {
      this.counter = counter;
      this.drifted = drifted;
      this.corrections = corrections;
      this.events = events;
    }

    /// 计数名。
    ///
    /// @return 名字
    @Override
    public String counter() {
      return counter;
    }

    /// 差异键。
    ///
    /// @return 键
    @Override
    public List<Long> findDrifted() {
      events.add("find:" + counter);
      return drifted;
    }

    /// 改了的键返回修正。
    ///
    /// @param key 键
    /// @return 修正
    @Override
    public Optional<StatsCorrection> recount(Long key) {
      events.add("recount:" + counter + ":" + key);
      return Optional.ofNullable(corrections.get(key)).map(after -> StatsCorrection.of(9L, after));
    }
  }
}
