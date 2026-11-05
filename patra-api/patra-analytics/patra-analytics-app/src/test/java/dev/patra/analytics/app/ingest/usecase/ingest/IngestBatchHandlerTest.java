package dev.patra.analytics.app.ingest.usecase.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.analytics.domain.ingest.exception.IngestRejectedException;
import dev.patra.analytics.domain.ingest.exception.IngestUnavailableException;
import dev.patra.analytics.domain.ingest.model.vo.IngestRecord;
import dev.patra.analytics.domain.ingest.port.store.EventStorePort;
import dev.patra.analytics.domain.kernel.model.enums.EventStream;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IngestBatchHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-01T08:31:05.118Z");

  private final FakeStore store = new FakeStore();
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final IngestBatchHandler handler =
      new IngestBatchHandler(
          store,
          rejected -> "dlq:" + rejected.reason().wire() + ":" + rejected.rawJson(),
          Clock.fixed(NOW, ZoneOffset.UTC),
          meters);

  @Test
  void token_covers_each_partition_range() {
    handler.handle(
        IngestBatchCommand.of(
            EventStream.RAW,
            List.of(
                record(0, 10, "a"), record(0, 11, "b"), record(0, 12, "c"), record(2, 5, "d"))));

    assertThat(store.writes)
        .singleElement()
        .extracting(Write::token)
        .isEqualTo("T|p0:10-12|p2:5-5");
  }

  @Test
  void token_is_stable_across_partition_order() {
    handler.handle(
        IngestBatchCommand.of(
            EventStream.RAW,
            List.of(record(2, 5, "d"), record(0, 10, "a"), record(1, 7, "x"), record(0, 12, "c"))));

    assertThat(store.writes.getFirst().token()).isEqualTo("T|p0:10-12|p1:7-7|p2:5-5");
  }

  @Test
  void unavailable_propagates_without_writing_dlq() {
    store.unavailable = true;

    assertThatThrownBy(
            () ->
                handler.handle(
                    IngestBatchCommand.of(EventStream.RAW, List.of(record(0, 0, "BAD")))))
        .isInstanceOf(IngestUnavailableException.class);
    assertThat(store.dlqWrites()).isEmpty();
  }

  @Test
  void garbage_record_goes_to_dlq_and_rest_ingested() {
    List<IngestRecord> records =
        IntStream.range(0, 8)
            .mapToObj(i -> record(0, 100 + i, i == 5 ? "BAD-" + i : "ok-" + i))
            .toList();

    IngestBatchResult result = handler.handle(IngestBatchCommand.of(EventStream.RAW, records));

    List<Write> eventWrites = store.eventWrites();
    assertThat(eventWrites).hasSizeLessThanOrEqualTo(7);
    assertThat(store.ingested)
        .containsExactlyInAnyOrder(
            "{ok-0}", "{ok-1}", "{ok-2}", "{ok-3}", "{ok-4}", "{ok-6}", "{ok-7}");
    assertThat(store.dlqWrites())
        .singleElement()
        .satisfies(
            write -> assertThat(write.lines()).containsExactly("dlq:ingest_rejected:{BAD-5}"));
    assertThat(eventWrites)
        .extracting(Write::token)
        .doesNotHaveDuplicates()
        .allSatisfy(token -> assertThat(token).startsWith("T|p0:100-107"));
    assertThat(result).isEqualTo(IngestBatchResult.of(7, 1));
  }

  @Test
  void blank_record_goes_to_dlq_without_being_written() {
    IngestBatchResult result =
        handler.handle(
            IngestBatchCommand.of(EventStream.RAW, List.of(record(0, 0, "ok"), raw(0, 1, null))));

    assertThat(store.eventWrites())
        .singleElement()
        .extracting(Write::lines)
        .isEqualTo(List.of("{ok}"));
    assertThat(store.dlqWrites().getFirst().lines()).containsExactly("dlq:ingest_rejected:");
    assertThat(result).isEqualTo(IngestBatchResult.of(1, 1));
  }

  @Test
  void non_object_record_goes_to_dlq_without_being_written() {
    IngestBatchResult result =
        handler.handle(
            IngestBatchCommand.of(
                EventStream.RAW, List.of(record(0, 0, "ok"), raw(0, 1, "  not json"))));

    assertThat(store.eventWrites())
        .singleElement()
        .extracting(Write::lines)
        .isEqualTo(List.of("{ok}"));
    assertThat(store.dlqWrites().getFirst().lines())
        .containsExactly("dlq:ingest_rejected:  not json");
    assertThat(result).isEqualTo(IngestBatchResult.of(1, 1));
  }

  @Test
  void rejected_dlq_record_is_not_reprocessed() {
    IngestBatchResult result =
        handler.handle(
            IngestBatchCommand.of(
                EventStream.DLQ, List.of(record(0, 0, "ok-0"), record(0, 1, "BAD-1"))));

    assertThat(store.writes).allSatisfy(write -> assertThat(write.table()).isEqualTo("dlq"));
    assertThat(store.ingested).containsExactly("{ok-0}");
    assertThat(store.writes).noneSatisfy(w -> assertThat(w.lines().getFirst()).startsWith("dlq:"));
    assertThat(result).isEqualTo(IngestBatchResult.of(1, 1));
  }

  @Test
  void metrics_record_batch_size_and_rejections() {
    handler.handle(
        IngestBatchCommand.of(EventStream.RAW, List.of(record(0, 0, "ok"), record(0, 1, "BAD"))));

    assertThat(
            meters.get("analytics.ingest.batch.size").tag("stream", "raw").summary().totalAmount())
        .isEqualTo(2);
    assertThat(meters.get("analytics.ingest.rejected").tag("stream", "raw").counter().count())
        .isEqualTo(1);
  }

  /// 一条 JSON 对象形状的消息：值是 `{内容}`。
  private static IngestRecord record(int partition, long offset, String content) {
    return raw(partition, offset, "{" + content + "}");
  }

  /// 一条原样的消息。
  private static IngestRecord raw(int partition, long offset, String value) {
    return IngestRecord.of("T", partition, offset, value);
  }

  /// 一次写入。
  private record Write(String table, List<String> lines, String token) {}

  /// 含 `BAD` 的行（转出来的 dlq 记录除外）让整次写入被拒；`unavailable` 时一律不可用。
  private static final class FakeStore implements EventStorePort {

    private final List<Write> writes = new ArrayList<>();
    private final List<String> ingested = new ArrayList<>();
    private boolean unavailable;

    @Override
    public void insertEvents(List<String> jsonLines, String token) {
      insert("events", jsonLines, token);
    }

    @Override
    public void insertDlq(List<String> jsonLines, String token) {
      insert("dlq", jsonLines, token);
    }

    private void insert(String table, List<String> lines, String token) {
      writes.add(new Write(table, List.copyOf(lines), token));
      if (unavailable) {
        throw new IngestUnavailableException("down", new RuntimeException());
      }
      if (lines.stream().anyMatch(line -> line.contains("BAD") && !line.startsWith("dlq:"))) {
        throw new IngestRejectedException("Code: 117", new RuntimeException());
      }
      if (lines.stream().noneMatch(line -> line.startsWith("dlq:"))) {
        ingested.addAll(lines);
      }
    }

    private List<Write> eventWrites() {
      return writes.stream().filter(write -> write.table().equals("events")).toList();
    }

    private List<Write> dlqWrites() {
      return writes.stream()
          .filter(write -> write.table().equals("dlq"))
          .filter(write -> write.lines().getFirst().startsWith("dlq:"))
          .toList();
    }

    @Override
    public List<Long> findUsersAfter(long afterUserId, int limit) {
      return List.of();
    }

    @Override
    public void deleteUsers(List<Long> userIds) {}
  }
}
