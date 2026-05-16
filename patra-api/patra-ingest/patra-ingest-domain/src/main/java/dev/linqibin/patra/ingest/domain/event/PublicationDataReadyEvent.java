package dev.linqibin.patra.ingest.domain.event;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.util.List;
import lombok.Builder;

/// 出版物数据就绪领域事件,表示任务已生成可供下游处理的出版物数据。
///
/// 该事件通过 Outbox 持久化并中继到下游服务。负载仅包含元数据;实际的出版物文档存储在外部(例如,对象存储)并通过 {@link #storageKeys()}
/// 引用。
///
/// 触发条件:任务成功完成出版物数据采集并上传到存储系统后触发。
///
/// 用途:
///
/// - 下游消费:通知下游服务出版物数据已就绪,可进行后续处理
///   - 审计跟踪:记录数据采集的完成情况和存储位置
///   - 监控指标:统计出版物采集成功率和批次处理情况
///
/// @param taskId 任务 ID
/// @param runId 运行 ID
/// @param provenanceCode 数据源代码(Provenance)
/// @param storageKeys 存储键列表,引用外部存储的出版物文档
/// @param totalPublicationCount 出版物总数
/// @param successBatchCount 成功批次数
/// @param failedBatchCount 失败批次数
/// @param timestamp 事件时间戳
@Builder
public record PublicationDataReadyEvent(
    Long taskId,
    Long runId,
    ProvenanceCode provenanceCode,
    List<String> storageKeys,
    Integer totalPublicationCount,
    Integer successBatchCount,
    Integer failedBatchCount,
    Long timestamp) {

  /// 紧凑构造器：确保存储键列表不可变。
  public PublicationDataReadyEvent {
    storageKeys = storageKeys != null ? List.copyOf(storageKeys) : List.of();
  }
}
