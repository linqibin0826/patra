package com.patra.ingest.domain.event;

import java.util.List;
import lombok.Builder;

/**
 * 文献数据就绪领域事件,表示任务已生成可供下游目录采集的文献数据。
 *
 * <p>该事件通过 Outbox 持久化并中继到 patra-catalog。负载仅包含元数据;实际的文献文档存储在外部(例如,对象存储)并通过 {@link #storageKeys()}
 * 引用。
 *
 * <p>触发条件:任务成功完成文献数据采集并上传到存储系统后触发。
 *
 * <p>用途:
 *
 * <ul>
 *   <li>下游消费:通知 patra-catalog 服务进行文献数据的目录化处理
 *   <li>审计跟踪:记录数据采集的完成情况和存储位置
 *   <li>监控指标:统计文献采集成功率和批次处理情况
 * </ul>
 */
@Builder
public record LiteratureDataReadyEvent(
    /** 任务 ID。 */
    Long taskId,
    /** 运行 ID。 */
    Long runId,
    /** 数据源代码(Provenance)。 */
    String provenanceCode,
    /** 存储键列表,引用外部存储的文献文档。 */
    List<String> storageKeys,
    /** 文献总数。 */
    Integer totalLiteratureCount,
    /** 成功批次数。 */
    Integer successBatchCount,
    /** 失败批次数。 */
    Integer failedBatchCount,
    /** 事件时间戳。 */
    Long timestamp) {}
