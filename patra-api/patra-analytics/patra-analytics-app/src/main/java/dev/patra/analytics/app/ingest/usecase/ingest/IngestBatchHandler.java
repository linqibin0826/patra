package dev.patra.analytics.app.ingest.usecase.ingest;

import dev.patra.analytics.domain.ingest.exception.IngestRejectedException;
import dev.patra.analytics.domain.ingest.model.vo.IngestRecord;
import dev.patra.analytics.domain.ingest.port.store.EventStorePort;
import dev.patra.analytics.domain.kernel.model.enums.EventStream;
import dev.patra.analytics.domain.kernel.model.enums.RejectionReason;
import dev.patra.analytics.domain.kernel.model.vo.RejectedEnvelope;
import dev.patra.analytics.domain.kernel.port.dlq.DlqRecordPort;
import dev.patra.tool.cqrs.CommandHandler;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 把一批 Kafka 消息写进 ClickHouse（入库设计 §4.2～§4.4）。
///
/// 空消息和不以 `{` 开头的消息先挑出来直接转 dlq，不送 ClickHouse：JSONEachRow 会静默跳过空行，最常见的垃圾
/// 也不必靠二分去找（坏行多时二分的写入次数与数据片段会成倍增加）。
///
/// 其余整批一次写入，带批次去重令牌；ClickHouse 拒绝时二分定位坏行：写成的那半就算写成，写不进的继续对半拆，
/// 到单条还写不进就转成 dlq 记录（`events.dlq` 来的被拒单条只记日志与指标，不再转 dlq）。
/// 连不上、超时等可重试的失败直接抛出，由消费者不提交偏移量、按退避重试同一批。不开事务：不写 PG。
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestBatchHandler implements CommandHandler<IngestBatchCommand, IngestBatchResult> {

  /// 每批条数（分布，标签 `stream`）
  static final String BATCH_SIZE_METRIC = "analytics.ingest.batch.size";

  /// 被拒条数（标签 `stream`）
  static final String REJECTED_METRIC = "analytics.ingest.rejected";

  private final EventStorePort store;
  private final DlqRecordPort dlqRecords;
  private final Clock clock;
  private final MeterRegistry meters;

  /// 写入一批消息。
  ///
  /// @param command 这一批
  /// @return 写进去与被拒的条数
  @Override
  public IngestBatchResult handle(IngestBatchCommand command) {
    List<IngestRecord> records = command.records();
    if (records.isEmpty()) {
      return IngestBatchResult.of(0, 0);
    }
    String stream = command.stream().name().toLowerCase(Locale.ROOT);
    DistributionSummary.builder(BATCH_SIZE_METRIC)
        .description("一批入库的消息条数")
        .tag("stream", stream)
        .register(meters)
        .record(records.size());
    String token = token(records);
    List<Rejection> rejected = new ArrayList<>();
    List<IngestRecord> writable = new ArrayList<>(records.size());
    for (IngestRecord record : records) {
      String value = record.value();
      if (value == null || value.isBlank()) {
        rejected.add(new Rejection(record, "空消息"));
      } else if (value.stripLeading().charAt(0) != '{') {
        rejected.add(new Rejection(record, "不是 JSON 对象"));
      } else {
        writable.add(record);
      }
    }
    if (!writable.isEmpty()) {
      write(command.stream(), writable, 0, writable.size() - 1, token, rejected);
    }
    if (!rejected.isEmpty()) {
      handleRejected(command.stream(), rejected, token);
      meters.counter(REJECTED_METRIC, "stream", stream).increment(rejected.size());
    }
    return IngestBatchResult.of(records.size() - rejected.size(), rejected.size());
  }

  /// 写入 `[from, to]` 这一段；被拒时对半拆开分别写，单条被拒记下来。
  ///
  /// 整批用批次令牌，子批在后面加 `#{起}-{止}`（批内序号，含两端），互不相撞；重试同一批时拆法相同、令牌相同。
  ///
  /// @param stream 写哪张表
  /// @param records 能写的消息
  /// @param from 起（含）
  /// @param to 止（含）
  /// @param token 批次令牌
  /// @param rejected 收集被拒的单条
  private void write(
      EventStream stream,
      List<IngestRecord> records,
      int from,
      int to,
      String token,
      List<Rejection> rejected) {
    boolean whole = from == 0 && to == records.size() - 1;
    List<String> lines = records.subList(from, to + 1).stream().map(IngestRecord::value).toList();
    try {
      insert(stream, lines, whole ? token : token + "#" + from + "-" + to);
    } catch (IngestRejectedException e) {
      if (from == to) {
        rejected.add(new Rejection(records.get(from), e.getMessage()));
        return;
      }
      int middle = (from + to) >>> 1;
      write(stream, records, from, middle, token, rejected);
      write(stream, records, middle + 1, to, token, rejected);
    }
  }

  /// 按去向写入。
  ///
  /// @param stream 去向
  /// @param lines 每条一行
  /// @param token 令牌
  private void insert(EventStream stream, List<String> lines, String token) {
    if (stream == EventStream.RAW) {
      store.insertEvents(lines, token);
    } else {
      store.insertDlq(lines, token);
    }
  }

  /// 被拒的单条：明细来的转成 dlq 记录写进 dlq 表；dlq 来的只记日志，不再转。
  ///
  /// @param stream 来自哪个 topic
  /// @param rejected 被拒的单条
  /// @param token 批次令牌
  private void handleRejected(EventStream stream, List<Rejection> rejected, String token) {
    rejected.forEach(
        rejection ->
            log.warn(
                "入库被拒: partition={}, offset={}, 原因={}",
                rejection.record().partition(),
                rejection.record().offset(),
                rejection.detail()));
    if (stream == EventStream.DLQ) {
      return;
    }
    Instant now = clock.instant();
    List<String> lines =
        rejected.stream()
            .map(
                rejection ->
                    dlqRecords.dlqJson(
                        RejectedEnvelope.builder()
                            .reason(RejectionReason.INGEST_REJECTED)
                            .detail(rejection.detail())
                            .rawJson(Objects.requireNonNullElse(rejection.record().value(), ""))
                            .receivedAt(now)
                            .build()))
            .toList();
    store.insertDlq(lines, token + "#dlq");
  }

  /// 批次去重令牌：`{topic ID}|p{分区}:{首}-{尾}|…`，按分区号排序（入库设计 §4.3）。
  ///
  /// @param records 这一批（同一 topic）
  /// @return 令牌
  private static String token(List<IngestRecord> records) {
    Map<Integer, long[]> ranges = new TreeMap<>();
    for (IngestRecord record : records) {
      ranges.merge(
          record.partition(),
          new long[] {record.offset(), record.offset()},
          (a, b) -> new long[] {Math.min(a[0], b[0]), Math.max(a[1], b[1])});
    }
    return records.getFirst().topicId()
        + ranges.entrySet().stream()
            .map(e -> "|p" + e.getKey() + ":" + e.getValue()[0] + "-" + e.getValue()[1])
            .collect(Collectors.joining());
  }

  /// 被拒的一条与原因。
  ///
  /// @param record 消息
  /// @param detail ClickHouse 的报错原文
  private record Rejection(IngestRecord record, String detail) {}
}
