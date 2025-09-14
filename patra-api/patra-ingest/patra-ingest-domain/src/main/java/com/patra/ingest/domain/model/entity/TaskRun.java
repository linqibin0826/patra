package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.TaskRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务运行（attempt）· 实体。
 * <p>
 * 一次任务的具体尝试；失败重试/回放各自新增记录，不覆盖历史。
 * 记录运行期 checkpoint（如 nextHint/resumeToken）、统计（fetched/upserted/failed/pages）、错误原因与冗余窗口。
 * </p>
 *
 * JSON 字段以字符串承载。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRun {

    /** 实体标识 */
    private Long id;

    /** 关联任务ID */
    private Long taskId;

    /** 尝试序号(1起) */
    private Integer attemptNo;

    /** 运行状态 */
    private TaskRunStatus status;

    /** 运行期检查点（JSON 字符串，如 nextHint/resumeToken 等） */
    private String checkpoint;

    /** 统计（JSON 字符串）：fetched/upserted/failed/pages 等 */
    private String stats;

    /** 失败原因 */
    private String error;

    /** 时间型切片时冗余窗口起(UTC)[含] */
    private LocalDateTime windowFrom;

    /** 时间型切片时冗余窗口止(UTC)[不含] */
    private LocalDateTime windowTo;

    /** 开始时间(UTC) */
    private LocalDateTime startedAt;

    /** 结束时间(UTC) */
    private LocalDateTime finishedAt;

    /** 最近心跳时间(UTC) */
    private LocalDateTime lastHeartbeat;

    /** 外部调度运行ID（若逐片触发） */
    private String schedulerRunId;

    /** Trace/CID */
    private String correlationId;
}

