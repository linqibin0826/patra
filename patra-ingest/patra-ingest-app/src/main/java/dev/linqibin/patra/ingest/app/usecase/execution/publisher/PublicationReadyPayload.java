package dev.linqibin.patra.ingest.app.usecase.execution.publisher;

import dev.linqibin.patra.ingest.domain.outbox.OutboxPayload;
import java.util.List;
import java.util.Objects;

/// 出版物就绪事件的Outbox载荷
///
/// @param taskId 任务标识符
/// @param runId 执行运行标识符
/// @param provenanceCode Provenance代码 (PUBMED/EPMC等)
/// @param storageKeys 对象存储中的存储位置列表
/// @param totalPublicationCount 持久化的出版物总数
/// @param successBatchCount 成功批次数
/// @param failedBatchCount 失败批次数
/// @param timestamp 事件创建时间(epochMillis)
public record PublicationReadyPayload(
    Long taskId,
    Long runId,
    String provenanceCode,
    List<String> storageKeys,
    Integer totalPublicationCount,
    Integer successBatchCount,
    Integer failedBatchCount,
    Long timestamp)
    implements OutboxPayload {

  public PublicationReadyPayload {
    Objects.requireNonNull(taskId, "taskId不能为空");
    Objects.requireNonNull(runId, "runId不能为空");
    storageKeys = storageKeys == null ? List.of() : List.copyOf(storageKeys);
  }
}
