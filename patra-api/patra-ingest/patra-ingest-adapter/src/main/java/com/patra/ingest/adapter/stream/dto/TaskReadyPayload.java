package com.patra.ingest.adapter.stream.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * INGEST_TASK_READY 消息负载 DTO(简化版)。
 *
 * <p>解析 MQ 消息体(JSON 格式)用于任务就绪事件。仅包含必需字段 - 所有其他业务数据应从数据库查询。
 *
 * <p>字段:
 *
 * <ul>
 *   <li>taskId: 任务 ID(必填,用于上下文加载和租约获取)
 *   <li>idempotentKey: 幂等键(必填,用于去重)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
public class TaskReadyPayload {

  /** 任务 ID */
  @JsonProperty("taskId")
  private Long taskId;

  /** 幂等键 */
  @JsonProperty("idempotentKey")
  private String idempotentKey;

  /**
   * 验证必填字段。
   *
   * @throws IllegalArgumentException 当必填字段为 null/空白时
   */
  public void validate() {
    if (taskId == null) {
      throw new IllegalArgumentException("任务 ID 不能为空");
    }
    if (idempotentKey == null || idempotentKey.isBlank()) {
      throw new IllegalArgumentException("幂等键不能为空");
    }
  }
}
