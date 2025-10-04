package com.patra.ingest.app.usecase.execution.command;

import java.time.Instant;
import java.util.Map;

/**
 * 任务就绪命令（Task Ready Command）。
 * <p>
 * 语义：MQ 消费到 INGEST_TASK_READY 消息后，adapter 层解析 payload 与 headers 组装此命令，
 * 传递给应用层执行用例，触发任务抢占租约与执行会话初始化。
 * </p>
 * <p>
 * 字段说明：
 * <ul>
 *   <li>taskId：任务主键（必需）</li>
 *   <li>idempotentKey：幂等键（必需），用于去重与 SUCCEEDED 检查</li>
 *   <li>provenance：来源编码（必需）</li>
 *   <li>operation：操作编码（必需）</li>
 *   <li>priority：调度优先级（可选）</li>
 *   <li>scheduledAt：计划执行时间（可选）</li>
 *   <li>planWindowFrom/To：计划窗口边界（可选，仅作为调试参考）</li>
 *   <li>headers：MQ 消息头（包含 ROCKET_MQ_MESSAGE_ID、traceId、partitionKey 等追踪字段）</li>
 * </ul>
 * </p>
 *
 * @param taskId 任务 ID
 * @param idempotentKey 幂等键
 * @param provenance 来源编码
 * @param operation 操作编码
 * @param priority 调度优先级（1高→9低）
 * @param scheduledAt 计划执行时间
 * @param planWindowFrom 计划窗口起点（UTC，含）
 * @param planWindowTo 计划窗口终点（UTC，不含）
 * @param headers MQ 消息头（用于追踪与审计）
 * @author linqibin
 * @since 0.1.0
 */
public record TaskReadyCommand(
        long taskId,
        String idempotentKey,
        String provenance,
        String operation,
        Integer priority,
        Instant scheduledAt,
        Instant planWindowFrom,
        Instant planWindowTo,
        Map<String, Object> headers
) {
    public TaskReadyCommand {
        if (idempotentKey == null || idempotentKey.isBlank()) {
            throw new IllegalArgumentException("幂等键不能为空");
        }
        if (provenance == null || provenance.isBlank()) {
            throw new IllegalArgumentException("来源编码不能为空");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("操作编码不能为空");
        }
    }

    /**
     * 从 headers 提取追踪字段（辅助方法）。
     */
    public String getMessageId() {
        return resolveHeaderAsString("ROCKET_MQ_MESSAGE_ID");
    }

    public String getCorrelationId() {
        return resolveHeaderAsString("id");
    }

    public String getSchedulerRunId() {
        return resolveHeaderAsString("scheduler");
    }

    private String resolveHeaderAsString(String key) {
        if (headers == null) {
            return null;
        }
        Object value = headers.get(key);
        if (value == null) {
            return null;
        }
        // RocketMQ 頭部字段可能為 UUID 等非字串類型，統一轉為字串避免類型轉換異常
        return value instanceof String ? (String) value : value.toString();
    }
}

