package com.patra.ingest.domain.model.vo.execution;

import com.patra.common.enums.ProvenanceCode;
import java.time.Instant;

/**
 * {@code TASK_READY} 通道的任务就绪消息值对象。
 *
 * @param payload 消息负载
 * @param header 消息头
 * @author linqibin
 * @since 0.1.0
 */
public record TaskReadyMessage(Payload payload, Header header) {

  /** 任务消息负载。 */
  public record Payload(
      Long taskId,
      Long planId,
      Long sliceId,
      ProvenanceCode provenance,
      String operation,
      String idempotentKey,
      Integer priority,
      Instant scheduledAt,
      TaskParams params,
      String planKey,
      Instant planWindowFrom,
      Instant planWindowTo,
      String planSliceStrategy,
      PlanSliceParams planSliceParams) {

    public Payload {}
  }

  /** 任务执行所需的特定参数。 */
  public record TaskParams(Integer sliceNo) {}

  /** 描述已应用的切片策略的计划切片参数。 */
  public record PlanSliceParams(String strategy) {}

  /** 任务消息头。 */
  public record Header(
      Long scheduleInstanceId,
      String scheduler,
      Long schedulerJobId,
      Long schedulerLogId,
      String triggerType,
      Instant triggeredAt,
      Instant occurredAt,
      String planKey,
      String planOperation,
      String planEndpoint) {}
}
