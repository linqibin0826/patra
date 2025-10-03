package com.patra.ingest.adapter.inbound.stream.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * INGEST_TASK_READY 消息 Payload DTO。
 * <p>
 * 用于解析 MQ 消息体（JSON 格式），映射 Outbox 发布的任务就绪消息。
 * </p>
 * <p>
 * 字段说明：
 * <ul>
 *   <li>taskId：任务 ID（必需）</li>
 *   <li>idempotentKey：幂等键（必需）</li>
 *   <li>provenance：来源编码（必需）</li>
 *   <li>operation：操作编码（必需）</li>
 *   <li>priority：调度优先级（可选）</li>
 *   <li>scheduledAt：计划执行时间（可选）</li>
 *   <li>planWindowFrom：计划窗口起点（可选）</li>
 *   <li>planWindowTo：计划窗口终点（可选）</li>
 *   <li>planId：计划 ID（可选，用于追踪）</li>
 *   <li>sliceId：切片 ID（可选，用于追踪）</li>
 * </ul>
 * </p>
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

    /** 来源编码 */
    @JsonProperty("provenance")
    private String provenance;

    /** 操作编码 */
    @JsonProperty("operation")
    private String operation;

    /** 调度优先级（1高→9低） */
    @JsonProperty("priority")
    private Integer priority;

    /** 计划执行时间（UTC） */
    @JsonProperty("scheduledAt")
    private Instant scheduledAt;

    /** 计划窗口起点（UTC，含） */
    @JsonProperty("planWindowFrom")
    private Instant planWindowFrom;

    /** 计划窗口终点（UTC，不含） */
    @JsonProperty("planWindowTo")
    private Instant planWindowTo;

    /** 计划 ID（用于追踪） */
    @JsonProperty("planId")
    private Long planId;

    /** 切片 ID（用于追踪） */
    @JsonProperty("sliceId")
    private Long sliceId;

    /**
     * 校验必需字段。
     *
     * @throws IllegalArgumentException 当必需字段为空时
     */
    public void validate() {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (idempotentKey == null || idempotentKey.isBlank()) {
            throw new IllegalArgumentException("idempotentKey 不能为空");
        }
        if (provenance == null || provenance.isBlank()) {
            throw new IllegalArgumentException("provenance 不能为空");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation 不能为空");
        }
    }
}
