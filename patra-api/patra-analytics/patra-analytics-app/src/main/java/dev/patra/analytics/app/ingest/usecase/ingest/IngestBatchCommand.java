package dev.patra.analytics.app.ingest.usecase.ingest;

import dev.patra.analytics.domain.ingest.model.vo.IngestRecord;
import dev.patra.analytics.domain.kernel.model.enums.EventStream;
import dev.patra.tool.cqrs.Command;
import java.util.List;

/// 把一批 Kafka 消息写进 ClickHouse。
///
/// @param stream 来自哪个 topic：`RAW` 写明细表，`DLQ` 写 dlq 表
/// @param records 这一批消息，顺序同拉取
public record IngestBatchCommand(EventStream stream, List<IngestRecord> records)
    implements Command<IngestBatchResult> {

  /// 防御性拷贝。
  public IngestBatchCommand {
    records = List.copyOf(records);
  }

  /// 创建命令。
  ///
  /// @param stream 来自哪个 topic
  /// @param records 这一批消息
  /// @return 命令
  public static IngestBatchCommand of(EventStream stream, List<IngestRecord> records) {
    return new IngestBatchCommand(stream, records);
  }
}
